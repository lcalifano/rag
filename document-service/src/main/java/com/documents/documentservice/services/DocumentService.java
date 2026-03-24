package com.documents.documentservice.services;

import com.documents.documentservice.dto.ChunkDto;
import com.documents.documentservice.dto.DocumentDto;
import com.documents.documentservice.entities.Document;
import com.documents.documentservice.entities.DocumentChunk;
import com.documents.documentservice.repositories.DocumentChunkRepository;
import com.documents.documentservice.repositories.DocumentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final PdfExtractorService pdfExtractorService;
    private final ChunkingService chunkingService;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @Transactional
    public DocumentDto uploadAndProcess(MultipartFile file, Long userId) throws IOException {
        // Salva il file su disco
        Path uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);

        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath);

        // Crea il record Document
        Document document = Document.builder()
                .userId(userId)
                .filename(filename)
                .originalFilename(file.getOriginalFilename())
                .fileSize(file.getSize())
                .status("PROCESSING")
                .createdAt(LocalDateTime.now())
                .build();
        document = documentRepository.save(document);

        try {
            // Estrai testo dal PDF
            String text = pdfExtractorService.extractText(file.getInputStream());

            // Chunking
            List<String> chunks = chunkingService.chunkText(text);

            // Salva i chunk
            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunk chunk = DocumentChunk.builder()
                        .documentId(document.getId())
                        .chunkIndex(i)
                        .content(chunks.get(i))
                        .createdAt(LocalDateTime.now())
                        .build();
                documentChunkRepository.save(chunk);
            }

            document.setTotalChunks(chunks.size());
            document.setStatus("READY");
            documentRepository.save(document);

        } catch (Exception e) {
            log.error("Errore nel processing del documento: {}", e.getMessage());
            document.setStatus("FAILED");
            documentRepository.save(document);
            throw new RuntimeException("Errore nel processing del documento", e);
        }

        return toDto(document);
    }

    public List<DocumentDto> getUserDocuments(Long userId) {
        return documentRepository.findAllByUserId(userId).stream()
                .map(this::toDto)
                .toList();
    }

    public DocumentDto getDocument(Long id, Long userId) {
        Document doc = documentRepository.findById(id)
                .filter(d -> d.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Documento non trovato"));
        return toDto(doc);
    }

    @Transactional
    public void deleteDocument(Long id, Long userId) {
        Document doc = documentRepository.findById(id)
                .filter(d -> d.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Documento non trovato"));

        documentChunkRepository.deleteAllByDocumentId(doc.getId());
        documentRepository.delete(doc);

        // Cancella il file fisico
        try {
            Files.deleteIfExists(Paths.get(uploadDir).resolve(doc.getFilename()));
        } catch (IOException e) {
            log.warn("Impossibile cancellare il file: {}", doc.getFilename());
        }
    }

    public List<ChunkDto> searchChunks(String query, Long userId, int limit) {
        return documentChunkRepository.searchByContentAndUserId(query, userId, limit).stream()
                .map(chunk -> ChunkDto.builder()
                        .id(chunk.getId())
                        .documentId(chunk.getDocumentId())
                        .chunkIndex(chunk.getChunkIndex())
                        .content(chunk.getContent())
                        .build())
                .toList();
    }

    private DocumentDto toDto(Document doc) {
        return DocumentDto.builder()
                .id(doc.getId())
                .originalFilename(doc.getOriginalFilename())
                .fileSize(doc.getFileSize())
                .totalChunks(doc.getTotalChunks())
                .status(doc.getStatus())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}

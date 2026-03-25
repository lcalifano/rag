package com.documents.documentservice.services;

import com.documents.documentservice.clients.LlmServiceClient;
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

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final PdfExtractorService pdfExtractorService;
    private final ChunkingService chunkingService;
    private final LlmServiceClient llmServiceClient;
    private final AsyncEmbeddingService asyncEmbeddingService;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.embedding-batch-size:10}")
    private int embeddingBatchSize;

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

        // Avvia il processing asincrono (passando id e filename, non il MultipartFile
        // che non sopravvive al cambio di thread)
        asyncEmbeddingService.embedDocument(document.getId(), document.getFilename());
//        try {
//            // Estrai testo dal PDF
//            String text = pdfExtractorService.extractText(file.getInputStream());
//
//            // Chunking
//            List<String> chunks = chunkingService.chunkText(text);
//
//            // Salva i chunk senza embedding (gestito via native query)
//            for (int i = 0; i < chunks.size(); i++) {
//                DocumentChunk chunk = DocumentChunk.builder()
//                        .documentId(document.getId())
//                        .chunkIndex(i)
//                        .content(chunks.get(i))
//                        .createdAt(LocalDateTime.now())
//                        .build();
//                documentChunkRepository.save(chunk);
//            }
//
//            // Genera embeddings a batch e salvali via native query con cast a vector
//            List<DocumentChunk> savedChunks = documentChunkRepository.findAllByDocumentId(document.getId());
//            generateAndSaveEmbeddings(savedChunks);
//
//            document.setTotalChunks(chunks.size());
//            document.setStatus("READY");
//            documentRepository.save(document);
//
//        } catch (Exception e) {
//            log.error("Errore nel processing del documento: {}", e.getMessage());
//            document.setStatus("FAILED");
//            documentRepository.save(document);
//            throw new RuntimeException("Errore nel processing del documento", e);
//        }

        return toDto(document);
    }

    /**
     * Genera embeddings per i chunk tramite il LLM Service e li salva nel DB.
     * I chunk vengono processati a batch per non sovraccaricare il servizio.
     */
//    private void generateAndSaveEmbeddings(List<DocumentChunk> chunks) {
//        for (int i = 0; i < chunks.size(); i += embeddingBatchSize) {
//            int end = Math.min(i + embeddingBatchSize, chunks.size());
//            List<DocumentChunk> batch = chunks.subList(i, end);
//
//            List<String> texts = batch.stream()
//                    .map(DocumentChunk::getContent)
//                    .toList();
//
//            List<List<Double>> embeddings = llmServiceClient.getEmbeddings(texts);
//
//            for (int j = 0; j < batch.size(); j++) {
//                String embeddingStr = embeddingToString(embeddings.get(j));
//                documentChunkRepository.updateEmbedding(batch.get(j).getId(), embeddingStr);
//            }
//
//            log.info("Embeddings generati per chunk {}-{} di {}", i, end - 1, chunks.size());
//        }
//    }

    /**
     * Converte una lista di double nel formato stringa pgvector: [0.1,0.2,0.3]
     */
    private String embeddingToString(List<Double> embedding) {
        return "[" + embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
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

    public Resource getFileResource(Long id, Long userId) {
        Document doc = documentRepository.findById(id)
                .filter(d -> d.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Documento non trovato"));

        try {
            Path filePath = Paths.get(uploadDir).resolve(doc.getFilename());
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) {
                throw new RuntimeException("File non trovato su disco: " + doc.getFilename());
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new RuntimeException("Errore nel recupero del file", e);
        }
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

    // === VECCHIO: ricerca testuale con ILIKE ===
//    public List<ChunkDto> searchChunks(String query, Long userId, int limit) {
//        return documentChunkRepository.searchByContentAndUserId(query, userId, limit).stream()
//                .map(chunk -> ChunkDto.builder()
//                        .id(chunk.getId())
//                        .documentId(chunk.getDocumentId())
//                        .chunkIndex(chunk.getChunkIndex())
//                        .content(chunk.getContent())
//                        .build())
//                .toList();
//    }

    // === NUOVO: similarity search vettoriale ===
    public List<ChunkDto> searchChunks(String query, Long userId, int limit) {
        // 1. Genera l'embedding della query
        List<List<Double>> queryEmbeddings = llmServiceClient.getEmbeddings(List.of(query));
        String queryEmbeddingStr = embeddingToString(queryEmbeddings.get(0));

        // 2. Cerca i chunk più simili nel DB via cosine distance (pgvector)
        return searchChunksByEmbedding(queryEmbeddingStr, userId, limit);
    }

    /**
     * Cerca chunk per similarity dato un embedding già calcolato.
     * Usato da chat-service che calcola l'embedding autonomamente.
     */
    public List<ChunkDto> searchChunksByEmbedding(String embeddingStr, Long userId, int limit) {
        List<ChunkDto> chunks = documentChunkRepository.searchByEmbeddingSimilarity(embeddingStr, userId, limit).stream()
                .map(chunk -> ChunkDto.builder()
                        .id(chunk.getId())
                        .documentId(chunk.getDocumentId())
                        .chunkIndex(chunk.getChunkIndex())
                        .content(chunk.getContent())
                        .build())
                .toList();

        return chunks;
    }

    public List<DocumentDto> getAllDocuments() {
        return documentRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    private DocumentDto toDto(Document doc) {
        return DocumentDto.builder()
                .id(doc.getId())
                .userId(doc.getUserId())
                .originalFilename(doc.getOriginalFilename())
                .fileSize(doc.getFileSize())
                .totalChunks(doc.getTotalChunks())
                .status(doc.getStatus())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}

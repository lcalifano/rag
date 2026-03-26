package com.documents.documentservice.services;

import com.documents.documentservice.clients.LlmServiceClient;
import com.documents.documentservice.entities.Document;
import com.documents.documentservice.entities.DocumentChunk;
import com.documents.documentservice.repositories.DocumentChunkRepository;
import com.documents.documentservice.repositories.DocumentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncEmbeddingService {

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.embedding-batch-size:10}")
    private int embeddingBatchSize;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ChunkingService chunkingService;
    private final LlmServiceClient llmServiceClient;
    private final PdfExtractorService pdfExtractorService;

    /**
     * Processa il documento in modo asincrono: estrae testo, chunka, genera embeddings.
     * Riceve il documentId e il filename (non il MultipartFile, che non sopravvive al cambio di thread).
     */
    @Async("documentUpload")
    @Transactional
    public void embedDocument(Long documentId, String filename) {
        // Re-fetch dell'entità nella transazione corrente (il document passato dal caller è detached)
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Documento non trovato: " + documentId));

        try {
            // Leggi il file dal disco (è già stato salvato da uploadAndProcess)
            Path filePath = Paths.get(uploadDir).resolve(filename);
            String text = pdfExtractorService.extractText(Files.newInputStream(filePath));

            // Chunking
            List<String> chunks = chunkingService.chunkText(text);

            // Salva i chunk senza embedding
            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunk chunk = DocumentChunk.builder()
                        .documentId(document.getId())
                        .chunkIndex(i)
                        .content(chunks.get(i))
                        .createdAt(LocalDateTime.now())
                        .build();
                documentChunkRepository.save(chunk);
            }

            // Genera embeddings a batch e salvali via native query con cast a vector
            List<DocumentChunk> savedChunks = documentChunkRepository.findAllByDocumentId(document.getId());
            generateAndSaveEmbeddings(savedChunks);

            document.setTotalChunks(chunks.size());
            document.setStatus("READY");
            documentRepository.save(document);

            log.info("Documento {} processato con successo: {} chunks", documentId, chunks.size());

        } catch (Exception e) {
            log.error("Errore nel processing del documento {}: {}", documentId, e.getMessage());
            document.setStatus("FAILED");
            documentRepository.save(document);
        }
    }

    private void generateAndSaveEmbeddings(List<DocumentChunk> chunks) {
        for (int i = 0; i < chunks.size(); i += embeddingBatchSize) {
            int end = Math.min(i + embeddingBatchSize, chunks.size());
            List<DocumentChunk> batch = chunks.subList(i, end);

            List<String> texts = batch.stream()
                    .map(DocumentChunk::getContent)
                    .toList();

            List<List<Double>> embeddings = llmServiceClient.getEmbeddings(texts);

            for (int j = 0; j < batch.size(); j++) {
                String embeddingStr = embeddingToString(embeddings.get(j));
                documentChunkRepository.updateEmbedding(batch.get(j).getId(), embeddingStr);
            }

            log.info("Embeddings generati per chunk {}-{} di {}", i, end - 1, chunks.size());
        }
    }

    private String embeddingToString(List<Double> embedding) {
        return "[" + embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
    }
}

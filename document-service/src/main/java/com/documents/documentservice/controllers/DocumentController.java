package com.documents.documentservice.controllers;

import com.documents.documentservice.config.UserContext;
import com.documents.documentservice.dto.ChunkDto;
import com.documents.documentservice.dto.DocumentDto;
import com.documents.documentservice.services.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public ResponseEntity<DocumentDto> upload(@RequestParam("file") MultipartFile file) throws IOException {
        Long userId = UserContext.getUserId();
        DocumentDto doc = documentService.uploadAndProcess(file, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(doc);
    }

    @GetMapping("/")
    public ResponseEntity<List<DocumentDto>> listDocuments() {
        Long userId = UserContext.getUserId();
        return ResponseEntity.ok(documentService.getUserDocuments(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentDto> getDocument(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        return ResponseEntity.ok(documentService.getDocument(id, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        documentService.deleteDocument(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id) throws IOException {
        Long userId = UserContext.getUserId();
        DocumentDto doc = documentService.getDocument(id, userId);
        Resource resource = documentService.getFileResource(id, userId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + doc.getOriginalFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/search")
    public ResponseEntity<List<ChunkDto>> search(
            @RequestParam String query,
            @RequestParam Long userId,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(documentService.searchChunks(query, userId, limit));
    }

    /**
     * Ricerca chunk per similarity vettoriale.
     * Riceve l'embedding già calcolato dal chiamante (es. chat-service).
     */
    @PostMapping("/search/similarity")
    public ResponseEntity<List<ChunkDto>> searchBySimilarity(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "5") int limit,
            @RequestBody String embedding) {
        return ResponseEntity.ok(documentService.searchChunksByEmbedding(embedding, userId, limit));
    }

    /** Admin: lista tutti i documenti di tutti gli utenti. */
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DocumentDto>> getAllDocuments() {
        return ResponseEntity.ok(documentService.getAllDocuments());
    }
}

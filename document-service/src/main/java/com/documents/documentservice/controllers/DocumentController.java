package com.documents.documentservice.controllers;

import com.documents.documentservice.config.UserContext;
import com.documents.documentservice.dto.ChunkDto;
import com.documents.documentservice.dto.DocumentDto;
import com.documents.documentservice.services.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/search")
    public ResponseEntity<List<ChunkDto>> search(
            @RequestParam String query,
            @RequestParam Long userId,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(documentService.searchChunks(query, userId, limit));
    }
}

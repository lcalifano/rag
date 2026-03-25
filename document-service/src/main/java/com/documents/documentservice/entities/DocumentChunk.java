package com.documents.documentservice.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_chunks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    // Embedding vettoriale per similarity search (pgvector).
    // insertable/updatable = false: Hibernate non tocca questa colonna,
    // la gestiamo esclusivamente via native query con cast esplicito a vector.
    // nomic-embed-text produce vettori a 768 dimensioni.
    @Column(name = "embedding", columnDefinition = "vector(768)", insertable = false, updatable = false)
    private String embedding;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

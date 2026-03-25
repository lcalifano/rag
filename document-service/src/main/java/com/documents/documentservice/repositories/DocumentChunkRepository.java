package com.documents.documentservice.repositories;

import com.documents.documentservice.entities.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    List<DocumentChunk> findAllByDocumentId(Long documentId);

    // === VECCHIO: ricerca testuale con ILIKE (non usa embeddings) ===
//    @Query(value = """
//            SELECT dc.* FROM document_chunks dc
//            JOIN documents d ON dc.document_id = d.id
//            WHERE d.user_id = :userId AND d.status = 'READY'
//            AND dc.content ILIKE CONCAT('%%', :query, '%%')
//            LIMIT :limit
//            """, nativeQuery = true)
//    List<DocumentChunk> searchByContentAndUserId(
//            @Param("query") String query,
//            @Param("userId") Long userId,
//            @Param("limit") int limit
//    );

    // === NUOVO: similarity search vettoriale con pgvector (cosine distance) ===
    @Query(value = """
            SELECT dc.* FROM document_chunks dc
            JOIN documents d ON dc.document_id = d.id
            WHERE d.user_id = :userId AND d.status = 'READY'
            AND dc.embedding IS NOT NULL
            ORDER BY dc.embedding <=> cast(:queryEmbedding as vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<DocumentChunk> searchByEmbeddingSimilarity(
            @Param("queryEmbedding") String queryEmbedding,
            @Param("userId") Long userId,
            @Param("limit") int limit
    );

    // Salva l'embedding di un chunk (native query perché JPA non gestisce il tipo vector)
    @Modifying
    @Query(value = "UPDATE document_chunks SET embedding = cast(:embedding as vector) WHERE id = :chunkId",
            nativeQuery = true)
    void updateEmbedding(@Param("chunkId") Long chunkId, @Param("embedding") String embedding);

    void deleteAllByDocumentId(Long documentId);
}

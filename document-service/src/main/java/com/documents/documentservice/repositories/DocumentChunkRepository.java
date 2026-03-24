package com.documents.documentservice.repositories;

import com.documents.documentservice.entities.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    List<DocumentChunk> findAllByDocumentId(Long documentId);

    @Query(value = """
            SELECT dc.* FROM document_chunks dc
            JOIN documents d ON dc.document_id = d.id
            WHERE d.user_id = :userId AND d.status = 'READY'
            AND dc.content ILIKE CONCAT('%%', :query, '%%')
            LIMIT :limit
            """, nativeQuery = true)
    List<DocumentChunk> searchByContentAndUserId(
            @Param("query") String query,
            @Param("userId") Long userId,
            @Param("limit") int limit
    );

    void deleteAllByDocumentId(Long documentId);
}

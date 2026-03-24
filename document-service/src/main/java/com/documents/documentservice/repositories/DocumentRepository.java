package com.documents.documentservice.repositories;

import com.documents.documentservice.entities.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findAllByUserId(Long userId);
    List<Document> findAllByUserIdAndStatus(Long userId, String status);
}

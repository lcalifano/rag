package com.documents.userservice.repositories;

import com.documents.userservice.entities.LlmConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LlmRepository extends JpaRepository<LlmConfig, Long> {
    List<LlmConfig> findAllByUserId(Long userId);
    Optional<LlmConfig> findByUserIdAndIsActiveTrue(Long userId);
//    Boolean existsByUserIdAndProvider(Long userId, String provider);
}

package com.documents.chatservice.repositories;

import com.documents.chatservice.entities.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findAllBySessionIdOrderByCreatedAtAsc(Long sessionId);

    /** Ultimi N messaggi della sessione (ordinati dal più recente). */
    List<ChatMessage> findTop10BySessionIdOrderByCreatedAtDesc(Long sessionId);
}

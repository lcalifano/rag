package com.documents.chatservice.services;

import com.documents.chatservice.config.UserContext;
import com.documents.chatservice.dto.*;
import com.documents.chatservice.entities.ChatMessage;
import com.documents.chatservice.entities.ChatSession;
import com.documents.chatservice.entities.MessageRole;
import com.documents.chatservice.repositories.ChatMessageRepository;
import com.documents.chatservice.repositories.ChatSessionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final AsyncLlmService asyncLlmService;

    private static final String PENDING_CONTENT = "__PENDING__";

    public ChatSessionDto createSession(Long userId, CreateSessionRequest request) {
        ChatSession session = ChatSession.builder()
                .userId(userId)
                .title(request.getTitle() != null ? request.getTitle() : "Nuova chat")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        session = sessionRepository.save(session);
        return toSessionDto(session);
    }

    public List<ChatSessionDto> listSessions(Long userId) {
        return sessionRepository.findAllByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toSessionDto)
                .toList();
    }

    public List<ChatMessageDto> getHistory(Long sessionId, Long userId) {
        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new RuntimeException("Sessione non trovata"));

        return messageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(this::toMessageDto)
                .toList();
    }

    @Transactional
    public ChatMessageDto sendMessage(Long sessionId, Long userId, SendMessageRequest request) {
        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new RuntimeException("Sessione non trovata"));

        // 1. Salva il messaggio dell'utente
        ChatMessage userMessage = ChatMessage.builder()
                .sessionId(session.getId())
                .role(MessageRole.USER)
                .content(request.getMessage())
                .createdAt(LocalDateTime.now())
                .build();
        messageRepository.save(userMessage);

        // 2. Salva un messaggio assistant PENDING (placeholder)
        ChatMessage pendingMessage = ChatMessage.builder()
                .sessionId(session.getId())
                .role(MessageRole.ASSISTANT)
                .content(PENDING_CONTENT)
                .createdAt(LocalDateTime.now())
                .build();
        messageRepository.save(pendingMessage);

        // 3. Cattura il contesto utente prima di passare al thread async
        String userIdStr = UserContext.getUserId() != null ? UserContext.getUserId().toString() : userId.toString();
        String username = UserContext.getUsername();
        String roles = UserContext.getRoles();

        // 4. Lancia l'elaborazione asincrona (embedding + RAG + LLM)
        asyncLlmService.processMessage(
                session.getId(),
                pendingMessage.getId(),
                request.getMessage(),
                userId,
                username,
                roles
        );

        // 5. Ritorna subito il messaggio utente (il frontend farà polling per la risposta)
        return toMessageDto(userMessage);
    }

    public ChatSessionDto updateSession(Long sessionId, Long userId, UpdateSessionRequest request) {
        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new RuntimeException("Sessione non trovata"));

        session.setTitle(request.getTitle());
        session.setUpdatedAt(LocalDateTime.now());
        session = sessionRepository.save(session);
        return toSessionDto(session);
    }

    private ChatSessionDto toSessionDto(ChatSession session) {
        return ChatSessionDto.builder()
                .id(session.getId())
                .title(session.getTitle())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    private ChatMessageDto toMessageDto(ChatMessage message) {
        return ChatMessageDto.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}

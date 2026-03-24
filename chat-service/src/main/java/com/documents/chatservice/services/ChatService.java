package com.documents.chatservice.services;

import com.documents.chatservice.clients.DocumentServiceClient;
import com.documents.chatservice.clients.LlmServiceClient;
import com.documents.chatservice.clients.UserServiceClient;
import com.documents.chatservice.dto.*;
import com.documents.chatservice.entities.ChatMessage;
import com.documents.chatservice.entities.ChatSession;
import com.documents.chatservice.entities.MessageRole;
import com.documents.chatservice.repositories.ChatMessageRepository;
import com.documents.chatservice.repositories.ChatSessionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final UserServiceClient userServiceClient;
    private final DocumentServiceClient documentServiceClient;
    private final LlmServiceClient llmServiceClient;

    @Value("${app.similar-chunks:5}")
    private int similarChunks;

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

        // 2. Recupera la config LLM dell'utente
        Map<String, Object> llmConfig = userServiceClient.getActiveLlmConfig(userId);

        // 3. Cerca chunk rilevanti nei documenti
        List<Map<String, Object>> chunks = documentServiceClient.searchChunks(
                request.getMessage(), userId, similarChunks);

        // 4. Costruisci il contesto dai chunk
        String context = chunks.stream()
                .map(chunk -> (String) chunk.get("content"))
                .collect(Collectors.joining("\n\n---\n\n"));

        // 5. Prepara la richiesta per il LLM Service
        Map<String, Object> llmRequest = new HashMap<>();
        llmRequest.put("provider", llmConfig.get("provider"));
        llmRequest.put("model", llmConfig.get("modelName"));
        llmRequest.put("api_key", llmConfig.get("apiKey"));
        llmRequest.put("base_url", llmConfig.get("ollamaUrl"));
        llmRequest.put("prompt", request.getMessage());
        llmRequest.put("temperature", llmConfig.getOrDefault("temperature", 0.7));
        llmRequest.put("stream", false);

        if (!context.isBlank()) {
            llmRequest.put("context", context);
        }

        // 6. Chiama il LLM Service
        String responseContent = llmServiceClient.generate(llmRequest);

        // 7. Salva la risposta dell'assistente
        ChatMessage assistantMessage = ChatMessage.builder()
                .sessionId(session.getId())
                .role(MessageRole.ASSISTANT)
                .content(responseContent)
                .createdAt(LocalDateTime.now())
                .build();
        messageRepository.save(assistantMessage);

        // 8. Aggiorna il timestamp della sessione
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);

        return toMessageDto(assistantMessage);
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

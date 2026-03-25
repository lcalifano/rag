package com.documents.chatservice.services;

import com.documents.chatservice.clients.DocumentServiceClient;
import com.documents.chatservice.clients.LlmServiceClient;
import com.documents.chatservice.entities.ChatMessage;
import com.documents.chatservice.entities.ChatSession;
import com.documents.chatservice.entities.MessageRole;
import com.documents.chatservice.repositories.ChatMessageRepository;
import com.documents.chatservice.repositories.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncLlmService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final DocumentServiceClient documentServiceClient;
    private final LlmServiceClient llmServiceClient;
    private final SseEmitterService sseEmitterService;

    @Value("${app.similar-chunks:5}")
    private int similarChunks;

    @Value("${app.chat-temperature:0.7}")
    private double chatTemperature;

    @Async("chatTaskExecutor")
    @Transactional
    public void processMessage(Long sessionId, Long pendingMessageId, String userPrompt,
                                Long userId, String username, String roles) {
        try {
            log.info("Inizio elaborazione asincrona per sessione {} messaggio {}", sessionId, pendingMessageId);

            // 1. Recupera gli ultimi 10 messaggi della sessione
            List<ChatMessage> recentMessages = messageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId);
            // Rimuovi il messaggio PENDING dalla lista
            recentMessages.removeIf(m -> m.getId().equals(pendingMessageId));
            Collections.reverse(recentMessages);

            // 2. Compatta la storia per l'embedding contestuale
            StringBuilder queryBuilder = new StringBuilder();
            for (ChatMessage msg : recentMessages) {
                queryBuilder.append(msg.getRole() == MessageRole.USER ? "Utente: " : "Assistente: ");
                queryBuilder.append(msg.getContent()).append("\n");
            }
            String compactedQuery = queryBuilder.toString().trim();

            List<Double> queryEmbedding = llmServiceClient.getEmbeddings(List.of(compactedQuery)).get(0);
            String queryEmbeddingStr = embeddingToString(queryEmbedding);

            // 3. Cerca chunk rilevanti passando gli header utente per auth service-to-service
            List<Map<String, Object>> chunks;
            try {
                // Imposta temporaneamente il contesto utente per il DocumentServiceClient
                com.documents.chatservice.config.UserContext.set(
                        userId.toString(), username, roles);
                chunks = documentServiceClient.searchByEmbeddingSimilarity(
                        queryEmbeddingStr, userId, similarChunks);
            } finally {
                com.documents.chatservice.config.UserContext.clear();
            }

            // 4. Costruisci contesto
            String chatHistory = recentMessages.stream()
                    .map(msg -> (msg.getRole() == MessageRole.USER ? "Utente" : "Assistente") + ": " + msg.getContent())
                    .collect(Collectors.joining("\n"));

            StringBuilder contextBuilder = new StringBuilder();
            String chunkContext = chunks.stream()
                    .map(chunk -> (String) chunk.get("content"))
                    .collect(Collectors.joining("\n\n---\n\n"));

            if (!chunkContext.isBlank()) {
                contextBuilder.append("=== DOCUMENTI RILEVANTI ===\n");
                contextBuilder.append(chunkContext);
            }
            if (!chatHistory.isBlank()) {
                if (!contextBuilder.isEmpty()) contextBuilder.append("\n\n");
                contextBuilder.append("=== CONVERSAZIONE RECENTE ===\n");
                contextBuilder.append(chatHistory);
            }

            // 5. Chiama il LLM
            Map<String, Object> llmRequest = new HashMap<>();
            llmRequest.put("prompt", userPrompt);
            llmRequest.put("temperature", chatTemperature);
            llmRequest.put("stream", false);
            String context = contextBuilder.toString();
            if (!context.isBlank()) {
                llmRequest.put("context", context);
            }

            String responseContent = llmServiceClient.generate(llmRequest);

            // 6. Aggiorna il messaggio PENDING con la risposta
            ChatMessage pending = messageRepository.findById(pendingMessageId)
                    .orElseThrow(() -> new RuntimeException("Messaggio pending non trovato"));
            pending.setContent(responseContent);
            messageRepository.save(pending);

            // 7. Aggiorna timestamp sessione
            sessionRepository.findById(sessionId).ifPresent(session -> {
                session.setUpdatedAt(LocalDateTime.now());
                sessionRepository.save(session);
            });

            // 8. Notifica il frontend via SSE
            Map<String, Object> sseData = new HashMap<>();
            sseData.put("messageId", pendingMessageId);
            sseData.put("content", responseContent);
            sseData.put("role", "ASSISTANT");
            sseEmitterService.sendEvent(sessionId, "message", sseData);
            sseEmitterService.completeEmitter(sessionId);

            log.info("Elaborazione completata per sessione {} messaggio {}", sessionId, pendingMessageId);

        } catch (Exception e) {
            log.error("Errore nell'elaborazione asincrona per sessione {}: {}", sessionId, e.getMessage(), e);

            String errorContent = "Errore nella generazione della risposta. Riprova.";

            // Aggiorna il messaggio PENDING con un messaggio di errore
            messageRepository.findById(pendingMessageId).ifPresent(pending -> {
                pending.setContent(errorContent);
                messageRepository.save(pending);
            });

            // Notifica l'errore via SSE
            Map<String, Object> sseData = new HashMap<>();
            sseData.put("messageId", pendingMessageId);
            sseData.put("content", errorContent);
            sseData.put("role", "ASSISTANT");
            sseData.put("error", true);
            sseEmitterService.sendEvent(sessionId, "message", sseData);
            sseEmitterService.completeEmitter(sessionId);
        }
    }

    private String embeddingToString(List<Double> embedding) {
        return "[" + embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
    }
}

package com.documents.chatservice.controllers;

import com.documents.chatservice.config.UserContext;
import com.documents.chatservice.dto.*;
import com.documents.chatservice.services.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST per la gestione delle sessioni e dei messaggi di chat.
 *
 * L'endpoint SSE /sessions/{id}/stream è stato rimosso: la notifica
 * real-time della risposta del LLM avviene ora tramite WebSocket,
 * gestito da ChatWebSocketHandler su /ws/chat/sessions/{id}.
 *
 * Il flusso rimane asincrono:
 *  1. Il client apre una connessione WebSocket per la sessione attiva.
 *  2. Il client invia il messaggio via POST /sessions/{id}/messages.
 *  3. Il server elabora in modo asincrono (AsyncLlmService).
 *  4. Il server spinge la risposta sul WebSocket quando pronta.
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/sessions")
    public ResponseEntity<ChatSessionDto> createSession(@RequestBody CreateSessionRequest request) {
        Long userId = UserContext.getUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.createSession(userId, request));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionDto>> listSessions() {
        Long userId = UserContext.getUserId();
        return ResponseEntity.ok(chatService.listSessions(userId));
    }

    /**
     * Riceve il messaggio dell'utente, salva immediatamente un messaggio
     * PENDING segnaposto e avvia l'elaborazione asincrona del LLM.
     * Ritorna subito senza aspettare la risposta del LLM.
     * La risposta finale arriva al client via WebSocket.
     */
    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ChatMessageDto> sendMessage(
            @PathVariable Long sessionId,
            @Valid @RequestBody SendMessageRequest request) {
        Long userId = UserContext.getUserId();
        return ResponseEntity.ok(chatService.sendMessage(sessionId, userId, request));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<ChatMessageDto>> getHistory(@PathVariable Long sessionId) {
        Long userId = UserContext.getUserId();
        return ResponseEntity.ok(chatService.getHistory(sessionId, userId));
    }

    @PutMapping("/sessions/{sessionId}")
    public ResponseEntity<ChatSessionDto> updateSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateSessionRequest request) {
        Long userId = UserContext.getUserId();
        return ResponseEntity.ok(chatService.updateSession(sessionId, userId, request));
    }
}

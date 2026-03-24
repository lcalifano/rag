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
}

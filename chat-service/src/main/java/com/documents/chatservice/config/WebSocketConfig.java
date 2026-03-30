package com.documents.chatservice.config;

import com.documents.chatservice.websocket.ChatWebSocketHandler;
import com.documents.chatservice.websocket.WebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Configurazione WebSocket per il chat-service.
 *
 * Registra il ChatWebSocketHandler sul path /ws/chat/sessions/*
 * e aggiunge il WebSocketAuthInterceptor che valida l'identità del
 * client durante l'handshake HTTP → WebSocket, leggendo gli header
 * X-User-* iniettati dal gateway.
 *
 * Il pattern /* permette di matchare qualsiasi sessionId numerico,
 * es. /ws/chat/sessions/42 o /ws/chat/sessions/7.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
            // Registra l'handler su tutti i path delle sessioni chat
            .addHandler(chatWebSocketHandler, "/ws/chat/sessions/*")
            // Interceptor di autenticazione: gira durante l'handshake HTTP iniziale
            .addInterceptors(webSocketAuthInterceptor)
            // Le origini sono controllate dal gateway e da Spring Security;
            // qui si permette tutto per compatibilità con il proxy Vite in dev
            .setAllowedOrigins("*");
    }
}

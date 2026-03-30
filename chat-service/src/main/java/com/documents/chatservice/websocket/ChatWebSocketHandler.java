package com.documents.chatservice.websocket;

import com.documents.chatservice.services.WebSocketSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Handler principale per le connessioni WebSocket della chat.
 *
 * Gestisce il ciclo di vita di ogni connessione WebSocket:
 *  - apertura (afterConnectionEstablished)
 *  - messaggi in ingresso dal client (handleTextMessage)
 *  - chiusura pulita (afterConnectionClosed)
 *  - errori di trasporto (handleTransportError)
 *
 * I messaggi di chat veri e propri vengono inviati dal client via REST
 * (POST /chat/sessions/{id}/messages). Il WebSocket viene usato solo
 * per il canale di PUSH server → client (risposta del LLM).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionService webSocketSessionService;

    /**
     * Chiamato quando il client stabilisce la connessione WebSocket.
     * Estrae il sessionId dal path URI, registra la sessione nella mappa
     * e invia al client un evento "connected" per confermare l'avvenuta connessione.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long sessionId = extractSessionId(session);

        if (sessionId == null) {
            log.warn("WebSocket: impossibile estrarre sessionId dal path '{}'",
                    session.getUri() != null ? session.getUri().getPath() : "null");
            // Chiude con BAD_DATA se il path non è valido
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        // Registra la sessione WebSocket associata a questa chat-session
        webSocketSessionService.registerSession(sessionId, session);

        // Notifica il client che il canale WebSocket è pronto.
        // Il frontend aspetta questo messaggio prima di considerare la connessione attiva.
        session.sendMessage(new TextMessage("{\"event\":\"connected\",\"data\":\"ok\"}"));

        log.info("WebSocket connesso: chat-session={} ws-id={} utente={}",
                sessionId, session.getId(),
                session.getAttributes().get(WebSocketAuthInterceptor.ATTR_USERNAME));
    }

    /**
     * Chiamato quando il client invia un messaggio testuale via WebSocket.
     * L'unico messaggio atteso è "ping" per il keepalive — il server risponde "pong".
     * Tutto il resto viene ignorato (i messaggi di chat passano via REST).
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload().trim();

        // Gestione ping/pong per mantenere viva la connessione
        if ("ping".equalsIgnoreCase(payload)) {
            try {
                session.sendMessage(new TextMessage("pong"));
            } catch (Exception e) {
                log.debug("Errore risposta ping per ws-id={}: {}", session.getId(), e.getMessage());
            }
        }
        // Qualsiasi altro messaggio viene ignorato
    }

    /**
     * Chiamato quando la connessione WebSocket viene chiusa (lato client o server).
     * Rimuove la sessione dalla mappa delle connessioni attive.
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long sessionId = extractSessionId(session);
        if (sessionId != null) {
            webSocketSessionService.removeSession(sessionId);
            log.info("WebSocket chiuso: chat-session={} ws-id={} status={}", sessionId, session.getId(), status);
        }
    }

    /**
     * Chiamato in caso di errori di trasporto (es. connessione di rete caduta).
     * Rimuove la sessione dalla mappa per evitare tentativi di invio su connessioni morte.
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long sessionId = extractSessionId(session);
        log.warn("WebSocket errore trasporto: chat-session={} ws-id={} errore={}",
                sessionId, session.getId(), exception.getMessage());
        if (sessionId != null) {
            webSocketSessionService.removeSession(sessionId);
        }
    }

    /**
     * Estrae il sessionId numerico dall'ultimo segmento del path URI.
     * Esempio: /ws/chat/sessions/42 → 42L
     * Restituisce null se il path non è valido o non contiene un numero.
     */
    private Long extractSessionId(WebSocketSession session) {
        if (session.getUri() == null) return null;
        String path = session.getUri().getPath();
        if (path == null || path.isBlank()) return null;
        try {
            String[] parts = path.split("/");
            return Long.parseLong(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

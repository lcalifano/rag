package com.documents.chatservice.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Interceptor eseguito durante l'handshake HTTP → WebSocket.
 *
 * Il gateway ha già validato il ticket SSE e ha iniettato gli header
 * X-User-Id, X-User-Username, X-User-Roles nella richiesta HTTP.
 * Questo interceptor li legge e li salva come attributi della sessione
 * WebSocket, rendendoli disponibili nell'handler durante tutta la
 * durata della connessione.
 */
@Component
@Slf4j
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    // Chiavi costanti per gli attributi di sessione WebSocket
    public static final String ATTR_USER_ID   = "userId";
    public static final String ATTR_USERNAME  = "username";
    public static final String ATTR_ROLES     = "roles";

    /**
     * Eseguito PRIMA che l'handshake WebSocket venga completato.
     * Restituisce false per rifiutare la connessione se mancano gli header
     * di autenticazione (significa che la richiesta non è passata dal gateway).
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        String userId   = request.getHeaders().getFirst("X-User-Id");
        String username = request.getHeaders().getFirst("X-User-Username");
        String roles    = request.getHeaders().getFirst("X-User-Roles");

        // Se mancano gli header il gateway non ha validato la richiesta — rifiuta
        if (userId == null || userId.isBlank() || username == null || username.isBlank()) {
            log.warn("WebSocket handshake rifiutato: header X-User-* mancanti (accesso diretto senza gateway?)");
            return false;
        }

        // Salva il contesto utente come attributi della sessione WebSocket.
        // L'handler li recupera tramite session.getAttributes().
        attributes.put(ATTR_USER_ID,  userId);
        attributes.put(ATTR_USERNAME, username);
        attributes.put(ATTR_ROLES,    roles != null ? roles : "");

        log.info("WebSocket handshake autorizzato per utente '{}' (id={})", username, userId);
        return true;
    }

    /**
     * Eseguito DOPO il completamento dell'handshake.
     * Non serve alcuna operazione in questo caso.
     */
    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // Nessuna operazione necessaria post-handshake
    }
}

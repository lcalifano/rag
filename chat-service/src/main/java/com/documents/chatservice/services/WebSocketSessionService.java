package com.documents.chatservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestisce il registro delle sessioni WebSocket attive.
 *
 * Mantiene una mappa chatSessionId → WebSocketSession per permettere
 * all'AsyncLlmService (che gira in un thread asincrono separato) di
 * spedire la risposta del LLM al client corretto una volta pronta.
 *
 * Thread-safe: ConcurrentHashMap per la mappa, synchronized sulla
 * singola WebSocketSession durante l'invio per evitare invii concorrenti.
 */
@Service
@Slf4j
public class WebSocketSessionService {

    // Mappa: id della chat-session → sessione WebSocket del client connesso
    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // Serializzatore JSON condiviso (thread-safe)
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Registra una nuova connessione WebSocket per una chat-session.
     * Se esiste già una connessione aperta per la stessa sessione (es. tab
     * duplicata), la chiude prima di registrare la nuova.
     *
     * @param chatSessionId id della chat-session nel DB
     * @param wsSession     sessione WebSocket appena aperta dal client
     */
    public void registerSession(Long chatSessionId, WebSocketSession wsSession) {
        // Sostituisce l'eventuale sessione precedente e la chiude se ancora aperta
        WebSocketSession existing = sessions.put(chatSessionId, wsSession);
        if (existing != null && existing.isOpen()) {
            try {
                existing.close();
                log.info("Chiusa sessione WS precedente per chat-session {}", chatSessionId);
            } catch (Exception e) {
                log.debug("Errore chiusura sessione WS precedente per chat-session {}: {}",
                        chatSessionId, e.getMessage());
            }
        }
        log.info("Registrata sessione WS {} per chat-session {}", wsSession.getId(), chatSessionId);
    }

    /**
     * Invia un messaggio JSON al client collegato alla chat-session indicata.
     *
     * Il payload viene strutturato come:
     * { "event": "<eventName>", "data": <data> }
     *
     * Se la sessione non esiste o è chiusa, l'invio viene ignorato
     * (il client farà fallback al polling della history via REST).
     *
     * L'invio è sincronizzato sulla sessione WebSocket per evitare
     * che thread concorrenti inviino messaggi in parallelo sulla stessa
     * connessione, il che causerebbe eccezioni.
     *
     * @param chatSessionId id della chat-session destinataria
     * @param eventName     nome dell'evento (es. "message", "error")
     * @param data          payload dati da serializzare in JSON
     */
    public void sendMessage(Long chatSessionId, String eventName, Object data) {
        WebSocketSession session = sessions.get(chatSessionId);

        // Se il client si è disconnesso prima che la risposta fosse pronta, skip
        if (session == null || !session.isOpen()) {
            log.warn("WebSocket sendMessage: nessuna sessione attiva per chat-session {}", chatSessionId);
            return;
        }

        try {
            // Costruisce l'envelope JSON: { "event": "...", "data": {...} }
            Map<String, Object> envelope = Map.of(
                    "event", eventName,
                    "data",  data
            );
            String json = objectMapper.writeValueAsString(envelope);

            // Sincronizzato per prevenire invii concorrenti sulla stessa sessione
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }

            log.info("WebSocket evento '{}' inviato per chat-session {}", eventName, chatSessionId);

        } catch (Exception e) {
            log.warn("Errore invio WebSocket per chat-session {}: {}", chatSessionId, e.getMessage());
            // Rimuove la sessione rotta dalla mappa
            removeSession(chatSessionId);
        }
    }

    /**
     * Rimuove la sessione dalla mappa quando la connessione viene chiusa.
     * Chiamato sia dall'handler (afterConnectionClosed/handleTransportError)
     * che internamente in caso di errore di invio.
     *
     * @param chatSessionId id della chat-session da rimuovere
     */
    public void removeSession(Long chatSessionId) {
        WebSocketSession removed = sessions.remove(chatSessionId);
        if (removed != null) {
            log.info("Rimossa sessione WS per chat-session {}", chatSessionId);
        }
    }
}

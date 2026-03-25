package com.documents.chatservice.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SseEmitterService {

    // Mappa: sessionId -> SseEmitter attivo
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(Long sessionId) {
        // Timeout di 5 minuti per la connessione SSE
        SseEmitter emitter = new SseEmitter(300_000L);

        emitter.onCompletion(() -> {
            log.debug("SSE completato per sessione {}", sessionId);
            emitters.remove(sessionId);
        });
        emitter.onTimeout(() -> {
            log.debug("SSE timeout per sessione {}", sessionId);
            emitters.remove(sessionId);
        });
        emitter.onError(e -> {
            log.debug("SSE errore per sessione {}: {}", sessionId, e.getMessage());
            emitters.remove(sessionId);
        });

        emitters.put(sessionId, emitter);
        return emitter;
    }

    public void sendEvent(Long sessionId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
        } catch (Exception e) {
            log.warn("Errore invio SSE per sessione {}: {}", sessionId, e.getMessage());
            emitters.remove(sessionId);
        }
    }

    public void completeEmitter(Long sessionId) {
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                // ignore
            }
        }
    }
}

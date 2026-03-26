package com.documents.chatservice.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

@Service
@Slf4j
public class SseEmitterService {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledFuture<?>> heartbeats = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public SseEmitter createEmitter(Long sessionId) {
        // Rimuovi eventuale emitter precedente per questa sessione
        completeEmitter(sessionId);

        SseEmitter emitter = new SseEmitter(300_000L);

        emitter.onCompletion(() -> {
            log.info("SSE onCompletion per sessione {}", sessionId);
            cleanup(sessionId);
        });
        emitter.onTimeout(() -> {
            log.info("SSE onTimeout per sessione {}", sessionId);
            cleanup(sessionId);
        });
        emitter.onError(e -> {
            log.info("SSE onError per sessione {}: {}", sessionId, e.getMessage());
            cleanup(sessionId);
        });

        emitters.put(sessionId, emitter);

        // Invia un evento iniziale per "attivare" la connessione
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("ok"));
            log.info("SSE emitter creato e evento iniziale inviato per sessione {}", sessionId);
        } catch (IOException e) {
            log.warn("Errore invio evento iniziale SSE per sessione {}: {}", sessionId, e.getMessage());
            cleanup(sessionId);
            return emitter;
        }

        // Avvia heartbeat ogni 15 secondi per mantenere viva la connessione
        ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(() -> {
            SseEmitter em = emitters.get(sessionId);
            if (em == null) return;
            try {
                em.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception e) {
                log.debug("Heartbeat fallito per sessione {}, rimuovo emitter", sessionId);
                cleanup(sessionId);
            }
        }, 15, 15, TimeUnit.SECONDS);
        heartbeats.put(sessionId, heartbeat);

        return emitter;
    }

    public void sendEvent(Long sessionId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) {
            log.warn("SSE sendEvent: nessun emitter trovato per sessione {}", sessionId);
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
            log.info("SSE evento '{}' inviato per sessione {}", eventName, sessionId);
        } catch (Exception e) {
            log.warn("Errore invio SSE per sessione {}: {}", sessionId, e.getMessage());
            cleanup(sessionId);
        }
    }

    public void completeEmitter(Long sessionId) {
        cleanup(sessionId);
        // complete() viene chiamato dopo cleanup per evitare che onCompletion ri-chiami cleanup
    }

    private void cleanup(Long sessionId) {
        // Ferma il heartbeat
        ScheduledFuture<?> heartbeat = heartbeats.remove(sessionId);
        if (heartbeat != null) {
            heartbeat.cancel(false);
        }

        // Rimuovi e completa l'emitter
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                // ignore — l'emitter potrebbe essere già chiuso
            }
        }
    }
}

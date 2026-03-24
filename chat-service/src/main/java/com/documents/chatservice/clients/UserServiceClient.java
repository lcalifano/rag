package com.documents.chatservice.clients;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceClient {

    private final WebClient.Builder loadBalancedWebClientBuilder;

    @SuppressWarnings("unchecked")
    public Map<String, Object> getActiveLlmConfig(Long userId) {
        try {
            return loadBalancedWebClientBuilder.build()
                    .get()
                    .uri("http://USER-SERVICE/internal/users/{userId}/active-llm-config", userId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.error("Errore nel recupero della configurazione LLM per userId {}: {}", userId, e.getMessage());
            throw new RuntimeException("Impossibile recuperare la configurazione LLM dell'utente", e);
        }
    }
}

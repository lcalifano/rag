package com.documents.chatservice.clients;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class LlmServiceClient {

    private final WebClient llmServiceWebClient;

    @SuppressWarnings("unchecked")
    public String generate(Map<String, Object> request) {
        try {
            Map<String, Object> response = llmServiceWebClient
                    .post()
                    .uri("/generate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return response != null ? (String) response.get("content") : "Errore nella generazione della risposta";
        } catch (Exception e) {
            log.error("Errore nella chiamata al LLM Service: {}", e.getMessage());
            throw new RuntimeException("Errore nella generazione della risposta", e);
        }
    }
}

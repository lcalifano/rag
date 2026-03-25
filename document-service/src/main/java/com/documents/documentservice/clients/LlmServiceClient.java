package com.documents.documentservice.clients;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
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

    /**
     * Chiama il LLM Service per ottenere gli embeddings dei testi forniti.
     * Restituisce una lista di vettori (uno per ogni testo).
     */
    @SuppressWarnings("unchecked")
    public List<List<Double>> getEmbeddings(List<String> texts) {
        try {
            Map<String, Object> request = Map.of("texts", texts);

            Map<String, Object> response = llmServiceWebClient
                    .post()
                    .uri("/embeddings")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null || !response.containsKey("embeddings")) {
                throw new RuntimeException("Risposta embedding vuota");
            }

            return (List<List<Double>>) response.get("embeddings");
        } catch (Exception e) {
            log.error("Errore nella generazione degli embeddings: {}", e.getMessage());
            throw new RuntimeException("Errore nella generazione degli embeddings", e);
        }
    }

    public List<Double> getSingleEmbedding(String text) {
        try {
            Map<String, Object> request = Map.of("text", text);

            Map<String, Object> response = llmServiceWebClient
                    .post()
                    .uri("/embedding")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null || !response.containsKey("embeddings")) {
                throw new RuntimeException("Risposta embedding vuota");
            }

            return (List<Double>) response.get("embeddings");
        } catch (Exception e) {
            log.error("Errore nella generazione degli embeddings: {}", e.getMessage());
            throw new RuntimeException("Errore nella generazione degli embeddings", e);
        }
    }


}

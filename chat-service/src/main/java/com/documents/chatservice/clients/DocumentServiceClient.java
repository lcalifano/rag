package com.documents.chatservice.clients;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceClient {

    private final WebClient.Builder loadBalancedWebClientBuilder;

    public List<Map<String, Object>> searchChunks(String query, Long userId, int limit) {
        try {
            return loadBalancedWebClientBuilder.build()
                    .get()
                    .uri("http://DOCUMENT-SERVICE/documents/search?query={query}&userId={userId}&limit={limit}",
                            query, userId, limit)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();
        } catch (Exception e) {
            log.warn("Errore nella ricerca dei chunk: {}. Procedo senza contesto.", e.getMessage());
            return Collections.emptyList();
        }
    }
}

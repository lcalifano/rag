package com.documents.chatservice.clients;

import com.documents.chatservice.config.UserContext;
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

    // === VECCHIO: ricerca testuale ===
    // public List<Map<String, Object>> searchChunks(String query, Long userId, int limit) {
    //     try {
    //         return loadBalancedWebClientBuilder.build()
    //                 .get()
    //                 .uri("http://DOCUMENT-SERVICE/documents/search?query={query}&userId={userId}&limit={limit}",
    //                         query, userId, limit)
    //                 .header("X-User-Id", UserContext.getUserId().toString())
    //                 .header("X-User-Username", UserContext.getUsername())
    //                 .header("X-User-Roles", UserContext.getRoles())
    //                 .retrieve()
    //                 .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
    //                 .block();
    //     } catch (Exception e) {
    //         log.warn("Errore nella ricerca dei chunk: {}. Procedo senza contesto.", e.getMessage());
    //         return Collections.emptyList();
    //     }
    // }

    /**
     * Ricerca chunk per similarity vettoriale.
     * Invia l'embedding già calcolato al document-service via POST.
     * Propaga gli header X-User-* per l'autenticazione service-to-service.
     */
    public List<Map<String, Object>> searchByEmbeddingSimilarity(String embedding, Long userId, int limit) {
        try {
            return loadBalancedWebClientBuilder.build()
                    .post()
                    .uri("http://DOCUMENT-SERVICE/documents/search/similarity?userId={userId}&limit={limit}",
                            userId, limit)
                    .header("X-User-Id", UserContext.getUserId().toString())
                    .header("X-User-Username", UserContext.getUsername())
                    .header("X-User-Roles", UserContext.getRoles())
                    .bodyValue(embedding)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();
        } catch (Exception e) {
            log.warn("Errore nella ricerca similarity dei chunk: {}. Procedo senza contesto.", e.getMessage());
            return Collections.emptyList();
        }
    }
}

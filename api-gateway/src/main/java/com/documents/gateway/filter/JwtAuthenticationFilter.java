package com.documents.gateway.filter;

import com.documents.gateway.config.JwtValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtValidationService jwtValidationService;
    private final ReactiveStringRedisTemplate redisTemplate;

    private static final List<String> OPEN_ENDPOINTS = List.of(
            "/auth/register",
            "/auth/login",
            "/auth/setup-status",
            "/auth/refresh",
            "/actuator",
            "/oauth2/authorization",
            "/login/oauth2"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isOpenEndpoint(path)) {
            return chain.filter(exchange);
        }

        // Fix 3: SSE ticket monouso — se c'è ?ticket=xxx lo validiamo via Redis
        String ticket = exchange.getRequest().getQueryParams().getFirst("ticket");
        if (ticket != null && !ticket.isBlank()) {
            return handleSseTicket(ticket, exchange, chain);
        }

        // Fix 2: Bearer JWT con blacklist Redis
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        String token = authHeader.substring(7);

        try {
            if (!jwtValidationService.isTokenValid(token)) {
                return unauthorized(exchange);
            }

            String jti = jwtValidationService.extractJti(token);

            // Controlla blacklist Redis prima di procedere
            return redisTemplate.hasKey("blacklist:" + jti)
                    .flatMap(blacklisted -> {
                        if (Boolean.TRUE.equals(blacklisted)) {
                            return unauthorized(exchange);
                        }
                        return proceedWithToken(token, exchange, chain);
                    });

        } catch (Exception e) {
            log.error("JWT validation failed: {}", e.getMessage());
            return unauthorized(exchange);
        }
    }

    // Valida il ticket SSE monouso da Redis — getAndDelete è atomico
    private Mono<Void> handleSseTicket(String ticket, ServerWebExchange exchange, GatewayFilterChain chain) {
        String redisKey = "sse_ticket:" + ticket;
        return redisTemplate.opsForValue().getAndDelete(redisKey)
                .flatMap(value -> {
                    if (value == null) {
                        log.warn("SSE ticket non valido o già usato: {}", ticket);
                        return unauthorized(exchange);
                    }
                    // value = "userId|username|roles"
                    String[] parts = value.split("\\|", 3);
                    if (parts.length < 3) {
                        return unauthorized(exchange);
                    }
                    ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                            .header("X-User-Id", parts[0])
                            .header("X-User-Username", parts[1])
                            .header("X-User-Roles", parts[2])
                            .build();
                    return chain.filter(exchange.mutate().request(modifiedRequest).build());
                })
                .switchIfEmpty(Mono.defer(() -> unauthorized(exchange)));
    }

    private Mono<Void> proceedWithToken(String token, ServerWebExchange exchange, GatewayFilterChain chain) {
        String username = jwtValidationService.extractUsername(token);
        Long userId = jwtValidationService.extractUserId(token);
        List<String> roles = jwtValidationService.extractRoles(token);

        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", String.valueOf(userId))
                .header("X-User-Username", username)
                .header("X-User-Roles", String.join(",", roles))
                .build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private boolean isOpenEndpoint(String path) {
        return OPEN_ENDPOINTS.stream().anyMatch(path::startsWith);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}

# Security Fixes — Implementazione pratica

## Indice
1. [Infrastruttura — Redis in docker-compose](#1-redis-in-docker-compose)
2. [Fix 1 — Access token breve + Refresh Token (user-service)](#2-fix-1--user-service)
3. [Fix 2 — Blacklist Redis + logout (api-gateway)](#3-fix-2--api-gateway)
4. [Fix 3 — SSE Ticket monouso](#4-fix-3--sse-ticket)

---

## 1. Redis in docker-compose

Aggiungere il servizio Redis e collegarlo a `api-gateway` e `user-service`.

```yaml
# docker-compose.yml — aggiungere tra i servizi

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  api-gateway:
    # ... esistente ...
    environment:
      JWT_SECRET: ${JWT_SECRET}
      EUREKA_URL: http://eureka-server:8761/eureka/
      REDIS_HOST: redis        # <-- aggiungere
    depends_on:
      eureka-server:
        condition: service_healthy
      redis:                   # <-- aggiungere
        condition: service_healthy

  user-service:
    # ... esistente ...
    environment:
      JWT_SECRET: ${JWT_SECRET}
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}
      EUREKA_URL: http://eureka-server:8761/eureka/
      DB_URL: jdbc:postgresql://postgres:5432/users_db
      REDIS_HOST: redis        # <-- aggiungere
    depends_on:
      postgres:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
      redis:                   # <-- aggiungere
        condition: service_healthy
```

---

## 2. Fix 1 — user-service

### 2.1 pom.xml — aggiungere dipendenze Redis

```xml
<!-- Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### 2.2 application.yaml — nuovi valori

```yaml
# user-service/src/main/resources/application.yaml

jwt:
  secret: ${JWT_SECRET:my-super-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256}
  expiration-ms: 900000          # 15 minuti (era 86400000 = 24h)
  refresh-expiration-ms: 604800000  # 7 giorni

spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
```

### 2.3 RefreshToken.java — nuova entity

```java
// user-service/.../entities/RefreshToken.java
package com.documents.userservice.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;
}
```

### 2.4 RefreshTokenRepository.java

```java
// user-service/.../repositories/RefreshTokenRepository.java
package com.documents.userservice.repositories;

import com.documents.userservice.entities.RefreshToken;
import com.documents.userservice.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    // Revoca tutti i refresh token di un utente (logout da tutti i dispositivi)
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user = :user")
    void revokeAllByUser(User user);
}
```

### 2.5 JwtService.java — aggiungere jti e refresh token

```java
// user-service/.../config/JwtService.java
package com.documents.userservice.config;

import com.documents.userservice.entities.Role;
import com.documents.userservice.entities.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration-ms}")
    private long expirationTime;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    /**
     * Genera l'access token (15 min).
     * Contiene jti (JWT ID) univoco — usato dalla blacklist del gateway.
     */
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("roles", user.getRoles().stream().map(Role::getName).toList());
        claims.put("jti", UUID.randomUUID().toString()); // <-- NUOVO
        return buildToken(claims, user.getUsername(), expirationTime);
    }

    private String buildToken(Map<String, Object> claims, String subject, long expiration) {
        Date now = new Date();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractJti(String token) {
        return extractClaim(token, claims -> claims.get("jti", String.class));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        return extractUsername(token).equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
```

### 2.6 RefreshTokenService.java — nuovo servizio

```java
// user-service/.../services/RefreshTokenService.java
package com.documents.userservice.services;

import com.documents.userservice.entities.RefreshToken;
import com.documents.userservice.entities.User;
import com.documents.userservice.repositories.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        // Revoca tutti i precedenti prima di crearne uno nuovo (rotation)
        refreshTokenRepository.revokeAllByUser(user);

        RefreshToken token = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .build();

        return refreshTokenRepository.save(token);
    }

    /**
     * Verifica il refresh token e restituisce l'utente associato.
     * Lancia eccezione se non valido, scaduto o revocato.
     */
    @Transactional
    public User validateAndRotate(String tokenValue, RefreshTokenRepository repo, JwtService jwtService,
                                  com.documents.userservice.config.JwtService jwtSvc) {
        // usare il metodo sotto direttamente
        throw new UnsupportedOperationException("usa validate()");
    }

    @Transactional
    public RefreshToken validate(String tokenValue) {
        RefreshToken token = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token non valido"));

        if (token.isRevoked()) {
            throw new IllegalArgumentException("Refresh token revocato");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            throw new IllegalArgumentException("Refresh token scaduto");
        }
        return token;
    }

    @Transactional
    public void revokeAll(User user) {
        refreshTokenRepository.revokeAllByUser(user);
    }
}
```

### 2.7 AuthService.java — aggiungere refresh e logout

Sostituire l'intera classe:

```java
// user-service/.../services/AuthService.java
package com.documents.userservice.services;

import com.documents.userservice.config.JwtService;
import com.documents.userservice.dto.AuthResponse;
import com.documents.userservice.dto.LoginRequest;
import com.documents.userservice.dto.RegisterRequest;
import com.documents.userservice.entities.RefreshToken;
import com.documents.userservice.entities.Role;
import com.documents.userservice.entities.User;
import com.documents.userservice.exceptions.UserAlreadyExistsException;
import com.documents.userservice.exceptions.UserNotFoundException;
import com.documents.userservice.repositories.RefreshTokenRepository;
import com.documents.userservice.repositories.RoleRepository;
import com.documents.userservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final StringRedisTemplate redisTemplate;

    public boolean isSetupCompleted() {
        return userRepository.count() > 0;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername()))
            throw new UserAlreadyExistsException("Username già utilizzato: " + request.getUsername());
        if (userRepository.existsByEmail(request.getEmail()))
            throw new UserAlreadyExistsException("Email già utilizzata: " + request.getEmail());

        boolean isFirstUser = userRepository.count() == 0;

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").build()));

        List<Role> roles = new ArrayList<>(List.of(userRole));
        if (isFirstUser) {
            Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                    .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_ADMIN").build()));
            roles.add(adminRole);
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(roles)
                .build();
        userRepository.save(user);

        return buildAuthResponse(user,
                isFirstUser ? "Setup completato! Sei stato registrato come amministratore."
                            : "Registrazione completata con successo");
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (Exception e) {
            throw new UserNotFoundException("Credenziali non valide");
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UserNotFoundException("Utente non trovato"));

        return buildAuthResponse(user, "Login effettuato con successo");
    }

    /**
     * Ruota il refresh token e restituisce un nuovo access token + refresh token.
     */
    public AuthResponse refresh(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenService.validate(refreshTokenValue);
        User user = refreshToken.getUser();

        // Invalida il vecchio refresh token e crea uno nuovo (rotation)
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        return buildAuthResponse(user, "Token rinnovato");
    }

    /**
     * Logout: aggiunge il jti dell'access token alla blacklist Redis
     * e revoca tutti i refresh token dell'utente.
     */
    public void logout(String accessToken, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("Utente non trovato"));

        // Blacklist dell'access token su Redis
        try {
            String jti = jwtService.extractJti(accessToken);
            Date expiration = jwtService.extractExpiration(accessToken);
            long ttlSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            if (ttlSeconds > 0) {
                redisTemplate.opsForValue().set(
                        "blacklist:" + jti,
                        "1",
                        Duration.ofSeconds(ttlSeconds)
                );
            }
        } catch (Exception ignored) {
            // Token già scaduto o malformato — non serve blacklistarlo
        }

        // Revoca tutti i refresh token
        refreshTokenService.revokeAll(user);
    }

    /**
     * Genera un ticket monouso per SSE, valido 30 secondi.
     * Il ticket viene salvato in Redis con le claim dell'utente.
     */
    public String generateSseTicket(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("Utente non trovato"));

        String ticket = UUID.randomUUID().toString();
        String roles = String.join(",", user.getRoles().stream()
                .map(Role::getName).toList());

        // Salviamo userId|username|roles come valore — il gateway legge queste info
        String value = user.getId() + "|" + user.getUsername() + "|" + roles;
        redisTemplate.opsForValue().set("sse_ticket:" + ticket, value, Duration.ofSeconds(30));

        return ticket;
    }

    // --- helper ---

    private AuthResponse buildAuthResponse(User user, String message) {
        String accessToken = jwtService.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        return AuthResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken.getToken())
                .username(user.getUsername())
                .email(user.getEmail())
                .message(message)
                .build();
    }
}
```

### 2.8 AuthResponse.java — aggiungere refreshToken

```java
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AuthResponse {
    private String token;
    private String refreshToken;   // <-- NUOVO
    private String username;
    private String email;
    private String message;
}
```

### 2.9 RefreshRequest.java — nuovo DTO

```java
// user-service/.../dto/RefreshRequest.java
package com.documents.userservice.dto;

import lombok.Data;

@Data
public class RefreshRequest {
    private String refreshToken;
}
```

### 2.10 AuthController.java — aggiungere /refresh, /logout, /sse-ticket

```java
// user-service/.../controllers/AuthController.java
package com.documents.userservice.controllers;

import com.documents.userservice.dto.*;
import com.documents.userservice.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/setup-status")
    public ResponseEntity<Map<String, Boolean>> setupStatus() {
        return ResponseEntity.ok(Map.of("setupCompleted", authService.isSetupCompleted()));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Rinnova access token tramite refresh token. Endpoint pubblico (no JWT richiesto). */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    /** Logout: revoca access token (blacklist) e tutti i refresh token. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authHeader,
            Authentication authentication) {
        String token = authHeader.substring(7);
        authService.logout(token, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /**
     * Genera un ticket monouso (30 sec) per aprire la connessione SSE
     * senza passare il JWT nell'URL.
     * Richiede autenticazione normale via Bearer token.
     */
    @GetMapping("/sse-ticket")
    public ResponseEntity<Map<String, String>> sseTicket(Authentication authentication) {
        String ticket = authService.generateSseTicket(authentication.getName());
        return ResponseEntity.ok(Map.of("ticket", ticket));
    }
}
```

### 2.11 SecurityConfig.java — aggiungere /auth/refresh e /auth/sse-ticket

```java
// aggiungere alle permitAll:
.requestMatchers("/auth/register", "/auth/login", "/auth/setup-status", "/auth/refresh").permitAll()
// /auth/sse-ticket rimane protetto — richiede Bearer token valido
```

---

## 3. Fix 2 — api-gateway

### 3.1 pom.xml — aggiungere Redis reattivo

```xml
<!-- Redis (versione reattiva per Spring WebFlux / Gateway) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
</dependency>
```

### 3.2 application.yaml — aggiungere Redis

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
```

### 3.3 JwtValidationService.java — aggiungere estrazione jti

Aggiungere questo metodo alla classe esistente:

```java
public String extractJti(String token) {
    return extractAllClaims(token).get("jti", String.class);
}
```

### 3.4 JwtAuthenticationFilter.java — blacklist + SSE ticket

Sostituire l'intera classe:

```java
// api-gateway/.../filter/JwtAuthenticationFilter.java
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
            "/actuator"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isOpenEndpoint(path)) {
            return chain.filter(exchange);
        }

        // --- Fix 3: SSE ticket ---
        // Se c'è un ?ticket=xxx, lo validiamo e costruiamo la request con gli header utente
        String ticket = exchange.getRequest().getQueryParams().getFirst("ticket");
        if (ticket != null && !ticket.isBlank()) {
            return handleSseTicket(ticket, exchange, chain);
        }

        // --- Fix 2: Bearer JWT con blacklist ---
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

            // Controlla blacklist Redis
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

    /**
     * Valida il ticket SSE monouso da Redis.
     * Il ticket viene consumato (deleted) al primo utilizzo.
     */
    private Mono<Void> handleSseTicket(String ticket, ServerWebExchange exchange, GatewayFilterChain chain) {
        String redisKey = "sse_ticket:" + ticket;
        return redisTemplate.opsForValue().getAndDelete(redisKey)  // atomico: legge e cancella
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
```

---

## 4. Fix 3 — SSE Ticket (frontend)

### Cambio nel frontend

Prima (non sicuro — JWT nell'URL):
```javascript
const eventSource = new EventSource(
    `/chat/sessions/${sessionId}/stream?token=${jwt}`
);
```

Dopo (ticket monouso a 30 sec):
```javascript
// 1. Ottieni ticket fresco
const { ticket } = await fetch('/auth/sse-ticket', {
    headers: { Authorization: `Bearer ${accessToken}` }
}).then(r => r.json());

// 2. Usa il ticket per aprire SSE — viene consumato al primo request
const eventSource = new EventSource(
    `/chat/sessions/${sessionId}/stream?ticket=${ticket}`
);
```

---

## Flusso completo post-fix

```
LOGIN
  → POST /auth/login
  ← { token: "eyJ..." (15 min), refreshToken: "uuid-v4" (7 giorni) }

CHIAMATA NORMALE
  → Authorization: Bearer eyJ...
  ← Gateway verifica JWT + controlla Redis blacklist su jti
  ← Se OK → propaga X-User-* headers

RINNOVO TOKEN (quando access token scade)
  → POST /auth/refresh { refreshToken: "uuid-v4" }
  ← { token: "nuovo eyJ...", refreshToken: "nuovo uuid-v4" }  (rotation)

LOGOUT
  → POST /auth/logout (Authorization: Bearer eyJ...)
  ← Gateway scrive "blacklist:jti" su Redis (TTL = vita restante del token)
  ← user-service revoca tutti i refresh token sul DB

SSE
  → GET /auth/sse-ticket (Authorization: Bearer eyJ...)  ← richiede auth normale
  ← { ticket: "uuid-v4" }  (valido 30 secondi su Redis)
  → GET /chat/sessions/1/stream?ticket=uuid-v4
  ← Gateway legge e cancella il ticket da Redis (getAndDelete — atomico)
  ← SSE stream aperto, ticket non più riutilizzabile
```

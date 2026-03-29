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

    // Rinnova access token tramite refresh token. Endpoint pubblico (no JWT richiesto).
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    // Logout: revoca access token (blacklist Redis) e tutti i refresh token.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authHeader,
            Authentication authentication) {
        String token = authHeader.substring(7);
        authService.logout(token, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    // Genera un ticket monouso (30 sec) per aprire la connessione SSE
    // senza passare il JWT nell'URL. Richiede autenticazione via Bearer token.
    @GetMapping("/sse-ticket")
    public ResponseEntity<Map<String, String>> sseTicket(Authentication authentication) {
        String ticket = authService.generateSseTicket(authentication.getName());
        return ResponseEntity.ok(Map.of("ticket", ticket));
    }
}

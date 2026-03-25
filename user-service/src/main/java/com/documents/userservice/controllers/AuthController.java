package com.documents.userservice.controllers;

import com.documents.userservice.dto.AuthResponse;
import com.documents.userservice.dto.LoginRequest;
import com.documents.userservice.dto.RegisterRequest;
import com.documents.userservice.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    /**
     * Controlla se il sistema è già stato configurato (esiste almeno un utente).
     * Endpoint pubblico, usato dal frontend per mostrare la pagina di setup.
     */
    @GetMapping("/setup-status")
    public ResponseEntity<Map<String, Boolean>> setupStatus() {
        return ResponseEntity.ok(Map.of("setupCompleted", authService.isSetupCompleted()));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}

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

    // Ruota il refresh token e restituisce un nuovo access token + refresh token
    public AuthResponse refresh(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenService.validate(refreshTokenValue);
        User user = refreshToken.getUser();

        // Invalida il vecchio refresh token e crea uno nuovo (rotation)
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        return buildAuthResponse(user, "Token rinnovato");
    }

    // Logout: blacklist dell'access token su Redis + revoca di tutti i refresh token
    public void logout(String accessToken, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("Utente non trovato"));

        // Blacklist dell'access token su Redis (TTL = vita restante del token)
        try {
            String jti = jwtService.extractJti(accessToken);
            Date expiration = jwtService.extractExpiration(accessToken);
            long ttlSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            if (ttlSeconds > 0) {
                redisTemplate.opsForValue().set("blacklist:" + jti, "1", Duration.ofSeconds(ttlSeconds));
            }
        } catch (Exception ignored) {
            // Token già scaduto o malformato — non serve blacklistarlo
        }

        refreshTokenService.revokeAll(user);
    }

    // Genera un ticket monouso per SSE, valido 30 secondi, salvato in Redis
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

package com.documents.userservice.services;

import com.documents.userservice.config.JwtService;
import com.documents.userservice.dto.AuthResponse;
import com.documents.userservice.dto.LoginRequest;
import com.documents.userservice.dto.RegisterRequest;
import com.documents.userservice.entities.Role;
import com.documents.userservice.entities.User;
import com.documents.userservice.exceptions.UserAlreadyExistsException;
import com.documents.userservice.exceptions.UserNotFoundException;
import com.documents.userservice.repositories.RoleRepository;
import com.documents.userservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    /**
     * Controlla se il sistema è già stato configurato (esiste almeno un utente).
     */
    public boolean isSetupCompleted() {
        return userRepository.count() > 0;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Username già utilizzato: " + request.getUsername());
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email già utilizzata: " + request.getEmail());
        }

        boolean isFirstUser = userRepository.count() == 0;

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(
                        Role.builder()
                                .name("ROLE_USER")
                                .build()
                ));

        List<Role> roles = new ArrayList<>(List.of(userRole));

        // Il primo utente registrato diventa automaticamente admin
        if (isFirstUser) {
            Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                    .orElseGet(() -> roleRepository.save(
                            Role.builder()
                                    .name("ROLE_ADMIN")
                                    .build()
                    ));
            roles.add(adminRole);
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(roles)
                .build();

        userRepository.save(user);

        String token = jwtService.generateToken(user);

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .message(isFirstUser
                        ? "Setup completato! Sei stato registrato come amministratore."
                        : "Registrazione completata con successo")
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new UserNotFoundException("Utente non trovato"));

            String token = jwtService.generateToken(user);

            return AuthResponse.builder()
                    .token(token)
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .message("Login effettuato con successo")
                    .build();
        } catch (Exception e) {
            throw new UserNotFoundException("Credenziali non valide");
        }
    }
}

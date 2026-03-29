package com.documents.userservice.config;

import com.documents.userservice.entities.RefreshToken;
import com.documents.userservice.entities.Role;
import com.documents.userservice.entities.User;
import com.documents.userservice.repositories.RoleRepository;
import com.documents.userservice.repositories.UserRepository;
import com.documents.userservice.services.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

        // Determina il provider dal request URI
        String requestUri = request.getRequestURI();
        String provider = requestUri.contains("github") ? "GITHUB" : "GOOGLE";

        // Estrai attributi in base al provider
        String providerId;
        String email;
        String username;
        String avatarUrl;

        if ("GITHUB".equals(provider)) {
            providerId = String.valueOf(oauth2User.getAttribute("id"));
            email = oauth2User.getAttribute("email");
            username = oauth2User.getAttribute("login");
            avatarUrl = oauth2User.getAttribute("avatar_url");
        } else { // GOOGLE
            providerId = oauth2User.getAttribute("sub");
            email = oauth2User.getAttribute("email");
            String name = oauth2User.getAttribute("name");
            username = name != null ? name.replaceAll("\\s+", "_").toLowerCase() : email;
            avatarUrl = oauth2User.getAttribute("picture");
        }

        // Trova o crea l'utente
        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> findOrCreateUser(provider, providerId, email, username, avatarUrl));

        // Genera access token + refresh token
        String token = jwtService.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        // Redirect al frontend con token e refreshToken
        String redirectUrl = frontendUrl + "/oauth2/callback?token=" + token
                + "&refreshToken=" + refreshToken.getToken()
                + "&username=" + user.getUsername();
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private User findOrCreateUser(String provider, String providerId,
                                  String email, String baseUsername, String avatarUrl) {
        // Se esiste già un utente LOCAL con questa email, collegalo
        if (email != null) {
            var existing = userRepository.findByEmail(email);
            if (existing.isPresent()) {
                User u = existing.get();
                if (u.getProvider() == null) {
                    u.setProvider(provider);
                    u.setProviderId(providerId);
                    if (u.getAvatarUrl() == null) u.setAvatarUrl(avatarUrl);
                }
                return userRepository.save(u);
            }
        }

        // Nuovo utente OAuth2: assegna ROLE_USER (o ROLE_ADMIN se è il primo)
        boolean isFirstUser = userRepository.count() == 0;

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").build()));

        List<Role> roles = new ArrayList<>(List.of(userRole));

        if (isFirstUser) {
            Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                    .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_ADMIN").build()));
            roles.add(adminRole);
        }

        // Garantisci unicità username
        String finalUsername = ensureUniqueUsername(baseUsername);

        User newUser = User.builder()
                .username(finalUsername)
                .email(email != null ? email : providerId + "@oauth2.local")
                .password(null) // nessuna password per utenti OAuth2
                .provider(provider)
                .providerId(providerId)
                .avatarUrl(avatarUrl)
                .roles(roles)
                .build();

        return userRepository.save(newUser);
    }

    private String ensureUniqueUsername(String base) {
        String candidate = base;
        int i = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + "_" + i++;
        }
        return candidate;
    }
}

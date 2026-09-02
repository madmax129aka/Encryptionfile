package com.securevault.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.List;

/**
 * Session-based security tuned for a browser SPA / static HTML client:
 *  - REST endpoints return 401 JSON instead of redirecting to a login page.
 *  - CSRF is disabled because auth rides on a session cookie and the API is
 *    JSON-only (documented trade-off for this prototype).
 *  - CORS allowed origins are CONFIGURABLE via the ALLOWED_ORIGIN env var
 *    (property securevault.allowed-origins). On Render the frontend Static Site
 *    lives on a different domain from the backend Web Service, so the exact
 *    origin must be whitelisted — a wildcard "*" is NOT allowed together with
 *    credentials (the session cookie).
 */
@Configuration
public class SecurityConfig {

    /**
     * Comma-separated list of allowed frontend origins. Defaults cover local dev.
     * On Render set ALLOWED_ORIGIN to your Static Site URL, e.g.
     * https://securevault-frontend.onrender.com
     */
    @Value("${securevault.allowed-origins:http://localhost:8080,http://localhost:3000,http://127.0.0.1:5500}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/", "/index.html", "/*.html", "/static/**", "/favicon.ico").permitAll()
                .anyRequest().authenticated()
            )
            // Return 401 for unauthenticated API calls instead of an HTML redirect.
            .exceptionHandling(eh -> eh.authenticationEntryPoint((req, res, ex) -> {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"Not authenticated\"}");
            }))
            // Allow the H2 console to render inside a frame (dev only).
            .headers(h -> h.frameOptions(f -> f.sameOrigin()));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();

        // Parse the configurable origins list. We use allowedOriginPatterns so
        // credentials (the session cookie) can be sent — Spring forbids the
        // literal "*" origin when allowCredentials is true.
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        cfg.setAllowedOriginPatterns(origins);

        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}

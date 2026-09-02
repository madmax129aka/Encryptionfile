package com.securevault.web;

import com.securevault.entity.User;
import com.securevault.repository.UserRepository;
import com.securevault.service.UserService;
import com.securevault.web.dto.AuthDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Register / login / logout / me. Auth is session-cookie based: a successful
 * login stores the SecurityContext in the HTTP session so subsequent API calls
 * are authenticated via the JSESSIONID cookie.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final UserService userService;

    public AuthController(UserRepository users,
                          PasswordEncoder encoder,
                          AuthenticationManager authManager,
                          UserService userService) {
        this.users = users;
        this.encoder = encoder;
        this.authManager = authManager;
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (users.existsByUsername(req.username())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Username already taken"));
        }
        User u = users.save(new User(req.username(), encoder.encode(req.password())));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UserResponse(u.getId(), u.getUsername()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));

            // Persist the authentication in the session so the cookie carries it.
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            HttpSession session = request.getSession(true);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

            User u = userService.requireByUsername(req.username());
            return ResponseEntity.ok(new UserResponse(u.getId(), u.getUsername()));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("status", "logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }
        User u = userService.requireByUsername(auth.getName());
        return ResponseEntity.ok(new UserResponse(u.getId(), u.getUsername()));
    }
}

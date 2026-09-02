package com.securevault.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight, unauthenticated health check for Render's Web Service.
 * Set the Render "Health Check Path" to /api/health.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "SecureVault");
    }
}

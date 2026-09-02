package com.securevault.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Render (and many PaaS providers) inject the database connection as a single
 * {@code DATABASE_URL} environment variable in the form:
 *
 *   postgresql://user:password@host:5432/dbname
 *
 * That is a libpq-style URL, NOT a JDBC URL, so Spring/HikariCP can't use it
 * directly. This post-processor runs BEFORE the datasource is created, parses
 * DATABASE_URL, and sets the standard Spring datasource properties.
 *
 * If DATABASE_URL is absent (e.g. local dev or the h2 profile), we do nothing
 * and the defaults in application.properties apply.
 *
 * Registered via META-INF/spring.factories.
 */
public class DatabaseUrlConfig implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String databaseUrl = env.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return; // nothing to translate
        }

        try {
            // Normalise scheme: accept both postgres:// and postgresql://
            String normalised = databaseUrl.replaceFirst("^postgres://", "postgresql://");
            URI uri = new URI(normalised);

            String userInfo = uri.getUserInfo(); // "user:password"
            String username = "";
            String password = "";
            if (userInfo != null) {
                String[] parts = userInfo.split(":", 2);
                username = parts[0];
                password = parts.length > 1 ? parts[1] : "";
            }

            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getPath() == null ? "" : uri.getPath();

            // Render's external URLs generally require SSL; add sslmode=require
            // unless the URL already specifies sslmode.
            String query = uri.getQuery();
            String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + path;
            if (query != null && !query.isBlank()) {
                jdbcUrl += "?" + query;
            } else {
                jdbcUrl += "?sslmode=require";
            }

            Map<String, Object> props = new HashMap<>();
            props.put("spring.datasource.url", jdbcUrl);
            props.put("spring.datasource.username", username);
            props.put("spring.datasource.password", password);
            props.put("spring.datasource.driver-class-name", "org.postgresql.Driver");

            // Highest precedence so it overrides application.properties defaults.
            env.getPropertySources().addFirst(
                    new MapPropertySource("renderDatabaseUrl", props));

            System.out.println("[SecureVault] Parsed DATABASE_URL -> " + jdbcUrl + " (user: " + username + ")");
        } catch (Exception e) {
            System.err.println("[SecureVault] Could not parse DATABASE_URL; "
                    + "falling back to spring.datasource.* properties. Reason: " + e.getMessage());
        }
    }
}

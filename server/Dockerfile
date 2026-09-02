# ─────────────────────────────────────────────────────────────
#  SecureVault backend — multi-stage Docker build for Render.
#
#  Render has no native "java" runtime, so the backend Web Service
#  uses runtime: docker and this Dockerfile.
#
#  Stage 1 builds the fat jar with Maven; Stage 2 runs it on a slim JRE.
#  The app already binds 0.0.0.0:${PORT} (Render injects PORT), so no
#  EXPOSE/port config is required here.
# ─────────────────────────────────────────────────────────────

# ── Stage 1: build ──
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Cache dependencies first (only re-downloads when pom.xml changes).
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# Build the application jar.
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ── Stage 2: runtime ──
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the built fat jar (artifactId-version.jar). The wildcard keeps this
# working even if the version bumps.
COPY --from=build /build/target/securevault-server-*.jar app.jar

# Run as a non-root user for a little extra hardening.
RUN useradd -r -u 1001 appuser
USER appuser

# Render sets PORT; the app reads it via server.port=${PORT:8080}.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

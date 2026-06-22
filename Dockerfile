# =============================================================================
# JWCode Docker Image — Multi-stage build
#
# Stage 1: Build frontend SPA (node:20-alpine)
# Stage 2: Build backend fat JAR (maven:3.9-eclipse-temurin-17)
# Stage 3: Minimal runtime (eclipse-temurin:17-jre-alpine)
#
# Usage:
#   docker build -t jwcode/jwcode:latest .
#   docker run -p 8080:8080 -p 8081:8081 \
#     -e JWCODE_API_KEY_DEEPSEEK=sk-xxx \
#     -v jwcode-config:/home/jwcode/.jwcode \
#     -v $(pwd):/jwcode/workspace \
#     jwcode/jwcode:latest
#
# Or with Docker Compose:
#   docker compose up -d
# =============================================================================

# ============================
# Stage 1: Build frontend SPA
# ============================
FROM node:20-alpine AS frontend-builder

WORKDIR /build/jwcode-web

# Copy dependency manifests and install (layer-cached)
COPY jwcode-web/package.json jwcode-web/package-lock.json ./
RUN npm ci && npm cache clean --force

# Copy frontend source and build
COPY jwcode-web/index.html jwcode-web/vite.config.js jwcode-web/tsconfig.json ./
COPY jwcode-web/tsconfig.node.json jwcode-web/postcss.config.js jwcode-web/tailwind.config.js ./
COPY jwcode-web/public ./public
COPY jwcode-web/src ./src
RUN npm run build

# ============================
# Stage 2: Build backend fat JAR
# ============================
FROM maven:3.9-eclipse-temurin-17 AS backend-builder

WORKDIR /build

# ---- Layer 1: POM files only (dependency caching) ----
COPY pom.xml ./
COPY jwcode-common/pom.xml jwcode-common/
COPY jwcode-plugin-api/pom.xml jwcode-plugin-api/
COPY jwcode-core/pom.xml jwcode-core/
COPY jwcode-web/pom.xml jwcode-web/
COPY jwcode-mcp/pom.xml jwcode-mcp/

# Download external dependencies (failures are non-fatal)
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline \
        -pl jwcode-core,jwcode-web -am \
        -Dfrontend.skip=true -q 2>/dev/null || true

# ---- Layer 2: Source code and frontend dist ----
COPY jwcode-common/src jwcode-common/src
COPY jwcode-plugin-api/src jwcode-plugin-api/src
COPY jwcode-core/src jwcode-core/src
COPY jwcode-web/src jwcode-web/src
COPY jwcode-mcp/src jwcode-mcp/src
COPY --from=frontend-builder /build/jwcode-web/dist jwcode-web/dist

# Build fat JAR
RUN --mount=type=cache,target=/root/.m2 \
    mvn install -pl jwcode-core,jwcode-web -am \
        -DskipTests -Dfrontend.skip=true -q && \
    cp jwcode-web/target/jwcode-web.jar /app.jar

# ============================
# Stage 3: Runtime image
# ============================
FROM eclipse-temurin:17-jre-alpine

LABEL org.opencontainers.image.title="JWCode" \
      org.opencontainers.image.description="Java-Native AI Coding Agent — Docker image" \
      org.opencontainers.image.source="https://github.com/jwcode-ai/jwcode" \
      org.opencontainers.image.licenses="Apache-2.0"

# Install runtime deps: docker-cli (sandbox), su-exec (privilege drop), wget (health check)
RUN apk add --no-cache \
    docker-cli \
    su-exec \
    wget

# Create non-root user
RUN addgroup -S jwcode -g 1000 && \
    adduser -S jwcode -G jwcode -u 1000 -h /home/jwcode

# Create directory structure
RUN mkdir -p /jwcode/workspace /home/jwcode/.jwcode && \
    chown -R jwcode:jwcode /jwcode /home/jwcode

# Copy artifacts from previous stages
COPY --from=backend-builder /app.jar /jwcode/jwcode-web.jar
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

# Ensure entrypoint is executable
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

# Health check — uses the existing /api/system endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=20s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/system || exit 1

# Default ports: HTTP 8080, WebSocket 8081
EXPOSE 8080 8081

WORKDIR /jwcode/workspace

ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["java", "-jar", "/jwcode/jwcode-web.jar", "8080", "8081", "/jwcode/workspace"]

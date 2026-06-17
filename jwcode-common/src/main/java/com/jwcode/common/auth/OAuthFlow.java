package com.jwcode.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * OAuthFlow - Production-ready OAuth 2.0 authorization code flow with PKCE.
 *
 * <p>Features:
 * <ul>
 *   <li>PKCE support (S256)</li>
 *   <li>Minimal local callback server</li>
 *   <li>Token exchange and refresh</li>
 *   <li>Thread-safe, reusable {@link HttpClient}</li>
 * </ul>
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class OAuthFlow {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    // ==================== PKCE ====================

    /**
     * Generates a random PKCE code verifier (128 characters, URL-safe Base64).
     */
    public static String generateCodeVerifier() {
        byte[] bytes = new byte[96];
        SECURE_RANDOM.nextBytes(bytes);
        return BASE64_URL_ENCODER.encodeToString(bytes);
    }

    /**
     * Generates the PKCE code challenge (S256) for the given verifier.
     */
    public static String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return BASE64_URL_ENCODER.encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    // ==================== Authorization URL ====================

    /**
     * Builds the authorization URL (without PKCE).
     */
    public static String getAuthorizationUrl(String authorizationEndpoint, String clientId,
                                             String redirectUri, String scope, String state) {
        return getAuthorizationUrl(authorizationEndpoint, clientId, redirectUri, scope, state, null);
    }

    /**
     * Builds the authorization URL with PKCE parameters.
     */
    public static String getAuthorizationUrl(String authorizationEndpoint, String clientId,
                                             String redirectUri, String scope, String state,
                                             String codeChallenge) {
        StringBuilder sb = new StringBuilder(authorizationEndpoint);
        sb.append(authorizationEndpoint.contains("?") ? "&" : "?");
        sb.append("response_type=code");
        sb.append("&client_id=").append(urlEncode(clientId));
        sb.append("&redirect_uri=").append(urlEncode(redirectUri));
        if (notEmpty(scope)) {
            sb.append("&scope=").append(urlEncode(scope));
        }
        if (notEmpty(state)) {
            sb.append("&state=").append(urlEncode(state));
        }
        if (notEmpty(codeChallenge)) {
            sb.append("&code_challenge=").append(urlEncode(codeChallenge));
            sb.append("&code_challenge_method=S256");
        }
        return sb.toString();
    }

    // ==================== Local Callback Server ====================

    /**
     * Starts a minimal local HTTP server to receive the OAuth callback.
     *
     * <p>The server is bound to {@code localhost} on the given {@code port}
     * (use {@code 0} for an ephemeral free port). The context is mounted at
     * {@code /} so it catches any callback path.
     *
     * <p>The server automatically stops after the first request.
     *
     * @param port           the port to bind
     * @param onCodeReceived callback invoked with the authorization code,
     *                       or {@code null} if an error parameter was returned
     * @return the started {@link HttpServer}
     * @throws IOException if the server cannot be created
     */
    public static HttpServer startLocalCallbackServer(int port, Consumer<String> onCodeReceived) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 1);
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String code = extractQueryParam(query, "code");
            String error = extractQueryParam(query, "error");
            String errorDescription = extractQueryParam(query, "error_description");

            String html;
            int status;
            if (code != null) {
                html = "<html><body><h1>Authorization Successful</h1>"
                        + "<p>You may close this window and return to the application.</p></body></html>";
                status = 200;
            } else {
                String msg = notEmpty(error) ? error : "unknown_error";
                if (notEmpty(errorDescription)) {
                    msg += ": " + errorDescription;
                }
                html = "<html><body><h1>Authorization Failed</h1><p>" + escapeHtml(msg) + "</p></body></html>";
                status = 400;
            }

            sendHtml(exchange, status, html);
            server.stop(0);

            if (onCodeReceived != null) {
                onCodeReceived.accept(code);
            }
        });
        server.start();
        return server;
    }

    // ==================== Token Exchange & Refresh ====================

    /**
     * Exchanges an authorization code for an access token (and optional refresh token).
     */
    public static CompletableFuture<OAuthToken> exchangeCodeForToken(String code, String redirectUri,
                                                                      String clientId, String clientSecret,
                                                                      String tokenEndpoint, String codeVerifier) {
        StringBuilder form = new StringBuilder();
        form.append("grant_type=authorization_code");
        form.append("&code=").append(urlEncode(code));
        form.append("&redirect_uri=").append(urlEncode(redirectUri));
        form.append("&client_id=").append(urlEncode(clientId));
        if (notEmpty(clientSecret)) {
            form.append("&client_secret=").append(urlEncode(clientSecret));
        }
        if (notEmpty(codeVerifier)) {
            form.append("&code_verifier=").append(urlEncode(codeVerifier));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString(), StandardCharsets.UTF_8))
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> parseTokenResponse(response.statusCode(), response.body()));
    }

    /**
     * Refreshes the access token using a refresh token.
     */
    public static CompletableFuture<OAuthToken> refreshToken(String refreshToken, String clientId,
                                                              String clientSecret, String tokenEndpoint) {
        StringBuilder form = new StringBuilder();
        form.append("grant_type=refresh_token");
        form.append("&refresh_token=").append(urlEncode(refreshToken));
        form.append("&client_id=").append(urlEncode(clientId));
        if (notEmpty(clientSecret)) {
            form.append("&client_secret=").append(urlEncode(clientSecret));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString(), StandardCharsets.UTF_8))
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> parseTokenResponse(response.statusCode(), response.body()));
    }

    // ==================== High-level Flow ====================

    /**
     * High-level convenience flow:
     * <ol>
     *   <li>Generates PKCE verifier + challenge</li>
     *   <li>Starts a local callback server (uses the port from {@code config.redirectUri},
     *       or binds to a free ephemeral port if the configured port is {@code 0})</li>
     *   <li>Builds the authorization URL and logs it</li>
     *   <li>Returns a {@link CompletableFuture} that completes with the token once
     *       the user authorizes the application and the code is exchanged</li>
     * </ol>
     */
    /**
     * High-level convenience flow:
     * <ol>
     *   <li>Generates PKCE verifier + challenge</li>
     *   <li>Starts a local callback server (uses the port from {@code config.redirectUri},
     *       or binds to a free ephemeral port if the configured port is {@code 0})</li>
     *   <li>Builds the authorization URL and logs it</li>
     *   <li>Returns a {@link CompletableFuture} that completes with the token once
     *       the user authorizes the application and the code is exchanged</li>
     * </ol>
     *
     * @deprecated Use {@link #startAuthFlow(OAuthConfig, java.util.function.Consumer)} instead.
     *             This variant prints the authorization URL to {@code System.out}.
     */
    @Deprecated
    public static CompletableFuture<OAuthToken> startAuthFlow(OAuthConfig config) {
        return startAuthFlow(config, url ->
            System.out.println("Please open the following URL in your browser to authorize: " + url));
    }

    /**
     * High-level convenience flow with an {@code onAuthUrl} callback.
     *
     * <p>Same as {@link #startAuthFlow(OAuthConfig)} but the authorization URL is
     * delivered to the given callback instead of printed to {@code System.out}.
     *
     * <ol>
     *   <li>Generates PKCE verifier + challenge</li>
     *   <li>Starts a local callback server (uses the port from {@code config.redirectUri},
     *       or binds to a free ephemeral port if the configured port is {@code 0})</li>
     *   <li>Builds the authorization URL and calls {@code onAuthUrl}</li>
     *   <li>Returns a {@link CompletableFuture} that completes with the token once
     *       the user authorizes the application and the code is exchanged</li>
     * </ol>
     */
    public static CompletableFuture<OAuthToken> startAuthFlow(OAuthConfig config, java.util.function.Consumer<String> onAuthUrl) {
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        URI redirect = URI.create(config.getRedirectUri());
        int requestedPort = redirect.getPort();
        if (requestedPort == -1) {
            requestedPort = "https".equalsIgnoreCase(redirect.getScheme()) ? 443 : 80;
        }

        CompletableFuture<OAuthToken> future = new CompletableFuture<>();

        try {
            HttpServer server = startLocalCallbackServer(requestedPort, code -> {
                if (code == null) {
                    future.completeExceptionally(new IOException("Authorization failed: no code received from provider"));
                    return;
                }
                exchangeCodeForToken(code, config.getRedirectUri(), config.getClientId(),
                                config.getClientSecret(), config.getTokenEndpoint(), codeVerifier)
                        .whenComplete((token, ex) -> {
                            if (ex != null) {
                                future.completeExceptionally(ex);
                            } else {
                                future.complete(token);
                            }
                        });
            });

            int actualPort = server.getAddress().getPort();
            String actualRedirectUri = config.getRedirectUri();
            if (requestedPort == 0 || actualPort != requestedPort) {
                actualRedirectUri = new URI(redirect.getScheme(), null, redirect.getHost(),
                        actualPort, redirect.getPath(), redirect.getQuery(), redirect.getFragment()).toString();
            }

            String authUrl = getAuthorizationUrl(config.getAuthorizationEndpoint(), config.getClientId(),
                    actualRedirectUri, config.getScope(), config.getState(), codeChallenge);

            if (onAuthUrl != null) {
                onAuthUrl.accept(authUrl);
            }

        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    // ==================== Backward Compatibility ====================

    /**
     * @deprecated Use {@link #startAuthFlow(OAuthConfig)} instead.
     */
    @Deprecated
    public CompletableFuture<AuthResult> startAuthFlow() {
        return CompletableFuture.completedFuture(new AuthResult(false, null, null));
    }

    /**
     * @deprecated Use {@link #refreshToken(String, String, String, String)} instead.
     */
    @Deprecated
    public CompletableFuture<AuthResult> refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            return CompletableFuture.completedFuture(new AuthResult(false, null, null));
        }
        return CompletableFuture.completedFuture(new AuthResult(false, null, null));
    }

    // ==================== Helpers ====================

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String extractQueryParam(String query, String name) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String param : query.split("&")) {
            int idx = param.indexOf('=');
            if (idx > 0 && name.equals(param.substring(0, idx))) {
                return java.net.URLDecoder.decode(param.substring(idx + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void sendHtml(HttpExchange exchange, int status, String html) {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        try {
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException ignored) {
            // best-effort response
        } finally {
            exchange.close();
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    @SuppressWarnings("unchecked")
    private static OAuthToken parseTokenResponse(int statusCode, String body) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new RuntimeException("Token endpoint returned HTTP " + statusCode + ": " + body);
        }
        try {
            Map<String, Object> map = OBJECT_MAPPER.readValue(body, Map.class);
            String accessToken = (String) map.get("access_token");
            String tokenType = (String) map.get("token_type");
            String refreshToken = (String) map.get("refresh_token");
            String scope = (String) map.get("scope");
            Object expiresInObj = map.get("expires_in");
            long expiresIn = expiresInObj instanceof Number ? ((Number) expiresInObj).longValue() : 0L;
            return new OAuthToken(accessToken, tokenType, expiresIn, refreshToken, scope, Instant.now());
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse token response: " + body, e);
        }
    }

    // ==================== Inner Classes ====================

    /**
     * OAuth authentication result (legacy, kept for backward compatibility).
     *
     * @deprecated Use {@link OAuthToken} instead.
     */
    @Deprecated
    public static class AuthResult {
        private final boolean success;
        private final String accessToken;
        private final String refreshToken;

        public AuthResult(boolean success, String accessToken, String refreshToken) {
            this.success = success;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }
    }

    /**
     * OAuth token model with expiration tracking.
     */
    public static class OAuthToken {
        private final String accessToken;
        private final String tokenType;
        private final long expiresIn;
        private final String refreshToken;
        private final String scope;
        private final Instant issuedAt;

        public OAuthToken(String accessToken, String tokenType, long expiresIn,
                          String refreshToken, String scope, Instant issuedAt) {
            this.accessToken = accessToken;
            this.tokenType = tokenType;
            this.expiresIn = expiresIn;
            this.refreshToken = refreshToken;
            this.scope = scope;
            this.issuedAt = issuedAt;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getTokenType() {
            return tokenType;
        }

        public long getExpiresIn() {
            return expiresIn;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public String getScope() {
            return scope;
        }

        public Instant getIssuedAt() {
            return issuedAt;
        }

        /**
         * Returns {@code true} if the token has expired.
         */
        public boolean isExpired() {
            return willExpireSoon(0);
        }

        /**
         * Returns {@code true} if the token will expire within the given threshold.
         *
         * @param thresholdMs threshold in milliseconds
         */
        public boolean willExpireSoon(long thresholdMs) {
            if (expiresIn <= 0 || issuedAt == null) {
                return false;
            }
            Instant expiry = issuedAt.plusSeconds(expiresIn);
            return Instant.now().plusMillis(thresholdMs).isAfter(expiry);
        }
    }

    /**
     * OAuth configuration with builder pattern.
     */
    public static class OAuthConfig {
        private final String authorizationEndpoint;
        private final String tokenEndpoint;
        private final String clientId;
        private final String clientSecret;
        private final String scope;
        private final String redirectUri;
        private final String state;

        private OAuthConfig(Builder builder) {
            this.authorizationEndpoint = builder.authorizationEndpoint;
            this.tokenEndpoint = builder.tokenEndpoint;
            this.clientId = builder.clientId;
            this.clientSecret = builder.clientSecret;
            this.scope = builder.scope;
            this.redirectUri = builder.redirectUri;
            this.state = builder.state;
        }

        public String getAuthorizationEndpoint() {
            return authorizationEndpoint;
        }

        public String getTokenEndpoint() {
            return tokenEndpoint;
        }

        public String getClientId() {
            return clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public String getScope() {
            return scope;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public String getState() {
            return state;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String authorizationEndpoint;
            private String tokenEndpoint;
            private String clientId;
            private String clientSecret;
            private String scope;
            private String redirectUri;
            private String state;

            public Builder authorizationEndpoint(String authorizationEndpoint) {
                this.authorizationEndpoint = authorizationEndpoint;
                return this;
            }

            public Builder tokenEndpoint(String tokenEndpoint) {
                this.tokenEndpoint = tokenEndpoint;
                return this;
            }

            public Builder clientId(String clientId) {
                this.clientId = clientId;
                return this;
            }

            public Builder clientSecret(String clientSecret) {
                this.clientSecret = clientSecret;
                return this;
            }

            public Builder scope(String scope) {
                this.scope = scope;
                return this;
            }

            public Builder redirectUri(String redirectUri) {
                this.redirectUri = redirectUri;
                return this;
            }

            public Builder state(String state) {
                this.state = state;
                return this;
            }

            public OAuthConfig build() {
                if (!notEmpty(authorizationEndpoint)) {
                    throw new IllegalArgumentException("authorizationEndpoint is required");
                }
                if (!notEmpty(tokenEndpoint)) {
                    throw new IllegalArgumentException("tokenEndpoint is required");
                }
                if (!notEmpty(clientId)) {
                    throw new IllegalArgumentException("clientId is required");
                }
                if (!notEmpty(redirectUri)) {
                    throw new IllegalArgumentException("redirectUri is required");
                }
                return new OAuthConfig(this);
            }
        }
    }
}

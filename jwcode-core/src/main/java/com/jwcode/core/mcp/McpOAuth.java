package com.jwcode.core.mcp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * McpOAuth - MCP OAuth 认证
 * 
 * 功能说明：
 * 实现 MCP 协议的 OAuth 2.0 认证流程。
 * 支持授权码模式、客户端凭证模式和 PKCE 扩展。
 * 
 * 核心特性：
 * - OAuth 2.0 授权码模式
 * - PKCE（Proof Key for Code Exchange）
 * - 客户端凭证模式
 * - 令牌自动刷新
 * - 多提供者支持
 * 
 * 上下文关系：
 * - 被 McpClient 用来进行身份认证
 * - 与外部 OAuth 提供者交互
 * - 为 MCP 连接提供认证令牌
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class McpOAuth {
    
    /**
     * OAuth 配置
     */
    private final OAuthConfig config;
    
    /**
     * HTTP 客户端
     */
    private final HttpClient httpClient;
    
    /**
     * 当前令牌
     */
    private OAuthToken currentToken;
    
    /**
     * 状态映射表（state -> PKCE 验证器）
     */
    private final Map<String, PkceVerifier> stateVerifiers;
    
    /**
     * 令牌刷新监听器
     */
    private final List<Consumer<OAuthToken>> refreshListeners;
    
    /**
     * 令牌刷新间隔（毫秒）
     */
    private static final long TOKEN_REFRESH_INTERVAL_MS = 300000; // 5 分钟
    
    /**
     * 随机数生成器
     */
    private static final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * 构造函数
     * 
     * @param config OAuth 配置
     */
    public McpOAuth(OAuthConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.stateVerifiers = new ConcurrentHashMap<>();
        this.refreshListeners = new ArrayList<>();
    }
    
    /**
     * 添加令牌刷新监听器
     * 
     * @param listener 监听器
     */
    public void addRefreshListener(Consumer<OAuthToken> listener) {
        refreshListeners.add(listener);
    }
    
    /**
     * 移除令牌刷新监听器
     * 
     * @param listener 监听器
     */
    public void removeRefreshListener(Consumer<OAuthToken> listener) {
        refreshListeners.remove(listener);
    }
    
    /**
     * 获取授权 URL（PKCE 模式）
     * 
     * @param redirectUri 重定向 URI
     * @param scope 权限范围
     * @return 授权 URL
     */
    public String getAuthorizationUrl(String redirectUri, String scope) {
        return getAuthorizationUrl(redirectUri, scope, true);
    }
    
    /**
     * 获取授权 URL
     * 
     * @param redirectUri 重定向 URI
     * @param scope 权限范围
     * @param usePkce 是否使用 PKCE
     * @return 授权 URL
     */
    public String getAuthorizationUrl(String redirectUri, String scope, boolean usePkce) {
        String state = generateRandomString(32);
        StringBuilder url = new StringBuilder();
        
        url.append(config.getAuthorizationEndpoint());
        url.append("?response_type=code");
        url.append("&client_id=").append(encode(config.getClientId()));
        url.append("&redirect_uri=").append(encode(redirectUri));
        url.append("&state=").append(state);
        
        if (scope != null && !scope.isEmpty()) {
            url.append("&scope=").append(encode(scope));
        }
        
        PkceVerifier pkceVerifier = null;
        if (usePkce) {
            pkceVerifier = generatePkceVerifier();
            stateVerifiers.put(state, pkceVerifier);
            url.append("&code_challenge=").append(pkceVerifier.getCodeChallenge());
            url.append("&code_challenge_method=S256");
        }
        
        return url.toString();
    }
    
    /**
     * 使用授权码交换令牌
     * 
     * @param code 授权码
     * @param redirectUri 重定向 URI
     * @param state 状态值
     * @return 令牌的 CompletableFuture
     */
    public CompletableFuture<OAuthToken> exchangeCodeForToken(String code, String redirectUri, String state) {
        CompletableFuture<OAuthToken> future = new CompletableFuture<>();
        
        try {
            // 获取 PKCE 验证器
            PkceVerifier pkceVerifier = stateVerifiers.remove(state);
            String codeVerifier = pkceVerifier != null ? pkceVerifier.getCodeVerifier() : null;
            
            // 构建请求体
            StringBuilder bodyBuilder = new StringBuilder();
            bodyBuilder.append("grant_type=authorization_code");
            bodyBuilder.append("&code=").append(encode(code));
            bodyBuilder.append("&redirect_uri=").append(encode(redirectUri));
            bodyBuilder.append("&client_id=").append(encode(config.getClientId()));
            
            if (config.getClientSecret() != null && !config.getClientSecret().isEmpty()) {
                bodyBuilder.append("&client_secret=").append(encode(config.getClientSecret()));
            }
            
            if (codeVerifier != null) {
                bodyBuilder.append("&code_verifier=").append(encode(codeVerifier));
            }
            
            // 发送请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getTokenEndpoint()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyBuilder.toString()))
                    .build();
            
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            throw new RuntimeException("令牌交换失败，状态码：" + response.statusCode());
                        }
                        return parseTokenResponse(response.body());
                    })
                    .whenComplete((token, error) -> {
                        if (error != null) {
                            future.completeExceptionally(error);
                        } else {
                            currentToken = token;
                            notifyRefreshListeners(token);
                            future.complete(token);
                        }
                    });
                    
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * 使用客户端凭证获取令牌
     * 
     * @param scope 权限范围
     * @return 令牌的 CompletableFuture
     */
    public CompletableFuture<OAuthToken> getClientCredentialsToken(String scope) {
        CompletableFuture<OAuthToken> future = new CompletableFuture<>();
        
        try {
            StringBuilder bodyBuilder = new StringBuilder();
            bodyBuilder.append("grant_type=client_credentials");
            bodyBuilder.append("&client_id=").append(encode(config.getClientId()));
            
            if (config.getClientSecret() != null && !config.getClientSecret().isEmpty()) {
                bodyBuilder.append("&client_secret=").append(encode(config.getClientSecret()));
            }
            
            if (scope != null && !scope.isEmpty()) {
                bodyBuilder.append("&scope=").append(encode(scope));
            }
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getTokenEndpoint()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyBuilder.toString()))
                    .build();
            
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            throw new RuntimeException("客户端凭证认证失败，状态码：" + response.statusCode());
                        }
                        return parseTokenResponse(response.body());
                    })
                    .whenComplete((token, error) -> {
                        if (error != null) {
                            future.completeExceptionally(error);
                        } else {
                            currentToken = token;
                            notifyRefreshListeners(token);
                            future.complete(token);
                        }
                    });
                    
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * 刷新令牌
     * 
     * @param refreshToken 刷新令牌
     * @return 新令牌的 CompletableFuture
     */
    public CompletableFuture<OAuthToken> refreshToken(String refreshToken) {
        CompletableFuture<OAuthToken> future = new CompletableFuture<>();
        
        try {
            StringBuilder bodyBuilder = new StringBuilder();
            bodyBuilder.append("grant_type=refresh_token");
            bodyBuilder.append("&refresh_token=").append(encode(refreshToken));
            bodyBuilder.append("&client_id=").append(encode(config.getClientId()));
            
            if (config.getClientSecret() != null && !config.getClientSecret().isEmpty()) {
                bodyBuilder.append("&client_secret=").append(encode(config.getClientSecret()));
            }
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getTokenEndpoint()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyBuilder.toString()))
                    .build();
            
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            throw new RuntimeException("令牌刷新失败，状态码：" + response.statusCode());
                        }
                        return parseTokenResponse(response.body());
                    })
                    .whenComplete((token, error) -> {
                        if (error != null) {
                            future.completeExceptionally(error);
                        } else {
                            currentToken = token;
                            notifyRefreshListeners(token);
                            future.complete(token);
                        }
                    });
                    
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * 撤销令牌
     * 
     * @param token 要撤销的令牌
     * @return CompletableFuture
     */
    public CompletableFuture<Void> revokeToken(String token) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        try {
            StringBuilder bodyBuilder = new StringBuilder();
            bodyBuilder.append("token=").append(encode(token));
            bodyBuilder.append("&client_id=").append(encode(config.getClientId()));
            
            if (config.getClientSecret() != null && !config.getClientSecret().isEmpty()) {
                bodyBuilder.append("&client_secret=").append(encode(config.getClientSecret()));
            }
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getRevocationEndpoint()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyBuilder.toString()))
                    .build();
            
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            future.completeExceptionally(error);
                        } else {
                            if (currentToken != null && currentToken.getAccessToken().equals(token)) {
                                currentToken = null;
                            }
                            future.complete(null);
                        }
                    });
                    
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * 获取当前令牌
     * 
     * @return 当前令牌
     */
    public OAuthToken getCurrentToken() {
        return currentToken;
    }
    
    /**
     * 检查是否有有效令牌
     * 
     * @return true 如果有有效令牌
     */
    public boolean hasValidToken() {
        return currentToken != null && !currentToken.isExpired();
    }
    
    /**
     * 获取访问令牌（自动刷新）
     * 
     * @return 访问令牌
     */
    public CompletableFuture<String> getAccessToken() {
        if (hasValidToken()) {
            // 如果令牌即将过期，提前刷新
            if (currentToken.willExpireSoon(TOKEN_REFRESH_INTERVAL_MS)) {
                return refreshToken(currentToken.getRefreshToken())
                        .thenApply(token -> token.getAccessToken());
            }
            return CompletableFuture.completedFuture(currentToken.getAccessToken());
        }
        return CompletableFuture.failedFuture(new IllegalStateException("没有有效的访问令牌"));
    }
    
    /**
     * 解析令牌响应
     */
    private OAuthToken parseTokenResponse(String responseBody) {
        // 简化实现：实际应该使用 JSON 库解析
        Map<String, String> params = parseFormUrlEncoded(responseBody);
        
        String accessToken = params.get("access_token");
        String tokenType = params.getOrDefault("token_type", "Bearer");
        Long expiresIn = parseLong(params.get("expires_in"));
        String refreshToken = params.get("refresh_token");
        String scope = params.get("scope");
        
        return new OAuthToken(accessToken, tokenType, expiresIn, refreshToken, scope);
    }
    
    /**
     * 解析表单 URL 编码字符串
     */
    private Map<String, String> parseFormUrlEncoded(String body) {
        Map<String, String> params = new HashMap<>();
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(decode(kv[0]), decode(kv[1]));
            }
        }
        return params;
    }
    
    /**
     * 生成 PKCE 验证器
     */
    private PkceVerifier generatePkceVerifier() {
        String codeVerifier = generateRandomString(64);
        String codeChallenge = generateCodeChallenge(codeVerifier);
        return new PkceVerifier(codeVerifier, codeChallenge);
    }
    
    /**
     * 生成随机字符串
     */
    private static String generateRandomString(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            int v = b & 0x3f; // 6 bits
            if (v < 10) {
                sb.append((char) ('0' + v));
            } else if (v < 36) {
                sb.append((char) ('a' + v - 10));
            } else if (v < 62) {
                sb.append((char) ('A' + v - 36));
            } else {
                sb.append(v == 62 ? '-' : '_');
            }
        }
        return sb.toString();
    }
    
    /**
     * 生成代码挑战（SHA256）
     */
    private static String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("生成代码挑战失败", e);
        }
    }
    
    /**
     * URL 编码
     */
    private static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value;
        }
    }
    
    /**
     * URL 解码
     */
    private static String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value;
        }
    }
    
    /**
     * 解析 Long
     */
    private static Long parseLong(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 通知刷新监听器
     */
    private void notifyRefreshListeners(OAuthToken token) {
        for (Consumer<OAuthToken> listener : refreshListeners) {
            try {
                listener.accept(token);
            } catch (Exception e) {
                // 忽略监听器异常
            }
        }
    }
    
    // ==================== 内部类 ====================
    
    /**
     * OAuth 配置类
     */
    public static class OAuthConfig {
        private final String authorizationEndpoint;
        private final String tokenEndpoint;
        private final String revocationEndpoint;
        private final String clientId;
        private final String clientSecret;
        private final String issuer;
        private final Set<String> scopes;
        
        private OAuthConfig(String authorizationEndpoint, String tokenEndpoint,
                           String revocationEndpoint, String clientId, String clientSecret,
                           String issuer, Set<String> scopes) {
            this.authorizationEndpoint = authorizationEndpoint;
            this.tokenEndpoint = tokenEndpoint;
            this.revocationEndpoint = revocationEndpoint != null ? revocationEndpoint : tokenEndpoint + "/revoke";
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.issuer = issuer;
            this.scopes = scopes != null ? scopes : new HashSet<>();
        }
        
        public String getAuthorizationEndpoint() {
            return authorizationEndpoint;
        }
        
        public String getTokenEndpoint() {
            return tokenEndpoint;
        }
        
        public String getRevocationEndpoint() {
            return revocationEndpoint;
        }
        
        public String getClientId() {
            return clientId;
        }
        
        public String getClientSecret() {
            return clientSecret;
        }
        
        public String getIssuer() {
            return issuer;
        }
        
        public Set<String> getScopes() {
            return scopes;
        }
        
        /**
         * 构建器
         */
        public static Builder builder() {
            return new Builder();
        }
        
        /**
         * 构建器类
         */
        public static class Builder {
            private String authorizationEndpoint;
            private String tokenEndpoint;
            private String revocationEndpoint;
            private String clientId;
            private String clientSecret;
            private String issuer;
            private Set<String> scopes = new HashSet<>();
            
            public Builder authorizationEndpoint(String endpoint) {
                this.authorizationEndpoint = endpoint;
                return this;
            }
            
            public Builder tokenEndpoint(String endpoint) {
                this.tokenEndpoint = endpoint;
                return this;
            }
            
            public Builder revocationEndpoint(String endpoint) {
                this.revocationEndpoint = endpoint;
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
            
            public Builder issuer(String issuer) {
                this.issuer = issuer;
                return this;
            }
            
            public Builder scopes(Set<String> scopes) {
                this.scopes = new HashSet<>(scopes);
                return this;
            }
            
            public Builder addScope(String scope) {
                this.scopes.add(scope);
                return this;
            }
            
            public OAuthConfig build() {
                return new OAuthConfig(authorizationEndpoint, tokenEndpoint,
                        revocationEndpoint, clientId, clientSecret, issuer, scopes);
            }
        }
    }
    
    /**
     * OAuth 令牌类
     */
    public static class OAuthToken {
        private final String accessToken;
        private final String tokenType;
        private final Long expiresIn;
        private final String refreshToken;
        private final String scope;
        private final Instant issuedAt;
        
        public OAuthToken(String accessToken, String tokenType, Long expiresIn,
                         String refreshToken, String scope) {
            this.accessToken = accessToken;
            this.tokenType = tokenType;
            this.expiresIn = expiresIn;
            this.refreshToken = refreshToken;
            this.scope = scope;
            this.issuedAt = Instant.now();
        }
        
        public String getAccessToken() {
            return accessToken;
        }
        
        public String getTokenType() {
            return tokenType;
        }
        
        public Long getExpiresIn() {
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
         * 检查令牌是否过期
         */
        public boolean isExpired() {
            if (expiresIn == null) {
                return false;
            }
            return Instant.now().isAfter(issuedAt.plusSeconds(expiresIn));
        }
        
        /**
         * 检查令牌是否即将过期
         * 
         * @param thresholdMs 阈值（毫秒）
         * @return true 如果即将过期
         */
        public boolean willExpireSoon(long thresholdMs) {
            if (expiresIn == null) {
                return false;
            }
            Instant expirationTime = issuedAt.plusSeconds(expiresIn);
            Instant thresholdTime = Instant.now().plusMillis(thresholdMs);
            return expirationTime.isBefore(thresholdTime);
        }
    }
    
    /**
     * PKCE 验证器类
     */
    public static class PkceVerifier {
        private final String codeVerifier;
        private final String codeChallenge;
        
        public PkceVerifier(String codeVerifier, String codeChallenge) {
            this.codeVerifier = codeVerifier;
            this.codeChallenge = codeChallenge;
        }
        
        public String getCodeVerifier() {
            return codeVerifier;
        }
        
        public String getCodeChallenge() {
            return codeChallenge;
        }
    }
}
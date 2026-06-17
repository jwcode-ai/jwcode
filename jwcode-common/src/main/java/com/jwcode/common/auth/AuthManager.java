package com.jwcode.common.auth;

import java.time.Instant;

/**
 * AuthManager - 认证管理器
 * 
 * 功能说明：
 * 管理认证状态和令牌信息，支持过期检查和敏感信息脱敏。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class AuthManager {
    
    private static final long API_KEY_DURATION_SECONDS = 86400L; // 默认 API Key 有效期 24h
    private static final long OAUTH_DEFAULT_EXPIRY_SECONDS = 3600L; // OAuth token 默认 1h
    
    private volatile AuthSession session;
    
    public AuthManager() {
        this.session = null;
    }
    
    /**
     * 设置 API Key
     */
    public void setApiKey(String apiKey) {
        this.session = new AuthSession(apiKey, null, 
            Instant.now().plusSeconds(API_KEY_DURATION_SECONDS), null);
    }
    
    /**
     * 设置访问令牌（不带过期信息时使用默认有效期）
     */
    public void setToken(String token) {
        this.session = new AuthSession(null, token,
            Instant.now().plusSeconds(OAUTH_DEFAULT_EXPIRY_SECONDS), null);
    }
    
    /**
     * 设置 OAuth 令牌（带过期信息）
     */
    public void setOAuthToken(OAuthFlow.OAuthToken oauthToken) {
        Instant expiresAt = oauthToken.getExpiresIn() > 0
            ? oauthToken.getIssuedAt().plusSeconds(oauthToken.getExpiresIn())
            : Instant.now().plusSeconds(OAUTH_DEFAULT_EXPIRY_SECONDS);
        this.session = new AuthSession(null, oauthToken.getAccessToken(),
            expiresAt, oauthToken.getRefreshToken());
    }
    
    /**
     * 设置刷新令牌
     */
    public void setRefreshToken(String refreshToken) {
        AuthSession current = this.session;
        if (current != null) {
            this.session = new AuthSession(current.apiKey, current.accessToken,
                current.expiresAt, refreshToken);
        } else {
            this.session = new AuthSession(null, null, null, refreshToken);
        }
    }
    
    /**
     * 获取 API Key
     */
    public String getApiKey() {
        AuthSession s = this.session;
        return s != null ? s.apiKey : null;
    }
    
    /**
     * 获取访问令牌
     */
    public String getToken() {
        AuthSession s = this.session;
        return s != null ? s.accessToken : null;
    }
    
    /**
     * 获取刷新令牌
     */
    public String getRefreshToken() {
        AuthSession s = this.session;
        return s != null ? s.refreshToken : null;
    }
    
    /**
     * 检查是否已认证（考虑过期时间）
     */
    public boolean isAuthenticated() {
        AuthSession s = this.session;
        return s != null && s.isValid();
    }
    
    /**
     * 清除认证信息
     */
    public void clearAuth() {
        this.session = null;
    }
    
    /**
     * 获取用于日志的脱敏字符串
     */
    public String toLogString() {
        AuthSession s = this.session;
        if (s == null) {
            return "AuthManager{session=null}";
        }
        return "AuthManager{session=" + s.toMaskedString() + "}";
    }
    
    /**
     * 不可变的认证会话，包含过期检查
     */
    private static final class AuthSession {
        private final String apiKey;
        private final String accessToken;
        private final Instant expiresAt;
        private final String refreshToken;
        
        AuthSession(String apiKey, String accessToken, Instant expiresAt, String refreshToken) {
            this.apiKey = apiKey;
            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
            this.refreshToken = refreshToken;
        }
        
        boolean isValid() {
            if (expiresAt == null) {
                return apiKey != null || accessToken != null;
            }
            // 提前 60 秒视为过期，避免边界竞态
            return Instant.now().isBefore(expiresAt.minusSeconds(60));
        }
        
        String toMaskedString() {
            StringBuilder sb = new StringBuilder();
            sb.append("apiKey=").append(mask(apiKey));
            sb.append(", accessToken=").append(mask(accessToken));
            sb.append(", expiresAt=").append(expiresAt);
            sb.append(", refreshToken=").append(mask(refreshToken));
            return sb.toString();
        }
        
        private static String mask(String value) {
            if (value == null) {
                return "null";
            }
            if (value.length() <= 8) {
                return "****";
            }
            return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
        }
    }
}

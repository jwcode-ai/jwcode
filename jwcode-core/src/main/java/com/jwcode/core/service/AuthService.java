package com.jwcode.core.service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuthService - 认证服务
 * 
 * 功能说明：
 * 提供统一的认证服务，支持 API Key 和 Token 认证。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class AuthService {
    
    private static final String API_KEY_PREFIX = "sk-";
    
    private final Map<String, String> authStore;
    private volatile String currentApiKey;
    private volatile String currentToken;
    private volatile String currentRefreshToken;
    private volatile boolean authenticated;
    
    public AuthService() {
        this.authStore = new ConcurrentHashMap<>();
        this.authenticated = false;
    }
    
    /**
     * 使用 API Key 认证
     */
    public boolean authenticateWithApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }
        
        this.currentApiKey = apiKey.trim();
        this.authenticated = true;
        authStore.put("api_key", this.currentApiKey);
        return true;
    }
    
    /**
     * 使用 Token 认证
     */
    public boolean authenticateWithToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        
        this.currentToken = token.trim();
        this.authenticated = true;
        authStore.put("token", this.currentToken);
        return true;
    }
    
    /**
     * 检查是否已认证
     */
    public boolean isAuthenticated() {
        return authenticated && (currentApiKey != null || currentToken != null);
    }
    
    /**
     * 获取当前认证类型
     */
    public String getAuthType() {
        if (currentApiKey != null) {
            return "api_key";
        }
        if (currentToken != null) {
            return "token";
        }
        return "none";
    }
    
    /**
     * 登出
     */
    public void logout() {
        this.currentApiKey = null;
        this.currentToken = null;
        this.currentRefreshToken = null;
        this.authenticated = false;
        authStore.clear();
    }
    
    /**
     * 设置刷新令牌
     */
    public void setRefreshToken(String refreshToken) {
        this.currentRefreshToken = refreshToken;
        authStore.put("refresh_token", refreshToken);
    }
    
    /**
     * 获取刷新令牌
     */
    public String getRefreshToken() {
        return this.currentRefreshToken;
    }
    
    /**
     * 刷新令牌
     */
    public CompletableFuture<Boolean> refreshToken() {
        return CompletableFuture.supplyAsync(() -> {
            if (this.currentRefreshToken == null) {
                return false;
            }
            // 模拟令牌刷新
            this.currentToken = "new_token_" + System.currentTimeMillis();
            authStore.put("token", this.currentToken);
            return true;
        });
    }
    
    /**
     * 获取访问令牌
     */
    public String getAccessToken() {
        return this.currentToken;
    }
    
    /**
     * 获取 API Key
     */
    public String getApiKey() {
        return this.currentApiKey;
    }
    
    /**
     * 从存储加载认证信息
     */
    public void loadAuthFromStore() {
        String apiKey = authStore.get("api_key");
        if (apiKey != null) {
            this.currentApiKey = apiKey;
            this.authenticated = true;
        }
        
        String token = authStore.get("token");
        if (token != null) {
            this.currentToken = token;
            this.authenticated = true;
        }
        
        String refreshToken = authStore.get("refresh_token");
        if (refreshToken != null) {
            this.currentRefreshToken = refreshToken;
        }
    }
    
    /**
     * 保存认证信息到存储
     */
    public void saveAuthToStore() {
        if (this.currentApiKey != null) {
            authStore.put("api_key", this.currentApiKey);
        }
        if (this.currentToken != null) {
            authStore.put("token", this.currentToken);
        }
        if (this.currentRefreshToken != null) {
            authStore.put("refresh_token", this.currentRefreshToken);
        }
    }
}
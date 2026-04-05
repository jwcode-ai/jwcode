package com.jwcode.common.service;

import com.jwcode.common.auth.AuthManager;
import com.jwcode.common.auth.OAuthFlow;
import com.jwcode.common.config.ConfigLoader;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AuthService - 认证服务
 * 
 * 功能说明：
 * 提供统一的认证服务，支持 API Key 和 OAuth 认证。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class AuthService {
    
    private final AuthManager authManager;
    private final OAuthFlow oauthFlow;
    private final ConfigLoader configLoader;
    
    public AuthService() {
        this.authManager = new AuthManager();
        this.oauthFlow = new OAuthFlow();
        this.configLoader = new ConfigLoader();
    }
    
    /**
     * 使用 API Key 认证
     */
    public boolean authenticateWithApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }
        
        authManager.setApiKey(apiKey.trim());
        saveAuthToConfig("api_key", apiKey.trim());
        return true;
    }
    
    /**
     * 使用 OAuth 认证
     */
    public CompletableFuture<Boolean> authenticateWithOAuth() {
        return oauthFlow.startAuthFlow()
            .thenApply(authResult -> {
                if (authResult.isSuccess()) {
                    authManager.setToken(authResult.getAccessToken());
                    saveAuthToConfig("oauth_token", authResult.getAccessToken());
                    return true;
                }
                return false;
            });
    }
    
    /**
     * 检查是否已认证
     */
    public boolean isAuthenticated() {
        return authManager.isAuthenticated();
    }
    
    /**
     * 获取当前认证类型
     */
    public String getAuthType() {
        if (authManager.getApiKey() != null) {
            return "api_key";
        }
        if (authManager.getToken() != null) {
            return "oauth";
        }
        return "none";
    }
    
    /**
     * 登出
     */
    public void logout() {
        authManager.clearAuth();
        clearAuthFromConfig();
    }
    
    /**
     * 刷新令牌
     */
    public CompletableFuture<Boolean> refreshToken() {
        return oauthFlow.refreshToken(authManager.getRefreshToken())
            .thenApply(authResult -> {
                if (authResult.isSuccess()) {
                    authManager.setToken(authResult.getAccessToken());
                    saveAuthToConfig("oauth_token", authResult.getAccessToken());
                    return true;
                }
                return false;
            });
    }
    
    /**
     * 获取访问令牌
     */
    public String getAccessToken() {
        return authManager.getToken();
    }
    
    private void saveAuthToConfig(String key, String value) {
        configLoader.setConfig("auth." + key, value, false);
    }
    
    private void clearAuthFromConfig() {
        configLoader.removeConfig("auth.api_key", false);
        configLoader.removeConfig("auth.oauth_token", false);
        configLoader.removeConfig("auth.refresh_token", false);
    }
    
    /**
     * 从配置加载认证信息
     */
    public void loadAuthFromConfig() {
        Map<String, Object> config = configLoader.loadConfig(false);
        
        String apiKey = (String) config.get("auth.api_key");
        if (apiKey != null) {
            authManager.setApiKey(apiKey);
        }
        
        String token = (String) config.get("auth.oauth_token");
        if (token != null) {
            authManager.setToken(token);
        }
        
        String refreshToken = (String) config.get("auth.refresh_token");
        if (refreshToken != null) {
            authManager.setRefreshToken(refreshToken);
        }
    }
}
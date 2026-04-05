package com.jwcode.common.auth;

/**
 * AuthManager - 认证管理器
 * 
 * 功能说明：
 * 管理认证状态和令牌信息。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class AuthManager {
    
    private String apiKey;
    private String token;
    private String refreshToken;
    private boolean authenticated;
    
    public AuthManager() {
        this.authenticated = false;
    }
    
    /**
     * 设置 API Key
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        this.authenticated = true;
    }
    
    /**
     * 设置访问令牌
     */
    public void setToken(String token) {
        this.token = token;
        this.authenticated = true;
    }
    
    /**
     * 设置刷新令牌
     */
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    /**
     * 获取 API Key
     */
    public String getApiKey() {
        return apiKey;
    }
    
    /**
     * 获取访问令牌
     */
    public String getToken() {
        return token;
    }
    
    /**
     * 获取刷新令牌
     */
    public String getRefreshToken() {
        return refreshToken;
    }
    
    /**
     * 检查是否已认证
     */
    public boolean isAuthenticated() {
        return authenticated && (apiKey != null || token != null);
    }
    
    /**
     * 清除认证信息
     */
    public void clearAuth() {
        this.apiKey = null;
        this.token = null;
        this.refreshToken = null;
        this.authenticated = false;
    }
}
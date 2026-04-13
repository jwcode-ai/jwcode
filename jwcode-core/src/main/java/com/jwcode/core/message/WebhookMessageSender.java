package com.jwcode.core.message;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Webhook 消息发送器
 * 通过 HTTP POST 调用 webhook URL
 */
public class WebhookMessageSender implements MessageSender {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private final HttpClient httpClient;
    private String defaultUrl;
    private Map<String, String> defaultHeaders;
    private Duration timeout;
    
    public WebhookMessageSender() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.defaultHeaders = new HashMap<>();
        this.timeout = Duration.ofSeconds(30);
    }
    
    public WebhookMessageSender(String defaultUrl, Map<String, String> defaultHeaders) {
        this();
        this.defaultUrl = defaultUrl;
        if (defaultHeaders != null) {
            this.defaultHeaders.putAll(defaultHeaders);
        }
    }
    
    @Override
    public CompletableFuture<MessageResult> send(String message, Map<String, String> params) {
        String url = params != null && params.containsKey("url") 
                ? params.get("url") 
                : this.defaultUrl;
        
        if (url == null || url.isEmpty()) {
            return CompletableFuture.completedFuture(
                    MessageResult.error("Webhook URL 未指定"));
        }
        
        try {
            // 构建请求体
            Map<String, Object> body = buildRequestBody(message, params);
            String jsonBody = OBJECT_MAPPER.writeValueAsString(body);
            
            // 构建 HTTP 请求
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            
            // 添加自定义 headers
            Map<String, String> headers = new HashMap<>(defaultHeaders);
            if (params != null) {
                params.forEach((key, value) -> {
                    if (key.startsWith("header_")) {
                        headers.put(key.substring(7), value);
                    }
                });
            }
            headers.forEach(requestBuilder::header);
            
            HttpRequest request = requestBuilder.build();
            
            // 发送请求
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        int statusCode = response.statusCode();
                        if (statusCode >= 200 && statusCode < 300) {
                            return MessageResult.success();
                        } else {
                            return MessageResult.error("HTTP " + statusCode + ": " + response.body());
                        }
                    })
                    .exceptionally(throwable -> 
                            MessageResult.error("请求失败: " + throwable.getMessage()));
            
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    MessageResult.error("构建请求失败: " + e.getMessage()));
        }
    }
    
    /**
     * 构建请求体
     */
    private Map<String, Object> buildRequestBody(String message, Map<String, String> params) {
        Map<String, Object> body = new HashMap<>();
        
        // 标准字段
        body.put("message", message);
        body.put("timestamp", System.currentTimeMillis());
        body.put("source", "jwcode");
        
        // 添加额外字段
        if (params != null) {
            // 特殊字段
            if (params.containsKey("event_type")) {
                body.put("event_type", params.get("event_type"));
            }
            if (params.containsKey("priority")) {
                body.put("priority", params.get("priority"));
            }
            if (params.containsKey("tags")) {
                body.put("tags", params.get("tags").split(","));
            }
            
            // 自定义字段（以 data_ 开头）
            Map<String, String> customData = new HashMap<>();
            params.forEach((key, value) -> {
                if (key.startsWith("data_")) {
                    customData.put(key.substring(5), value);
                }
            });
            if (!customData.isEmpty()) {
                body.put("data", customData);
            }
        }
        
        return body;
    }
    
    /**
     * 发送原始 JSON
     */
    public CompletableFuture<MessageResult> sendRaw(String jsonBody, Map<String, String> params) {
        String url = params != null && params.containsKey("url") 
                ? params.get("url") 
                : this.defaultUrl;
        
        if (url == null || url.isEmpty()) {
            return CompletableFuture.completedFuture(
                    MessageResult.error("Webhook URL 未指定"));
        }
        
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            
            // 添加 headers
            Map<String, String> headers = new HashMap<>(defaultHeaders);
            if (params != null) {
                params.forEach((key, value) -> {
                    if (key.startsWith("header_")) {
                        headers.put(key.substring(7), value);
                    }
                });
            }
            headers.forEach(requestBuilder::header);
            
            HttpRequest request = requestBuilder.build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        int statusCode = response.statusCode();
                        if (statusCode >= 200 && statusCode < 300) {
                            return MessageResult.success();
                        } else {
                            return MessageResult.error("HTTP " + statusCode);
                        }
                    })
                    .exceptionally(throwable -> 
                            MessageResult.error("请求失败: " + throwable.getMessage()));
            
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    MessageResult.error("构建请求失败: " + e.getMessage()));
        }
    }
    
    @Override
    public boolean isConfigured() {
        return defaultUrl != null && !defaultUrl.isEmpty();
    }
    
    @Override
    public String getName() {
        return "Webhook";
    }
    
    // Getters and Setters
    public String getDefaultUrl() {
        return defaultUrl;
    }
    
    public void setDefaultUrl(String defaultUrl) {
        this.defaultUrl = defaultUrl;
    }
    
    public Map<String, String> getDefaultHeaders() {
        return defaultHeaders;
    }
    
    public void setDefaultHeaders(Map<String, String> defaultHeaders) {
        this.defaultHeaders = defaultHeaders != null ? defaultHeaders : new HashMap<>();
    }
    
    public void addDefaultHeader(String name, String value) {
        this.defaultHeaders.put(name, value);
    }
    
    public Duration getTimeout() {
        return timeout;
    }
    
    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}

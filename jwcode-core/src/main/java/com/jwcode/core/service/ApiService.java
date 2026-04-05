package com.jwcode.core.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ApiService - API 客户端服务
 * 
 * 功能说明：
 * 提供统一的 HTTP 客户端封装，支持请求/响应拦截器、自动重试、限流等功能。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ApiService {
    
    private final HttpClient httpClient;
    private final Map<String, String> defaultHeaders;
    private final Map<String, RequestInterceptor> requestInterceptors;
    private final Map<String, ResponseInterceptor> responseInterceptors;
    private final RateLimiter rateLimiter;
    private final AtomicInteger retryCounter;
    private int maxRetries;
    private Duration retryDelay;
    private String baseUrl;
    private String apiKey;
    
    public ApiService() {
        this(null, null);
    }
    
    public ApiService(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
        this.defaultHeaders = new ConcurrentHashMap<>();
        this.requestInterceptors = new ConcurrentHashMap<>();
        this.responseInterceptors = new ConcurrentHashMap<>();
        this.rateLimiter = new RateLimiter(60, Duration.ofMinutes(1));
        this.retryCounter = new AtomicInteger(0);
        this.maxRetries = 3;
        this.retryDelay = Duration.ofSeconds(1);
        
        // 设置默认头
        defaultHeaders.put("Content-Type", "application/json");
        defaultHeaders.put("Accept", "application/json");
        if (apiKey != null) {
            defaultHeaders.put("Authorization", "Bearer " + apiKey);
        }
    }
    
    /**
     * GET 请求
     */
    public CompletableFuture<ApiResponse> get(String path) {
        return request("GET", path, null, null);
    }
    
    /**
     * GET 请求（带参数）
     */
    public CompletableFuture<ApiResponse> get(String path, Map<String, String> params) {
        String fullPath = buildUrl(path, params);
        return request("GET", fullPath, null, null);
    }
    
    /**
     * POST 请求
     */
    public CompletableFuture<ApiResponse> post(String path, String body) {
        return request("POST", path, null, body);
    }
    
    /**
     * PUT 请求
     */
    public CompletableFuture<ApiResponse> put(String path, String body) {
        return request("PUT", path, null, body);
    }
    
    /**
     * DELETE 请求
     */
    public CompletableFuture<ApiResponse> delete(String path) {
        return request("DELETE", path, null, null);
    }
    
    /**
     * 通用请求方法
     */
    public CompletableFuture<ApiResponse> request(String method, String path, 
                                                   Map<String, String> params, String body) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 限流检查
                rateLimiter.acquire();
                
                // 构建 URL
                String fullPath = path.startsWith("http") ? path : buildUrl(path, params);
                
                // 构建请求
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(fullPath))
                    .timeout(Duration.ofMinutes(10));
                
                // 添加默认头
                for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }
                
                // 应用请求拦截器
                for (RequestInterceptor interceptor : requestInterceptors.values()) {
                    builder = interceptor.intercept(builder, method, body);
                }
                
                // 设置请求方法和 body
                if (body != null) {
                    builder.method(method, HttpRequest.BodyPublishers.ofString(body));
                } else {
                    builder.method(method, HttpRequest.BodyPublishers.noBody());
                }
                
                // 发送请求
                HttpResponse<String> response = httpClient.send(
                    builder.build(), 
                    HttpResponse.BodyHandlers.ofString()
                );
                
                // 应用响应拦截器
                ApiResponse apiResponse = new ApiResponse(
                    response.statusCode(),
                    response.headers().map(),
                    response.body()
                );
                
                for (ResponseInterceptor interceptor : responseInterceptors.values()) {
                    apiResponse = interceptor.intercept(apiResponse);
                }
                
                return apiResponse;
                
            } catch (Exception e) {
                return ApiResponse.error(e.getMessage());
            }
        });
    }
    
    /**
     * 带重试的请求
     */
    public CompletableFuture<ApiResponse> requestWithRetry(String method, String path,
                                                            Map<String, String> params, String body) {
        return requestWithRetry(method, path, params, body, 0);
    }
    
    private CompletableFuture<ApiResponse> requestWithRetry(String method, String path,
                                                             Map<String, String> params, 
                                                             String body, int attempt) {
        return request(method, path, params, body)
            .thenCompose(response -> {
                if (response.isSuccess() || attempt >= maxRetries) {
                    return CompletableFuture.completedFuture(response);
                }
                
                // 重试
                retryCounter.incrementAndGet();
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(retryDelay.toMillis());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }).thenCompose(v -> requestWithRetry(method, path, params, body, attempt + 1));
            });
    }
    
    /**
     * 构建 URL
     */
    private String buildUrl(String path, Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        
        if (path.startsWith("http")) {
            sb.append(path);
        } else {
            if (baseUrl != null && !baseUrl.isEmpty()) {
                sb.append(baseUrl);
                if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
                    sb.append("/");
                }
            }
            sb.append(path);
        }
        
        // 添加查询参数
        if (params != null && !params.isEmpty()) {
            sb.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first) sb.append("&");
                first = false;
                sb.append(entry.getKey())
                  .append("=")
                  .append(java.net.URLEncoder.encode(entry.getValue(), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 设置默认头
     */
    public void setDefaultHeader(String key, String value) {
        defaultHeaders.put(key, value);
    }
    
    /**
     * 设置 API Key
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        defaultHeaders.put("Authorization", "Bearer " + apiKey);
    }
    
    /**
     * 设置基础 URL
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    /**
     * 注册请求拦截器
     */
    public void addRequestInterceptor(String name, RequestInterceptor interceptor) {
        requestInterceptors.put(name, interceptor);
    }
    
    /**
     * 注册响应拦截器
     */
    public void addResponseInterceptor(String name, ResponseInterceptor interceptor) {
        responseInterceptors.put(name, interceptor);
    }
    
    /**
     * 设置最大重试次数
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    
    /**
     * 设置重试延迟
     */
    public void setRetryDelay(Duration delay) {
        this.retryDelay = delay;
    }
    
    /**
     * 设置限流
     */
    public void setRateLimit(int requests, Duration period) {
        rateLimiter.setLimit(requests, period);
    }
    
    /**
     * 获取重试次数
     */
    public int getRetryCount() {
        return retryCounter.get();
    }
    
    /**
     * 重置重试计数
     */
    public void resetRetryCount() {
        retryCounter.set(0);
    }
    
    /**
     * 请求拦截器接口
     */
    @FunctionalInterface
    public interface RequestInterceptor {
        HttpRequest.Builder intercept(HttpRequest.Builder builder, String method, String body);
    }
    
    /**
     * 响应拦截器接口
     */
    @FunctionalInterface
    public interface ResponseInterceptor {
        ApiResponse intercept(ApiResponse response);
    }
    
    /**
     * API 响应类
     */
    public static class ApiResponse {
        public final int statusCode;
        public final Map<String, java.util.List<String>> headers;
        public final String body;
        public final String error;
        
        private ApiResponse(int statusCode, Map<String, java.util.List<String>> headers,
                           String body, String error) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.body = body;
            this.error = error;
        }
        
        public ApiResponse(int statusCode, Map<String, java.util.List<String>> headers, String body) {
            this(statusCode, headers, body, null);
        }
        
        public static ApiResponse error(String error) {
            return new ApiResponse(0, new HashMap<>(), null, error);
        }
        
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
        
        public String getBody() {
            return body;
        }
    }
    
    /**
     * 限流器类
     */
    public static class RateLimiter {
        private final int limit;
        private final Duration period;
        private final java.util.Queue<Long> timestamps;
        
        public RateLimiter(int limit, Duration period) {
            this.limit = limit;
            this.period = period;
            this.timestamps = new java.util.concurrent.ConcurrentLinkedQueue<>();
        }
        
        public void setLimit(int limit, Duration period) {
            synchronized (this) {
                // 这里可以动态更新限制
            }
        }
        
        public void acquire() throws InterruptedException {
            synchronized (this) {
                long now = System.currentTimeMillis();
                long periodMillis = period.toMillis();
                
                // 移除过期的时间戳
                while (!timestamps.isEmpty() && now - timestamps.peek() > periodMillis) {
                    timestamps.poll();
                }
                
                // 如果达到限制，等待
                if (timestamps.size() >= limit) {
                    long waitTime = periodMillis - (now - timestamps.peek());
                    if (waitTime > 0) {
                        Thread.sleep(waitTime);
                        acquire(); // 递归检查
                        return;
                    }
                }
                
                timestamps.offer(now);
            }
        }
    }
}
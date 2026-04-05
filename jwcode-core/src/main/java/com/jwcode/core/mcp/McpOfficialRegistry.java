package com.jwcode.core.mcp;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * McpOfficialRegistry - MCP 官方注册表
 * 
 * 功能说明：
 * MCP 官方服务器注册表，用于发现和注册 MCP 服务器。
 * 提供服务器搜索、分类浏览、详情查询等功能。
 * 
 * 核心特性：
 * - 官方服务器目录
 * - 服务器搜索和过滤
 * - 分类浏览
 * - 服务器详情查询
 * - 本地缓存
 * 
 * 上下文关系：
 * - 被 McpServerRegistry 扩展
 * - 与外部 MCP 官方注册表 API 交互
 * - 为用户提供服务器发现功能
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class McpOfficialRegistry {
    
    /**
     * HTTP 客户端
     */
    private final HttpClient httpClient;
    
    /**
     * 官方注册表 API URL
     */
    private static final String REGISTRY_API_URL = "https://registry.modelcontextprotocol.io/api/v1";
    
    /**
     * 服务器缓存
     */
    private final Map<String, RegistryServer> serverCache;
    
    /**
     * 分类缓存
     */
    private final List<RegistryCategory> categoryCache;
    
    /**
     * 缓存过期时间（毫秒）
     */
    private static final long CACHE_EXPIRY_MS = 3600000; // 1 小时
    
    /**
     * 缓存时间戳
     */
    private long cacheTimestamp;
    
    /**
     * 构造函数
     */
    public McpOfficialRegistry() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.serverCache = new ConcurrentHashMap<>();
        this.categoryCache = new ArrayList<>();
        this.cacheTimestamp = 0;
    }
    
    /**
     * 列出所有服务器
     * 
     * @return 服务器列表的 CompletableFuture
     */
    public CompletableFuture<List<RegistryServer>> listServers() {
        return listServers(null, null, null, null);
    }
    
    /**
     * 列出服务器（带过滤）
     * 
     * @param category 分类
     * @param query 搜索查询
     * @param limit 限制数量
     * @param offset 偏移量
     * @return 服务器列表的 CompletableFuture
     */
    public CompletableFuture<List<RegistryServer>> listServers(String category, String query, 
                                                               Integer limit, Integer offset) {
        // 检查缓存
        if (!isCacheExpired() && category == null && query == null) {
            return CompletableFuture.completedFuture(new ArrayList<>(serverCache.values()));
        }
        
        try {
            StringBuilder urlBuilder = new StringBuilder(REGISTRY_API_URL);
            urlBuilder.append("/servers");
            
            List<String> params = new ArrayList<>();
            if (category != null && !category.isEmpty()) {
                params.add("category=" + encode(category));
            }
            if (query != null && !query.isEmpty()) {
                params.add("q=" + encode(query));
            }
            if (limit != null) {
                params.add("limit=" + limit);
            }
            if (offset != null) {
                params.add("offset=" + offset);
            }
            
            if (!params.isEmpty()) {
                urlBuilder.append("?").append(String.join("&", params));
            }
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlBuilder.toString()))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            throw new RuntimeException("获取服务器列表失败，状态码：" + response.statusCode());
                        }
                        return parseServerList(response.body());
                    })
                    .thenApply(servers -> {
                        if (category == null && query == null) {
                            // 更新缓存
                            updateCache(servers);
                        }
                        return servers;
                    });
                    
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 获取服务器详情
     * 
     * @param serverId 服务器 ID
     * @return 服务器详情的 CompletableFuture
     */
    public CompletableFuture<RegistryServer> getServer(String serverId) {
        // 检查缓存
        RegistryServer cached = serverCache.get(serverId);
        if (cached != null && !isCacheExpired()) {
            return CompletableFuture.completedFuture(cached);
        }
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REGISTRY_API_URL + "/servers/" + encode(serverId)))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            throw new RuntimeException("获取服务器详情失败，状态码：" + response.statusCode());
                        }
                        return parseServer(response.body());
                    });
                    
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 列出所有分类
     * 
     * @return 分类列表的 CompletableFuture
     */
    public CompletableFuture<List<RegistryCategory>> listCategories() {
        // 检查缓存
        if (!isCacheExpired() && !categoryCache.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>(categoryCache));
        }
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REGISTRY_API_URL + "/categories"))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            throw new RuntimeException("获取分类列表失败，状态码：" + response.statusCode());
                        }
                        List<RegistryCategory> categories = parseCategories(response.body());
                        if (!categories.isEmpty()) {
                            categoryCache.clear();
                            categoryCache.addAll(categories);
                        }
                        return categories;
                    });
                    
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 搜索服务器
     * 
     * @param query 搜索查询
     * @return 服务器列表的 CompletableFuture
     */
    public CompletableFuture<List<RegistryServer>> search(String query) {
        return listServers(null, query, null, null);
    }
    
    /**
     * 按分类获取服务器
     * 
     * @param category 分类名称
     * @return 服务器列表的 CompletableFuture
     */
    public CompletableFuture<List<RegistryServer>> getByCategory(String category) {
        return listServers(category, null, null, null);
    }
    
    /**
     * 检查缓存是否过期
     */
    private boolean isCacheExpired() {
        return System.currentTimeMillis() - cacheTimestamp > CACHE_EXPIRY_MS;
    }
    
    /**
     * 更新缓存
     */
    private void updateCache(List<RegistryServer> servers) {
        serverCache.clear();
        for (RegistryServer server : servers) {
            serverCache.put(server.getId(), server);
        }
        cacheTimestamp = System.currentTimeMillis();
    }
    
    /**
     * 解析服务器列表响应
     */
    private List<RegistryServer> parseServerList(String json) {
        List<RegistryServer> servers = new ArrayList<>();
        // 简化实现：实际应该使用 JSON 库解析
        // 这里假设响应格式为 {"servers": [...]}
        String[] parts = json.split("\\{");
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("\"id\"") && part.contains("\"name\"")) {
                RegistryServer server = parseServerPart(part);
                if (server != null) {
                    servers.add(server);
                }
            }
        }
        return servers;
    }
    
    /**
     * 解析服务器详情响应
     */
    private RegistryServer parseServer(String json) {
        return parseServerPart(json);
    }
    
    /**
     * 解析服务器部分
     */
    private RegistryServer parseServerPart(String json) {
        try {
            String id = extractJsonString(json, "id");
            String name = extractJsonString(json, "name");
            String description = extractJsonString(json, "description");
            String repository = extractJsonString(json, "repository");
            String category = extractJsonString(json, "category");
            
            if (id == null || name == null) {
                return null;
            }
            
            return new RegistryServer(id, name, description, repository, category);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 解析分类列表响应
     */
    private List<RegistryCategory> parseCategories(String json) {
        List<RegistryCategory> categories = new ArrayList<>();
        String[] parts = json.split("\\{");
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("\"name\"") || part.contains("\"slug\"")) {
                String name = extractJsonString(part, "name");
                String slug = extractJsonString(part, "slug");
                String description = extractJsonString(part, "description");
                if (slug != null) {
                    categories.add(new RegistryCategory(name, slug, description));
                }
            }
        }
        return categories;
    }
    
    /**
     * 从 JSON 提取字符串值
     */
    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
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
    
    // ==================== 内部类 ====================
    
    /**
     * 注册表服务器类
     */
    public static class RegistryServer {
        private final String id;
        private final String name;
        private final String description;
        private final String repository;
        private final String category;
        private final List<String> tools;
        private final List<String> resources;
        private final Map<String, Object> metadata;
        
        public RegistryServer(String id, String name, String description,
                             String repository, String category) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.repository = repository;
            this.category = category;
            this.tools = new ArrayList<>();
            this.resources = new ArrayList<>();
            this.metadata = new HashMap<>();
        }
        
        public String getId() {
            return id;
        }
        
        public String getName() {
            return name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public String getRepository() {
            return repository;
        }
        
        public String getCategory() {
            return category;
        }
        
        public List<String> getTools() {
            return tools;
        }
        
        public List<String> getResources() {
            return resources;
        }
        
        public Map<String, Object> getMetadata() {
            return metadata;
        }
        
        public void addTool(String tool) {
            this.tools.add(tool);
        }
        
        public void addResource(String resource) {
            this.resources.add(resource);
        }
        
        public void addMetadata(String key, Object value) {
            this.metadata.put(key, value);
        }
    }
    
    /**
     * 注册表分类类
     */
    public static class RegistryCategory {
        private final String name;
        private final String slug;
        private final String description;
        
        public RegistryCategory(String name, String slug, String description) {
            this.name = name;
            this.slug = slug;
            this.description = description;
        }
        
        public String getName() {
            return name;
        }
        
        public String getSlug() {
            return slug;
        }
        
        public String getDescription() {
            return description;
        }
    }
}
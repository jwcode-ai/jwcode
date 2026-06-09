package com.jwcode.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.config.ConfigManager;
import com.jwcode.core.config.ConfigScope;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 配置 API - 从 ConfigManager 读取真实配置
 */
public class ConfigHandler implements HttpHandler {

    private static final Logger logger = Logger.getLogger(ConfigHandler.class.getName());
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        
        // 设置 CORS 头
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        
        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }
        
        // 路由处理
        if (path.endsWith("/api/config/advanced")) {
            handleAdvancedConfig(exchange, method);
        } else if (path.endsWith("/api/config/advanced/toggle")) {
            handleAdvancedToggle(exchange, method);
        } else if (path.endsWith("/api/config/provider")) {
            handleProviderConfig(exchange, method);
        } else {
            // 默认配置路由
            switch (method.toUpperCase()) {
                case "GET":
                    getConfig(exchange);
                    break;
                case "POST":
                case "PUT":
                    updateConfig(exchange);
                    break;
                case "DELETE":
                    deleteConfig(exchange);
                    break;
                default:
                    sendJsonResponse(exchange, 405, createError("Method not allowed"));
            }
        }
    }
    
    /**
     * 处理高级配置端点 /api/config/advanced
     */
    private void handleAdvancedConfig(HttpExchange exchange, String method) throws IOException {
        switch (method.toUpperCase()) {
            case "GET":
                getAdvancedConfig(exchange);
                break;
            case "POST":
            case "PUT":
                updateAdvancedConfig(exchange);
                break;
            default:
                sendJsonResponse(exchange, 405, createError("Method not allowed"));
        }
    }
    
    /**
     * 处理高级配置切换端点 /api/config/advanced/toggle
     */
    private void handleAdvancedToggle(HttpExchange exchange, String method) throws IOException {
        if ("POST".equalsIgnoreCase(method)) {
            toggleAdvancedConfig(exchange);
        } else {
            sendJsonResponse(exchange, 405, createError("Method not allowed, only POST is supported"));
        }
    }
    
    /**
     * 获取配置 - 从 ConfigManager 读取真实配置
     */
    private void getConfig(HttpExchange exchange) throws IOException {
        ConfigManager configManager = ConfigManager.getInstance();
        Map<String, String> allConfig = configManager.getAll();
        
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        
        // 系统信息
        ObjectNode system = response.putObject("system");
        system.put("version", "1.0.0");
        system.put("streaming", true);
        system.put("websocketPort", 8081);
        
        // 用户配置
        ObjectNode userConfig = response.putObject("user");
        configManager.getAll(ConfigScope.USER).forEach(userConfig::put);
        
        // 项目配置
        ObjectNode projectConfig = response.putObject("project");
        configManager.getAll(ConfigScope.PROJECT).forEach(projectConfig::put);
        
        // 合并后的配置
        ObjectNode config = response.putObject("config");
        allConfig.forEach(config::put);
        
        // 高级功能配置
        addAdvancedConfigToResponse(response, configManager);
        
        sendJsonResponse(exchange, 200, response);
    }
    
    /**
     * 获取配置值，带默认值
     */
    private String getConfigWithDefault(ConfigManager configManager, String key, String defaultValue) {
        String value = configManager.get(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * 添加高级功能配置到响应
     */
    private void addAdvancedConfigToResponse(ObjectNode response, ConfigManager configManager) {
        ObjectNode advanced = response.putObject("advanced");

        // YOLO Mode (全自动模式)
        ObjectNode yolo = advanced.putObject("yolo");
        yolo.put("enabled", Boolean.parseBoolean(getConfigWithDefault(configManager, "yolo.enabled", "false")));
        yolo.put("description", "全自动模式 - AI 将自动执行命令而无需确认");

        // Auto Swarm Mode (自动 Agent Swarm 模式)
        ObjectNode autoSwarm = advanced.putObject("autoSwarm");
        autoSwarm.put("enabled", Boolean.parseBoolean(getConfigWithDefault(configManager, "autoSwarm.enabled", "false")));
        autoSwarm.put("description", "自动 Agent Swarm 模式 - 根据任务复杂度自动创建 Agent 团队");
    }
    
    /**
     * 获取高级功能配置 - GET /api/config/advanced
     */
    private void getAdvancedConfig(HttpExchange exchange) throws IOException {
        ConfigManager configManager = ConfigManager.getInstance();
        
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        
        // 添加高级功能配置
        addAdvancedConfigToResponse(response, configManager);
        
        sendJsonResponse(exchange, 200, response);
    }
    
    /**
     * 更新高级功能配置 - POST/PUT /api/config/advanced
     */
    private void updateAdvancedConfig(HttpExchange exchange) throws IOException {
        ObjectNode request = parseRequestBody(exchange);
        
        if (request == null) {
            sendJsonResponse(exchange, 400, createError("请求体不能为空"));
            return;
        }
        
        ConfigManager configManager = ConfigManager.getInstance();
        
        // 支持批量更新高级配置
        if (request.has("configs")) {
            ObjectNode configs = (ObjectNode) request.get("configs");
            configs.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                String value = entry.getValue().asText();
                // 验证是否为有效的高级配置键
                if (isValidAdvancedConfigKey(key)) {
                    configManager.set(key, value);
                }
            });
        } else if (request.has("key") && request.has("value")) {
            // 单个更新
            String key = request.get("key").asText();
            String value = request.get("value").asText();
            
            if (!isValidAdvancedConfigKey(key)) {
                sendJsonResponse(exchange, 400, createError("无效的高级配置键: " + key));
                return;
            }
            
            configManager.set(key, value);
        } else {
            sendJsonResponse(exchange, 400, createError("需要提供 configs 或 key/value"));
            return;
        }
        
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.put("message", "高级配置已更新");
        
        sendJsonResponse(exchange, 200, response);
    }
    
    /**
     * 切换布尔值配置 - POST /api/config/advanced/toggle
     * 请求体: { "key": "thinking.enabled" }
     */
    private void toggleAdvancedConfig(HttpExchange exchange) throws IOException {
        ObjectNode request = parseRequestBody(exchange);
        
        if (request == null || !request.has("key")) {
            sendJsonResponse(exchange, 400, createError("需要提供 key 参数"));
            return;
        }
        
        String key = request.get("key").asText();
        
        if (!isValidAdvancedConfigKey(key)) {
            sendJsonResponse(exchange, 400, createError("无效的高级配置键: " + key));
            return;
        }
        
        // 只支持布尔值配置的切换
        if (!isBooleanConfigKey(key)) {
            sendJsonResponse(exchange, 400, createError("该配置项不支持切换操作: " + key));
            return;
        }
        
        ConfigManager configManager = ConfigManager.getInstance();
        String currentValue = getConfigWithDefault(configManager, key, "false");
        String newValue = Boolean.parseBoolean(currentValue) ? "false" : "true";
        
        configManager.set(key, newValue);
        
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.put("message", "配置已切换");
        response.put("key", key);
        response.put("value", Boolean.parseBoolean(newValue));
        
        sendJsonResponse(exchange, 200, response);
    }
    
    /**
     * 检查是否为有效的高级配置键
     */
    private boolean isValidAdvancedConfigKey(String key) {
        return key.equals("yolo.enabled") ||
               key.equals("autoSwarm.enabled");
    }
    
    /**
     * 检查是否为布尔值配置键
     */
    private boolean isBooleanConfigKey(String key) {
        return key.endsWith(".enabled");
    }
    
    /**
     * 更新配置
     */
    private void updateConfig(HttpExchange exchange) throws IOException {
        ObjectNode request = parseRequestBody(exchange);
        
        if (request == null) {
            sendJsonResponse(exchange, 400, createError("请求体不能为空"));
            return;
        }
        
        ConfigManager configManager = ConfigManager.getInstance();
        
        // 支持批量更新
        if (request.has("configs")) {
            ObjectNode configs = (ObjectNode) request.get("configs");
            configs.fields().forEachRemaining(entry -> {
                configManager.set(entry.getKey(), entry.getValue().asText());
            });
        } else if (request.has("key") && request.has("value")) {
            // 单个更新
            String key = request.get("key").asText();
            String value = request.get("value").asText();
            configManager.set(key, value);
        } else {
            sendJsonResponse(exchange, 400, createError("需要提供 configs 或 key/value"));
            return;
        }
        
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.put("message", "配置已更新");
        
        sendJsonResponse(exchange, 200, response);
    }
    
    /**
     * 删除配置
     */
    private void deleteConfig(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String key = null;
        
        if (query != null) {
            for (String param : query.split("&")) {
                String[] parts = param.split("=");
                if (parts.length == 2 && "key".equals(parts[0])) {
                    key = parts[1];
                    break;
                }
            }
        }
        
        ObjectNode response = objectMapper.createObjectNode();
        
        if (key == null || key.isEmpty()) {
            response.put("success", false);
            response.put("error", "需要提供 key 参数");
            sendJsonResponse(exchange, 400, response);
            return;
        }
        
        ConfigManager configManager = ConfigManager.getInstance();
        configManager.delete(key);
        
        response.put("success", true);
        response.put("message", "配置已删除");
        response.put("key", key);
        
        sendJsonResponse(exchange, 200, response);
    }
    
    private ObjectNode parseRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (body.isEmpty()) {
                return null;
            }
            return objectMapper.readValue(body, ObjectNode.class);
        }
    }
    
    private void sendJsonResponse(HttpExchange exchange, int statusCode, ObjectNode json) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(json);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    private ObjectNode createError(String message) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("success", false);
        error.put("error", message);
        return error;
    }

    /**
     * Handle /api/config/provider — get or update provider configuration.
     * GET: returns provider summary (API keys masked).
     * POST: saves provider config to ~/.jwcode/config.yaml.
     */
    private void handleProviderConfig(HttpExchange exchange, String method) throws IOException {
        switch (method.toUpperCase()) {
            case "GET":
                getProviderConfig(exchange);
                break;
            case "POST":
            case "PUT":
                saveProviderConfig(exchange);
                break;
            default:
                sendJsonResponse(exchange, 405, createError("Method not allowed"));
        }
    }

    private void getProviderConfig(HttpExchange exchange) throws IOException {
        try {
            com.jwcode.core.config.YamlConfigLoader loader =
                com.jwcode.core.config.YamlConfigLoader.getInstance();
            Map<String, Object> summary = loader.getProviderSummary();
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.set("data", objectMapper.valueToTree(summary));
            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            sendJsonResponse(exchange, 500, createError("Failed to read provider config: " + e.getMessage()));
        }
    }

    private void saveProviderConfig(HttpExchange exchange) throws IOException {
        ObjectNode request = parseRequestBody(exchange);
        if (request == null) {
            sendJsonResponse(exchange, 400, createError("Request body required"));
            return;
        }

        try {
            com.jwcode.core.config.YamlConfigLoader loader =
                com.jwcode.core.config.YamlConfigLoader.getInstance();
            com.jwcode.core.config.JwcodeConfig config = loader.getConfig();

            String providerName = request.has("provider") ? request.get("provider").asText() : null;
            if (providerName == null || providerName.isBlank()) {
                sendJsonResponse(exchange, 400, createError("'provider' field is required"));
                return;
            }

            // Get or create provider config
            com.jwcode.core.config.JwcodeConfig.ProviderConfig provider =
                config.getProviders().computeIfAbsent(providerName,
                    k -> new com.jwcode.core.config.JwcodeConfig.ProviderConfig());

            if (request.has("baseUrl")) {
                provider.setBaseUrl(request.get("baseUrl").asText());
            }
            if (request.has("apiType")) {
                provider.setApiType(request.get("apiType").asText());
            }
            if (request.has("apiKeys")) {
                com.fasterxml.jackson.databind.JsonNode keysNode = request.get("apiKeys");
                java.util.List<String> keys = new java.util.ArrayList<>();
                if (keysNode.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode k : keysNode) {
                        keys.add(k.asText());
                    }
                }
                provider.setApiKeys(keys);
            }
            if (request.has("apiKey")) {
                // Single key convenience field
                java.util.List<String> keys = new java.util.ArrayList<>();
                keys.add(request.get("apiKey").asText());
                provider.setApiKeys(keys);
            }
            if (request.has("models")) {
                com.fasterxml.jackson.databind.JsonNode modelsNode = request.get("models");
                java.util.List<com.jwcode.core.config.JwcodeConfig.ModelDefinition> models =
                    objectMapper.readValue(modelsNode.toString(),
                        objectMapper.getTypeFactory().constructCollectionType(
                            java.util.List.class, com.jwcode.core.config.JwcodeConfig.ModelDefinition.class));
                provider.setModels(models);
            }

            // Set as default provider if requested or if it's the only one
            if (request.has("setDefault") && request.get("setDefault").asBoolean()) {
                config.setDefaultProvider(providerName);
            } else if (config.getProviders().size() == 1) {
                config.setDefaultProvider(providerName);
            }

            // 软校验 baseUrl — 检测常见配置错误并自动修正或警告
            String apiType = provider.getApiType();
            String baseUrl = provider.getBaseUrl();
            if (baseUrl != null && apiType != null) {
                if ("anthropic-messages".equals(apiType)) {
                    if (baseUrl.endsWith("/v1/messages")) {
                        String corrected = baseUrl.substring(0, baseUrl.length() - "/v1/messages".length());
                        logger.warning("[ConfigHandler] Base URL ends with /v1/messages for anthropic-messages provider; "
                            + "auto-correcting to: " + corrected
                            + " (the suffix is added automatically)");
                        provider.setBaseUrl(corrected);
                    } else if (baseUrl.endsWith("/v1")) {
                        String corrected = baseUrl.substring(0, baseUrl.length() - 3);
                        logger.warning("[ConfigHandler] Base URL ends with /v1 for anthropic-messages provider; "
                            + "auto-correcting to: " + corrected);
                        provider.setBaseUrl(corrected);
                    }
                } else if ("openai-completions".equals(apiType)) {
                    if (baseUrl.endsWith("/chat/completions")) {
                        String corrected = baseUrl.substring(0, baseUrl.length() - "/chat/completions".length());
                        logger.warning("[ConfigHandler] Base URL ends with /chat/completions for openai-completions provider; "
                            + "auto-correcting to: " + corrected);
                        provider.setBaseUrl(corrected);
                    }
                }
            }

            // Save to user config
            loader.saveConfig(config);

            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("message", "Provider '" + providerName + "' saved successfully");
            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            sendJsonResponse(exchange, 500, createError("Failed to save provider config: " + e.getMessage()));
        }
    }
}

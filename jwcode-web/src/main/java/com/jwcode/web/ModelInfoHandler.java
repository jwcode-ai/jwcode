package com.jwcode.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.config.YamlConfigLoader;
import com.jwcode.core.llm.LLMFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * ModelInfoHandler - 模型信息 API 处理器
 */
public class ModelInfoHandler implements HttpHandler {
    
    private static final Logger logger = Logger.getLogger(ModelInfoHandler.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        
        if ("OPTIONS".equals(method)) {
            handleOptions(exchange);
            return;
        }
        
        try {
            if ("GET".equals(method)) {
                if ("/api/models".equals(path)) {
                    handleGetModels(exchange);
                } else if ("/api/models/status".equals(path)) {
                    handleGetStatus(exchange);
                } else {
                    sendError(exchange, 404, "Not found: " + path);
                }
            } else if ("POST".equals(method)) {
                if ("/api/models".equals(path)) {
                    handleAddModel(exchange);
                } else if ("/api/models/toggle".equals(path)) {
                    handleToggleModel(exchange);
                } else if ("/api/models/delete".equals(path)) {
                    handleDeleteModel(exchange);
                } else if ("/api/models/update".equals(path)) {
                    handleUpdateModel(exchange);
                } else if ("/api/models/defaults".equals(path)) {
                    handleSetDefaults(exchange);
                } else {
                    sendError(exchange, 404, "Not found: " + path);
                }
            } else {
                sendError(exchange, 405, "Method not allowed: " + method);
            }
        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "ModelInfoHandler error", e);
            sendError(exchange, 500, "Internal error: " + e.getMessage());
        }
    }
    
    private void handleOptions(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(204, -1);
    }
    
    private void handleGetModels(HttpExchange exchange) throws IOException {
        JwcodeConfig config = YamlConfigLoader.getInstance().getConfig();
        List<Map<String, Object>> models = new ArrayList<>();
        
        if (config != null) {
            String pn = config.getDefaultProviderName();
            logger.info("[ModelAPI] defaultProviderName=" + pn + ", providers=" + config.getProviders().keySet());
        }
        
        if (config != null && config.getProviders() != null) {
            config.ensureDefaultsInitialized();
            String globalDefaultRef = config.getDefaultModelRef("global");
            String planDefaultRef = config.getDefaultModelRef("plan");
            String actDefaultRef = config.getDefaultModelRef("act");
            String defaultProviderName = config.getDefaultProviderName();

            for (Map.Entry<String, JwcodeConfig.ProviderConfig> entry : config.getProviders().entrySet()) {
                String providerName = entry.getKey();
                JwcodeConfig.ProviderConfig provider = entry.getValue();

                if (provider.getModels() != null) {
                    for (JwcodeConfig.ModelDefinition model : provider.getModels()) {
                        String modelRef = providerName + ":" + model.getId();
                        Map<String, Object> modelInfo = new HashMap<>();
                        modelInfo.put("id", model.getId());
                        modelInfo.put("name", model.getName());
                        modelInfo.put("enabled", model.isEnabled());
                        modelInfo.put("maxTokens", model.getMaxTokens());
                        modelInfo.put("temperature", model.getTemperature());
                        modelInfo.put("contextWindow", model.getContextWindow());
                        modelInfo.put("isDefault", modelRef.equals(globalDefaultRef));
                        modelInfo.put("isGlobalDefault", modelRef.equals(globalDefaultRef));
                        modelInfo.put("isPlanDefault", modelRef.equals(planDefaultRef));
                        modelInfo.put("isActDefault", modelRef.equals(actDefaultRef));
                        modelInfo.put("provider", providerName);
                        modelInfo.put("apiType", provider.getApiType() != null ? provider.getApiType() : "openai-completions");
                        modelInfo.put("status", model.isEnabled() ? "online" : "offline");
                        modelInfo.put("priority", model.getPriority());

                        if (model.getCost() != null) {
                            Map<String, Object> costInfo = new HashMap<>();
                            costInfo.put("input", model.getCost().getInput());
                            costInfo.put("output", model.getCost().getOutput());
                            costInfo.put("cacheRead", model.getCost().getCacheRead());
                            costInfo.put("cacheWrite", model.getCost().getCacheWrite());
                            modelInfo.put("cost", costInfo);
                            modelInfo.put("price", costInfo);
                        }

                        modelInfo.put("load", 0);
                        modelInfo.put("maxLoad", 100);
                        modelInfo.put("tokens", 0);

                        models.add(modelInfo);
                    }
                }
            }
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("models", models);
        data.put("defaultProvider", config != null ? config.getDefaultProvider() : null);
        
        sendSuccess(exchange, 200, data);
    }
    
    private void handleGetStatus(HttpExchange exchange) throws IOException {
        JwcodeConfig config = YamlConfigLoader.getInstance().getConfig();
        
        Map<String, Object> status = new HashMap<>();
        status.put("status", config != null ? "configured" : "not_configured");
        
        if (config != null) {
            JwcodeConfig.ProviderConfig provider = config.getDefaultProvider();
            if (provider != null) {
                status.put("provider", config.getDefaultProvider());
                status.put("baseUrl", provider.getBaseUrl());
                status.put("modelCount", provider.getModels() != null ? provider.getModels().size() : 0);
                status.put("hasApiKey", provider.getApiKeys() != null && !provider.getApiKeys().isEmpty());
            }
        }
        
        sendSuccess(exchange, 200, status);
    }
    
    /**
     * 处理添加模型请求 POST /api/models
     * 请求体: { "provider": "providerName", "model": { "id": "...", "name": "...", ... } }
     */
    @SuppressWarnings("unchecked")
    private void handleAddModel(HttpExchange exchange) throws IOException {
        // 解析请求体
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        
        Map<String, Object> requestMap;
        try {
            requestMap = mapper.readValue(body, Map.class);
        } catch (Exception e) {
            sendError(exchange, 400, "Invalid JSON: " + e.getMessage());
            return;
        }
        
        String providerName = (String) requestMap.get("provider");
        Map<String, Object> modelData = (Map<String, Object>) requestMap.get("model");
        
        if (providerName == null || providerName.isBlank()) {
            sendError(exchange, 400, "provider is required");
            return;
        }
        if (modelData == null) {
            sendError(exchange, 400, "model is required");
            return;
        }
        
        String modelId = (String) modelData.get("id");
        String modelName = (String) modelData.get("name");
        if (modelId == null || modelId.isBlank()) {
            sendError(exchange, 400, "model.id is required");
            return;
        }
        if (modelName == null || modelName.isBlank()) {
            modelName = modelId;
        }
        
        // 获取当前配置
        YamlConfigLoader loader = YamlConfigLoader.getInstance();
        JwcodeConfig config = loader.getConfig();
        
        // 获取或创建 provider
        JwcodeConfig.ProviderConfig provider = config.getProviders().get(providerName);
        if (provider == null) {
            provider = new JwcodeConfig.ProviderConfig();
            provider.setBaseUrl((String) modelData.getOrDefault("baseUrl", "https://api.openai.com/v1"));
            provider.setApiType((String) modelData.getOrDefault("apiType", "openai-completions"));
            
            // 如果有 apiKeys，一并设置
            if (modelData.containsKey("apiKeys") && modelData.get("apiKeys") instanceof List) {
                provider.setApiKeys((List<String>) modelData.get("apiKeys"));
            }
            
            config.getProviders().put(providerName, provider);
        }
        
        // 检查模型是否已存在
        boolean exists = provider.getModels().stream()
            .anyMatch(m -> m.getId().equals(modelId));
        if (exists) {
            sendError(exchange, 409, "Model '" + modelId + "' already exists in provider '" + providerName + "'");
            return;
        }
        
        // 创建新模型
        JwcodeConfig.ModelDefinition newModel = new JwcodeConfig.ModelDefinition();
        newModel.setId(modelId);
        newModel.setName(modelName);
        newModel.setEnabled((Boolean) modelData.getOrDefault("enabled", true));
        
        if (modelData.containsKey("temperature")) {
            Object temp = modelData.get("temperature");
            if (temp instanceof Number) {
                newModel.setTemperature(((Number) temp).doubleValue());
            }
        }
        if (modelData.containsKey("maxTokens")) {
            Object mt = modelData.get("maxTokens");
            if (mt instanceof Number) {
                newModel.setMaxTokens(((Number) mt).intValue());
            }
        }
        if (modelData.containsKey("contextWindow")) {
            Object cw = modelData.get("contextWindow");
            if (cw instanceof Number) {
                newModel.setContextWindow(((Number) cw).intValue());
            }
        }
        if (modelData.containsKey("priority")) {
            Object p = modelData.get("priority");
            if (p instanceof Number) {
                newModel.setPriority(((Number) p).intValue());
            }
        }
        
        provider.getModels().add(newModel);
        
        // 保存配置到用户目录 ~/.jwcode/config.yaml
        try {
            loader.saveConfig(config);
            logger.info("Saved new model '" + modelId + "' to provider '" + providerName + "'");
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to save config: " + e.getMessage());
            return;
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("model", modelData);
        result.put("provider", providerName);
        result.put("savedTo", loader.getUserConfigPath() != null ? loader.getUserConfigPath().toString() : "unknown");
        
        sendSuccess(exchange, 201, result);
    }

    /**
     * 切换模型的启用/禁用状态 POST /api/models/toggle
     * 请求体: { "provider": "openai", "modelId": "gpt-4o" }
     */
    @SuppressWarnings("unchecked")
    private void handleToggleModel(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        Map<String, Object> requestMap;
        try {
            requestMap = mapper.readValue(body, Map.class);
        } catch (Exception e) {
            sendError(exchange, 400, "Invalid JSON: " + e.getMessage());
            return;
        }

        String providerName = (String) requestMap.get("provider");
        String modelId = (String) requestMap.get("modelId");

        if (providerName == null || providerName.isBlank()) {
            sendError(exchange, 400, "provider is required");
            return;
        }
        if (modelId == null || modelId.isBlank()) {
            sendError(exchange, 400, "modelId is required");
            return;
        }

        YamlConfigLoader loader = YamlConfigLoader.getInstance();
        JwcodeConfig config = loader.getConfig();

        JwcodeConfig.ProviderConfig provider = config.getProviders().get(providerName);
        if (provider == null) {
            sendError(exchange, 404, "Provider '" + providerName + "' not found");
            return;
        }

        JwcodeConfig.ModelDefinition target = null;
        for (JwcodeConfig.ModelDefinition m : provider.getModels()) {
            if (m.getId().equals(modelId)) {
                target = m;
                break;
            }
        }
        if (target == null) {
            sendError(exchange, 404, "Model '" + modelId + "' not found in provider '" + providerName + "'");
            return;
        }

        // Prevent disabling a default model
        boolean currentlyEnabled = target.isEnabled();
        if (currentlyEnabled && isModelAnyDefault(config, providerName, modelId)) {
            sendError(exchange, 400, "Cannot disable a default model. Please set a different default model first.");
            return;
        }

        // Toggle enabled state
        boolean newEnabled = !currentlyEnabled;
        target.setEnabled(newEnabled);

        // Save config
        try {
            loader.saveConfig(config);
            logger.info("Toggled model '" + modelId + "' for provider '" + providerName + "' to enabled=" + newEnabled);
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to save config: " + e.getMessage());
            return;
        }

        // Hot-reload LLMFactory
        try {
            LLMFactory factory = LLMFactory.getGlobalInstance();
            if (factory != null) {
                Path userPath = loader.getUserConfigPath();
                if (userPath != null) {
                    factory.reloadConfig(userPath.toString());
                    logger.info("LLMFactory reloaded after model toggle");
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to reload LLMFactory after model toggle: " + e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("provider", providerName);
        result.put("modelId", modelId);
        result.put("enabled", newEnabled);
        sendSuccess(exchange, 200, result);
    }

    /**
     * 删除模型 POST /api/models/delete
     * 请求体: { "provider": "openai", "modelId": "gpt-4o" }
     */
    @SuppressWarnings("unchecked")
    private void handleDeleteModel(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        Map<String, Object> requestMap;
        try {
            requestMap = mapper.readValue(body, Map.class);
        } catch (Exception e) {
            sendError(exchange, 400, "Invalid JSON: " + e.getMessage());
            return;
        }

        String providerName = (String) requestMap.get("provider");
        String modelId = (String) requestMap.get("modelId");

        if (providerName == null || providerName.isBlank()) {
            sendError(exchange, 400, "provider is required");
            return;
        }
        if (modelId == null || modelId.isBlank()) {
            sendError(exchange, 400, "modelId is required");
            return;
        }

        YamlConfigLoader loader = YamlConfigLoader.getInstance();
        JwcodeConfig config = loader.getConfig();

        JwcodeConfig.ProviderConfig provider = config.getProviders().get(providerName);
        if (provider == null) {
            sendError(exchange, 404, "Provider '" + providerName + "' not found");
            return;
        }

        JwcodeConfig.ModelDefinition target = null;
        for (JwcodeConfig.ModelDefinition m : provider.getModels()) {
            if (m.getId().equals(modelId)) {
                target = m;
                break;
            }
        }
        if (target == null) {
            sendError(exchange, 404, "Model '" + modelId + "' not found in provider '" + providerName + "'");
            return;
        }

        // Prevent deleting a default model
        if (isModelAnyDefault(config, providerName, modelId)) {
            sendError(exchange, 400, "Cannot delete a default model. Please set a different default model first.");
            return;
        }

        // 移除模型
        provider.getModels().remove(target);

        // 保存配置
        try {
            loader.saveConfig(config);
            logger.info("Deleted model '" + modelId + "' from provider '" + providerName + "'");
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to save config: " + e.getMessage());
            return;
        }

        // Hot-reload LLMFactory
        try {
            LLMFactory factory = LLMFactory.getGlobalInstance();
            if (factory != null) {
                Path userPath = loader.getUserConfigPath();
                if (userPath != null) {
                    factory.reloadConfig(userPath.toString());
                    logger.info("LLMFactory reloaded after model delete");
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to reload LLMFactory after model delete: " + e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("provider", providerName);
        result.put("modelId", modelId);
        result.put("removed", true);
        sendSuccess(exchange, 200, result);
    }

    /**
     * 更新模型 POST /api/models/update
     * 请求体: { "provider": "openai", "modelId": "gpt-4o", "model": { "name": "...", "temperature": 1.0, ... } }
     */
    @SuppressWarnings("unchecked")
    private void handleUpdateModel(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        Map<String, Object> requestMap;
        try {
            requestMap = mapper.readValue(body, Map.class);
        } catch (Exception e) {
            sendError(exchange, 400, "Invalid JSON: " + e.getMessage());
            return;
        }

        String providerName = (String) requestMap.get("provider");
        String modelId = (String) requestMap.get("modelId");
        Map<String, Object> modelData = (Map<String, Object>) requestMap.get("model");

        if (providerName == null || providerName.isBlank()) {
            sendError(exchange, 400, "provider is required");
            return;
        }
        if (modelId == null || modelId.isBlank()) {
            sendError(exchange, 400, "modelId is required");
            return;
        }
        if (modelData == null) {
            sendError(exchange, 400, "model is required");
            return;
        }

        YamlConfigLoader loader = YamlConfigLoader.getInstance();
        JwcodeConfig config = loader.getConfig();

        JwcodeConfig.ProviderConfig provider = config.getProviders().get(providerName);
        if (provider == null) {
            sendError(exchange, 404, "Provider '" + providerName + "' not found");
            return;
        }

        JwcodeConfig.ModelDefinition target = null;
        for (JwcodeConfig.ModelDefinition m : provider.getModels()) {
            if (m.getId().equals(modelId)) {
                target = m;
                break;
            }
        }
        if (target == null) {
            sendError(exchange, 404, "Model '" + modelId + "' not found in provider '" + providerName + "'");
            return;
        }

        // 更新字段
        if (modelData.containsKey("name")) {
            Object name = modelData.get("name");
            if (name instanceof String) {
                target.setName((String) name);
            }
        }
        if (modelData.containsKey("enabled")) {
            Object enabled = modelData.get("enabled");
            if (enabled instanceof Boolean) {
                target.setEnabled((Boolean) enabled);
            }
        }
        if (modelData.containsKey("temperature")) {
            Object temp = modelData.get("temperature");
            if (temp instanceof Number) {
                target.setTemperature(((Number) temp).doubleValue());
            }
        }
        if (modelData.containsKey("maxTokens")) {
            Object mt = modelData.get("maxTokens");
            if (mt instanceof Number) {
                target.setMaxTokens(((Number) mt).intValue());
            }
        }
        if (modelData.containsKey("contextWindow")) {
            Object cw = modelData.get("contextWindow");
            if (cw instanceof Number) {
                target.setContextWindow(((Number) cw).intValue());
            }
        }
        if (modelData.containsKey("priority")) {
            Object p = modelData.get("priority");
            if (p instanceof Number) {
                target.setPriority(((Number) p).intValue());
            }
        }

        // 保存配置
        try {
            loader.saveConfig(config);
            logger.info("Updated model '" + modelId + "' for provider '" + providerName + "'");
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to save config: " + e.getMessage());
            return;
        }

        // Hot-reload LLMFactory
        try {
            LLMFactory factory = LLMFactory.getGlobalInstance();
            if (factory != null) {
                Path userPath = loader.getUserConfigPath();
                if (userPath != null) {
                    factory.reloadConfig(userPath.toString());
                    logger.info("LLMFactory reloaded after model update");
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to reload LLMFactory after model update: " + e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("provider", providerName);
        result.put("modelId", modelId);
        result.put("model", modelData);
        sendSuccess(exchange, 200, result);
    }

    /**
     * 设置默认模型 POST /api/models/defaults
     * 请求体: { "global": "provider:modelId", "plan": "provider:modelId", "act": "provider:modelId" }
     * 所有字段可选，只更新提供的字段。
     */
    @SuppressWarnings("unchecked")
    private void handleSetDefaults(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        Map<String, Object> requestMap;
        try {
            requestMap = mapper.readValue(body, Map.class);
        } catch (Exception e) {
            sendError(exchange, 400, "Invalid JSON: " + e.getMessage());
            return;
        }

        YamlConfigLoader loader = YamlConfigLoader.getInstance();
        JwcodeConfig config = loader.getConfig();
        config.ensureDefaultsInitialized();

        // Validate each provided modelRef
        String[] modes = {"global", "plan", "act"};
        for (String mode : modes) {
            if (requestMap.containsKey(mode)) {
                String modelRef = (String) requestMap.get(mode);
                if (modelRef == null || modelRef.isBlank()) {
                    sendError(exchange, 400, mode + " modelRef cannot be empty");
                    return;
                }
                if (!validateModelRefExists(config, modelRef)) {
                    sendError(exchange, 400, "Model '" + modelRef + "' not found in configuration for " + mode + " default");
                    return;
                }
                config.getDefaultModels().put(mode, modelRef);
            }
        }

        // Save config
        try {
            loader.saveConfig(config);
            logger.info("Updated default models: " + config.getDefaultModels());
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to save config: " + e.getMessage());
            return;
        }

        // Hot-reload LLMFactory
        try {
            LLMFactory factory = LLMFactory.getGlobalInstance();
            if (factory != null) {
                Path userPath = loader.getUserConfigPath();
                if (userPath != null) {
                    factory.reloadConfig(userPath.toString());
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to reload LLMFactory after defaults update: " + e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("defaults", new HashMap<>(config.getDefaultModels()));
        sendSuccess(exchange, 200, result);
    }

    /**
     * 验证 ModelRef 对应的模型是否存在于配置中
     */
    private boolean validateModelRefExists(JwcodeConfig config, String modelRef) {
        JwcodeConfig.ModelRefParts parts = JwcodeConfig.parseModelRef(modelRef);
        if (parts == null) return false;
        JwcodeConfig.ProviderConfig provider = config.getProvider(parts.getProvider());
        if (provider == null) return false;
        return provider.findModel(parts.getModelId()).isPresent();
    }

    /**
     * 检查模型是否被设为任何默认（全局/Plan/Act）
     */
    private boolean isModelAnyDefault(JwcodeConfig config, String providerName, String modelId) {
        config.ensureDefaultsInitialized();
        String modelRef = providerName + ":" + modelId;
        String globalRef = config.getDefaultModelRef("global");
        String planRef = config.getDefaultModelRef("plan");
        String actRef = config.getDefaultModelRef("act");
        return modelRef.equals(globalRef) || modelRef.equals(planRef) || modelRef.equals(actRef);
    }

    private void sendSuccess(HttpExchange exchange, int statusCode, Object data) throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        sendJson(exchange, statusCode, response);
    }
    
    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        sendJson(exchange, statusCode, error);
    }
    
    private void sendJson(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = mapper.writeValueAsString(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        
        exchange.sendResponseHeaders(statusCode, bytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}

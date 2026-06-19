package com.jwcode.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.agent.Agent;
import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.config.JwcodeConfig.AgentModelBinding;
import com.jwcode.core.config.YamlConfigLoader;
import com.jwcode.core.llm.LLMFactory;
import com.jwcode.core.llm.ModelResolver;
import com.jwcode.core.llm.ResolvedModel;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Agent 管理 API
 * v3.2 增强：支持模型绑定配置
 */
public class AgentsHandler implements HttpHandler {

    private static final Logger logger = Logger.getLogger(AgentsHandler.class.getName());

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentRegistry agentRegistry;

    public AgentsHandler() {
        this.agentRegistry = AgentRegistry.createDefault();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // 设置 CORS 头
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        if ("GET".equalsIgnoreCase(method)) {
            if (path.matches("/api/agents/[^/]+")) {
                String agentId = path.substring(path.lastIndexOf('/') + 1);
                getAgentDetail(exchange, agentId);
            } else {
                listAgents(exchange);
            }
        } else if ("POST".equalsIgnoreCase(method)) {
            if (path.matches("/api/agents/[^/]+/toggle")) {
                String agentId = path.substring("/api/agents/".length(), path.lastIndexOf("/toggle"));
                toggleAgent(exchange, agentId);
            } else if (path.matches("/api/agents/[^/]+/activate")) {
                String agentId = path.substring("/api/agents/".length(), path.lastIndexOf("/activate"));
                activateAgent(exchange, agentId);
            } else if (path.matches("/api/agents/[^/]+/model")) {
                String agentId = path.substring("/api/agents/".length(), path.lastIndexOf("/model"));
                setAgentModelBinding(exchange, agentId);
            } else if (path.endsWith("/switch")) {
                switchAgent(exchange);
            } else {
                sendJsonResponse(exchange, 405, createError("不支持的端点"));
            }
        } else if ("DELETE".equalsIgnoreCase(method)) {
            if (path.matches("/api/agents/[^/]+")) {
                String agentId = path.substring(path.lastIndexOf('/') + 1);
                deleteAgent(exchange, agentId);
            } else {
                sendJsonResponse(exchange, 405, createError("不支持的端点"));
            }
        } else {
            sendJsonResponse(exchange, 405, createError("Method not allowed"));
        }
    }

    private void listAgents(HttpExchange exchange) throws IOException {
        Collection<Agent> agents = agentRegistry.getAll();
        Agent currentAgent = agentRegistry.getCurrent();

        // 获取模型绑定配置
        JwcodeConfig config = getConfig();
        Map<String, AgentModelBinding> bindings = (config != null) ? config.getAgentModelBindings() : null;
        ModelResolver resolver = getModelResolver();
        com.jwcode.core.agent.AgentRuntimeRegistry runtimeRegistry =
            com.jwcode.core.agent.AgentRuntimeRegistry.getInstance();

        ArrayNode data = objectMapper.createArrayNode();

        for (Agent agent : agents) {
            com.jwcode.core.agent.AgentRuntimeRegistry.RuntimeSnapshot runtime =
                runtimeRegistry.getSnapshot(agent.getId());
            ObjectNode agentNode = objectMapper.createObjectNode();
            agentNode.put("id", agent.getId());
            agentNode.put("name", agent.getName());
            agentNode.put("description", agent.getDescription());
            agentNode.put("color", generateColor(agent.getId()));
            agentNode.put("active", agent.equals(currentAgent));
            agentNode.put("enabled", runtime.isEnabled());
            agentNode.put("instanceCount", runtime.getInstanceCount());
            agentNode.put("state", runtime.getInstanceCount() > 0 ? "busy" : "idle");

            // 模型绑定信息
            ObjectNode bindingNode = agentNode.putObject("modelBinding");
            if (bindings != null && bindings.containsKey(agent.getId())) {
                AgentModelBinding binding = bindings.get(agent.getId());
                bindingNode.put("mode", binding.getMode() != null ? binding.getMode() : "mode-default");
                if (binding.getModelRef() != null) {
                    bindingNode.put("modelRef", binding.getModelRef());
                }
            } else {
                bindingNode.put("mode", "mode-default");
            }

            // 各模式的生效模型
            if (resolver != null) {
                // Plan 模式
                ResolvedModel planModel = resolver.resolveForAgent(agent.getId(), "plan");
                ObjectNode planNode = agentNode.putObject("effectivePlanModel");
                planNode.put("modelRef", planModel.getModelRef());
                planNode.put("provider", planModel.getProvider());
                planNode.put("modelId", planModel.getModelId());
                planNode.put("usable", planModel.isUsable());
                planNode.put("fallback", planModel.isFallback());
                if (planModel.getFallbackReason() != null) {
                    planNode.put("fallbackReason", planModel.getFallbackReason());
                }
                if (planModel.getError() != null) {
                    planNode.put("error", planModel.getError());
                }

                // Act 模式
                ResolvedModel actModel = resolver.resolveForAgent(agent.getId(), "act");
                ObjectNode actNode = agentNode.putObject("effectiveActModel");
                actNode.put("modelRef", actModel.getModelRef());
                actNode.put("provider", actModel.getProvider());
                actNode.put("modelId", actModel.getModelId());
                actNode.put("usable", actModel.isUsable());
                actNode.put("fallback", actModel.isFallback());
                if (actModel.getFallbackReason() != null) {
                    actNode.put("fallbackReason", actModel.getFallbackReason());
                }
                if (actModel.getError() != null) {
                    actNode.put("error", actModel.getError());
                }
            }

            data.add(agentNode);
        }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.set("data", data);
        sendJsonResponse(exchange, 200, response);
    }

    private void getAgentDetail(HttpExchange exchange, String agentId) throws IOException {
        Agent agent = agentRegistry.get(agentId);

        ObjectNode response = objectMapper.createObjectNode();

        if (agent == null) {
            response.put("success", false);
            response.put("error", "Agent 不存在: " + agentId);
            sendJsonResponse(exchange, 404, response);
            return;
        }

        Agent currentAgent = agentRegistry.getCurrent();
        com.jwcode.core.agent.AgentRuntimeRegistry.RuntimeSnapshot runtime =
            com.jwcode.core.agent.AgentRuntimeRegistry.getInstance().getSnapshot(agent.getId());
        ObjectNode agentNode = objectMapper.createObjectNode();
        agentNode.put("id", agent.getId());
        agentNode.put("name", agent.getName());
        agentNode.put("description", agent.getDescription());
        agentNode.put("color", generateColor(agent.getId()));
        agentNode.put("active", agent.equals(currentAgent));
        agentNode.put("enabled", runtime.isEnabled());
        agentNode.put("instanceCount", runtime.getInstanceCount());
        agentNode.put("state", runtime.getInstanceCount() > 0 ? "busy" : "idle");

        // 系统提示词
        if (agent.getSystemPrompt() != null) {
            agentNode.put("systemPrompt", agent.getSystemPrompt());
        }

        // 模型配置（Agent 本身声明的）
        Agent.ModelConfig modelConfig = agent.getModelConfig();
        if (modelConfig != null) {
            ObjectNode modelNode = agentNode.putObject("model");
            modelNode.put("name", modelConfig.getModel());
            modelNode.put("temperature", modelConfig.getTemperature());
            modelNode.put("maxTokens", modelConfig.getMaxTokens());
        }

        // 模型绑定配置（来自 config.yaml）
        JwcodeConfig config = getConfig();
        if (config != null) {
            config.ensureDefaultsInitialized();
            AgentModelBinding binding = config.getAgentModelBinding(agentId);
            ObjectNode bindingNode = agentNode.putObject("modelBinding");
            if (binding != null) {
                bindingNode.put("mode", binding.getMode() != null ? binding.getMode() : "mode-default");
                if (binding.getModelRef() != null) {
                    bindingNode.put("modelRef", binding.getModelRef());
                }
            } else {
                bindingNode.put("mode", "mode-default");
            }
        }

        // 工具列表
        ArrayNode toolsArray = agentNode.putArray("tools");
        if (agent.getTools() != null) {
            for (var tool : agent.getTools()) {
                ObjectNode toolNode = toolsArray.addObject();
                toolNode.put("name", tool.getName());
                toolNode.put("description", tool.getDescription());
            }
        }

        response.put("success", true);
        response.set("data", agentNode);
        sendJsonResponse(exchange, 200, response);
    }

    /**
     * POST /api/agents/{agentId}/model
     * 设置 Agent 的模型绑定
     * 请求体:
     *   { "mode": "mode-default" }
     *   或
     *   { "mode": "specified", "modelRef": "provider:modelId" }
     */
    private void setAgentModelBinding(HttpExchange exchange, String agentId) throws IOException {
        ObjectNode request = parseRequestBody(exchange);

        if (request == null) {
            sendJsonResponse(exchange, 400, createError("需要 JSON 请求体"));
            return;
        }

        String mode = request.has("mode") ? request.get("mode").asText() : "mode-default";
        if (!"mode-default".equals(mode) && !"specified".equals(mode)) {
            sendJsonResponse(exchange, 400, createError("mode 必须是 'mode-default' 或 'specified'"));
            return;
        }

        YamlConfigLoader loader = YamlConfigLoader.getInstance();
        JwcodeConfig config = loader.getConfig();

        if ("specified".equals(mode)) {
            if (!request.has("modelRef") || request.get("modelRef").asText().isBlank()) {
                sendJsonResponse(exchange, 400, createError("specified 模式需要 modelRef (格式: provider:modelId)"));
                return;
            }
            String modelRef = request.get("modelRef").asText();
            JwcodeConfig.ModelRefParts parts = JwcodeConfig.parseModelRef(modelRef);
            if (parts == null) {
                sendJsonResponse(exchange, 400, createError("无效的 modelRef 格式，应为 provider:modelId"));
                return;
            }
            // 验证 provider、API key、模型存在性及启用状态
            JwcodeConfig.ProviderConfig provider = config.getProvider(parts.getProvider());
            if (provider == null) {
                sendJsonResponse(exchange, 400, createError("Provider '" + parts.getProvider() + "' 不存在"));
                return;
            }
            boolean hasValidKey = provider.getApiKeys() != null && provider.getApiKeys().stream()
                .anyMatch(k -> k != null && !k.isBlank() && !k.contains("your-api-key") && k.length() >= 20);
            if (!hasValidKey) {
                sendJsonResponse(exchange, 400, createError("Provider '" + parts.getProvider() + "' 没有有效的 API Key"));
                return;
            }
            java.util.Optional<JwcodeConfig.ModelDefinition> modelOpt = provider.findModel(parts.getModelId());
            if (modelOpt.isEmpty()) {
                sendJsonResponse(exchange, 400, createError("模型 '" + parts.getModelId() + "' 不存在于 provider '" + parts.getProvider() + "' 中"));
                return;
            }
            if (!modelOpt.get().isEnabled()) {
                sendJsonResponse(exchange, 400, createError("模型 '" + parts.getModelId() + "' 已被禁用，请先启用"));
                return;
            }
        }

        config.ensureDefaultsInitialized();

        AgentModelBinding binding = new AgentModelBinding();
        binding.setMode(mode);
        if ("specified".equals(mode) && request.has("modelRef")) {
            binding.setModelRef(request.get("modelRef").asText());
        }
        config.getAgentModelBindings().put(agentId, binding);

        try {
            loader.saveConfig(config);
            logger.info("Updated model binding for agent '" + agentId + "': mode=" + mode
                + (binding.getModelRef() != null ? ", modelRef=" + binding.getModelRef() : ""));
        } catch (Exception e) {
            sendJsonResponse(exchange, 500, createError("保存配置失败: " + e.getMessage()));
            return;
        }

        // 刷新 LLMFactory
        try {
            LLMFactory factory = LLMFactory.getGlobalInstance();
            if (factory != null) {
                Path userPath = loader.getUserConfigPath();
                if (userPath != null) {
                    factory.reloadConfig(userPath.toString());
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to reload LLMFactory after agent model binding update: " + e.getMessage());
        }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        ObjectNode data = response.putObject("data");
        data.put("agentId", agentId);
        data.put("mode", mode);
        if (binding.getModelRef() != null) {
            data.put("modelRef", binding.getModelRef());
        }
        sendJsonResponse(exchange, 200, response);
    }

    private void toggleAgent(HttpExchange exchange, String agentId) throws IOException {
        if (!agentRegistry.hasAgent(agentId)) {
            sendJsonResponse(exchange, 404, createError("Agent not found: " + agentId));
            return;
        }

        ObjectNode request = parseRequestBody(exchange);
        com.jwcode.core.agent.AgentRuntimeRegistry runtimeRegistry =
            com.jwcode.core.agent.AgentRuntimeRegistry.getInstance();
        com.jwcode.core.agent.AgentRuntimeRegistry.RuntimeSnapshot runtime;

        if (request != null && request.has("enabled")) {
            runtime = runtimeRegistry.setEnabled(agentId, request.get("enabled").asBoolean());
        } else {
            runtime = runtimeRegistry.toggleEnabled(agentId);
        }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        ObjectNode data = response.putObject("data");
        data.put("agentId", agentId);
        data.put("enabled", runtime.isEnabled());
        data.put("instanceCount", runtime.getInstanceCount());
        data.put("state", runtime.getInstanceCount() > 0 ? "busy" : "idle");
        sendJsonResponse(exchange, 200, response);
    }

    private void activateAgent(HttpExchange exchange, String agentId) throws IOException {
        boolean success = agentRegistry.switchTo(agentId);

        ObjectNode response = objectMapper.createObjectNode();
        if (success) {
            response.put("success", true);
            response.put("message", "已激活 Agent: " + agentId);
            response.put("currentAgent", agentId);
        } else {
            response.put("success", false);
            response.put("error", "激活失败，Agent 不存在: " + agentId);
        }

        sendJsonResponse(exchange, success ? 200 : 404, response);
    }

    private void deleteAgent(HttpExchange exchange, String agentId) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.put("message", "Agent 删除请求已接收（当前版本内置 Agent 不可删除）");
        sendJsonResponse(exchange, 200, response);
    }

    /**
     * 切换当前 Agent（旧版兼容）
     */
    private void switchAgent(HttpExchange exchange) throws IOException {
        ObjectNode request = parseRequestBody(exchange);

        if (request == null || !request.has("agentId")) {
            sendJsonResponse(exchange, 400, createError("需要提供 agentId"));
            return;
        }

        String agentId = request.get("agentId").asText();
        boolean success = agentRegistry.switchTo(agentId);

        ObjectNode response = objectMapper.createObjectNode();

        if (success) {
            response.put("success", true);
            response.put("message", "已切换到 Agent: " + agentId);
            response.put("currentAgent", agentId);
        } else {
            response.put("success", false);
            response.put("error", "切换失败，Agent 不存在: " + agentId);
        }

        sendJsonResponse(exchange, success ? 200 : 404, response);
    }

    // ==================== Helpers ====================

    private ObjectNode parseRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (body.isEmpty()) {
                return null;
            }
            return objectMapper.readValue(body, ObjectNode.class);
        }
    }

    private JwcodeConfig getConfig() {
        try {
            return YamlConfigLoader.getInstance().getConfig();
        } catch (Exception e) {
            return null;
        }
    }

    private ModelResolver getModelResolver() {
        LLMFactory factory = LLMFactory.getGlobalInstance();
        if (factory != null) {
            return factory.getModelResolver();
        }
        // Fallback: create a standalone resolver
        JwcodeConfig config = getConfig();
        return config != null ? new ModelResolver(config) : null;
    }

    /**
     * 根据 Agent ID 生成固定颜色
     */
    private String generateColor(String id) {
        String[] colors = {
            "#3B82F6", // blue
            "#10B981", // green
            "#F59E0B", // yellow
            "#EF4444", // red
            "#8B5CF6", // purple
            "#EC4899", // pink
            "#06B6D4", // cyan
            "#F97316", // orange
        };
        int hash = id.hashCode();
        return colors[Math.abs(hash) % colors.length];
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
}

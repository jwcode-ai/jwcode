package com.jwcode.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.agent.Agent;
import com.jwcode.core.agent.AgentRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * Agent 管理 API
 */
public class AgentsHandler implements HttpHandler {
    
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
            if (path.matches("/api/agents/[^/]+/activate")) {
                String agentId = path.substring("/api/agents/".length(), path.lastIndexOf("/activate"));
                activateAgent(exchange, agentId);
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
        ArrayNode data = objectMapper.createArrayNode();
        
        for (Agent agent : agents) {
            ObjectNode agentNode = objectMapper.createObjectNode();
            agentNode.put("id", agent.getId());
            agentNode.put("name", agent.getName());
            agentNode.put("description", agent.getDescription());
            agentNode.put("color", generateColor(agent.getId()));
            agentNode.put("active", agent.equals(currentAgent));
            agentNode.put("state", "idle");
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
        ObjectNode agentNode = objectMapper.createObjectNode();
        agentNode.put("id", agent.getId());
        agentNode.put("name", agent.getName());
        agentNode.put("description", agent.getDescription());
        agentNode.put("color", generateColor(agent.getId()));
        agentNode.put("active", agent.equals(currentAgent));
        agentNode.put("state", "idle");
        
        // 系统提示词
        if (agent.getSystemPrompt() != null) {
            agentNode.put("systemPrompt", agent.getSystemPrompt());
        }
        
        // 模型配置
        Agent.ModelConfig modelConfig = agent.getModelConfig();
        if (modelConfig != null) {
            ObjectNode modelNode = agentNode.putObject("model");
            modelNode.put("name", modelConfig.getModel());
            modelNode.put("temperature", modelConfig.getTemperature());
            modelNode.put("maxTokens", modelConfig.getMaxTokens());
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
        // AgentRegistry 暂不支持删除，返回成功但提示
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
    
    private ObjectNode parseRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (body.isEmpty()) {
                return null;
            }
            return objectMapper.readValue(body, ObjectNode.class);
        }
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

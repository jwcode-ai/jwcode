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
 * Agent 管理 API - 展示 3 个内置 Agent
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
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
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
            if (path.endsWith("/switch")) {
                switchAgent(exchange);
            } else {
                sendJsonResponse(exchange, 405, createError("不支持的端点"));
            }
        } else {
            sendJsonResponse(exchange, 405, createError("Method not allowed"));
        }
    }
    
    /**
     * 列出所有 Agent
     */
    private void listAgents(HttpExchange exchange) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        
        Collection<Agent> agents = agentRegistry.getAll();
        response.put("count", agents.size());
        
        // 当前活跃的 Agent
        Agent currentAgent = agentRegistry.getCurrent();
        if (currentAgent != null) {
            response.put("currentAgent", currentAgent.getId());
        }
        
        ArrayNode agentsArray = response.putArray("agents");
        
        for (Agent agent : agents) {
            ObjectNode agentNode = objectMapper.createObjectNode();
            agentNode.put("id", agent.getId());
            agentNode.put("name", agent.getName());
            agentNode.put("description", agent.getDescription());
            agentNode.put("isCurrent", agent.equals(currentAgent));
            
            // 模型配置
            Agent.ModelConfig modelConfig = agent.getModelConfig();
            if (modelConfig != null) {
                ObjectNode modelNode = agentNode.putObject("model");
                modelNode.put("name", modelConfig.getModel());
                modelNode.put("temperature", modelConfig.getTemperature());
                modelNode.put("maxTokens", modelConfig.getMaxTokens());
            }
            
            // 工具数量
            if (agent.getTools() != null) {
                agentNode.put("toolCount", agent.getTools().size());
            }
            
            agentsArray.add(agentNode);
        }
        
        sendJsonResponse(exchange, 200, response);
    }
    
    /**
     * 获取单个 Agent 详情
     */
    private void getAgentDetail(HttpExchange exchange, String agentId) throws IOException {
        Agent agent = agentRegistry.get(agentId);
        
        ObjectNode response = objectMapper.createObjectNode();
        
        if (agent == null) {
            response.put("success", false);
            response.put("error", "Agent 不存在: " + agentId);
            sendJsonResponse(exchange, 404, response);
            return;
        }
        
        response.put("success", true);
        
        ObjectNode agentNode = response.putObject("agent");
        agentNode.put("id", agent.getId());
        agentNode.put("name", agent.getName());
        agentNode.put("description", agent.getDescription());
        agentNode.put("isCurrent", agent.equals(agentRegistry.getCurrent()));
        
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
        
        // 禁用工具列表（如果 Agent 支持）
        ArrayNode disallowedToolsArray = agentNode.putArray("disallowedTools");
        // 注意：Agent 接口目前没有 getDisallowedTools 方法
        
        sendJsonResponse(exchange, 200, response);
    }
    
    /**
     * 切换当前 Agent
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

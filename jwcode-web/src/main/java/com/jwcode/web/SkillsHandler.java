package com.jwcode.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.skill.Skill;
import com.jwcode.core.skill.SkillRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

/**
 * 技能管理 API
 */
public class SkillsHandler implements HttpHandler {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkillRegistry skillRegistry;
    
    public SkillsHandler() {
        this.skillRegistry = new SkillRegistry();
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
            if (path.matches("/api/skills/[^/]+")) {
                String skillId = path.substring(path.lastIndexOf('/') + 1);
                getSkillDetail(exchange, skillId);
            } else {
                listSkills(exchange);
            }
        } else if ("POST".equalsIgnoreCase(method)) {
            if (path.matches("/api/skills/[^/]+/toggle")) {
                String skillId = path.substring("/api/skills/".length(), path.lastIndexOf("/toggle"));
                toggleSkill(exchange, skillId);
            } else {
                sendJsonResponse(exchange, 405, createError("不支持的端点"));
            }
        } else {
            sendJsonResponse(exchange, 405, createError("Method not allowed"));
        }
    }
    
    private void listSkills(HttpExchange exchange) throws IOException {
        List<Skill> skills = skillRegistry.getAll();
        ArrayNode data = objectMapper.createArrayNode();
        
        for (Skill skill : skills) {
            ObjectNode skillNode = objectMapper.createObjectNode();
            skillNode.put("id", skill.getId());
            skillNode.put("name", skill.getName());
            skillNode.put("description", skill.getDescription());
            skillNode.put("category", skill.getCategory() != null ? skill.getCategory().name().toLowerCase() : "unknown");
            skillNode.put("enabled", true);
            skillNode.put("icon", getCategoryIcon(skill.getCategory()));
            data.add(skillNode);
        }
        
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.set("data", data);
        sendJsonResponse(exchange, 200, response);
    }
    
    private void getSkillDetail(HttpExchange exchange, String skillId) throws IOException {
        Optional<Skill> skillOpt = skillRegistry.get(skillId);
        
        ObjectNode response = objectMapper.createObjectNode();
        
        if (skillOpt.isEmpty()) {
            response.put("success", false);
            response.put("error", "技能不存在: " + skillId);
            sendJsonResponse(exchange, 404, response);
            return;
        }
        
        Skill skill = skillOpt.get();
        ObjectNode skillNode = objectMapper.createObjectNode();
        skillNode.put("id", skill.getId());
        skillNode.put("name", skill.getName());
        skillNode.put("description", skill.getDescription());
        skillNode.put("category", skill.getCategory() != null ? skill.getCategory().name().toLowerCase() : "unknown");
        skillNode.put("enabled", true);
        skillNode.put("icon", getCategoryIcon(skill.getCategory()));
        
        // 标签
        ArrayNode tagsArray = skillNode.putArray("tags");
        if (skill.getTags() != null) {
            skill.getTags().forEach(tagsArray::add);
        }
        
        // 系统提示词
        if (skill.getSystemPrompt() != null) {
            skillNode.put("systemPrompt", skill.getSystemPrompt());
        }
        
        // 示例
        ArrayNode examplesArray = skillNode.putArray("examples");
        if (skill.getExamples() != null) {
            for (Skill.Example example : skill.getExamples()) {
                ObjectNode exampleNode = examplesArray.addObject();
                exampleNode.put("input", example.getInput());
                exampleNode.put("output", example.getOutput());
            }
        }
        
        response.put("success", true);
        response.set("data", skillNode);
        sendJsonResponse(exchange, 200, response);
    }
    
    private void toggleSkill(HttpExchange exchange, String skillId) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.put("message", "技能状态切换请求已接收（当前版本技能始终启用）");
        sendJsonResponse(exchange, 200, response);
    }
    
    private String getCategoryIcon(Skill.Category category) {
        if (category == null) return "⭐";
        switch (category) {
            case CODE: return "💻";
            case ANALYSIS: return "🔍";
            case DOCUMENT: return "📄";
            case TEST: return "🧪";
            case DEVOPS: return "🚀";
            default: return "⭐";
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

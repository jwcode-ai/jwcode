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
 * 技能管理 API - 展示 6 个内置技能
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
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        
        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }
        
        if ("GET".equalsIgnoreCase(method)) {
            if (path.matches("/api/skills/[^/]+")) {
                // 获取单个技能详情
                String skillId = path.substring(path.lastIndexOf('/') + 1);
                getSkillDetail(exchange, skillId);
            } else {
                // 获取所有技能列表
                listSkills(exchange);
            }
        } else {
            sendJsonResponse(exchange, 405, createError("Method not allowed"));
        }
    }
    
    /**
     * 列出所有技能
     */
    private void listSkills(HttpExchange exchange) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        
        List<Skill> skills = skillRegistry.getAll();
        response.put("count", skills.size());
        
        ArrayNode skillsArray = response.putArray("skills");
        
        for (Skill skill : skills) {
            ObjectNode skillNode = objectMapper.createObjectNode();
            skillNode.put("id", skill.getId());
            skillNode.put("name", skill.getName());
            skillNode.put("description", skill.getDescription());
            skillNode.put("category", skill.getCategory() != null ? skill.getCategory().name() : "UNKNOWN");
            
            // 添加标签
            ArrayNode tagsArray = skillNode.putArray("tags");
            if (skill.getTags() != null) {
                skill.getTags().forEach(tagsArray::add);
            }
            
            skillsArray.add(skillNode);
        }
        
        // 添加分类统计
        ObjectNode categories = response.putObject("categories");
        for (Skill.Category category : Skill.Category.values()) {
            List<Skill> categorySkills = skillRegistry.getByCategory(category);
            categories.put(category.name(), categorySkills.size());
        }
        
        sendJsonResponse(exchange, 200, response);
    }
    
    /**
     * 获取单个技能详情
     */
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
        response.put("success", true);
        
        ObjectNode skillNode = response.putObject("skill");
        skillNode.put("id", skill.getId());
        skillNode.put("name", skill.getName());
        skillNode.put("description", skill.getDescription());
        skillNode.put("category", skill.getCategory() != null ? skill.getCategory().name() : "UNKNOWN");
        
        // 标签
        ArrayNode tagsArray = skillNode.putArray("tags");
        if (skill.getTags() != null) {
            skill.getTags().forEach(tagsArray::add);
        }
        
        // 系统提示词（可选，可能较长）
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
        
        sendJsonResponse(exchange, 200, response);
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

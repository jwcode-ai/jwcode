package com.jwcode.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.planner.ExecutionPlan;
import com.jwcode.core.planner.PlanStep;
import com.jwcode.core.planner.PlanTemplate;
import com.jwcode.core.planner.PlanTemplateRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

/**
 * 计划模板 API - 展示计划模板
 */
public class TemplatesHandler implements HttpHandler {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
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
            if (path.matches("/api/templates/[^/]+")) {
                String templateName = path.substring(path.lastIndexOf('/') + 1);
                getTemplateDetail(exchange, templateName);
            } else {
                listTemplates(exchange);
            }
        } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/apply")) {
            applyTemplate(exchange);
        } else {
            sendJsonResponse(exchange, 405, createError("Method not allowed"));
        }
    }
    
    /**
     * 列出所有模板
     */
    private void listTemplates(HttpExchange exchange) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        
        Collection<String> templateNames = PlanTemplateRegistry.getTemplateNames();
        response.put("count", templateNames.size());
        
        ArrayNode templatesArray = response.putArray("templates");
        
        for (String name : templateNames) {
            PlanTemplate template = PlanTemplateRegistry.get(name);
            if (template != null) {
                ObjectNode templateNode = objectMapper.createObjectNode();
                templateNode.put("name", template.getName());
                templateNode.put("description", template.getDescription());
                templatesArray.add(templateNode);
            }
        }
        
        sendJsonResponse(exchange, 200, response);
    }
    
    /**
     * 获取单个模板详情（简化版，返回基本信息）
     */
    private void getTemplateDetail(HttpExchange exchange, String templateName) throws IOException {
        PlanTemplate template = PlanTemplateRegistry.get(templateName);
        
        ObjectNode response = objectMapper.createObjectNode();
        
        if (template == null) {
            response.put("success", false);
            response.put("error", "模板不存在: " + templateName);
            sendJsonResponse(exchange, 404, response);
            return;
        }
        
        response.put("success", true);
        
        ObjectNode templateNode = response.putObject("template");
        templateNode.put("name", template.getName());
        templateNode.put("description", template.getDescription());
        
        // 示例步骤（由于没有直接获取步骤的方法，这里展示说明）
        templateNode.put("note", "使用 POST /api/templates/apply 并传入 templateName 和 request 来应用模板");
        
        sendJsonResponse(exchange, 200, response);
    }
    
    /**
     * 应用模板
     */
    private void applyTemplate(HttpExchange exchange) throws IOException {
        ObjectNode request = parseRequestBody(exchange);
        
        if (request == null) {
            sendJsonResponse(exchange, 400, createError("请求体不能为空"));
            return;
        }
        
        if (!request.has("templateName") || !request.has("userRequest")) {
            sendJsonResponse(exchange, 400, createError("需要提供 templateName 和 userRequest"));
            return;
        }
        
        String templateName = request.get("templateName").asText();
        String userRequest = request.get("userRequest").asText();
        
        PlanTemplate template = PlanTemplateRegistry.get(templateName);
        
        ObjectNode response = objectMapper.createObjectNode();
        
        if (template == null) {
            response.put("success", false);
            response.put("error", "模板不存在: " + templateName);
            sendJsonResponse(exchange, 404, response);
            return;
        }
        
        // 应用模板生成计划
        try {
            com.jwcode.core.planner.PlanningContext context = 
                new com.jwcode.core.planner.PlanningContext();
            ExecutionPlan plan = template.apply(userRequest, context);
            
            response.put("success", true);
            response.put("message", "模板应用成功");
            
            ObjectNode planNode = response.putObject("plan");
            planNode.put("planId", plan.getPlanId());
            planNode.put("originalRequest", plan.getOriginalRequest());
            
            if (plan.getIntent() != null) {
                ObjectNode intentNode = planNode.putObject("intent");
                intentNode.put("type", plan.getIntent().getType() != null ? 
                    plan.getIntent().getType().name() : "UNKNOWN");
                intentNode.put("confidence", plan.getIntent().getConfidence());
            }
            
            ArrayNode stepsArray = planNode.putArray("steps");
            List<PlanStep> steps = plan.getSteps();
            if (steps != null) {
                for (PlanStep step : steps) {
                    ObjectNode stepNode = stepsArray.addObject();
                    stepNode.put("stepNumber", step.getStepNumber());
                    stepNode.put("action", step.getAction());
                    stepNode.put("description", step.getDescription());
                    stepNode.put("agentType", step.getAgentType());
                    stepNode.put("dependsOnPrevious", step.isDependsOnPrevious());
                    if (step.getEstimatedTimeMs() > 0) {
                        stepNode.put("estimatedTimeMs", step.getEstimatedTimeMs());
                    }
                }
            }
            
            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "应用模板失败: " + e.getMessage());
            sendJsonResponse(exchange, 500, response);
        }
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

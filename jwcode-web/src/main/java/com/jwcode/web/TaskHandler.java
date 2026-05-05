package com.jwcode.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jwcode.core.task.Task;
import com.jwcode.core.task.TaskStore;
import com.jwcode.core.task.TaskStatus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * 任务管理 HTTP 处理器
 * 提供任务的 CRUD API
 */
public class TaskHandler implements HttpHandler {
    
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    
    private final TaskStore taskStore;
    
    public TaskHandler(TaskStore taskStore) {
        this.taskStore = taskStore;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        
        // 提取路径参数
        String[] pathParts = path.split("/");
        String taskId = pathParts.length > 3 ? pathParts[3] : null;
        
        try {
            switch (method) {
                case "GET":
                    if (taskId != null && !taskId.isEmpty()) {
                        handleGetTask(exchange, taskId);
                    } else {
                        handleListTasks(exchange);
                    }
                    break;
                    
                case "POST":
                    handleCreateTask(exchange);
                    break;
                    
                case "PUT":
                    if (taskId != null) {
                        handleUpdateTask(exchange, taskId);
                    } else {
                        sendError(exchange, 400, "Task ID required");
                    }
                    break;
                    
                case "PATCH":
                    if (taskId != null) {
                        handlePatchTask(exchange, taskId);
                    } else {
                        sendError(exchange, 400, "Task ID required");
                    }
                    break;
                    
                case "DELETE":
                    if (taskId != null) {
                        handleDeleteTask(exchange, taskId);
                    } else {
                        handleClearCompleted(exchange);
                    }
                    break;
                    
                default:
                    sendError(exchange, 405, "Method not allowed");
            }
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }
    
    private void handleListTasks(HttpExchange exchange) throws IOException {
        List<Task> tasks = taskStore.list();
        sendSuccess(exchange, 200, tasks);
    }
    
    private void handleGetTask(HttpExchange exchange, String taskId) throws IOException {
        Task task = taskStore.get(taskId);
        if (task != null) {
            sendSuccess(exchange, 200, task);
        } else {
            sendError(exchange, 404, "Task not found");
        }
    }
    
    private void handleCreateTask(HttpExchange exchange) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = MAPPER.readValue(exchange.getRequestBody(), Map.class);
        
        Task task = new Task();
        task.setTitle((String) body.get("title"));
        task.setDescription((String) body.get("description"));
        
        Object priority = body.get("priority");
        if (priority instanceof Number) {
            task.setPriority(((Number) priority).intValue());
        } else {
            task.setPriority(5);
        }
        
        Task created = taskStore.create(task);
        sendSuccess(exchange, 201, created);
    }
    
    private void handleUpdateTask(HttpExchange exchange, String taskId) throws IOException {
        Task task = taskStore.get(taskId);
        if (task == null) {
            sendError(exchange, 404, "Task not found");
            return;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> body = MAPPER.readValue(exchange.getRequestBody(), Map.class);
        
        if (body.containsKey("title")) {
            task.setTitle((String) body.get("title"));
        }
        if (body.containsKey("description")) {
            task.setDescription((String) body.get("description"));
        }
        if (body.containsKey("status")) {
            task.setStatus(TaskStatus.valueOf((String) body.get("status")));
        }
        if (body.containsKey("priority")) {
            task.setPriority(((Number) body.get("priority")).intValue());
        }
        if (body.containsKey("progress")) {
            task.setProgress(((Number) body.get("progress")).intValue());
        }
        
        Task updated = taskStore.update(task);
        sendSuccess(exchange, 200, updated);
    }
    
    private void handlePatchTask(HttpExchange exchange, String taskId) throws IOException {
        Task task = taskStore.get(taskId);
        if (task == null) {
            sendError(exchange, 404, "Task not found");
            return;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> body = MAPPER.readValue(exchange.getRequestBody(), Map.class);
        
        if (body.containsKey("status")) {
            task.setStatus(TaskStatus.valueOf((String) body.get("status")));
        }
        
        Task updated = taskStore.update(task);
        sendSuccess(exchange, 200, updated);
    }
    
    private void handleDeleteTask(HttpExchange exchange, String taskId) throws IOException {
        boolean deleted = taskStore.delete(taskId);
        if (deleted) {
            sendSuccess(exchange, 200, null);
        } else {
            sendError(exchange, 404, "Task not found");
        }
    }
    
    private void handleClearCompleted(HttpExchange exchange) throws IOException {
        int count = 0;
        for (Task t : taskStore.listByStatus(TaskStatus.COMPLETED)) {
            if (taskStore.delete(t.getId())) count++;
        }
        sendSuccess(exchange, 200, Map.of("deleted", count));
    }
    
    private void sendSuccess(HttpExchange exchange, int status, Object data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        
        ObjectNode response = MAPPER.createObjectNode();
        response.put("success", true);
        if (data != null) {
            response.set("data", MAPPER.valueToTree(data));
        }
        
        byte[] bytes = MAPPER.writeValueAsBytes(response);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        
        ObjectNode response = MAPPER.createObjectNode();
        response.put("success", false);
        response.put("error", message);
        
        byte[] bytes = MAPPER.writeValueAsBytes(response);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
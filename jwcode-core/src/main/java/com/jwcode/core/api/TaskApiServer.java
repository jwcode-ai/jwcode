package com.jwcode.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jwcode.core.agent.EnhancedOrchestratorAgent;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.session.SessionManager;
import com.jwcode.core.task.Task;
import com.jwcode.core.task.TaskStore;
import com.jwcode.core.task.TaskStatus;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Task REST API Server + WebSocket Server
 * 
 * 使用 JDK 内置 HttpServer 提供任务管理的 REST 接口
 * 使用 Java-WebSocket 提供实时通信
 * HTTP 端口: 8084
 * WebSocket 端口: 8081
 */
public class TaskApiServer {
    
    private static final Logger logger = Logger.getLogger(TaskApiServer.class.getName());
    
    private static final int HTTP_PORT = 8084;
    private static final int WS_PORT = 8081;
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    
    private final TaskStore taskStore;
    private HttpServer httpServer;
    private TaskWebSocketServer wsServer;
    
    // 核心依赖（由外部注入）
    private EnhancedOrchestratorAgent orchestrator;
    private LLMService llmService;
    private ToolExecutor toolExecutor;
    private ToolRegistry toolRegistry;
    private SessionManager sessionManager;
    
    public TaskApiServer(TaskStore taskStore) {
        this.taskStore = taskStore;
    }
    
    /**
     * 设置核心依赖
     */
    public void setOrchestrator(EnhancedOrchestratorAgent orchestrator) {
        this.orchestrator = orchestrator;
    }
    
    public void setLlmService(LLMService llmService) {
        this.llmService = llmService;
    }
    
    public void setToolExecutor(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }
    
    public void setToolRegistry(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }
    
    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
    
    /**
     * 启动服务器
     */
    public void start() throws IOException {
        // 启动 HTTP 服务器
        httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
        
        // 任务路由
        httpServer.createContext("/api/tasks", new TaskHandler());
        
        // 健康检查
        httpServer.createContext("/api/health", exchange -> {
            sendJson(exchange, 200, Map.of("status", "ok"));
        });
        
        httpServer.setExecutor(Executors.newFixedThreadPool(4));
        httpServer.start();
        
        logger.info("HTTP Server started on port " + HTTP_PORT);
        
        // 启动 WebSocket 服务器
        wsServer = new TaskWebSocketServer(WS_PORT);
        wsServer.start();
        
        // 配置 StepMessageBroadcaster 使用 WebSocket 服务器
        StepMessageBroadcaster.setWebSocketServer(wsServer);
        logger.info("StepMessageBroadcaster configured");

        // 配置 PlanTaskBroadcaster 使用 WebSocket 服务器
        PlanTaskBroadcaster.setWebSocketServer(wsServer);
        logger.info("PlanTaskBroadcaster configured");
        
        // 创建 WebSocket 消息处理器并注入到 WebSocket 服务器
        // 这样前端发送的 chat/plan 消息会被正确路由到 Orchestrator 或 LLMQueryEngine
        if (orchestrator != null && llmService != null) {
            SessionManager sm = sessionManager != null ? sessionManager : new SessionManager();
            WebSocketMessageHandler handler = new WebSocketMessageHandler(
                sm, orchestrator, llmService, toolExecutor, toolRegistry, wsServer
            );
            wsServer.setMessageHandler(handler);
            logger.info("WebSocketMessageHandler configured and injected");
        } else {
            logger.warning("WebSocketMessageHandler not configured - missing dependencies");
        }
    }
    
    /**
     * 停止服务器
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            logger.info("HTTP Server stopped");
        }
        if (wsServer != null) {
            wsServer.stop();
            logger.info("WebSocket Server stopped");
        }
    }
    
    /**
     * 获取 WebSocket 服务器实例（用于发送日志）
     */
    public TaskWebSocketServer getWsServer() {
        return wsServer;
    }
    
    /**
     * 发送日志到所有 WebSocket 客户端
     */
    public void sendLog(String level, String source, String message) {
        if (wsServer != null) {
            wsServer.broadcastLog(level, source, message);
        }
    }
    
    /**
     * 任务处理器
     */
    private class TaskHandler implements HttpHandler {
        
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
                            sendJson(exchange, 400, Map.of("error", "Task ID required"));
                        }
                        break;
                        
                    case "PATCH":
                        if (taskId != null) {
                            handlePatchTask(exchange, taskId);
                        } else {
                            sendJson(exchange, 400, Map.of("error", "Task ID required"));
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
                        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                }
            } catch (Exception e) {
                sendJson(exchange, 500, Map.of("error", e.getMessage()));
            }
        }
        
        private void handleListTasks(HttpExchange exchange) throws IOException {
            List<Task> tasks = taskStore.list();
            sendJson(exchange, 200, tasks);
        }
        
        private void handleGetTask(HttpExchange exchange, String taskId) throws IOException {
            Task task = taskStore.get(taskId);
            if (task != null) {
                sendJson(exchange, 200, task);
            } else {
                sendJson(exchange, 404, Map.of("error", "Task not found"));
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
            
            // 广播 WebSocket 消息
            if (wsServer != null) {
                wsServer.broadcastTaskUpdate(created.getId(), "created", created);
            }
            
            sendJson(exchange, 201, created);
        }
        
        private void handleUpdateTask(HttpExchange exchange, String taskId) throws IOException {
            Task task = taskStore.get(taskId);
            if (task == null) {
                sendJson(exchange, 404, Map.of("error", "Task not found"));
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
                TaskStatus newStatus = TaskStatus.valueOf((String) body.get("status"));
                // 状态转换校验
                if (!TaskStatus.isValidTransition(task.getStatus(), newStatus)) {
                    sendJson(exchange, 422, Map.of(
                        "error", "非法的状态转换: " + task.getStatus() + " → " + newStatus,
                        "currentStatus", task.getStatus().name(),
                        "requestedStatus", newStatus.name()
                    ));
                    return;
                }
                task.setStatus(newStatus);
                // 自动记录时间戳
                if (newStatus == TaskStatus.RUNNING || newStatus == TaskStatus.EXECUTING) {
                    task.setStartedAt(java.time.LocalDateTime.now());
                } else if (newStatus.isFinished()) {
                    task.setCompletedAt(java.time.LocalDateTime.now());
                }
            }
            if (body.containsKey("priority")) {
                task.setPriority(((Number) body.get("priority")).intValue());
            }
            if (body.containsKey("progress")) {
                task.setProgress(((Number) body.get("progress")).intValue());
            }
            
            Task updated = taskStore.update(task);
            
            // 广播 WebSocket 消息
            if (wsServer != null) {
                wsServer.broadcastTaskUpdate(updated.getId(), "updated", updated);
            }
            
            sendJson(exchange, 200, updated);
        }
        
        private void handlePatchTask(HttpExchange exchange, String taskId) throws IOException {
            Task task = taskStore.get(taskId);
            if (task == null) {
                sendJson(exchange, 404, Map.of("error", "Task not found"));
                return;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> body = MAPPER.readValue(exchange.getRequestBody(), Map.class);
            
            if (body.containsKey("status")) {
                TaskStatus newStatus = TaskStatus.valueOf((String) body.get("status"));
                // 状态转换校验
                if (!TaskStatus.isValidTransition(task.getStatus(), newStatus)) {
                    sendJson(exchange, 422, Map.of(
                        "error", "非法的状态转换: " + task.getStatus() + " → " + newStatus,
                        "currentStatus", task.getStatus().name(),
                        "requestedStatus", newStatus.name()
                    ));
                    return;
                }
                task.setStatus(newStatus);
                // 自动记录时间戳
                if (newStatus == TaskStatus.RUNNING || newStatus == TaskStatus.EXECUTING) {
                    task.setStartedAt(java.time.LocalDateTime.now());
                } else if (newStatus.isFinished()) {
                    task.setCompletedAt(java.time.LocalDateTime.now());
                }
            }
            
            Task updated = taskStore.update(task);
            
            // 广播 WebSocket 消息
            if (wsServer != null) {
                wsServer.broadcastTaskUpdate(updated.getId(), "status_changed", updated);
            }
            
            sendJson(exchange, 200, updated);
        }
        
        private void handleDeleteTask(HttpExchange exchange, String taskId) throws IOException {
            boolean deleted = taskStore.delete(taskId);
            if (deleted) {
                // 广播 WebSocket 消息
                if (wsServer != null) {
                    wsServer.broadcastTaskUpdate(taskId, "deleted", null);
                }
                sendJson(exchange, 204, null);
            } else {
                sendJson(exchange, 404, Map.of("error", "Task not found"));
            }
        }
        
        private void handleClearCompleted(HttpExchange exchange) throws IOException {
            int count = 0;
            for (Task t : taskStore.listByStatus(TaskStatus.COMPLETED)) {
                if (taskStore.delete(t.getId())) count++;
            }
            sendJson(exchange, 200, Map.of("deleted", count));
        }
    }
    
    /**
     * 发送 JSON 响应
     */
    private void sendJson(HttpExchange exchange, int status, Object data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        
        byte[] bytes;
        if (data == null) {
            bytes = new byte[0];
        } else {
            bytes = MAPPER.writeValueAsBytes(data);
        }
        
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    /**
     * 启动入口
     */
    public static void main(String[] args) throws IOException {
        TaskStore taskStore = TaskStore.getInstance();
        TaskApiServer server = new TaskApiServer(taskStore);
        server.start();
        
        logger.info("HTTP: http://localhost:8084");
        logger.info("WebSocket: ws://localhost:8081");
        logger.info("Press Ctrl+C to stop");
        
        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        
        // 保持主线程运行
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            // 忽略
        }
    }
}

package com.jwcode.core.a2a.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jwcode.core.a2a.model.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A2AServer — A2A 协议 HTTP 服务端骨架。
 *
 * <p>每个子Agent 启动一个 A2AServer 来接收 Orchestrator 的远程调用。
 * 支持 Agent Card 查询、任务提交、状态查询、SSE 流式推送。</p>
 *
 * <p>启动方式：</p>
 * <pre>
 * A2AServer server = new A2AServer("Coder", 9101, agentInstance);
 * server.start();
 * </pre>
 */
public class A2AServer {

    private static final Logger logger = Logger.getLogger(A2AServer.class.getName());

    private final String agentName;
    private final int port;
    private final ObjectMapper objectMapper;
    private final HttpServer httpServer;
    private final AgentCard agentCard;
    private final Map<String, A2ATask> taskStore;
    private final ExecutorService executor;

    /** 任务处理器接口 — 实际执行任务的回调 */
    private TaskHandler taskHandler;

    public A2AServer(String agentName, int port, AgentCard agentCard) {
        this.agentName = agentName;
        this.port = port;
        this.agentCard = agentCard;
        this.taskStore = new ConcurrentHashMap<>();
        this.executor = Executors.newCachedThreadPool();
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

        try {
            this.httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            setupRoutes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create A2A server on port " + port, e);
        }
    }

    /**
     * 设置任务处理器
     */
    public void setTaskHandler(TaskHandler taskHandler) {
        this.taskHandler = taskHandler;
    }

    /**
     * 注册 HTTP 路由
     */
    private void setupRoutes() {
        // Agent Card 查询
        httpServer.createContext("/a2a/card", this::handleCardRequest);

        // 健康检查
        httpServer.createContext("/a2a/health", this::handleHealthCheck);

        // 任务提交
        httpServer.createContext("/a2a/task", this::handleTaskSubmit);

        // 任务状态查询
        httpServer.createContext("/a2a/status", this::handleTaskStatus);

        // 任务取消
        httpServer.createContext("/a2a/cancel", this::handleTaskCancel);

        // SSE 流式推送
        httpServer.createContext("/a2a/stream", this::handleStream);

        logger.info("A2AServer: routes registered for agent " + agentName);
    }

    /**
     * 启动服务
     */
    public void start() {
        httpServer.setExecutor(executor);
        httpServer.start();
        logger.info("A2AServer: agent '" + agentName + "' started on port " + port);
        System.out.println("A2A Server [" + agentName + "] running on http://localhost:" + port);
    }

    /**
     * 停止服务
     */
    public void stop() {
        httpServer.stop(0);
        executor.shutdown();
        logger.info("A2AServer: agent '" + agentName + "' stopped");
    }

    public int getPort() { return port; }
    public String getAgentName() { return agentName; }

    // ==================== HTTP Handlers ====================

    /**
     * GET /a2a/card — 返回 Agent Card
     */
    private void handleCardRequest(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(agentCard);
            sendResponse(exchange, 200, json);
        } catch (Exception e) {
            sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * GET /a2a/health — 健康检查
     */
    private void handleHealthCheck(HttpExchange exchange) throws IOException {
        sendResponse(exchange, 200, "{\"status\":\"ok\",\"agent\":\"" + agentName + "\",\"timestamp\":\"" + LocalDateTime.now() + "\"}");
    }

    /**
     * POST /a2a/task — 提交任务
     */
    @SuppressWarnings("unchecked")
    private void handleTaskSubmit(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            String body = readRequestBody(exchange);
            Map<String, Object> request = objectMapper.readValue(body, Map.class);

            String taskId = (String) request.getOrDefault("taskId", UUID.randomUUID().toString().substring(0, 8));
            String skillId = (String) request.get("skillId");
            String description = (String) request.get("description");
            Map<String, Object> input = (Map<String, Object>) request.get("input");

            if (skillId == null) {
                sendResponse(exchange, 400, "{\"error\":\"skillId is required\"}");
                return;
            }

            // 创建任务
            A2ATask task = A2ATask.builder()
                .taskId(taskId)
                .skillId(skillId)
                .description(description != null ? description : "")
                .input(input != null ? input : Collections.emptyMap())
                .status(A2ATask.TaskStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            taskStore.put(taskId, task);

            logger.info("A2AServer: received task " + taskId + " (skill: " + skillId + ")");

            // 异步执行
            executor.submit(() -> executeTask(task));

            // 立即返回任务已接收
            Map<String, Object> response = new HashMap<>();
            response.put("taskId", taskId);
            response.put("status", "PENDING");
            response.put("message", "Task accepted");

            sendResponse(exchange, 202, objectMapper.writeValueAsString(response));

        } catch (Exception e) {
            logger.warning("A2AServer: task submit error: " + e.getMessage());
            sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * GET /a2a/status?taskId=xxx — 查询任务状态
     */
    private void handleTaskStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            String query = exchange.getRequestURI().getQuery();
            String taskId = extractQueryParam(query, "taskId");

            if (taskId == null) {
                sendResponse(exchange, 400, "{\"error\":\"taskId is required\"}");
                return;
            }

            A2ATask task = taskStore.get(taskId);
            if (task == null) {
                sendResponse(exchange, 404, "{\"error\":\"Task not found: " + taskId + "\"}");
                return;
            }

            Map<String, Object> response = new HashMap<>();
            response.put("taskId", task.getTaskId());
            response.put("skillId", task.getSkillId());
            response.put("status", task.getStatus().name());
            response.put("description", task.getDescription());
            response.put("createdAt", task.getCreatedAt().toString());
            response.put("updatedAt", task.getUpdatedAt().toString());

            if (task.getOutput() != null) {
                response.put("summary", task.getOutput().getSummary());
            }
            if (task.getErrorMessage() != null) {
                response.put("error", task.getErrorMessage());
            }

            sendResponse(exchange, 200, objectMapper.writeValueAsString(response));

        } catch (Exception e) {
            sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * POST /a2a/cancel — 取消任务
     */
    private void handleTaskCancel(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            String body = readRequestBody(exchange);
            @SuppressWarnings("unchecked")
            Map<String, Object> request = objectMapper.readValue(body, Map.class);
            String taskId = (String) request.get("taskId");

            if (taskId == null) {
                sendResponse(exchange, 400, "{\"error\":\"taskId is required\"}");
                return;
            }

            A2ATask task = taskStore.get(taskId);
            if (task == null) {
                sendResponse(exchange, 404, "{\"error\":\"Task not found\"}");
                return;
            }

            task.cancel();
            sendResponse(exchange, 200, "{\"status\":\"cancelled\",\"taskId\":\"" + taskId + "\"}");

        } catch (Exception e) {
            sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * GET /a2a/stream/{taskId} — SSE 流式推送任务进度
     */
    private void handleStream(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String taskId = path.substring(path.lastIndexOf('/') + 1);

        if (taskId.isEmpty()) {
            sendResponse(exchange, 400, "{\"error\":\"taskId is required\"}");
            return;
        }

        A2ATask task = taskStore.get(taskId);
        if (task == null) {
            sendResponse(exchange, 404, "{\"error\":\"Task not found\"}");
            return;
        }

        // 设置 SSE 响应头
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);

        OutputStream os = exchange.getResponseBody();
        Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);

        try {
            // 发送初始状态
            writer.write("data: {\"status\":\"" + task.getStatus().name() + "\",\"taskId\":\"" + taskId + "\"}\n\n");
            writer.flush();

            // 轮询任务状态直到完成
            while (!task.isTerminal()) {
                Thread.sleep(500);
                writer.write("data: {\"status\":\"" + task.getStatus().name() + "\",\"taskId\":\"" + taskId + "\"}\n\n");
                writer.flush();
            }

            // 发送最终结果
            Map<String, Object> finalData = new HashMap<>();
            finalData.put("status", task.getStatus().name());
            finalData.put("taskId", taskId);
            if (task.getOutput() != null) {
                finalData.put("summary", task.getOutput().getSummary());
            }
            if (task.getErrorMessage() != null) {
                finalData.put("error", task.getErrorMessage());
            }
            writer.write("data: " + objectMapper.writeValueAsString(finalData) + "\n\n");
            writer.write("event: done\ndata:\n\n");
            writer.flush();

        } catch (Exception e) {
            logger.warning("A2AServer: SSE stream error: " + e.getMessage());
        } finally {
            writer.close();
            os.close();
        }
    }

    // ==================== 任务执行 ====================

    /**
     * 执行任务（由 TaskHandler 回调实际逻辑）
     */
    private void executeTask(A2ATask task) {
        task.start();
        logger.info("A2AServer: executing task " + task.getTaskId());

        if (taskHandler != null) {
            try {
                TaskOutput output = taskHandler.handle(task);
                task.complete(output);
                logger.info("A2AServer: task " + task.getTaskId() + " completed");
            } catch (Exception e) {
                task.fail(e.getMessage());
                logger.warning("A2AServer: task " + task.getTaskId() + " failed: " + e.getMessage());
            }
        } else {
            // 没有 TaskHandler 时返回模拟结果
            try {
                Thread.sleep(500); // 模拟执行
                TaskOutput output = TaskOutput.success(
                    "Task executed by " + agentName + ": " + task.getDescription());
                task.complete(output);
            } catch (Exception e) {
                task.fail(e.getMessage());
            }
        }
    }

    // ==================== 辅助方法 ====================

    private void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining());
        }
    }

    private String extractQueryParam(String query, String param) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(param)) {
                return kv[1];
            }
        }
        return null;
    }

    // ==================== 函数式接口 ====================

    /**
     * TaskHandler — 任务处理回调接口。
     * 子Agent 实现此接口来执行实际的任务逻辑。
     */
    @FunctionalInterface
    public interface TaskHandler {
        TaskOutput handle(A2ATask task) throws Exception;
    }
}

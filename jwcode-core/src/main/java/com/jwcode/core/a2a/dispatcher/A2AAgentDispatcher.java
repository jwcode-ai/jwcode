package com.jwcode.core.a2a.dispatcher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jwcode.core.a2a.model.*;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A2AAgentDispatcher — 基于 A2A 协议的远程 Agent 调度实现。
 *
 * <p>通过 HTTP/SSE 协议与远程 A2A Agent 服务通信。
 * 支持 Agent Card 发现、任务提交、状态轮询、流式进度接收。</p>
 *
 * <p>当远程 Agent 不可用时，可回退到 LocalAgentDispatcher。</p>
 */
public class A2AAgentDispatcher implements AgentDispatcher {

    private static final Logger logger = Logger.getLogger(A2AAgentDispatcher.class.getName());

    private final String registryEndpoint;
    private final Map<String, AgentCard> agentCards;
    private final Map<String, A2ATask> taskStore;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final HttpClient javaHttpClient;
    private final ExecutorService executor;
    private final Duration timeout;

    /** 默认 A2A 服务端口基址 */
    private static final int DEFAULT_BASE_PORT = 9100;

    public A2AAgentDispatcher(String registryEndpoint) {
        this.registryEndpoint = registryEndpoint;
        this.agentCards = new ConcurrentHashMap<>();
        this.taskStore = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.javaHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.executor = Executors.newCachedThreadPool();
        this.timeout = Duration.ofSeconds(30);
    }

    public A2AAgentDispatcher() {
        this("http://localhost:" + DEFAULT_BASE_PORT);
    }

    // ==================== Agent Card 发现 ====================

    /**
     * 从远程注册中心刷新 Agent Card 列表
     */
    public void refreshAgentCards() {
        try {
            String json = httpGet(registryEndpoint + "/agents");
            AgentCard[] cards = objectMapper.readValue(json, AgentCard[].class);
            for (AgentCard card : cards) {
                agentCards.put(card.getName(), card);
            }
            logger.info("A2ADispatcher: refreshed " + cards.length + " agent cards from " + registryEndpoint);
        } catch (Exception e) {
            logger.warning("A2ADispatcher: failed to refresh agent cards: " + e.getMessage());
        }
    }

    /**
     * 直接注册一个远程 Agent Card
     */
    public void registerAgent(AgentCard card) {
        agentCards.put(card.getName(), card);
        logger.fine("A2ADispatcher: registered remote agent: " + card.getName() +
                    " @ " + card.getEndpointUrl());
    }

    @Override
    public List<AgentCard> getAvailableAgents() {
        return List.copyOf(agentCards.values());
    }

    @Override
    public AgentCard findAgentBySkill(String skillId) {
        return agentCards.values().stream()
            .filter(card -> card.hasSkill(skillId))
            .findFirst()
            .orElse(null);
    }

    @Override
    public AgentCard findAgentByType(String agentType) {
        return agentCards.values().stream()
            .filter(card -> card.getAgentType().equalsIgnoreCase(agentType))
            .findFirst()
            .orElse(null);
    }

    // ==================== 任务调度 ====================

    @Override
    public CompletableFuture<TaskOutput> submitTask(String agentName, A2ATask task) {
        AgentCard card = agentCards.get(agentName);
        if (card == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Unknown remote agent: " + agentName));
        }

        String endpoint = card.getEndpointUrl();
        if (endpoint == null || endpoint.isEmpty()) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Agent " + agentName + " has no endpoint URL"));
        }

        task.start();
        taskStore.put(task.getTaskId(), task);

        logger.info("A2ADispatcher: submitting task " + task.getTaskId() +
                    " to remote agent " + agentName + " @ " + endpoint);

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 构建请求体
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("taskId", task.getTaskId());
                requestBody.put("skillId", task.getSkillId());
                requestBody.put("description", task.getDescription());
                requestBody.put("input", task.getInput());

                String json = objectMapper.writeValueAsString(requestBody);

                // 发送 HTTP POST 请求
                String responseJson = httpPost(endpoint + "/a2a/task", json);

                // 解析响应
                @SuppressWarnings("unchecked")
                Map<String, Object> response = objectMapper.readValue(responseJson, Map.class);

                String status = (String) response.getOrDefault("status", "COMPLETED");
                String summary = (String) response.getOrDefault("summary", "Task completed");

                TaskOutput output = TaskOutput.success(summary, response);

                if ("COMPLETED".equals(status)) {
                    task.complete(output);
                } else {
                    task.fail(summary);
                }

                logger.info("A2ADispatcher: task " + task.getTaskId() + " completed with status " + status);
                return output;

            } catch (Exception e) {
                task.fail(e.getMessage());
                logger.warning("A2ADispatcher: task " + task.getTaskId() + " failed: " + e.getMessage());
                throw new CompletionException(e);
            }
        }, executor);
    }

    @Override
    public TaskOutput submitTaskSync(String agentName, A2ATask task) {
        try {
            return submitTask(agentName, task).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            task.fail("Task timeout after " + timeout.toSeconds() + "s");
            return TaskOutput.success("Task timeout: " + task.getTaskId());
        } catch (Exception e) {
            task.fail(e.getMessage());
            return TaskOutput.success("Task failed: " + e.getMessage());
        }
    }

    @Override
    public A2ATask getTaskStatus(String taskId) {
        return taskStore.get(taskId);
    }

    @Override
    public boolean cancelTask(String taskId) {
        A2ATask task = taskStore.get(taskId);
        if (task != null && !task.isTerminal()) {
            task.cancel();
            // 尝试通知远程 Agent 取消
            try {
                httpPost(registryEndpoint + "/a2a/cancel", "{\"taskId\":\"" + taskId + "\"}");
            } catch (Exception e) {
                logger.fine("A2ADispatcher: failed to notify cancel: " + e.getMessage());
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isAvailable() {
        try {
            String response = httpGet(registryEndpoint + "/health");
            return response != null && response.contains("ok");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getName() {
        return "A2AAgentDispatcher";
    }

    @Override
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        httpClient.dispatcher().executorService().shutdown();
        logger.info("A2ADispatcher: shutdown complete");
    }

    // ==================== HTTP 辅助方法 ====================

    private String httpGet(String url) throws IOException {
        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP GET " + url + " returned " + response.code());
            }
            ResponseBody body = response.body();
            return body != null ? body.string() : "";
        }
    }

    private String httpPost(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP POST " + url + " returned " + response.code());
            }
            ResponseBody responseBody = response.body();
            return responseBody != null ? responseBody.string() : "";
        }
    }

    // ==================== SSE 流式支持 ====================

    /**
     * 通过 SSE 流式接收任务进度
     *
     * @param agentName Agent 名称
     * @param taskId    任务 ID
     * @param callback  进度回调
     */
    public void streamTaskProgress(String agentName, String taskId,
                                    Consumer<String> callback) {
        AgentCard card = agentCards.get(agentName);
        if (card == null || !card.getCapabilities().isStreaming()) {
            logger.warning("A2ADispatcher: agent " + agentName + " does not support streaming");
            return;
        }

        executor.submit(() -> {
            try {
                String url = card.getEndpointUrl() + "/a2a/stream/" + taskId;
                Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

                httpClient.newCall(request).enqueue(new okhttp3.Callback() {
                    @Override
                    public void onFailure(@NotNull okhttp3.Call call, @NotNull IOException e) {
                        logger.warning("A2ADispatcher: SSE stream error: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(@NotNull okhttp3.Call call, @NotNull Response response) {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("data: ")) {
                                    String data = line.substring(6);
                                    callback.accept(data);
                                }
                            }
                        } catch (IOException e) {
                            logger.warning("A2ADispatcher: SSE read error: " + e.getMessage());
                        }
                    }
                });
            } catch (Exception e) {
                logger.warning("A2ADispatcher: SSE stream error: " + e.getMessage());
            }
        });
    }

    /**
     * 函数式接口：消费型回调
     */
    @FunctionalInterface
    public interface Consumer<T> {
        void accept(T t);
    }
}

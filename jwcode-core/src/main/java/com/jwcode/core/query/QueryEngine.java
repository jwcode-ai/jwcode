package com.jwcode.core.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jwcode.common.util.Preconditions;
import com.jwcode.core.model.Message;
import com.jwcode.core.service.ApiClient;
import com.jwcode.core.service.ApiRequest;
import com.jwcode.core.service.ApiResponse;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.*;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * QueryEngine - 查询引擎（重构后）
 * 
 * 对标 JavaScript 项目的 QueryEngine
 * 实现完整的工具调用循环：请求 -> AI响应 -> 工具执行 -> 继续对话
 * 
 * 新特性：
 * - 支持 EngineConfig 配置化
 * - 可配置的最大循环次数（或禁用限制）
 * - 智能完成检测
 * - 循环检测
 * - Token 预算控制
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class QueryEngine {
    
    private static final Logger logger = Logger.getLogger(QueryEngine.class.getName());
    
    private final Session session;
    private final String model;
    private final boolean debug;
    private final ToolExecutor toolExecutor;
    private final ApiClient apiClient;
    private final ToolExecutionContext toolExecutionContext;
    private final EngineConfig engineConfig;
    
    private final Instant startTime;
    private final List<String> toolCallHistory;
    
    private QueryEngine(Session session, String model, boolean debug,
                        ToolExecutor toolExecutor, ApiClient apiClient, 
                        ToolExecutionContext toolExecutionContext,
                        EngineConfig engineConfig) {
        this.session = Preconditions.checkNotNull(session, "session cannot be null");
        this.model = Preconditions.checkNotNull(model, "model cannot be null");
        this.debug = debug;
        this.toolExecutor = toolExecutor;
        this.apiClient = apiClient;
        this.toolExecutionContext = toolExecutionContext;
        this.engineConfig = engineConfig != null ? engineConfig : EngineConfig.defaultConfig();
        
        this.startTime = Instant.now();
        this.toolCallHistory = new ArrayList<>();
    }
    
    /**
     * 执行查询（入口方法）
     */
    public CompletableFuture<QueryResult> query(String prompt) {
        Preconditions.checkNotNull(prompt, "prompt cannot be null");
        
        if (debug || engineConfig.isDebug()) {
            System.out.println("[QueryEngine] 开始处理查询：" + prompt);
            System.out.println("[QueryEngine] 配置：" + engineConfig);
        }
        
        Message userMessage = Message.createUserMessage(prompt);
        session.addMessage(userMessage);
        
        // 开始对话循环
        return runConversationLoop(0);
    }
    
    /**
     * 对话循环
     */
    private CompletableFuture<QueryResult> runConversationLoop(int iteration) {
        // 检查时间预算
        if (isTimeBudgetExceeded()) {
            Duration elapsed = Duration.between(startTime, Instant.now());
            String error = String.format("达到最大运行时间限制：%s", elapsed);
            logger.warning(error);
            return CompletableFuture.completedFuture(QueryResult.error(error));
        }
        
        // 检查迭代次数限制
        if (engineConfig.isIterationLimitEnabled() && 
            engineConfig.isIterationExceeded(iteration)) {
            String error = String.format("达到最大工具调用次数限制：%d", 
                engineConfig.getMaxIterations());
            logger.warning(error);
            return CompletableFuture.completedFuture(QueryResult.error(error));
        }
        
        // 检查循环检测
        if (engineConfig.isEnableLoopDetection() && isLooping()) {
            String error = "检测到循环调用模式，任务可能无法完成";
            logger.warning(error);
            return CompletableFuture.completedFuture(QueryResult.error(error));
        }
        
        // 构建请求
        ApiRequest request = ApiRequest.builder()
                .model(model)
                .messages(session.getMessages())
                .tools(toolExecutor.getEnabledTools())
                .build();
        
        // 发送请求并处理响应
        return apiClient.sendRequest(request)
                .thenCompose(response -> handleApiResponse(response, iteration));
    }
    
    /**
     * 处理 API 响应
     */
    private CompletableFuture<QueryResult> handleApiResponse(ApiResponse response, int iteration) {
        if (response.hasError()) {
            logger.severe("API 错误：" + response.getErrorMessage());
            return CompletableFuture.completedFuture(
                QueryResult.error("API 错误：" + response.getErrorMessage())
            );
        }
        
        // 获取 AI 的响应消息
        Message assistantMessage = response.toMessage();
        session.addMessage(assistantMessage);
        
        // 检查是否智能完成
        if (engineConfig.isEnableSmartCompletion() && 
            isTaskCompleted(assistantMessage)) {
            if (debug || engineConfig.isDebug()) {
                System.out.println("[QueryEngine] 检测到任务完成标记");
            }
            return CompletableFuture.completedFuture(QueryResult.success(assistantMessage));
        }
        
        // 检查是否有工具调用
        List<ApiResponse.ToolUse> toolUses = response.getToolUses();
        if (toolUses == null || toolUses.isEmpty()) {
            // 没有工具调用，直接返回结果
            if (debug || engineConfig.isDebug()) {
                System.out.println("[QueryEngine] AI 响应完成，无工具调用");
            }
            return CompletableFuture.completedFuture(QueryResult.success(assistantMessage));
        }
        
        // 有工具调用，执行工具
        if (debug || engineConfig.isDebug()) {
            System.out.println("[QueryEngine] AI 请求工具调用：" + toolUses.size() + " 个");
        }
        
        // 记录工具调用历史（用于循环检测）
        for (ApiResponse.ToolUse toolUse : toolUses) {
            toolCallHistory.add(toolUse.getName() + ":" + toolUse.getInput().hashCode());
        }
        
        return executeToolsAndContinue(toolUses, iteration + 1);
    }
    
    /**
     * 执行工具调用并继续对话
     */
    private CompletableFuture<QueryResult> executeToolsAndContinue(
            List<ApiResponse.ToolUse> toolUses, int nextIteration) {
        
        List<CompletableFuture<ToolExecutionInfo>> toolFutures = new ArrayList<>();
        
        for (ApiResponse.ToolUse toolUse : toolUses) {
            CompletableFuture<ToolExecutionInfo> future = executeSingleTool(toolUse);
            toolFutures.add(future);
        }
        
        // 等待所有工具执行完成
        return CompletableFuture.allOf(toolFutures.toArray(new CompletableFuture[0]))
                .thenCompose(v -> {
                    // 收集所有工具结果
                    List<ToolExecutionInfo> results = new ArrayList<>();
                    for (CompletableFuture<ToolExecutionInfo> future : toolFutures) {
                        results.add(future.join());
                    }
                    
                    // 添加工具结果到会话
                    boolean hasErrors = false;
                    StringBuilder errorMessages = new StringBuilder();
                    
                    for (ToolExecutionInfo result : results) {
                        String resultContent;
                        if (result.error() != null) {
                            resultContent = String.format(
                                "工具 %s 执行失败: %s",
                                result.toolName(),
                                result.error()
                            );
                            hasErrors = true;
                            errorMessages.append("- ").append(result.toolName())
                                        .append(": ").append(result.error()).append("\n");
                        } else {
                            if (result.result() != null) {
                                ToolResult<?> toolResult = result.result();
                                if (toolResult.getContent() != null) {
                                    resultContent = toolResult.getContent();
                                } else if (toolResult.getData() != null) {
                                    try {
                                        JsonNode dataJson = ToolSchemaGenerator.toJson(toolResult.getData());
                                        resultContent = dataJson.toString();
                                    } catch (Exception e) {
                                        resultContent = toolResult.getData().toString();
                                    }
                                } else {
                                    resultContent = toolResult.isSuccess() ? "成功" : "失败";
                                }
                            } else {
                                resultContent = "成功";
                            }
                        }
                        
                        Message toolResultMessage = Message.createToolResultMessage(
                            result.toolUseId(),
                            result.toolName(),
                            resultContent
                        );
                        session.addMessage(toolResultMessage);
                    }
                    
                    if (debug || engineConfig.isDebug()) {
                        if (errorMessages.length() > 0) {
                            System.out.println("[QueryEngine] 工具执行失败:\n" + errorMessages);
                        }
                        System.out.println("[QueryEngine] 继续对话循环（迭代 " + nextIteration + "）");
                    }
                    
                    if (hasErrors) {
                        String errorFeedback = String.format(
                            "以上工具执行出现错误，请分析错误原因并修正参数后重新调用。\n\n错误详情:\n%s",
                            errorMessages.toString()
                        );
                        Message errorFeedbackMsg = Message.createUserMessage(errorFeedback);
                        session.addMessage(errorFeedbackMsg);
                    }
                    
                    return runConversationLoop(nextIteration);
                });
    }
    
    /**
     * 执行单个工具
     */
    private CompletableFuture<ToolExecutionInfo> executeSingleTool(ApiResponse.ToolUse toolUse) {
        String toolName = toolUse.getName();
        String toolUseId = toolUse.getId();
        JsonNode input = convertToJsonNode(toolUse.getInput());
        
        if (debug || engineConfig.isDebug()) {
            System.out.println("[QueryEngine] 执行工具：" + toolName);
        }
        
        return toolExecutor.execute(toolName, input, toolExecutionContext)
                .thenApply(result -> {
                    if (result.isSuccess()) {
                        logger.fine("工具执行成功：" + toolName);
                        return new ToolExecutionInfo(
                            toolUseId,
                            toolName,
                            result.getResult(),
                            null
                        );
                    } else {
                        logger.warning("工具执行失败：" + toolName + " - " + result.getErrorMessage());
                        return new ToolExecutionInfo(
                            toolUseId,
                            toolName,
                            ToolResult.error(result.getErrorMessage()),
                            result.getErrorMessage()
                        );
                    }
                })
                .exceptionally(throwable -> {
                    logger.severe("工具执行异常：" + toolName + " - " + throwable.getMessage());
                    return new ToolExecutionInfo(
                        toolUseId,
                        toolName,
                        ToolResult.error("执行异常：" + throwable.getMessage()),
                        throwable.getMessage()
                    );
                });
    }
    
    /**
     * 检查时间预算是否超限
     */
    private boolean isTimeBudgetExceeded() {
        Duration elapsed = Duration.between(startTime, Instant.now());
        return elapsed.compareTo(engineConfig.getMaxDuration()) > 0;
    }
    
    /**
     * 检查是否陷入循环
     */
    private boolean isLooping() {
        if (toolCallHistory.size() < engineConfig.getLoopDetectionThreshold() * 2) {
            return false;
        }
        
        int threshold = engineConfig.getLoopDetectionThreshold();
        int size = toolCallHistory.size();
        
        // 检查最近 threshold 次调用是否重复
        List<String> recent = toolCallHistory.subList(size - threshold, size);
        List<String> previous = toolCallHistory.subList(size - threshold * 2, size - threshold);
        
        return recent.equals(previous);
    }
    
    /**
     * 检测任务是否已完成
     */
    private boolean isTaskCompleted(Message message) {
        String content = getMessageContent(message);
        
        // 检测完成标记
        String[] completionMarkers = {
            "<task_complete>",
            "<done>",
            "任务完成",
            "已完成",
            "完成！"
        };
        
        for (String marker : completionMarkers) {
            if (content.contains(marker)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 获取消息文本内容
     */
    private String getMessageContent(Message message) {
        if (message.getContent() == null || message.getContent().isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (Message.ContentBlock block : message.getContent()) {
            if (block instanceof Message.TextContent) {
                sb.append(((Message.TextContent) block).getText());
            }
        }
        return sb.toString();
    }
    
    /**
     * 工具执行信息记录
     */
    private record ToolExecutionInfo(
        String toolUseId,
        String toolName,
        ToolResult<?> result,
        String error
    ) {}
    
    // Getters
    public Session getSession() { return session; }
    public String getModel() { return model; }
    public boolean isDebug() { return debug; }
    public ToolExecutor getToolExecutor() { return toolExecutor; }
    public ApiClient getApiClient() { return apiClient; }
    public ToolExecutionContext getToolExecutionContext() { return toolExecutionContext; }
    public EngineConfig getEngineConfig() { return engineConfig; }
    
    public static class Builder {
        private Session session;
        private String model = "sonnet";
        private boolean debug = false;
        private ToolExecutor toolExecutor;
        private ApiClient apiClient;
        private ToolRegistry toolRegistry;
        private EngineConfig engineConfig;
        
        public Builder session(Session session) { this.session = session; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder debug(boolean debug) { this.debug = debug; return this; }
        public Builder toolExecutor(ToolExecutor toolExecutor) { this.toolExecutor = toolExecutor; return this; }
        public Builder apiClient(ApiClient apiClient) { this.apiClient = apiClient; return this; }
        public Builder toolRegistry(ToolRegistry toolRegistry) { this.toolRegistry = toolRegistry; return this; }
        public Builder engineConfig(EngineConfig engineConfig) { this.engineConfig = engineConfig; return this; }
        
        /**
         * 使用无限制配置（不限制迭代次数）
         */
        public Builder unlimitedIterations() {
            this.engineConfig = EngineConfig.unlimited();
            return this;
        }
        
        public QueryEngine build() {
            Preconditions.checkNotNull(session, "session is required");
            if (toolExecutor == null) {
                toolExecutor = new ToolExecutor(toolRegistry != null ? toolRegistry : ToolRegistry.createDefault());
            }
            if (apiClient == null) {
                apiClient = new ApiClient();
            }
            if (engineConfig == null) {
                engineConfig = EngineConfig.defaultConfig();
            }
            ToolExecutionContext toolExecutionContext = new ToolExecutionContext(
                    session,
                    java.nio.file.Path.of(System.getProperty("user.dir")),
                    null
            );
            return new QueryEngine(session, model, debug, toolExecutor, apiClient, toolExecutionContext, engineConfig);
        }
    }
    
    /**
     * 将 Map 转换为 JsonNode
     */
    private JsonNode convertToJsonNode(Map<String, Object> map) {
        if (map == null) {
            return JsonNodeFactory.instance.nullNode();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.valueToTree(map);
        } catch (Exception e) {
            logger.warning("Map 转换为 JsonNode 失败: " + e.getMessage());
            return JsonNodeFactory.instance.pojoNode(map);
        }
    }
    
    public static Builder builder() { return new Builder(); }
}

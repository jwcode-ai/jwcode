package com.jwcode.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.model.Message;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.*;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.jwcode.core.service.ContextWindowManager;

/**
 * 基于 LLM 服务的查询引擎
 * 
 * 完全替换旧的 QueryEngine，使用新的 LLM 服务层
 */
public class LLMQueryEngine {
    
    private static final Logger logger = Logger.getLogger(LLMQueryEngine.class.getName());
    
    private final Session session;
    private final LLMService llmService;
    private final ToolExecutor toolExecutor;
    private final ToolExecutionContext toolContext;
    private final EngineConfig config;
    private StepCallback stepCallback;
    
    private final Instant startTime;
    private final List<String> toolCallHistory;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    public LLMQueryEngine(Session session, LLMService llmService, 
                          ToolExecutor toolExecutor, EngineConfig config) {
        this.session = session;
        this.llmService = llmService;
        this.toolExecutor = toolExecutor;
        this.config = config != null ? config : EngineConfig.defaultConfig();
        this.toolContext = new ToolExecutionContext(
            session,
            java.nio.file.Path.of(System.getProperty("user.dir")),
            null
        );
        this.startTime = Instant.now();
        this.toolCallHistory = new ArrayList<>();
    }
    
    /**
     * 设置步骤回调，用于在执行过程中报告进度
     */
    public void setStepCallback(StepCallback callback) {
        this.stepCallback = callback;
    }
    
    /**
     * 步骤回调接口
     */
    public interface StepCallback {
        void onStepStart(String stepName, String description);
        void onStepThinking(String stepName, String thought);
        void onStepAction(String stepName, String action);
        void onStepComplete(String stepName, String result);
    }
    
    /**
     * 执行查询
     */
    public CompletableFuture<QueryResult> query(String prompt) {
        logger.info("[LLMQueryEngine] Query: " + prompt);
        
        // 触发回调：开始查询
        if (stepCallback != null) {
            stepCallback.onStepStart("LLM查询", "正在分析问题并制定解决方案...");
        }
        
        // 添加用户消息到会话
        session.addMessage(Message.createUserMessage(prompt));
        
        // 开始对话循环，初始空回复计数为 0
        return runConversationLoop(0, 0);
    }
    
    // 空回复限制次数
    private static final int MAX_EMPTY_RESPONSES = 3;
    // 结束标记
    private static final String FINISH_MARKER = "[FINISH]";
    
    /**
     * 对话循环
     * 
     * @param iteration 当前迭代次数
     * @param emptyResponseCount 连续空回复次数
     */
    private CompletableFuture<QueryResult> runConversationLoop(int iteration, int emptyResponseCount) {
        // 检查迭代限制
        if (config.getMaxIterations() > 0 && iteration >= config.getMaxIterations()) {
            logger.warning("[LLMQueryEngine] Max iterations reached: " + config.getMaxIterations());
            triggerStepComplete("LLM查询", "达到最大迭代次数限制");
            return CompletableFuture.completedFuture(
                QueryResult.error("达到最大迭代次数限制")
            );
        }
        
        // 检查超时
        if (config.getTimeout() != null) {
            Duration elapsed = Duration.between(startTime, Instant.now());
            if (elapsed.compareTo(config.getTimeout()) > 0) {
                logger.warning("[LLMQueryEngine] Timeout after " + elapsed);
                triggerStepComplete("LLM查询", "查询超时");
                return CompletableFuture.completedFuture(
                    QueryResult.error("查询超时")
                );
            }
        }
        
        // 转换会话消息到 LLM 格式
        List<LLMMessage> llmMessages = convertSessionMessages(session);
        
        logger.info("[LLMQueryEngine] Iteration " + iteration + ", messages: " + llmMessages.size());
        
        // 获取可用工具
        List<LLMTool> tools = convertTools(toolExecutor.getEnabledTools());
        
        // 触发回调：发送请求
        if (stepCallback != null) {
            if (iteration == 0) {
                stepCallback.onStepThinking("思考", "正在构建请求，发送 " + llmMessages.size() + " 条消息给AI模型...");
            } else {
                stepCallback.onStepThinking("分析", "继续对话循环 (第 " + iteration + " 轮)");
            }
        }
        
        // 发送请求
        CompletableFuture<LLMResponse> future = tools.isEmpty() 
            ? llmService.chat(llmMessages)
            : llmService.chatWithTools(llmMessages, tools);
        
        return future.thenCompose(response -> handleResponse(response, iteration, emptyResponseCount));
    }
    
    /**
     * 处理响应
     * 
     * @param response LLM 响应
     * @param iteration 当前迭代次数
     * @param emptyResponseCount 连续空回复次数
     */
    private CompletableFuture<QueryResult> handleResponse(LLMResponse response, int iteration, int emptyResponseCount) {
        if (response.hasError()) {
            logger.severe("[LLMQueryEngine] API error: " + response.getErrorMessage());
            triggerStepComplete("LLM查询", "API错误: " + response.getErrorMessage());
            return CompletableFuture.completedFuture(
                QueryResult.error(response.getErrorMessage())
            );
        }
        
        logger.info("[LLMQueryEngine] Response received, content length: " + 
            (response.getContent() != null ? response.getContent().length() : 0));
        
        // 创建助手消息
        Message assistantMessage;
        if (response.hasToolCalls()) {
            // 有工具调用
            assistantMessage = Message.createAssistantMessageWithToolCalls(
                response.getContent(),
                convertToolCalls(response.getToolCalls()),
                response.getReasoningContent()
            );
            
            // 添加到会话
            session.addMessage(assistantMessage);
            
            // 触发回调：准备调用工具
            if (stepCallback != null) {
                stepCallback.onStepThinking("分析", "AI决定调用 " + response.getToolCalls().size() + " 个工具");
            }
            
            // 执行工具调用
            return executeToolCalls(response.getToolCalls(), iteration + 1, emptyResponseCount);
        } else {
            // 没有工具调用
            String content = response.getContent();
            assistantMessage = Message.createAssistantMessage(content);
            session.addMessage(assistantMessage);
            
            // 检查是否有 finishReason
            if (response.getFinishReason() != null) {
                triggerStepComplete("LLM查询", "完成回复");
                return CompletableFuture.completedFuture(
                    QueryResult.success(assistantMessage)
                );
            }
            
            // 检查回复内容是否包含结束标记
            if (content != null && content.contains(FINISH_MARKER)) {
                logger.info("[LLMQueryEngine] 检测到结束标记 " + FINISH_MARKER + "，结束对话");
                triggerStepComplete("LLM查询", "完成回复");
                return CompletableFuture.completedFuture(
                    QueryResult.success(assistantMessage)
                );
            }
            
            // 检查是否为空回复
            if (content == null || content.trim().isEmpty()) {
                emptyResponseCount++;
                logger.warning("[LLMQueryEngine] 空回复 (第 " + emptyResponseCount + "/" + MAX_EMPTY_RESPONSES + " 次)");
                
                if (emptyResponseCount >= MAX_EMPTY_RESPONSES) {
                    logger.warning("[LLMQueryEngine] 空回复次数已达上限，强制结束对话");
                    triggerStepComplete("LLM查询", "空回复过多，已自动结束");
                    return CompletableFuture.completedFuture(
                        QueryResult.error("对话无响应，已自动结束")
                    );
                }
            } else {
                // 有有效内容，重置空回复计数
                emptyResponseCount = 0;
                
                // 添加提示消息，提醒 AI 使用结束标记
                session.addMessage(Message.createSystemMessage(
                    "提示：如果任务已完成，请在回复末尾添加 [FINISH] 标记以结束对话。例如：\"任务已完成。\n\n[FINISH]\""
                ));
            }
            
            // 没有 finishReason，继续对话循环
            return runConversationLoop(iteration + 1, emptyResponseCount);
        }
    }
    
    /**
     * 执行工具调用
     * 
     * @param toolCalls 工具调用列表
     * @param nextIteration 下一轮迭代次数
     * @param emptyResponseCount 连续空回复次数
     */
    private CompletableFuture<QueryResult> executeToolCalls(List<LLMMessage.ToolCall> toolCalls, int nextIteration, int emptyResponseCount) {
        logger.info("[LLMQueryEngine] Executing " + toolCalls.size() + " tool calls");
        
        List<CompletableFuture<ToolExecutionResult>> futures = new ArrayList<>();
        int toolIndex = 1;
        
        for (LLMMessage.ToolCall tc : toolCalls) {
            String toolName = tc.getFunction().getName();
            
            // 触发回调：开始执行工具
            if (stepCallback != null) {
                stepCallback.onStepAction("工具调用", "执行 " + toolName + " (第 " + toolIndex + "/" + toolCalls.size() + " 个)");
            }
            
            // 记录工具调用历史
            toolCallHistory.add(toolName + ":" + tc.getFunction().getArguments());
            
            // 查找并执行工具
            Tool<?, ?, ?> tool = findTool(toolName);
            if (tool == null) {
                logger.warning("[LLMQueryEngine] Tool not found: " + toolName);
                // 触发回调：工具未找到
                if (stepCallback != null) {
                    stepCallback.onStepComplete("工具执行", "未找到工具: " + toolName);
                }
                // 必须添加错误结果，否则 assistant 的 tool_calls 会缺少对应的 tool 消息
                futures.add(CompletableFuture.completedFuture(
                    new ToolExecutionResult(tc.getId(), toolName,
                        tc.getFunction().getArguments(), "Error: Tool not found: " + toolName)
                ));
                toolIndex++;
                continue;
            }
            
            // 异步执行工具（传入输入参数）
            CompletableFuture<ToolExecutionResult> future = executeToolAsync(tool, tc);
            futures.add(future);
            toolIndex++;
        }
        
        // 等待所有工具执行完成
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenCompose(v -> {
                // 添加工具结果到会话
                int resultIndex = 1;
                for (CompletableFuture<ToolExecutionResult> future : futures) {
                    try {
                        ToolExecutionResult result = future.get();
                        
                        // 触发回调：工具执行完成
                        if (stepCallback != null) {
                            String resultPreview = truncate(result.getResult(), 100);
                            stepCallback.onStepComplete("工具执行", result.getToolName() + " → " + resultPreview);
                        }
                        
                        // 添加工具结果消息（包含输入参数）
                        Message toolResultMsg = Message.createToolResultMessage(
                            result.getToolCallId(),
                            result.getToolName(),
                            result.getInputArguments(),  // 新增：传递输入参数
                            result.getResult()
                        );
                        session.addMessage(toolResultMsg);
                        
                    } catch (Exception e) {
                        logger.severe("[LLMQueryEngine] Failed to get tool result: " + e.getMessage());
                        if (stepCallback != null) {
                            stepCallback.onStepComplete("工具执行", "执行失败: " + e.getMessage());
                        }
                        // 即使获取结果失败，也必须添加工具结果消息以保持 tool_calls 与 results 数量一致
                        // 使用第一个工具调用的 ID 作为回退
                        String fallbackToolCallId = "unknown";
                        if (resultIndex <= futures.size() && toolCalls != null && resultIndex <= toolCalls.size()) {
                            fallbackToolCallId = toolCalls.get(resultIndex - 1).getId();
                        }
                        Message errorMsg = Message.createToolResultMessage(
                            fallbackToolCallId,
                            "system",
                            "Error: Failed to get tool result - " + e.getMessage()
                        );
                        session.addMessage(errorMsg);
                    }
                }
                
                // 触发回调：继续分析
                if (stepCallback != null) {
                    stepCallback.onStepThinking("分析", "工具执行完成，继续分析结果...");
                }
                
                // 继续对话循环（重置空回复计数，因为工具执行可能有有效输出）
                return runConversationLoop(nextIteration, 0);
            });
    }
    
    /**
     * 触发步骤完成回调
     */
    private void triggerStepComplete(String stepName, String result) {
        if (stepCallback != null) {
            stepCallback.onStepComplete(stepName, result);
        }
    }
    
    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * 异步执行工具
     * 
     * 通过 ToolExecutor 真正执行工具调用，将 JSON 参数解析后委托给注册的工具实现，
     * 并将执行结果序列化为字符串返回给 LLM。
     */
    private CompletableFuture<ToolExecutionResult> executeToolAsync(
            Tool<?, ?, ?> tool, 
            LLMMessage.ToolCall tc) {
        
        String toolCallId = tc.getId();
        String toolName = tc.getFunction().getName();
        String args = tc.getFunction().getArguments();
        
        logger.info("[LLMQueryEngine] Executing tool: " + toolName);
        logger.info("[LLMQueryEngine] Tool input arguments: " + args);
        
        // 解析 JSON 参数
        final JsonNode argsNode;
        try {
            argsNode = MAPPER.readTree(args);
        } catch (Exception e) {
            logger.warning("[LLMQueryEngine] Failed to parse tool arguments as JSON: " + e.getMessage());
            return CompletableFuture.completedFuture(
                new ToolExecutionResult(toolCallId, toolName, args,
                    "Error: Invalid tool arguments - " + e.getMessage())
            );
        }
        
        // 通过 ToolExecutor 真正执行工具，保持 CompletableFuture 链式调用
        return toolExecutor.execute(toolName, argsNode, toolContext)
            .thenApply(execResult -> {
                String resultContent;
                if (execResult.isSuccess()) {
                    ToolResult<?> toolResult = execResult.getResult();
                    if (toolResult != null && toolResult.isSuccess()) {
                        Object data = toolResult.getData();
                        if (data != null) {
                            try {
                                resultContent = MAPPER.valueToTree(data).toString();
                            } catch (Exception e) {
                                resultContent = data.toString();
                            }
                        } else {
                            resultContent = "Success";
                        }
                    } else {
                        String error = toolResult != null ? toolResult.getContent() : "Tool execution failed";
                        resultContent = "Error: " + error;
                    }
                } else {
                    resultContent = "Error: " + execResult.getErrorMessage();
                }
                return new ToolExecutionResult(toolCallId, toolName, args, resultContent);
            })
            .exceptionally(e -> {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                logger.severe("[LLMQueryEngine] Tool execution failed: " + cause.getMessage());
                return new ToolExecutionResult(toolCallId, toolName, args, 
                    "Error: " + cause.getMessage());
            });
    }
    
    /**
     * 查找工具
     */
    private Tool<?, ?, ?> findTool(String name) {
        for (Tool<?, ?, ?> tool : toolExecutor.getEnabledTools()) {
            if (tool.getName().equals(name)) {
                return tool;
            }
        }
        return null;
    }
    
    /**
     * 上下文窗口管理器 - 用于自动压缩消息历史
     * 默认限制 50 条消息，避免超出 LLM 的上下文窗口
     */
    private static final ContextWindowManager contextWindowManager = 
        new ContextWindowManager(
            ContextWindowManager.DEFAULT_CONTEXT_LIMIT,  // Token 限制
            50,   // 最大消息数：降低到 50（原 100 导致问题）
            4     // 最小保留消息数
        );
    
    /**
     * 转换会话消息到 LLM 格式
     * 
     * 关键修复：自动使用 ContextWindowManager 压缩消息历史，
     * 防止消息数量超出 LLM 的上下文窗口限制（100 条）
     */
    private List<LLMMessage> convertSessionMessages(Session session) {
        List<Message> messages = session.getMessages();
        
        // 检查并自动压缩消息历史
        if (messages.size() > 40) {  // 接近限制前就开始压缩
            logger.info("[LLMQueryEngine] 消息数量 " + messages.size() + " 接近限制，使用 ContextWindowManager 压缩");
            messages = contextWindowManager.prepareMessages(messages);
            logger.info("[LLMQueryEngine] 压缩后消息数量: " + messages.size());
        }
        
        List<LLMMessage> result = new ArrayList<>();
        
        for (Message msg : messages) {
            LLMMessage.Role role = convertRole(msg.getRole());
            
            if (role == null) continue;
            
            // 处理 TOOL 消息 - 需要提取 toolUseId
            if (role == LLMMessage.Role.TOOL) {
                String toolCallId = extractToolCallId(msg);
                String content = extractToolResultContent(msg);
                
                if (toolCallId == null || toolCallId.isEmpty()) {
                    logger.warning("[LLMQueryEngine] TOOL message missing toolCallId, skipping");
                    continue;
                }
                
                LLMMessage llmMsg = LLMMessage.tool(toolCallId, content);
                result.add(llmMsg);
            } else {
                // 处理其他消息类型
                String content = msg.getTextContent();
                if (content == null) content = "";
                
                // 处理带有 tool_calls 的 assistant 消息
                if (role == LLMMessage.Role.ASSISTANT && msg.hasToolCalls()) {
                    List<LLMMessage.ToolCall> toolCalls = convertToolCallInfoToLLM(msg.getToolCalls());
                    LLMMessage llmMsg = LLMMessage.assistantWithTools(content, toolCalls, msg.getReasoningContent());
                    result.add(llmMsg);
                } else {
                    LLMMessage llmMsg = LLMMessage.builder()
                        .role(role)
                        .content(content)
                        .build();
                    result.add(llmMsg);
                }
            }
        }
        
        return result;
    }
    
    /**
     * 从 TOOL 消息中提取 toolCallId
     */
    private String extractToolCallId(Message msg) {
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
            return null;
        }
        for (Message.ContentBlock block : msg.getContent()) {
            if (block instanceof Message.ToolResultContent) {
                return ((Message.ToolResultContent) block).getToolUseId();
            }
        }
        return null;
    }
    
    /**
     * 从 TOOL 消息中提取结果内容（使用格式化的完整内容）
     */
    private String extractToolResultContent(Message msg) {
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
            return "";
        }
        for (Message.ContentBlock block : msg.getContent()) {
            if (block instanceof Message.ToolResultContent) {
                Message.ToolResultContent trc = (Message.ToolResultContent) block;
                // 使用格式化方法，包含输入和输出
                return trc.getFormattedContent();
            }
        }
        return msg.getTextContent();
    }
    
    /**
     * 转换 ToolCallInfo 到 LLM ToolCall
     */
    private List<LLMMessage.ToolCall> convertToolCallInfoToLLM(
            List<Message.ToolCallInfo> toolCalls) {
        List<LLMMessage.ToolCall> result = new ArrayList<>();
        for (Message.ToolCallInfo info : toolCalls) {
            LLMMessage.ToolCall tc = LLMMessage.ToolCall.builder()
                .id(info.getId())
                .function(info.getName(), info.getArguments())
                .build();
            result.add(tc);
        }
        return result;
    }
    
    /**
     * 转换角色
     */
    private LLMMessage.Role convertRole(Message.Role role) {
        return switch (role) {
            case SYSTEM -> LLMMessage.Role.SYSTEM;
            case USER -> LLMMessage.Role.USER;
            case ASSISTANT -> LLMMessage.Role.ASSISTANT;
            case TOOL -> LLMMessage.Role.TOOL;
        };
    }
    
    /**
     * 转换工具
     */
    private List<LLMTool> convertTools(List<Tool<?, ?, ?>> tools) {
        List<LLMTool> result = new ArrayList<>();
        for (Tool<?, ?, ?> tool : tools) {
            LLMTool llmTool = new LLMTool();
            llmTool.setType("function");
            
            LLMTool.Function func = new LLMTool.Function();
            func.setName(tool.getName());
            func.setDescription(tool.getDescription());
            // 将 JsonNode 转换为 Map
            JsonNode inputSchema = tool.getInputSchema();
            if (inputSchema != null) {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> params = mapper.convertValue(inputSchema, Map.class);
                func.setParameters(params);
            } else {
                func.setParameters(null);
            }
            
            llmTool.setFunction(func);
            result.add(llmTool);
        }
        return result;
    }
    
    /**
     * 转换工具调用
     */
    private List<Message.ToolCallInfo> convertToolCalls(List<LLMMessage.ToolCall> toolCalls) {
        List<Message.ToolCallInfo> result = new ArrayList<>();
        for (LLMMessage.ToolCall tc : toolCalls) {
            Message.ToolCallInfo info = new Message.ToolCallInfo(
                tc.getId(),
                tc.getFunction().getName(),
                tc.getFunction().getArguments()
            );
            result.add(info);
        }
        return result;
    }
    
    // ==================== 数据类 ====================
    
    public static class EngineConfig {
        private int maxIterations = 100;
        private Duration timeout = Duration.ofMinutes(5);
        
        /**
         * 从 JwcodeConfig 创建 EngineConfig
         */
        public static EngineConfig fromJwcodeConfig(JwcodeConfig config) {
            EngineConfig engineConfig = new EngineConfig();
            if (config != null && config.getSettings() != null) {
                JwcodeConfig.EngineSettings engine = config.getSettings().getEngine();
                if (engine != null) {
                    engineConfig.setMaxIterations(engine.getMaxIterations());
                    engineConfig.setTimeout(Duration.ofMinutes(engine.getTimeoutMinutes()));
                }
            }
            return engineConfig;
        }
        
        public int getMaxIterations() { return maxIterations; }
        public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
        
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        
        public static EngineConfig defaultConfig() {
            return new EngineConfig();
        }
    }
    
    public static class QueryResult {
        private final boolean success;
        private final Message message;
        private final String errorMessage;
        
        public QueryResult(boolean success, Message message, String errorMessage) {
            this.success = success;
            this.message = message;
            this.errorMessage = errorMessage;
        }
        
        public boolean isSuccess() { return success; }
        public Message getMessage() { return message; }
        public String getErrorMessage() { return errorMessage; }
        
        public static QueryResult success(Message message) {
            return new QueryResult(true, message, null);
        }
        
        public static QueryResult error(String errorMessage) {
            return new QueryResult(false, null, errorMessage);
        }
    }
    
    /**
     * 工具执行结果（包含输入参数）
     */
    private static class ToolExecutionResult {
        private final String toolCallId;
        private final String toolName;
        private final String inputArguments;  // 新增：工具输入参数
        private final String result;
        
        public ToolExecutionResult(String toolCallId, String toolName, String inputArguments, String result) {
            this.toolCallId = toolCallId;
            this.toolName = toolName;
            this.inputArguments = inputArguments;
            this.result = result;
        }
        
        public String getToolCallId() { return toolCallId; }
        public String getToolName() { return toolName; }
        public String getInputArguments() { return inputArguments; }
        public String getResult() { return result; }
    }
    
    // ==================== Getters ====================
    
    public Session getSession() { return session; }
    public LLMService getLLMService() { return llmService; }
    public ToolExecutor getToolExecutor() { return toolExecutor; }
    public EngineConfig getConfig() { return config; }
    
    // ==================== Builder ====================
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private Session session;
        private LLMService llmService;
        private ToolExecutor toolExecutor;
        private ToolRegistry toolRegistry;
        private EngineConfig config;
        
        public Builder session(Session session) {
            this.session = session;
            return this;
        }
        
        public Builder llmService(LLMService llmService) {
            this.llmService = llmService;
            return this;
        }
        
        public Builder toolExecutor(ToolExecutor toolExecutor) {
            this.toolExecutor = toolExecutor;
            return this;
        }
        
        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = toolRegistry;
            return this;
        }
        
        public Builder config(EngineConfig config) {
            this.config = config;
            return this;
        }
        
        public LLMQueryEngine build() {
            if (session == null) {
                throw new IllegalArgumentException("Session is required");
            }
            if (llmService == null) {
                throw new IllegalArgumentException("LLMService is required");
            }
            if (toolExecutor == null) {
                toolExecutor = new ToolExecutor(toolRegistry != null ? toolRegistry : ToolRegistry.createDefault());
            }
            return new LLMQueryEngine(session, llmService, toolExecutor, config);
        }
    }
}

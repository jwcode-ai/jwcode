package com.jwcode.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.logging.Logger;

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
    
    private final Instant startTime;
    private final List<String> toolCallHistory;
    
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
     * 执行查询
     */
    public CompletableFuture<QueryResult> query(String prompt) {
        logger.info("[LLMQueryEngine] Query: " + prompt);
        
        // 添加用户消息到会话
        session.addMessage(Message.createUserMessage(prompt));
        
        // 开始对话循环
        return runConversationLoop(0);
    }
    
    /**
     * 对话循环
     */
    private CompletableFuture<QueryResult> runConversationLoop(int iteration) {
        // 检查迭代限制
        if (config.getMaxIterations() > 0 && iteration >= config.getMaxIterations()) {
            logger.warning("[LLMQueryEngine] Max iterations reached: " + config.getMaxIterations());
            return CompletableFuture.completedFuture(
                QueryResult.error("达到最大迭代次数限制")
            );
        }
        
        // 检查超时
        if (config.getTimeout() != null) {
            Duration elapsed = Duration.between(startTime, Instant.now());
            if (elapsed.compareTo(config.getTimeout()) > 0) {
                logger.warning("[LLMQueryEngine] Timeout after " + elapsed);
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
        
        // 发送请求
        CompletableFuture<LLMResponse> future = tools.isEmpty() 
            ? llmService.chat(llmMessages)
            : llmService.chatWithTools(llmMessages, tools);
        
        return future.thenCompose(response -> handleResponse(response, iteration));
    }
    
    /**
     * 处理响应
     */
    private CompletableFuture<QueryResult> handleResponse(LLMResponse response, int iteration) {
        if (response.hasError()) {
            logger.severe("[LLMQueryEngine] API error: " + response.getErrorMessage());
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
            
            // 执行工具调用
            return executeToolCalls(response.getToolCalls(), iteration + 1);
        } else {
            // 没有工具调用，直接返回
            assistantMessage = Message.createAssistantMessage(
                response.getContent()
            );
            session.addMessage(assistantMessage);
            
            return CompletableFuture.completedFuture(
                QueryResult.success(assistantMessage)
            );
        }
    }
    
    /**
     * 执行工具调用
     */
    private CompletableFuture<QueryResult> executeToolCalls(List<LLMMessage.ToolCall> toolCalls, int nextIteration) {
        logger.info("[LLMQueryEngine] Executing " + toolCalls.size() + " tool calls");
        
        List<CompletableFuture<ToolExecutionResult>> futures = new ArrayList<>();
        
        for (LLMMessage.ToolCall tc : toolCalls) {
            // 记录工具调用历史
            toolCallHistory.add(tc.getFunction().getName() + ":" + tc.getFunction().getArguments());
            
            // 查找并执行工具
            Tool<?, ?, ?> tool = findTool(tc.getFunction().getName());
            if (tool == null) {
                logger.warning("[LLMQueryEngine] Tool not found: " + tc.getFunction().getName());
                // 必须添加错误结果，否则 assistant 的 tool_calls 会缺少对应的 tool 消息
                futures.add(CompletableFuture.completedFuture(
                    new ToolExecutionResult(tc.getId(), tc.getFunction().getName(),
                        tc.getFunction().getArguments(), "Error: Tool not found: " + tc.getFunction().getName())
                ));
                continue;
            }
            
            // 异步执行工具（传入输入参数）
            CompletableFuture<ToolExecutionResult> future = executeToolAsync(tool, tc);
            futures.add(future);
        }
        
        // 等待所有工具执行完成
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenCompose(v -> {
                // 添加工具结果到会话
                for (CompletableFuture<ToolExecutionResult> future : futures) {
                    try {
                        ToolExecutionResult result = future.get();
                        
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
                    }
                }
                
                // 继续对话循环
                return runConversationLoop(nextIteration);
            });
    }
    
    /**
     * 异步执行工具
     */
    private CompletableFuture<ToolExecutionResult> executeToolAsync(
            Tool<?, ?, ?> tool, 
            LLMMessage.ToolCall tc) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String args = tc.getFunction().getArguments();
                logger.info("[LLMQueryEngine] Executing tool: " + tc.getFunction().getName());
                logger.info("[LLMQueryEngine] Tool input arguments: " + args);
                
                // TODO: 实际工具执行逻辑
                String result = "Tool executed: " + tc.getFunction().getName();
                
                // 返回包含输入参数的结果
                return new ToolExecutionResult(tc.getId(), tc.getFunction().getName(), args, result);
                
            } catch (Exception e) {
                logger.severe("[LLMQueryEngine] Tool execution failed: " + e.getMessage());
                return new ToolExecutionResult(tc.getId(), tc.getFunction().getName(), 
                    tc.getFunction().getArguments(), "Error: " + e.getMessage());
            }
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
     * 转换会话消息到 LLM 格式
     */
    private List<LLMMessage> convertSessionMessages(Session session) {
        List<LLMMessage> result = new ArrayList<>();
        
        for (Message msg : session.getMessages()) {
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

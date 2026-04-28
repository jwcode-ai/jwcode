package com.jwcode.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

import com.jwcode.core.observability.*;
import com.jwcode.core.resilience.RecoveryExecutor;
import com.jwcode.core.resilience.RecoveryProtocol;
import com.jwcode.core.service.ContextWindowManager;
import com.jwcode.core.service.SimpleCompactionStrategy;
import com.jwcode.core.task.TaskLifecycleManager;

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
    private final ObservationPipeline pipeline;
    private final TokenBudget tokenBudget;

    private final Instant startTime;
    private final List<String> toolCallHistory;
    private final SimpleCompactionStrategy compactionStrategy;
    private final TaskLifecycleManager taskLifecycleManager;
    private long lastSeenCompactCount = 0;
    private String pendingBudgetAdvice;
    // 去重：记录已注册的 StepCallbackAdapter，防止重复订阅导致日志重复
    private StepCallbackAdapter stepCallbackAdapter;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    
    public LLMQueryEngine(Session session, LLMService llmService, 
                          ToolExecutor toolExecutor, EngineConfig config) {
        this.session = session;
        this.llmService = llmService;
        this.toolExecutor = toolExecutor;
        this.config = config != null ? config : EngineConfig.defaultConfig();
        if (this.config.getMaxMessageHistory() > 0) {
            session.setMaxMessageHistory(this.config.getMaxMessageHistory());
        }
        this.toolContext = new ToolExecutionContext(
            session,
            java.nio.file.Path.of(System.getProperty("user.dir")),
            null
        );
        this.startTime = Instant.now();
        this.toolCallHistory = new ArrayList<>();
        this.pipeline = new DefaultObservationPipeline();
        this.tokenBudget = TokenBudget.of(this.config.getTokenBudget());
        this.compactionStrategy = new SimpleCompactionStrategy(llmService);
        this.taskLifecycleManager = new TaskLifecycleManager(llmService, this.pipeline);
    }
    
    /**
     * 设置步骤回调，用于在执行过程中报告进度
     * 去重：防止重复订阅相同类型的 Observer 导致日志重复
     */
    public void setStepCallback(StepCallback callback) {
        if (callback != null) {
            // 如果已有订阅者，先移除旧的在添加新的
            if (this.stepCallbackAdapter != null) {
                this.pipeline.unsubscribe(this.stepCallbackAdapter);
                logger.info("[LLMQueryEngine] Removed old StepCallbackAdapter before adding new one");
            }
            this.stepCallbackAdapter = new StepCallbackAdapter(callback);
            this.pipeline.subscribe(this.stepCallbackAdapter);
        }
    }
    
    /**
     * 获取可观测管道，用于注册自定义观察者
     */
    public ObservationPipeline getPipeline() {
        return pipeline;
    }
    
    /**
     * 步骤回调接口
     */
    public interface StepCallback {
        void onStepStart(String stepName, String description);
        void onStepThinking(String stepName, String thought);
        void onStepAction(String stepName, String action);
        void onStepComplete(String stepName, String result);
        
        void onToolResult(String toolName, String result);
        
        /**
         * 流式内容片段（实时推送生成的 content）
         */
        default void onContentChunk(String chunk) {}
        
        /**
         * 流式思考片段（实时推送 reasoning_content）
         */
        default void onThinkingChunk(String chunk) {}
        
        /**
         * 流式工具调用片段（实时推送部分工具调用参数）
         */
        default void onToolCallChunk(LLMService.StreamToolCallEvent event) {}
    }
    
    /**
     * 执行查询
     */
    public CompletableFuture<QueryResult> query(String prompt) {
        logger.info("[LLMQueryEngine] Query: " + prompt);
        
        // 【任务生命周期】意图检测与上下文重置
        TaskLifecycleManager.TaskIntent intent = taskLifecycleManager.detectIntent(session, prompt);
        switch (intent) {
            case NEW_TASK -> {
                taskLifecycleManager.startNewTask(session, prompt);
                tokenBudget.reset();
                lastSeenCompactCount = 0;
                pendingBudgetAdvice = null;
                logger.info("[LLMQueryEngine] 新任务已启动，上下文已重置");
            }
            case CLARIFICATION -> taskLifecycleManager.onUserInput(session, prompt);
            case CONTINUE -> {
                // 正常流程，不做特殊处理
            }
        }
        
        // 触发事件：开始查询
        pipeline.publish(new ObservationEvent.StepStart("LLM查询", "正在分析问题并制定解决方案..."));
        
        // 添加用户消息到会话
        session.addMessage(Message.createUserMessage(prompt));
        
        // 添加系统提示，强调文件编辑前必须先读取
        addFileEditGuidelines();
        
        // 开始对话循环，初始空回复计数为 0
        return runConversationLoop(0, 0);
    }
    
    /**
     * 添加文件编辑指南到系统提示
     */
    private void addFileEditGuidelines() {
        String guidelines = """
            【重要】文件编辑规则：
            
            1. 编辑任何文件前，必须先使用 FileReadTool 读取文件最新内容
            2. 禁止基于记忆或推测编辑文件，必须使用刚读取的实际内容
            3. 读取文件前，如果不确定文件路径或文件名，必须先使用 GlobTool 搜索确认，禁止猜测文件路径
            4. 如果工具执行失败，立即使用 FileReadTool 重新读取文件
            5. 检查错误信息中的文件内容提示，修正编辑指令
            6. 同一文件多次编辑失败时，考虑使用 GrepTool 搜索关键代码片段
            
            这些规则是为了避免"幻觉"问题 - 即 AI 基于不准确的文件内容生成编辑指令。
            
            【重要】任务结束规则：
            
            当任务完成时，你必须：
            1. 在回复末尾添加 [FINISH] 标记
            2. 简洁总结已完成的工作
            3. 停止调用任何工具，直接返回结果
            
            例如："所有文件修改已完成。[FINISH]"
            
            不要再继续分析或提出建议，直接结束对话。
            
            【重要】简单任务快速路径（避免过度工程化）：
            
            对于简单的文件操作，不要走"枚举→分类→逐个读取"的复杂路径，直接使用原子工具：
            
            - 批量读取多个文件 → 使用 BatchReadTool（替代逐个 FileReadTool）
            - 合并多个文件为一个 → 使用 MergeFilesTool（如：合并 md 文件）
            - 按模式搜索并复制/移动 → 使用 BashTool 或 PowerShellTool 的 shell 批处理
            - 简单统计/过滤文件内容 → 使用 BashTool 的 grep/find/awk 等（Linux）或 Select-String（Windows）
            
            原则：能用 1 轮工具调用完成的，绝不用 5 轮。
            
            【重要】任务清单执行规则：
            
            1. 如果存在任务清单，必须在每次回复开头简要汇报当前进度（如"步骤 2/5 已完成，正在执行步骤 3"）
            2. 每完成一个步骤，在回复中明确标注该步骤状态变更（如"✅ 步骤 X 完成"）
            3. 如果发现遗漏的工作，动态添加新步骤并继续执行
            4. 如果需要用户补充信息，使用 AskUserQuestionTool 主动提问，不要空等
            5. 遇到步骤失败时，优先尝试替代方案；若确实无法解决，标记失败并说明原因
            6. 所有步骤完成后，添加 [FINISH] 标记结束对话
            
            【重要】大任务拆分规则：
            
            当任务涉及多个独立子任务（如"同时修改多个不相关文件"、"并行分析多个模块"），你必须使用 AgentTool 创建子 Agent 并行执行：
            
            - 使用 AgentTool 的 execute 操作分配子任务
            - 每个子 Agent 拥有独立的上下文和迭代预算，不会消耗主 Agent 的资源
            - 子 Agent 完成后自动清理上下文，结果合并返回主 Agent
            - 适用于：批量代码审查、多文件重构、并行测试、跨模块分析
            
            示例：{"action": "execute", "tasks": [{"name": "review-auth", "role": "安全专家", "task": "审查认证模块"}, {"name": "review-api", "role": "API专家", "task": "审查接口模块"}]}
            """;
        
        session.addMessage(Message.createSystemMessage(guidelines));
    }

    /**
     * 重置 TokenBudget（通常在上下文压缩后调用）
     */
    public void resetTokenBudget() {
        tokenBudget.reset();
    }
    
    // 空回复限制次数（可通过 EngineConfig 覆盖，默认保持向后兼容）
    private static final int DEFAULT_MAX_EMPTY_RESPONSES = 3;
    // 结束标记
    private static final String FINISH_MARKER = "[FINISH]";
    // 系统提示去重检查窗口（最近 N 条消息）
    private static final int SYSTEM_PROMPT_DEDUP_WINDOW = 5;
    // 空回复时的引导提示
    private static final String EMPTY_RESPONSE_PROMPT = "你上一条回复为空。请继续完成当前任务，如果已完成请添加 [FINISH] 标记。";
    
    /**
     * 对话循环
     * 
     * @param iteration 当前迭代次数
     * @param emptyResponseCount 连续空回复次数
     */
    private CompletableFuture<QueryResult> runConversationLoop(int iteration, int emptyResponseCount) {
        // 检测会话是否被外部压缩（如 /compact 命令），如果是则重置 TokenBudget
        if (session.getCompactCount() > lastSeenCompactCount) {
            resetTokenBudget();
            lastSeenCompactCount = session.getCompactCount();
            logger.info("[LLMQueryEngine] Context compacted detected, TokenBudget reset.");
        }

        // 【任务生命周期】检查当前任务状态
        var activeTask = session.getActiveTask();
        if (activeTask != null && activeTask.getStatus() == com.jwcode.core.task.TaskStatus.WAITING_INPUT) {
            logger.info("[LLMQueryEngine] 任务处于 WAITING_INPUT 状态，暂停循环等待用户输入");
            return CompletableFuture.completedFuture(
                QueryResult.success(Message.createAssistantMessage(
                    "⏳ 等待用户补充信息：" + activeTask.getWaitingFor()))
            );
        }
        
        // 【任务生命周期】如果任务已规划但未开始执行，注入开始执行提示
        if (activeTask != null && activeTask.getStatus() == com.jwcode.core.task.TaskStatus.PLANNED && iteration == 0) {
            session.addMessage(Message.createSystemMessage(
                "任务清单已制定完成，请从第一个待办步骤开始执行。每完成一步请汇报进度。"
            ));
            activeTask.setStatus(com.jwcode.core.task.TaskStatus.EXECUTING);
            activeTask.advanceToNextStep();
        }

        // 检查 Token 预算
        if (tokenBudget.isExhausted()) {
            logger.warning("[LLMQueryEngine] Token budget exhausted: " + tokenBudget);
            triggerStepComplete("LLM查询", "Token 预算已耗尽");
            return CompletableFuture.completedFuture(
                QueryResult.error("Token 预算已耗尽，任务被迫终止。请使用 /compact 压缩上下文后重试。")
            );
        }

        // 自动触发上下文压缩（当预算使用超过 70% 时）
        if (tokenBudget.usageRatio() > 0.70 && session.getMessages().size() > 10) {
            logger.info("[LLMQueryEngine] Token budget usage=" + String.format("%.0f%%", tokenBudget.usageRatio() * 100)
                + ", auto-compacting context...");
            int beforeCount = session.getMessages().size();
            List<Message> compacted = compactionStrategy.compact(session.getMessages());
            if (compacted != null && compacted.size() < beforeCount) {
                session.clearMessages();
                compacted.forEach(session::addMessage);
                logger.info("[LLMQueryEngine] Auto-compacted: " + beforeCount + " -> " + session.getMessages().size()
                    + " messages, budget=" + String.format("%.0f%%", tokenBudget.usageRatio() * 100));
            } else {
                logger.warning("[LLMQueryEngine] Auto-compact skipped: result size not reduced (before=" + beforeCount + ")");
            }
        }

        // Token 预算紧张时缓存建议（将在 convertSessionMessages 中临时注入，不保存到 session 历史）
        if (iteration > 0) {
            String advice = tokenBudget.toPromptAdvice();
            if (!advice.isEmpty()) {
                logger.info("[LLMQueryEngine] Token budget advice pending");
                this.pendingBudgetAdvice = advice;
            }
        }

        // 后备：迭代限制检查（Token 预算的兜底安全网）
        if (config.getMaxIterations() > 0 && iteration >= config.getMaxIterations()) {
            logger.warning("[LLMQueryEngine] Max iterations reached: " + config.getMaxIterations());
            triggerStepComplete("LLM查询", "达到最大迭代次数限制");
            return CompletableFuture.completedFuture(
                QueryResult.error("达到最大迭代次数限制")
            );
        }
        
        // 已移除累计超时检查，由 HTTP 层超时控制（OpenAILLMService 中的 timeoutSeconds）
        // 单次 LLM 请求的超时由 HTTP 客户端处理，不受工具执行时间影响
        
        // 转换会话消息到 LLM 格式
        List<LLMMessage> llmMessages = convertSessionMessages(session);
        
        logger.info("[LLMQueryEngine] Iteration " + iteration + ", messages: " + llmMessages.size());
        
        // 获取可用工具
        List<LLMTool> tools = convertTools(toolExecutor.getEnabledTools());
        
        // 提醒使用 [FINISH] 标记，但避免重复堆积系统提示
        if (iteration < 20 && !hasRecentSystemPrompt("[FINISH]")) {
            session.addMessage(Message.createSystemMessage(
                "提示：如果任务已完成，请在回复末尾添加 [FINISH] 标记以结束对话。"
            ));
        }
        
        // 触发事件：发送请求
        if (iteration == 0) {
            pipeline.publish(new ObservationEvent.Thinking("思考", "正在构建请求，发送 " + llmMessages.size() + " 条消息给AI模型..."));
        } else {
            pipeline.publish(new ObservationEvent.Thinking("分析", "继续对话循环 (第 " + iteration + " 轮)"));
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

        // 消费 Token 预算并发布事件
        int promptTokens = response.getPromptTokens();
        int completionTokens = response.getCompletionTokens();
        if (promptTokens > 0 || completionTokens > 0) {
            tokenBudget.consume(promptTokens, completionTokens);
            pipeline.publish(new ObservationEvent.TokenUsage(promptTokens, completionTokens,
                response.getModel() != null ? response.getModel() : "unknown"));
            logger.info("[LLMQueryEngine] Token usage: +" + completionTokens +
                " completion (total used=" + tokenBudget.getUsedTotal() + "/" + tokenBudget.getTotalBudget() + ")");
        }

        // 打印 AI 思考内容
        if (response.getReasoningContent() != null && !response.getReasoningContent().isEmpty()) {
            String think = response.getReasoningContent();
            logger.info("[LLMQueryEngine] AI思考内容: " + think);
            System.out.println("🤔 AI思考: " + think);
        }
        
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
            
            // 触发事件：准备调用工具
            pipeline.publish(new ObservationEvent.Thinking("分析", "AI决定调用 " + response.getToolCalls().size() + " 个工具"));
            
            // 执行工具调用
            return executeToolCalls(response.getToolCalls(), iteration + 1, emptyResponseCount, this::runConversationLoop);
        } else {
            // 没有工具调用
            String content = response.getContent();
            assistantMessage = Message.createAssistantMessage(content, response.getReasoningContent());
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
                // 【任务生命周期】检查任务是否全部完成
                taskLifecycleManager.checkTaskCompletion(session);
                triggerStepComplete("LLM查询", "完成回复");
                return CompletableFuture.completedFuture(
                    QueryResult.success(assistantMessage)
                );
            }
            
            // 检查是否为空回复（reasoningContent 非空时视为有效思考，不算空回复）
            String reasoning = response.getReasoningContent();
            boolean hasReasoning = reasoning != null && !reasoning.trim().isEmpty();
            boolean isEmptyContent = content == null || content.trim().isEmpty();
            
            if (isEmptyContent && !hasReasoning) {
                emptyResponseCount++;
                int maxEmpty = config.getMaxEmptyResponses();
                logger.warning("[LLMQueryEngine] 空回复 (第 " + emptyResponseCount + "/" + maxEmpty + " 次)");
                
                if (emptyResponseCount >= maxEmpty) {
                    logger.warning("[LLMQueryEngine] 空回复次数已达上限，强制结束对话");
                    triggerStepComplete("LLM查询", "空回复过多，已自动结束");
                    return CompletableFuture.completedFuture(
                        QueryResult.error("对话无响应，已自动结束")
                    );
                }
                
                // 第一次/第二次空回复时，主动引导模型继续，而不是静默等待
                session.addMessage(Message.createSystemMessage(EMPTY_RESPONSE_PROMPT));
            } else {
                // 有有效内容或仅有思考内容，重置空回复计数
                emptyResponseCount = 0;
                
                // 仅在无重复提示时追加结束标记提醒
                if (!hasRecentSystemPrompt("[FINISH]")) {
                    session.addMessage(Message.createSystemMessage(
                        "提示：如果任务已完成，请在回复末尾添加 [FINISH] 标记以结束对话。例如：\"任务已完成。\n\n[FINISH]\""
                    ));
                }
            }
            
            // 没有 finishReason，继续对话循环
            return runConversationLoop(iteration + 1, emptyResponseCount);
        }
    }
    
    // ==================== 流式查询 ====================
    
    /**
     * 执行流式查询
     * 
     * @param prompt 用户输入
     * @param contentConsumer 内容片段消费回调
     * @param thinkingConsumer 思考片段消费回调
     * @param toolCallConsumer 工具调用片段消费回调
     */
    public CompletableFuture<QueryResult> queryStream(String prompt,
            Consumer<String> contentConsumer,
            Consumer<String> thinkingConsumer,
            Consumer<LLMService.StreamToolCallEvent> toolCallConsumer) {
        logger.info("[LLMQueryEngine] Stream Query: " + prompt);
        
        pipeline.publish(new ObservationEvent.StepStart("LLM查询", "正在分析问题并制定解决方案..."));
        
        session.addMessage(Message.createUserMessage(prompt));
        addFileEditGuidelines();
        
        return runStreamConversationLoop(0, 0, contentConsumer, thinkingConsumer, toolCallConsumer);
    }
    
    /**
     * 流式对话循环
     */
    private CompletableFuture<QueryResult> runStreamConversationLoop(int iteration, int emptyResponseCount,
            Consumer<String> contentConsumer,
            Consumer<String> thinkingConsumer,
            Consumer<LLMService.StreamToolCallEvent> toolCallConsumer) {
        // 检测会话是否被外部压缩（如 /compact 命令），如果是则重置 TokenBudget
        if (session.getCompactCount() > lastSeenCompactCount) {
            resetTokenBudget();
            lastSeenCompactCount = session.getCompactCount();
            logger.info("[LLMQueryEngine] Context compacted detected, TokenBudget reset.");
        }

        // 检查 Token 预算
        if (tokenBudget.isExhausted()) {
            logger.warning("[LLMQueryEngine] Token budget exhausted: " + tokenBudget);
            triggerStepComplete("LLM查询", "Token 预算已耗尽");
            return CompletableFuture.completedFuture(
                QueryResult.error("Token 预算已耗尽，任务被迫终止。请使用 /compact 压缩上下文后重试。")
            );
        }

        // 自动触发上下文压缩（当预算使用超过 70% 时）
        if (tokenBudget.usageRatio() > 0.70 && session.getMessages().size() > 10) {
            logger.info("[LLMQueryEngine] Token budget usage=" + String.format("%.0f%%", tokenBudget.usageRatio() * 100)
                + ", auto-compacting context...");
            int beforeCount = session.getMessages().size();
            List<Message> compacted = compactionStrategy.compact(session.getMessages());
            if (compacted != null && compacted.size() < beforeCount) {
                session.clearMessages();
                compacted.forEach(session::addMessage);
                logger.info("[LLMQueryEngine] Auto-compacted: " + beforeCount + " -> " + session.getMessages().size()
                    + " messages, budget=" + String.format("%.0f%%", tokenBudget.usageRatio() * 100));
            } else {
                logger.warning("[LLMQueryEngine] Auto-compact skipped: result size not reduced (before=" + beforeCount + ")");
            }
        }

        // Token 预算紧张时缓存建议（将在 convertSessionMessages 中临时注入，不保存到 session 历史）
        if (iteration > 0) {
            String advice = tokenBudget.toPromptAdvice();
            if (!advice.isEmpty()) {
                this.pendingBudgetAdvice = advice;
            }
        }

        // 后备：迭代限制检查
        if (config.getMaxIterations() > 0 && iteration >= config.getMaxIterations()) {
            logger.warning("[LLMQueryEngine] Max iterations reached: " + config.getMaxIterations());
            triggerStepComplete("LLM查询", "达到最大迭代次数限制");
            return CompletableFuture.completedFuture(
                QueryResult.error("达到最大迭代次数限制")
            );
        }
        
        // 转换会话消息到 LLM 格式
        List<LLMMessage> llmMessages = convertSessionMessages(session);
        
        logger.info("[LLMQueryEngine] Stream Iteration " + iteration + ", messages: " + llmMessages.size());
        
        // 获取可用工具
        List<LLMTool> tools = convertTools(toolExecutor.getEnabledTools());
        
        // 提醒使用 [FINISH] 标记，但避免重复堆积系统提示
        if (iteration < 20 && !hasRecentSystemPrompt("[FINISH]")) {
            session.addMessage(Message.createSystemMessage(
                "提示：如果任务已完成，请在回复末尾添加 [FINISH] 标记以结束对话。"
            ));
        }
        
        // 触发事件：发送请求
        if (iteration == 0) {
            pipeline.publish(new ObservationEvent.Thinking("思考", "正在构建请求，发送 " + llmMessages.size() + " 条消息给AI模型..."));
        } else {
            pipeline.publish(new ObservationEvent.Thinking("分析", "继续对话循环 (第 " + iteration + " 轮)"));
        }
        
        // 包装 Consumer，同时发布到管道
        Consumer<String> wrappedContentConsumer = chunk -> {
            if (contentConsumer != null) {
                contentConsumer.accept(chunk);
            }
            pipeline.publish(new ObservationEvent.ContentChunk(chunk));
        };
        
        Consumer<String> wrappedThinkingConsumer = chunk -> {
            if (thinkingConsumer != null) {
                thinkingConsumer.accept(chunk);
            }
            pipeline.publish(new ObservationEvent.ThinkingChunk(chunk));
        };
        
        Consumer<LLMService.StreamToolCallEvent> wrappedToolCallConsumer = event -> {
            if (toolCallConsumer != null) {
                toolCallConsumer.accept(event);
            }
            // StreamToolCallEvent 映射为 ToolCall 事件（工具名和参数从事件中提取）
            String toolName = event != null && event.getName() != null ? event.getName() : "unknown";
            String args = event != null && event.getArguments() != null ? event.getArguments() : "";
            pipeline.publish(new ObservationEvent.ToolCall(toolName, args, event != null ? event.getId() : null));
        };
        
        // 发送流式请求
        CompletableFuture<LLMResponse> future = tools.isEmpty()
            ? llmService.chatStream(llmMessages, wrappedContentConsumer)
            : llmService.chatStreamWithTools(llmMessages, tools, wrappedContentConsumer,
                                              wrappedThinkingConsumer, wrappedToolCallConsumer);
        
        return future.thenCompose(response -> handleStreamResponse(response, iteration, emptyResponseCount,
            contentConsumer, thinkingConsumer, toolCallConsumer));
    }
    
    /**
     * 处理流式响应
     */
    private CompletableFuture<QueryResult> handleStreamResponse(LLMResponse response, int iteration, int emptyResponseCount,
            Consumer<String> contentConsumer,
            Consumer<String> thinkingConsumer,
            Consumer<LLMService.StreamToolCallEvent> toolCallConsumer) {
        if (response.hasError()) {
            logger.severe("[LLMQueryEngine] API error: " + response.getErrorMessage());
            triggerStepComplete("LLM查询", "API错误: " + response.getErrorMessage());
            return CompletableFuture.completedFuture(
                QueryResult.error(response.getErrorMessage())
            );
        }
        
        logger.info("[LLMQueryEngine] Stream response received, content length: " +
            (response.getContent() != null ? response.getContent().length() : 0));

        // 消费 Token 预算并发布事件
        int promptTokens = response.getPromptTokens();
        int completionTokens = response.getCompletionTokens();
        if (promptTokens > 0 || completionTokens > 0) {
            tokenBudget.consume(promptTokens, completionTokens);
            pipeline.publish(new ObservationEvent.TokenUsage(promptTokens, completionTokens,
                response.getModel() != null ? response.getModel() : "unknown"));
        }

        // 打印 AI 思考内容
        if (response.getReasoningContent() != null && !response.getReasoningContent().isEmpty()) {
            String think = response.getReasoningContent();
            logger.info("[LLMQueryEngine] AI思考内容: " + think);
            System.out.println("🤔 AI思考: " + think);
        }
        
        // 创建助手消息
        Message assistantMessage;
        if (response.hasToolCalls()) {
            // 有工具调用
            assistantMessage = Message.createAssistantMessageWithToolCalls(
                response.getContent(),
                convertToolCalls(response.getToolCalls()),
                response.getReasoningContent()
            );
            
            session.addMessage(assistantMessage);
            
            pipeline.publish(new ObservationEvent.Thinking("分析", "AI决定调用 " + response.getToolCalls().size() + " 个工具"));
            
            // 执行工具调用，工具完成后继续流式循环
            return executeToolCalls(response.getToolCalls(), iteration + 1, emptyResponseCount,
                (iter, emptyCount) -> runStreamConversationLoop(iter, emptyCount, contentConsumer, thinkingConsumer, toolCallConsumer));
        } else {
            // 没有工具调用
            String content = response.getContent();
            assistantMessage = Message.createAssistantMessage(content, response.getReasoningContent());
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
            
            // 检查是否为空回复（reasoningContent 非空时视为有效思考，不算空回复）
            String reasoning = response.getReasoningContent();
            boolean hasReasoning = reasoning != null && !reasoning.trim().isEmpty();
            boolean isEmptyContent = content == null || content.trim().isEmpty();
            
            if (isEmptyContent && !hasReasoning) {
                emptyResponseCount++;
                int maxEmpty = config.getMaxEmptyResponses();
                logger.warning("[LLMQueryEngine] 空回复 (第 " + emptyResponseCount + "/" + maxEmpty + " 次)");
                
                if (emptyResponseCount >= maxEmpty) {
                    logger.warning("[LLMQueryEngine] 空回复次数已达上限，强制结束对话");
                    triggerStepComplete("LLM查询", "空回复过多，已自动结束");
                    return CompletableFuture.completedFuture(
                        QueryResult.error("对话无响应，已自动结束")
                    );
                }
                
                // 第一次/第二次空回复时，主动引导模型继续，而不是静默等待
                session.addMessage(Message.createSystemMessage(EMPTY_RESPONSE_PROMPT));
            } else {
                // 有有效内容或仅有思考内容，重置空回复计数
                emptyResponseCount = 0;
                
                // 仅在无重复提示时追加结束标记提醒
                if (!hasRecentSystemPrompt("[FINISH]")) {
                    session.addMessage(Message.createSystemMessage(
                        "提示：如果任务已完成，请在回复末尾添加 [FINISH] 标记以结束对话。例如：\"任务已完成。\\n\\n[FINISH]\""
                    ));
                }
            }
            
            // 没有 finishReason，继续流式对话循环
            return runStreamConversationLoop(iteration + 1, emptyResponseCount, contentConsumer, thinkingConsumer, toolCallConsumer);
        }
    }
    
    /**
     * 执行工具调用
     * 
     * @param toolCalls 工具调用列表
     * @param nextIteration 下一轮迭代次数
     * @param emptyResponseCount 连续空回复次数
     */
    private CompletableFuture<QueryResult> executeToolCalls(List<LLMMessage.ToolCall> toolCalls, int nextIteration, int emptyResponseCount,
            java.util.function.BiFunction<Integer, Integer, CompletableFuture<QueryResult>> loopContinuation) {
        logger.info("[LLMQueryEngine] Executing " + toolCalls.size() + " tool calls");
        
        List<CompletableFuture<ToolExecutionResult>> futures = new ArrayList<>();
        int toolIndex = 1;
        
        for (LLMMessage.ToolCall tc : toolCalls) {
            String toolName = tc.getFunction().getName();
            
            // 触发事件：开始执行工具
            pipeline.publish(new ObservationEvent.StepStart("工具调用", "执行 " + toolName + " (第 " + toolIndex + "/" + toolCalls.size() + " 个)"));
            
            // 记录工具调用历史
            toolCallHistory.add(toolName + ":" + tc.getFunction().getArguments());
            
            // 查找并执行工具
            Tool<?, ?, ?> tool = findTool(toolName);
            if (tool == null) {
                logger.warning("[LLMQueryEngine] Tool not found: " + toolName + " [toolCallId=" + tc.getId() + "]");
                // 触发事件：工具未找到
                pipeline.publish(new ObservationEvent.StepComplete("工具执行", "未找到工具: " + toolName));
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
                boolean hasAskUserQuestion = false;
                boolean hasError = false;
                for (CompletableFuture<ToolExecutionResult> future : futures) {
                    try {
                        ToolExecutionResult result = future.get();
                        
                        // 触发事件：工具执行完成
                        boolean success = result.getResult() != null && !result.getResult().startsWith("Error:");
                        pipeline.publish(new ObservationEvent.ToolResult(
                            result.getToolName(), result.getResult(), success, null, result.getToolCallId()));
                        String resultPreview = truncate(result.getResult(), 100);
                        pipeline.publish(new ObservationEvent.StepComplete("工具执行", result.getToolName() + " → " + resultPreview));
                        
                        // 添加工具结果消息（包含输入参数）
                        Message toolResultMsg = Message.createToolResultMessage(
                            result.getToolCallId(),
                            result.getToolName(),
                            result.getInputArguments(),  // 新增：传递输入参数
                            result.getResult()
                        );
                        logger.info("[LLMQueryEngine] Created tool result message: toolCallId=" + 
                            result.getToolCallId() + ", toolName=" + result.getToolName());
                        session.addMessage(toolResultMsg);
                        
                        // 【任务生命周期】检测关键工具
                        if ("AskUserQuestion".equals(result.getToolName())) {
                            hasAskUserQuestion = true;
                        }
                        if (!success) {
                            hasError = true;
                        }
                        
                    } catch (Exception e) {
                        logger.severe("[LLMQueryEngine] Failed to get tool result: " + e.getMessage());
                        pipeline.publish(new ObservationEvent.StepComplete("工具执行", "执行失败: " + e.getMessage()));
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
                        hasError = true;
                    }
                    resultIndex++;
                }
                
                // 【任务生命周期】根据工具结果更新任务状态
                if (hasAskUserQuestion) {
                    // AskUserQuestion 的结果作为等待的问题描述
                    String question = futures.stream()
                        .map(f -> {
                            try { return f.get(); } catch (Exception e) { return null; }
                        })
                        .filter(r -> r != null && "AskUserQuestion".equals(r.getToolName()))
                        .findFirst()
                        .map(ToolExecutionResult::getResult)
                        .orElse("需要用户补充信息");
                    taskLifecycleManager.waitForUserInput(session, question);
                } else if (hasError) {
                    taskLifecycleManager.failStep(session, "工具执行失败");
                } else {
                    taskLifecycleManager.advanceStep(session, "工具执行成功");
                }
                
                // 触发事件：继续分析
                pipeline.publish(new ObservationEvent.Thinking("分析", "工具执行完成，继续分析结果..."));
                
                // 继续对话循环（重置空回复计数，因为工具执行可能有有效输出）
                return loopContinuation.apply(nextIteration, 0);
            });
    }
    
    /**
     * 检查最近的消息中是否已包含相同关键词的系统提示
     * 用于避免系统提示无限堆积
     */
    private boolean hasRecentSystemPrompt(String keyword) {
        List<Message> messages = session.getMessages();
        int checkCount = Math.min(messages.size(), SYSTEM_PROMPT_DEDUP_WINDOW);
        for (int i = messages.size() - 1; i >= messages.size() - checkCount; i--) {
            Message msg = messages.get(i);
            if (msg.getRole() == Message.Role.SYSTEM) {
                String text = msg.getTextContent();
                if (text != null && text.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 触发步骤完成回调
     */
    private void triggerStepComplete(String stepName, String result) {
        pipeline.publish(new ObservationEvent.StepComplete(stepName, result));
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
        
        logger.info("[LLMQueryEngine] Executing tool: " + toolName + " args=" + truncate(args, 120));
        
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
        
        // 通过 ToolExecutor 执行工具，并应用三阶段恢复协议
        return RecoveryExecutor.executeWithRecovery(
            () -> toolExecutor.execute(toolName, argsNode, toolContext),
            new com.jwcode.core.resilience.RecoveryProtocol.AutoRetry(),
            toolName
        ).thenApply(execResult -> {
            String resultContent;
            if (execResult != null && execResult.isSuccess()) {
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
                String errorMsg = (execResult != null) ? execResult.getErrorMessage() : "Tool execution failed after recovery";
                resultContent = "Error: " + errorMsg;
            }
            return new ToolExecutionResult(toolCallId, toolName, args, resultContent);
        }).exceptionally(e -> {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            logger.severe("[LLMQueryEngine] Tool execution failed after recovery: " + cause.getMessage());
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
     * 创建上下文窗口管理器 - 根据模型实际支持的上下文窗口动态配置
     */
    private ContextWindowManager createContextWindowManager() {
        int modelContextLimit = llmService.getContextWindow();
        // TokenBudget 应比模型窗口更早触发压缩，防止预算耗尽
        int budgetLimit = (int) Math.min(modelContextLimit, tokenBudget.getTotalBudget());
        // 动态计算消息数限制：每 10K token 允许 1 条消息，最少保留 50 条
        int dynamicMaxMessages = Math.max(50, budgetLimit / 10_000);
        return new ContextWindowManager(
            budgetLimit,           // 取模型窗口与 TokenBudget 的较小值
            dynamicMaxMessages,    // 动态最大消息数限制
            6,                     // 最小保留消息数，确保工具调用链完整
            compactionStrategy     // LLM 语义压缩策略
        );
    }
    
    /**
     * 转换会话消息到 LLM 格式
     * 
     * 关键修复：自动使用 ContextWindowManager 压缩消息历史，
     * 根据模型实际支持的上下文窗口动态限制 token 数量，防止 API 报错。
     */
    private List<LLMMessage> convertSessionMessages(Session session) {
        List<Message> messages = session.getMessages();
        
        // 根据当前模型动态创建 ContextWindowManager 并执行压缩
        ContextWindowManager windowManager = createContextWindowManager();
        int originalCount = messages.size();
        messages = windowManager.prepareMessages(messages);
        if (messages.size() < originalCount) {
            logger.info("[LLMQueryEngine] 上下文压缩: " + originalCount + " -> " + messages.size() + 
                " 条消息 (限制=" + windowManager.getContextLimit() + ")");
            // 修复：压缩后的消息需要写回 session，否则下一轮会使用原始消息列表
            session.setMessages(messages);
            session.markCompacted();
        }
        
        List<LLMMessage> result = new ArrayList<>();
        
        for (Message msg : messages) {
            LLMMessage.Role role = convertRole(msg.getRole());
            
            if (role == null) continue;
            
            // 处理 TOOL 消息 - 需要提取 toolUseId
            if (role == LLMMessage.Role.TOOL) {
                String toolCallId = extractToolCallId(msg);
                String content = extractToolResultContent(msg);
                
                logger.fine("[LLMQueryEngine] Converting TOOL message: toolCallId=" + toolCallId + 
                    ", contentLength=" + (content != null ? content.length() : 0));
                
                // 修复: 如果 toolCallId 为空，生成临时 ID 而不是跳过
                // 这样可以保证 tool_calls 和 tool 消息数量一致，避免 API 报错 "tool call and result not match"
                if (toolCallId == null || toolCallId.isEmpty()) {
                    toolCallId = "temp_tool_" + System.nanoTime();
                    logger.warning("[LLMQueryEngine] TOOL message missing toolCallId, generated temp ID: " + toolCallId);
                }
                
                LLMMessage llmMsg = LLMMessage.tool(toolCallId, content);
                logger.fine("[LLMQueryEngine] Created LLMMessage.TOOL: toolCallId=" + toolCallId);
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
                        .reasoningContent(msg.getReasoningContent())
                        .build();
                    result.add(llmMsg);
                }
            }
        }
        
        // 临时注入 Token 预算建议（仅当前请求有效，不进入 session 历史）
        if (pendingBudgetAdvice != null && !pendingBudgetAdvice.isEmpty()) {
            result.add(LLMMessage.system(pendingBudgetAdvice));
            pendingBudgetAdvice = null;
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
     * 从 TOOL 消息中提取结果内容
     * 仅返回结果字符串，丢弃工具名和输入参数；过长结果自动截断以节省 token。
     */
    private String extractToolResultContent(Message msg) {
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
            return "";
        }
        for (Message.ContentBlock block : msg.getContent()) {
            if (block instanceof Message.ToolResultContent) {
                Message.ToolResultContent trc = (Message.ToolResultContent) block;
                String result = trc.getResult() != null ? trc.getResult().toString() : "";
                if (result.length() > 800) {
                    result = result.substring(0, 800) + "\n...[内容已截断]";
                }
                return result;
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
        private int maxIterations = 0; // 默认 0 表示不限制，由 TokenBudget 控制
        private Duration timeout = Duration.ofMinutes(5);
        private int maxEmptyResponses = DEFAULT_MAX_EMPTY_RESPONSES;
        private boolean reasoningModel = false;
        private long tokenBudget = 1_000_000; // 默认 1M token 预算
        private int maxMessageHistory = 0; // 默认 0 表示不限制，由 ContextWindowManager 智能压缩
        
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
                    engineConfig.setTokenBudget(engine.getTokenBudget());
                    engineConfig.setMaxMessageHistory(engine.getMaxMessageHistory());
                }
            }
            return engineConfig;
        }
        
        /**
         * 根据模型特性自动调整配置
         */
        public void applyModelTraits(String modelId) {
            if (modelId == null || modelId.isEmpty()) return;
            String lower = modelId.toLowerCase();
            if (lower.contains("deepseek-r1") || lower.contains("deepseek-v4") || lower.contains("kimi-k1") || lower.contains("o1") || lower.contains("o3")) {
                this.reasoningModel = true;
                this.maxEmptyResponses = 5;
                // reasoning 模型需要更长的总超时
                if (this.timeout.compareTo(Duration.ofMinutes(10)) < 0) {
                    this.timeout = Duration.ofMinutes(15);
                }
            }
        }
        
        public int getMaxIterations() { return maxIterations; }
        public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
        
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        
        public int getMaxEmptyResponses() { return maxEmptyResponses; }
        public void setMaxEmptyResponses(int maxEmptyResponses) { this.maxEmptyResponses = maxEmptyResponses; }
        
        public boolean isReasoningModel() { return reasoningModel; }
        public void setReasoningModel(boolean reasoningModel) { this.reasoningModel = reasoningModel; }

        public long getTokenBudget() { return tokenBudget; }
        public void setTokenBudget(long tokenBudget) { this.tokenBudget = tokenBudget; }

        public int getMaxMessageHistory() { return maxMessageHistory; }
        public void setMaxMessageHistory(int maxMessageHistory) { this.maxMessageHistory = maxMessageHistory; }

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

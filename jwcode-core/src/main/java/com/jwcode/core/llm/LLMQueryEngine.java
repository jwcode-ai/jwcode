package com.jwcode.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jwcode.core.agent.Agent;
import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.model.Message;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.*;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import com.jwcode.core.aicl.AICLPromptBuilder;
import com.jwcode.core.agent.BudgetExhaustedHandler;
import com.jwcode.core.config.ConfigManager;
import com.jwcode.core.permission.PermissionManagerChecker;
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
    // 【反编造】审计追踪：记录当前任务中已实际执行的工具
    private final java.util.Set<String> executedWriteTools = new java.util.HashSet<>();
    private final java.util.Set<String> executedReadTools = new java.util.HashSet<>();
    private final java.util.Set<String> executedCommandTools = new java.util.HashSet<>();
    // volatile 确保跨线程的 fabrication check 注入一致性
    private volatile boolean fabricationCheckInjected = false;
    private final SimpleCompactionStrategy compactionStrategy;
    private final TaskLifecycleManager taskLifecycleManager;
    private long lastSeenCompactCount = 0;
    private String pendingBudgetAdvice;
    // 【Token 银行】记录上次压缩时的消息数，用于计算释放的 token
    private int lastCompactMessageCount = 0;
    // 去重：记录已注册的 StepCallbackAdapter，防止重复订阅导致日志重复
    private StepCallbackAdapter stepCallbackAdapter;
    // 原始回调引用，用于直接推送 Token 更新等实时事件
    private StepCallback stepCallback;
    // 【修复】压缩冷却时间：避免短时间内多次压缩
    private long lastCompactTime = 0;
    private static final long COMPACT_COOLDOWN_MS = 30000; // 30秒冷却时间
    // 【Phase 5】Agent 感知：当前 Agent 注册表，用于工具过滤和提示词注入
    private AgentRegistry agentRegistry;
    // 【Phase 5】Agent 桥接器：连接 LLMQueryEngine 与 EnhancedOrchestratorAgent
    private com.jwcode.core.agent.AgentBridge agentBridge;
    // CompactorAgent 引用（用于语义级上下文压缩）
    private com.jwcode.core.agent.CompactorAgent compactorAgent;
    
    // BudgetExhaustedHandler 引用（用于Token预算监控）
    private com.jwcode.core.agent.BudgetExhaustedHandler budgetHandler;
    // 【优化】无进展检测：连续仅工具调用无文本回复的轮数
    private int consecutiveToolOnlyRounds = 0;
    // 【优化】重复工具检测：记录上一轮的工具名列表，用于检测重复模式
    private java.util.List<String> lastToolNames = new java.util.ArrayList<>();
    // 【优化】重复工具检测计数器
    private int repeatedToolPatternCount = 0;
    // 【修复】纯思考无行动检测：连续仅有 reasoning 但无文本/无工具调用的轮数
    private int consecutiveThinkingOnlyRounds = 0;
    // 【修复】连续失败工具轮数检测：连续 N 轮工具调用全部失败则强制终止
    private int consecutiveFailedToolRounds = 0;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    
    public LLMQueryEngine(Session session, LLMService llmService, 
                          ToolExecutor toolExecutor, EngineConfig config,
                          AgentRegistry agentRegistry) {
        this.session = session;
        this.llmService = llmService;
        this.toolExecutor = toolExecutor;
        this.config = config != null ? config : EngineConfig.defaultConfig();
        this.agentRegistry = agentRegistry;
        if (this.config.getMaxMessageHistory() > 0) {
            session.setMaxMessageHistory(this.config.getMaxMessageHistory());
        }
        // 【修复】使用 session 的工作目录而非 user.dir，确保与验收检查一致
        String sessionWd = session.getWorkingDirectory();
        java.nio.file.Path wd = (sessionWd != null && !sessionWd.isEmpty())
            ? java.nio.file.Path.of(sessionWd)
            : java.nio.file.Path.of(System.getProperty("user.dir"));
        this.toolContext = ToolExecutionContext.builder()
            .session(session)
            .workingDirectory(wd)
            .agentRegistry(agentRegistry)
            .llmService(llmService)
            .build();
        this.startTime = Instant.now();
        this.toolCallHistory = new ArrayList<>();
        this.pipeline = new DefaultObservationPipeline();
        this.tokenBudget = TokenBudget.of(this.config.getTokenBudget());
        this.compactionStrategy = new SimpleCompactionStrategy(llmService);
        this.taskLifecycleManager = new TaskLifecycleManager(llmService, this.pipeline);
        // 创建 AgentBridge（如果 agentRegistry 可用）
        initAgentBridge();
        // 创建 CompactorAgent（用于语义级上下文压缩）
        initCompactorAgent();
        // 创建 BudgetExhaustedHandler（用于Token预算监控）
        initBudgetHandler();
    }

    /**
     * 初始化 CompactorAgent — 用于智能语义压缩
     */
    private void initCompactorAgent() {
        try {
            this.compactorAgent = new com.jwcode.core.agent.CompactorAgent(
                llmService, compactionStrategy
            );
            logger.info("[LLMQueryEngine] CompactorAgent initialized");
        } catch (Exception e) {
            logger.warning("[LLMQueryEngine] Failed to init CompactorAgent: " + e.getMessage());
            this.compactorAgent = null;
        }
    }
    
    /**
     * 初始化 BudgetExhaustedHandler — 用于Token预算监控与自动处理
     */
    private void initBudgetHandler() {
        try {
            this.budgetHandler = BudgetExhaustedHandler.createDefault();
            logger.info("[LLMQueryEngine] BudgetExhaustedHandler initialized");
        } catch (Exception e) {
            logger.warning("[LLMQueryEngine] Failed to init BudgetExhaustedHandler: " + e.getMessage());
            this.budgetHandler = null;
        }
    }

    /**
     * 初始化 AgentBridge — 连接 LLMQueryEngine 与 EnhancedOrchestratorAgent
     */
    private void initAgentBridge() {
        if (agentRegistry == null) {
            this.agentBridge = null;
            return;
        }
        try {
            // 从 toolExecutor 获取 ToolRegistry（通过反射方式兼容）
            com.jwcode.core.tool.ToolRegistry toolReg = null;
            if (toolExecutor != null) {
                try {
                    // ToolExecutor 内部持有 ToolRegistry，尝试通过反射获取
                    var field = toolExecutor.getClass().getDeclaredField("toolRegistry");
                    field.setAccessible(true);
                    toolReg = (com.jwcode.core.tool.ToolRegistry) field.get(toolExecutor);
                } catch (Exception e) {
                    logger.fine("Cannot extract ToolRegistry from ToolExecutor: " + e.getMessage());
                }
            }
            this.agentBridge = new com.jwcode.core.agent.AgentBridge(
                this, agentRegistry, llmService, toolReg, toolExecutor
            );
            logger.info("[LLMQueryEngine] AgentBridge initialized, orchestratorAvailable="
                + agentBridge.isOrchestratorAvailable());
        } catch (Exception e) {
            logger.warning("[LLMQueryEngine] Failed to init AgentBridge: " + e.getMessage());
            this.agentBridge = null;
        }
    }

    /**
     * 获取 AgentBridge
     */
    public com.jwcode.core.agent.AgentBridge getAgentBridge() {
        return agentBridge;
    }
    
    /**
     * 兼容构造器：不需要 AgentRegistry
     */
    public LLMQueryEngine(Session session, LLMService llmService, 
                          ToolExecutor toolExecutor, EngineConfig config) {
        this(session, llmService, toolExecutor, config, null);
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
            this.stepCallback = callback;
            this.stepCallbackAdapter = new StepCallbackAdapter(callback);
            this.pipeline.subscribe(this.stepCallbackAdapter);
        }
    }
    
    /**
     * 检查 Token 预算并在必要时触发处理
     */
    private void checkTokenBudget() {
        if (budgetHandler == null || tokenBudget == null || session == null) return;
        try {
            BudgetExhaustedHandler.BudgetAction action = 
                budgetHandler.checkAndHandle(session, tokenBudget);
            if (action != null && action != BudgetExhaustedHandler.BudgetAction.NONE) {
                logger.info("[LLMQueryEngine] Budget action: " + action + " at " + String.format("%.1f%%", tokenBudget.usageRatio() * 100));
            }
        } catch (Exception e) {
            logger.fine("[LLMQueryEngine] Budget check skipped: " + e.getMessage());
        }
    }
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
        
        /**
         * Token 用量更新（实时推送 Token 预算变化）
         * @param usedTokens 已用 token 数
         * @param totalBudget 总预算
         * @param usageRatio 使用率 (0.0~1.0)
         */
        default void onTokenUpdate(long usedTokens, long totalBudget, double usageRatio) {}

        default void onContextCompressed(int originalCount, int compressedCount,
                                         long tokensSaved, String summary) {}
    }
    
    /**
     * 执行查询
     */
    public CompletableFuture<QueryResult> query(String prompt) {
        Agent currentAgent = agentRegistry != null ? agentRegistry.getCurrent() : null;
        String agentName = currentAgent != null ? currentAgent.getName() : "default";
        logger.info("[LLMQueryEngine] Query [Agent=" + agentName + "]: " + prompt);
        
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
        
        // 记录用户活动 + 检查基于时间的 MicroCompact（Prompt Cache TTL 60min）
        var mcService = com.jwcode.core.service.MicroCompactService.getGlobalInstance();
        mcService.recordActivity();
        if (mcService.shouldTimeBasedCompact()) {
            int cleaned = mcService.performTimeBasedCompact();
            if (cleaned > 0) {
                logger.info("[LLMQueryEngine] 基于时间清理: " + cleaned + " 个旧工具结果已标记");
            }
        }

        // 添加用户消息到会话
        session.addMessage(Message.createUserMessage(prompt));

        // 添加系统提示，强调文件编辑前必须先读取
        addFileEditGuidelines();

        // 注入环境信息（操作系统、工作目录、系统时间等），让 AI 了解当前环境
        injectEnvironmentInfo();
        
        // 【优化】重置无进展检测计数器
        resetStagnationDetectors();

        // Auto Swarm: 如果启用，分析任务复杂度并注入分解计划
        checkAndInjectSwarmPlan(prompt);

        // 开始对话循环，初始空回复计数为 0
        return runConversationLoop(0, 0);
    }
    
    /**
     * 【Phase 5】注入当前 Agent 的系统提示词
     * 在对话开始时注入，让 LLM 知晓自己的角色、职责和可用工具约束。
     */
    private void injectAgentSystemPrompt() {
        if (agentRegistry == null) return;
        Agent agent = agentRegistry.getCurrent();
        if (agent == null) return;

        // 检查最近是否已经注入过该 Agent 的提示词（避免重复）
        String marker = "[AGENT_ROLE:" + agent.getId() + "]";
        if (hasRecentSystemPrompt(marker)) {
            logger.fine("[LLMQueryEngine] Agent system prompt already injected for " + agent.getId());
            return;
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append(marker).append("\n");
        prompt.append("# 当前角色：").append(agent.getName()).append("\n\n");
        prompt.append(agent.getSystemPrompt()).append("\n\n");
        
        // 注入 AICL 上下文解析规则（让 AI 理解优先级和生命周期）
        prompt.append(AICLPromptBuilder.buildCompactPrompt()).append("\n\n");

        // 显式列出可用工具（增强约束感）
        List<Tool<?, ?, ?>> allowedTools = toolExecutor.getEnabledTools().stream()
            .filter(t -> agent.canUseTool(t.getName()))
            .toList();
        if (!allowedTools.isEmpty()) {
            prompt.append("## 你当前可用的工具（仅限以下工具）\n\n");
            for (Tool<?, ?, ?> t : allowedTools) {
                prompt.append("- ").append(t.getName()).append(": ").append(t.getDescription()).append("\n");
            }
            prompt.append("\n");
        }

        // 显式列出禁止工具（对 Orchestrator 尤为重要）
        List<Tool<?, ?, ?>> disallowedTools = toolExecutor.getEnabledTools().stream()
            .filter(t -> !agent.canUseTool(t.getName()))
            .toList();
        if (!disallowedTools.isEmpty()) {
            prompt.append("## 你【禁止】使用的工具（必须通过 AgentTool 指派给子Agent）\n\n");
            for (Tool<?, ?, ?> t : disallowedTools) {
                prompt.append("- ").append(t.getName()).append("\n");
            }
            prompt.append("\n如果你需要执行上述禁止工具的工作，请使用 AgentTool 创建对应角色的子Agent来完成。\n\n");
        }

        session.addMessage(Message.createSystemMessage(prompt.toString()));
        logger.info("[LLMQueryEngine] 已注入 Agent 系统提示词 | agent=" + agent.getId()
            + " | 允许工具=" + allowedTools.size()
            + " | 禁止工具=" + disallowedTools.size());
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
            
            【‼️ 强制】任务结束规则 — 这是最重要的规则，违反将导致任务无限循环：

            当你认为任务已经完成（包括回答简单问题、执行完操作、确认结果），
            必须立即在回复的最后一行单独输出 [FINISH]。

            正确示例：
              当前工作目录是 D:\test
              [FINISH]

            错误示例（缺少 [FINISH]，将导致循环）：
              当前工作目录是 D:\test
              (没有 [FINISH] — 系统会继续追问！)

            ⚠ 记住：无论任务大小，回答完后必须在最后一行输出 [FINISH]。不输出 [FINISH] = 任务未完成。
            
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
            3. 如果发现遗漏的工作，动态添加新步骤并继续执行（AI自动执行，无需用户确认）
            4. 如果发现某个步骤不需要，可以自动删除该步骤
            5. 如果发现步骤顺序不合理，可以自动调整顺序
            6. 如果需要用户补充信息，使用 AskUserQuestionTool 主动提问，不要空等
            7. 所有步骤完成后，添加 [FINISH] 标记结束对话
            8. 在回复末尾使用以下格式显示任务进度：
               【任务进度】X/Y 已完成 | Z 待处理
               例如：【任务进度】3/5 已完成 | 2 待处理
            
            重要：任务清单的添加/删除/调整由 AI 自动判断并执行，无需询问用户！
            如果发现当前步骤遗漏了相关工作，直接添加新步骤。
            如果发现某个步骤已经完成或不需要，可以直接删除。
            如果发现后续步骤需要提前执行，可以直接调整顺序。
            
【关键】执行诚信规则（绝对禁止——违反即任务失败）：

            1. **禁止谎报完成**：绝对不能声称完成了某个步骤而实际上没有执行相应的工具调用。
               标注"✅ 步骤 X 完成"之前，必须已经实际执行了该步骤所需的全部工具操作。

            2. **禁止虚构结果**：绝对不能编造文件内容、命令输出、或任何你没有实际获取到的信息。
               如果工具调用失败，必须如实报告失败，而不是假装成功。
               ❌ 工具返回 "Error: file not found" → 不得说"文件修改成功"
               ❌ BashTool 返回空 → 不得说"编译通过，BUILD SUCCESS"
               ❌ 未调用任何测试工具 → 不得说"测试全部通过"

            3. **禁止跳跃执行**：必须按照任务清单的顺序逐个执行步骤。
               不得在未执行步骤 1-3 的情况下直接声称步骤 4 完成。

            4. **完成必须有证据**：每个步骤完成后，在回复中附上实际的执行结果摘要
              （如读取到的文件内容片段、命令执行的输出摘要、修改的文件路径列表）。
              没有证据的"完成"声明将被视为无效（编造）。

            5. **失败必须上报**：如果某个步骤尝试多次仍无法完成，必须明确标记为失败
              并说明原因，不得悄悄跳过或假装完成。

            6. **【强制】在输出 [FINISH] 之前，执行自我审计**：
               a. 回查当前对话中的所有工具调用记录（tool-call → tool-result 消息对）
               b. 确认每一个声称的"已修改"、"已通过"、"已完成"都有对应的真实工具调用
               c. 确认引用的所有文件内容/命令输出都来自工具返回值，而非你的推测或记忆
               d. 如果发现任何声明缺少工具调用证据 → 不得输出 [FINISH]，必须先补执行
               e. 编造内容比执行失败严重 10 倍——宁可报告失败，不要编造成功
            
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
     * 注入环境信息到系统提示
     *
     * <p>让 AI 知晓当前的运行环境：操作系统、Java版本、系统时间、工作目录等。
     * 这样 AI 在回答"当前工作目录是什么"等问题时能给出准确的答案，
     * 而不是返回 JVM 启动目录。</p>
     *
     * <p>使用去重标记 [ENV_INFO] 避免每次迭代重复注入。</p>
     */
    private void injectEnvironmentInfo() {
        String marker = "[ENV_INFO]";
        if (hasRecentSystemPrompt(marker)) {
            return;
        }

        String osName = System.getProperty("os.name", "Unknown");
        String osVersion = System.getProperty("os.version", "Unknown");
        String osArch = System.getProperty("os.arch", "Unknown");
        String javaVersion = System.getProperty("java.version", "Unknown");
        String userName = System.getProperty("user.name", "Unknown");
        String workingDir = session.getWorkingDirectory();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
        String currentTime = formatter.format(Instant.now());

        String envInfo = """
            %s
            【当前环境信息】
            - 操作系统：%s %s (%s)
            - Java 版本：%s
            - 当前用户：%s
            - 当前时间：%s
            - 工作目录：%s

            注意：以上环境信息随用户操作动态更新。当用户询问"当前工作目录"、
            "现在时间"、"操作系统"等问题时，请以上述信息为准。
            """.formatted(marker, osName, osVersion, osArch, javaVersion, userName, currentTime, workingDir);

        session.addMessage(Message.createSystemMessage(envInfo));
        logger.info("[LLMQueryEngine] 已注入环境信息 | OS=" + osName + " | 工作目录=" + workingDir);
    }

    /**
     * 【优化】重置无进展检测计数器（新任务开始时调用）
     */
    private void resetStagnationDetectors() {
        this.consecutiveToolOnlyRounds = 0;
        this.consecutiveThinkingOnlyRounds = 0;
        this.consecutiveFailedToolRounds = 0;
        this.lastToolNames = new java.util.ArrayList<>();
        this.repeatedToolPatternCount = 0;
        this.fabricationCheckInjected = false;
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
    // 【优化】无进展检测阈值已移至 EngineConfig.maxConsecutiveToolOnlyRounds（可通过 config.yaml 配置）
    // 【修复】纯思考无行动检测：连续 N 轮仅有 reasoning 但无文本/无工具调用则强制终止
    private static final int MAX_CONSECUTIVE_THINKING_ONLY_ROUNDS = 10;
    // 【修复】连续失败工具轮数检测：连续 N 轮工具调用全部失败则强制终止
    private static final int MAX_CONSECUTIVE_FAILED_TOOL_ROUNDS = 5;
    // 【优化】重复工具检测：同一工具连续调用超过此阈值触发干预
    private static final int MAX_REPEATED_TOOL_CALLS = 8;
    // 【优化】[FINISH] 提示注入间隔：每 N 轮才检查一次，避免频繁注入
    private static final int FINISH_REMINDER_INTERVAL = 2; // 强提醒: 每 2 轮注入 [FINISH] 提示
    
    /**
     * 对话循环
     * 
     * @param iteration 当前迭代次数
     * @param emptyResponseCount 连续空回复次数
     */
    private CompletableFuture<QueryResult> runConversationLoop(int iteration, int emptyResponseCount) {
        // Quick guard: max iterations
        if (config.getMaxIterations() > 0 && iteration >= config.getMaxIterations()) {
            logger.warning("[LLMQueryEngine] Max iterations reached: " + config.getMaxIterations());
            return CompletableFuture.completedFuture(QueryResult.error("Max iterations exceeded"));
        }

        // Task lifecycle checks (non-stream only)
        var activeTask = session.getActiveTask();
        if (activeTask != null && activeTask.getStatus() == com.jwcode.core.task.TaskStatus.WAITING_INPUT) {
            logger.info("[LLMQueryEngine] 任务处于 WAITING_INPUT 状态，暂停循环等待用户输入");
            return CompletableFuture.completedFuture(
                QueryResult.success(Message.createAssistantMessage(
                    "⏳ 等待用户补充信息：" + activeTask.getWaitingFor()))
            );
        }

        if (activeTask != null && activeTask.getStatus() == com.jwcode.core.task.TaskStatus.PLANNED && iteration == 0) {
            session.addMessage(Message.createSystemMessage(
                "任务清单已制定完成，请从第一个待办步骤开始执行。每完成一步请汇报进度。"
            ));
            activeTask.setStatus(com.jwcode.core.task.TaskStatus.EXECUTING);
            taskLifecycleManager.startFirstStep(session);
        }

        // Shared pre-flight checks (compact, budget, stagnation, message/tool conversion)
        PreFlightResult preFlight = runPreFlightChecks(iteration, null);
        if (preFlight.shouldAbort()) return preFlight.abort;

        // Event publishing
        if (iteration == 0) {
            pipeline.publish(new ObservationEvent.Thinking("思考", "正在构建请求，发送 " + preFlight.llmMessages.size() + " 条消息给AI模型..."));
        } else {
            pipeline.publish(new ObservationEvent.Thinking("分析", "继续对话循环 (第 " + iteration + " 轮)"));
        }

        // Send request
        CompletableFuture<LLMResponse> future = preFlight.tools.isEmpty()
            ? llmService.chat(preFlight.llmMessages)
            : llmService.chatWithTools(preFlight.llmMessages, preFlight.tools);

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
            // 实时推送 Token 更新到回调
            if (stepCallback != null) {
                stepCallback.onTokenUpdate(tokenBudget.getUsedTotal(), tokenBudget.getTotalBudget(), tokenBudget.usageRatio());
            }
        }

        // 打印 AI 思考内容
        if (response.getReasoningContent() != null && !response.getReasoningContent().isEmpty()) {
            String think = response.getReasoningContent();
            logger.info("[LLMQueryEngine] AI思考内容: " + think);
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
            
            // 【优化】无进展检测：有工具调用但无实质文本内容时计数
            String content = response.getContent();
            boolean hasSubstantiveContent = content != null && !content.trim().isEmpty()
                && content.trim().length() > 10; // 少于10个字符视为无实质内容
            if (!hasSubstantiveContent) {
                consecutiveToolOnlyRounds++;
                logger.info("[LLMQueryEngine] Tool-only round #" + consecutiveToolOnlyRounds
                    + " (no substantive text content)");
            } else {
                consecutiveToolOnlyRounds = 0; // 有实质内容则重置
            }
            
            // 【优化】重复工具检测：检查本次工具名列表是否与上次高度重复
            List<String> currentToolNames = response.getToolCalls().stream()
                .map(tc -> tc.getFunction().getName())
                .sorted()
                .toList();
            if (currentToolNames.equals(lastToolNames) && !currentToolNames.isEmpty()) {
                repeatedToolPatternCount++;
                logger.info("[LLMQueryEngine] Repeated tool pattern #" + repeatedToolPatternCount
                    + ": " + currentToolNames);
                if (repeatedToolPatternCount >= MAX_REPEATED_TOOL_CALLS) {
                    logger.warning("[LLMQueryEngine] Repeated tool pattern exceeded threshold, injecting urgency prompt");
                    session.addMessage(Message.createSystemMessage(
                        "【系统提示】你已连续多次调用相同的工具集但未完成任务。"
                        + "请评估当前策略是否有效：如果遇到阻碍，请尝试不同方法；"
                        + "如果任务已实际完成，请直接输出 [FINISH] 结束对话。"
                        + "不要重复调用相同工具而不产生进展。"
                    ));
                    repeatedToolPatternCount = 0; // 注入提示后重置计数器
                }
            } else {
                repeatedToolPatternCount = 0; // 工具模式变化则重置
            }
            lastToolNames = new java.util.ArrayList<>(currentToolNames);
            
            // 触发事件：准备调用工具
            pipeline.publish(new ObservationEvent.Thinking("分析", "AI决定调用 " + response.getToolCalls().size() + " 个工具"));
            
            // 执行工具调用
            return executeToolCalls(response.getToolCalls(), iteration + 1, emptyResponseCount, this::runConversationLoop);
        } else {
            // 没有工具调用
            String content = response.getContent();
            assistantMessage = Message.createAssistantMessage(content, response.getReasoningContent());
            session.addMessage(assistantMessage);
            
            // API finish_reason="stop" + 无工具调用 → 模型认为任务完成，直接结束
            if ("stop".equals(response.getFinishReason()) && !response.hasToolCalls()) {
                return CompletableFuture.completedFuture(QueryResult.success(assistantMessage));
            }
            // 检查回复内容是否包含结束标记
            if (content != null && content.contains(FINISH_MARKER)) {
                // 【反编造】执行审计：记录本次任务的实际工具调用情况
                logger.info("[LLMQueryEngine] " + getExecutionAuditSummary());

                // 【反编造】预检：如果启用了检查且多次迭代但零工具调用，拦截并注入验证
                if (config.isFabricationCheckEnabled() && iteration > 1
                    && executedWriteTools.isEmpty() && executedCommandTools.isEmpty()
                    && executedReadTools.isEmpty() && !fabricationCheckInjected) {
                    logger.warning("[LLMQueryEngine] ⚠️ 反编造拦截: AI 声称完成但零工具调用 (迭代=" + iteration + ")，注入验证提示");
                    fabricationCheckInjected = true;
                    session.addMessage(Message.createSystemMessage(
                        "【系统反编造检查】你在本次对话中未调用任何工具（读/写/命令均为0），"
                        + "但声称任务完成并标记了 [FINISH]。\n\n"
                        + "请确认：你是否真的执行了用户要求的操作？\n"
                        + "- 如果你实际执行了但系统未记录，请忽略此消息直接重新输出 [FINISH]\n"
                        + "- 如果你编造了完成声明，请立即调用正确的工具真实执行任务"
                    ));
                    return runConversationLoop(iteration + 1, 0);
                }

                logger.info("[LLMQueryEngine] 检测到结束标记 " + FINISH_MARKER + "，结束对话");
                // 【任务生命周期】检查任务是否全部完成
                taskLifecycleManager.checkTaskCompletion(session);
                triggerStepComplete("LLM查询", "完成回复");
                return CompletableFuture.completedFuture(
                    QueryResult.success(assistantMessage)
                );
            }
            
            // 检查是否为空回复
            // 【修复】纯思考（仅有 reasoning，无文本内容、无工具调用）不算有效进展，
            // 连续纯思考达到阈值必须强制终止，否则会导致无限循环
            String reasoning = response.getReasoningContent();
            boolean hasReasoning = reasoning != null && !reasoning.trim().isEmpty();
            boolean isEmptyContent = content == null || content.trim().isEmpty();
            boolean isThinkingOnly = isEmptyContent && hasReasoning; // 仅有思考，无行动

            if (isEmptyContent && !hasReasoning) {
                // 完全空回复（无思考、无内容）
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

                session.addMessage(Message.createSystemMessage(EMPTY_RESPONSE_PROMPT));
            } else if (isThinkingOnly) {
                // 【修复】纯思考无行动：不为有效进展，递增计数器且不重置空回复计数
                consecutiveThinkingOnlyRounds++;
                logger.warning("[LLMQueryEngine] 纯思考无行动 (第 " + consecutiveThinkingOnlyRounds
                    + "/" + MAX_CONSECUTIVE_THINKING_ONLY_ROUNDS + " 轮)，iteration=" + iteration);

                if (consecutiveThinkingOnlyRounds >= MAX_CONSECUTIVE_THINKING_ONLY_ROUNDS) {
                    logger.warning("[LLMQueryEngine] 纯思考轮数已达上限，强制结束对话");
                    triggerStepComplete("LLM查询", "模型持续思考但无行动，已自动终止");
                    return CompletableFuture.completedFuture(
                        QueryResult.error("模型持续思考但无行动，已自动终止")
                    );
                }

                // 注入更明确的引导提示
                if (consecutiveThinkingOnlyRounds % 3 == 0) {
                    session.addMessage(Message.createSystemMessage(
                        "【系统提示】你已连续 " + consecutiveThinkingOnlyRounds + " 轮仅有内部思考而无实际输出。"
                        + "请立即做出决定：执行工具操作，或输出文本回复，或在文本末尾添加 [FINISH] 结束对话。"
                        + "不要只思考不行动。"
                    ));
                }
            } else {
                // 有有效文本内容，重置所有停滞计数器
                emptyResponseCount = 0;
                consecutiveThinkingOnlyRounds = 0;

                // 【优化】降低 [FINISH] 提醒注入频率：每 FINISH_REMINDER_INTERVAL 轮才注入一次
                if (iteration % FINISH_REMINDER_INTERVAL == 0 && !hasRecentSystemPrompt("[FINISH]")) {
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
        Agent currentAgent = agentRegistry != null ? agentRegistry.getCurrent() : null;
        String agentName = currentAgent != null ? currentAgent.getName() : "default";
        logger.info("[LLMQueryEngine] Stream Query [Agent=" + agentName + "]: " + prompt);
        
        pipeline.publish(new ObservationEvent.StepStart("LLM查询", "正在分析问题并制定解决方案..."));
        
        session.addMessage(Message.createUserMessage(prompt));
        addFileEditGuidelines();
        
        // 注入环境信息（操作系统、工作目录、系统时间等），让 AI 了解当前环境
        injectEnvironmentInfo();
        
        // 【优化】重置无进展检测计数器
        resetStagnationDetectors();

        // Auto Swarm: 如果启用，分析任务复杂度并注入分解计划
        checkAndInjectSwarmPlan(prompt);

        return runStreamConversationLoop(0, 0, contentConsumer, thinkingConsumer, toolCallConsumer);
    }
    
    /**
     * 流式对话循环
     */
    private CompletableFuture<QueryResult> runStreamConversationLoop(int iteration, int emptyResponseCount,
            Consumer<String> contentConsumer,
            Consumer<String> thinkingConsumer,
            Consumer<LLMService.StreamToolCallEvent> toolCallConsumer) {
        // Shared pre-flight checks (compact, budget, stagnation, message/tool conversion)
        PreFlightResult preFlight = runPreFlightChecks(iteration, contentConsumer);
        if (preFlight.shouldAbort()) return preFlight.abort;

        // Event publishing
        if (iteration == 0) {
            pipeline.publish(new ObservationEvent.Thinking("思考", "正在构建请求，发送 " + preFlight.llmMessages.size() + " 条消息给AI模型..."));
        } else {
            pipeline.publish(new ObservationEvent.Thinking("分析", "继续对话循环 (第 " + iteration + " 轮)"));
        }

        // Wrap consumers to also publish to pipeline
        Consumer<String> wrappedContentConsumer = chunk -> {
            if (contentConsumer != null) contentConsumer.accept(chunk);
            pipeline.publish(new ObservationEvent.ContentChunk(chunk));
        };

        Consumer<String> wrappedThinkingConsumer = chunk -> {
            if (thinkingConsumer != null) thinkingConsumer.accept(chunk);
            pipeline.publish(new ObservationEvent.ThinkingChunk(chunk));
        };

        Consumer<LLMService.StreamToolCallEvent> wrappedToolCallConsumer = event -> {
            if (toolCallConsumer != null) toolCallConsumer.accept(event);
            String toolName = event != null && event.getName() != null ? event.getName() : "unknown";
            String args = event != null && event.getArguments() != null ? event.getArguments() : "";
            pipeline.publish(new ObservationEvent.ToolCall(toolName, args, event != null ? event.getId() : null));
        };

        // Send stream request
        CompletableFuture<LLMResponse> future = preFlight.tools.isEmpty()
            ? llmService.chatStream(preFlight.llmMessages, wrappedContentConsumer)
            : llmService.chatStreamWithTools(preFlight.llmMessages, preFlight.tools, wrappedContentConsumer,
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
            // 实时推送 Token 更新到回调
            if (stepCallback != null) {
                stepCallback.onTokenUpdate(tokenBudget.getUsedTotal(), tokenBudget.getTotalBudget(), tokenBudget.usageRatio());
            }
        }

        // 打印 AI 思考内容
        if (response.getReasoningContent() != null && !response.getReasoningContent().isEmpty()) {
            String think = response.getReasoningContent();
            logger.info("[LLMQueryEngine] AI思考内容: " + think);
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
            
            // 【优化】无进展检测：检查回复是否仅有工具调用而无实质文本内容
            String content = response.getContent();
            boolean hasSubstantiveContent = content != null && !content.trim().isEmpty()
                && content.trim().length() > 10;
            if (!hasSubstantiveContent) {
                consecutiveToolOnlyRounds++;
                logger.info("[LLMQueryEngine] Tool-only round #" + consecutiveToolOnlyRounds
                    + " (no substantive text content)");
            } else {
                consecutiveToolOnlyRounds = 0;
            }
            
            // 【优化】重复工具检测
            List<String> currentToolNames = response.getToolCalls().stream()
                .map(tc -> tc.getFunction().getName())
                .sorted()
                .toList();
            if (currentToolNames.equals(lastToolNames) && !currentToolNames.isEmpty()) {
                repeatedToolPatternCount++;
                logger.info("[LLMQueryEngine] Repeated tool pattern #" + repeatedToolPatternCount
                    + ": " + currentToolNames);
                if (repeatedToolPatternCount >= MAX_REPEATED_TOOL_CALLS) {
                    logger.warning("[LLMQueryEngine] Repeated tool pattern exceeded threshold, injecting urgency prompt");
                    session.addMessage(Message.createSystemMessage(
                        "【系统提示】你已连续多次调用相同的工具集但未完成任务。"
                        + "请评估当前策略是否有效：如果遇到阻碍，请尝试不同方法；"
                        + "如果任务已实际完成，请直接输出 [FINISH] 结束对话。"
                        + "不要重复调用相同工具而不产生进展。"
                    ));
                    repeatedToolPatternCount = 0;
                }
            } else {
                repeatedToolPatternCount = 0;
            }
            lastToolNames = new java.util.ArrayList<>(currentToolNames);
            
            pipeline.publish(new ObservationEvent.Thinking("分析", "AI决定调用 " + response.getToolCalls().size() + " 个工具"));
            
            // 执行工具调用，工具完成后继续流式循环
            return executeToolCalls(response.getToolCalls(), iteration + 1, emptyResponseCount,
                (iter, emptyCount) -> runStreamConversationLoop(iter, emptyCount, contentConsumer, thinkingConsumer, toolCallConsumer));
        } else {
            // 没有工具调用
            String content = response.getContent();
            assistantMessage = Message.createAssistantMessage(content, response.getReasoningContent());
            session.addMessage(assistantMessage);
            
            // API finish_reason="stop" + 无工具调用 → 模型认为任务完成，直接结束
            if ("stop".equals(response.getFinishReason()) && !response.hasToolCalls()) {
                return CompletableFuture.completedFuture(QueryResult.success(assistantMessage));
            }
            // 检查回复内容是否包含结束标记
            if (content != null && content.contains(FINISH_MARKER)) {
                // 【反编造】执行审计
                logger.info("[LLMQueryEngine] " + getExecutionAuditSummary());

                // 【反编造】预检：多次迭代但零工具调用时拦截
                if (config.isFabricationCheckEnabled() && iteration > 1
                    && executedWriteTools.isEmpty() && executedCommandTools.isEmpty()
                    && executedReadTools.isEmpty() && !fabricationCheckInjected) {
                    logger.warning("[LLMQueryEngine] ⚠️ 反编造拦截(stream): AI 声称完成但零工具调用，注入验证提示");
                    fabricationCheckInjected = true;
                    session.addMessage(Message.createSystemMessage(
                        "【系统反编造检查】你在本次对话中未调用任何工具（读/写/命令均为0），"
                        + "但声称任务完成并标记了 [FINISH]。\n\n"
                        + "请确认：你是否真的执行了用户要求的操作？\n"
                        + "- 如果你实际执行了但系统未记录，请忽略此消息直接重新输出 [FINISH]\n"
                        + "- 如果你编造了完成声明，请立即调用正确的工具真实执行任务"
                    ));
                    return runStreamConversationLoop(iteration + 1, 0,
                        contentConsumer, thinkingConsumer, toolCallConsumer);
                }

                logger.info("[LLMQueryEngine] 检测到结束标记 " + FINISH_MARKER + "，结束对话");

                // 【修复】通过 contentConsumer 回放完整内容（移除 [FINISH] 标记），
                // 确保即使流式过程中 contentConsumer 为 null，最终内容也能送达
                if (contentConsumer != null && content != null) {
                    String displayContent = content.replace(FINISH_MARKER, "").trim();
                    if (!displayContent.isEmpty()) {
                        contentConsumer.accept(displayContent);
                    }
                }

                triggerStepComplete("LLM查询", "完成回复");
                return CompletableFuture.completedFuture(
                    QueryResult.success(assistantMessage)
                );
            }
            
            // 检查是否为空回复
            // 【修复】纯思考（仅有 reasoning，无文本内容、无工具调用）不算有效进展
            String reasoning = response.getReasoningContent();
            boolean hasReasoning = reasoning != null && !reasoning.trim().isEmpty();
            boolean isEmptyContent = content == null || content.trim().isEmpty();
            boolean isThinkingOnly = isEmptyContent && hasReasoning;

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

                session.addMessage(Message.createSystemMessage(EMPTY_RESPONSE_PROMPT));
            } else if (isThinkingOnly) {
                // 【修复】纯思考无行动：不为有效进展，递增计数器
                consecutiveThinkingOnlyRounds++;
                logger.warning("[LLMQueryEngine] 纯思考无行动 (第 " + consecutiveThinkingOnlyRounds
                    + "/" + MAX_CONSECUTIVE_THINKING_ONLY_ROUNDS + " 轮)，iteration=" + iteration);

                if (consecutiveThinkingOnlyRounds >= MAX_CONSECUTIVE_THINKING_ONLY_ROUNDS) {
                    logger.warning("[LLMQueryEngine] 纯思考轮数已达上限，强制结束对话");
                    triggerStepComplete("LLM查询", "模型持续思考但无行动，已自动终止");
                    if (contentConsumer != null) {
                        contentConsumer.accept("\n\n---\n⚠️ **模型持续思考但无行动，已自动终止。**\n\n[FINISH]");
                    }
                    return CompletableFuture.completedFuture(
                        QueryResult.error("模型持续思考但无行动，已自动终止")
                    );
                }

                if (consecutiveThinkingOnlyRounds % 3 == 0) {
                    session.addMessage(Message.createSystemMessage(
                        "【系统提示】你已连续 " + consecutiveThinkingOnlyRounds + " 轮仅有内部思考而无实际输出。"
                        + "请立即做出决定：执行工具操作，或输出文本回复，或在文本末尾添加 [FINISH] 结束对话。"
                    ));
                }
            } else {
                // 有有效文本内容，重置所有停滞计数器
                emptyResponseCount = 0;
                consecutiveThinkingOnlyRounds = 0;

                if (iteration % FINISH_REMINDER_INTERVAL == 0 && !hasRecentSystemPrompt("[FINISH]")) {
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
                boolean allFailed = true; // 【修复】追踪是否所有工具都失败
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
                        } else {
                            allFailed = false; // 至少有一个工具成功
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
                
                // 【修复】更新连续失败工具轮数计数器
                if (allFailed && !futures.isEmpty()) {
                    consecutiveFailedToolRounds++;
                    logger.warning("[LLMQueryEngine] All tools failed in this round (consecutiveFailedToolRounds="
                        + consecutiveFailedToolRounds + "/" + MAX_CONSECUTIVE_FAILED_TOOL_ROUNDS + ")");
                } else if (!allFailed) {
                    consecutiveFailedToolRounds = 0; // 有工具成功，重置计数器
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

                // 【修复】工具执行 = 真实进展，重置纯思考计数器
                consecutiveThinkingOnlyRounds = 0;

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
        
        // 【反编造】审计追踪：记录工具调用分类
        trackToolExecution(toolName);
        
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
        
        // Sync workspace guard bypass from session metadata (allows runtime toggle)
        // Default to true — only disable bypass when metadata is explicitly false
        Boolean bypassWsGuard = session.getMetadata("workspaceGuardBypass");
        toolContext.setBypassWorkspaceGuard(!Boolean.FALSE.equals(bypassWsGuard));

        // Sync YOLO mode from session metadata (allows runtime toggle)
        Boolean yoloEnabled = session.getMetadata("yoloEnabled");
        if (Boolean.TRUE.equals(yoloEnabled)) {
            com.jwcode.core.permission.PermissionManager.getInstance().setYoloMode(true);
        } else if (Boolean.FALSE.equals(yoloEnabled)) {
            com.jwcode.core.permission.PermissionManager.getInstance().setYoloMode(false);
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
     * 【反编造】审计追踪：分类记录实际执行的工具。
     * 用于在任务结束时验证 AI 的完成声明是否有真实工具调用支撑。
     */
    private void trackToolExecution(String toolName) {
        if (toolName == null) return;
        // 写工具
        if (toolName.equals("FileWriteTool") || toolName.equals("FileEditTool")
            || toolName.equals("EditTool") || toolName.equals("NotebookEditTool")
            || toolName.equals("MergeFilesTool")) {
            executedWriteTools.add(toolName);
        }
        // 命令执行工具
        if (toolName.equals("BashTool") || toolName.equals("PowerShellTool")
            || toolName.equals("REPLTool")) {
            executedCommandTools.add(toolName);
        }
        // 读工具
        if (toolName.equals("FileReadTool") || toolName.equals("BatchReadTool")
            || toolName.equals("GlobTool") || toolName.equals("GrepTool")
            || toolName.equals("WebFetchTool") || toolName.equals("WebSearchTool")) {
            executedReadTools.add(toolName);
        }
    }

    /**
     * 【反编造】获取执行审计摘要（用于日志和调试）。
     */
    private String getExecutionAuditSummary() {
        return String.format(
            "[执行审计] 写工具:%d(%s) | 命令工具:%d(%s) | 读工具:%d(%s)",
            executedWriteTools.size(), executedWriteTools,
            executedCommandTools.size(), executedCommandTools,
            executedReadTools.size(), executedReadTools
        );
    }

    /**
     * 【反编造】检查当前任务是否有任何写操作的实际执行记录。
     */
    private boolean hasAnyWriteExecution() {
        return !executedWriteTools.isEmpty();
    }

    /**
     * 【反编造】检查当前任务是否有任何命令执行记录。
     */
    private boolean hasAnyCommandExecution() {
        return !executedCommandTools.isEmpty();
    }
    
    /**
     * 估算压缩释放的 token 数（替代硬编码 500 tokens/消息）。
     * <p>策略：对被压缩前的消息列表采样，使用 TokenBudget.estimateMessageTokens
     * 对最后 messagesRemoved 条消息（通常是最旧的）进行估算求和。</p>
     */
    private long estimateCompactTokenSavings(List<Message> currentMessages, int messagesRemoved) {
        if (messagesRemoved <= 0 || currentMessages == null || currentMessages.isEmpty()) {
            return 0;
        }
        // 采样被移除的消息（假设压缩从旧→新，取最后 messagesRemoved 条来估算）
        int sampleStart = Math.max(0, currentMessages.size() - messagesRemoved);
        long total = 0;
        for (int i = sampleStart; i < currentMessages.size(); i++) {
            total += TokenBudget.estimateMessageTokens(currentMessages.get(i));
        }
        // 至少返回移除数 * 100 作为保底（避免极端低估导致无限循环压缩）
        return Math.max(total, (long) messagesRemoved * 100);
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
            
            // 【Token 银行】压缩联动：基于消息内容估算释放的 token
            int messagesRemoved = originalCount - messages.size();
            long estimatedTokensSaved = estimateCompactTokenSavings(messages, messagesRemoved);
            if (estimatedTokensSaved > 0) {
                tokenBudget.releaseTokens(estimatedTokensSaved);
                logger.info("[LLMQueryEngine] Token 银行: 压缩释放 " + estimatedTokensSaved 
                    + " tokens (移除了 " + messagesRemoved + " 条消息), 当前使用率=" 
                    + String.format("%.1f%%", tokenBudget.usageRatio() * 100));
            }
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
        // 【Phase 5】按当前 Agent 的工具白名单过滤
        Agent currentAgent = agentRegistry != null ? agentRegistry.getCurrent() : null;
        if (currentAgent != null) {
            List<Tool<?, ?, ?>> original = tools;
            tools = tools.stream()
                .filter(t -> currentAgent.canUseTool(t.getName()))
                .toList();
            logger.info("[LLMQueryEngine] Agent '" + currentAgent.getName() + "' 工具过滤: "
                + original.size() + " -> " + tools.size()
                + " | 允许: " + tools.stream().map(Tool::getName).toList());
        }
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
                Map<String, Object> params = MAPPER.convertValue(inputSchema, Map.class);
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
    
    // ==================== Pre-flight check result ====================

    /**
     * Result of {@link #runPreFlightChecks}: either proceed with LLM call,
     * or abort with a terminal result.
     */
    private static class PreFlightResult {
        final CompletableFuture<QueryResult> abort;
        final List<LLMMessage> llmMessages;
        final List<LLMTool> tools;

        private PreFlightResult(CompletableFuture<QueryResult> abort,
                                List<LLMMessage> msgs, List<LLMTool> tools) {
            this.abort = abort;
            this.llmMessages = msgs;
            this.tools = tools;
        }

        static PreFlightResult abort(CompletableFuture<QueryResult> f) {
            return new PreFlightResult(f, null, null);
        }

        static PreFlightResult proceed(List<LLMMessage> msgs, List<LLMTool> tools) {
            return new PreFlightResult(null, msgs, tools);
        }

        boolean shouldAbort() { return abort != null; }
    }

    /**
     * Shared pre-flight checks for both stream and non-stream conversation loops.
     * Extracts ~130 lines of duplicated logic: compact detection, auto-compact,
     * token budget checks, stagnation detection, iteration limits, agent prompt
     * injection, message conversion, tool conversion, and FINISH reminders.
     *
     * @param iteration current loop iteration
     * @param contentConsumer nullable; if non-null and an error occurs, the error
     *                        message is pushed to this consumer (for stream mode)
     * @return {@link PreFlightResult} — call {@link PreFlightResult#shouldAbort()}
     *         to decide whether to terminate or proceed with the LLM call
     */
    private PreFlightResult runPreFlightChecks(int iteration, Consumer<String> contentConsumer) {
        // 1. Detect external compact (e.g. /compact command) and reset TokenBudget
        if (session.getCompactCount() > lastSeenCompactCount) {
            resetTokenBudget();
            lastSeenCompactCount = session.getCompactCount();
            logger.info("[LLMQueryEngine] Context compacted detected, TokenBudget reset.");
        }

        // 2. Auto-compact
        long now = System.currentTimeMillis();
        boolean budgetExhausted = tokenBudget.isExhausted();
        boolean inCooldown = (now - lastCompactTime) <= COMPACT_COOLDOWN_MS;
        boolean highUrgency = tokenBudget.usageRatio() > 0.85;
        if (tokenBudget.usageRatio() > 0.70 && session.getMessages().size() > 10
            && (!inCooldown || budgetExhausted || highUrgency)) {
            String compactReason = budgetExhausted ? "EMERGENCY (budget exhausted)"
                : highUrgency ? "HIGH_URGENCY usage=" + String.format("%.0f%%", tokenBudget.usageRatio() * 100)
                : "usage=" + String.format("%.0f%%", tokenBudget.usageRatio() * 100);
            logger.info("[LLMQueryEngine] Auto-compacting context, reason=" + compactReason + "...");
            int beforeCount = session.getMessages().size();
                                    ContextWindowManager windowManager = createContextWindowManager();
            List<Message> compacted = windowManager.prepareMessages(session.getMessages(), budgetExhausted);
            if (compacted != null && compacted.size() < beforeCount) {
                session.setMessages(compacted);
                session.markCompacted();
                lastCompactTime = now;

                int messagesRemoved = beforeCount - compacted.size();
                long tokensSaved;
                if (budgetExhausted) {
                    tokenBudget.reset();
                    tokensSaved = (long) messagesRemoved * 100;
                } else {
                    tokensSaved = estimateCompactTokenSavings(compacted, messagesRemoved);
                    if (tokensSaved > 0) {
                        tokenBudget.releaseTokens(tokensSaved);
                    }
                }

                String compactSummary = "Token 使用率 " + String.format("%.0f%%", tokenBudget.usageRatio() * 100) + "，自动触发上下文压缩";
                pipeline.publish(new ObservationEvent.ContextCompressed(
                    beforeCount, compacted.size(), tokensSaved, compactSummary));
                if (stepCallback != null) {
                    stepCallback.onContextCompressed(beforeCount, compacted.size(),
                        tokensSaved, compactSummary);
                }

                logger.info("[LLMQueryEngine] Auto-compacted: " + beforeCount + " -> " + session.getMessages().size()
                    + " messages, budget=" + String.format("%.0f%%", tokenBudget.usageRatio() * 100));
            } else {
                logger.warning("[LLMQueryEngine] Auto-compact skipped: result size not reduced (before=" + beforeCount + ")");
            }
        } else if (tokenBudget.usageRatio() > 0.70 && inCooldown && !budgetExhausted && !highUrgency) {
            logger.info("[LLMQueryEngine] Auto-compact skipped: cooldown period active ("
                + ((COMPACT_COOLDOWN_MS - (now - lastCompactTime)) / 1000) + "s remaining, usage="
                + String.format("%.0f%%", tokenBudget.usageRatio() * 100) + ")");
        }

        // 3. Token budget exhausted check
        if (tokenBudget.isExhausted()) {
            logger.warning("[LLMQueryEngine] Token budget exhausted: " + tokenBudget);
            triggerStepComplete("LLM查询", "Token 预算已耗尽");
            if (contentConsumer != null) {
                contentConsumer.accept("\n\n---\n⚠️ **Token 预算已耗尽，任务被迫终止。**\n\n请使用 `/compact` 压缩上下文后重试，或开始新会话。\n\n[FINISH]");
            }
            return PreFlightResult.abort(CompletableFuture.completedFuture(
                QueryResult.error("Token 预算已耗尽，任务被迫终止。请使用 /compact 压缩上下文后重试。")));
        }

        // 4. Token budget advice
        if (iteration > 0) {
            String advice = tokenBudget.toPromptAdvice();
            if (!advice.isEmpty()) {
                this.pendingBudgetAdvice = advice;
            }
        }

        // 5. Iteration limit (backup safety net for TokenBudget)
        if (config.getMaxIterations() > 0 && iteration >= config.getMaxIterations()) {
            logger.warning("[LLMQueryEngine] Max iterations reached: " + config.getMaxIterations());
            triggerStepComplete("LLM查询", "达到最大迭代次数限制");
            return PreFlightResult.abort(CompletableFuture.completedFuture(
                QueryResult.error("达到最大迭代次数限制")));
        }

        // 6. Tool-only stagnation
        if (consecutiveToolOnlyRounds >= config.getMaxConsecutiveToolOnlyRounds()) {
            logger.warning("[LLMQueryEngine] Stagnation: " + consecutiveToolOnlyRounds
                + " consecutive tool-only rounds, force terminating");
            triggerStepComplete("LLM查询", "检测到任务停滞（连续" + consecutiveToolOnlyRounds + "轮仅工具调用无进展）");
            if (contentConsumer != null) {
                contentConsumer.accept("\n\n---\n⚠️ **任务停滞：连续 " + consecutiveToolOnlyRounds + " 轮工具调用无进展，已自动终止。**\n\n[FINISH]");
            }
            return PreFlightResult.abort(CompletableFuture.completedFuture(
                QueryResult.error("任务停滞：连续" + consecutiveToolOnlyRounds + "轮工具调用无进展，已自动终止")));
        }

        // 7. Thinking-only stagnation
        if (consecutiveThinkingOnlyRounds >= MAX_CONSECUTIVE_THINKING_ONLY_ROUNDS) {
            logger.warning("[LLMQueryEngine] Thinking-only stagnation: " + consecutiveThinkingOnlyRounds);
            triggerStepComplete("LLM查询", "模型持续思考但无行动，已自动终止");
            if (contentConsumer != null) {
                contentConsumer.accept("\n\n---\n⚠️ **模型持续思考但无行动，已自动终止。**\n\n[FINISH]");
            }
            return PreFlightResult.abort(CompletableFuture.completedFuture(
                QueryResult.error("模型持续思考但无行动，已自动终止")));
        }

        // 8. Consecutive failed tool rounds
        if (consecutiveFailedToolRounds >= MAX_CONSECUTIVE_FAILED_TOOL_ROUNDS) {
            logger.warning("[LLMQueryEngine] Consecutive failed tool rounds: " + consecutiveFailedToolRounds);
            triggerStepComplete("LLM查询", "连续" + consecutiveFailedToolRounds + "轮工具调用全部失败，已自动终止");
            if (contentConsumer != null) {
                contentConsumer.accept("\n\n---\n⚠️ **连续" + consecutiveFailedToolRounds + "轮工具调用全部失败，已自动终止。**\n\n请检查工具配置或网络连接后重试。\n\n[FINISH]");
            }
            return PreFlightResult.abort(CompletableFuture.completedFuture(
                QueryResult.error("连续" + consecutiveFailedToolRounds + "轮工具调用全部失败，已自动终止。请检查工具配置或网络连接后重试。")));
        }

        // 9. Inject agent system prompt (first iteration only)
        if (iteration == 0) {
            injectAgentSystemPrompt();
            checkTokenBudget();
        }

        // 10. Convert session messages and tools
        List<LLMMessage> llmMessages = convertSessionMessages(session);
        List<LLMTool> tools = convertTools(toolExecutor.getEnabledTools());

        logger.info("[LLMQueryEngine] Iteration " + iteration + ", messages: " + llmMessages.size());

        // 11. FINISH reminder
        if (iteration % FINISH_REMINDER_INTERVAL == 0 && !hasRecentSystemPrompt("[FINISH]")) {
            session.addMessage(Message.createSystemMessage(
                "提示：如果任务已完成，请在回复末尾添加 [FINISH] 标记以结束对话。"));
        }

        return PreFlightResult.proceed(llmMessages, tools);
    }

    // ==================== 数据类 ====================
    
    private void checkAndInjectSwarmPlan(String prompt) {
        Boolean autoSwarmEnabled = session.getMetadata("autoSwarmEnabled");
        if (!Boolean.TRUE.equals(autoSwarmEnabled)) {
            autoSwarmEnabled = Boolean.parseBoolean(
                com.jwcode.core.config.ConfigManager.getInstance().get("autoSwarm.enabled"));
        }
        if (!Boolean.TRUE.equals(autoSwarmEnabled)) return;

        try {
            com.jwcode.core.advanced.swarm.AgentSwarm swarm =
                new com.jwcode.core.advanced.swarm.AgentSwarm(llmService);
            com.jwcode.core.advanced.swarm.AutoSwarmTrigger trigger =
                new com.jwcode.core.advanced.swarm.AutoSwarmTrigger(swarm);
            com.jwcode.core.advanced.swarm.AutoSwarmTrigger.TaskAnalysis analysis = trigger.analyzeTask(prompt);
            logger.info("[AutoSwarm] 复杂度=" + analysis.getComplexity() + " 触发=" + analysis.isShouldUseSwarm());

            if (analysis.isShouldUseSwarm()) {
                var result = swarm.executeComplexTask(prompt, null);
                String plan = result.getFinalResult() != null ? result.getFinalResult().toString() : "";
                session.addMessage(Message.createSystemMessage(
                    "[自动 Agent Swarm 分析]\n" + plan +
                    "\n\n请基于以上 Swarm 分析结果继续执行任务。"));
                logger.info("[AutoSwarm] 已注入 Swarm 分解计划 (" + result.getSubTaskCount() + " 个子任务)");
            }
        } catch (Exception e) {
            logger.warning("[AutoSwarm] 分析失败: " + e.getMessage());
        }
    }

    public static class EngineConfig {
        private int maxIterations = 0; // 默认 0 表示不限制，由 TokenBudget 控制
        private Duration timeout = Duration.ofMinutes(5);
        private int maxEmptyResponses = DEFAULT_MAX_EMPTY_RESPONSES;
        private boolean reasoningModel = false;
        private long tokenBudget = 1_000_000; // 默认 1M token 预算
        private int maxMessageHistory = 0; // 默认 0 表示不限制，由 ContextWindowManager 智能压缩
        private int maxConsecutiveToolOnlyRounds = 100; // 连续仅工具调用无文本回复的轮数上限
        private boolean layeredMode = true; // 默认启用分层多Agent架构
        private boolean fabricationCheckEnabled = true; // 【反编造】默认启用执行前自检
        
        /**
         * 从 JwcodeConfig 创建 EngineConfig
         */
        public static EngineConfig fromJwcodeConfig(JwcodeConfig config) {
            EngineConfig engineConfig = new EngineConfig();
            if (config != null && config.getSettings() != null) {
                JwcodeConfig.EngineSettings engine = config.getSettings().getEngine();
                if (engine != null) {
                    // 【修复】只有当配置值 > 0 时才覆盖默认值，确保默认不限制迭代次数
                    int configuredMaxIterations = engine.getMaxIterations();
                    if (configuredMaxIterations > 0) {
                        engineConfig.setMaxIterations(configuredMaxIterations);
                    }
                    // 否则保持默认值 0（无限制）
                    
                    engineConfig.setTimeout(Duration.ofMinutes(engine.getTimeoutMinutes()));
                    engineConfig.setTokenBudget(engine.getTokenBudget());
                    engineConfig.setMaxMessageHistory(engine.getMaxMessageHistory());
                    if (engine.getMaxConsecutiveToolOnlyRounds() > 0) {
                        engineConfig.setMaxConsecutiveToolOnlyRounds(engine.getMaxConsecutiveToolOnlyRounds());
                    }
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

        public int getMaxConsecutiveToolOnlyRounds() { return maxConsecutiveToolOnlyRounds; }
        public void setMaxConsecutiveToolOnlyRounds(int maxConsecutiveToolOnlyRounds) { this.maxConsecutiveToolOnlyRounds = maxConsecutiveToolOnlyRounds; }

        /** 是否启用分层多Agent架构（Orchestrator→Worker→Tool→MCP） */
        public boolean isLayeredMode() { return layeredMode; }
        public void setLayeredMode(boolean layeredMode) { this.layeredMode = layeredMode; }

        /** 【反编造】是否启用执行前自检（默认 true） */
        public boolean isFabricationCheckEnabled() { return fabricationCheckEnabled; }
        public void setFabricationCheckEnabled(boolean fabricationCheckEnabled) { this.fabricationCheckEnabled = fabricationCheckEnabled; }

        public static EngineConfig defaultConfig() {
            return new EngineConfig();
        }

        /** 创建强制启用分层模式的配置 */
        public static EngineConfig forceLayeredMode() {
            EngineConfig config = new EngineConfig();
            config.setLayeredMode(true);
            return config;
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
    public TokenBudget getTokenBudget() { return tokenBudget; }
    public String getModelName() { return llmService != null ? llmService.getModelName() : "unknown"; }
    public AgentRegistry getAgentRegistry() { return agentRegistry; }
    public void setAgentRegistry(AgentRegistry agentRegistry) { this.agentRegistry = agentRegistry; }
    
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
        private AgentRegistry agentRegistry;
        
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

        public Builder agentRegistry(AgentRegistry agentRegistry) {
            this.agentRegistry = agentRegistry;
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
                toolExecutor = new ToolExecutor(
                    toolRegistry != null ? toolRegistry : ToolRegistry.createDefault(),
                    new PermissionManagerChecker(),
                    null,
                    null);
            }
            return new LLMQueryEngine(session, llmService, toolExecutor, config, agentRegistry);
        }
    }
}

package com.jwcode.core.agent;

import com.jwcode.core.a2a.A2AConfig;
import com.jwcode.core.a2a.A2AFacade;
import com.jwcode.core.a2a.model.A2ATask;
import com.jwcode.core.a2a.model.AgentCard;
import com.jwcode.core.a2a.model.TaskOutput;
import com.jwcode.core.api.PlanTaskBroadcaster;
import com.jwcode.core.config.ResourcePromptLoader;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.model.PlanTask;
import com.jwcode.core.model.StructuredTask;
import com.jwcode.core.planner.IntentAnalyzer;
import com.jwcode.core.planner.IntentAnalyzer.AnalysisResult;
import com.jwcode.core.planner.IntentAnalyzer.TaskType;
import com.jwcode.core.planner.IntentAnalyzer.Complexity;
import com.jwcode.core.planner.SemanticIntentAnalyzer;
import com.jwcode.core.planner.checkpoint.CheckpointManager;
import com.jwcode.core.planner.checkpoint.CheckpointManager.Checkpoint;
import com.jwcode.core.planner.checkpoint.SharedContextBus;
import com.jwcode.core.planner.LayeredTaskRepresentation;
import com.jwcode.core.planner.PlanStep;
import com.jwcode.core.report.ExecutionReport;
import com.jwcode.core.report.ExecutionReport.*;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * EnhancedOrchestratorAgent — 增强版 Orchestrator。
 *
 * <p>整合了意图识别、任务拆解、Checkpoint 中断恢复、SharedContextBus 共享上下文、
 * ExecutionReport 报告生成等完整流程。</p>
 *
 * <p>工作流程：</p>
 * <pre>
 * 1. SemanticIntentAnalyzer 分析用户输入（LLM驱动 + 正则fallback）
 * 2. 如果是中断 → 保存 Checkpoint → 处理新任务
 * 3. 如果是闲聊 → 直接回复
 * 4. 如果是任务 → 拆解 → 创建 A2ATask → 通过 A2AFacade 调度子Agent → 验证 → 生成报告
 * </pre>
 */
public class EnhancedOrchestratorAgent {

    private static final Logger logger = Logger.getLogger(EnhancedOrchestratorAgent.class.getName());

    /** Jackson ObjectMapper — 用于安全 JSON 序列化 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 语义意图分析器（LLM驱动 + 正则fallback） */
    private final SemanticIntentAnalyzer intentAnalyzer;
    private final CheckpointManager checkpointManager;
    private final SharedContextBus contextBus;

    // A2A 协议调度门面
    private A2AFacade a2aFacade;

    // LLM 执行引擎依赖（仅通过构造器传递给 A2AFacade，不直接持有工具引用）
    private LLMService llmService;

    // Plan 模式广播器（用于向前端推送任务状态）
    private PlanTaskBroadcaster planTaskBroadcaster;

    // 当前会话 ID（用于 WebSocket 消息路由）
    private String sessionId;

    // 任务结构化 Agent（将 AI 回复转为结构化任务列表）
    private final TaskAgent taskAgent;

    // 任务执行 Agent（按结构化任务列表逐步执行）
    private TaskExecutionAgent taskExecutionAgent;

    // 当前结构化任务列表（用于前端展示）
    private List<StructuredTask> currentStructuredTasks;

    // 工作目录记忆 Agent（按工作目录主动记忆项目信息）
    private MemoryAgent memoryAgent;

    // 当前执行上下文
    private String currentTaskId;
    private String currentTaskGoal;
    private LocalDateTime taskStartTime;
    private final List<SubTaskResult> completedSubTasks;
    private final List<FileChange> changes;
    private final List<TestResult> testResults;
    private final List<ReviewFinding> reviewFindings;
    private final List<TimelineEntry> timeline;
    private final List<String> recommendations;
    
    /** 分层任务表征（Phase 4 接线）：防 Goal 漂移 */
    private LayeredTaskRepresentation layeredTask;

    /**
     * 默认构造器（无 LLMService — 子Agent将回退到模拟执行）
     */
    public EnhancedOrchestratorAgent() {
        this(null, null, null);
    }

    /**
     * 完整构造器，支持传入 LLMService、ToolRegistry 和 AgentRegistry
     * 这些依赖仅传递给 A2AFacade → LocalAgentDispatcher，Orchestrator 自身不持有工具引用
     * （符合 AGENTS.md 架构规范：第1层不直接调用工具）
     *
     * @param llmService    LLM 服务
     * @param toolRegistry  工具注册表
     * @param toolExecutor  工具执行器
     * @param agentRegistry Agent 注册表（复用已有实例，避免重复创建）
     */
    public EnhancedOrchestratorAgent(LLMService llmService,
                                     ToolRegistry toolRegistry,
                                     ToolExecutor toolExecutor,
                                     AgentRegistry agentRegistry) {
        this.intentAnalyzer = new SemanticIntentAnalyzer(llmService);
        this.checkpointManager = new CheckpointManager();
        this.contextBus = new SharedContextBus();
        this.completedSubTasks = new ArrayList<>();
        this.changes = new ArrayList<>();
        this.testResults = new ArrayList<>();
        this.reviewFindings = new ArrayList<>();
        this.timeline = new ArrayList<>();
        this.recommendations = new ArrayList<>();
        this.llmService = llmService;
        this.taskAgent = new TaskAgent();
        this.currentStructuredTasks = new ArrayList<>();
        initA2A(toolRegistry, toolExecutor, agentRegistry);
    }

    /**
     * 完整构造器（向后兼容，无 AgentRegistry）
     */
    public EnhancedOrchestratorAgent(LLMService llmService,
                                     ToolRegistry toolRegistry,
                                     ToolExecutor toolExecutor) {
        this(llmService, toolRegistry, toolExecutor, null);
    }

    /**
     * 初始化 A2A 协议支持
     * 将 ToolRegistry、ToolExecutor、AgentRegistry 传递给 A2AFacade，Orchestrator 自身不持有
     */
    private void initA2A(ToolRegistry toolRegistry, ToolExecutor toolExecutor, AgentRegistry agentRegistry) {
        try {
            // 优先复用传入的 AgentRegistry，避免重复创建（减少日志刷屏和性能浪费）
            AgentRegistry registry = agentRegistry != null ? agentRegistry : AgentRegistry.createDefault();
            this.a2aFacade = new A2AFacade(registry, new A2AConfig(), llmService, toolRegistry, toolExecutor);
            logger.info("A2A Facade initialized: " + a2aFacade.getDispatcherName()
                + " | llmService=" + (llmService != null ? "available" : "null (mock fallback)"));
        } catch (Exception e) {
            logger.warning("Failed to initialize A2A Facade: " + e.getMessage());
            this.a2aFacade = null;
        }
    }

    /**
     * 处理用户输入的主入口
     *
     * @param userInput 用户输入
     * @return 响应字符串
     */
    public String processInput(String userInput) {
        // Step 1: 意图识别
        AnalysisResult analysis = intentAnalyzer.analyze(userInput, currentTaskId);

        logger.info("Intent analysis: " + analysis);

        // Step 2: 处理中断
        if (analysis.isInterruption() && currentTaskId != null) {
            return handleInterruption(analysis);
        }

        // Step 3: 根据任务类型分发
        switch (analysis.getTaskType()) {
            case CHAT:
                return handleChat(userInput);
            case FEATURE:
                return startNewTask(analysis, "feature-dev");
            case BUGFIX:
                return startNewTask(analysis, "bug-fix");
            case REFACTOR:
                return startNewTask(analysis, "refactoring");
            case REVIEW:
                return startNewTask(analysis, "code-review");
            case TEST:
                return startNewTask(analysis, null);
            case DOC:
                return startNewTask(analysis, null);
            case ANALYZE:
                return startNewTask(analysis, null);
            case DEBUG:
                return startNewTask(analysis, "bug-fix");
            default:
                return startNewTask(analysis, null);
        }
    }

    /**
     * 处理 AI 确认后的 plan 回复（plan/act 模式专用入口）。
     *
     * <p>当 AI 在 plan 模式下生成了执行计划，用户确认后调用此方法。
     * 此方法使用 TaskAgent 将 AI 回复转为结构化任务，并通过
     * TaskExecutionAgent 逐步执行。MemoryAgent 的记忆上下文会自动注入。</p>
     *
     * @param aiPlanResponse AI 生成的 plan 回复全文
     * @param goal           任务目标
     * @return 执行结果字符串
     */
    public String processConfirmedPlan(String aiPlanResponse, String goal) {
        logger.info("[Orchestrator] Processing confirmed plan for goal: " + goal);

        // 注入 MemoryAgent 记忆上下文（如果可用）
        if (memoryAgent != null && memoryAgent.isEnabled()) {
            String memCtx = memoryAgent.getPlanContextPrompt();
            if (!memCtx.isEmpty()) {
                // 将记忆上下文作为 plan 分析的前置信息
                aiPlanResponse = "## 项目记忆提示\n" + memCtx + "\n\n## AI 执行计划\n" + aiPlanResponse;
                logger.info("[Orchestrator] MemoryAgent context injected (" + memCtx.length() + " chars)");
            }
        }

        return executeWithStructuredPlan(aiPlanResponse, goal);
    }

    /**
     * 获取 enhanced plan prompt — 包含 MemoryAgent 记忆上下文的完整 prompt。
     * 在 plan 模式下，应该使用此方法获取 prompt 发送给 AI。
     *
     * @param userRequest 用户原始请求
     * @return 增强后的 prompt（含项目记忆）
     */
    public String getEnhancedPlanPrompt(String userRequest) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(userRequest);

        if (memoryAgent != null && memoryAgent.isEnabled()) {
            String memCtx = memoryAgent.getPlanContextPrompt();
            if (!memCtx.isEmpty()) {
                prompt.insert(0, "以下是你对本项目的已知信息（由 MemoryAgent 维护），请在制定计划时充分利用这些信息：\n\n");
                prompt.insert(0, memCtx);
                prompt.insert(0, "\n---\n\n");
            }
        }

        return prompt.toString();
    }

    /**
     * 处理闲聊
     */
    private String handleChat(String userInput) {
        StringBuilder sb = new StringBuilder();
        sb.append("👋 你好！我是 JWCode Orchestrator，你的 AI 软件工程助手。\n\n");
        sb.append("我可以帮你完成以下任务：\n");
        sb.append("- **开发新功能**：从设计到实现到测试的全流程\n");
        sb.append("- **修复 Bug**：定位根因 → 修复 → 回归测试\n");
        sb.append("- **代码重构**：分析 → 计划 → 执行 → 验证\n");
        sb.append("- **代码审查**：质量、安全、风格检查\n");
        sb.append("- **编写测试**：单元测试、集成测试\n");
        sb.append("- **编写文档**：README、API文档\n");
        sb.append("- **代码分析**：结构分析、技术债务评估\n\n");
        sb.append("请告诉我你需要什么帮助？");

        if (currentTaskId != null) {
            sb.append("\n\n⚠️ 你有一个正在进行的任务 (`").append(currentTaskId).append("`)：");
            sb.append("\"").append(currentTaskGoal).append("\"");
            sb.append("\n输入 `/resume` 可以恢复该任务。");
        }

        return sb.toString();
    }

    /**
     * 处理中断
     */
    private String handleInterruption(AnalysisResult analysis) {
        // 保存检查点
        saveCheckpoint();

        String input = analysis.getSummary();

        // 处理 /resume
        if (input.contains("/resume") || input.contains("恢复")) {
            return resumeTask();
        }

        // 处理 /stop, /cancel
        if (input.contains("/stop") || input.contains("/cancel") || 
            input.contains("停止") || input.contains("取消")) {
            cancelCurrentTask();
            return "✅ 当前任务已取消。检查点已保存，你可以随时输入 `/resume` 恢复。";
        }

        // 处理 /pause
        if (input.contains("/pause") || input.contains("暂停")) {
            return "⏸️ 任务已暂停。检查点已保存，你可以随时输入 `/resume` 恢复。";
        }

        // 处理其他斜杠命令
        if (input.startsWith("/")) {
            return handleSlashCommand(input);
        }

        // 新任务中断旧任务
        return "⏸️ 当前任务已暂停并保存检查点。\n\n" +
               "检测到新请求，正在处理...\n\n" +
               processInput(analysis.getSummary().replaceAll("\\[.*?\\] ", ""));
    }

    /**
     * 处理斜杠命令
     */
    private String handleSlashCommand(String input) {
        String cmd = input.toLowerCase().trim();

        if (cmd.contains("/help")) {
            return """
                📖 **JWCode 斜杠命令**
                - `/design` — 仅做设计
                - `/debug` — 仅调试
                - `/review` — 仅代码审查
                - `/test` — 仅编写测试
                - `/doc` — 仅编写文档
                - `/resume` — 恢复暂停的任务
                - `/pause` — 暂停当前任务
                - `/stop` — 停止当前任务
                - `/cancel` — 取消当前任务
                - `/status` — 查看当前任务状态
                - `/report` — 生成当前任务报告
                """;
        }

        if (cmd.contains("/status")) {
            return getCurrentStatus();
        }

        if (cmd.contains("/report")) {
            return generateReport();
        }

        return "未知命令: " + input + "\n输入 `/help` 查看可用命令。";
    }

    /**
     * 开始新任务 — 真正创建 A2ATask 并通过 A2AFacade 调度子Agent 执行
     */
    private String startNewTask(AnalysisResult analysis, String templateName) {
        // 重置任务上下文
        this.currentTaskId = UUID.randomUUID().toString().substring(0, 8);
        this.currentTaskGoal = analysis.getSummary();
        this.taskStartTime = LocalDateTime.now();
        this.completedSubTasks.clear();
        this.changes.clear();
        this.testResults.clear();
        this.reviewFindings.clear();
        this.timeline.clear();
        this.recommendations.clear();
        this.contextBus.clear();
        
        // 【Phase 4 接线】初始化分层任务表征，防 Goal 漂移
        this.layeredTask = new LayeredTaskRepresentation(analysis.getSummary());

        // 广播 plan_start 消息到前端
        if (planTaskBroadcaster != null && sessionId != null) {
            planTaskBroadcaster.broadcastPlanStart(sessionId,
                "开始分析用户请求: " + analysis.getSummary());
        }

        StringBuilder sb = new StringBuilder();

        // 输出任务分析结果
        sb.append("## 🔍 Intent Analysis\n\n");
        sb.append("| Field | Value |\n");
        sb.append("|-------|-------|\n");
        sb.append("| Task ID | `").append(currentTaskId).append("` |\n");
        sb.append("| Task Type | ").append(analysis.getTaskType().getDisplayName()).append(" |\n");
        sb.append("| Complexity | ").append(analysis.getComplexity().getDisplayName()).append(" |\n");
        if (!analysis.getModulesInvolved().isEmpty()) {
            sb.append("| Modules | ").append(String.join(", ", analysis.getModulesInvolved())).append(" |\n");
        }
        if (!analysis.getTechStack().isEmpty()) {
            sb.append("| Tech Stack | ").append(analysis.getTechStack()).append(" |\n");
        }
        sb.append("\n");

        // 根据复杂度决定策略并执行
        try {
            switch (analysis.getComplexity()) {
                case SIMPLE:
                    sb.append("### 📋 Plan: Direct Assignment\n\n");
                    sb.append("This is a simple task. Assigning directly to a sub-agent.\n\n");
                    String simpleAgent = getAgentForTaskType(analysis.getTaskType());
                    sb.append("**Sub-task**: ").append(analysis.getSummary()).append("\n");
                    sb.append("**Assigned to**: ").append(simpleAgent).append("\n\n");

                    // 真正创建并提交任务
                    TaskOutput simpleOutput = executeSingleTask(simpleAgent, analysis);
                    sb.append(formatTaskOutput(simpleOutput));
                    break;

                case MEDIUM:
                    sb.append("### 📋 Plan: Decomposed Execution\n\n");
                    sb.append("This is a medium complexity task. Breaking into sub-tasks.\n\n");
                    List<SubTaskPlan> mediumSteps = buildMediumPlan(analysis, templateName);
                    sb.append(formatPlanTable(mediumSteps));
                    sb.append("\n");

                    // 真正执行子任务（串行执行，因为存在依赖关系）
                    List<TaskOutput> mediumResults = executePlanSequentially(mediumSteps);
                    sb.append(formatAllResults(mediumResults));
                    break;

                case COMPLEX:
                    sb.append("### 📋 Plan: Complex Task\n\n");
                    sb.append("This is a complex task. Starting with exploration phase.\n\n");

                    // Phase 1: 先派 Explorer 调研
                    sb.append("**Phase 1**: Explore codebase structure\n");
                    TaskOutput exploreOutput = executeSingleTask("Explorer", analysis);
                    sb.append(formatTaskOutput(exploreOutput));
                    contextBus.put("exploration_result", exploreOutput);

                    // Phase 2: 派 Architect 设计
                    sb.append("**Phase 2**: Design architecture\n");
                    TaskOutput archOutput = executeSingleTask("Architect", analysis);
                    sb.append(formatTaskOutput(archOutput));
                    contextBus.put("architecture_result", archOutput);

                    // Phase 3: 派 Coder 实现
                    sb.append("**Phase 3**: Implement in phases\n");
                    TaskOutput coderOutput = executeSingleTask("Coder", analysis);
                    sb.append(formatTaskOutput(coderOutput));

                    // Phase 4: 派 Tester 测试
                    sb.append("**Phase 4**: Test and verify\n");
                    TaskOutput testerOutput = executeSingleTask("Tester", analysis);
                    sb.append(formatTaskOutput(testerOutput));

                    // Phase 5: 派 Reviewer 审查
                    sb.append("**Phase 5**: Review and report\n");
                    TaskOutput reviewerOutput = executeSingleTask("Reviewer", analysis);
                    sb.append(formatTaskOutput(reviewerOutput));

                    // Phase 6: GAN 迭代循环（Generator ⇄ Evaluator）
                    sb.append("**Phase 6**: GAN iterative refinement (Generator ⇄ Evaluator)\n");
                    String ganResult = executeGanIteration(analysis, coderOutput);
                    sb.append(ganResult);
                    break;
            }
        } catch (Exception e) {
            logger.warning("Task execution failed: " + e.getMessage());
            sb.append("\n\n⚠️ **Task execution error**: ").append(e.getMessage()).append("\n");
        }

        sb.append("\n---\n");
        sb.append("✅ Execution completed.\n");

        // 生成执行报告摘要
        if (!completedSubTasks.isEmpty()) {
            sb.append("\n### 📊 Execution Summary\n");
            sb.append("- Total sub-tasks: ").append(completedSubTasks.size()).append("\n");
            long successCount = completedSubTasks.stream()
                .filter(st -> st.getStatus() == Status.SUCCESS).count();
            sb.append("- Successful: ").append(successCount).append("\n");
            sb.append("- Failed: ").append(completedSubTasks.size() - successCount).append("\n");
        }

        // 广播 plan_tasks — 发送完整的任务树到前端
        if (planTaskBroadcaster != null && sessionId != null) {
            List<PlanTask> taskTree = buildPlanTaskTree();
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
                String tasksJson = mapper.writeValueAsString(taskTree);
                planTaskBroadcaster.broadcastPlanTasks(sessionId, tasksJson);
            } catch (Exception e) {
                logger.warning("Failed to serialize plan task tree: " + e.getMessage());
            }
        }

        // 广播 plan_complete — 全部任务完成
        if (planTaskBroadcaster != null && sessionId != null) {
            planTaskBroadcaster.broadcastPlanComplete(sessionId, "任务执行完成");
        }

        return sb.toString();
    }

    // ==================== 子任务调度核心方法 ====================

    /**
     * 子任务计划 — 描述一个待执行的子任务
     */
    private static class SubTaskPlan {
        final int step;
        final String agentName;
        final String description;

        SubTaskPlan(int step, String agentName, String description) {
            this.step = step;
            this.agentName = agentName;
            this.description = description;
        }
    }

    /**
     * 执行 GAN 迭代循环（Generator ⇄ Evaluator）。
     *
     * <p>在标准执行流程后添加的迭代式生成-评估对抗循环。
     * 使用 SprintContract 定义验收标准和评分权重，
     * 通过 IterativeSprintOrchestrator 驱动最多 N 轮迭代。</p>
     */
    private String executeGanIteration(AnalysisResult analysis, TaskOutput coderOutput) {
        StringBuilder sb = new StringBuilder();

        try {
            // 创建 SprintContract
            com.jwcode.core.model.SprintContract contract;
            boolean isFrontend = analysis.getTechStack() != null
                && (analysis.getTechStack().toLowerCase().contains("react")
                    || analysis.getTechStack().toLowerCase().contains("vue")
                    || analysis.getTechStack().toLowerCase().contains("ui")
                    || analysis.getTechStack().toLowerCase().contains("frontend"));
            boolean isBackend = analysis.getTechStack() != null
                && (analysis.getTechStack().toLowerCase().contains("api")
                    || analysis.getTechStack().toLowerCase().contains("backend")
                    || analysis.getTechStack().toLowerCase().contains("database"));

            if (isFrontend) {
                contract = com.jwcode.core.model.SprintContract.createFrontendContract(
                    analysis.getSummary(), currentTaskId);
                sb.append("  > 使用前端权重配置（视觉设计权重最高: 0.35）\n");
            } else if (isBackend) {
                contract = com.jwcode.core.model.SprintContract.createBackendContract(
                    analysis.getSummary(), currentTaskId);
                sb.append("  > 使用后端权重配置（功能性权重最高: 0.35）\n");
            } else {
                contract = com.jwcode.core.model.SprintContract.createFullstackContract(
                    analysis.getSummary(), currentTaskId);
                sb.append("  > 使用全栈权重配置\n");
            }

            // 添加验收标准
            contract.addAcceptanceCriterion("功能完整性：所有功能按规格实现");
            contract.addAcceptanceCriterion("正确性：核心逻辑无错误");
            contract.addAcceptanceCriterion("代码质量：符合项目编码规范");
            contract.addAcceptanceCriterion("边界处理：空状态、错误状态、异常输入");

            // 进入谈判状态
            contract.startNegotiation();

            // 模拟双方签署（在实际系统中，应由 Generator 和 Evaluator 各自确认）
            contract.signByGenerator();
            contract.signByEvaluator();

            sb.append("  > Sprint Contract 已签署: ").append(contract.getContractId()).append("\n");
            sb.append("  > 最大迭代轮数: ").append(contract.getMaxIterations()).append("\n\n");

            // 执行迭代循环
            AgentRegistry registry = AgentRegistry.createDefault();
            com.jwcode.core.service.IterativeSprintOrchestrator orchestrator =
                new com.jwcode.core.service.IterativeSprintOrchestrator(registry);

            com.jwcode.core.service.IterativeSprintOrchestrator.IterationResult result =
                orchestrator.executeSprint(contract, coderOutput.getSummary());

            if (result.isSuccess()) {
                sb.append("  ✅ GAN 迭代循环完成: ").append(result.toSummary()).append("\n");
            } else {
                sb.append("  ⚠️ GAN 迭代循环未完全通过: ").append(result.toSummary()).append("\n");
                sb.append("  > 最后一次评估报告:\n");
                if (result.getLastReport() != null) {
                    sb.append("  > Verdict: ").append(result.getLastReport().getVerdict().getLabel()).append("\n");
                    sb.append("  > 加权总分: ").append(String.format("%.2f",
                        result.getLastReport().getWeightedTotalScore())).append("/10.0\n");
                }
            }

            // 记录到时间线
            timeline.add(new TimelineEntry(
                "GAN Iteration: " + contract.getContractId() + " (" + result.getTotalIterations() + " rounds)",
                java.time.Duration.ZERO, "Evaluator"));

        } catch (Exception e) {
            logger.warning("GAN iteration failed: " + e.getMessage());
            sb.append("  ⚠️ GAN 迭代循环异常: ").append(e.getMessage()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 执行单个子任务（同步），并在执行过程中广播任务状态到前端
     */
    private TaskOutput executeSingleTask(String agentName, AnalysisResult analysis) {
        if (a2aFacade == null) {
            logger.warning("A2AFacade not available, returning mock output");
            return TaskOutput.success("A2AFacade not initialized. Task would be executed by " + agentName);
        }

        // 创建 A2ATask
        Map<String, Object> input = new HashMap<>();
        input.put("summary", analysis.getSummary());
        input.put("modules", analysis.getModulesInvolved());
        input.put("techStack", analysis.getTechStack());
        input.put("taskType", analysis.getTaskType().getKeyword());
        input.put("complexity", analysis.getComplexity().getKeyword());

        // 从共享上下文总线注入上游结果
        if (contextBus.containsKey("exploration_result")) {
            input.put("explorationContext", "Exploration phase completed");
        }
        if (contextBus.containsKey("architecture_result")) {
            input.put("architectureContext", "Architecture design completed");
        }

        A2ATask task = A2ATask.create(
            getSkillIdForAgent(agentName),
            analysis.getSummary(),
            input
        );

        logger.info("Submitting task " + task.getTaskId() + " to agent " + agentName);

        // 广播 plan_task_start — 子任务开始执行
        if (planTaskBroadcaster != null && sessionId != null) {
            planTaskBroadcaster.broadcastPlanTaskStart(sessionId, task.getTaskId(), agentName.toLowerCase());
        }

        LocalDateTime stepStart = LocalDateTime.now();
        TaskOutput output;
        try {
            // 同步提交（阻塞等待结果）
            output = a2aFacade.submitTaskSync(agentName, task);
        } catch (Exception e) {
            logger.warning("Task " + task.getTaskId() + " failed: " + e.getMessage());
            output = TaskOutput.success("Task failed: " + e.getMessage());
            
            // 【Phase 4 接线】子Agent失败时更新分层任务表征，检查是否需要重规划
            if (layeredTask != null) {
                layeredTask.getExecutionLayer().recordToolExecution(
                    agentName, analysis.getSummary(), e.getMessage(), false);
                if (layeredTask.getPlanningLayer().needsReplanning()) {
                    logger.warning("[LayeredTask] 子Agent失败率 > 50%，触发保护性重规划 | goal='" +
                        layeredTask.getGoalLayer().getRefinedGoal() + "'");
                }
            }
        }
        Duration stepDuration = Duration.between(stepStart, LocalDateTime.now());

        // 记录子任务结果
        Status stepStatus = output.isSuccess() ? Status.SUCCESS : Status.FAILED;
        completedSubTasks.add(new SubTaskResult(
            task.getTaskId(),
            agentName,
            analysis.getSummary(),
            stepStatus,
            output.getSummary(),
            stepDuration
        ));

        // 广播 plan_task_result — 子任务完成/失败
        if (planTaskBroadcaster != null && sessionId != null) {
            String status = output.isSuccess() ? "completed" : "failed";
            String result = output.getSummary();
            String error = output.isSuccess() ? null : output.getSummary();
            planTaskBroadcaster.broadcastPlanTaskResult(sessionId, task.getTaskId(), status, result, error);
        }

        // 记录时间线
        timeline.add(new TimelineEntry(
            agentName + ": " + truncate(analysis.getSummary(), 50),
            stepDuration,
            agentName
        ));

        // 提取文件变更
        for (TaskOutput.FileChange fc : output.getFileChanges()) {
            FileChange.Operation op = fc.getOperation() == TaskOutput.FileChange.Operation.ADDED
                ? FileChange.Operation.ADDED
                : fc.getOperation() == TaskOutput.FileChange.Operation.MODIFIED
                    ? FileChange.Operation.MODIFIED
                    : FileChange.Operation.DELETED;
            changes.add(new FileChange(op, fc.getFilePath(), fc.getLinesAdded(), fc.getLinesDeleted()));
        }

        return output;
    }

    /**
     * 串行执行一组子任务计划
     */
    private List<TaskOutput> executePlanSequentially(List<SubTaskPlan> steps) {
        List<TaskOutput> results = new ArrayList<>();
        for (SubTaskPlan step : steps) {
            logger.info("Executing step " + step.step + ": " + step.agentName + " - " + step.description);
            TaskOutput output = executeSingleTask(step.agentName, 
                new AnalysisResult(
                    IntentAnalyzer.TaskType.GENERAL,
                    IntentAnalyzer.Complexity.SIMPLE,
                    Collections.emptyList(), "",
                    step.description, false, null
                )
            );
            results.add(output);
        }
        return results;
    }

    // ==================== 计划构建方法 ====================

    /**
     * 构建中等复杂度任务的执行计划
     */
    private List<SubTaskPlan> buildMediumPlan(AnalysisResult analysis, String templateName) {
        List<SubTaskPlan> steps = new ArrayList<>();

        switch (analysis.getTaskType()) {
            case FEATURE:
                steps.add(new SubTaskPlan(1, "Explorer", "Analyze existing codebase for feature implementation"));
                steps.add(new SubTaskPlan(2, "Architect", "Design feature interface and architecture"));
                steps.add(new SubTaskPlan(3, "Coder", "Implement the feature"));
                steps.add(new SubTaskPlan(4, "Tester", "Write and execute tests for the feature"));
                steps.add(new SubTaskPlan(5, "Reviewer", "Review code quality and security"));
                break;

            case BUGFIX:
            case DEBUG:
                steps.add(new SubTaskPlan(1, "Debug", "Reproduce and analyze root cause of the bug"));
                steps.add(new SubTaskPlan(2, "Coder", "Implement the fix"));
                steps.add(new SubTaskPlan(3, "Tester", "Write regression test to verify the fix"));
                steps.add(new SubTaskPlan(4, "Reviewer", "Review fix quality and impact"));
                break;

            case REFACTOR:
                steps.add(new SubTaskPlan(1, "Explorer", "Analyze current code structure"));
                steps.add(new SubTaskPlan(2, "Architect", "Design refactoring plan"));
                steps.add(new SubTaskPlan(3, "Coder", "Execute refactoring changes"));
                steps.add(new SubTaskPlan(4, "Tester", "Run full test suite to verify"));
                steps.add(new SubTaskPlan(5, "Reviewer", "Review refactoring quality"));
                break;

            case REVIEW:
                steps.add(new SubTaskPlan(1, "Reviewer", "Full code review for quality, security, and style"));
                break;

            case TEST:
                steps.add(new SubTaskPlan(1, "Explorer", "Analyze code to understand test requirements"));
                steps.add(new SubTaskPlan(2, "Tester", "Design and implement test cases"));
                break;

            case DOC:
                steps.add(new SubTaskPlan(1, "Explorer", "Scan codebase for documentation needs"));
                steps.add(new SubTaskPlan(2, "Documenter", "Write documentation"));
                steps.add(new SubTaskPlan(3, "Reviewer", "Review documentation accuracy"));
                break;

            case ANALYZE:
                steps.add(new SubTaskPlan(1, "Explorer", "Analyze codebase structure and dependencies"));
                break;

            default:
                steps.add(new SubTaskPlan(1, "Explorer", "Analyze context and requirements"));
                steps.add(new SubTaskPlan(2, "Coder", "Execute the task"));
                steps.add(new SubTaskPlan(3, "Tester", "Verify results"));
                break;
        }

        return steps;
    }

    /**
     * 格式化计划表格
     */
    private String formatPlanTable(List<SubTaskPlan> steps) {
        StringBuilder sb = new StringBuilder();
        sb.append("| Step | Agent | Description |\n");
        sb.append("|------|-------|-------------|\n");
        for (SubTaskPlan step : steps) {
            sb.append("| ").append(step.step).append(" | ")
              .append(step.agentName).append(" | ")
              .append(step.description).append(" |\n");
        }
        return sb.toString();
    }

    /**
     * 格式化单个任务输出
     */
    private String formatTaskOutput(TaskOutput output) {
        StringBuilder sb = new StringBuilder();
        sb.append("> **Result**: ").append(output.getSummary()).append("\n\n");

        if (!output.getFileChanges().isEmpty()) {
            sb.append("> **Files changed**:\n");
            for (TaskOutput.FileChange fc : output.getFileChanges()) {
                String op = fc.getOperation() == TaskOutput.FileChange.Operation.ADDED ? "➕ ADD" :
                           fc.getOperation() == TaskOutput.FileChange.Operation.MODIFIED ? "✏️ MOD" : "❌ DEL";
                sb.append("> - ").append(op).append(" `").append(fc.getFilePath()).append("`");
                if (fc.getLinesAdded() > 0 || fc.getLinesDeleted() > 0) {
                    sb.append(" (+").append(fc.getLinesAdded()).append("/-").append(fc.getLinesDeleted()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (!output.getMessages().isEmpty()) {
            sb.append("> **Messages**:\n");
            for (String msg : output.getMessages()) {
                sb.append("> - ").append(msg).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 格式化所有任务结果
     */
    private String formatAllResults(List<TaskOutput> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ✅ Execution Results\n\n");
        for (int i = 0; i < results.size(); i++) {
            TaskOutput output = results.get(i);
            String statusIcon = output.isSuccess() ? "✅" : "❌";
            sb.append("**Step ").append(i + 1).append("** ").append(statusIcon).append(": ");
            sb.append(output.getSummary()).append("\n\n");
        }
        return sb.toString();
    }

    // ==================== 辅助方法 ====================

    /**
     * 根据 Agent 名称获取对应的 Skill ID
     */
    private String getSkillIdForAgent(String agentName) {
        switch (agentName.toLowerCase()) {
            case "coder": return "implement-feature";
            case "tester": return "write-tests";
            case "reviewer": return "code-review";
            case "debug": return "diagnose-bug";
            case "explorer": return "explore-codebase";
            case "architect": return "design-architecture";
            case "documenter": return "write-docs";
            default: return "implement-feature";
        }
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() <= maxLen ? str : str.substring(0, maxLen) + "...";
    }

    /**
     * 保存检查点
     *
     * <p>使用 Jackson ObjectMapper 安全构建 JSON，消除手动字符串拼接的注入风险。</p>
     */
    private void saveCheckpoint() {
        if (currentTaskId == null) return;

        try {
            // 使用 ObjectMapper 安全构建 contextJson
            ObjectNode contextNode = OBJECT_MAPPER.createObjectNode();
            contextNode.put("goal", currentTaskGoal != null ? currentTaskGoal : "");
            contextNode.put("startTime", taskStartTime != null ? taskStartTime.toString() : "");

            // 使用 ObjectMapper 安全构建 resultsJson
            ObjectNode resultsNode = OBJECT_MAPPER.createObjectNode();
            resultsNode.put("completedSubTasks", completedSubTasks.size());
            resultsNode.put("totalChanges", changes.size());

            Checkpoint checkpoint = Checkpoint.builder()
                .taskId(currentTaskId)
                .contextJson(OBJECT_MAPPER.writeValueAsString(contextNode))
                .resultsJson(OBJECT_MAPPER.writeValueAsString(resultsNode))
                .busJson(contextBus.exportToJson())
                .timelineJson("[]")
                .build();

            checkpointManager.saveCheckpoint(checkpoint);
        } catch (Exception e) {
            logger.warning("Failed to save checkpoint: " + e.getMessage());
        }
    }

    /**
     * 恢复任务
     */
    private String resumeTask() {
        if (currentTaskId == null) {
            // 查找最近的检查点
            List<String> checkpoints = checkpointManager.listCheckpoints();
            if (checkpoints.isEmpty()) {
                return "❌ 没有找到可恢复的任务。";
            }
            currentTaskId = checkpoints.get(checkpoints.size() - 1);
        }

        Checkpoint checkpoint = checkpointManager.loadCheckpoint(currentTaskId);
        if (checkpoint == null) {
            return "❌ 检查点加载失败: " + currentTaskId;
        }

        return "✅ 任务已恢复: `" + currentTaskId + "`\n" +
               "Goal: " + currentTaskGoal + "\n\n" +
               "继续执行中...";
    }

    /**
     * 取消当前任务
     */
    private void cancelCurrentTask() {
        if (currentTaskId != null) {
            checkpointManager.deleteCheckpoint(currentTaskId);
        }
        currentTaskId = null;
        currentTaskGoal = null;
        contextBus.clear();
    }

    /**
     * 获取当前状态
     */
    private String getCurrentStatus() {
        if (currentTaskId == null) {
            return "📋 当前没有正在进行的任务。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 **Current Task Status**\n\n");
        sb.append("| Field | Value |\n");
        sb.append("|-------|-------|\n");
        sb.append("| Task ID | `").append(currentTaskId).append("` |\n");
        sb.append("| Goal | ").append(currentTaskGoal).append(" |\n");
        sb.append("| Started | ").append(taskStartTime).append(" |\n");
        sb.append("| Duration | ").append(
            Duration.between(taskStartTime, LocalDateTime.now()).toMinutes()).append(" min |\n");
        sb.append("| Completed Steps | ").append(completedSubTasks.size()).append(" |\n");
        sb.append("| Changes | +").append(
            changes.stream().mapToInt(FileChange::getLinesAdded).sum()).append("/-").append(
            changes.stream().mapToInt(FileChange::getLinesDeleted).sum()).append(" lines |\n");

        return sb.toString();
    }

    /**
     * 生成报告
     *
     * <p>修复 Builder 模式误用问题：先一次性收集所有子任务/变更/测试结果，
     * 再构建最终的 ExecutionReport，避免每次循环新建 builder 导致数据丢失。</p>
     */
    private String generateReport() {
        if (currentTaskId == null) {
            return "❌ 没有任务可以生成报告。";
        }

        // 先收集所有子任务/变更/测试结果
        ExecutionReport.Builder builder = ExecutionReport.builder()
            .taskId(currentTaskId)
            .taskGoal(currentTaskGoal)
            .status(completedSubTasks.isEmpty() ? Status.FAILED : Status.SUCCESS)
            .startTime(taskStartTime)
            .endTime(LocalDateTime.now())
            .duration(Duration.between(taskStartTime, LocalDateTime.now()));

        // 添加子任务结果（一次性收集，避免 builder 循环覆盖）
        List<SubTaskResult> allSubTasks = new ArrayList<>(completedSubTasks);
        for (SubTaskResult st : allSubTasks) {
            builder.addSubTask(st);
        }

        // 添加变更
        List<FileChange> allChanges = new ArrayList<>(changes);
        for (FileChange fc : allChanges) {
            builder.addChange(fc);
        }

        // 添加测试结果
        List<TestResult> allTestResults = new ArrayList<>(testResults);
        for (TestResult tr : allTestResults) {
            builder.addTestResult(tr);
        }

        ExecutionReport report = builder.build();
        return report.toMarkdown();
    }

    /**
     * 根据任务类型获取合适的 Agent
     */
    private String getAgentForTaskType(TaskType taskType) {
        switch (taskType) {
            case FEATURE: return "Coder";
            case BUGFIX: return "Debug";
            case REFACTOR: return "Coder";
            case TEST: return "Tester";
            case DOC: return "Documenter";
            case ANALYZE: return "Explorer";
            case DEBUG: return "Debug";
            case REVIEW: return "Reviewer";
            default: return "Default";
        }
    }

    /**
     * 获取完整的 System Prompt
     */
    public String getSystemPrompt() {
        // 优先从 classpath 资源加载
        if (ResourcePromptLoader.promptResourcesExist()) {
            return ResourcePromptLoader.loadFullPrompt();
        }

        // 回退到内置默认值
        return buildDefaultSystemPrompt();
    }

    /**
     * 获取特定角色的提示词
     */
    public String getRolePrompt(String roleName) {
        String prompt = ResourcePromptLoader.loadRolePrompt(roleName);
        if (prompt != null) return prompt;
        return "You are a " + roleName + " agent in the JWCode multi-agent system.";
    }

    /**
     * 内置默认 System Prompt
     */
    private String buildDefaultSystemPrompt() {
        return """
            # JWCode System Prompt

            You are JWCode Orchestrator, an expert software engineering AI.
            You NEVER execute work directly. You analyze, decompose, delegate, verify, and report.

            ## Available Agents
            - Coder: Code writing and refactoring
            - Tester: Test design and execution
            - Reviewer: Code review (read-only)
            - Debug: Error diagnosis
            - Documenter: Documentation writing
            - Explorer: Codebase analysis (read-only)
            - Architect: Architecture design

            ## Workflow
            1. Analyze user intent
            2. Decompose into sub-tasks
            3. Dispatch to appropriate agents
            4. Verify results
            5. Generate report
            """;
    }

    // ==================== Setters for PlanTaskBroadcaster & SessionId ====================

    /**
     * 设置 PlanTaskBroadcaster（用于向前端广播任务状态）
     */
    public void setPlanTaskBroadcaster(PlanTaskBroadcaster broadcaster) {
        this.planTaskBroadcaster = broadcaster;
    }

    /**
     * 设置当前会话 ID（用于 WebSocket 消息路由）
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 设置工作目录根路径 — 初始化 MemoryAgent，并配置 SemanticIntentAnalyzer。
     * MemoryAgent 会在 `.jwcode/memory/` 下自动维护项目记忆。
     *
     * @param workspaceRoot 工作目录根路径（如 /home/user/project）
     */
    public void setWorkspaceRoot(java.nio.file.Path workspaceRoot) {
        if (workspaceRoot != null && java.nio.file.Files.exists(workspaceRoot)) {
            this.memoryAgent = new MemoryAgent(workspaceRoot);
            logger.info("[Orchestrator] MemoryAgent 初始化: " + workspaceRoot);

            // 将项目上下文注入 SemanticIntentAnalyzer，提升意图分类准确率
            if (memoryAgent.isEnabled()) {
                String projectCtx = memoryAgent.getPlanContextPrompt();
                if (projectCtx != null && !projectCtx.isEmpty()) {
                    intentAnalyzer.setProjectContext(projectCtx);
                }
                // 从工作目录提取已知模块名
                List<String> modules = detectProjectModules(workspaceRoot);
                if (!modules.isEmpty()) {
                    intentAnalyzer.setKnownModules(modules);
                }
            }
        } else {
            logger.warning("[Orchestrator] 工作目录无效，MemoryAgent 未初始化: " + workspaceRoot);
        }
    }

    /**
     * 探测项目子模块名（如 core、web、cli 等）
     */
    private List<String> detectProjectModules(java.nio.file.Path workspaceRoot) {
        List<String> modules = new ArrayList<>();
        try {
            java.io.File root = workspaceRoot.toFile();
            java.io.File[] children = root.listFiles(java.io.File::isDirectory);
            if (children != null) {
                for (java.io.File child : children) {
                    String name = child.getName();
                    // 过滤掉非项目目录
                    if (!name.startsWith(".") && !name.equals("target")
                        && !name.equals("node_modules") && !name.equals("logs")
                        && !name.equals("docs")) {
                        // 检查是否是 Maven 子模块（包含 pom.xml）
                        if (new java.io.File(child, "pom.xml").exists()
                            || new java.io.File(child, "package.json").exists()) {
                            modules.add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return modules;
    }

    /**
     * 获取 MemoryAgent（可能为 null）
     */
    public MemoryAgent getMemoryAgent() {
        return memoryAgent;
    }

    /**
     * 获取当前会话 ID
     */
    public String getSessionId() {
        return sessionId;
    }

    // ==================== 结构化任务集成方法 ====================

    /**
     * 设置当前的 TaskExecutionAgent（在 A2A 初始化后调用）
     */
    public void initTaskExecutionAgent() {
        if (a2aFacade != null) {
            this.taskExecutionAgent = new TaskExecutionAgent(
                a2aFacade, planTaskBroadcaster, sessionId, memoryAgent);
            logger.info("TaskExecutionAgent initialized"
                + (memoryAgent != null ? " (with MemoryAgent)" : ""));
        }
    }

    /**
     * 使用结构化任务流程执行 AI 的 plan 回复。
     *
     * <p>这是一个完整的 plan→结构化→执行→广播 流程：
     * <ol>
     *   <li>使用 TaskAgent 将 AI 回复解析为 StructuredTask 列表</li>
     *   <li>通过 PlanTaskBroadcaster 将结构化任务树广播到前端</li>
     *   <li>使用 TaskExecutionAgent 按步骤执行任务（自动处理并发/串行）</li>
     *   <li>广播执行进度和结果到前端</li>
     * </ol>
     * </p>
     *
     * @param aiPlanResponse AI 返回的 plan 文本回复
     * @param goal           任务总体目标
     * @return 执行结果字符串
     */
    public String executeWithStructuredPlan(String aiPlanResponse, String goal) {
        // 重置上下文
        this.currentTaskId = "plan-" + UUID.randomUUID().toString().substring(0, 8);
        this.currentTaskGoal = goal;
        this.taskStartTime = LocalDateTime.now();
        this.currentStructuredTasks = new ArrayList<>();

        StringBuilder sb = new StringBuilder();

        // Step 0: 注入 MemoryAgent 记忆上下文
        String memoryContext = "";
        if (memoryAgent != null && memoryAgent.isEnabled()) {
            memoryContext = memoryAgent.getPlanContextPrompt();
            if (!memoryContext.isEmpty()) {
                sb.append("## 🧠 项目记忆上下文\n\n");
                sb.append("> MemoryAgent 提供的项目记忆已自动注入到任务分析中。\n\n");
            }
        }

        sb.append("## 📋 结构化任务分解\n\n");

        // Step 1: TaskAgent 解析 AI 回复 → 结构化任务列表
        logger.info("[Orchestrator] Parsing AI plan response into structured tasks...");

        List<StructuredTask> structuredTasks;
        try {
            structuredTasks = taskAgent.parsePlan(aiPlanResponse, currentTaskId);
            if (structuredTasks.isEmpty()) {
                // 回退到快速解析模式
                structuredTasks = taskAgent.parseQuickPlan(aiPlanResponse, currentTaskId);
            }
        } catch (Exception e) {
            logger.warning("TaskAgent parse failed: " + e.getMessage() + ", using quick parse");
            structuredTasks = taskAgent.parseQuickPlan(aiPlanResponse, currentTaskId);
        }

        this.currentStructuredTasks = structuredTasks;

        // 输出结构化任务概览
        sb.append(formatStructuredTaskSummary(structuredTasks, 0));
        sb.append("\n");

        // Step 2: 广播结构化任务树到前端
        broadcastStructuredTasks(structuredTasks);

        // Step 3: 初始化 TaskExecutionAgent
        initTaskExecutionAgent();

        // Step 4: 使用 TaskExecutionAgent 逐步执行
        if (taskExecutionAgent != null) {
            sb.append("### 🚀 开始执行任务\n\n");

            TaskExecutionAgent.ExecutionResult result =
                taskExecutionAgent.execute(structuredTasks, goal);

            sb.append(formatExecutionResult(result));
        } else {
            sb.append("⚠️ TaskExecutionAgent 未初始化，无法执行任务。\n");
            sb.append("请确保 A2AFacade 已正确配置。\n");
        }

        return sb.toString();
    }

    /**
     * 获取当前结构化任务列表（供外部查询）
     */
    public List<StructuredTask> getCurrentStructuredTasks() {
        return currentStructuredTasks;
    }

    /**
     * 格式化结构化任务摘要
     */
    private String formatStructuredTaskSummary(List<StructuredTask> tasks, int depth) {
        StringBuilder sb = new StringBuilder();
        String indent = "  ".repeat(depth);

        for (int i = 0; i < tasks.size(); i++) {
            StructuredTask task = tasks.get(i);
            String modeLabel = task.getExecutionMode() == StructuredTask.ExecutionMode.CONCURRENT
                ? "⚡并发" : "➡️串行";
            String phaseLabel = getPhaseLabel(task.getPhase());

            sb.append(indent);
            if (task.getChildren() != null && !task.getChildren().isEmpty()) {
                // 阶段包装任务
                sb.append(String.format("**%s %s** [%s] (%d个子任务)\n",
                    phaseLabel, task.getTitle(), modeLabel, task.getChildren().size()));
                sb.append(formatStructuredTaskSummary(task.getChildren(), depth + 1));
            } else {
                // 叶子任务
                String agentIcon = getAgentIcon(task.getAgentType());
                sb.append(String.format("%d. %s **%s** `[%s]` %s\n",
                    task.getStepNumber(), agentIcon, task.getTitle(),
                    task.getAgentType(), modeLabel));
                if (task.getDescription() != null && !task.getDescription().isEmpty()
                    && !task.getDescription().equals(task.getTitle())) {
                    sb.append(indent).append("   → ").append(task.getDescription()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * 格式化执行结果
     */
    private String formatExecutionResult(TaskExecutionAgent.ExecutionResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n---\n");
        sb.append("### 📊 执行总结\n\n");
        sb.append("| 指标 | 值 |\n");
        sb.append("|------|------|\n");
        sb.append("| ✅ 成功 | ").append(result.successCount).append(" |\n");
        sb.append("| ❌ 失败 | ").append(result.failedCount).append(" |\n");
        sb.append("| ⏭️ 跳过 | ").append(result.skippedCount).append(" |\n");
        sb.append("| ⏱️ 耗时 | ").append(result.duration != null
            ? result.duration.toMillis() + "ms" : "N/A").append(" |\n");
        sb.append("\n");

        if (!result.errors.isEmpty()) {
            sb.append("**错误详情：**\n");
            for (String error : result.errors) {
                sb.append("- ").append(error).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 广播结构化任务到前端
     */
    private void broadcastStructuredTasks(List<StructuredTask> tasks) {
        if (planTaskBroadcaster == null || sessionId == null) return;

        try {
            // 将 StructuredTask 转换为 PlanTask 树（向前兼容）
            List<PlanTask> planTasks = new ArrayList<>();
            for (StructuredTask st : tasks) {
                planTasks.add(st.toPlanTask());
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            String tasksJson = mapper.writeValueAsString(planTasks);
            planTaskBroadcaster.broadcastPlanTasks(sessionId, tasksJson);

            // 同时广播结构化任务元数据（含执行模式信息）
            broadcastStructuredTaskMeta(tasks);

            logger.info("[Orchestrator] Broadcasted " + countAllStructuredTasks(tasks)
                + " structured tasks to session " + sessionId);
        } catch (Exception e) {
            logger.warning("Failed to broadcast structured tasks: " + e.getMessage());
        }
    }

    /**
     * 广播结构化任务元数据（执行模式、阶段、依赖关系）
     */
    private void broadcastStructuredTaskMeta(List<StructuredTask> tasks) {
        if (planTaskBroadcaster == null || sessionId == null || tasks.isEmpty()) return;

        try {
            List<Map<String, Object>> metaList = new ArrayList<>();
            for (StructuredTask task : tasks) {
                metaList.add(buildTaskMeta(task));
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            String metaJson = mapper.writeValueAsString(Map.of("structuredTasks", metaList));

            // 使用 broadcastPlanData 直接发送原始结构化数据（不额外嵌套 tasks 层）
            planTaskBroadcaster.broadcastPlanData(sessionId, metaJson);
        } catch (Exception e) {
            logger.warning("Failed to broadcast structured task meta: " + e.getMessage());
        }
    }

    /**
     * 递归构建任务元数据
     */
    private Map<String, Object> buildTaskMeta(StructuredTask task) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", task.getId());
        meta.put("title", task.getTitle());
        meta.put("description", task.getDescription());
        meta.put("executionMode", task.getExecutionMode().name());
        meta.put("phase", task.getPhase().name());
        meta.put("stepNumber", task.getStepNumber());
        meta.put("parallelGroup", task.getParallelGroup());
        meta.put("agentType", task.getAgentType());
        meta.put("status", task.getStatus());
        meta.put("dependencies", task.getDependencies());
        meta.put("progress", task.getProgress());

        // 包含任务上下文
        if (task.getContext() != null && !task.getContext().isEmpty()) {
            meta.put("context", task.getContext());
        }

        if (task.getChildren() != null && !task.getChildren().isEmpty()) {
            List<Map<String, Object>> childMetas = new ArrayList<>();
            for (StructuredTask child : task.getChildren()) {
                childMetas.add(buildTaskMeta(child));
            }
            meta.put("children", childMetas);
        }

        return meta;
    }

    /**
     * 统计所有结构化任务数
     */
    private int countAllStructuredTasks(List<StructuredTask> tasks) {
        int count = 0;
        for (StructuredTask t : tasks) {
            count++;
            if (t.getChildren() != null) {
                count += countAllStructuredTasks(t.getChildren());
            }
        }
        return count;
    }

    // ==================== 辅助显示方法 ====================

    private String getPhaseLabel(StructuredTask.TaskPhase phase) {
        switch (phase) {
            case EXPLORATION: return "🔍";
            case DESIGN: return "🏗️";
            case IMPLEMENTATION: return "💻";
            case TESTING: return "🧪";
            case REVIEW: return "👁️";
            case DOCUMENTATION: return "📝";
            default: return "📌";
        }
    }

    private String getAgentIcon(String agentType) {
        if (agentType == null) return "⚙️";
        switch (agentType.toLowerCase()) {
            case "coder": return "💻";
            case "debug": return "🐛";
            case "explore": return "🔍";
            case "architect": return "🏗️";
            case "test": return "🧪";
            case "reviewer": return "👁️";
            case "doc": return "📝";
            default: return "⚙️";
        }
    }

    /**
     * 构建 PlanTask 树形结构，用于发送到前端
     */
    public List<PlanTask> buildPlanTaskTree() {
        List<PlanTask> tasks = new ArrayList<>();

        // 主任务
        PlanTask mainTask = PlanTask.builder()
                .id(currentTaskId != null ? currentTaskId : "task-" + System.currentTimeMillis())
                .title(currentTaskGoal != null ? currentTaskGoal : "未命名任务")
                .description(currentTaskGoal != null ? currentTaskGoal : "")
                .status("pending")
                .agentType("orchestrator")
                .build();

        // 从已完成的子任务构建子任务列表
        List<PlanTask> subTaskList = new ArrayList<>();
        for (SubTaskResult st : completedSubTasks) {
            String agentType = st.getTaskType().toLowerCase();
            PlanTask subTask = PlanTask.builder()
                    .id(st.getTaskId())
                    .title(st.getTaskType() + ": " + truncate(st.getDescription(), 60))
                    .description(st.getDescription())
                    .status(st.getStatus() == Status.SUCCESS ? "completed" : "failed")
                    .agentType(agentType)
                    .result(st.getStatus() == Status.SUCCESS ? st.getOutput() : null)
                    .error(st.getStatus() != Status.SUCCESS ? st.getOutput() : null)
                    .startedAt(System.currentTimeMillis() - st.getDuration().toMillis())
                    .completedAt(System.currentTimeMillis())
                    .progress(100)
                    .build();
            subTaskList.add(subTask);
        }

        // 如果没有已完成的子任务，添加默认子任务占位
        if (subTaskList.isEmpty()) {
            // 根据任务类型添加默认子任务
            String[] defaultAgents = {"explorer", "architect", "coder", "tester", "reviewer"};
            for (String agent : defaultAgents) {
                PlanTask subTask = PlanTask.builder()
                        .id(agent + "-" + System.currentTimeMillis())
                        .title(getAgentDisplayName(agent) + ": 待执行")
                        .description("等待执行")
                        .status("pending")
                        .agentType(agent)
                        .build();
                subTaskList.add(subTask);
            }
        }

        mainTask.setChildren(subTaskList);
        tasks.add(mainTask);

        return tasks;
    }

    /**
     * 获取 Agent 显示名称
     */
    private String getAgentDisplayName(String agentType) {
        switch (agentType.toLowerCase()) {
            case "explorer": return "调研分析";
            case "architect": return "方案设计";
            case "coder": return "编码实现";
            case "tester": return "测试验证";
            case "reviewer": return "代码审查";
            case "debug": return "调试诊断";
            case "documenter": return "文档编写";
            default: return agentType;
        }
    }

    // ==================== Getters ====================

    public String getCurrentTaskId() { return currentTaskId; }
    public String getCurrentTaskGoal() { return currentTaskGoal; }
    public SharedContextBus getContextBus() { return contextBus; }
    public CheckpointManager getCheckpointManager() { return checkpointManager; }
    public SemanticIntentAnalyzer getIntentAnalyzer() { return intentAnalyzer; }
}

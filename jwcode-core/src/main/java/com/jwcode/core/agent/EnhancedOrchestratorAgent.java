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
import com.jwcode.core.planner.IntentAnalyzer;
import com.jwcode.core.planner.IntentAnalyzer.AnalysisResult;
import com.jwcode.core.planner.IntentAnalyzer.TaskType;
import com.jwcode.core.planner.IntentAnalyzer.Complexity;
import com.jwcode.core.planner.checkpoint.CheckpointManager;
import com.jwcode.core.planner.checkpoint.CheckpointManager.Checkpoint;
import com.jwcode.core.planner.checkpoint.SharedContextBus;
import com.jwcode.core.report.ExecutionReport;
import com.jwcode.core.report.ExecutionReport.*;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;

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
 * 1. IntentAnalyzer 分析用户输入
 * 2. 如果是中断 → 保存 Checkpoint → 处理新任务
 * 3. 如果是闲聊 → 直接回复
 * 4. 如果是任务 → 拆解 → 创建 A2ATask → 通过 A2AFacade 调度子Agent → 验证 → 生成报告
 * </pre>
 */
public class EnhancedOrchestratorAgent {

    private static final Logger logger = Logger.getLogger(EnhancedOrchestratorAgent.class.getName());

    private final IntentAnalyzer intentAnalyzer;
    private final CheckpointManager checkpointManager;
    private final SharedContextBus contextBus;

    // A2A 协议调度门面
    private A2AFacade a2aFacade;

    // 【新增】LLM 执行引擎依赖（传递给子Agent）
    private LLMService llmService;
    private ToolRegistry toolRegistry;
    private ToolExecutor toolExecutor;

    // Plan 模式广播器（用于向前端推送任务状态）
    private PlanTaskBroadcaster planTaskBroadcaster;

    // 当前会话 ID（用于 WebSocket 消息路由）
    private String sessionId;

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

    /**
     * 默认构造器（无 LLMService — 子Agent将回退到模拟执行）
     */
    public EnhancedOrchestratorAgent() {
        this(null, null, null);
    }

    /**
     * 【新增】完整构造器，支持传入 LLMService 和 ToolRegistry
     * 这些依赖将传递给 A2AFacade → LocalAgentDispatcher，使子Agent真正通过LLM执行任务
     */
    public EnhancedOrchestratorAgent(LLMService llmService,
                                     ToolRegistry toolRegistry,
                                     ToolExecutor toolExecutor) {
        this.intentAnalyzer = new IntentAnalyzer();
        this.checkpointManager = new CheckpointManager();
        this.contextBus = new SharedContextBus();
        this.completedSubTasks = new ArrayList<>();
        this.changes = new ArrayList<>();
        this.testResults = new ArrayList<>();
        this.reviewFindings = new ArrayList<>();
        this.timeline = new ArrayList<>();
        this.recommendations = new ArrayList<>();
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        initA2A();
    }

    /**
     * 初始化 A2A 协议支持
     * 【修复】将 LLMService、ToolRegistry、ToolExecutor 传递给 A2AFacade
     */
    private void initA2A() {
        try {
            AgentRegistry registry = AgentRegistry.createDefault();
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
     */
    private void saveCheckpoint() {
        if (currentTaskId == null) return;

        Checkpoint checkpoint = Checkpoint.builder()
            .taskId(currentTaskId)
            .contextJson("{\"goal\":\"" + currentTaskGoal + "\",\"startTime\":\"" + taskStartTime + "\"}")
            .resultsJson("{\"completedSubTasks\":" + completedSubTasks.size() + "}")
            .busJson(contextBus.exportToJson())
            .timelineJson("[]")
            .build();

        checkpointManager.saveCheckpoint(checkpoint);
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
     */
    private String generateReport() {
        if (currentTaskId == null) {
            return "❌ 没有任务可以生成报告。";
        }

        ExecutionReport report = ExecutionReport.builder()
            .taskId(currentTaskId)
            .taskGoal(currentTaskGoal)
            .status(completedSubTasks.isEmpty() ? Status.FAILED : Status.SUCCESS)
            .startTime(taskStartTime)
            .endTime(LocalDateTime.now())
            .duration(Duration.between(taskStartTime, LocalDateTime.now()))
            .build();

        // 添加子任务结果
        for (SubTaskResult st : completedSubTasks) {
            report = ExecutionReport.builder()
                .taskId(report.getTaskId())
                .taskGoal(report.getTaskGoal())
                .status(report.getStatus())
                .startTime(report.getStartTime())
                .endTime(report.getEndTime())
                .duration(report.getDuration())
                .addSubTask(st)
                .build();
        }

        // 添加变更
        for (FileChange fc : changes) {
            report = ExecutionReport.builder()
                .taskId(report.getTaskId())
                .taskGoal(report.getTaskGoal())
                .status(report.getStatus())
                .startTime(report.getStartTime())
                .endTime(report.getEndTime())
                .duration(report.getDuration())
                .addChange(fc)
                .build();
        }

        // 添加测试结果
        for (TestResult tr : testResults) {
            report = ExecutionReport.builder()
                .taskId(report.getTaskId())
                .taskGoal(report.getTaskGoal())
                .status(report.getStatus())
                .startTime(report.getStartTime())
                .endTime(report.getEndTime())
                .duration(report.getDuration())
                .addTestResult(tr)
                .build();
        }

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
     * 获取当前会话 ID
     */
    public String getSessionId() {
        return sessionId;
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
    public IntentAnalyzer getIntentAnalyzer() { return intentAnalyzer; }
}

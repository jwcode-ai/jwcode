package com.jwcode.core.eval;

import com.jwcode.core.eval.EvalTask.*;
import com.jwcode.core.eval.EvalTask.CheckResult;
import com.jwcode.core.eval.EvalTask.EvalResult;
import com.jwcode.core.eval.EvalTask.EvalReport;
import com.jwcode.core.eval.EvalTask.Summary;
import com.jwcode.core.eval.EvalTask.CategoryResult;
import com.jwcode.core.eval.EvalTask.Difficulty;
import com.jwcode.core.eval.EvalTask.ToolInvocation;
import com.jwcode.core.llm.LLMFactory;
import com.jwcode.core.llm.LLMQueryEngine;
import com.jwcode.core.llm.LLMQueryEngine.QueryResult;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.model.Message;
import com.jwcode.core.session.Session;
import com.jwcode.core.session.SessionManager;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CapabilityEvalSuite — JWCode 能力评测套件。
 *
 * <p>系统化评测 JWCode 系统在不同难度级别下的能力表现。
 * 评测流程：</p>
 *
 * <pre>
 * 1. 从 YAML 文件加载评测任务
 * 2. 按难度级别依次执行（SIMPLE → MEDIUM → COMPLEX）
 * 3. 每个任务：
 *    a. 客观验收检查（文件存在、内容匹配、编译检查等）
 *    b. （可选）AI 评审（通过 EvaluatorAgent 评分）
 *    c. 记录执行轨迹
 * 4. 生成综合报告（Markdown + JSON）
 * 5. 保存到 eval-results/ 目录
 * </pre>
 *
 * <p>运行方式：</p>
 * <pre>
 * mvn test -Dtest=CapabilityEvalSuite -pl jwcode-core -am
 * mvn test -Peval  # 通过 Maven profile
 * </pre>
 */
@DisplayName("🧪 JWCode 能力评测套件")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CapabilityEvalSuite {

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static EvalReport report;
    private static List<EvalTask> allTasks;
    private static EvalTaskLoader loader;
    private static AcceptanceChecker checker;
    private static EvalReportGenerator reportGenerator;
    private static Path workspaceRoot;
    private static long suiteStartTime;

    // LLM 集成组件（eval-full 模式下使用）
    private static boolean isFullMode;
    private static boolean skipComplex;
    private static LLMQueryEngine queryEngine;
    private static Session session;
    private static SessionManager sessionManager;
    private static LLMFactory llmFactory;
    private static LLMService llmService;

    @BeforeAll
    static void setUp() {
        workspaceRoot = Paths.get(System.getProperty("user.dir"));
        // 如果在 jwcode-core 目录下运行，需要回到项目根
        if (workspaceRoot.endsWith("jwcode-core")) {
            workspaceRoot = workspaceRoot.getParent();
        }

        loader = new EvalTaskLoader();
        checker = new AcceptanceChecker(workspaceRoot);
        reportGenerator = new EvalReportGenerator();
        report = new EvalReport();
        report.setSuiteName("JWCode Capability Eval");
        report.setTimestamp(LocalDateTime.now().format(TIMESTAMP_FMT));

        // 检测是否为完整模式（真实 LLM 调用）
        isFullMode = "true".equals(System.getProperty("eval.full.mode"));
        skipComplex = "true".equals(System.getProperty("eval.skip.complex"));

        // 从 classpath 加载评测任务
        allTasks = new ArrayList<>();
        allTasks.addAll(loader.loadFromClasspath("eval-tasks/simple-tasks.yaml"));
        allTasks.addAll(loader.loadFromClasspath("eval-tasks/medium-tasks.yaml"));
        allTasks.addAll(loader.loadFromClasspath("eval-tasks/complex-tasks.yaml"));

        System.out.println("\n╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║           JWCode 能力评测套件启动                                   ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        System.out.println("║ 工作目录: " + padRight(workspaceRoot.toString(), 48) + "║");
        System.out.println("║ 加载任务: " + padRight(allTasks.size() + " 个", 48) + "║");
        System.out.println("║ 运行模式: " + padRight(isFullMode ? "🔴 完整模式 (真实LLM)" : "🟢 模拟模式 (仅验收)", 48) + "║");
        if (skipComplex) {
            System.out.println("║ 快速模式: " + padRight("跳过复杂任务", 48) + "║");
        }
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝\n");

        assertTrue(allTasks.size() > 0, "应该至少加载一个评测任务");

        // 完整模式下初始化 LLM 组件
        if (isFullMode) {
            initLLMComponents();
        }

        suiteStartTime = System.currentTimeMillis();
    }

    /**
     * 初始化 LLM 组件（完整模式）。
     * 创建 Session、LLMFactory、LLMService、LLMQueryEngine。
     */
    private static void initLLMComponents() {
        try {
            System.out.println("  🔧 初始化 LLM 组件...");

            // 1. 创建 Session
            sessionManager = SessionManager.getInstance();
            session = sessionManager.createSession(workspaceRoot.toString());
            System.out.println("  ✅ Session 已创建: " + session.getId());

            // 2. 创建 LLMFactory（从配置文件加载）
            llmFactory = new LLMFactory();
            llmService = llmFactory.getLLMService();
            System.out.println("  ✅ LLMService 已创建: " + llmService.getModelName());

            // 3. 创建 ToolRegistry 和 ToolExecutor
            ToolRegistry toolRegistry = ToolRegistry.createDefault();
            ToolExecutor toolExecutor = new ToolExecutor(toolRegistry);

            // 4. 创建 LLMQueryEngine
            LLMQueryEngine.EngineConfig engineConfig = LLMQueryEngine.EngineConfig.defaultConfig();
            engineConfig.setMaxIterations(0); // 不限制迭代
            engineConfig.setLayeredMode(true); // 启用分层多Agent架构

            queryEngine = LLMQueryEngine.builder()
                .session(session)
                .llmService(llmService)
                .toolExecutor(toolExecutor)
                .config(engineConfig)
                .build();

            System.out.println("  ✅ LLMQueryEngine 已就绪");
            System.out.println();
        } catch (Exception e) {
            System.err.println("  ❌ LLM 组件初始化失败: " + e.getMessage());
            e.printStackTrace();
            isFullMode = false; // 初始化失败时回退到模拟模式
        }
    }

    @AfterAll
    static void tearDown() {
        // 清理评测产生的临时文件
        cleanupEvalFiles();

        report.setTotalDurationMs(System.currentTimeMillis() - suiteStartTime);

        // 计算汇总
        computeSummary();

        // 保存报告
        String outputDir = System.getProperty("eval.output.dir",
            workspaceRoot.resolve("eval-results").toString());
        reportGenerator.saveToFiles(report, outputDir, "capability-eval");

        // 输出到控制台
        System.out.println(reportGenerator.generateConsoleSummary(report));

        // JUnit 断言：不同难度有不同的通过率要求
        // 模拟模式：跳过写操作任务，只验收读操作任务，阈值设高
        // 完整模式：真实 LLM 调用执行所有任务
        double simpleThreshold = isFullMode ? 50.0 : 85.0;
        double mediumThreshold = isFullMode ? 40.0 : 80.0;
        double complexThreshold = isFullMode ? 50.0 : 80.0;

        CategoryResult simpleResult = report.getByDifficulty().get(Difficulty.SIMPLE);
        if (simpleResult != null) {
            assertTrue(simpleResult.getPassRate() >= simpleThreshold,
                "简单任务通过率应 >= " + simpleThreshold + "%，当前: "
                    + String.format("%.1f%%", simpleResult.getPassRate()));
        }

        CategoryResult mediumResult = report.getByDifficulty().get(Difficulty.MEDIUM);
        if (mediumResult != null) {
            assertTrue(mediumResult.getPassRate() >= mediumThreshold,
                "中等任务通过率应 >= " + mediumThreshold + "%，当前: "
                    + String.format("%.1f%%", mediumResult.getPassRate()));
        }

        CategoryResult complexResult = report.getByDifficulty().get(Difficulty.COMPLEX);
        if (complexResult != null) {
            assertTrue(complexResult.getPassRate() >= complexThreshold,
                "复杂任务通过率应 >= " + complexThreshold + "%，当前: "
                    + String.format("%.1f%%", complexResult.getPassRate()));
        }
    }

    // ==================== 简单任务 ====================

    @Test
    @Order(1)
    @DisplayName("🟢 简单任务集")
    void testSimpleTasks() {
        List<EvalTask> simpleTasks = filterTasks(Difficulty.SIMPLE);
        executeTasks(simpleTasks, "简单");
    }

    // ==================== 中等任务 ====================

    @Test
    @Order(2)
    @DisplayName("🟡 中等任务集")
    void testMediumTasks() {
        List<EvalTask> mediumTasks = filterTasks(Difficulty.MEDIUM);
        executeTasks(mediumTasks, "中等");
    }

    // ==================== 复杂任务 ====================

    @Test
    @Order(3)
    @DisplayName("🔴 复杂任务集")
    void testComplexTasks() {
        List<EvalTask> complexTasks = filterTasks(Difficulty.COMPLEX);
        executeTasks(complexTasks, "复杂");
    }

    // ==================== 核心执行逻辑 ====================

    /**
     * 执行一组评测任务。
     * 注意：当前版本执行"模拟执行"模式 — 跳过真实的 LLM 调用，
     * 专注于验证评测框架本身的完整性（任务加载、验收检查、报告生成）。
     *
     * 未来版本将通过 LLMQueryEngine 或 A2AFacade 提交给真实的 Agent 执行。
     */
    private void executeTasks(List<EvalTask> tasks, String label) {
        if (tasks.isEmpty()) {
            System.out.println("  ⏭ 没有 " + label + " 任务需要执行");
            return;
        }

        System.out.println("  ── " + label + " 任务集 (" + tasks.size() + " 个, 并行执行) ──");

        // 使用线程池并行执行任务，提升评测速度
        int threadCount = Math.min(tasks.size(), 4);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<EvalResult> results = Collections.synchronizedList(new ArrayList<>());

        try {
            List<CompletableFuture<Void>> futures = tasks.stream()
                .map(task -> CompletableFuture.runAsync(() -> {
                    EvalResult result = executeSingleTask(task);
                    results.add(result);
                    String icon = result.isPassed() ? "✅" : "❌";
                    System.out.println("  " + icon + " " + task.getId() + " " +
                        padRight(task.getName(), 30) +
                        " (" + EvalReportGenerator.formatDuration(result.getDurationMs()) + ")" +
                        (result.getFailureReason() != null ? " - " + result.getFailureReason() : ""));
                }, executor))
                .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 按任务 ID 排序后添加到报告，保证输出顺序稳定
            results.sort(Comparator.comparing(EvalResult::getTaskId));
            for (EvalResult result : results) {
                report.addResult(result);
            }
        } finally {
            executor.shutdown();
        }
    }

    /**
     * 执行单个评测任务。
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li><b>完整模式</b>：通过 LLMQueryEngine 提交给 AI 真实执行，记录工具调用轨迹</li>
     *   <li><b>模拟模式</b>：跳过 LLM 调用，直接做验收检查</li>
     *   <li><b>验收检查</b>：无论哪种模式，都执行客观验收检查</li>
     *   <li><b>AI 评审</b>：复杂任务启用 AI 评分（通过 EvaluatorAgent）</li>
     * </ol>
     */
    private EvalResult executeSingleTask(EvalTask task) {
        EvalResult result = new EvalResult();
        result.setTaskId(task.getId());
        long taskStartTime = System.currentTimeMillis();

        // 创建执行轨迹
        ExecutionTrace trace = new ExecutionTrace();
        trace.setTaskId(task.getId());
        trace.setStartedAt(taskStartTime);

        try {
            // ============================================================
            // Step 0: 模拟模式下跳过写操作类任务
            // ============================================================
            if (!isFullMode && task.isSkipInMockMode()) {
                result.setPassed(false);
                result.setObjectiveScore(0.0);
                result.setFailureReason("⏭ 跳过（模拟模式不支持写操作，请使用 eval-full 模式）");
                trace.addError("模拟模式跳过 - 需完整模式执行");
                trace.setTotalDurationMs(System.currentTimeMillis() - taskStartTime);
                result.setTrace(trace);
                result.setDurationMs(System.currentTimeMillis() - taskStartTime);
                // 直接返回，不执行后续验收检查
                long taskEndTime = System.currentTimeMillis();
                trace.setCompletedAt(taskEndTime);
                return result;
            }

            // ============================================================
            // Step 1: 完整模式 — 通过 LLMQueryEngine 真实执行任务
            // ============================================================
            // 为每个任务创建独立的 session（超时/异常后仍需提取工具调用轨迹）
            Session taskSession = null;

            if (isFullMode && queryEngine != null) {
                try {
                    System.out.print("    🤖 " + task.getId() + " 提交给 AI 执行... ");
                    System.out.flush();

                    taskSession = sessionManager.createSession(workspaceRoot.toString());
                    var taskSessionFinal = taskSession;
                    taskSessionFinal.setWorkingDirectory(workspaceRoot.toString());

                    LLMQueryEngine taskEngine = LLMQueryEngine.builder()
                        .session(taskSession)
                        .llmService(llmService)
                        .toolExecutor(new ToolExecutor(ToolRegistry.createDefault()))
                        .config(queryEngine.getConfig())
                        .build();

                    // 【修复】在 prompt 中注入工作目录信息，让 AI 知道文件应写到哪里
                    String osName = System.getProperty("os.name", "Unknown");
                    String fullPrompt = task.getUserPrompt()
                        + "\n\n【工作目录】当前工作目录是: " + workspaceRoot.toString()
                        + "\n【操作系统】" + osName
                        + "\n重要规则："
                        + "\n1. 所有文件操作请使用相对于工作目录的路径（如 jwcode-core/src/main/java/...）"
                        + "\n2. 当前操作系统是 Windows，请使用 Windows 兼容命令（dir、type、mkdir 等），不要使用 ls、mkdir -p、grep 等 Unix 命令"
                        + "\n3. 每个模块（jwcode-core、jwcode-common 等）下都有 .jwcode 目录用于工具配置"
                        + "\n4. 工作目录下也有 .jwcode 目录"
                        + "\n5. 创建文件时，请使用 FileWriteTool 直接写入内容，不要使用 echo > 或 BashTool 重定向"
                        + "\n6. 如果需要在 eval/ 目录下创建文件，先确认该目录存在，不存在则先用 mkdir 创建";

                    // 提交任务给 AI 并等待完成（带超时）
                    CompletableFuture<QueryResult> future = taskEngine.query(fullPrompt);
                    QueryResult qr = future.get(task.getTimeoutSeconds(), TimeUnit.SECONDS);

                    System.out.println(qr.isSuccess() ? "✅" : "❌");

                    // 从 session 消息中提取工具调用轨迹（完整版）
                    List<Message> messages = taskSession.getMessages();
                    int toolCallCount = 0;
                    for (Message msg : messages) {
                        if (msg.hasToolCalls()) toolCallCount += msg.getToolCalls().size();
                    }
                    trace.setTotalSteps(toolCallCount);
                    trace.setTotalDurationMs(System.currentTimeMillis() - taskStartTime);

                    // 用 Map 暂存 tool_call_id → ToolInvocation，匹配 call 和 result
                    java.util.Map<String, ToolInvocation> pendingCalls = new java.util.HashMap<>();

                    for (Message msg : messages) {
                        // assistant 消息中的工具调用声明
                        if (msg.hasToolCalls()) {
                            for (Message.ToolCallInfo tci : msg.getToolCalls()) {
                                ToolInvocation inv = new ToolInvocation(
                                    tci.getName(),
                                    tci.getArguments(),  // 完整入参 JSON
                                    "",                    // output 稍后填充
                                    0,                     // duration 稍后填充
                                    true
                                );
                                inv.setToolCallId(tci.getId());
                                pendingCalls.put(tci.getId(), inv);
                                trace.addToolCall(inv);
                            }
                        }
                        // TOOL 消息中的工具执行结果
                        if (msg.getRole() == Message.Role.TOOL) {
                            String toolUseId = null;
                            String content = null;
                            // 从 ContentBlock 中提取 toolUseId 和 result
                            for (Message.ContentBlock block : msg.getContent()) {
                                if (block instanceof Message.ToolResultContent) {
                                    Message.ToolResultContent trc = (Message.ToolResultContent) block;
                                    toolUseId = trc.getToolUseId();
                                    Object resultObj = trc.getResult();
                                    content = resultObj != null ? resultObj.toString() : "";
                                }
                            }
                            if (content == null) content = msg.getTextContent();

                            if (toolUseId != null && pendingCalls.containsKey(toolUseId)) {
                                ToolInvocation inv = pendingCalls.get(toolUseId);
                                inv.setOutput(content != null ? content.substring(0, Math.min(500, content.length())) : "");
                                inv.setSuccess(content == null
                                    || (!content.contains("\"isError\":true")
                                        && !content.contains("error_code")
                                        && !content.matches(".*\"error\"\\s*:\\s*\"[^\"]+\".*")));
                            }

                            // 记录错误 — 精确匹配非 null 的 error 字段或 isError:true
                            if (content != null && (content.contains("\"isError\":true")
                                || content.contains("error_code")
                                || content.matches(".*\"error\"\\s*:\\s*\"[^\"]+\".*"))) {
                                trace.addError("工具结果异常: " + content.substring(0, Math.min(300, content.length())));
                            }
                        }
                    }

                    // 记录 AI 最终回复摘要
                    if (qr.getMessage() != null) {
                        String aiContent = qr.getMessage().getTextContent();
                        if (aiContent != null && aiContent.length() > 200) {
                            trace.addError("[AI回复摘要] " + aiContent.substring(0, 200) + "...");
                        }
                    }

                    // 清理 session
                    sessionManager.deleteSession(taskSession.getId());

                } catch (java.util.concurrent.TimeoutException e) {
                    System.out.print("⏰ (partial trace extracted) ");
                    // 超时时仍然提取已收集的工具调用轨迹
                    extractToolCallsFromSession(taskSession, trace);
                    trace.addError("AI 执行超时 (" + task.getTimeoutSeconds() + "s)");
                    // 注意: 不 setPassed(false) — 验收检查决定最终结果
                } catch (Exception e) {
                    System.out.print("❌ ");
                    extractToolCallsFromSession(taskSession, trace);
                    String errMsg = e.getMessage();
                    if (errMsg != null && errMsg.length() > 100) {
                        errMsg = errMsg.substring(0, 100);
                    }
                    trace.addError("AI 执行异常: " + errMsg);
                    result.setPassed(false);
                    result.setFailureReason("AI 执行异常: " + errMsg);
                }
            }

            // ============================================================
            // Step 1: 执行客观验收检查
            // ============================================================
            List<CheckResult> checkResults = checker.executeAll(task.getAcceptanceChecks());
            for (CheckResult cr : checkResults) {
                result.addCheckResult(cr);
            }

            double objectiveScore = AcceptanceChecker.calculateScore(checkResults);
            result.setObjectiveScore(objectiveScore);
            result.setPassed(AcceptanceChecker.allPassed(checkResults));

            if (!result.isPassed()) {
                // 找到第一个失败的检查作为失败原因
                String reason = checkResults.stream()
                    .filter(cr -> !cr.isPassed())
                    .findFirst()
                    .map(cr -> cr.getDescription() + ": " + cr.getDetail())
                    .orElse("验收检查未通过");
                result.setFailureReason(reason);
                trace.addError(reason);
            }

            // ============================================================
            // Step 2: AI 评审（仅完整模式 + 复杂任务）
            // ============================================================
            if (isFullMode && task.isAiEvalEnabled() && result.isPassed()) {
                try {
                    // 【修复】传入实际执行结果供 AI 评审
                    String executionSummary = buildExecutionSummary(task, result, trace);
                    String evalPrompt = buildEvalPrompt(task, executionSummary);
                    com.jwcode.core.llm.LLMMessage evalMsg =
                        com.jwcode.core.llm.LLMMessage.builder()
                            .role(com.jwcode.core.llm.LLMMessage.Role.USER)
                            .content(EVALUATOR_SYSTEM_PROMPT + "\n\n" + evalPrompt)
                            .build();
                    CompletableFuture<com.jwcode.core.llm.LLMResponse> evalFuture =
                        llmService.chat(java.util.List.of(evalMsg));
                    com.jwcode.core.llm.LLMResponse evalResp = evalFuture.get(120, TimeUnit.SECONDS);

                    if (evalResp != null && evalResp.getContent() != null) {
                        result.setAiScore(parseAiScore(evalResp.getContent()));
                        result.setAiFeedback(truncate(evalResp.getContent(), 500));
                    } else {
                        result.setAiScore(5.0);
                        result.setAiFeedback("AI 评审无返回");
                    }
                } catch (Exception e) {
                    result.setAiScore(null);
                    result.setAiFeedback("评审异常: " + e.getMessage());
                }
            }

            // ============================================================
            // Step 3: 计算综合得分
            // ============================================================
            if (result.getAiScore() != null) {
                // 综合 = 客观(70%) + AI(30%)
                result.setWeightedFinalScore(
                    (result.getObjectiveScore() / 100.0 * 7.0) +
                    (result.getAiScore() / 10.0 * 3.0)
                );
            } else {
                result.setWeightedFinalScore(result.getObjectiveScore() / 100.0 * 10.0);
            }

        } catch (Exception e) {
            result.setPassed(false);
            result.setFailureReason("执行异常: " + e.getMessage());
            trace.addError("异常: " + e.getMessage());
        }

        // 记录轨迹
        long taskEndTime = System.currentTimeMillis();
        trace.setCompletedAt(taskEndTime);
        trace.setTotalDurationMs(taskEndTime - taskStartTime);
        if (trace.getTotalSteps() == 0) {
            trace.setTotalSteps(result.getCheckResults().size());
        }
        result.setTrace(trace);
        result.setDurationMs(taskEndTime - taskStartTime);

        return result;
    }

    /**
     * 构建 AI 评审用的评估 Prompt。
     *
     * @param task              任务定义
     * @param executionSummary  实际执行结果摘要
     */
    private static String buildEvalPrompt(EvalTask task, String executionSummary) {
        return """
            请对以下任务的执行结果进行 4 维评分（0-10分）：

            任务: %s - %s
            难度: %s
            能力维度: %s

            == 实际执行结果 ==
            %s

            评分维度：
            1. 任务完成度 — 是否满足所有验收标准
            2. 执行效率 — 步骤数是否合理
            3. 输出质量 — 结果是否准确完整
            4. 错误处理 — 遇到错误是否能正确处理

            请输出 JSON 格式评分报告。
            """.formatted(task.getId(), task.getName(),
                task.getDifficulty(), task.getCapability(), executionSummary);
    }

    /**
     * 构建执行结果摘要，供 AI 评审使用。
     */
    private static String buildExecutionSummary(EvalTask task, EvalResult result, ExecutionTrace trace) {
        StringBuilder sb = new StringBuilder();
        sb.append("任务状态: ").append(result.isPassed() ? "✅ 通过" : "❌ 失败").append("\n");
        sb.append("客观得分: ").append(String.format("%.1f/100", result.getObjectiveScore())).append("\n");
        sb.append("总耗时: ").append(result.getDurationMs() / 1000.0).append("s\n");
        sb.append("工具调用数: ").append(trace.getToolCalls().size()).append("\n");
        sb.append("总步骤数: ").append(trace.getTotalSteps()).append("\n\n");

        // 验收检查结果
        sb.append("--- 验收检查 ---\n");
        for (CheckResult cr : result.getCheckResults()) {
            sb.append(cr.isPassed() ? "  ✅ " : "  ❌ ")
              .append(cr.getDescription()).append(": ").append(cr.getDetail()).append("\n");
        }
        sb.append("\n");

        // 工具调用摘要
        sb.append("--- 工具调用轨迹 ---\n");
        for (ToolInvocation inv : trace.getToolCalls()) {
            String icon = inv.isSuccess() ? "✅" : "❌";
            sb.append("  ").append(icon).append(" ").append(inv.getToolName());
            if (inv.getInput() != null && !inv.getInput().isEmpty()) {
                String inputSummary = inv.getInput().length() > 150
                    ? inv.getInput().substring(0, 150) + "..."
                    : inv.getInput();
                sb.append(" | 入参: ").append(inputSummary);
            }
            sb.append("\n");
        }
        sb.append("\n");

        // 错误信息
        if (!trace.getErrors().isEmpty()) {
            sb.append("--- 异常/错误 ---\n");
            for (String err : trace.getErrors()) {
                String errSummary = err.length() > 200 ? err.substring(0, 200) + "..." : err;
                sb.append("  ⚠ ").append(errSummary).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /** EvaluatorAgent 的 System Prompt（简化版，用于 AI 评审） */
    private static final String EVALUATOR_SYSTEM_PROMPT = """
        # Evaluator Agent — Quality Evaluation Expert

        You are a senior quality evaluator. Your role is the "Discriminator".
        You must judge the Generator's output with strict standards.

        ## Scoring Rules
        - 9-10: Exceptional. Exceeds expectations significantly.
        - 7-8: Good. Solid implementation with minor room for improvement.
        - 5-6: Adequate. Meets minimum requirements but has clear issues.
        - 3-4: Below standard. Significant problems that need addressing.
        - 1-2: Poor. Fundamentally flawed. Needs complete rework.
        - 0: Not implemented or non-functional.

        ## Anti-Slop Rules
        - NO grade inflation. 7 is "good", not "amazing".
        - NO vague evidence. Every score MUST cite specific observations.
        - NO passing everything. If it looks too clean, you're not looking hard enough.

        ## Output Format
        ```json
        {
          "evaluation": {
            "scores": {
              "task_completion": {"score": 7.5, "evidence": "...", "suggestions": [...]},
              "execution_efficiency": {"score": 6.0, "evidence": "...", "suggestions": [...]},
              "output_quality": {"score": 8.0, "evidence": "...", "suggestions": [...]},
              "error_handling": {"score": 5.5, "evidence": "...", "suggestions": [...]}
            },
            "weighted_total": 6.85,
            "verdict": "PASS",
            "summary": "..."
          }
        }
        ```
        """;

    // ==================== 辅助方法 ====================

    /**
     * 从 LLM 响应中解析 AI 评分。
     * 尝试从 JSON 中提取 weighted_total，失败则返回默认值 5.0。
     */
    private static double parseAiScore(String content) {
        if (content == null || content.isEmpty()) return 5.0;
        try {
            // 尝试提取 "weighted_total": X.X
            int idx = content.indexOf("weighted_total");
            if (idx >= 0) {
                int colon = content.indexOf(':', idx);
                int end = content.indexOf(',', colon);
                if (end < 0) end = content.indexOf('}', colon);
                if (end < 0) end = content.length();
                String numStr = content.substring(colon + 1, end).trim();
                return Double.parseDouble(numStr);
            }
            // 尝试提取 "score": X.X (取第一个)
            idx = content.indexOf("\"score\"");
            if (idx >= 0) {
                int colon = content.indexOf(':', idx);
                int end = content.indexOf(',', colon);
                if (end < 0) end = content.indexOf('}', colon);
                if (end < 0) end = content.length();
                String numStr = content.substring(colon + 1, end).trim();
                return Double.parseDouble(numStr);
            }
        } catch (Exception ignored) {
            // 解析失败返回默认值
        }
        return 5.0;
    }

    /**
     * 截断字符串到指定长度。
     */
    /** 从 Session 消息中提取工具调用轨迹（超时/异常后仍可用） */
    private static void extractToolCallsFromSession(Session taskSession, ExecutionTrace trace) {
        if (taskSession == null) return;
        try {
            List<Message> messages = taskSession.getMessages();
            Map<String, ToolInvocation> pendingCalls = new HashMap<>();
            for (Message msg : messages) {
                if (msg.hasToolCalls()) {
                    for (Message.ToolCallInfo tci : msg.getToolCalls()) {
                        ToolInvocation inv = new ToolInvocation(tci.getName(), tci.getArguments(), "", 0, true);
                        inv.setToolCallId(tci.getId());
                        pendingCalls.put(tci.getId(), inv);
                        trace.addToolCall(inv);
                    }
                }
                if (msg.getRole() == Message.Role.TOOL) {
                    for (Message.ContentBlock block : msg.getContent()) {
                        if (block instanceof Message.ToolResultContent trc) {
                            ToolInvocation inv = pendingCalls.get(trc.getToolUseId());
                            if (inv != null) {
                                Object r = trc.getResult();
                                String output = r != null ? r.toString() : "";
                                inv.setOutput(output.substring(0, Math.min(500, output.length())));
                            }
                        }
                    }
                }
            }
            trace.setTotalSteps(trace.getToolCalls().size());
        } catch (Exception ignored) { }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // ==================== 清理 ====================

    /**
     * 清理评测产生的临时文件。
     */
    private static void cleanupEvalFiles() {
        try {
            java.nio.file.Path[] tempFiles = {
                workspaceRoot.resolve("hello.txt"),
                workspaceRoot.resolve("temp/test-output"),
                workspaceRoot.resolve("temp"),
                workspaceRoot.resolve("jwcode-core/hello.txt"),
                workspaceRoot.resolve("jwcode-core/temp/test-output"),
                workspaceRoot.resolve("jwcode-core/temp"),
                workspaceRoot.resolve("jwcode-core/src/main/java/com/jwcode/core/eval/version.txt"),
                workspaceRoot.resolve("jwcode-core/src/main/java/com/jwcode/core/eval/Config.java"),
                workspaceRoot.resolve("jwcode-core/src/main/java/com/jwcode/core/eval/StringUtils.java"),
                workspaceRoot.resolve("jwcode-core/jwcode-core/src/main/java/com/jwcode/core/eval/version.txt"),
                workspaceRoot.resolve("jwcode-core/jwcode-core/src/main/java/com/jwcode/core/eval/Config.java"),
                workspaceRoot.resolve("jwcode-core/jwcode-core/src/main/java/com/jwcode/core/eval/StringUtils.java"),
                workspaceRoot.resolve("jwcode-core/jwcode-core/src/main/java/com/jwcode/core/eval/model"),
                workspaceRoot.resolve("jwcode-core/src/main/java/com/jwcode/core/eval/model"),
                workspaceRoot.resolve("jwcode-core/src/main/java/com/jwcode/core/eval/model/User.java"),
                workspaceRoot.resolve("jwcode-core/src/main/java/com/jwcode/core/eval/model/Order.java"),
                workspaceRoot.resolve("jwcode-core/src/main/java/com/jwcode/core/eval/model/Product.java"),
                workspaceRoot.resolve("jwcode-core/src/main/java/com/jwcode/core/eval/ExampleEvalTest.java"),
                workspaceRoot.resolve("jwcode-core/src/test/java/com/jwcode/core/eval/ExampleEvalTest.java"),
                workspaceRoot.resolve("jwcode-core/main_files.txt"),
                workspaceRoot.resolve("jwcode-core/test_files.txt"),
                workspaceRoot.resolve("jwcode-core/report_en.txt"),
                workspaceRoot.resolve("jwcode-core/report_utf8.txt"),
                workspaceRoot.resolve("jwcode-core/analyze_missing_tests.py"),
                workspaceRoot.resolve("jwcode-core/missing_tests_report.txt"),
                workspaceRoot.resolve("jwcode-core/result_py.txt"),
                workspaceRoot.resolve("jwcode-core/result_utf8.txt"),
                workspaceRoot.resolve("jwcode-core/tool_files_list.txt"),
                workspaceRoot.resolve("jwcode-core/source_files.txt"),
                workspaceRoot.resolve("jwcode-core/subpackage_dirs.txt"),
                workspaceRoot.resolve("main_files.txt"),
                workspaceRoot.resolve("test_files.txt"),
                // 工作区根目录下的其他遗留文件
                workspaceRoot.resolve("main_all.txt"),
                workspaceRoot.resolve("main_full.txt"),
                workspaceRoot.resolve("test_all.txt"),
                workspaceRoot.resolve("test_full.txt"),
                workspaceRoot.resolve("hook_files.txt"),
                workspaceRoot.resolve("root_list.txt"),
                workspaceRoot.resolve("root_list2.txt"),
                workspaceRoot.resolve("todo_result.txt"),
                workspaceRoot.resolve("tool_structure.txt"),
                workspaceRoot.resolve("list_tool.ps1"),
                workspaceRoot.resolve("测试命令.txt"),
                workspaceRoot.resolve("nul"),
                // jwcode-core 目录下的其他遗留文件
                workspaceRoot.resolve("jwcode-core/main_all.txt"),
                workspaceRoot.resolve("jwcode-core/main_full.txt"),
                workspaceRoot.resolve("jwcode-core/test_all.txt"),
                workspaceRoot.resolve("jwcode-core/test_full.txt"),
                workspaceRoot.resolve("jwcode-core/hook_files.txt"),
                workspaceRoot.resolve("jwcode-core/root_list.txt"),
                workspaceRoot.resolve("jwcode-core/root_list2.txt"),
                workspaceRoot.resolve("jwcode-core/todo_result.txt"),
                workspaceRoot.resolve("jwcode-core/tool_structure.txt"),
                workspaceRoot.resolve("jwcode-core/list_tool.ps1"),
                workspaceRoot.resolve("jwcode-core/测试命令.txt"),
                workspaceRoot.resolve("jwcode-core/nul"),
            };
            for (java.nio.file.Path p : tempFiles) {
                java.nio.file.Files.deleteIfExists(p);
            }
            // 递归删除目录
            for (java.nio.file.Path p : tempFiles) {
                if (java.nio.file.Files.isDirectory(p)) {
                    try (var stream = java.nio.file.Files.walk(p)) {
                        stream.sorted(java.util.Comparator.reverseOrder())
                            .forEach(path -> {
                                try { java.nio.file.Files.deleteIfExists(path); } catch (Exception ignored) {}
                            });
                    }
                }
            }

            // === 通配扫描兜底清理 ===
            // 扫描工作区根目录，删除符合常见测试遗留文件模式的条目
            String[] rootPatterns = {"_files.txt", "_all.txt", "_full.txt", "_list.txt", "_result.txt", "_structure.txt"};
            try (var rootFiles = java.nio.file.Files.list(workspaceRoot)) {
                rootFiles.forEach(path -> {
                    String name = path.getFileName().toString();
                    // 跳过目录和受保护路径
                    if (java.nio.file.Files.isDirectory(path)) return;
                    for (String pattern : rootPatterns) {
                        if (name.endsWith(pattern)) {
                            try {
                                java.nio.file.Files.deleteIfExists(path);
                                System.out.println("  [清理-通配] 删除: " + name);
                            } catch (Exception ignored) {}
                            break;
                        }
                    }
                });
            }
            // 扫描 jwcode-core 目录
            java.nio.file.Path coreDir = workspaceRoot.resolve("jwcode-core");
            if (java.nio.file.Files.isDirectory(coreDir)) {
                try (var coreFiles = java.nio.file.Files.list(coreDir)) {
                    coreFiles.forEach(path -> {
                        String name = path.getFileName().toString();
                        if (java.nio.file.Files.isDirectory(path)) return;
                        // 清理 Python 脚本产物和报告文件
                        if (name.startsWith("analyze_") && name.endsWith(".py")) {
                            try { java.nio.file.Files.deleteIfExists(path); System.out.println("  [清理-通配] 删除: jwcode-core/" + name); } catch (Exception ignored) {}
                        } else if (name.startsWith("report_") && (name.endsWith(".txt") || name.endsWith(".md"))) {
                            try { java.nio.file.Files.deleteIfExists(path); System.out.println("  [清理-通配] 删除: jwcode-core/" + name); } catch (Exception ignored) {}
                        } else if (name.startsWith("result_") && name.endsWith(".txt")) {
                            try { java.nio.file.Files.deleteIfExists(path); System.out.println("  [清理-通配] 删除: jwcode-core/" + name); } catch (Exception ignored) {}
                        } else if (name.endsWith("_files.txt") || name.endsWith("_list.txt") || name.endsWith("_structure.txt")) {
                            try { java.nio.file.Files.deleteIfExists(path); System.out.println("  [清理-通配] 删除: jwcode-core/" + name); } catch (Exception ignored) {}
                        }
                    });
                }
            }

            // === 确保 temp/ 目录被彻底删除 ===
            // 即使 temp/ 中有不在列表中的文件，也强制递归删除
            java.nio.file.Path[] tempDirs = {
                workspaceRoot.resolve("temp"),
                workspaceRoot.resolve("jwcode-core/temp"),
            };
            for (java.nio.file.Path tempDir : tempDirs) {
                if (java.nio.file.Files.isDirectory(tempDir)) {
                    try (var stream = java.nio.file.Files.walk(tempDir)) {
                        stream.sorted(java.util.Comparator.reverseOrder())
                            .forEach(path -> {
                                try { java.nio.file.Files.deleteIfExists(path); } catch (Exception ignored) {}
                            });
                        System.out.println("  [清理] 已递归删除目录: " + tempDir.getFileName());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("  ⚠ 清理临时文件时出错: " + e.getMessage());
        }
    }

    // ==================== 统计计算 ====================

    private static void computeSummary() {
        List<EvalResult> results = report.getResults();

        Summary summary = new Summary();
        int skipped = (int) results.stream().filter(r -> !r.isPassed()
            && r.getFailureReason() != null && r.getFailureReason().contains("跳过")).count();
        int executed = results.size() - skipped;
        summary.setTotal(results.size());
        summary.setPassed((int) results.stream().filter(EvalResult::isPassed).count());
        summary.setFailed((int) results.stream().filter(r -> !r.isPassed()
            && (r.getFailureReason() == null || !r.getFailureReason().contains("跳过"))).count());
        summary.setSkipped(skipped);
        summary.setPassRate(executed > 0
            ? (double) summary.getPassed() / executed * 100.0 : 0.0);
        summary.setAvgObjectiveScore(results.stream()
            .mapToDouble(EvalResult::getObjectiveScore).average().orElse(0.0));

        Double avgAi = results.stream()
            .filter(r -> r.getAiScore() != null)
            .mapToDouble(EvalResult::getAiScore)
            .average().orElse(0.0);
        if (results.stream().anyMatch(r -> r.getAiScore() != null)) {
            summary.setAvgAiScore(avgAi);
        }

        report.setSummary(summary);

        // 按难度分组
        for (Difficulty diff : Difficulty.values()) {
            List<EvalResult> diffResults = results.stream()
                .filter(r -> {
                    EvalTask task = findTask(r.getTaskId());
                    return task != null && task.getDifficulty() == diff;
                })
                .collect(Collectors.toList());

            if (!diffResults.isEmpty()) {
                CategoryResult cr = new CategoryResult();
                cr.setCategory(diff.getLabel());
                int diffSkipped = (int) diffResults.stream().filter(r -> !r.isPassed()
                    && r.getFailureReason() != null && r.getFailureReason().contains("跳过")).count();
                int diffExecuted = diffResults.size() - diffSkipped;
                cr.setTotal(diffResults.size());
                cr.setPassed((int) diffResults.stream().filter(EvalResult::isPassed).count());
                cr.setFailed(diffExecuted - cr.getPassed());
                cr.setPassRate(diffExecuted > 0 ? (double) cr.getPassed() / diffExecuted * 100.0 : 100.0);
                report.getByDifficulty().put(diff, cr);
            }
        }

        // 按能力维度分组
        Map<String, List<EvalResult>> byCap = results.stream()
            .collect(Collectors.groupingBy(r -> {
                EvalTask task = findTask(r.getTaskId());
                return task != null ? task.getCapability() : "unknown";
            }));

        for (Map.Entry<String, List<EvalResult>> entry : byCap.entrySet()) {
            CategoryResult cr = new CategoryResult();
            cr.setCategory(entry.getKey());
            int capSkipped = (int) entry.getValue().stream().filter(r -> !r.isPassed()
                && r.getFailureReason() != null && r.getFailureReason().contains("跳过")).count();
            int capExecuted = entry.getValue().size() - capSkipped;
            cr.setTotal(entry.getValue().size());
            cr.setPassed((int) entry.getValue().stream().filter(EvalResult::isPassed).count());
            cr.setFailed(capExecuted - cr.getPassed());
            cr.setPassRate(capExecuted > 0 ? (double) cr.getPassed() / capExecuted * 100.0 : 100.0);
            report.getByCapability().put(entry.getKey(), cr);
        }
    }

    // ==================== 工具方法 ====================

    private static List<EvalTask> filterTasks(Difficulty difficulty) {
        // eval-quick 模式下跳过复杂任务
        if (skipComplex && difficulty == Difficulty.COMPLEX) {
            System.out.println("  ⏭ 跳过 " + difficulty.getLabel() + " 任务集 (eval-quick 模式)");
            return new ArrayList<>();
        }
        return allTasks.stream()
            .filter(t -> t.getDifficulty() == difficulty)
            .collect(Collectors.toList());
    }

    private static EvalTask findTask(String taskId) {
        return allTasks.stream()
            .filter(t -> t.getId().equals(taskId))
            .findFirst().orElse(null);
    }

    private static String padRight(String s, int n) {
        if (s == null) s = "";
        return String.format("%-" + n + "s", s);
    }
}

package com.jwcode.core.coordinator;

import com.fasterxml.jackson.databind.JsonNode;
import com.jwcode.core.checker.CheckerResult;
import com.jwcode.core.checker.EnvironmentChecker;
import com.jwcode.core.report.*;
import com.jwcode.core.state.TestState;
import com.jwcode.core.state.ToolTestStateMachine;
import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;
import com.jwcode.core.tool.ToolValidationResult;
import com.jwcode.core.tool.context.ToolExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 工具测试协调器
 * 
 * 负责管理工具测试的完整生命周期：
 * - 环境自检
 * - 工具注册与发现
 * - 参数验证
 * - 异常处理
 * - 流程控制
 * - 报告生成
 */
public class ToolTestCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(ToolTestCoordinator.class);

    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final EnvironmentChecker environmentChecker;
    private final ToolTestConfig config;
    private final ToolTestStateMachine stateMachine;
    private final TestReport.Builder reportBuilder;
    private final List<TestResult> testResults;

    private CheckerResult lastEnvironmentCheck;
    private Consumer<TestProgress> progressCallback;

    public ToolTestCoordinator() {
        this(new ToolTestConfig());
    }

    public ToolTestCoordinator(ToolTestConfig config) {
        this.config = config;
        this.toolRegistry = new ToolRegistry();
        this.toolExecutor = new ToolExecutor(toolRegistry);
        this.environmentChecker = new EnvironmentChecker();
        this.stateMachine = new ToolTestStateMachine();
        this.reportBuilder = TestReport.builder();
        this.testResults = new ArrayList<>();
    }

    public ToolTestCoordinator(ToolRegistry registry, ToolExecutor executor, ToolTestConfig config) {
        this.config = config;
        this.toolRegistry = registry;
        this.toolExecutor = executor;
        this.environmentChecker = new EnvironmentChecker();
        this.stateMachine = new ToolTestStateMachine();
        this.reportBuilder = TestReport.builder();
        this.testResults = new ArrayList<>();
    }

    /**
     * 运行完整测试流程
     */
    public TestReport runAllTests() {
        return runAllTests(null);
    }

    /**
     * 运行完整测试流程（带进度回调）
     */
    public TestReport runAllTests(Consumer<TestProgress> progressCallback) {
        this.progressCallback = progressCallback;
        
        logger.info("═══ 开始工具测试流程 ═══");
        stateMachine.start();
        
        try {
            // 1. 环境自检
            if (config.isEnvironmentCheckEnabled()) {
                performEnvironmentCheck();
            }
            
            // 2. 获取所有工具
            List<Tool<?, ?, ?>> tools = getToolsToTest();
            
            if (tools.isEmpty()) {
                logger.warn("没有找到要测试的工具");
                stateMachine.skip();
                return buildReport();
            }
            
            reportBuilder.testSuiteName(config.getTestSuiteName());
            reportBuilder.environmentCheck(lastEnvironmentCheck);
            reportBuilder.stateMachine(stateMachine);
            
            // 3. 依次测试每个工具
            int total = tools.size();
            int current = 0;
            
            for (Tool<?, ?, ?> tool : tools) {
                current++;
                TestProgress progress = new TestProgress(current, total, tool.getName());
                emitProgress(progress);
                
                TestResult result = testTool(tool);
                testResults.add(result);
                reportBuilder.addResult(result);
                
                // 检查是否需要终止
                if (shouldTerminate(result)) {
                    logger.warn("关键工具测试失败，终止测试流程");
                    stateMachine.terminate();
                    break;
                }
            }
            
            // 4. 确定最终状态
            finalizeState();
            
        } catch (Exception e) {
            logger.error("测试流程异常", e);
            stateMachine.error();
            reportBuilder.addError("测试流程异常: " + e.getMessage());
        }
        
        return buildReport();
    }

    /**
     * 测试单个工具
     */
    private TestResult testTool(Tool<?, ?, ?> tool) {
        String toolName = tool.getName();
        TestResult result = TestResult.create(toolName, "N/A");
        
        try {
            // 检查工具是否启用
            if (!tool.isEnabled()) {
                return result.skipped("工具已禁用");
            }
            
            // 获取测试输入
            JsonNode testInput = config.getTestInput(toolName);
            if (testInput == null) {
                testInput = tool.getInputSchema();
            }
            
            if (testInput == null) {
                return result.skipped("无测试输入");
            }
            
            // 创建上下文
            ToolExecutionContext context = ToolExecutionContext.builder().build();
            
            // 使用 JSON 方式执行工具
            ToolExecutor.ToolExecutionResult execResult = 
                toolExecutor.execute(toolName, testInput, context).get();
            
            if (execResult.isSuccess()) {
                return result.success("执行成功");
            } else {
                return result.failed(execResult.getErrorMessage());
            }
            
        } catch (Exception e) {
            logger.error("工具 {} 执行异常", toolName, e);
            
            if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                return result.error("执行超时（" + config.getTimeoutSeconds() + "秒）");
            } else {
                return result.error(e.getMessage());
            }
        }
    }

    /**
     * 执行环境检测
     */
    private void performEnvironmentCheck() {
        logger.info("执行环境自检...");
        lastEnvironmentCheck = environmentChecker.checkAll();
        
        emitProgress(new TestProgress(0, 100, "环境检测", 
            lastEnvironmentCheck.isOverallSuccess() ? "通过" : "有依赖缺失"));
        
        if (!config.isStrictMode() && !lastEnvironmentCheck.isOverallSuccess()) {
            logger.warn("环境检测未完全通过，但继续执行（严格模式关闭）");
            reportBuilder.addWarning("部分依赖不可用，部分测试将被跳过");
        }
    }

    /**
     * 获取要测试的工具列表
     */
    private List<Tool<?, ?, ?>> getToolsToTest() {
        List<Tool<?, ?, ?>> tools;
        
        if (config.getToolNames() != null && !config.getToolNames().isEmpty()) {
            // 指定工具列表
            tools = new ArrayList<>();
            for (String name : config.getToolNames()) {
                toolRegistry.findByName(name).ifPresent(tools::add);
            }
        } else {
            // 所有已注册工具
            tools = toolRegistry.getAllTools();
        }
        
        // 过滤掉需要外部依赖的工具（如果环境不支持）
        if (!config.isIncludeToolsRequiringExternalDeps()) {
            tools = tools.stream()
                .filter(t -> !requiresExternalDependency(t))
                .toList();
        }
        
        return tools;
    }

    /**
     * 检查工具是否需要外部依赖
     */
    private boolean requiresExternalDependency(Tool<?, ?, ?> tool) {
        String name = tool.getName().toLowerCase();
        return name.contains("websearch") || 
               name.contains("lsp") || 
               name.contains("mcp");
    }

    /**
     * 判断是否需要终止流程
     */
    private boolean shouldTerminate(TestResult result) {
        if (result.isError() && config.isCriticalErrorStopsAll()) {
            return true;
        }
        
        // 连续失败次数超过阈值
        long recentFailures = testResults.stream()
            .skip(Math.max(0, testResults.size() - config.getMaxConsecutiveFailures()))
            .filter(TestResult::isFailed)
            .count();
        
        return recentFailures >= config.getMaxConsecutiveFailures();
    }

    /**
     * 确定最终状态
     */
    private void finalizeState() {
        if (stateMachine.getCurrentState() != TestState.TERMINATED &&
            stateMachine.getCurrentState() != TestState.ERROR) {
            
            long failed = testResults.stream().filter(TestResult::isFailed).count();
            long error = testResults.stream().filter(TestResult::isError).count();
            long success = testResults.stream().filter(TestResult::isSuccess).count();
            
            if (failed + error == 0) {
                stateMachine.success();
            } else if (success > 0) {
                stateMachine.partial();
            } else {
                stateMachine.failed();
            }
        }
    }

    /**
     * 构建报告
     */
    private TestReport buildReport() {
        return reportBuilder
            .addResults(testResults)
            .build();
    }

    /**
     * 发送进度
     */
    private void emitProgress(TestProgress progress) {
        if (progressCallback != null) {
            try {
                progressCallback.accept(progress);
            } catch (Exception e) {
                logger.warn("进度回调异常", e);
            }
        }
    }

    /**
     * 生成报告（指定格式）
     */
    public String generateReport(ReportFormat format) {
        TestReport report = buildReport();
        
        ReportFormatter formatter = switch (format) {
            case MARKDOWN -> new MarkdownFormatter();
            case JSON -> new JsonFormatter();
        };
        
        return formatter.format(report);
    }

    /**
     * 获取状态机
     */
    public ToolTestStateMachine getStateMachine() {
        return stateMachine;
    }

    /**
     * 获取环境检测结果
     */
    public CheckerResult getLastEnvironmentCheck() {
        return lastEnvironmentCheck;
    }

    /**
     * 测试进度
     */
    public record TestProgress(
        int current,
        int total,
        String currentTool,
        String message
    ) {
        public TestProgress(int current, int total, String currentTool) {
            this(current, total, currentTool, null);
        }
        
        public double getPercentage() {
            return total > 0 ? (double) current / total * 100 : 0;
        }
    }

    /**
     * 报告格式
     */
    public enum ReportFormat {
        MARKDOWN,
        JSON
    }
}

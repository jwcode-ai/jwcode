package com.jwcode.core.eval;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * EvalTask — 能力评测任务定义。
 *
 * <p>描述一个可执行的评测任务，包含任务元信息、验收标准和执行配置。
 * 任务定义从 YAML 文件加载，支持简单/中等/复杂三级难度。</p>
 */
public class EvalTask {

    /** 任务唯一标识 */
    private String id;

    /** 任务名称 */
    private String name;

    /** 任务描述 */
    private String description;

    /** 难度级别 */
    private Difficulty difficulty;

    /** 能力维度（tool_call / agent_collab / code_understand / session_mgmt / ...） */
    private String capability;

    /** 用户提示词（提交给系统的初始输入） */
    private String userPrompt;

    /** 验收检查列表 */
    private List<AcceptanceCheck> acceptanceChecks;

    /** 超时时间（秒） */
    private int timeoutSeconds;

    /** 是否启用 AI 评审（复杂任务启用，简单任务可关闭） */
    private boolean aiEvalEnabled;

    /** 模拟模式下是否跳过（写操作类任务在模拟模式下无法通过） */
    private boolean skipInMockMode;

    /** 期望的最小步骤数（用于效率评估） */
    private int expectedMinSteps;

    /** 期望的最大步骤数 */
    private int expectedMaxSteps;

    /** 前置依赖任务 ID 列表 */
    private List<String> dependsOn;

    /** 标签 */
    private List<String> tags;

    public EvalTask() {
        this.acceptanceChecks = new ArrayList<>();
        this.dependsOn = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.timeoutSeconds = 60;
        this.aiEvalEnabled = true;
        this.skipInMockMode = false;
        this.expectedMinSteps = 1;
        this.expectedMaxSteps = 20;
    }

    // ==================== 枚举 ====================

    public enum Difficulty {
        SIMPLE("🟢 简单"),
        MEDIUM("🟡 中等"),
        COMPLEX("🔴 复杂");

        private final String label;
        Difficulty(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    // ==================== 验收检查 ====================

    /**
     * 验收检查定义。
     * 每个检查包含检查类型和参数，由 AcceptanceChecker 执行。
     */
    public static class AcceptanceCheck {
        /** 检查类型 */
        private CheckType type;
        /** 检查参数 */
        private java.util.Map<String, Object> params;

        public AcceptanceCheck() {
            this.params = new java.util.HashMap<>();
        }

        public AcceptanceCheck(CheckType type, java.util.Map<String, Object> params) {
            this.type = type;
            this.params = params != null ? params : new java.util.HashMap<>();
        }

        public CheckType getType() { return type; }
        public void setType(CheckType type) { this.type = type; }
        public java.util.Map<String, Object> getParams() { return params; }
        public void setParams(java.util.Map<String, Object> params) { this.params = params; }

        @SuppressWarnings("unchecked")
        public <T> T getParam(String key, T defaultValue) {
            Object val = params.get(key);
            return val != null ? (T) val : defaultValue;
        }
    }

    public enum CheckType {
        /** 文件存在 */
        FILE_EXISTS,
        /** 文件内容精确匹配 */
        FILE_CONTENT_MATCHES,
        /** 文件内容包含子串 */
        FILE_CONTENT_CONTAINS,
        /** 文件内容匹配正则 */
        FILE_CONTENT_MATCHES_REGEX,
        /** 目录存在 */
        DIR_EXISTS,
        /** 命令执行成功（检查退出码） */
        COMMAND_EXIT_CODE_ZERO,
        /** 命令输出包含指定文本 */
        COMMAND_OUTPUT_CONTAINS,
        /** 编译检查（mvn compile） */
        COMPILE_SUCCESS,
        /** 测试通过（mvn test） */
        TEST_SUCCESS,
        /** 文件行数在范围内 */
        FILE_LINE_COUNT_BETWEEN,
        /** 自定义检查（通过类名反射） */
        CUSTOM
    }

    // ==================== 执行轨迹 ====================

    /**
     * 执行轨迹 — 记录任务执行过程中的详细信息。
     */
    public static class ExecutionTrace {
        private String taskId;
        private List<ToolInvocation> toolCalls;
        private long totalDurationMs;
        private int totalSteps;
        private List<String> errors;
        private int retryCount;
        private long startedAt;
        private long completedAt;

        public ExecutionTrace() {
            this.toolCalls = new ArrayList<>();
            this.errors = new ArrayList<>();
        }

        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public List<ToolInvocation> getToolCalls() { return toolCalls; }
        public long getTotalDurationMs() { return totalDurationMs; }
        public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }
        public int getTotalSteps() { return totalSteps; }
        public void setTotalSteps(int totalSteps) { this.totalSteps = totalSteps; }
        public List<String> getErrors() { return errors; }
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
        public long getStartedAt() { return startedAt; }
        public void setStartedAt(long startedAt) { this.startedAt = startedAt; }
        public long getCompletedAt() { return completedAt; }
        public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }
        public Duration getDuration() { return Duration.ofMillis(totalDurationMs); }

        public void addToolCall(ToolInvocation call) { this.toolCalls.add(call); }
        public void addError(String error) { this.errors.add(error); }
    }

    /** 单次工具调用记录（完整版） */
    public static class ToolInvocation {
        private String toolName;
        private String toolCallId;     // 工具调用 ID（用于匹配 call ↔ result）
        private String input;          // 工具入参 JSON
        private String output;         // 工具返回结果（截取前 500 字符）
        private long durationMs;
        private boolean success;
        private String errorMessage;   // 工具执行错误信息
        private int inputLength;       // 入参长度
        private int outputLength;      // 出参长度

        public ToolInvocation() {}

        public ToolInvocation(String toolName, String input, String output, long durationMs, boolean success) {
            this.toolName = toolName;
            this.input = input;
            this.output = output;
            this.durationMs = durationMs;
            this.success = success;
            this.inputLength = input != null ? input.length() : 0;
            this.outputLength = output != null ? output.length() : 0;
        }

        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        public String getToolCallId() { return toolCallId; }
        public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
        public String getInput() { return input; }
        public void setInput(String input) { this.input = input; this.inputLength = input != null ? input.length() : 0; }
        public String getOutput() { return output; }
        public void setOutput(String output) { this.output = output; this.outputLength = output != null ? output.length() : 0; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public int getInputLength() { return inputLength; }
        public int getOutputLength() { return outputLength; }

        /** 获取入参摘要（前 200 字符，surrogate-safe） */
        public String getInputSummary() {
            if (input == null) return "";
            return input.length() <= 200 ? input : safeSubstring(input, 0, 200) + "...";
        }

        /** 获取出参摘要（前 200 字符，surrogate-safe） */
        public String getOutputSummary() {
            if (output == null) return "";
            return output.length() <= 200 ? output : safeSubstring(output, 0, 200) + "...";
        }

        /**
         * 安全截断字符串，确保不会在 Unicode surrogate pair 中间截断。
         * 如果截断位置恰好在一个 surrogate pair 中间，则向前回退一个字符。
         */
        private static String safeSubstring(String s, int start, int end) {
            if (s == null || start < 0 || end > s.length() || start >= end) return "";
            int adjustedEnd = end;
            // 如果 end 恰好落在一个 low surrogate 上（即前一个字符是 high surrogate），回退
            if (adjustedEnd > start && adjustedEnd <= s.length()) {
                char c = s.charAt(adjustedEnd - 1);
                if (Character.isHighSurrogate(c)) {
                    adjustedEnd--;
                }
            }
            return s.substring(start, adjustedEnd);
        }
    }

    // ==================== 评测结果 ====================

    /**
     * 单个任务的评测结果。
     */
    public static class EvalResult {
        private String taskId;
        private boolean passed;
        private String failureReason;
        private long durationMs;
        private int totalSteps;
        private double objectiveScore;       // 客观验收得分 0-100
        private Double aiScore;              // AI 评审得分 0-10 (null 表示未启用)
        private Double weightedFinalScore;   // 综合得分
        private List<CheckResult> checkResults;
        private ExecutionTrace trace;
        private String aiFeedback;           // AI 评审反馈

        public EvalResult() {
            this.checkResults = new ArrayList<>();
        }

        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public int getTotalSteps() { return totalSteps; }
        public void setTotalSteps(int totalSteps) { this.totalSteps = totalSteps; }
        public double getObjectiveScore() { return objectiveScore; }
        public void setObjectiveScore(double objectiveScore) { this.objectiveScore = objectiveScore; }
        public Double getAiScore() { return aiScore; }
        public void setAiScore(Double aiScore) { this.aiScore = aiScore; }
        public Double getWeightedFinalScore() { return weightedFinalScore; }
        public void setWeightedFinalScore(Double weightedFinalScore) { this.weightedFinalScore = weightedFinalScore; }
        public List<CheckResult> getCheckResults() { return checkResults; }
        public ExecutionTrace getTrace() { return trace; }
        public void setTrace(ExecutionTrace trace) { this.trace = trace; }
        public String getAiFeedback() { return aiFeedback; }
        public void setAiFeedback(String aiFeedback) { this.aiFeedback = aiFeedback; }

        public void addCheckResult(CheckResult cr) { this.checkResults.add(cr); }
    }

    /** 单次验收检查结果 */
    public static class CheckResult {
        private CheckType type;
        private String description;
        private boolean passed;
        private String detail;
        private String expected;
        private String actual;

        public CheckResult() {}

        public CheckResult(CheckType type, String description, boolean passed, String detail) {
            this.type = type;
            this.description = description;
            this.passed = passed;
            this.detail = detail;
        }

        public CheckType getType() { return type; }
        public void setType(CheckType type) { this.type = type; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        public String getDetail() { return detail; }
        public void setDetail(String detail) { this.detail = detail; }
        public String getExpected() { return expected; }
        public void setExpected(String expected) { this.expected = expected; }
        public String getActual() { return actual; }
        public void setActual(String actual) { this.actual = actual; }
    }

    // ==================== 综合报告 ====================

    /**
     * 完整的能力评测报告。
     */
    public static class EvalReport {
        private String suiteName;
        private String timestamp;
        private long totalDurationMs;
        private Summary summary;
        private java.util.Map<Difficulty, CategoryResult> byDifficulty;
        private java.util.Map<String, CategoryResult> byCapability;
        private List<EvalResult> results;
        private String comparisonPreviousRun;  // 与上次对比的摘要

        public EvalReport() {
            this.byDifficulty = new java.util.HashMap<>();
            this.byCapability = new java.util.HashMap<>();
            this.results = new java.util.ArrayList<>();
        }

        public String getSuiteName() { return suiteName; }
        public void setSuiteName(String suiteName) { this.suiteName = suiteName; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        public long getTotalDurationMs() { return totalDurationMs; }
        public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }
        public Summary getSummary() { return summary; }
        public void setSummary(Summary summary) { this.summary = summary; }
        public java.util.Map<Difficulty, CategoryResult> getByDifficulty() { return byDifficulty; }
        public java.util.Map<String, CategoryResult> getByCapability() { return byCapability; }
        public List<EvalResult> getResults() { return results; }
        public String getComparisonPreviousRun() { return comparisonPreviousRun; }
        public void setComparisonPreviousRun(String s) { this.comparisonPreviousRun = s; }

        public void addResult(EvalResult result) { this.results.add(result); }
    }

    /** 汇总统计 */
    public static class Summary {
        private int total;
        private int passed;
        private int failed;
        private int skipped;
        private int errors;
        private double passRate;
        private double avgObjectiveScore;
        private Double avgAiScore;

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public int getPassed() { return passed; }
        public void setPassed(int passed) { this.passed = passed; }
        public int getFailed() { return failed; }
        public void setFailed(int failed) { this.failed = failed; }
        public int getSkipped() { return skipped; }
        public void setSkipped(int skipped) { this.skipped = skipped; }
        public int getErrors() { return errors; }
        public void setErrors(int errors) { this.errors = errors; }
        public double getPassRate() { return passRate; }
        public void setPassRate(double passRate) { this.passRate = passRate; }
        public double getAvgObjectiveScore() { return avgObjectiveScore; }
        public void setAvgObjectiveScore(double avgObjectiveScore) { this.avgObjectiveScore = avgObjectiveScore; }
        public Double getAvgAiScore() { return avgAiScore; }
        public void setAvgAiScore(Double avgAiScore) { this.avgAiScore = avgAiScore; }
    }

    /** 分类统计 */
    public static class CategoryResult {
        private String category;
        private int total;
        private int passed;
        private int failed;
        private double passRate;

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public int getPassed() { return passed; }
        public void setPassed(int passed) { this.passed = passed; }
        public int getFailed() { return failed; }
        public void setFailed(int failed) { this.failed = failed; }
        public double getPassRate() { return passRate; }
        public void setPassRate(double passRate) { this.passRate = passRate; }
    }

    // ==================== Builder/Getters/Setters ====================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }
    public String getCapability() { return capability; }
    public void setCapability(String capability) { this.capability = capability; }
    public String getUserPrompt() { return userPrompt; }
    public void setUserPrompt(String userPrompt) { this.userPrompt = userPrompt; }
    public List<AcceptanceCheck> getAcceptanceChecks() { return acceptanceChecks; }
    public void setAcceptanceChecks(List<AcceptanceCheck> checks) { this.acceptanceChecks = checks; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public boolean isAiEvalEnabled() { return aiEvalEnabled; }
    public void setAiEvalEnabled(boolean aiEvalEnabled) { this.aiEvalEnabled = aiEvalEnabled; }
    public int getExpectedMinSteps() { return expectedMinSteps; }
    public void setExpectedMinSteps(int expectedMinSteps) { this.expectedMinSteps = expectedMinSteps; }
    public int getExpectedMaxSteps() { return expectedMaxSteps; }
    public void setExpectedMaxSteps(int expectedMaxSteps) { this.expectedMaxSteps = expectedMaxSteps; }
    public List<String> getDependsOn() { return dependsOn; }
    public void setDependsOn(List<String> dependsOn) { this.dependsOn = dependsOn; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public boolean isSkipInMockMode() { return skipInMockMode; }
    public void setSkipInMockMode(boolean skipInMockMode) { this.skipInMockMode = skipInMockMode; }

    public void addAcceptanceCheck(AcceptanceCheck check) { this.acceptanceChecks.add(check); }
    public void addTag(String tag) { this.tags.add(tag); }

    public EvalTask withId(String id) { this.id = id; return this; }
    public EvalTask withName(String name) { this.name = name; return this; }
    public EvalTask withDescription(String desc) { this.description = desc; return this; }
    public EvalTask withDifficulty(Difficulty d) { this.difficulty = d; return this; }
    public EvalTask withCapability(String c) { this.capability = c; return this; }
    public EvalTask withUserPrompt(String p) { this.userPrompt = p; return this; }
    public EvalTask withTimeout(int s) { this.timeoutSeconds = s; return this; }
    public EvalTask withAiEval(boolean v) { this.aiEvalEnabled = v; return this; }
    public EvalTask withSkipInMockMode(boolean v) { this.skipInMockMode = v; return this; }
}

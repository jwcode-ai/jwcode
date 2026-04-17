package com.jwcode.core.report;

import com.jwcode.core.checker.CheckerResult;
import com.jwcode.core.state.TestState;
import com.jwcode.core.state.ToolTestStateMachine;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 测试报告
 */
public class TestReport {

    private final LocalDateTime generatedAt;
    private final String testSuiteName;
    private final List<TestResult> results;
    private final CheckerResult environmentCheck;
    private final ToolTestStateMachine stateMachine;
    private final Map<String, Object> summary;
    private final List<String> errors;
    private final List<String> warnings;

    private TestReport(Builder builder) {
        this.generatedAt = LocalDateTime.now();
        this.testSuiteName = builder.testSuiteName;
        this.results = new ArrayList<>(builder.results);
        this.environmentCheck = builder.environmentCheck;
        this.stateMachine = builder.stateMachine;
        this.summary = builder.summary;
        this.errors = new ArrayList<>(builder.errors);
        this.warnings = new ArrayList<>(builder.warnings);
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public String getTestSuiteName() { return testSuiteName; }
    public List<TestResult> getResults() { return new ArrayList<>(results); }
    public CheckerResult getEnvironmentCheck() { return environmentCheck; }
    public ToolTestStateMachine getStateMachine() { return stateMachine; }
    public Map<String, Object> getSummary() { return summary; }
    public List<String> getErrors() { return new ArrayList<>(errors); }
    public List<String> getWarnings() { return new ArrayList<>(warnings); }

    // 统计方法
    public int getTotalCount() { return results.size(); }
    
    public int getSuccessCount() {
        return (int) results.stream().filter(TestResult::isSuccess).count();
    }
    
    public int getFailedCount() {
        return (int) results.stream().filter(TestResult::isFailed).count();
    }
    
    public int getSkippedCount() {
        return (int) results.stream().filter(TestResult::isSkipped).count();
    }
    
    public int getErrorCount() {
        return (int) results.stream().filter(TestResult::isError).count();
    }

    public double getSuccessRate() {
        if (results.isEmpty()) return 0.0;
        int executed = results.size() - getSkippedCount();
        if (executed == 0) return 0.0;
        return (double) getSuccessCount() / executed * 100;
    }

    public long getTotalDurationMs() {
        return results.stream().mapToLong(TestResult::getDurationMs).sum();
    }

    public List<TestResult> getSuccessfulTests() {
        return results.stream().filter(TestResult::isSuccess).collect(Collectors.toList());
    }

    public List<TestResult> getFailedTests() {
        return results.stream().filter(TestResult::isFailed).collect(Collectors.toList());
    }

    public List<TestResult> getSkippedTests() {
        return results.stream().filter(TestResult::isSkipped).collect(Collectors.toList());
    }

    public TestState getOverallState() {
        if (stateMachine != null) {
            return stateMachine.getCurrentState();
        }
        if (getFailedCount() > 0 || getErrorCount() > 0) {
            return TestState.FAILED;
        }
        if (getSuccessCount() == results.size()) {
            return TestState.SUCCESS;
        }
        if (getSuccessCount() > 0) {
            return TestState.PARTIAL;
        }
        return TestState.SKIPPED;
    }

    /**
     * 生成摘要
     */
    public String generateSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ 测试报告摘要 ═══\n");
        sb.append(String.format("测试套件: %s\n", testSuiteName));
        sb.append(String.format("生成时间: %s\n", 
            generatedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        sb.append(String.format("总体状态: %s %s\n", 
            getOverallState().getIcon(), getOverallState().getDescription()));
        sb.append("\n── 测试统计 ──\n");
        sb.append(String.format("总计: %d | 成功: %d | 失败: %d | 跳过: %d | 错误: %d\n",
            getTotalCount(), getSuccessCount(), getFailedCount(), 
            getSkippedCount(), getErrorCount()));
        sb.append(String.format("成功率: %.1f%%\n", getSuccessRate()));
        sb.append(String.format("总耗时: %d ms\n", getTotalDurationMs()));
        
        if (!errors.isEmpty()) {
            sb.append("\n── 错误 ──\n");
            errors.forEach(e -> sb.append("  ❌ ").append(e).append("\n"));
        }
        
        if (!warnings.isEmpty()) {
            sb.append("\n── 警告 ──\n");
            warnings.forEach(w -> sb.append("  ⚠️ ").append(w).append("\n"));
        }
        
        return sb.toString();
    }

    /**
     * 构建器
     */
    public static class Builder {
        private String testSuiteName = "JwCode Tool Test";
        private List<TestResult> results = new ArrayList<>();
        private CheckerResult environmentCheck;
        private ToolTestStateMachine stateMachine;
        private Map<String, Object> summary;
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();

        public Builder testSuiteName(String name) {
            this.testSuiteName = name;
            return this;
        }

        public Builder addResult(TestResult result) {
            this.results.add(result);
            return this;
        }

        public Builder addResults(List<TestResult> results) {
            this.results.addAll(results);
            return this;
        }

        public Builder environmentCheck(CheckerResult check) {
            this.environmentCheck = check;
            return this;
        }

        public Builder stateMachine(ToolTestStateMachine stateMachine) {
            this.stateMachine = stateMachine;
            return this;
        }

        public Builder summary(Map<String, Object> summary) {
            this.summary = summary;
            return this;
        }

        public Builder addError(String error) {
            this.errors.add(error);
            return this;
        }

        public Builder addWarning(String warning) {
            this.warnings.add(warning);
            return this;
        }

        public TestReport build() {
            return new TestReport(this);
        }
    }
}

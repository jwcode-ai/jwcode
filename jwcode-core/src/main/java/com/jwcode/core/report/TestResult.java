package com.jwcode.core.report;

import com.jwcode.core.state.TestState;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 单个测试结果
 */
public class TestResult {

    private final String toolName;
    private final String input;
    private TestState state;
    private String message;
    private String errorDetail;
    private long durationMs;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Map<String, Object> metadata;

    public TestResult(String toolName, String input) {
        this.toolName = toolName;
        this.input = input;
        this.state = TestState.PENDING;
        this.startTime = LocalDateTime.now();
    }

    public static TestResult create(String toolName, String input) {
        return new TestResult(toolName, input);
    }

    public TestResult success(String message) {
        this.state = TestState.SUCCESS;
        this.message = message;
        this.endTime = LocalDateTime.now();
        this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        return this;
    }

    public TestResult failed(String error) {
        this.state = TestState.FAILED;
        this.errorDetail = error;
        this.endTime = LocalDateTime.now();
        this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        return this;
    }

    public TestResult skipped(String reason) {
        this.state = TestState.SKIPPED;
        this.message = reason;
        this.endTime = LocalDateTime.now();
        return this;
    }

    public TestResult error(String errorDetail) {
        this.state = TestState.ERROR;
        this.errorDetail = errorDetail;
        this.endTime = LocalDateTime.now();
        this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        return this;
    }

    public TestResult withMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }

    // Getters
    public String getToolName() { return toolName; }
    public String getInput() { return input; }
    public TestState getState() { return state; }
    public String getMessage() { return message; }
    public String getErrorDetail() { return errorDetail; }
    public long getDurationMs() { return durationMs; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public Map<String, Object> getMetadata() { return metadata; }

    public boolean isSuccess() { return state == TestState.SUCCESS; }
    public boolean isFailed() { return state == TestState.FAILED; }
    public boolean isSkipped() { return state == TestState.SKIPPED; }
    public boolean isError() { return state == TestState.ERROR; }

    @Override
    public String toString() {
        return String.format("[%s] %s (%dms)", state.getIcon(), toolName, durationMs);
    }
}

package com.jwcode.core.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.format.DateTimeFormatter;

/**
 * JSON 格式报告生成器
 */
public class JsonFormatter implements ReportFormatter {

    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter DATE_FORMAT = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public JsonFormatter() {
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public String format(TestReport report) {
        try {
            ReportData data = new ReportData(report);
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    @Override
    public String getFileExtension() {
        return ".json";
    }

    /**
     * 报告数据模型
     */
    public static class ReportData {
        public String testSuiteName;
        public String generatedAt;
        public String overallState;
        public Statistics statistics;
        public java.util.List<TestResultData> results;
        public java.util.List<String> errors;
        public java.util.List<String> warnings;

        public ReportData(TestReport report) {
            this.testSuiteName = report.getTestSuiteName();
            this.generatedAt = report.getGeneratedAt().format(DATE_FORMAT);
            this.overallState = report.getOverallState().name();
            this.statistics = new Statistics(report);
            this.results = report.getResults().stream()
                .map(TestResultData::new)
                .collect(java.util.stream.Collectors.toList());
            this.errors = report.getErrors();
            this.warnings = report.getWarnings();
        }
    }

    /**
     * 统计数据模型
     */
    public static class Statistics {
        public int total;
        public int success;
        public int failed;
        public int skipped;
        public int error;
        public double successRate;
        public long totalDurationMs;

        public Statistics(TestReport report) {
            this.total = report.getTotalCount();
            this.success = (int) report.getSuccessCount();
            this.failed = (int) report.getFailedCount();
            this.skipped = (int) report.getSkippedCount();
            this.error = (int) report.getErrorCount();
            this.successRate = report.getSuccessRate();
            this.totalDurationMs = report.getTotalDurationMs();
        }
    }

    /**
     * 测试结果数据模型
     */
    public static class TestResultData {
        public String toolName;
        public String state;
        public String message;
        public String errorDetail;
        public long durationMs;
        public String startTime;
        public String endTime;

        public TestResultData(TestResult result) {
            this.toolName = result.getToolName();
            this.state = result.getState().name();
            this.message = result.getMessage();
            this.errorDetail = result.getErrorDetail();
            this.durationMs = result.getDurationMs();
            this.startTime = result.getStartTime().format(DATE_FORMAT);
            this.endTime = result.getEndTime() != null ? 
                result.getEndTime().format(DATE_FORMAT) : null;
        }
    }
}

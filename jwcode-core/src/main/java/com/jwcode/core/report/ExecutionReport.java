package com.jwcode.core.report;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ExecutionReport — AI 任务执行报告。
 *
 * <p>记录任务执行的完整信息，包括子任务详情、变更列表、测试结果、代码审查发现等。
 * 支持导出为 Markdown、HTML、JSON 格式。</p>
 */
public class ExecutionReport {

    // ==================== 核心字段 ====================

    private final String taskId;
    private final String taskGoal;
    private final Status status;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Duration duration;

    private final List<SubTaskResult> subTasks;
    private final List<FileChange> changes;
    private final List<TestResult> testResults;
    private final List<ReviewFinding> reviewFindings;
    private final List<TimelineEntry> timeline;
    private final List<String> recommendations;

    // ==================== 枚举 ====================

    public enum Status {
        SUCCESS("✅ Success"),
        FAILED("❌ Failed"),
        PARTIAL("⚠️ Partial");

        private final String display;
        Status(String display) { this.display = display; }
        public String getDisplay() { return display; }
    }

    // ==================== 内部数据类 ====================

    public static class SubTaskResult {
        private final String taskId;
        private final String taskType;
        private final String description;
        private final Status status;
        private final String output;
        private final Duration duration;

        public SubTaskResult(String taskId, String taskType, String description,
                            Status status, String output, Duration duration) {
            this.taskId = taskId;
            this.taskType = taskType;
            this.description = description;
            this.status = status;
            this.output = output;
            this.duration = duration;
        }

        public String getTaskId() { return taskId; }
        public String getTaskType() { return taskType; }
        public String getDescription() { return description; }
        public Status getStatus() { return status; }
        public String getOutput() { return output; }
        public Duration getDuration() { return duration; }
    }

    public static class FileChange {
        public enum Operation { ADDED, MODIFIED, DELETED }

        private final Operation operation;
        private final String filePath;
        private final int linesAdded;
        private final int linesDeleted;

        public FileChange(Operation operation, String filePath, int linesAdded, int linesDeleted) {
            this.operation = operation;
            this.filePath = filePath;
            this.linesAdded = linesAdded;
            this.linesDeleted = linesDeleted;
        }

        public Operation getOperation() { return operation; }
        public String getFilePath() { return filePath; }
        public int getLinesAdded() { return linesAdded; }
        public int getLinesDeleted() { return linesDeleted; }
    }

    public static class TestResult {
        private final String testName;
        private final boolean passed;
        private final Duration duration;
        private final String errorMessage;

        public TestResult(String testName, boolean passed, Duration duration, String errorMessage) {
            this.testName = testName;
            this.passed = passed;
            this.duration = duration;
            this.errorMessage = errorMessage;
        }

        public String getTestName() { return testName; }
        public boolean isPassed() { return passed; }
        public Duration getDuration() { return duration; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class ReviewFinding {
        public enum Severity { CRITICAL, MEDIUM, SUGGESTION }

        private final Severity severity;
        private final String filePath;
        private final int lineNumber;
        private final String description;
        private final String suggestion;
        private final boolean resolved;

        public ReviewFinding(Severity severity, String filePath, int lineNumber,
                            String description, String suggestion, boolean resolved) {
            this.severity = severity;
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.description = description;
            this.suggestion = suggestion;
            this.resolved = resolved;
        }

        public Severity getSeverity() { return severity; }
        public String getFilePath() { return filePath; }
        public int getLineNumber() { return lineNumber; }
        public String getDescription() { return description; }
        public String getSuggestion() { return suggestion; }
        public boolean isResolved() { return resolved; }
    }

    public static class TimelineEntry {
        private final String phase;
        private final Duration duration;
        private final String agent;

        public TimelineEntry(String phase, Duration duration, String agent) {
            this.phase = phase;
            this.duration = duration;
            this.agent = agent;
        }

        public String getPhase() { return phase; }
        public Duration getDuration() { return duration; }
        public String getAgent() { return agent; }
    }

    // ==================== 构建器 ====================

    private ExecutionReport(Builder builder) {
        this.taskId = builder.taskId;
        this.taskGoal = builder.taskGoal;
        this.status = builder.status;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.duration = builder.duration;
        this.subTasks = Collections.unmodifiableList(builder.subTasks);
        this.changes = Collections.unmodifiableList(builder.changes);
        this.testResults = Collections.unmodifiableList(builder.testResults);
        this.reviewFindings = Collections.unmodifiableList(builder.reviewFindings);
        this.timeline = Collections.unmodifiableList(builder.timeline);
        this.recommendations = Collections.unmodifiableList(builder.recommendations);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String taskId;
        private String taskGoal;
        private Status status = Status.SUCCESS;
        private LocalDateTime startTime = LocalDateTime.now();
        private LocalDateTime endTime = LocalDateTime.now();
        private Duration duration = Duration.ZERO;

        private final List<SubTaskResult> subTasks = new ArrayList<>();
        private final List<FileChange> changes = new ArrayList<>();
        private final List<TestResult> testResults = new ArrayList<>();
        private final List<ReviewFinding> reviewFindings = new ArrayList<>();
        private final List<TimelineEntry> timeline = new ArrayList<>();
        private final List<String> recommendations = new ArrayList<>();

        public Builder taskId(String taskId) { this.taskId = taskId; return this; }
        public Builder taskGoal(String taskGoal) { this.taskGoal = taskGoal; return this; }
        public Builder status(Status status) { this.status = status; return this; }
        public Builder startTime(LocalDateTime startTime) { this.startTime = startTime; return this; }
        public Builder endTime(LocalDateTime endTime) { this.endTime = endTime; return this; }
        public Builder duration(Duration duration) { this.duration = duration; return this; }

        public Builder addSubTask(SubTaskResult subTask) {
            this.subTasks.add(subTask); return this;
        }

        public Builder addChange(FileChange change) {
            this.changes.add(change); return this;
        }

        public Builder addTestResult(TestResult testResult) {
            this.testResults.add(testResult); return this;
        }

        public Builder addReviewFinding(ReviewFinding finding) {
            this.reviewFindings.add(finding); return this;
        }

        public Builder addTimelineEntry(TimelineEntry entry) {
            this.timeline.add(entry); return this;
        }

        public Builder addRecommendation(String recommendation) {
            this.recommendations.add(recommendation); return this;
        }

        public ExecutionReport build() {
            return new ExecutionReport(this);
        }
    }

    // ==================== 导出方法 ====================

    /**
     * 导出为 Markdown 格式
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();

        sb.append("# 🤖 AI Task Execution Report\n\n");

        // 执行摘要
        sb.append("## 📋 Executive Summary\n");
        sb.append("| Field | Value |\n");
        sb.append("|-------|-------|\n");
        sb.append("| Task Goal | ").append(escapeMd(taskGoal)).append(" |\n");
        sb.append("| Status | ").append(status.getDisplay()).append(" |\n");
        sb.append("| Total Duration | ").append(formatDuration(duration)).append(" |\n");
        sb.append("| Sub-tasks | ").append(countCompleted()).append("/").append(subTasks.size()).append(" |\n");

        long totalTests = testResults.size();
        long passedTests = testResults.stream().filter(TestResult::isPassed).count();
        sb.append("| Test Pass Rate | ").append(totalTests > 0 ? (passedTests * 100 / totalTests) + "%" : "N/A").append(" |\n");
        sb.append("\n");

        // 变更列表
        if (!changes.isEmpty()) {
            sb.append("## 📂 Change List\n");
            sb.append("| Operation | File | Lines |\n");
            sb.append("|-----------|------|-------|\n");
            for (FileChange change : changes) {
                String op = change.getOperation() == FileChange.Operation.ADDED ? "✅ Added" :
                           change.getOperation() == FileChange.Operation.MODIFIED ? "✅ Modified" : "❌ Deleted";
                String lines = change.getLinesAdded() > 0 || change.getLinesDeleted() > 0 ?
                    "+" + change.getLinesAdded() + "/-" + change.getLinesDeleted() : "-";
                sb.append("| ").append(op).append(" | ").append(change.getFilePath()).append(" | ").append(lines).append(" |\n");
            }
            sb.append("\n");
        }

        // 测试结果
        if (!testResults.isEmpty()) {
            sb.append("## 🧪 Test Results\n");
            sb.append("| Test Case | Status | Duration |\n");
            sb.append("|-----------|--------|----------|\n");
            for (TestResult tr : testResults) {
                String statusIcon = tr.isPassed() ? "✅ Pass" : "❌ Fail";
                sb.append("| ").append(tr.getTestName()).append(" | ").append(statusIcon);
                sb.append(" | ").append(formatDuration(tr.getDuration())).append(" |\n");
            }
            sb.append("\n");
        }

        // 代码审查
        if (!reviewFindings.isEmpty()) {
            sb.append("## 🔍 Code Review\n");
            sb.append("| Severity | Count | Status |\n");
            sb.append("|----------|-------|--------|\n");

            long criticalCount = reviewFindings.stream().filter(f -> f.getSeverity() == ReviewFinding.Severity.CRITICAL).count();
            long mediumCount = reviewFindings.stream().filter(f -> f.getSeverity() == ReviewFinding.Severity.MEDIUM).count();
            long suggestionCount = reviewFindings.stream().filter(f -> f.getSeverity() == ReviewFinding.Severity.SUGGESTION).count();

            sb.append("| 🔴 Critical | ").append(criticalCount).append(" | ").append(criticalCount > 0 ? "Open" : "None").append(" |\n");
            sb.append("| 🟡 Medium | ").append(mediumCount).append(" | ").append(mediumCount > 0 ? "Open" : "None").append(" |\n");
            sb.append("| 🟢 Suggestion | ").append(suggestionCount).append(" | ").append(suggestionCount > 0 ? "Open" : "None").append(" |\n");
            sb.append("\n");

            // 详细发现
            for (ReviewFinding finding : reviewFindings) {
                String sev = finding.getSeverity() == ReviewFinding.Severity.CRITICAL ? "🔴" :
                            finding.getSeverity() == ReviewFinding.Severity.MEDIUM ? "🟡" : "🟢";
                sb.append("- ").append(sev).append(" `").append(finding.getFilePath()).append(":").append(finding.getLineNumber()).append("` ");
                sb.append(finding.getDescription()).append("\n");
                if (finding.getSuggestion() != null && !finding.getSuggestion().isEmpty()) {
                    sb.append("  - Suggestion: ").append(finding.getSuggestion()).append("\n");
                }
            }
            sb.append("\n");
        }

        // 执行时间线
        if (!timeline.isEmpty()) {
            sb.append("## ⏱️ Execution Timeline\n");
            sb.append("| Phase | Duration | Agent |\n");
            sb.append("|-------|----------|-------|\n");
            for (TimelineEntry entry : timeline) {
                sb.append("| ").append(entry.getPhase()).append(" | ");
                sb.append(formatDuration(entry.getDuration())).append(" | ");
                sb.append(entry.getAgent()).append(" |\n");
            }
            sb.append("\n");
        }

        // 子任务详情
        if (!subTasks.isEmpty()) {
            sb.append("## 📋 Sub-task Details\n");
            for (SubTaskResult st : subTasks) {
                String statusIcon = st.getStatus() == Status.SUCCESS ? "✅" :
                                   st.getStatus() == Status.FAILED ? "❌" : "⚠️";
                sb.append("### ").append(statusIcon).append(" ").append(st.getTaskId()).append(" (").append(st.getTaskType()).append(")\n");
                sb.append("- **Description**: ").append(escapeMd(st.getDescription())).append("\n");
                sb.append("- **Status**: ").append(st.getStatus().getDisplay()).append("\n");
                sb.append("- **Duration**: ").append(formatDuration(st.getDuration())).append("\n");
                if (st.getOutput() != null && !st.getOutput().isEmpty()) {
                    sb.append("- **Output**:\n```\n").append(st.getOutput()).append("\n```\n");
                }
                sb.append("\n");
            }
        }

        // 建议
        if (!recommendations.isEmpty()) {
            sb.append("## 💡 Recommendations\n");
            for (int i = 0; i < recommendations.size(); i++) {
                sb.append(i + 1).append(". ").append(recommendations.get(i)).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 导出为 JSON 格式
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"taskId\":\"").append(escapeJson(taskId)).append("\",");
        sb.append("\"taskGoal\":\"").append(escapeJson(taskGoal)).append("\",");
        sb.append("\"status\":\"").append(status.name()).append("\",");
        sb.append("\"durationMs\":").append(duration.toMillis()).append(",");
        sb.append("\"subTasks\":").append(subTasksToJson()).append(",");
        sb.append("\"changes\":").append(changesToJson()).append(",");
        sb.append("\"testResults\":").append(testResultsToJson()).append(",");
        sb.append("\"reviewFindings\":").append(reviewFindingsToJson()).append(",");
        sb.append("\"timeline\":").append(timelineToJson()).append(",");
        sb.append("\"recommendations\":").append(listToJson(recommendations));
        sb.append("}");
        return sb.toString();
    }

    /**
     * 导出为 HTML 格式
     */
    public String toHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
        sb.append("<title>AI Task Execution Report</title>");
        sb.append("<style>");
        sb.append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;max-width:960px;margin:0 auto;padding:20px;color:#333;line-height:1.6}");
        sb.append("h1{color:#1a1a2e;border-bottom:2px solid #e94560;padding-bottom:10px}");
        sb.append("h2{color:#16213e;margin-top:30px}");
        sb.append("table{border-collapse:collapse;width:100%;margin:10px 0}");
        sb.append("th,td{border:1px solid #ddd;padding:8px 12px;text-align:left}");
        sb.append("th{background-color:#f5f5f5;font-weight:600}");
        sb.append("tr:nth-child(even){background-color:#fafafa}");
        sb.append(".success{color:#27ae60}.fail{color:#e74c3c}.partial{color:#f39c12}");
        sb.append("code{background:#f4f4f4;padding:2px 6px;border-radius:3px;font-size:0.9em}");
        sb.append("pre{background:#f4f4f4;padding:12px;border-radius:4px;overflow-x:auto}");
        sb.append("</style></head><body>");
        // 将 Markdown 转换为简单的 HTML 结构
        String md = toMarkdown();
        String html = convertMarkdownToHtml(md);
        sb.append(html);
        sb.append("</body></html>");
        return sb.toString();
    }

    /**
     * 将 Markdown 转换为简单的 HTML
     */
    private String convertMarkdownToHtml(String md) {
        if (md == null || md.isEmpty()) return "";
        String[] lines = md.split("\n");
        StringBuilder html = new StringBuilder();
        boolean inTable = false;
        boolean inCodeBlock = false;

        for (String line : lines) {
            // 代码块
            if (line.startsWith("```")) {
                if (inCodeBlock) {
                    html.append("</pre>\n");
                    inCodeBlock = false;
                } else {
                    html.append("<pre>");
                    inCodeBlock = true;
                }
                continue;
            }
            if (inCodeBlock) {
                html.append(escapeHtml(line)).append("\n");
                continue;
            }

            // 标题
            if (line.startsWith("# ")) {
                html.append("<h1>").append(escapeHtml(line.substring(2))).append("</h1>\n");
            } else if (line.startsWith("## ")) {
                html.append("<h2>").append(escapeHtml(line.substring(3))).append("</h2>\n");
            } else if (line.startsWith("### ")) {
                html.append("<h3>").append(escapeHtml(line.substring(4))).append("</h3>\n");
            }
            // 表格
            else if (line.startsWith("|")) {
                if (!inTable) {
                    html.append("<table>\n");
                    inTable = true;
                }
                if (line.contains("---")) {
                    // 分隔行，跳过
                    continue;
                }
                html.append("<tr>");
                String[] cells = line.split("\\|");
                for (String cell : cells) {
                    String trimmed = cell.trim();
                    if (!trimmed.isEmpty()) {
                        html.append("<td>").append(escapeHtml(trimmed)).append("</td>");
                    }
                }
                html.append("</tr>\n");
            } else {
                if (inTable) {
                    html.append("</table>\n");
                    inTable = false;
                }
                // 普通段落
                if (!line.trim().isEmpty()) {
                    html.append("<p>").append(escapeHtml(line)).append("</p>\n");
                }
            }
        }
        if (inTable) html.append("</table>\n");
        if (inCodeBlock) html.append("</pre>\n");
        return html.toString();
    }

    private String escapeHtml(String str) {
        if (str == null) return "";
        StringBuilder sb = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '&':  sb.append("&").append("amp;"); break;
                case '<':  sb.append("&").append("lt;"); break;
                case '>':  sb.append("&").append("gt;"); break;
                case '"':  sb.append("&").append("quot;"); break;
                default:   sb.append(c); break;
            }
        }
        return sb.toString();
    }

    // ==================== 辅助方法 ====================

    private long countCompleted() {
        return subTasks.stream().filter(st -> st.getStatus() == Status.SUCCESS).count();
    }

    private String formatDuration(Duration d) {
        long millis = d.toMillis();
        if (millis < 1000) return millis + "ms";
        if (millis < 60000) return (millis / 1000) + "s";
        long minutes = millis / 60000;
        long seconds = (millis % 60000) / 1000;
        return minutes + "m " + seconds + "s";
    }

    private String escapeMd(String str) {
        if (str == null) return "";
        return str.replace("|", "\\|").replace("\n", " ").replace("\r", "");
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private String subTasksToJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (SubTaskResult st : subTasks) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{");
            sb.append("\"taskId\":\"").append(escapeJson(st.getTaskId())).append("\",");
            sb.append("\"taskType\":\"").append(escapeJson(st.getTaskType())).append("\",");
            sb.append("\"description\":\"").append(escapeJson(st.getDescription())).append("\",");
            sb.append("\"status\":\"").append(st.getStatus().name()).append("\",");
            sb.append("\"durationMs\":").append(st.getDuration().toMillis());
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String changesToJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (FileChange fc : changes) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{");
            sb.append("\"operation\":\"").append(fc.getOperation().name()).append("\",");
            sb.append("\"filePath\":\"").append(escapeJson(fc.getFilePath())).append("\",");
            sb.append("\"linesAdded\":").append(fc.getLinesAdded()).append(",");
            sb.append("\"linesDeleted\":").append(fc.getLinesDeleted());
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String testResultsToJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (TestResult tr : testResults) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{");
            sb.append("\"testName\":\"").append(escapeJson(tr.getTestName())).append("\",");
            sb.append("\"passed\":").append(tr.isPassed()).append(",");
            sb.append("\"durationMs\":").append(tr.getDuration().toMillis());
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String reviewFindingsToJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (ReviewFinding rf : reviewFindings) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{");
            sb.append("\"severity\":\"").append(rf.getSeverity().name()).append("\",");
            sb.append("\"filePath\":\"").append(escapeJson(rf.getFilePath())).append("\",");
            sb.append("\"lineNumber\":").append(rf.getLineNumber()).append(",");
            sb.append("\"description\":\"").append(escapeJson(rf.getDescription())).append("\",");
            sb.append("\"resolved\":").append(rf.isResolved());
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String timelineToJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (TimelineEntry te : timeline) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{");
            sb.append("\"phase\":\"").append(escapeJson(te.getPhase())).append("\",");
            sb.append("\"durationMs\":").append(te.getDuration().toMillis()).append(",");
            sb.append("\"agent\":\"").append(escapeJson(te.getAgent())).append("\"");
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String listToJson(List<String> list) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String item : list) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(item)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    // ==================== Getters ====================

    public String getTaskId() { return taskId; }
    public String getTaskGoal() { return taskGoal; }
    public Status getStatus() { return status; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public Duration getDuration() { return duration; }
    public List<SubTaskResult> getSubTasks() { return subTasks; }
    public List<FileChange> getChanges() { return changes; }
    public List<TestResult> getTestResults() { return testResults; }
    public List<ReviewFinding> getReviewFindings() { return reviewFindings; }
    public List<TimelineEntry> getTimeline() { return timeline; }
    public List<String> getRecommendations() { return recommendations; }
}

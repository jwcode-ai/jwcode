package com.jwcode.core.eval;

import com.jwcode.core.eval.EvalTask.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * EvalReportGenerator — 评测报告生成器。
 *
 * <p>支持三种输出格式：</p>
 * <ul>
 *   <li>Markdown — 人类可读的详细报告</li>
 *   <li>JSON — 机器可读的结构化数据（用于趋势分析）</li>
 *   <li>控制台 — 彩色摘要输出</li>
 * </ul>
 */
public class EvalReportGenerator {

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final ObjectMapper jsonMapper;

    public EvalReportGenerator() {
        this.jsonMapper = new ObjectMapper();
        this.jsonMapper.registerModule(new JavaTimeModule());
        this.jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 生成控制台彩色摘要。
     */
    public String generateConsoleSummary(EvalReport report) {
        StringBuilder sb = new StringBuilder();
        Summary summary = report.getSummary();

        sb.append("\n");
        sb.append("╔══════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║               JWCode 能力评测报告                                  ║\n");
        sb.append("╠══════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ 套件: %-55s║\n", report.getSuiteName()));
        sb.append(String.format("║ 时间: %-55s║\n", report.getTimestamp()));
        sb.append(String.format("║ 耗时: %-55s║\n", formatDuration(report.getTotalDurationMs())));
        sb.append("╠══════════════════════════════════════════════════════════════════════╣\n");

        // 汇总
        sb.append(String.format("║ 📊 汇总: %d 总任务 | ✅ %d 通过 | ❌ %d 失败 | ⏭ %d 跳过     ║\n",
            summary.getTotal(), summary.getPassed(), summary.getFailed(), summary.getSkipped()));
        sb.append(String.format("║ 通过率: %5.1f%% | 客观均分: %5.1f",
            summary.getPassRate(), summary.getAvgObjectiveScore()));
        if (summary.getAvgAiScore() != null) {
            sb.append(String.format(" | AI均分: %.1f", summary.getAvgAiScore()));
        }
        sb.append("             ║\n");
        sb.append("╠══════════════════════════════════════════════════════════════════════╣\n");

        // 按难度
        sb.append("║ 📈 按难度:                                                         ║\n");
        for (Difficulty diff : Difficulty.values()) {
            CategoryResult cr = report.getByDifficulty().get(diff);
            if (cr != null) {
                String icon = cr.getPassRate() >= 80 ? "🟢" : cr.getPassRate() >= 50 ? "🟡" : "🔴";
                sb.append(String.format("║   %s %-8s %d/%d 通过 (%.0f%%)                               ║\n",
                    icon, diff.getLabel(), cr.getPassed(), cr.getTotal(), cr.getPassRate()));
            }
        }
        sb.append("╠══════════════════════════════════════════════════════════════════════╣\n");

        // 按能力维度
        sb.append("║ 🎯 按能力维度:                                                     ║\n");
        for (Map.Entry<String, CategoryResult> entry : report.getByCapability().entrySet()) {
            CategoryResult cr = entry.getValue();
            String icon = cr.getPassRate() >= 80 ? "🟢" : cr.getPassRate() >= 50 ? "🟡" : "🔴";
            sb.append(String.format("║   %s %-20s %d/%d 通过 (%.0f%%)                         ║\n",
                icon, entry.getKey(), cr.getPassed(), cr.getTotal(), cr.getPassRate()));
        }
        sb.append("╠══════════════════════════════════════════════════════════════════════╣\n");

        // 详细结果
        sb.append("║ 📋 详细结果:                                                       ║\n");
        for (EvalResult result : report.getResults()) {
            String statusIcon;
            if (result.isPassed()) {
                statusIcon = "✅";
            } else if (result.getFailureReason() != null && result.getFailureReason().contains("跳过")) {
                statusIcon = "⏭";
            } else {
                statusIcon = "❌";
            }
            sb.append(String.format("║   %s %-12s %-30s %s\n",
                statusIcon, result.getTaskId(), truncate(result.getTrace() != null ? "..." : "", 30),
                formatDuration(result.getDurationMs())));
        }
        sb.append("╚══════════════════════════════════════════════════════════════════════╝\n");

        // 与上次对比
        if (report.getComparisonPreviousRun() != null) {
            sb.append("\n📈 趋势对比:\n");
            sb.append(report.getComparisonPreviousRun()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 生成 Markdown 详细报告。
     */
    public String generateMarkdown(EvalReport report) {
        StringBuilder sb = new StringBuilder();
        Summary summary = report.getSummary();

        sb.append("# JWCode 能力评测报告\n\n");
        sb.append("| 元数据 | 值 |\n");
        sb.append("|--------|-----|\n");
        sb.append("| 套件名称 | ").append(report.getSuiteName()).append(" |\n");
        sb.append("| 评测时间 | ").append(report.getTimestamp()).append(" |\n");
        sb.append("| 总耗时 | ").append(formatDuration(report.getTotalDurationMs())).append(" |\n\n");

        // 汇总
        sb.append("## 📊 汇总\n\n");
        sb.append("| 指标 | 数值 |\n");
        sb.append("|------|------|\n");
        sb.append("| 总任务数 | ").append(summary.getTotal()).append(" |\n");
        sb.append("| ✅ 通过 | ").append(summary.getPassed()).append(" |\n");
        sb.append("| ❌ 失败 | ").append(summary.getFailed()).append(" |\n");
        sb.append("| ⏭ 跳过 | ").append(summary.getSkipped()).append(" |\n");
        sb.append("| 通过率 | ").append(String.format("%.1f%%", summary.getPassRate())).append(" |\n");
        sb.append("| 客观验收均分 | ").append(String.format("%.1f", summary.getAvgObjectiveScore())).append(" |\n");
        if (summary.getAvgAiScore() != null) {
            sb.append("| AI 评审均分 | ").append(String.format("%.1f", summary.getAvgAiScore())).append(" |\n");
        }
        sb.append("\n");

        // 按难度
        sb.append("## 📈 按难度分类\n\n");
        sb.append("| 难度 | 通过率 | 详情 |\n");
        sb.append("|------|--------|------|\n");
        for (Difficulty diff : Difficulty.values()) {
            CategoryResult cr = report.getByDifficulty().get(diff);
            if (cr != null) {
                String icon = cr.getPassRate() >= 80 ? "🟢" : cr.getPassRate() >= 50 ? "🟡" : "🔴";
                sb.append(String.format("| %s %s | %.0f%% | %d/%d 通过 |\n",
                    icon, diff.getLabel(), cr.getPassRate(), cr.getPassed(), cr.getTotal()));
            }
        }
        sb.append("\n");

        // 按能力维度
        sb.append("## 🎯 按能力维度\n\n");
        sb.append("| 维度 | 通过率 | 详情 |\n");
        sb.append("|------|--------|------|\n");
        for (Map.Entry<String, CategoryResult> entry : report.getByCapability().entrySet()) {
            CategoryResult cr = entry.getValue();
            String icon = cr.getPassRate() >= 80 ? "🟢" : cr.getPassRate() >= 50 ? "🟡" : "🔴";
            sb.append(String.format("| %s %s | %.0f%% | %d/%d 通过 |\n",
                icon, entry.getKey(), cr.getPassRate(), cr.getPassed(), cr.getTotal()));
        }
        sb.append("\n");

        // 详细结果
        sb.append("## 📋 详细结果\n\n");
        for (EvalResult result : report.getResults()) {
            String icon = result.isPassed() ? "✅" : "❌";
            sb.append(String.format("### %s %s\n\n", icon, result.getTaskId()));
            sb.append(String.format("| 字段 | 值 |\n"));
            sb.append("|------|-----|\n");
            sb.append(String.format("| 状态 | %s |\n", result.isPassed() ? "✅ 通过" : "❌ 失败"));
            sb.append(String.format("| 耗时 | %s |\n", formatDuration(result.getDurationMs())));
            sb.append(String.format("| 客观得分 | %.1f/100 |\n", result.getObjectiveScore()));
            if (result.getAiScore() != null) {
                sb.append(String.format("| AI 评分 | %.1f/10 |\n", result.getAiScore()));
            }
            if (result.getWeightedFinalScore() != null) {
                sb.append(String.format("| 综合得分 | %.1f |\n", result.getWeightedFinalScore()));
            }
            if (result.getFailureReason() != null) {
                sb.append(String.format("| 失败原因 | %s |\n", result.getFailureReason()));
            }
            sb.append("\n");

            // 检查结果
            if (!result.getCheckResults().isEmpty()) {
                sb.append("#### 验收检查\n\n");
                sb.append("| 检查项 | 结果 | 详情 |\n");
                sb.append("|--------|------|------|\n");
                for (CheckResult cr : result.getCheckResults()) {
                    String checkIcon = cr.isPassed() ? "✅" : "❌";
                    sb.append(String.format("| %s %s | %s | %s |\n",
                        checkIcon, cr.getDescription(), cr.isPassed() ? "通过" : "失败",
                        cr.getDetail() != null ? cr.getDetail().replace("\n", "<br>") : ""));
                }
                sb.append("\n");
            }

            // AI 反馈
            if (result.getAiFeedback() != null) {
                sb.append("#### AI 评审反馈\n\n");
                sb.append("```\n").append(result.getAiFeedback()).append("\n```\n\n");
            }

            // 执行轨迹
            if (result.getTrace() != null) {
                ExecutionTrace trace = result.getTrace();
                sb.append("#### 执行轨迹\n\n");
                sb.append(String.format("- 总步骤数: %d\n", trace.getTotalSteps()));
                sb.append(String.format("- 工具调用数: %d\n", trace.getToolCalls().size()));
                sb.append(String.format("- 总重试次数: %d\n", trace.getRetryCount()));
                sb.append(String.format("- 总耗时: %s\n", formatDuration(trace.getTotalDurationMs())));
                if (!trace.getErrors().isEmpty()) {
                    sb.append("- 异常/错误列表:\n");
                    for (String err : trace.getErrors()) {
                        sb.append("  - ").append(err).append("\n");
                    }
                }
                if (!trace.getToolCalls().isEmpty()) {
                    sb.append("- 工具调用明细:\n\n");
                    sb.append("| # | 工具 | 耗时 | 状态 | 入参摘要 | 出参摘要 |\n");
                    sb.append("|---|------|------|------|----------|----------|\n");
                    int idx = 0;
                    for (ToolInvocation inv : trace.getToolCalls()) {
                        idx++;
                        String invIcon = inv.isSuccess() ? "✅" : "❌";
                        String duration = inv.getDurationMs() > 0 ? inv.getDurationMs() + "ms" : "-";
                        String inputSum = inv.getInputSummary() != null
                            ? inv.getInputSummary().replace("\n", " ").substring(0, Math.min(80, inv.getInputSummary().length()))
                            : "-";
                        String outputSum = inv.getOutputSummary() != null
                            ? inv.getOutputSummary().replace("\n", " ").substring(0, Math.min(80, inv.getOutputSummary().length()))
                            : "-";
                        sb.append(String.format("| %d | %s %s | %s | %s | `%s` | `%s` |\n",
                            idx, invIcon, inv.getToolName(), duration, invIcon,
                            inputSum, outputSum));
                        if (inv.getErrorMessage() != null) {
                            sb.append("| | | | | ⚠ ").append(inv.getErrorMessage()).append(" |\n");
                        }
                    }
                }
                sb.append("\n");
            }
        }

        // 趋势对比
        if (report.getComparisonPreviousRun() != null) {
            sb.append("## 📈 趋势对比\n\n");
            sb.append(report.getComparisonPreviousRun()).append("\n");
        }

        sb.append("---\n");
        sb.append(String.format("*报告生成时间: %s*\n", report.getTimestamp()));

        return sb.toString();
    }

    /**
     * 生成 JSON 格式报告（用于持久化和趋势分析）。
     */
    public String generateJson(EvalReport report) {
        try {
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        } catch (IOException e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 保存报告到文件。
     *
     * @param report 评测报告
     * @param outputDir 输出目录
     * @param suiteName 套件名称
     * @return 保存的文件路径列表
     */
    public List<String> saveToFiles(EvalReport report, String outputDir, String suiteName) {
        List<String> savedFiles = new ArrayList<>();
        try {
            String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP_FMT);
            Path dir = Paths.get(outputDir);
            Files.createDirectories(dir);

            // 对生成的字符串做防御性清理，移除可能导致 UTF-8 编码失败的非法 surrogate 字符
            String markdown = sanitizeForFile(generateMarkdown(report));
            String json = sanitizeForFile(generateJson(report));

            // Markdown 报告
            String mdFileName = suiteName + "-" + timestamp + ".md";
            Path mdPath = dir.resolve(mdFileName);
            Files.writeString(mdPath, markdown, StandardCharsets.UTF_8);
            savedFiles.add(mdPath.toString());
            System.out.println("  📄 Markdown 报告: " + mdPath);

            // JSON 数据（用于趋势分析）
            String jsonFileName = suiteName + "-" + timestamp + ".json";
            Path jsonPath = dir.resolve(jsonFileName);
            Files.writeString(jsonPath, json, StandardCharsets.UTF_8);
            savedFiles.add(jsonPath.toString());
            System.out.println("  📄 JSON 数据: " + jsonPath);

            // 最新结果链接
            Path latestJson = dir.resolve(suiteName + "-latest.json");
            Files.writeString(latestJson, json, StandardCharsets.UTF_8);
            savedFiles.add(latestJson.toString());

            // 摘要到控制台
            System.out.println(generateConsoleSummary(report));

        } catch (IOException e) {
            System.err.println("保存评测报告失败: " + e.getMessage());
            // 输出额外诊断信息帮助定位问题
            System.err.println("  输出目录: " + outputDir);
            System.err.println("  套件名称: " + suiteName);
            Throwable cause = e.getCause();
            if (cause != null) {
                System.err.println("  根因: " + cause.getClass().getName() + ": " + cause.getMessage());
            }
        } catch (Exception e) {
            System.err.println("保存评测报告异常: " + e.getClass().getName() + ": " + e.getMessage());
        }
        return savedFiles;
    }

    /**
     * 清理字符串中的非法 surrogate 字符，确保可以安全地以 UTF-8 编码写入文件。
     * 移除孤立的 high/low surrogate，保留合法的 surrogate pair（如 emoji）。
     */
    private static String sanitizeForFile(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                // high surrogate 后面必须跟 low surrogate
                if (i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                    sb.append(c);       // 保留合法的 surrogate pair
                    sb.append(s.charAt(i + 1));
                    i++;
                }
                // 孤立的 high surrogate → 跳过
            } else if (Character.isLowSurrogate(c)) {
                // 孤立的 low surrogate → 跳过
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ==================== 工具方法 ====================

    static String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return String.format("%.1fs", ms / 1000.0);
        long minutes = ms / 60000;
        long seconds = (ms % 60000) / 1000;
        return minutes + "m " + seconds + "s";
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        // surrogate-safe 截断
        int adjustedEnd = maxLen;
        if (adjustedEnd > 0 && adjustedEnd <= s.length()) {
            char c = s.charAt(adjustedEnd - 1);
            if (Character.isHighSurrogate(c)) {
                adjustedEnd--;
            }
        }
        return s.substring(0, adjustedEnd) + "...";
    }
}

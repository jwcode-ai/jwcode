package com.jwcode.core.report;

import java.time.format.DateTimeFormatter;

/**
 * Markdown 格式报告生成器
 */
public class MarkdownFormatter implements ReportFormatter {

    private static final DateTimeFormatter DATE_FORMAT = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String format(TestReport report) {
        StringBuilder md = new StringBuilder();
        
        // 标题
        md.append("# ").append(report.getTestSuiteName()).append("\n\n");
        
        // 概览
        md.append("## 📊 概览\n\n");
        md.append("| 指标 | 值 |\n");
        md.append("|------|-----|\n");
        md.append(String.format("| 生成时间 | %s |\n", 
            report.getGeneratedAt().format(DATE_FORMAT)));
        md.append(String.format("| 总体状态 | %s %s |\n", 
            report.getOverallState().getIcon(), 
            report.getOverallState().getDescription()));
        md.append(String.format("| 总计测试 | %d |\n", report.getTotalCount()));
        md.append(String.format("| ✅ 成功 | %d |\n", report.getSuccessCount()));
        md.append(String.format("| ❌ 失败 | %d |\n", report.getFailedCount()));
        md.append(String.format("| ⏭️ 跳过 | %d |\n", report.getSkippedCount()));
        md.append(String.format("| 🚫 错误 | %d |\n", report.getErrorCount()));
        md.append(String.format("| 成功率 | %.1f%% |\n", report.getSuccessRate()));
        md.append(String.format("| 总耗时 | %d ms |\n\n", report.getTotalDurationMs()));
        
        // 成功测试
        if (!report.getSuccessfulTests().isEmpty()) {
            md.append("## ✅ 成功测试\n\n");
            for (TestResult result : report.getSuccessfulTests()) {
                md.append(String.format("- **%s**: %s (%d ms)\n", 
                    result.getToolName(), 
                    result.getMessage() != null ? result.getMessage() : "成功",
                    result.getDurationMs()));
            }
            md.append("\n");
        }
        
        // 失败测试
        if (!report.getFailedTests().isEmpty()) {
            md.append("## ❌ 失败测试\n\n");
            for (TestResult result : report.getFailedTests()) {
                md.append(String.format("### %s\n", result.getToolName()));
                md.append(String.format("- **状态**: %s\n", result.getState().getDescription()));
                md.append(String.format("- **错误**: %s\n", result.getErrorDetail()));
                md.append(String.format("- **耗时**: %d ms\n\n", result.getDurationMs()));
            }
        }
        
        // 跳过测试
        if (!report.getSkippedTests().isEmpty()) {
            md.append("## ⏭️ 跳过测试\n\n");
            for (TestResult result : report.getSkippedTests()) {
                md.append(String.format("- **%s**: %s\n", 
                    result.getToolName(), 
                    result.getMessage() != null ? result.getMessage() : "原因未指定"));
            }
            md.append("\n");
        }
        
        // 环境检测
        if (report.getEnvironmentCheck() != null) {
            md.append("## 🔍 环境检测\n\n");
            md.append(report.getEnvironmentCheck().generateSummary().replace("\n", "  \n"));
            md.append("\n");
        }
        
        // 错误详情
        if (!report.getErrors().isEmpty()) {
            md.append("## ⚠️ 错误详情\n\n");
            for (int i = 0; i < report.getErrors().size(); i++) {
                md.append(String.format("%d. %s\n", i + 1, report.getErrors().get(i)));
            }
            md.append("\n");
        }
        
        // 警告
        if (!report.getWarnings().isEmpty()) {
            md.append("## 💡 警告与建议\n\n");
            for (int i = 0; i < report.getWarnings().size(); i++) {
                md.append(String.format("%d. %s\n", i + 1, report.getWarnings().get(i)));
            }
            md.append("\n");
        }
        
        // 页脚
        md.append("---\n");
        md.append("*由 JwCode 测试框架生成*\n");
        
        return md.toString();
    }

    @Override
    public String getContentType() {
        return "text/markdown";
    }

    @Override
    public String getFileExtension() {
        return ".md";
    }
}

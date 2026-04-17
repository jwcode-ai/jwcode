package com.jwcode.core.report;

import java.time.format.DateTimeFormatter;

/**
 * HTML 格式报告生成器
 */
public class HtmlFormatter implements ReportFormatter {

    private static final DateTimeFormatter DATE_FORMAT = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String format(TestReport report) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>").append(report.getTestSuiteName()).append(" - 测试报告</title>\n");
        html.append("<style>\n");
        html.append(getCss());
        html.append("</style>\n</head>\n<body>\n");
        
        // Header
        html.append("<header class=\"header\">\n");
        html.append("<h1>").append(report.getTestSuiteName()).append("</h1>\n");
        html.append("<p class=\"timestamp\">生成时间: ").append(
            report.getGeneratedAt().format(DATE_FORMAT)).append("</p>\n");
        html.append("</header>\n");
        
        // Overview
        html.append("<section class=\"overview\">\n");
        html.append("<h2>📊 测试概览</h2>\n");
        html.append("<div class=\"stats\">\n");
        html.append(String.format("<div class=\"stat total\">总计: <strong>%d</strong></div>\n", 
            report.getTotalCount()));
        html.append(String.format("<div class=\"stat success\">✅ 成功: <strong>%d</strong></div>\n", 
            report.getSuccessCount()));
        html.append(String.format("<div class=\"stat failed\">❌ 失败: <strong>%d</strong></div>\n", 
            report.getFailedCount()));
        html.append(String.format("<div class=\"stat skipped\">⏭️ 跳过: <strong>%d</strong></div>\n", 
            report.getSkippedCount()));
        html.append(String.format("<div class=\"stat error\">🚫 错误: <strong>%d</strong></div>\n", 
            report.getErrorCount()));
        html.append("</div>\n");
        html.append(String.format("<p class=\"success-rate\">成功率: <strong>%.1f%%</strong></p>\n", 
            report.getSuccessRate()));
        html.append("</section>\n");
        
        // Failed tests
        if (!report.getFailedTests().isEmpty()) {
            html.append("<section class=\"failed-tests\">\n");
            html.append("<h2>❌ 失败测试</h2>\n");
            html.append("<ul>\n");
            for (TestResult result : report.getFailedTests()) {
                html.append("<li>\n");
                html.append("<strong>").append(result.getToolName()).append("</strong>\n");
                html.append("<p>错误: ").append(result.getErrorDetail()).append("</p>\n");
                html.append("<small>耗时: ").append(result.getDurationMs()).append(" ms</small>\n");
                html.append("</li>\n");
            }
            html.append("</ul>\n");
            html.append("</section>\n");
        }
        
        // Warnings
        if (!report.getWarnings().isEmpty()) {
            html.append("<section class=\"warnings\">\n");
            html.append("<h2>💡 警告与建议</h2>\n");
            html.append("<ul>\n");
            for (String warning : report.getWarnings()) {
                html.append("<li>").append(warning).append("</li>\n");
            }
            html.append("</ul>\n");
            html.append("</section>\n");
        }
        
        // Footer
        html.append("<footer>\n");
        html.append("<p>由 JwCode 测试框架生成</p>\n");
        html.append("</footer>\n");
        
        html.append("</body>\n</html>");
        
        return html.toString();
    }

    private String getCss() {
        return """
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body { font-family: 'Segoe UI', Tahoma, sans-serif; line-height: 1.6; 
                   color: #333; background: #f5f5f5; }
            .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                      color: white; padding: 2rem; text-align: center; }
            .header h1 { margin-bottom: 0.5rem; }
            .timestamp { opacity: 0.9; font-size: 0.9rem; }
            section { background: white; margin: 1rem; padding: 1.5rem; 
                      border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
            section h2 { margin-bottom: 1rem; border-bottom: 2px solid #667eea; 
                         padding-bottom: 0.5rem; }
            .stats { display: flex; gap: 1rem; flex-wrap: wrap; margin-bottom: 1rem; }
            .stat { padding: 0.75rem 1.5rem; border-radius: 4px; font-size: 1.1rem; }
            .stat.success { background: #d4edda; color: #155724; }
            .stat.failed { background: #f8d7da; color: #721c24; }
            .stat.skipped { background: #fff3cd; color: #856404; }
            .stat.error { background: #f8d7da; color: #721c24; }
            .stat.total { background: #e2e3e5; color: #383d41; }
            .success-rate { font-size: 1.2rem; text-align: center; }
            ul { list-style: none; }
            li { padding: 0.75rem; margin-bottom: 0.5rem; background: #f8f9fa;
                 border-left: 4px solid #667eea; border-radius: 4px; }
            footer { text-align: center; padding: 1rem; color: #666; font-size: 0.9rem; }
            """;
    }

    @Override
    public String getContentType() {
        return "text/html";
    }

    @Override
    public String getFileExtension() {
        return ".html";
    }
}

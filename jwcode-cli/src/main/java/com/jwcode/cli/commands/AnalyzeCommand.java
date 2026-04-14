package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;
import com.jwcode.core.advanced.analyzer.ProjectAnalysisReport;
import com.jwcode.core.advanced.analyzer.SmartProjectAnalyzer;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * /analyze 命令 - 智能项目分析
 * 
 * 对标 Kimi Code 的项目分析能力：
 * 1. 排噪取证：自动排除 .git / target / node_modules，定位关键文件
 * 2. 假设驱动：读取配置推导需要验证的代码文件
 * 3. 错误恢复：分析命令失败原因，给出策略建议
 */
public class AnalyzeCommand implements Command {
    
    @Override
    public String getName() {
        return "analyze";
    }
    
    @Override
    public String getDescription() {
        return "智能分析项目结构。自动排噪、定位关键文件、推导假设、支持错误恢复建议。";
    }
    
    @Override
    public String getUsage() {
        return "/analyze [路径] [--compact] [--max N]";
    }
    
    @Override
    public String[] getAliases() {
        return new String[]{"analyse", "scan", "inspect"};
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        String path = ".";
        boolean compact = false;
        int maxFiles = 30;
        
        if (args != null && !args.trim().isEmpty()) {
            String[] parts = args.trim().split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                if ("--compact".equals(parts[i])) {
                    compact = true;
                } else if ("--max".equals(parts[i]) && i + 1 < parts.length) {
                    try {
                        maxFiles = Integer.parseInt(parts[++i]);
                    } catch (NumberFormatException e) {
                        return CommandResult.error("--max 参数必须是整数");
                    }
                } else if (!parts[i].startsWith("-")) {
                    path = parts[i];
                }
            }
        }
        
        if (!Files.exists(Paths.get(path))) {
            return CommandResult.error("路径不存在: " + path);
        }
        
        try {
            SmartProjectAnalyzer analyzer = new SmartProjectAnalyzer(path);
            ProjectAnalysisReport report = analyzer.analyze();
            
            String output = compact ? toCompactOutput(report) : report.toMarkdown();
            return CommandResult.success(output);
            
        } catch (Exception e) {
            return CommandResult.error("分析失败: " + e.getMessage());
        }
    }
    
    private String toCompactOutput(ProjectAnalysisReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════╗\n");
        sb.append("║           智能项目分析摘要                               ║\n");
        sb.append("╚══════════════════════════════════════════════════════════╝\n\n");
        sb.append("项目路径: ").append(report.getProjectRoot()).append("\n");
        sb.append("项目类型: ").append(report.getProjectType()).append("\n");
        sb.append("识别指标: ").append(String.join(", ", report.getIndicators())).append("\n");
        sb.append("扫描统计: ").append(report.getTotalFilesScanned()).append(" 文件, 跳过 ")
          .append(report.getNoiseFilesSkipped()).append(" 噪音, 耗时 ")
          .append(report.getScanDurationMs()).append("ms\n\n");
        
        sb.append("【关键文件】\n");
        for (ProjectAnalysisReport.EvidenceItem e : report.getEvidence()) {
            sb.append(String.format("  [%3d] %s (%s)%n", e.priority(), e.relativePath(), e.source()));
        }
        
        sb.append("\n【假设验证】\n");
        for (ProjectAnalysisReport.HypothesisItem h : report.getHypotheses()) {
            sb.append(String.format("  [%2d/100] %s%n", h.confidence(), h.description()));
            if (!h.verifiedFiles().isEmpty()) {
                for (String vf : h.verifiedFiles()) {
                    sb.append(String.format("    ✓ %s%n", vf));
                }
            }
        }
        
        if (!report.getRecoveryNotes().isEmpty()) {
            sb.append("\n【恢复记录】\n");
            for (String note : report.getRecoveryNotes()) {
                sb.append("  ! ").append(note).append("\n");
            }
        }
        
        return sb.toString();
    }
}

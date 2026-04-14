package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.jwcode.core.advanced.analyzer.*;
import com.jwcode.core.advanced.analyzer.CommandRecoveryAdvisor.RecoveryAdvice;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.SmartAnalyzeInput;
import com.jwcode.core.tool.output.SmartAnalyzeOutput;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 智能项目分析工具 - 对标 Kimi Code 的项目分析能力
 * 
 * 核心能力：
 * 1. 排噪取证：扫描时自动排除 .git / target / node_modules，精准定位关键文件
 * 2. 假设驱动：读取配置后推导需要验证的代码文件
 * 3. 错误恢复：分析命令失败原因，给出下一步策略建议
 * 
 * AI Prompt 指引：
 * 当你需要分析一个项目的系统设计时，优先使用 SmartAnalyzeTool，
 * 而不是直接用 BashTool 做 ls/find/grep 等容易淹没在噪音中的操作。
 */
public class SmartAnalyzeTool implements Tool<SmartAnalyzeInput, SmartAnalyzeOutput, Void> {
    
    @Override
    public String getName() {
        return "SmartAnalyzeTool";
    }
    
    @Override
    public String getDescription() {
        return "智能分析项目结构，自动排噪、定位关键文件、推导假设、提供错误恢复建议。" +
               "分析项目时应优先使用此工具，避免被 .git / target / node_modules 淹没。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 SmartAnalyzeTool 对项目进行智能分析。
               
               参数:
               - project_root: 项目根目录路径（必需）
               - max_evidence_files: 最多收集的关键文件数（可选，默认 30）
               - preview_lines: 文件内容预览行数（可选，默认 50）
               - output_format: 输出格式 "agent" | "markdown" | "json" | "compact"（可选，默认 agent）
               
               错误恢复模式（可选）:
               - recovery_mode: true 表示分析上一次失败的命令
               - failed_command: 失败命令
               - failed_stdout: 标准输出
               - failed_stderr: 标准错误
               - failed_exit_code: 退出码
               
               示例:
               - 基础分析: {"project_root": "/path/to/project"}
               - 紧凑输出: {"project_root": "/path/to/project", "output_format": "compact"}
               - 错误恢复: {"recovery_mode": true, "failed_command": "ls -la | head -20", "failed_exit_code": 1, "failed_stderr": "head: command not found"}
               
               ⚠️ 行为约束（必须遵守）:
               - 调用此工具后，你应该直接基于返回的文件列表做分析，或调用 FileReadTool 读取核心文件内容。
               - 不要再调用 BashTool、GlobTool、PowerShell 等工具做目录 listing 或文件存在性验证。
               - SmartAnalyzeTool 已经完成了排噪和验证，输出顶部的 🛑 STOP_LISTING 信号明确表示无需再做递归扫描。
               - 你的下一步动作应该是：从返回的关键文件列表中挑选优先级最高的文件，调用 FileReadTool 读取它们。
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        return ToolSchemaGenerator.generateSchema(SmartAnalyzeInput.class);
    }
    
    @Override
    public JsonNode getOutputSchema() {
        return ToolSchemaGenerator.generateSchema(SmartAnalyzeOutput.class);
    }
    
    @Override
    public TypeReference<SmartAnalyzeInput> getInputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public TypeReference<SmartAnalyzeOutput> getOutputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<SmartAnalyzeOutput>> call(
            SmartAnalyzeInput input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Void>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 验证输入
                if (input.projectRoot() == null || input.projectRoot().isBlank()) {
                    return ToolResult.error("project_root 是必需的");
                }
                
                Path rootPath = Paths.get(input.projectRoot()).toAbsolutePath().normalize();
                if (!Files.exists(rootPath)) {
                    return ToolResult.error("项目路径不存在: " + input.projectRoot());
                }
                if (!Files.isDirectory(rootPath)) {
                    return ToolResult.error("项目路径不是目录: " + input.projectRoot());
                }
                
                SmartProjectAnalyzer analyzer = new SmartProjectAnalyzer(rootPath.toString());
                
                // 错误恢复模式
                if (Boolean.TRUE.equals(input.recoveryMode()) && input.failedCommand() != null) {
                    RecoveryAdvice advice = analyzer.adviseCommandRecovery(
                        input.failedCommand(),
                        input.failedStdout() != null ? input.failedStdout() : "",
                        input.failedStderr() != null ? input.failedStderr() : "",
                        input.failedExitCode() != null ? input.failedExitCode() : -1
                    );
                    
                    SmartAnalyzeOutput.RecoveryAdviceOutput recoveryOut = new SmartAnalyzeOutput.RecoveryAdviceOutput(
                        advice.action().name(),
                        advice.diagnosis(),
                        advice.suggestions(),
                        advice.confidence()
                    );
                    
                    return ToolResult.success(new SmartAnalyzeOutput(
                        true,
                        rootPath.toString(),
                        analyzer.getFingerprint().getProjectType().getDisplayName(),
                        analyzer.getFingerprint().getIndicators(),
                        "错误恢复分析完成",
                        null,
                        null,
                        null,
                        recoveryOut,
                        analyzer.getFingerprint().getMetadata(),
                        null
                    ));
                }
                
                // 正常分析模式
                ProjectAnalysisReport report = analyzer.analyze();
                
                // 格式化输出
                String formattedReport;
                switch (input.outputFormat().toLowerCase()) {
                    case "json" -> formattedReport = report.toJson();
                    case "compact" -> formattedReport = toCompactReport(report);
                    case "markdown" -> formattedReport = report.toMarkdown();
                    default -> formattedReport = report.toAgentOptimized();
                }
                
                List<String> evidenceFiles = report.getEvidence().stream()
                    .map(ProjectAnalysisReport.EvidenceItem::relativePath)
                    .collect(Collectors.toList());
                
                List<SmartAnalyzeOutput.HypothesisOutput> hypotheses = report.getHypotheses().stream()
                    .map(h -> new SmartAnalyzeOutput.HypothesisOutput(
                        h.description(),
                        h.confidence(),
                        h.verifiedFiles()
                    ))
                    .collect(Collectors.toList());
                
                String scanSummary = String.format(
                    "项目类型: %s | 扫描文件: %d | 跳过噪音: %d | 证据文件: %d | 假设数: %d | 耗时: %dms",
                    report.getProjectType(),
                    report.getTotalFilesScanned(),
                    report.getNoiseFilesSkipped(),
                    evidenceFiles.size(),
                    hypotheses.size(),
                    report.getScanDurationMs()
                );
                
                SmartAnalyzeOutput output = new SmartAnalyzeOutput(
                    true,
                    report.getProjectRoot(),
                    report.getProjectType(),
                    report.getIndicators(),
                    scanSummary,
                    formattedReport,
                    evidenceFiles,
                    hypotheses,
                    null,
                    report.getMetadata(),
                    null
                );
                
                return ToolResult.success(output);
                
            } catch (Exception e) {
                return ToolResult.error("SmartAnalyzeTool 执行失败: " + e.getMessage());
            }
        });
    }
    
    @Override
    public boolean isReadOnly(SmartAnalyzeInput input) {
        return true;
    }
    
    @Override
    public boolean isConcurrencySafe(SmartAnalyzeInput input) {
        return true;
    }
    
    private String toCompactReport(ProjectAnalysisReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 项目分析摘要 ===\n");
        sb.append("类型: ").append(report.getProjectType()).append("\n");
        sb.append("指标: ").append(String.join(", ", report.getIndicators())).append("\n");
        sb.append("扫描: ").append(report.getTotalFilesScanned()).append(" 文件, 跳过 ")
          .append(report.getNoiseFilesSkipped()).append(" 噪音, 耗时 ")
          .append(report.getScanDurationMs()).append("ms\n\n");
        
        sb.append("=== 关键文件 ===\n");
        for (ProjectAnalysisReport.EvidenceItem e : report.getEvidence()) {
            sb.append(String.format("[%3d] %s (%s)%n", e.priority(), e.relativePath(), e.source()));
        }
        
        sb.append("\n=== 假设验证 ===\n");
        for (ProjectAnalysisReport.HypothesisItem h : report.getHypotheses()) {
            sb.append(String.format("[%2d/100] %s%n", h.confidence(), h.description()));
            if (!h.verifiedFiles().isEmpty()) {
                for (String vf : h.verifiedFiles()) {
                    sb.append("  ✓ ").append(vf).append("\n");
                }
            }
        }
        
        return sb.toString();
    }
}

package com.jwcode.core.advanced.analyzer;

import java.nio.file.Path;
import java.util.*;

/**
 * 项目分析报告 - 结构化输出分析结果
 */
public class ProjectAnalysisReport {
    
    private final String projectRoot;
    private final String projectType;
    private final List<String> indicators;
    private final Map<String, Object> metadata;
    private final List<EvidenceItem> evidence;
    private final List<HypothesisItem> hypotheses;
    private final List<String> recoveryNotes;
    private final String filterSummary;
    private final long scanDurationMs;
    private final int totalFilesScanned;
    private final int noiseFilesSkipped;
    
    public ProjectAnalysisReport(String projectRoot, String projectType, List<String> indicators,
                                  Map<String, Object> metadata, List<EvidenceItem> evidence,
                                  List<HypothesisItem> hypotheses, List<String> recoveryNotes,
                                  String filterSummary, long scanDurationMs,
                                  int totalFilesScanned, int noiseFilesSkipped) {
        this.projectRoot = projectRoot;
        this.projectType = projectType;
        this.indicators = indicators;
        this.metadata = metadata;
        this.evidence = evidence;
        this.hypotheses = hypotheses;
        this.recoveryNotes = recoveryNotes;
        this.filterSummary = filterSummary;
        this.scanDurationMs = scanDurationMs;
        this.totalFilesScanned = totalFilesScanned;
        this.noiseFilesSkipped = noiseFilesSkipped;
    }
    
    public String toAgentOptimized() {
        StringBuilder sb = new StringBuilder();
        sb.append("🛑 STOP_LISTING: 此项目已分析完成，关键文件列表如下，请勿再用 BashTool/GlobTool/PowerShell 做递归目录扫描。\n\n");
        sb.append("[项目分析完成] ").append(projectRoot).append("\n");
        sb.append("类型: ").append(projectType).append(" | 扫描: ").append(totalFilesScanned)
          .append(" 文件 | 跳过噪音: ").append(noiseFilesSkipped).append(" | 耗时: ")
          .append(scanDurationMs).append("ms\n");
        sb.append("指标: ").append(String.join(", ", indicators)).append("\n\n");
        
        sb.append("[关键文件列表 - 已排噪验证，真实存在]\n");
        if (evidence.isEmpty()) {
            sb.append("  (无)\n");
        } else {
            for (EvidenceItem item : evidence) {
                sb.append(String.format("%3d %s (%s)%n", item.priority(), item.relativePath(), item.source()));
                if (item.contentPreview() != null && !item.contentPreview().isEmpty()) {
                    String[] lines = item.contentPreview().split("\n", 6);
                    for (int i = 0; i < Math.min(lines.length, 5); i++) {
                        sb.append("    > ").append(lines[i]).append("\n");
                    }
                    if (lines.length > 5) {
                        sb.append("    > ...\n");
                    }
                }
            }
        }
        sb.append("\n");
        
        sb.append("[配置线索 -> 推荐验证]\n");
        if (hypotheses.isEmpty()) {
            sb.append("  (无)\n");
        } else {
            for (HypothesisItem h : hypotheses) {
                if (!h.verifiedFiles().isEmpty()) {
                    List<String> files = h.verifiedFiles().size() > 6
                        ? h.verifiedFiles().subList(0, 6)
                        : h.verifiedFiles();
                    String suffix = h.verifiedFiles().size() > 6 ? " 等" + h.verifiedFiles().size() + "个" : "";
                    sb.append(String.format("- %s [%d/100] -> %s%s%n", h.description(), h.confidence(), String.join(", ", files), suffix));
                } else {
                    sb.append(String.format("- %s [%d/100] -> (未找到匹配文件)%n", h.description(), h.confidence()));
                }
            }
        }
        sb.append("\n");
        
        if (!recoveryNotes.isEmpty()) {
            sb.append("[恢复记录]\n");
            for (String note : recoveryNotes) {
                sb.append("! ").append(note).append("\n");
            }
            sb.append("\n");
        }
        
        sb.append("[下一步建议]\n");
        sb.append("直接调用 FileReadTool 读取上述高优先级文件的内容进行分析。\n");
        sb.append("不要调用 BashTool/GlobTool 做目录 listing。\n");
        
        return sb.toString();
    }
    
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 智能项目分析报告\n\n");
        sb.append("**项目路径**: ").append(projectRoot).append("\n\n");
        sb.append("**项目类型**: ").append(projectType).append("\n\n");
        sb.append("**识别指标**: ").append(String.join(", ", indicators)).append("\n\n");
        sb.append("**扫描耗时**: ").append(scanDurationMs).append("ms | ");
        sb.append("**扫描文件**: ").append(totalFilesScanned).append(" | ");
        sb.append("**跳过噪音**: ").append(noiseFilesSkipped).append("\n\n");
        sb.append("---\n\n");
        
        // 关键证据
        sb.append("## 高信息密度文件（已排噪）\n\n");
        if (evidence.isEmpty()) {
            sb.append("未收集到关键证据文件。\n\n");
        } else {
            for (EvidenceItem item : evidence) {
                sb.append("### ").append(item.relativePath()).append("\n");
                sb.append("- **来源**: ").append(item.source()).append(" | **优先级**: ").append(item.priority()).append("\n");
                if (item.contentPreview() != null && !item.contentPreview().isEmpty()) {
                    sb.append("```\n").append(item.contentPreview()).append("\n```\n\n");
                }
            }
        }
        
        // 假设推导
        sb.append("## 假设推导与验证\n\n");
        if (hypotheses.isEmpty()) {
            sb.append("未生成假设。\n\n");
        } else {
            for (HypothesisItem h : hypotheses) {
                sb.append("### ").append(h.description()).append("\n");
                sb.append("- **置信度**: ").append(h.confidence()).append("/100\n");
                if (!h.verifiedFiles().isEmpty()) {
                    sb.append("- **已验证文件**:\n");
                    for (String vf : h.verifiedFiles()) {
                        sb.append("  - `").append(vf).append("`\n");
                    }
                } else {
                    sb.append("- **已验证文件**: 无（建议按推荐模式搜索）\n");
                }
                sb.append("\n");
            }
        }
        
        // 恢复记录
        if (!recoveryNotes.isEmpty()) {
            sb.append("## 错误恢复记录\n\n");
            for (String note : recoveryNotes) {
                sb.append("- ").append(note).append("\n");
            }
            sb.append("\n");
        }
        
        sb.append("---\n");
        sb.append(filterSummary).append("\n");
        
        return sb.toString();
    }
    
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"projectRoot\": \"").append(escapeJson(projectRoot)).append("\",\n");
        sb.append("  \"projectType\": \"").append(escapeJson(projectType)).append("\",\n");
        sb.append("  \"scanDurationMs\": ").append(scanDurationMs).append(",\n");
        sb.append("  \"totalFilesScanned\": ").append(totalFilesScanned).append(",\n");
        sb.append("  \"noiseFilesSkipped\": ").append(noiseFilesSkipped).append(",\n");
        sb.append("  \"evidenceCount\": ").append(evidence.size()).append(",\n");
        sb.append("  \"hypothesisCount\": ").append(hypotheses.size()).append("\n");
        sb.append("}");
        return sb.toString();
    }
    
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
    
    // Getters
    public String getProjectRoot() { return projectRoot; }
    public String getProjectType() { return projectType; }
    public List<String> getIndicators() { return indicators; }
    public Map<String, Object> getMetadata() { return metadata; }
    public List<EvidenceItem> getEvidence() { return evidence; }
    public List<HypothesisItem> getHypotheses() { return hypotheses; }
    public List<String> getRecoveryNotes() { return recoveryNotes; }
    public long getScanDurationMs() { return scanDurationMs; }
    public int getTotalFilesScanned() { return totalFilesScanned; }
    public int getNoiseFilesSkipped() { return noiseFilesSkipped; }
    
    public record EvidenceItem(String relativePath, int priority, String source, String contentPreview) {}
    public record HypothesisItem(String description, int confidence, List<String> verifiedFiles) {}
}

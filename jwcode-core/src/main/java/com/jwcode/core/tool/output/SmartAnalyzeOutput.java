package com.jwcode.core.tool.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * SmartAnalyzeTool 输出结果
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SmartAnalyzeOutput(
    @JsonProperty("success")
    boolean success,
    
    @JsonProperty("project_root")
    String projectRoot,
    
    @JsonProperty("project_type")
    String projectType,
    
    @JsonProperty("indicators")
    List<String> indicators,
    
    @JsonProperty("scan_summary")
    String scanSummary,
    
    @JsonProperty("report")
    String report,  // markdown / json / compact 格式的完整报告
    
    @JsonProperty("evidence_files")
    List<String> evidenceFiles,
    
    @JsonProperty("hypotheses")
    List<HypothesisOutput> hypotheses,
    
    @JsonProperty("recovery_advice")
    RecoveryAdviceOutput recoveryAdvice,
    
    @JsonProperty("metadata")
    Map<String, Object> metadata,
    
    @JsonProperty("error")
    String error,

    @JsonProperty("code_analysis")
    CodeAnalysisOutput codeAnalysis
) {
    
    public SmartAnalyzeOutput {
        // success 是基本类型 boolean，无需 null 检查
    }

    /**
     * 向后兼容的构造函数（不包含 codeAnalysis 字段）
     */
    public SmartAnalyzeOutput(boolean success, String projectRoot, String projectType,
                              List<String> indicators, String scanSummary, String report,
                              List<String> evidenceFiles, List<HypothesisOutput> hypotheses,
                              RecoveryAdviceOutput recoveryAdvice, Map<String, Object> metadata,
                              String error) {
        this(success, projectRoot, projectType, indicators, scanSummary, report,
             evidenceFiles, hypotheses, recoveryAdvice, metadata, error, null);
    }
    
    public static SmartAnalyzeOutput error(String message) {
        return new SmartAnalyzeOutput(false, null, null, null, null, null, null, null, null, null, message, null);
    }
    
    public record HypothesisOutput(
        @JsonProperty("description")
        String description,
        
        @JsonProperty("confidence")
        int confidence,
        
        @JsonProperty("verified_files")
        List<String> verifiedFiles
    ) {}
    
    public record RecoveryAdviceOutput(
        @JsonProperty("action")
        String action,
        
        @JsonProperty("diagnosis")
        String diagnosis,
        
        @JsonProperty("suggestions")
        List<String> suggestions,
        
        @JsonProperty("confidence")
        int confidence
    ) {}

    /**
     * 代码语义分析输出（V2 能力合并）
     */
    public record CodeAnalysisOutput(
        @JsonProperty("parsed_files")
        int parsedFiles,

        @JsonProperty("cached_files")
        int cachedFiles,

        @JsonProperty("symbol_nodes")
        int symbolNodes,

        @JsonProperty("symbol_edges")
        int symbolEdges,

        @JsonProperty("query_matches")
        List<Map<String, Object>> queryMatches,

        @JsonProperty("query_summary")
        String querySummary
    ) {}
}

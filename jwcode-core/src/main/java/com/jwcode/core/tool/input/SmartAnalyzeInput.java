package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SmartAnalyzeTool 输入参数
 * 
 * 用于触发智能项目分析，支持排噪取证、假设推导、错误恢复建议
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SmartAnalyzeInput(
    @JsonProperty("project_root")
    String projectRoot,
    
    @JsonProperty("max_evidence_files")
    Integer maxEvidenceFiles,
    
    @JsonProperty("preview_lines")
    Integer previewLines,
    
    @JsonProperty("output_format")
    String outputFormat,  // "agent" | "markdown" | "json" | "compact"
    
    // 以下用于错误恢复分析模式
    @JsonProperty("recovery_mode")
    Boolean recoveryMode, // true = 分析上次命令失败
    
    @JsonProperty("failed_command")
    String failedCommand,
    
    @JsonProperty("failed_stdout")
    String failedStdout,
    
    @JsonProperty("failed_stderr")
    String failedStderr,
    
    @JsonProperty("failed_exit_code")
    Integer failedExitCode,
    
    // 以下用于 V2 代码分析
    @JsonProperty("enable_code_analysis")
    Boolean enableCodeAnalysis,
    
    @JsonProperty("query")
    String query,
    
    @JsonProperty("builtin_query")
    String builtinQuery
) {
    
    public SmartAnalyzeInput {
        if (maxEvidenceFiles == null || maxEvidenceFiles < 1) {
            maxEvidenceFiles = 30;
        }
        if (previewLines == null || previewLines < 1) {
            previewLines = 50;
        }
        if (outputFormat == null || outputFormat.isBlank()) {
            outputFormat = "markdown";
        }
        if (recoveryMode == null) {
            recoveryMode = false;
        }
    }
    
    public SmartAnalyzeInput(String projectRoot) {
        this(projectRoot, null, null, null, null, null, null, null, null, null, null, null);
    }
}

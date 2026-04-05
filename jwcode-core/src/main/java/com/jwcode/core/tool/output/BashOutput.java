package com.jwcode.core.tool.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * BashTool 的输出结果
 * 
 * 对标 JavaScript 项目的 BashTool output
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BashOutput(
    @JsonProperty("stdout")
    String stdout,
    
    @JsonProperty("stderr")
    String stderr,
    
    @JsonProperty("exit_code")
    Integer exitCode,
    
    @JsonProperty("timed_out")
    Boolean timedOut,
    
    @JsonProperty("execution_time_ms")
    Long executionTimeMs,
    
    @JsonProperty("command")
    String command,
    
    @JsonProperty("working_directory")
    String workingDirectory,
    
    @JsonProperty("truncated")
    Boolean truncated,
    
    @JsonProperty("truncation_reason")
    String truncationReason
) {
    
    public BashOutput {
        if (exitCode == null) {
            exitCode = 0;
        }
        if (timedOut == null) {
            timedOut = false;
        }
        if (truncated == null) {
            truncated = false;
        }
    }
    
    /**
     * 创建成功输出
     */
    public static BashOutput success(String stdout, String command, String workingDirectory) {
        return new BashOutput(stdout, "", 0, false, null, command, workingDirectory, false, null);
    }
    
    /**
     * 创建错误输出
     */
    public static BashOutput error(String stderr, int exitCode, String command) {
        return new BashOutput("", stderr, exitCode, false, null, command, null, false, null);
    }
    
    /**
     * 创建超时输出
     */
    public static BashOutput timeout(String partialOutput, String command, long executionTimeMs) {
        return new BashOutput(partialOutput, "", -1, true, executionTimeMs, command, null, true, "timeout");
    }
    
    /**
     * 创建截断输出
     */
    public static BashOutput truncated(String stdout, String stderr, int exitCode, 
                                        String command, String reason) {
        return new BashOutput(stdout, stderr, exitCode, false, null, command, null, true, reason);
    }
    
    /**
     * 检查是否成功
     */
    public boolean isSuccess() {
        return exitCode != null && exitCode == 0 && !timedOut;
    }
    
    /**
     * 检查是否超时
     */
    public boolean isTimedOut() {
        return timedOut != null && timedOut;
    }
    
    /**
     * 检查是否被截断
     */
    public boolean isTruncated() {
        return truncated != null && truncated;
    }
    
    /**
     * 获取完整输出（stdout + stderr）
     */
    public String getFullOutput() {
        StringBuilder sb = new StringBuilder();
        if (stdout != null && !stdout.isEmpty()) {
            sb.append(stdout);
        }
        if (stderr != null && !stderr.isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(stderr);
        }
        return sb.toString();
    }
}

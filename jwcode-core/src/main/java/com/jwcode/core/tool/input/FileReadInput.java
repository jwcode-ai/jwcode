package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;

/**
 * FileReadTool 的输入参数
 * 
 * 对标 JavaScript 项目的 FileReadTool input schema
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileReadInput(
    @NotBlank(message = "file_path 是必需的")
    @JsonProperty("file_path")
    String filePath,
    
    @Min(value = 1, message = "start_line 必须大于 0")
    @JsonProperty("start_line")
    Integer startLine,
    
    @Min(value = 1, message = "end_line 必须大于 0")
    @JsonProperty("end_line")
    Integer endLine,
    
    @JsonProperty("reason")
    String reason,
    
    @JsonProperty("offset")
    Integer offset
) {
    
    public FileReadInput {
        // 规范化路径
        if (filePath != null) {
            filePath = filePath.replace("\\", "/").trim();
        }
    }
    
    public FileReadInput(String filePath) {
        this(filePath, null, null, null, null);
    }
    
    public FileReadInput(String filePath, int startLine, int endLine) {
        this(filePath, startLine, endLine, null, null);
    }
    
    /**
     * 检查是否是范围读取
     */
    public boolean isRangeRead() {
        return startLine != null || endLine != null;
    }
    
    /**
     * 获取实际的起始行（1-based）
     */
    public int getEffectiveStartLine() {
        return startLine != null ? startLine : 1;
    }
    
    /**
     * 获取有效的偏移量
     */
    public int getEffectiveOffset() {
        return offset != null ? offset : 0;
    }
}

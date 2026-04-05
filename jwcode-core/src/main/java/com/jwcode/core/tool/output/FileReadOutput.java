package com.jwcode.core.tool.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * FileReadTool 的输出结果
 * 
 * 对标 JavaScript 项目的 FileReadTool output
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileReadOutput(
    @JsonProperty("type")
    String type,
    
    @JsonProperty("file_path")
    String filePath,
    
    @JsonProperty("content")
    String content,
    
    @JsonProperty("total_lines")
    Integer totalLines,
    
    @JsonProperty("start_line")
    Integer startLine,
    
    @JsonProperty("end_line")
    Integer endLine,
    
    @JsonProperty("lines_read")
    Integer linesRead,
    
    @JsonProperty("mime_type")
    String mimeType,
    
    @JsonProperty("file_size")
    Long fileSize,
    
    @JsonProperty("base64_data")
    String base64Data,
    
    @JsonProperty("truncated")
    Boolean truncated,
    
    @JsonProperty("truncation_reason")
    String truncationReason,
    
    @JsonProperty("metadata")
    Map<String, Object> metadata
) {
    
    public FileReadOutput {
        if (type == null) {
            type = "text";
        }
    }
    
    /**
     * 创建文本文件输出
     */
    public static FileReadOutput text(String filePath, String content, int totalLines, 
                                       int startLine, int endLine, int linesRead) {
        return new FileReadOutput(
            "text", filePath, content, totalLines, startLine, endLine, linesRead,
            null, null, null, null, null, null
        );
    }
    
    /**
     * 创建图片文件输出
     */
    public static FileReadOutput image(String filePath, String mimeType, long fileSize, 
                                        String base64Data) {
        return new FileReadOutput(
            "image", filePath, null, null, null, null, null,
            mimeType, fileSize, base64Data, null, null, null
        );
    }
    
    /**
     * 创建截断的输出
     */
    public static FileReadOutput truncated(String filePath, String content, int totalLines,
                                            int startLine, int endLine, int linesRead,
                                            String reason) {
        return new FileReadOutput(
            "text", filePath, content, totalLines, startLine, endLine, linesRead,
            null, null, null, true, reason, null
        );
    }
    
    /**
     * 检查是否是图片
     */
    public boolean isImage() {
        return "image".equals(type);
    }
    
    /**
     * 检查是否被截断
     */
    public boolean isTruncated() {
        return truncated != null && truncated;
    }
}

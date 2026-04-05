package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * FileWriteTool 的输入参数
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileWriteInput(
    @NotBlank(message = "file_path 是必需的")
    @JsonProperty("file_path")
    String filePath,
    
    @NotBlank(message = "content 是必需的")
    @JsonProperty("content")
    String content,
    
    @JsonProperty("reason")
    String reason,
    
    @JsonProperty("append")
    Boolean append,
    
    @JsonProperty("create_directories")
    Boolean createDirectories
) {
    
    public FileWriteInput {
        if (filePath != null) {
            filePath = filePath.replace("\\", "/").trim();
        }
        if (append == null) {
            append = false;
        }
        if (createDirectories == null) {
            createDirectories = true;
        }
    }
    
    public FileWriteInput(String filePath, String content) {
        this(filePath, content, null, null, null);
    }
}

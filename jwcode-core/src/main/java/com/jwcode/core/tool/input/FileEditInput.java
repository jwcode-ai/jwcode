package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * FileEditTool 的输入参数
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileEditInput(
    @NotBlank(message = "file_path 是必需的")
    @JsonProperty("file_path")
    String filePath,
    
    @JsonProperty("old_content")
    String oldContent,
    
    @JsonProperty("new_content")
    String newContent,
    
    @JsonProperty("reason")
    String reason
) {
    public FileEditInput(String filePath, String oldContent, String newContent) {
        this(filePath, oldContent, newContent, null);
    }
}

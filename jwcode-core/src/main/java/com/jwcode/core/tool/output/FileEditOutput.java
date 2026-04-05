package com.jwcode.core.tool.output;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * FileEditTool 的输出结果
 */
public record FileEditOutput(
    @JsonProperty("file_path")
    String filePath,
    
    @JsonProperty("old_content")
    String oldContent,
    
    @JsonProperty("new_content")
    String newContent,
    
    @JsonProperty("changes_made")
    int changesMade,
    
    @JsonProperty("error")
    String error
) {
    public boolean success() {
        return error == null || error.isEmpty();
    }
}

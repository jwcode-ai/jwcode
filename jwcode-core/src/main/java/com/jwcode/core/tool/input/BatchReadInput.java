package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * BatchReadTool 的输入参数
 *
 * 用于批量读取多个文件内容
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BatchReadInput(
    @JsonProperty("file_paths")
    List<String> filePaths,

    @JsonProperty("max_total_lines")
    Integer maxTotalLines,

    @JsonProperty("max_total_tokens")
    Integer maxTotalTokens
) {

    public BatchReadInput {
        if (maxTotalLines == null) {
            maxTotalLines = 2000;
        }
        if (maxTotalTokens == null) {
            maxTotalTokens = 8000;
        }
        if (filePaths != null) {
            // 限制最大文件数
            if (filePaths.size() > 20) {
                filePaths = filePaths.subList(0, 20);
            }
        }
    }

    public int getMaxTotalLines() {
        return maxTotalLines != null ? maxTotalLines : 2000;
    }

    public int getMaxTotalTokens() {
        return maxTotalTokens != null ? maxTotalTokens : 8000;
    }
}

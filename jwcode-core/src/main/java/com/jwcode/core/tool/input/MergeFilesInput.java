package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * MergeFilesTool 的输入参数
 *
 * 用于将多个文件合并为一个文件
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MergeFilesInput(
    @JsonProperty("source_pattern")
    String sourcePattern,

    @JsonProperty("source_paths")
    List<String> sourcePaths,

    @JsonProperty("output_path")
    String outputPath,

    @JsonProperty("separator")
    String separator
) {

    public MergeFilesInput {
        if (separator == null || separator.isBlank()) {
            separator = "\n\n---\n\n";
        }
    }

    public String getSeparator() {
        return separator != null && !separator.isBlank() ? separator : "\n\n---\n\n";
    }
}

package com.jwcode.core.tool.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * BatchReadTool 的输出结果
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BatchReadOutput(
    @JsonProperty("files")
    List<FileResult> files,

    @JsonProperty("total_files")
    int totalFiles,

    @JsonProperty("read_files")
    int readFiles,

    @JsonProperty("skipped_files")
    List<String> skippedFiles,

    @JsonProperty("truncated")
    boolean truncated,

    @JsonProperty("truncation_reason")
    String truncationReason
) {

    public BatchReadOutput {
        if (skippedFiles == null) {
            skippedFiles = List.of();
        }
    }

    public static BatchReadOutput success(List<FileResult> files, List<String> skippedFiles) {
        return new BatchReadOutput(
            files,
            files.size() + skippedFiles.size(),
            files.size(),
            skippedFiles,
            false,
            null
        );
    }

    public static BatchReadOutput truncated(List<FileResult> files, List<String> skippedFiles, String reason) {
        return new BatchReadOutput(
            files,
            files.size() + skippedFiles.size(),
            files.size(),
            skippedFiles,
            true,
            reason
        );
    }

    /**
     * 单个文件的读取结果
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FileResult(
        @JsonProperty("file_path")
        String filePath,

        @JsonProperty("content")
        String content,

        @JsonProperty("total_lines")
        int totalLines,

        @JsonProperty("read_lines")
        int readLines,

        @JsonProperty("truncated")
        boolean truncated,

        @JsonProperty("error")
        String error
    ) {
        public static FileResult success(String filePath, String content, int totalLines, int readLines) {
            return new FileResult(filePath, content, totalLines, readLines, false, null);
        }

        public static FileResult truncated(String filePath, String content, int totalLines, int readLines) {
            return new FileResult(filePath, content, totalLines, readLines, true, null);
        }

        public static FileResult error(String filePath, String error) {
            return new FileResult(filePath, null, 0, 0, false, error);
        }
    }
}

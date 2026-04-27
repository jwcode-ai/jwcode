package com.jwcode.core.tool.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * MergeFilesTool 的输出结果
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MergeFilesOutput(
    @JsonProperty("output_path")
    String outputPath,

    @JsonProperty("files_merged")
    int filesMerged,

    @JsonProperty("total_size")
    long totalSize,

    @JsonProperty("skipped_files")
    List<String> skippedFiles,

    @JsonProperty("truncated")
    boolean truncated,

    @JsonProperty("truncation_reason")
    String truncationReason
) {

    public MergeFilesOutput {
        if (skippedFiles == null) {
            skippedFiles = List.of();
        }
    }

    public static MergeFilesOutput success(String outputPath, int filesMerged, long totalSize) {
        return new MergeFilesOutput(outputPath, filesMerged, totalSize, List.of(), false, null);
    }

    public static MergeFilesOutput success(String outputPath, int filesMerged, long totalSize, List<String> skippedFiles) {
        return new MergeFilesOutput(outputPath, filesMerged, totalSize, skippedFiles, false, null);
    }

    public static MergeFilesOutput truncated(String outputPath, int filesMerged, long totalSize, List<String> skippedFiles, String reason) {
        return new MergeFilesOutput(outputPath, filesMerged, totalSize, skippedFiles, true, reason);
    }
}

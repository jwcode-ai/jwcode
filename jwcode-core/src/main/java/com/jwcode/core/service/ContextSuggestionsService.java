package com.jwcode.core.service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ContextSuggestionsService — 上下文建议服务（对标 Claude Code contextSuggestions.ts）。
 *
 * <p>根据 ContextAnalysisService.TokenStats 自动生成用户可操作的优化建议。
 * 检测 5 类问题：</p>
 * <ol>
 *   <li><b>NearCapacity</b> — 上下文接近容量上限（≥80%）</li>
 *   <li><b>LargeToolResults</b> — 单个工具输出占比过高（≥15% 或 ≥10K tokens）</li>
 *   <li><b>FileReadBloat</b> — 文件读取结果膨胀（≥5% 且 ≥10K tokens）</li>
 *   <li><b>MemoryBloat</b> — 记忆文件占用过高（≥5% 或 ≥5K tokens）</li>
 *   <li><b>AutoCompactDisabled</b> — 自动压缩未启用但容量 ≥50%</li>
 * </ol>
 *
 * <h3>设计原则</h3>
 * <p>建议按严重性排序（warning 优先），每组建议包含预估可节省的 Token 数。
 * 这直接对标 Claude Code 的 generateContextSuggestions()，其建议驱动了
 * UI 中的上下文警告提示。</p>
 *
 * @author JWCode Team
 * @since 3.0.0
 */
public class ContextSuggestionsService {

    // 阈值
    private static final double LARGE_TOOL_PERCENT = 15.0;
    private static final long LARGE_TOOL_TOKENS = 10_000;
    private static final double READ_BLOAT_PERCENT = 5.0;
    private static final double NEAR_CAPACITY_PERCENT = 80.0;
    private static final double MEMORY_HIGH_PERCENT = 5.0;
    private static final long MEMORY_HIGH_TOKENS = 5_000;

    /**
     * 建议严重性。
     */
    public enum Severity {
        /** 信息建议 */
        INFO,
        /** 警告（需要关注） */
        WARNING
    }

    /**
     * 单条上下文建议。
     */
    public static class ContextSuggestion {
        public final Severity severity;
        public final String title;
        public final String detail;
        /** 预估可节省的 Token 数 */
        public final long savingsTokens;

        public ContextSuggestion(Severity severity, String title, String detail, long savingsTokens) {
            this.severity = severity;
            this.title = title;
            this.detail = detail;
            this.savingsTokens = savingsTokens;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s (save ~%d tokens)", severity, title, savingsTokens);
        }
    }

    /**
     * 生成上下文建议列表。
     *
     * @param stats Token 统计
     * @param maxTokens 上下文窗口大小
     * @param memoryTokens 记忆文件总 Token
     * @param memoryFiles 记忆文件列表（名称 → Token 数）
     * @param isAutoCompactEnabled 是否启用自动压缩
     * @return 建议列表（按严重性降序、节省量降序排列）
     */
    public List<ContextSuggestion> generate(ContextAnalysisService.TokenStats stats,
                                            long maxTokens,
                                            long memoryTokens,
                                            Map<String, Long> memoryFiles,
                                            boolean isAutoCompactEnabled) {
        List<ContextSuggestion> suggestions = new ArrayList<>();

        if (maxTokens <= 0) return suggestions;

        double usedPercent = (stats.total * 100.0) / maxTokens;

        checkNearCapacity(usedPercent, isAutoCompactEnabled, suggestions);
        checkLargeToolResults(stats, maxTokens, suggestions);
        checkFileReadBloat(stats, maxTokens, suggestions);
        checkMemoryBloat(memoryTokens, memoryFiles, maxTokens, suggestions);
        checkAutoCompactDisabled(usedPercent, isAutoCompactEnabled, suggestions);

        // 排序：warning 优先，然后按节省量降序
        suggestions.sort((a, b) -> {
            if (a.severity != b.severity) {
                return a.severity == Severity.WARNING ? -1 : 1;
            }
            return Long.compare(b.savingsTokens, a.savingsTokens);
        });

        return suggestions;
    }

    // ==================== 各项检查 ====================

    private void checkNearCapacity(double percent, boolean isAutoCompactEnabled,
                                   List<ContextSuggestion> suggestions) {
        if (percent >= NEAR_CAPACITY_PERCENT) {
            suggestions.add(new ContextSuggestion(
                Severity.WARNING,
                String.format("上下文使用率达 %.0f%%", percent),
                isAutoCompactEnabled
                    ? "自动压缩即将触发，会丢弃较旧的消息。使用 /compact 手动控制保留内容。"
                    : "自动压缩已禁用。使用 /compact 释放空间，或在 /config 中启用自动压缩。",
                0
            ));
        }
    }

    private void checkLargeToolResults(ContextAnalysisService.TokenStats stats,
                                       long maxTokens,
                                       List<ContextSuggestion> suggestions) {
        // 合并所有工具名
        Set<String> toolNames = new LinkedHashSet<>();
        toolNames.addAll(stats.toolRequests.keySet());
        toolNames.addAll(stats.toolResults.keySet());

        for (String toolName : toolNames) {
            long totalTokens = stats.getToolTotal(toolName);
            double percent = (totalTokens * 100.0) / maxTokens;

            if (percent < LARGE_TOOL_PERCENT || totalTokens < LARGE_TOOL_TOKENS) {
                continue;
            }

            ContextSuggestion suggestion = buildToolSuggestion(toolName, totalTokens, percent);
            if (suggestion != null) {
                suggestions.add(suggestion);
            }
        }
    }

    private ContextSuggestion buildToolSuggestion(String toolName, long tokens, double percent) {
        return switch (toolName) {
            case "Bash" -> new ContextSuggestion(
                Severity.WARNING,
                String.format("Bash 输出占 %.0f%% (%s tokens)", percent, formatTokens(tokens)),
                "使用 head/tail/grep 过滤输出。避免对大文件使用 cat — 使用 Read 带 offset/limit。",
                tokens / 2
            );
            case "Read" -> new ContextSuggestion(
                Severity.INFO,
                String.format("Read 输出占 %.0f%% (%s tokens)", percent, formatTokens(tokens)),
                "使用 offset 和 limit 参数只读取需要的部分。避免重复读取整个文件。",
                (long) (tokens * 0.3)
            );
            case "Grep" -> new ContextSuggestion(
                Severity.INFO,
                String.format("Grep 输出占 %.0f%% (%s tokens)", percent, formatTokens(tokens)),
                "使用更精确的正则模式，或用 glob/type 参数缩小文件范围。考虑用 Glob 做文件发现。",
                (long) (tokens * 0.3)
            );
            case "WebFetch" -> new ContextSuggestion(
                Severity.INFO,
                String.format("WebFetch 输出占 %.0f%% (%s tokens)", percent, formatTokens(tokens)),
                "网页内容可能非常大。考虑只提取需要的信息。",
                (long) (tokens * 0.4)
            );
            default -> {
                if (percent >= 20) {
                    yield new ContextSuggestion(
                        Severity.INFO,
                        String.format("%s 输出占 %.0f%% (%s tokens)", toolName, percent, formatTokens(tokens)),
                        "此工具消耗了显著的上下文空间。",
                        (long) (tokens * 0.2)
                    );
                }
                yield null;
            }
        };
    }

    private void checkFileReadBloat(ContextAnalysisService.TokenStats stats,
                                    long maxTokens,
                                    List<ContextSuggestion> suggestions) {
        long readResultTokens = stats.toolResults.getOrDefault("Read", 0L);
        long readCallTokens = stats.toolRequests.getOrDefault("Read", 0L);
        long totalReadTokens = readCallTokens + readResultTokens;
        double totalReadPercent = (totalReadTokens * 100.0) / maxTokens;
        double readPercent = (readResultTokens * 100.0) / maxTokens;

        // 如果已被 checkLargeToolResults 覆盖（≥15%），跳过
        if (totalReadPercent >= LARGE_TOOL_PERCENT && totalReadTokens >= LARGE_TOOL_TOKENS) {
            return;
        }

        if (readPercent >= READ_BLOAT_PERCENT && readResultTokens >= LARGE_TOOL_TOKENS) {
            suggestions.add(new ContextSuggestion(
                Severity.INFO,
                String.format("文件读取占 %.0f%% (%s tokens)", readPercent, formatTokens(readResultTokens)),
                "如果重复读取文件，考虑引用之前的读取结果。对大文件使用 offset/limit。",
                (long) (readResultTokens * 0.3)
            ));
        }
    }

    private void checkMemoryBloat(long memoryTokens, Map<String, Long> memoryFiles,
                                  long maxTokens, List<ContextSuggestion> suggestions) {
        if (maxTokens <= 0) return;
        double memoryPercent = (memoryTokens * 100.0) / maxTokens;

        if (memoryPercent >= MEMORY_HIGH_PERCENT && memoryTokens >= MEMORY_HIGH_TOKENS) {
            // 找出最大的 3 个记忆文件
            String largestFiles = memoryFiles.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(3)
                .map(e -> String.format("%s (%s)", e.getKey(), formatTokens(e.getValue())))
                .collect(Collectors.joining(", "));

            suggestions.add(new ContextSuggestion(
                Severity.INFO,
                String.format("记忆文件占 %.0f%% (%s tokens)", memoryPercent, formatTokens(memoryTokens)),
                String.format("最大文件: %s。使用 /memory 检查和清理过时条目。", largestFiles),
                (long) (memoryTokens * 0.3)
            ));
        }
    }

    private void checkAutoCompactDisabled(double percent, boolean isAutoCompactEnabled,
                                          List<ContextSuggestion> suggestions) {
        if (!isAutoCompactEnabled && percent >= 50 && percent < NEAR_CAPACITY_PERCENT) {
            suggestions.add(new ContextSuggestion(
                Severity.INFO,
                "自动压缩已禁用",
                "不启用自动压缩可能会在达到上下文上限时丢失对话。在 /config 中启用或手动使用 /compact。",
                0
            ));
        }
    }

    // ==================== 工具方法 ====================

    private static String formatTokens(long tokens) {
        if (tokens >= 1_000_000) return String.format("%.1fM", tokens / 1_000_000.0);
        if (tokens >= 1_000) return String.format("%.1fK", tokens / 1_000.0);
        return String.valueOf(tokens);
    }
}

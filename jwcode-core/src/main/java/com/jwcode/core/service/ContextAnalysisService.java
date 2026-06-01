package com.jwcode.core.service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ContextAnalysisService — 上下文分析服务（对标 Claude Code contextAnalysis.ts）。
 *
 * <p>分析会话消息的 Token 分布，生成按工具类型、消息类型、附件类型的详细统计。
 * 用于驱动上下文建议（ContextSuggestionsService）和自动压缩决策。</p>
 *
 * <h3>核心指标</h3>
 * <ul>
 *   <li><b>toolRequests</b> — 各工具调用的 Token 数</li>
 *   <li><b>toolResults</b> — 各工具结果的 Token 数</li>
 *   <li><b>humanMessages</b> — 用户消息 Token 数</li>
 *   <li><b>assistantMessages</b> — AI 消息 Token 数</li>
 *   <li><b>duplicateFileReads</b> — 重复文件读取（同一文件被多次 Read）</li>
 *   <li><b>localCommandOutputs</b> — 本地命令输出 Token 数</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <p>只做统计分析，不生成建议。建议由 {@link ContextSuggestionsService} 负责。
 * 这种分离符合单一职责原则，也便于单元测试。</p>
 *
 * @author JWCode Team
 * @since 3.0.0
 */
public class ContextAnalysisService {

    // 阈值常量
    private static final double LARGE_TOOL_RESULT_PERCENT = 15.0;
    private static final long LARGE_TOOL_RESULT_TOKENS = 10_000;
    private static final double READ_BLOAT_PERCENT = 5.0;
    private static final double NEAR_CAPACITY_PERCENT = 80.0;
    private static final double MEMORY_HIGH_PERCENT = 5.0;
    private static final long MEMORY_HIGH_TOKENS = 5_000;

    /**
     * 消息节点的 Token 统计。
     */
    public static class TokenStats {
        // 工具请求：tool_name → tokens
        public final Map<String, Long> toolRequests = new LinkedHashMap<>();
        // 工具结果：tool_name → tokens
        public final Map<String, Long> toolResults = new LinkedHashMap<>();
        // 工具调用计数
        public final Map<String, Long> toolCallCounts = new LinkedHashMap<>();
        // 工具结果计数
        public final Map<String, Long> toolResultCounts = new LinkedHashMap<>();

        public long humanMessages = 0;
        public long assistantMessages = 0;
        public long localCommandOutputs = 0;
        public long other = 0;
        public long total = 0;

        // 附件：type → count
        public final Map<String, Long> attachments = new LinkedHashMap<>();
        // 重复文件读取：path → {count, tokens}
        public final Map<String, DuplicateReadInfo> duplicateFileReads = new LinkedHashMap<>();

        /**
         * 重复读取信息。
         */
        public static class DuplicateReadInfo {
            public long count;
            public long tokens;

            public DuplicateReadInfo(long count, long tokens) {
                this.count = count;
                this.tokens = tokens;
            }

            /** 浪费的 Token 数（重复读取的 Token） */
            public long wastedTokens() {
                if (count <= 1) return 0;
                long avgTokensPerRead = tokens / count;
                return avgTokensPerRead * (count - 1);
            }
        }

        /**
         * 工具调用的总 Token（请求 + 结果）。
         */
        public long getToolTotal(String toolName) {
            return toolRequests.getOrDefault(toolName, 0L)
                + toolResults.getOrDefault(toolName, 0L);
        }

        /**
         * 工具结果的百分比。
         */
        public double getToolPercent(String toolName, long maxTokens) {
            if (maxTokens <= 0) return 0;
            return (getToolTotal(toolName) * 100.0) / maxTokens;
        }

        /**
         * 重复读取浪费的总 Token。
         */
        public long getDuplicateWastedTokens() {
            return duplicateFileReads.values().stream()
                .mapToLong(DuplicateReadInfo::wastedTokens)
                .sum();
        }

        /**
         * 格式化报告。
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("TokenStats{total=").append(formatTokens(total)).append("\n");

            sb.append("  toolRequests: ");
            toolRequests.forEach((name, tokens) ->
                sb.append(name).append("=").append(formatTokens(tokens)).append(" "));
            sb.append("\n");

            sb.append("  toolResults: ");
            toolResults.forEach((name, tokens) ->
                sb.append(name).append("=").append(formatTokens(tokens)).append(" "));
            sb.append("\n");

            sb.append("  humanMsgs=").append(formatTokens(humanMessages))
              .append(", assistantMsgs=").append(formatTokens(assistantMessages))
              .append(", localCmdOutputs=").append(formatTokens(localCommandOutputs))
              .append(", other=").append(formatTokens(other)).append("\n");

            if (!duplicateFileReads.isEmpty()) {
                sb.append("  duplicateFileReads: ");
                duplicateFileReads.forEach((path, info) ->
                    sb.append(path).append("(x").append(info.count)
                      .append(", wasted=").append(formatTokens(info.wastedTokens())).append(") "));
                sb.append("\n");
            }

            sb.append("}");
            return sb.toString();
        }

        private String formatTokens(long tokens) {
            if (tokens >= 1_000_000) return String.format("%.1fM", tokens / 1_000_000.0);
            if (tokens >= 1_000) return String.format("%.1fK", tokens / 1_000.0);
            return String.valueOf(tokens);
        }
    }

    /**
     * 分析消息列表，生成 TokenStats。
     *
     * @param messages 消息列表（按时间顺序）
     * @param tokenEstimator Token 估算函数
     * @return 详细的 Token 统计
     */
    public TokenStats analyze(List<MessageNode> messages,
                              java.util.function.ToIntFunction<String> tokenEstimator) {
        TokenStats stats = new TokenStats();

        if (messages == null || messages.isEmpty()) {
            return stats;
        }

        // tool_use_id → tool_name 映射（用于 tool_result 回溯工具名）
        Map<String, String> toolIdToName = new LinkedHashMap<>();
        // tool_use_id → file_path 映射（用于检测重复文件读取）
        Map<String, String> readToolIdToFilePath = new LinkedHashMap<>();
        // file_path → {count, tokens} 中间统计
        Map<String, long[]> fileReadStats = new LinkedHashMap<>();

        for (MessageNode msg : messages) {
            if (msg.type.equals("attachment")) {
                String attachmentType = msg.attachmentType != null ? msg.attachmentType : "unknown";
                stats.attachments.merge(attachmentType, 1L, Long::sum);
                continue;
            }

            int tokens = tokenEstimator.applyAsInt(msg.content != null ? msg.content : "");
            stats.total += tokens;

            switch (msg.role) {
                case "user" -> {
                    if (msg.content != null && msg.content.contains("local-command-stdout")) {
                        stats.localCommandOutputs += tokens;
                    } else {
                        stats.humanMessages += tokens;
                    }
                }
                case "assistant" -> stats.assistantMessages += tokens;
                case "tool_use" -> {
                    String toolName = msg.toolName != null ? msg.toolName : "unknown";
                    stats.toolRequests.merge(toolName, (long) tokens, Long::sum);
                    stats.toolCallCounts.merge(toolName, 1L, Long::sum);
                    if (msg.toolUseId != null) {
                        toolIdToName.put(msg.toolUseId, toolName);
                    }
                    // 跟踪 Read 工具的文件路径
                    if ("Read".equals(toolName) && msg.filePath != null) {
                        readToolIdToFilePath.put(msg.toolUseId, msg.filePath);
                    }
                }
                case "tool_result" -> {
                    String toolName = toolIdToName.getOrDefault(msg.toolUseId, "unknown");
                    stats.toolResults.merge(toolName, (long) tokens, Long::sum);
                    stats.toolResultCounts.merge(toolName, 1L, Long::sum);

                    // 跟踪 Read 工具结果的 Token
                    if ("Read".equals(toolName)) {
                        String path = readToolIdToFilePath.get(msg.toolUseId);
                        if (path != null) {
                            long[] entry = fileReadStats.computeIfAbsent(path, k -> new long[2]);
                            entry[0]++; // count
                            entry[1] += tokens; // totalTokens
                        }
                    }
                }
                default -> stats.other += tokens;
            }
        }

        // 计算重复文件读取
        fileReadStats.forEach((path, entry) -> {
            long count = entry[0];
            if (count > 1) {
                long totalTokens = entry[1];
                stats.duplicateFileReads.put(path,
                    new TokenStats.DuplicateReadInfo(count, totalTokens));
            }
        });

        return stats;
    }

    /**
     * 消息节点接口 — 适配不同消息表示。
     */
    public static class MessageNode {
        public String role;       // user, assistant, tool_use, tool_result
        public String type;       // 普通消息或 "attachment"
        public String content;
        public String toolName;   // tool_use 时有效
        public String toolUseId;  // tool_use / tool_result 时有效
        public String filePath;   // Read 工具时有效
        public String attachmentType;

        public MessageNode() {}

        public static MessageNode user(String content) {
            MessageNode n = new MessageNode();
            n.role = "user";
            n.content = content;
            return n;
        }

        public static MessageNode assistant(String content) {
            MessageNode n = new MessageNode();
            n.role = "assistant";
            n.content = content;
            return n;
        }

        public static MessageNode toolUse(String toolName, String toolUseId, String content) {
            MessageNode n = new MessageNode();
            n.role = "tool_use";
            n.toolName = toolName;
            n.toolUseId = toolUseId;
            n.content = content;
            return n;
        }

        public static MessageNode toolResult(String toolUseId, String content) {
            MessageNode n = new MessageNode();
            n.role = "tool_result";
            n.toolUseId = toolUseId;
            n.content = content;
            return n;
        }

        public static MessageNode attachment(String attachmentType) {
            MessageNode n = new MessageNode();
            n.type = "attachment";
            n.attachmentType = attachmentType;
            return n;
        }
    }

    // ==================== 便捷阈值检查 ====================

    /**
     * 检查是否接近上下文容量。
     */
    public static boolean isNearCapacity(long usedTokens, long maxTokens) {
        if (maxTokens <= 0) return false;
        return (usedTokens * 100.0 / maxTokens) >= NEAR_CAPACITY_PERCENT;
    }

    /**
     * 检查某个工具的结果是否过大。
     */
    public static boolean isToolResultTooLarge(String toolName, TokenStats stats, long maxTokens) {
        long toolTotal = stats.getToolTotal(toolName);
        double percent = stats.getToolPercent(toolName, maxTokens);
        return percent >= LARGE_TOOL_RESULT_PERCENT && toolTotal >= LARGE_TOOL_RESULT_TOKENS;
    }

    /**
     * 检查文件读取是否膨胀。
     */
    public static boolean isFileReadBloat(TokenStats stats, long maxTokens) {
        if (maxTokens <= 0) return false;
        long readResultTokens = stats.toolResults.getOrDefault("Read", 0L);
        double readPercent = (readResultTokens * 100.0) / maxTokens;
        return readPercent >= READ_BLOAT_PERCENT && readResultTokens >= LARGE_TOOL_RESULT_TOKENS;
    }
}

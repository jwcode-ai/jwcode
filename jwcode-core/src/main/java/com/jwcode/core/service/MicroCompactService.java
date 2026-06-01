package com.jwcode.core.service;

import java.util.Set;
import java.util.logging.Logger;

/**
 * MicroCompactService — 单条工具结果轻量级压缩服务（对标 Claude Code microCompact）。
 *
 * <p>从 SimpleCompactionStrategy 中提取 5 级工具结果分类逻辑，
 * 作为独立服务暴露，支持在单条工具结果返回后做轻量级压缩，
 * 而不触发完整的 compaction 流程。</p>
 *
 * <h3>五级分层保留策略</h3>
 * <ul>
 *   <li><b>Tier 1 (CRITICAL)</b>: 错误/异常 → 完整保留（最多 300 字符）</li>
 *   <li><b>Tier 2 (HIGH)</b>: 文件修改 → 保留路径 + 变更摘要</li>
 *   <li><b>Tier 3 (MEDIUM)</b>: 读取工具 → 保留路径 + 首尾 100 字符</li>
 *   <li><b>Tier 4 (LOW)</b>: 命令执行 → 保留退出码 + 最后 200 字符</li>
 *   <li><b>Tier 5 (MINIMAL)</b>: 其他 → 仅保留工具名 + success/fail</li>
 * </ul>
 *
 * @author JWCode Team
 * @since 3.1.0
 */
public class MicroCompactService {

    private static final Logger logger = Logger.getLogger(MicroCompactService.class.getName());

    private final MicroCompactConfig config;

    // 工具分类集合
    private static final Set<String> FILE_MODIFY_TOOLS = Set.of(
        "FileWriteTool", "FileEditTool", "EditTool", "NotebookEditTool", "MergeFilesTool");
    private static final Set<String> READ_TOOLS = Set.of(
        "FileReadTool", "BatchReadTool", "GlobTool", "GrepTool",
        "WebFetchTool", "WebSearchTool", "ToolSearchTool");
    private static final Set<String> COMMAND_TOOLS = Set.of(
        "BashTool", "PowerShellTool", "REPLTool");

    public MicroCompactService() {
        this(new MicroCompactConfig());
    }

    public MicroCompactService(MicroCompactConfig config) {
        this.config = config;
    }

    /**
     * 自动分级 — 根据工具名称和结果内容判断应使用的压缩等级。
     *
     * @param toolName 工具名称
     * @param content  工具结果内容
     * @return 压缩等级
     */
    public MicroCompactConfig.Tier classifyToolResult(String toolName, String content) {
        if (toolName == null) return MicroCompactConfig.Tier.MINIMAL;

        if (isError(content)) {
            return MicroCompactConfig.Tier.CRITICAL;
        }
        if (FILE_MODIFY_TOOLS.contains(toolName)) {
            return MicroCompactConfig.Tier.HIGH;
        }
        if (READ_TOOLS.contains(toolName)) {
            return MicroCompactConfig.Tier.MEDIUM;
        }
        if (COMMAND_TOOLS.contains(toolName)) {
            return MicroCompactConfig.Tier.LOW;
        }
        return MicroCompactConfig.Tier.MINIMAL;
    }

    /**
     * 对单条工具结果执行微压缩。
     *
     * @param toolName 工具名称
     * @param content  工具结果原始内容
     * @param tier     压缩等级（null 则自动分级）
     * @return 压缩后的内容
     */
    public String microCompact(String toolName, String content, MicroCompactConfig.Tier tier) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        if (tier == null) {
            tier = classifyToolResult(toolName, content);
        }
        // 如果当前 tier 的重要性低于配置的最低保留等级，先做字符截断
        // CRITICAL/HIGH/MEDIUM 使用专用压缩算法，LOW/MINIMAL 先截断
        if (tier.ordinal() > config.getMaxTier().ordinal()) {
            if (content.length() > config.getMaxRetainedChars()) {
                content = content.substring(0, config.getMaxRetainedChars()) + "\n...[已截断]";
            }
        }

        return switch (tier) {
            case CRITICAL -> compactCritical(toolName, content);
            case HIGH     -> compactHigh(toolName, content);
            case MEDIUM   -> compactMedium(toolName, content);
            case LOW      -> compactLow(toolName, content);
            case MINIMAL  -> compactMinimal(toolName, content);
        };
    }

    /**
     * 自动判断是否需要压缩并执行。
     *
     * @param toolName 工具名称
     * @param content  工具结果内容
     * @return 压缩后的内容（未触发压缩则返回原内容）
     */
    public String autoCompact(String toolName, String content) {
        if (!config.isAutoCompact()) return content;
        if (content == null || content.length() < config.getCharThreshold()) return content;

        MicroCompactConfig.Tier tier = classifyToolResult(toolName, content);
        String compacted = microCompact(toolName, content, tier);
        logger.fine("[MicroCompact] " + toolName + " (" + content.length()
            + " -> " + compacted.length() + " chars, tier=" + tier + ")");
        return compacted;
    }

    // ==================== 五级压缩实现 ====================

    private String compactCritical(String toolName, String content) {
        return "[" + toolName + " ERROR: " + truncate(content, 300) + "]";
    }

    private String compactHigh(String toolName, String content) {
        String paths = extractFilePaths(content);
        return "[" + toolName + ": modified " + paths + "]";
    }

    private String compactMedium(String toolName, String content) {
        String trimmed = trimHeadTail(content, 200);
        return "[" + toolName + ": " + trimmed + "]";
    }

    private String compactLow(String toolName, String content) {
        String exitInfo = content.contains("exit=0") ? "exit=0" : "exit!=0";
        String tail = content.length() > 200
            ? content.substring(content.length() - 200) : content;
        return "[" + toolName + " " + exitInfo + ": " + truncate(tail, 200) + "]";
    }

    private String compactMinimal(String toolName, String content) {
        return "[" + toolName + ": success]";
    }

    // ==================== 工具方法 ====================

    private boolean isError(String content) {
        if (content == null) return false;
        return content.startsWith("Error:")
            || content.contains("Exception")
            || content.contains("失败");
    }

    private String extractFilePaths(String result) {
        if (result == null || result.isEmpty()) return "unknown";
        StringBuilder paths = new StringBuilder();
        for (String line : result.split("[\\n;]")) {
            String trimmed = line.trim();
            if (trimmed.contains("/") || trimmed.contains("\\")
                || trimmed.endsWith(".java") || trimmed.endsWith(".ts")
                || trimmed.endsWith(".js") || trimmed.endsWith(".py")) {
                if (paths.length() > 0) paths.append(", ");
                paths.append(trimmed);
                if (paths.length() > 200) break;
            }
        }
        return paths.length() > 0 ? paths.toString() : "files";
    }

    private String trimHeadTail(String result, int maxLen) {
        if (result == null || result.isEmpty()) return "(empty)";
        if (result.length() <= maxLen) return result;
        return result.substring(0, maxLen / 2) + "..."
            + result.substring(result.length() - maxLen / 2);
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...[已截断]";
    }

    public MicroCompactConfig getConfig() { return config; }
}

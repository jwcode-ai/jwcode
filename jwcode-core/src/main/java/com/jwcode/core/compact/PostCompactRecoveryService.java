package com.jwcode.core.compact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Logger;

/**
 * PostCompactRecoveryService — 压缩后自动恢复工作上下文。
 *
 * <p>对标 Claude Code 压缩后自动重读最近文件的机制。
 * 压缩完成后自动 re-read 最近访问过的文件，确保模型无需手动重新打开代码。</p>
 *
 * <h3>预算控制</h3>
 * <ul>
 *   <li>最多重读 {@link #MAX_FILES} 个文件</li>
 *   <li>每个文件最多 {@link #MAX_CHARS_PER_FILE} 字符 (≈5K tokens)</li>
 *   <li>总预算 {@link #MAX_TOTAL_CHARS} 字符 (≈50K tokens)</li>
 * </ul>
 *
 * @author JWCode Team
 * @since 3.3.0
 */
public class PostCompactRecoveryService {

    private static final Logger logger = Logger.getLogger(PostCompactRecoveryService.class.getName());

    /** 最多重读文件数 */
    private static final int MAX_FILES = 5;
    /** 每个文件最多读取字符数 (≈5K tokens) */
    private static final int MAX_CHARS_PER_FILE = 20_000;
    /** 总字符预算 (≈50K tokens) */
    private static final int MAX_TOTAL_CHARS = 200_000;

    /** 单例实例 */
    private static final PostCompactRecoveryService INSTANCE = new PostCompactRecoveryService();

    /** 获取单例实例 */
    public static PostCompactRecoveryService getInstance() {
        return INSTANCE;
    }

    /** 最近访问的文件路径 (线程安全，最多保留 30 个) */
    private final ConcurrentLinkedDeque<String> recentFiles = new ConcurrentLinkedDeque<>();

    /** 最大记忆文件数 */
    private static final int MAX_RECENT = 30;

    /**
     * 记录一次文件访问。
     * 在文件读取工具执行成功后调用。
     *
     * @param filePath 被访问的文件绝对路径
     */
    public void recordFileAccess(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        // 去重：如果已存在则先移除
        recentFiles.remove(filePath);
        recentFiles.addLast(filePath);

        // 保持容量
        while (recentFiles.size() > MAX_RECENT) {
            recentFiles.pollFirst();
        }
    }

    /**
     * 记录批量文件访问。
     */
    public void recordFileAccesses(Collection<String> filePaths) {
        if (filePaths == null) return;
        for (String path : filePaths) {
            recordFileAccess(path);
        }
    }

    /**
     * 压缩后恢复 — 重新读取最近访问的文件，构建上下文附件。
     *
     * @param workspaceDir 工作区根目录（用于解析相对路径）
     * @return 上下文附件文本，可直接注入到系统消息中；无文件可恢复时返回 null
     */
    public String recoverAfterCompact(String workspaceDir) {
        if (recentFiles.isEmpty()) {
            logger.fine("[PostCompactRecovery] 无最近文件需要恢复");
            return null;
        }

        // 倒序取最近的文件（去重后最新的在前）
        List<String> candidates = new ArrayList<>(recentFiles);
        Collections.reverse(candidates);

        StringBuilder context = new StringBuilder();
        context.append("<post_compact_recovery>\n");
        context.append("The following files were recently accessed before compaction. ");
        context.append("They have been automatically re-read so you can continue working ");
        context.append("without manually re-opening them:\n\n");

        int filesRead = 0;
        int totalChars = 0;

        for (String filePath : candidates) {
            if (filesRead >= MAX_FILES || totalChars >= MAX_TOTAL_CHARS) {
                break;
            }

            Path resolvedPath = resolvePath(filePath, workspaceDir);
            if (!Files.isRegularFile(resolvedPath)) {
                continue;
            }

            try {
                String content = Files.readString(resolvedPath, StandardCharsets.UTF_8);
                if (content.length() > MAX_CHARS_PER_FILE) {
                    // 保留首尾各一半
                    int half = MAX_CHARS_PER_FILE / 2;
                    content = content.substring(0, half)
                        + "\n\n... [file truncated: "
                        + (content.length() - MAX_CHARS_PER_FILE)
                        + " characters omitted] ...\n\n"
                        + content.substring(content.length() - half);
                }

                if (totalChars + content.length() > MAX_TOTAL_CHARS && filesRead > 0) {
                    // 预算不足，至少保证已读文件的内容完整
                    logger.fine("[PostCompactRecovery] Token 预算用尽，已恢复 " + filesRead + " 个文件");
                    break;
                }

                context.append("<file path=\"").append(escapeXml(filePath)).append("\">\n");
                context.append("```").append(detectLanguage(filePath)).append("\n");
                context.append(content).append("\n");
                context.append("```\n");
                context.append("</file>\n\n");

                filesRead++;
                totalChars += content.length();

            } catch (IOException e) {
                logger.fine("[PostCompactRecovery] 无法读取文件: " + filePath + " — " + e.getMessage());
            }
        }

        if (filesRead == 0) {
            return null;
        }

        context.append("</post_compact_recovery>");

        logger.info("[PostCompactRecovery] 压缩后恢复: " + filesRead + " 个文件, "
            + totalChars + " chars");

        return context.toString();
    }

    /**
     * 获取最近访问的文件路径列表（用于调试/UI）。
     */
    public List<String> getRecentFiles() {
        List<String> files = new ArrayList<>(recentFiles);
        Collections.reverse(files);
        return Collections.unmodifiableList(files);
    }

    /**
     * 清空最近文件记录。
     */
    public void clear() {
        recentFiles.clear();
    }

    /**
     * 获取最近文件数量。
     */
    public int getRecentFileCount() {
        return recentFiles.size();
    }

    // ==================== 私有方法 ====================

    private Path resolvePath(String filePath, String workspaceDir) {
        Path path = Paths.get(filePath);
        if (path.isAbsolute()) {
            return path;
        }
        if (workspaceDir != null && !workspaceDir.isBlank()) {
            return Paths.get(workspaceDir).resolve(filePath);
        }
        return path.toAbsolutePath();
    }

    private String detectLanguage(String filePath) {
        String name = filePath.toLowerCase();
        if (name.endsWith(".java")) return "java";
        if (name.endsWith(".py")) return "python";
        if (name.endsWith(".js")) return "javascript";
        if (name.endsWith(".ts") || name.endsWith(".tsx")) return "typescript";
        if (name.endsWith(".go")) return "go";
        if (name.endsWith(".rs")) return "rust";
        if (name.endsWith(".cpp") || name.endsWith(".cc") || name.endsWith(".cxx")) return "cpp";
        if (name.endsWith(".c") || name.endsWith(".h")) return "c";
        if (name.endsWith(".json")) return "json";
        if (name.endsWith(".xml")) return "xml";
        if (name.endsWith(".yaml") || name.endsWith(".yml")) return "yaml";
        if (name.endsWith(".md")) return "markdown";
        if (name.endsWith(".sql")) return "sql";
        if (name.endsWith(".sh") || name.endsWith(".bash")) return "bash";
        if (name.endsWith(".html")) return "html";
        if (name.endsWith(".css")) return "css";
        if (name.endsWith(".kt") || name.endsWith(".kts")) return "kotlin";
        if (name.endsWith(".swift")) return "swift";
        return "";
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

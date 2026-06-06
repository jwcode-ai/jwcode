package com.jwcode.core.service;

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
 */
public class PostCompactRecoveryService {

    private static final Logger logger = Logger.getLogger(PostCompactRecoveryService.class.getName());

    private static final int MAX_FILES = 5;
    private static final int MAX_CHARS_PER_FILE = 20_000;
    private static final int MAX_TOTAL_CHARS = 200_000;

    private static final PostCompactRecoveryService INSTANCE = new PostCompactRecoveryService();

    public static PostCompactRecoveryService getInstance() {
        return INSTANCE;
    }

    private final ConcurrentLinkedDeque<String> recentFiles = new ConcurrentLinkedDeque<>();
    private static final int MAX_RECENT = 30;

    public void recordFileAccess(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        recentFiles.remove(filePath);
        recentFiles.addLast(filePath);
        while (recentFiles.size() > MAX_RECENT) {
            recentFiles.pollFirst();
        }
    }

    public void recordFileAccesses(Collection<String> filePaths) {
        if (filePaths == null) return;
        for (String path : filePaths) {
            recordFileAccess(path);
        }
    }

    public String recoverAfterCompact(String workspaceDir) {
        if (recentFiles.isEmpty()) {
            return null;
        }

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
            if (filesRead >= MAX_FILES || totalChars >= MAX_TOTAL_CHARS) break;

            Path resolvedPath = resolvePath(filePath, workspaceDir);
            if (!Files.isRegularFile(resolvedPath)) continue;

            try {
                String content = Files.readString(resolvedPath, StandardCharsets.UTF_8);
                if (content.length() > MAX_CHARS_PER_FILE) {
                    int half = MAX_CHARS_PER_FILE / 2;
                    content = content.substring(0, half)
                        + "\n\n... [file truncated: "
                        + (content.length() - MAX_CHARS_PER_FILE)
                        + " characters omitted] ...\n\n"
                        + content.substring(content.length() - half);
                }

                if (totalChars + content.length() > MAX_TOTAL_CHARS && filesRead > 0) break;

                context.append("<file path=\"").append(escapeXml(filePath)).append("\">\n");
                context.append("```").append(detectLanguage(filePath)).append("\n");
                context.append(content).append("\n");
                context.append("```\n");
                context.append("</file>\n\n");

                filesRead++;
                totalChars += content.length();

            } catch (IOException e) {
                logger.fine("[PostCompactRecovery] Cannot read: " + filePath);
            }
        }

        if (filesRead == 0) return null;

        context.append("</post_compact_recovery>");
        logger.info("[PostCompactRecovery] Recovered " + filesRead + " files, " + totalChars + " chars");
        return context.toString();
    }

    public List<String> getRecentFiles() {
        List<String> files = new ArrayList<>(recentFiles);
        Collections.reverse(files);
        return Collections.unmodifiableList(files);
    }

    public void clear() {
        recentFiles.clear();
    }

    public int getRecentFileCount() {
        return recentFiles.size();
    }

    private Path resolvePath(String filePath, String workspaceDir) {
        Path path = Paths.get(filePath);
        if (path.isAbsolute()) return path;
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

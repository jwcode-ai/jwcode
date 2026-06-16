package com.jwcode.core.context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * BuiltinContextSources — 注册所有内置上下文源。
 *
 * <p>这些源对应 opencode 的 {@code core/date}, {@code core/environment} 等，
 * 但适配 jwcode 的现有基础设施。
 */
public final class BuiltinContextSources {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private BuiltinContextSources() {}

    /**
     * 注册所有内置上下文源到 registry。
     */
    public static void registerAll(ContextRegistry registry) {
        registry.register(dateSource(), "system");
        registry.register(environmentSource(), "system");
        registry.register(workspaceSource(), "system");
    }

    // ==================== 内置源 ====================

    /**
     * core/date — 当前日期。
     */
    public static ContextSource<String> dateSource() {
        return ContextSource.<String>builder("core/date")
            .loader(() -> ContextValue.available(LocalDate.now().format(DATE_FMT)))
            .baselineRenderer(date -> "Current date: " + date + ".")
            .updateRenderer(date -> "[System: Today's date is " + date + ".]")
            .build();
    }

    /**
     * core/environment — 工作目录、平台、Git 状态。
     */
    public static ContextSource<String> environmentSource() {
        return ContextSource.<String>builder("core/environment")
            .loader(() -> {
                String cwd = System.getProperty("user.dir", "unknown");
                String os = System.getProperty("os.name", "unknown");
                String java = System.getProperty("java.version", "unknown");
                return ContextValue.available(
                    "cwd=" + cwd + ", os=" + os + ", java=" + java
                );
            })
            .baselineRenderer(env ->
                "Environment:\n" +
                "  working directory: " + extractField(env, "cwd") + "\n" +
                "  platform: " + extractField(env, "os") + "\n" +
                "  java version: " + extractField(env, "java") + "\n"
            )
            .updateRenderer(env ->
                "[System: Working directory changed to " + extractField(env, "cwd") + "]"
            )
            .build();
    }

    /**
     * core/workspace — 工作区文件结构摘要。
     */
    public static ContextSource<String> workspaceSource() {
        return ContextSource.<String>builder("core/workspace")
            .loader(() -> {
                String cwd = System.getProperty("user.dir", ".");
                try (var files = Files.list(Path.of(cwd))) {
                    long dirs = files.filter(Files::isDirectory).count();
                    long filesCount;
                    try (var f2 = Files.list(Path.of(cwd))) {
                        filesCount = f2.filter(p -> !Files.isDirectory(p)).count();
                    }
                    return ContextValue.available("dirs=" + dirs + ", files=" + filesCount);
                } catch (Exception e) {
                    return ContextValue.unavailable();
                }
            })
            .baselineRenderer(ws ->
                "Workspace has " + extractField(ws, "dirs") + " directories, "
                    + extractField(ws, "files") + " files in root."
            )
            .build();
    }

    // ==================== 工具 ====================

    private static String extractField(String kvStr, String key) {
        for (String part : kvStr.split(",")) {
            part = part.trim();
            if (part.startsWith(key + "=")) {
                return part.substring(key.length() + 1);
            }
        }
        return "unknown";
    }
}

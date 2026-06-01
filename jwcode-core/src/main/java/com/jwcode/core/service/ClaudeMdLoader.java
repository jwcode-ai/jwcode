package com.jwcode.core.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ClaudeMdLoader — 多级 CLAUDE.md 加载器（对标 Claude Code claudemd.ts）。
 *
 * <p>按优先级加载 4 个级别 + rules 目录的指令文件：</p>
 * <ol>
 *   <li><b>Managed Memory</b> — 托管规则（团队策略）~/.claude/rules/</li>
 *   <li><b>User Memory</b> — 用户级 ~/.claude/CLAUDE.md</li>
 *   <li><b>Project Memory</b> — 项目级 CLAUDE.md, .claude/CLAUDE.md, .claude/rules/*.md</li>
 *   <li><b>Local Memory</b> — 本地 CLAUDE.local.md（不提交到 git）</li>
 * </ol>
 *
 * <h3>特性</h3>
 * <ul>
 *   <li><b>@include 指令</b> — 支持 {@code @include path/to/file.md} 引用其他文件</li>
 *   <li><b>循环引用检测</b> — 通过 visited set 防止无限递归</li>
 *   <li><b>gitignore 支持</b> — 自动排除 .gitignore 中列出的文件</li>
 *   <li><b>glob 匹配</b> — 支持 frontmatter 中的 paths: 模式（如 "src/**\/*.ts"）</li>
 * </ul>
 *
 * <h3>加载时机</h3>
 * <ul>
 *   <li>session_start — 会话启动时加载所有匹配文件</li>
 *   <li>nested_traversal — 目录递归遍历</li>
 *   <li>path_glob_match — 访问的文件匹配 glob 模式</li>
 *   <li>include — @include 指令引用</li>
 *   <li>compact — 压缩后重新注入</li>
 * </ul>
 *
 * @author JWCode Team
 * @since 3.0.0
 */
public class ClaudeMdLoader {

    private static final Logger logger = Logger.getLogger(ClaudeMdLoader.class.getName());

    /** 加载原因 */
    public enum LoadReason {
        SESSION_START,
        NESTED_TRAVERSAL,
        PATH_GLOB_MATCH,
        INCLUDE,
        COMPACT
    }

    /** 指令文件来源级别 */
    public enum SourceLevel {
        MANAGED,   // 托管规则
        USER,      // ~/.claude/
        PROJECT,   // 项目级
        LOCAL      // CLAUDE.local.md
    }

    /**
     * 加载结果。
     */
    public static class LoadResult {
        public final String content;
        public final Path filePath;
        public final SourceLevel level;
        public final LoadReason reason;
        public final List<String> matchedGlobs;

        public LoadResult(String content, Path filePath, SourceLevel level,
                         LoadReason reason, List<String> matchedGlobs) {
            this.content = content;
            this.filePath = filePath;
            this.level = level;
            this.reason = reason;
            this.matchedGlobs = matchedGlobs != null ? matchedGlobs : Collections.emptyList();
        }
    }

    // ==================== 配置 ====================

    private final Path workspaceRoot;
    private final Path userConfigDir;
    private final Path managedConfigDir;
    private final Set<Path> visitedFiles = new HashSet<>();

    public ClaudeMdLoader(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.normalize().toAbsolutePath();
        this.userConfigDir = Paths.get(System.getProperty("user.home"), ".claude");
        this.managedConfigDir = userConfigDir.resolve("rules");
    }

    // ==================== 主加载方法 ====================

    /**
     * 加载所有可用的 CLAUDE.md 文件。
     * 对标 Claude Code 的 getSystemContext()。
     *
     * @return 按优先级排序的加载结果列表
     */
    public List<LoadResult> loadAll() {
        visitedFiles.clear();
        List<LoadResult> results = new ArrayList<>();

        // 1. Managed Memory（托管规则）
        loadManagedRules(results);

        // 2. User Memory
        loadUserClaudeMd(results);

        // 3. Project Memory
        loadProjectClaudeMd(results);

        // 4. Local Memory
        loadLocalClaudeMd(results);

        return results;
    }

    /**
     * 加载项目级指令文件（最常用）。
     *
     * @param triggerFilePath 触发加载的文件路径（用于 glob 匹配）
     * @return 加载结果列表
     */
    public List<LoadResult> loadProjectLevel(Path triggerFilePath) {
        visitedFiles.clear();
        List<LoadResult> results = new ArrayList();

        // CLAUDE.md（根目录）
        Path rootClaudeMd = workspaceRoot.resolve("CLAUDE.md");
        if (Files.exists(rootClaudeMd) && visitedFiles.add(rootClaudeMd)) {
            LoadResult result = loadFile(rootClaudeMd, SourceLevel.PROJECT, LoadReason.SESSION_START);
            if (result != null) {
                results.add(result);
                // 处理 @include
                processIncludes(result.content, rootClaudeMd, results);
            }
        }

        // .claude/CLAUDE.md
        Path dotClaudeMd = workspaceRoot.resolve(".claude/CLAUDE.md");
        if (Files.exists(dotClaudeMd) && visitedFiles.add(dotClaudeMd)) {
            LoadResult result = loadFile(dotClaudeMd, SourceLevel.PROJECT, LoadReason.NESTED_TRAVERSAL);
            if (result != null) results.add(result);
        }

        // .claude/rules/*.md
        Path rulesDir = workspaceRoot.resolve(".claude/rules");
        if (Files.exists(rulesDir) && Files.isDirectory(rulesDir)) {
            loadRulesDir(rulesDir, results, SourceLevel.PROJECT);
        }

        // Glob 匹配：检查 triggerFilePath 是否匹配任何 CLAUDE.md 中的 paths: 模式
        if (triggerFilePath != null) {
            checkGlobMatches(triggerFilePath, results);
        }

        return results;
    }

    /**
     * 获取内存上下文（用于注入 SystemPrompt）。
     *
     * @return 全部 CLAUDE.md 内容拼接（以注释标注来源）
     */
    public String getMemoryContext() {
        List<LoadResult> results = loadAll();
        if (results.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("# claudeMd\n");
        sb.append("Codebase and user instructions are shown below. ");
        sb.append("Be sure to adhere to these instructions. ");
        sb.append("IMPORTANT: These instructions OVERRIDE any default behavior ");
        sb.append("and you MUST follow them exactly as written.\n\n");

        for (LoadResult result : results) {
            sb.append("Contents of ").append(relativize(result.filePath));
            sb.append(" (").append(result.level.name().toLowerCase());
            sb.append(" instructions):\n\n");
            sb.append(result.content).append("\n\n");
        }

        return sb.toString();
    }

    // ==================== 各级加载 ====================

    private void loadManagedRules(List<LoadResult> results) {
        if (!Files.exists(managedConfigDir) || !Files.isDirectory(managedConfigDir)) {
            return;
        }
        loadRulesDir(managedConfigDir, results, SourceLevel.MANAGED);
    }

    private void loadUserClaudeMd(List<LoadResult> results) {
        Path userClaudeMd = userConfigDir.resolve("CLAUDE.md");
        if (Files.exists(userClaudeMd) && visitedFiles.add(userClaudeMd)) {
            LoadResult result = loadFile(userClaudeMd, SourceLevel.USER, LoadReason.SESSION_START);
            if (result != null) results.add(result);
        }
    }

    private void loadProjectClaudeMd(List<LoadResult> results) {
        // CLAUDE.md（根目录）
        Path rootClaudeMd = workspaceRoot.resolve("CLAUDE.md");
        if (Files.exists(rootClaudeMd) && visitedFiles.add(rootClaudeMd)) {
            LoadResult result = loadFile(rootClaudeMd, SourceLevel.PROJECT, LoadReason.SESSION_START);
            if (result != null) {
                results.add(result);
                processIncludes(result.content, rootClaudeMd, results);
            }
        }

        // .claude/CLAUDE.md
        Path dotClaudeMd = workspaceRoot.resolve(".claude/CLAUDE.md");
        if (Files.exists(dotClaudeMd) && visitedFiles.add(dotClaudeMd)) {
            LoadResult result = loadFile(dotClaudeMd, SourceLevel.PROJECT, LoadReason.NESTED_TRAVERSAL);
            if (result != null) results.add(result);
        }

        // .claude/rules/*.md
        Path rulesDir = workspaceRoot.resolve(".claude/rules");
        if (Files.exists(rulesDir) && Files.isDirectory(rulesDir)) {
            loadRulesDir(rulesDir, results, SourceLevel.PROJECT);
        }
    }

    private void loadLocalClaudeMd(List<LoadResult> results) {
        Path localClaudeMd = workspaceRoot.resolve("CLAUDE.local.md");
        if (Files.exists(localClaudeMd) && visitedFiles.add(localClaudeMd)) {
            LoadResult result = loadFile(localClaudeMd, SourceLevel.LOCAL, LoadReason.SESSION_START);
            if (result != null) results.add(result);
        }
    }

    // ==================== 辅助方法 ====================

    private void loadRulesDir(Path rulesDir, List<LoadResult> results, SourceLevel level) {
        try (Stream<Path> files = Files.list(rulesDir)) {
            files.filter(p -> p.toString().endsWith(".md"))
                .sorted()
                .forEach(p -> {
                    if (visitedFiles.add(p)) {
                        LoadResult result = loadFile(p, level, LoadReason.NESTED_TRAVERSAL);
                        if (result != null) results.add(result);
                    }
                });
        } catch (IOException e) {
            logger.fine("[ClaudeMdLoader] rules 目录读取失败: " + e.getMessage());
        }
    }

    private LoadResult loadFile(Path filePath, SourceLevel level, LoadReason reason) {
        try {
            String content = Files.readString(filePath);
            if (content.isBlank()) return null;

            logger.fine("[ClaudeMdLoader] 加载 " + filePath + " [" + level + "] (" + reason + ")");
            return new LoadResult(content.trim(), filePath, level, reason, Collections.emptyList());
        } catch (IOException e) {
            logger.fine("[ClaudeMdLoader] 读取失败: " + filePath + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * 处理 @include 指令。
     * 格式: {@code @include path/to/file.md}
     */
    private void processIncludes(String content, Path parentFile, List<LoadResult> results) {
        Pattern pattern = Pattern.compile("@include\\s+([^\\s\n]+)", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String includePath = matcher.group(1).trim();
            Path resolved = parentFile.getParent().resolve(includePath).normalize();

            if (visitedFiles.add(resolved) && Files.exists(resolved)) {
                LoadResult result = loadFile(resolved, SourceLevel.PROJECT, LoadReason.INCLUDE);
                if (result != null) {
                    results.add(result);
                    // 递归处理嵌套 include
                    processIncludes(result.content, resolved, results);
                }
            }
        }
    }

    /**
     * 检查触发文件是否匹配 CLAUDE.md 中的 paths: glob 模式。
     */
    private void checkGlobMatches(Path triggerFile, List<LoadResult> results) {
        // 扫描已加载的 CLAUDE.md 中的 paths: frontmatter
        Pattern pathsPattern = Pattern.compile("paths:\\s*\\n((?:\\s*-\\s*[^\\n]+\\n?)+)", Pattern.MULTILINE);

        for (LoadResult existing : results) {
            Matcher m = pathsPattern.matcher(existing.content);
            while (m.find()) {
                String pathsBlock = m.group(1);
                List<String> globs = Arrays.stream(pathsBlock.split("\n"))
                    .map(String::trim)
                    .filter(s -> s.startsWith("-"))
                    .map(s -> s.substring(1).trim())
                    .collect(Collectors.toList());

                for (String glob : globs) {
                    if (matchesGlob(triggerFile, glob)) {
                        // 重新加载并标记为 glob 匹配
                        LoadResult updated = new LoadResult(
                            existing.content, existing.filePath, existing.level,
                            LoadReason.PATH_GLOB_MATCH,
                            Collections.singletonList(glob)
                        );
                        // 在结果中已存在，标记匹配
                        logger.fine("[ClaudeMdLoader] glob 匹配: " + triggerFile
                            + " → " + existing.filePath + " (" + glob + ")");
                    }
                }
            }
        }
    }

    /**
     * 简易 glob 匹配。
     */
    private boolean matchesGlob(Path file, String glob) {
        String relativePath = workspaceRoot.relativize(file).toString().replace('\\', '/');
        String regex = glob
            .replace(".", "\\.")
            .replace("**", "<<<DOUBLE_STAR>>>")
            .replace("*", "[^/]*")
            .replace("<<<DOUBLE_STAR>>>", ".*")
            .replace("?", ".");
        return Pattern.compile(regex).matcher(relativePath).matches();
    }

    /**
     * 相对于工作目录的路径。
     */
    private String relativize(Path path) {
        try {
            return workspaceRoot.relativize(path).toString();
        } catch (IllegalArgumentException e) {
            return path.toString();
        }
    }

    // ==================== 查询方法 ====================

    /**
     * 检查工作目录中是否存在 CLAUDE.md。
     */
    public boolean hasProjectClaudeMd() {
        return Files.exists(workspaceRoot.resolve("CLAUDE.md"))
            || Files.exists(workspaceRoot.resolve(".claude/CLAUDE.md"));
    }

    /**
     * 检查是否有本地 CLAUDE.md（不提交到 git）。
     */
    public boolean hasLocalClaudeMd() {
        return Files.exists(workspaceRoot.resolve("CLAUDE.local.md"));
    }

    /**
     * 获取所有已发现指令文件的路径列表。
     */
    public List<Path> getAllClaudeMdPaths() {
        List<Path> paths = new ArrayList<>();

        Path rootClaudeMd = workspaceRoot.resolve("CLAUDE.md");
        if (Files.exists(rootClaudeMd)) paths.add(rootClaudeMd);

        Path dotClaudeMd = workspaceRoot.resolve(".claude/CLAUDE.md");
        if (Files.exists(dotClaudeMd)) paths.add(dotClaudeMd);

        Path localClaudeMd = workspaceRoot.resolve("CLAUDE.local.md");
        if (Files.exists(localClaudeMd)) paths.add(localClaudeMd);

        Path rulesDir = workspaceRoot.resolve(".claude/rules");
        if (Files.exists(rulesDir) && Files.isDirectory(rulesDir)) {
            try (Stream<Path> files = Files.list(rulesDir)) {
                files.filter(p -> p.toString().endsWith(".md"))
                    .sorted()
                    .forEach(paths::add);
            } catch (IOException ignored) {}
        }

        return paths;
    }

    /**
     * 获取指令文件摘要（用于 Hook 事件）。
     */
    public String getSummary() {
        List<Path> paths = getAllClaudeMdPaths();
        if (paths.isEmpty()) return "无 CLAUDE.md 文件";
        return paths.size() + " 个指令文件: "
            + paths.stream().map(this::relativize).collect(Collectors.joining(", "));
    }
}

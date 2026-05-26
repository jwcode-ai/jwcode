package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * FileNameCache — 文件名索引缓存。
 *
 * <p>为 GlobTool 提供基于文件名的快速检索，避免每次搜索都全量遍历目录树。
 * 缓存存储在 {@code .jwcode/index/filename-cache/} 目录下。</p>
 *
 * <p>核心机制：
 * <ul>
 *   <li>首次扫描：遍历目录树，建立 {相对路径 → 文件元数据} 索引</li>
 *   <li>增量更新：通过文件最后修改时间检测变更，仅重新索引变更的目录</li>
 *   <li>持久化：索引以 JSON 格式保存到磁盘，JVM 重启后复用</li>
 *   <li>TTL 淘汰：超过 30 分钟未访问的缓存自动失效</li>
 * </ul>
 * </p>
 *
 * <p>线程安全：使用 ConcurrentHashMap，支持并发读。</p>
 */
public class FileNameCache {

    private static final Logger logger = Logger.getLogger(FileNameCache.class.getName());

    /** 缓存文件名 */
    private static final String CACHE_FILE_NAME = "filename-index.json";

    /** 缓存元数据文件名 */
    private static final String META_FILE_NAME = "filename-meta.json";

    /** 默认 TTL（毫秒） */
    private static final long DEFAULT_TTL_MS = 30 * 60 * 1000;

    /** 缓存清理间隔（毫秒） */
    private static final long CLEANUP_INTERVAL_MS = 5 * 60 * 1000;

    private final ObjectMapper mapper;

    /** 工作区根路径 */
    private final Path workspaceRoot;

    /** 缓存存储目录 */
    private final Path cacheDir;

    /** 文件名索引：相对路径 → 文件条目 */
    private final Map<String, CacheEntry> index;

    /** 目录最后修改时间缓存：目录路径 → 最后修改时间 */
    private final Map<String, Long> dirModTimeCache;

    /** 最后访问时间 */
    private volatile long lastAccessTime;

    /** 是否已脏（需要持久化） */
    private volatile boolean dirty;

    /** 定时清理任务 */
    private final ScheduledExecutorService cleanupScheduler;

    /** 跳过的目录模式 */
    private static final Set<String> SKIPPED_DIRS = Set.of(
        ".git", ".svn", ".hg", ".cvs",
        "target", "build", "dist", "out", "bin",
        "node_modules", "bower_components", "vendor",
        "__pycache__", ".venv", "venv", ".env",
        ".gradle", ".idea", ".cache", ".temp", "tmp", ".tmp"
    );

    /** 跳过的以点开头的目录（白名单除外） */
    private static final Set<String> DOT_DIR_ALLOWLIST = Set.of(
        ".github", ".vscode", ".idea", ".settings", ".jwcode"
    );

    /**
     * 缓存条目
     */
    static class CacheEntry {
        /** 相对路径 */
        String relativePath;
        /** 文件名 */
        String fileName;
        /** 父目录相对路径 */
        String parentDir;
        /** 文件大小 */
        long fileSize;
        /** 最后修改时间 */
        long lastModified;
        /** 是否为目录 */
        boolean isDirectory;
        /** 文件扩展名（小写） */
        String extension;

        CacheEntry() {}

        CacheEntry(String relativePath, String fileName, String parentDir,
                   long fileSize, long lastModified, boolean isDirectory) {
            this.relativePath = relativePath;
            this.fileName = fileName;
            this.parentDir = parentDir;
            this.fileSize = fileSize;
            this.lastModified = lastModified;
            this.isDirectory = isDirectory;
            int dotIdx = fileName.lastIndexOf('.');
            this.extension = dotIdx > 0 ? fileName.substring(dotIdx + 1).toLowerCase() : "";
        }

        public String getRelativePath() { return relativePath; }
        public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getParentDir() { return parentDir; }
        public void setParentDir(String parentDir) { this.parentDir = parentDir; }
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        public long getLastModified() { return lastModified; }
        public void setLastModified(long lastModified) { this.lastModified = lastModified; }
        public boolean isDirectory() { return isDirectory; }
        public void setDirectory(boolean directory) { isDirectory = directory; }
        public String getExtension() { return extension; }
        public void setExtension(String extension) { this.extension = extension; }
    }

    /**
     * 缓存元数据
     */
    static class CacheMeta {
        String workspaceRoot;
        long buildTime;
        int entryCount;
        Map<String, Long> dirTimestamps = new HashMap<>();

        public String getWorkspaceRoot() { return workspaceRoot; }
        public void setWorkspaceRoot(String workspaceRoot) { this.workspaceRoot = workspaceRoot; }
        public long getBuildTime() { return buildTime; }
        public void setBuildTime(long buildTime) { this.buildTime = buildTime; }
        public int getEntryCount() { return entryCount; }
        public void setEntryCount(int entryCount) { this.entryCount = entryCount; }
        public Map<String, Long> getDirTimestamps() { return dirTimestamps; }
        public void setDirTimestamps(Map<String, Long> dirTimestamps) { this.dirTimestamps = dirTimestamps; }
    }

    public FileNameCache(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.normalize().toAbsolutePath();
        this.cacheDir = workspaceRoot.resolve(".jwcode/index/filename-cache");
        this.mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
        this.index = new ConcurrentHashMap<>();
        this.dirModTimeCache = new ConcurrentHashMap<>();
        this.lastAccessTime = System.currentTimeMillis();
        this.dirty = false;

        // 加载持久化缓存
        loadFromDisk();

        // 启动定时清理
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "filename-cache-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupScheduler.scheduleAtFixedRate(
            this::cleanup, CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);

        logger.info("[FileNameCache] 初始化完成 | workspaceRoot=" + this.workspaceRoot
            + " | 缓存条目数=" + index.size());
    }

    /**
     * 按 glob 模式搜索文件（从缓存中匹配）。
     *
     * @param pattern glob 模式
     * @param maxResults 最大结果数
     * @return 匹配的绝对路径列表
     */
    public List<String> search(String pattern, int maxResults) {
        return search(pattern, false, null, maxResults);
    }

    /**
     * 按模式搜索文件（从缓存中匹配）。
     *
     * @param pattern 搜索模式
     * @param isRegex 是否为正则表达式
     * @param excludePattern 排除模式
     * @param maxResults 最大结果数
     * @return 匹配的绝对路径列表
     */
    public List<String> search(String pattern, boolean isRegex, String excludePattern, int maxResults) {
        lastAccessTime = System.currentTimeMillis();
        List<String> results = new ArrayList<>();

        java.util.regex.Pattern compiledPattern;
        if (isRegex) {
            compiledPattern = java.util.regex.Pattern.compile(pattern);
        } else {
            compiledPattern = compileGlobToRegex(pattern);
        }

        java.util.regex.Pattern compiledExclude = null;
        if (excludePattern != null && !excludePattern.isEmpty()) {
            compiledExclude = excludePattern.startsWith("regex:")
                ? java.util.regex.Pattern.compile(excludePattern.substring(6))
                : compileGlobToRegex(excludePattern);
        }

        for (CacheEntry entry : index.values()) {
            if (results.size() >= maxResults) break;
            if (entry.isDirectory) continue;

            String relPath = entry.relativePath.replace('\\', '/');
            if (compiledPattern.matcher(relPath).matches()
                || compiledPattern.matcher(entry.fileName).matches()) {
                if (compiledExclude == null || !compiledExclude.matcher(relPath).matches()) {
                    results.add(workspaceRoot.resolve(entry.relativePath).normalize().toString());
                }
            }
        }

        return results;
    }

    /**
     * 按扩展名过滤文件。
     *
     * @param extensions 扩展名列表（小写，不含点）
     * @return 匹配的绝对路径列表
     */
    public List<String> searchByExtension(Set<String> extensions) {
        lastAccessTime = System.currentTimeMillis();
        return index.values().stream()
            .filter(e -> !e.isDirectory && extensions.contains(e.extension))
            .map(e -> workspaceRoot.resolve(e.relativePath).normalize().toString())
            .collect(Collectors.toList());
    }

    /**
     * 检查缓存是否有效（目录时间戳无变化）。
     */
    public boolean isValid() {
        if (index.isEmpty()) return false;

        // 检查每个缓存过的目录的时间戳
        for (Map.Entry<String, Long> entry : dirModTimeCache.entrySet()) {
            Path dirPath = workspaceRoot.resolve(entry.getKey());
            if (!Files.exists(dirPath)) return false;
            long currentModTime = getDirMaxModifiedTime(dirPath);
            if (currentModTime != entry.getValue()) return false;
        }

        return true;
    }

    /**
     * 重建缓存（全量扫描）。
     */
    public synchronized void rebuild() {
        logger.info("[FileNameCache] 开始重建缓存...");
        index.clear();
        dirModTimeCache.clear();

        try {
            Files.walkFileTree(workspaceRoot, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName().toString();

                    // 跳过隐藏目录（白名单除外）
                    if (dirName.startsWith(".") && !DOT_DIR_ALLOWLIST.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    // 跳过常见噪音目录
                    if (SKIPPED_DIRS.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    // 跳过缓存自己的目录
                    if (dir.startsWith(cacheDir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    addEntry(file, attrs);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });

            // 记录目录时间戳
            recordDirTimestamps();

            dirty = true;
            saveToDisk();
            logger.info("[FileNameCache] 缓存重建完成 | 条目数=" + index.size());
        } catch (IOException e) {
            logger.warning("[FileNameCache] 缓存重建失败: " + e.getMessage());
        }
    }

    /**
     * 增量更新：检查变更并刷新。
     */
    public synchronized void refresh() {
        if (index.isEmpty()) {
            rebuild();
            return;
        }

        // 检查每个目录是否有变更
        boolean changed = false;
        for (Map.Entry<String, Long> entry : dirModTimeCache.entrySet()) {
            Path dirPath = workspaceRoot.resolve(entry.getKey());
            if (!Files.exists(dirPath)) {
                // 目录被删除，全量重建
                rebuild();
                return;
            }
            long currentModTime = getDirMaxModifiedTime(dirPath);
            if (currentModTime != entry.getValue()) {
                changed = true;
                // 重新索引该目录
                reindexDir(dirPath);
            }
        }

        if (changed) {
            recordDirTimestamps();
            dirty = true;
            saveToDisk();
        }
    }

    /**
     * 关闭缓存，释放资源。
     */
    public void shutdown() {
        cleanupScheduler.shutdown();
        if (dirty) {
            saveToDisk();
        }
    }

    // ==================== 私有方法 ====================

    private void addEntry(Path file, BasicFileAttributes attrs) {
        Path relPath = workspaceRoot.relativize(file);
        String relPathStr = relPath.toString().replace('\\', '/');
        String fileName = file.getFileName().toString();
        String parentDir = relPath.getParent() != null
            ? relPath.getParent().toString().replace('\\', '/')
            : "";

        CacheEntry entry = new CacheEntry(
            relPathStr, fileName, parentDir,
            attrs.size(), attrs.lastModifiedTime().toMillis(),
            false
        );
        index.put(relPathStr, entry);
    }

    private void reindexDir(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                    String dirName = d.getFileName().toString();
                    if (dirName.startsWith(".") && !DOT_DIR_ALLOWLIST.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (SKIPPED_DIRS.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path relPath = workspaceRoot.relativize(file);
                    String relPathStr = relPath.toString().replace('\\', '/');

                    // 更新或添加
                    if (index.containsKey(relPathStr)) {
                        CacheEntry existing = index.get(relPathStr);
                        if (existing.lastModified != attrs.lastModifiedTime().toMillis()) {
                            addEntry(file, attrs);
                        }
                    } else {
                        addEntry(file, attrs);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.warning("[FileNameCache] 重新索引目录失败: " + dir + " - " + e.getMessage());
        }
    }

    private void recordDirTimestamps() {
        dirModTimeCache.clear();
        // 记录所有一级子目录的时间戳
        try {
            Files.list(workspaceRoot).filter(Files::isDirectory).forEach(dir -> {
                String dirName = dir.getFileName().toString();
                if (!SKIPPED_DIRS.contains(dirName)) {
                    String relDir = workspaceRoot.relativize(dir).toString().replace('\\', '/');
                    dirModTimeCache.put(relDir, getDirMaxModifiedTime(dir));
                }
            });
        } catch (IOException e) {
            logger.warning("[FileNameCache] 记录目录时间戳失败: " + e.getMessage());
        }
    }

    private long getDirMaxModifiedTime(Path dir) {
        try {
            return Files.walk(dir)
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try { return Files.getLastModifiedTime(p).toMillis(); }
                    catch (IOException e) { return 0; }
                })
                .max()
                .orElse(0);
        } catch (IOException e) {
            return 0;
        }
    }

    private java.util.regex.Pattern compileGlobToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*':
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        regex.append(".*");
                        i++;
                        if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') i++;
                    } else {
                        regex.append("[^/]*");
                    }
                    break;
                case '?': regex.append("[^/]"); break;
                case '.': regex.append("\\."); break;
                case '{':
                    // 简单处理 {a,b} → (a|b)
                    StringBuilder alt = new StringBuilder();
                    alt.append('(');
                    while (i + 1 < glob.length() && glob.charAt(i + 1) != '}') {
                        i++;
                        if (glob.charAt(i) == ',') alt.append('|');
                        else alt.append(glob.charAt(i));
                    }
                    alt.append(')');
                    i++; // 跳过 }
                    regex.append(alt);
                    break;
                case '/': regex.append("/"); break;
                default: regex.append(Character.toLowerCase(c)); break;
            }
        }
        regex.append("$");
        return java.util.regex.Pattern.compile(regex.toString(), java.util.regex.Pattern.CASE_INSENSITIVE);
    }

    private void loadFromDisk() {
        try {
            Files.createDirectories(cacheDir);

            // 加载元数据
            Path metaPath = cacheDir.resolve(META_FILE_NAME);
            if (Files.exists(metaPath)) {
                CacheMeta meta = mapper.readValue(metaPath.toFile(), CacheMeta.class);
                // 检查工作区根是否匹配
                if (meta.workspaceRoot != null
                    && Path.of(meta.workspaceRoot).normalize().equals(workspaceRoot)) {
                    dirModTimeCache.putAll(meta.dirTimestamps);
                } else {
                    logger.info("[FileNameCache] 工作区根路径变化，忽略旧缓存");
                    return;
                }
            }

            // 加载索引
            Path cachePath = cacheDir.resolve(CACHE_FILE_NAME);
            if (Files.exists(cachePath)) {
                List<CacheEntry> entries = mapper.readValue(
                    cachePath.toFile(),
                    new TypeReference<List<CacheEntry>>() {}
                );
                for (CacheEntry entry : entries) {
                    index.put(entry.relativePath, entry);
                }
                logger.info("[FileNameCache] 从磁盘加载缓存 | 条目数=" + index.size());
            }
        } catch (IOException e) {
            logger.warning("[FileNameCache] 加载磁盘缓存失败: " + e.getMessage());
        }
    }

    private synchronized void saveToDisk() {
        try {
            Files.createDirectories(cacheDir);

            // 保存索引
            Path cachePath = cacheDir.resolve(CACHE_FILE_NAME);
            mapper.writeValue(cachePath.toFile(), new ArrayList<>(index.values()));

            // 保存元数据
            CacheMeta meta = new CacheMeta();
            meta.workspaceRoot = workspaceRoot.toString();
            meta.buildTime = System.currentTimeMillis();
            meta.entryCount = index.size();
            meta.dirTimestamps = new HashMap<>(dirModTimeCache);
            mapper.writeValue(cacheDir.resolve(META_FILE_NAME).toFile(), meta);

            dirty = false;
            logger.fine("[FileNameCache] 缓存已持久化 | 条目数=" + index.size());
        } catch (IOException e) {
            logger.warning("[FileNameCache] 持久化缓存失败: " + e.getMessage());
        }
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        if (now - lastAccessTime > DEFAULT_TTL_MS && dirty) {
            saveToDisk();
        }
    }

    /** 获取缓存条目数 */
    public int size() { return index.size(); }
}

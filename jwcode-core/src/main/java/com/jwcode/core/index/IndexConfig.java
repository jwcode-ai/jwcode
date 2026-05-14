package com.jwcode.core.index;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * IndexConfig — 代码库索引配置。
 *
 * <p>定义索引行为：哪些文件纳入索引、哪些排除、存储路径等。</p>
 */
public class IndexConfig {

    /** 索引存储根路径（工作区 .jwcode/index/） */
    private Path indexDir;

    /** 排除的目录名/前缀 */
    private Set<String> excludeDirs;

    /** 排除的文件扩展名 */
    private Set<String> excludeExtensions;

    /** 支持索引的文件扩展名 */
    private Set<String> includeExtensions;

    /** 单文件最大大小（字节），超过不分块索引 */
    private long maxFileSizeBytes;

    /** 每个代码块的最大字符数（用于分块） */
    private int maxChunkChars;

    /** 嵌入向量维度 */
    private int embeddingDimension;

    /** 语义搜索默认返回数量 */
    private int defaultTopK;

    /** 是否在启动时自动构建索引 */
    private boolean autoIndexOnStartup;

    /** 文件变更后延迟索引的毫秒数（防抖） */
    private long fileWatchDebounceMs;

    // ==================== 默认值 ====================

    public static final String DEFAULT_INDEX_DIR = ".jwcode/index";
    public static final long DEFAULT_MAX_FILE_SIZE = 500_000; // 500KB
    public static final int DEFAULT_MAX_CHUNK_CHARS = 2000;
    public static final int DEFAULT_EMBEDDING_DIM = 1536; // OpenAI text-embedding-ada-002
    public static final int DEFAULT_TOP_K = 10;
    public static final long DEFAULT_DEBOUNCE_MS = 3000;

    public static final Set<String> DEFAULT_EXCLUDE_DIRS = Set.of(
        "node_modules", "target", ".git", ".svn", ".hg",
        "__pycache__", ".idea", ".vscode", ".DS_Store",
        "dist", "build", "out", ".next", ".nuxt",
        "venv", ".venv", "vendor", "bower_components"
    );

    public static final Set<String> DEFAULT_EXCLUDE_EXTENSIONS = Set.of(
        ".class", ".jar", ".war", ".ear",
        ".o", ".so", ".dll", ".exe", ".bin",
        ".png", ".jpg", ".jpeg", ".gif", ".ico", ".bmp", ".svg",
        ".mp3", ".mp4", ".avi", ".mov", ".wmv",
        ".zip", ".tar", ".gz", ".rar", ".7z",
        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
        ".lock", ".log", ".tmp", ".bak"
    );

    public static final Set<String> DEFAULT_INCLUDE_EXTENSIONS = Set.of(
        ".java", ".kt", ".scala", ".groovy",
        ".ts", ".tsx", ".js", ".jsx", ".mjs",
        ".py", ".pyi", ".pyx",
        ".go", ".rs", ".cpp", ".c", ".h", ".hpp",
        ".rb", ".php", ".swift",
        ".cs", ".fs", ".vb",
        ".vue", ".svelte",
        ".xml", ".yaml", ".yml", ".json", ".toml",
        ".sql", ".sh", ".bat", ".ps1",
        ".md", ".mdx", ".rst", ".txt",
        ".html", ".css", ".scss", ".less",
        ".gradle", ".properties", ".cfg", ".ini",
        ".proto", ".graphql", ".prisma"
    );

    // ==================== 构造函数 ====================

    public IndexConfig() {
        this.indexDir = Paths.get(DEFAULT_INDEX_DIR);
        this.excludeDirs = DEFAULT_EXCLUDE_DIRS;
        this.excludeExtensions = DEFAULT_EXCLUDE_EXTENSIONS;
        this.includeExtensions = DEFAULT_INCLUDE_EXTENSIONS;
        this.maxFileSizeBytes = DEFAULT_MAX_FILE_SIZE;
        this.maxChunkChars = DEFAULT_MAX_CHUNK_CHARS;
        this.embeddingDimension = DEFAULT_EMBEDDING_DIM;
        this.defaultTopK = DEFAULT_TOP_K;
        this.autoIndexOnStartup = true;
        this.fileWatchDebounceMs = DEFAULT_DEBOUNCE_MS;
    }

    /**
     * 以工作区根路径创建配置
     */
    public static IndexConfig forWorkspace(Path workspaceRoot) {
        IndexConfig config = new IndexConfig();
        config.setIndexDir(workspaceRoot.resolve(DEFAULT_INDEX_DIR));
        return config;
    }

    // ==================== 判断方法 ====================

    /**
     * 判断文件是否应被索引
     */
    public boolean shouldIndex(Path filePath) {
        String fileName = filePath.getFileName().toString();

        // 检查扩展名
        String ext = getFileExtension(fileName);
        if (ext.isEmpty() || !includeExtensions.contains(ext)) {
            return false;
        }
        if (excludeExtensions.contains(ext)) {
            return false;
        }

        // 检查路径是否在排除目录中
        for (Path part : filePath) {
            if (excludeDirs.contains(part.toString())) {
                return false;
            }
        }

        // 检查隐藏文件
        if (fileName.startsWith(".")) {
            return false;
        }

        return true;
    }

    /**
     * 获取文件扩展名（含点号，小写）
     */
    public static String getFileExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) return "";
        return fileName.substring(dot).toLowerCase();
    }

    // ==================== Getters & Setters ====================

    public Path getIndexDir() { return indexDir; }
    public void setIndexDir(Path indexDir) { this.indexDir = indexDir; }

    public Set<String> getExcludeDirs() { return excludeDirs; }
    public void setExcludeDirs(Set<String> excludeDirs) { this.excludeDirs = excludeDirs; }

    public Set<String> getExcludeExtensions() { return excludeExtensions; }
    public void setExcludeExtensions(Set<String> excludeExtensions) { this.excludeExtensions = excludeExtensions; }

    public Set<String> getIncludeExtensions() { return includeExtensions; }
    public void setIncludeExtensions(Set<String> includeExtensions) { this.includeExtensions = includeExtensions; }

    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }

    public int getMaxChunkChars() { return maxChunkChars; }
    public void setMaxChunkChars(int maxChunkChars) { this.maxChunkChars = maxChunkChars; }

    public int getEmbeddingDimension() { return embeddingDimension; }
    public void setEmbeddingDimension(int embeddingDimension) { this.embeddingDimension = embeddingDimension; }

    public int getDefaultTopK() { return defaultTopK; }
    public void setDefaultTopK(int defaultTopK) { this.defaultTopK = defaultTopK; }

    public boolean isAutoIndexOnStartup() { return autoIndexOnStartup; }
    public void setAutoIndexOnStartup(boolean autoIndexOnStartup) { this.autoIndexOnStartup = autoIndexOnStartup; }

    public long getFileWatchDebounceMs() { return fileWatchDebounceMs; }
    public void setFileWatchDebounceMs(long fileWatchDebounceMs) { this.fileWatchDebounceMs = fileWatchDebounceMs; }
}

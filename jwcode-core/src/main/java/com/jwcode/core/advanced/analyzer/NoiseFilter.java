package com.jwcode.core.advanced.analyzer;

import java.nio.file.Path;
import java.util.Set;

/**
 * 噪音过滤器 - 自动排除无信息密度的目录和文件
 * 
 * 核心策略：
 * 1. 绝对排除：.git, node_modules, target, build, dist 等构建产物和依赖目录
 * 2. 后缀排除：.class, .log, .tmp, .cache 等临时文件
 * 3. 大小排除：超过阈值的二进制文件
 * 4. 深度限制：避免无限递归导致的输出截断
 */
public class NoiseFilter {
    
    // 绝对排除的目录名（大小写不敏感）
    private static final Set<String> NOISE_DIRS = Set.of(
        ".git", "node_modules", "target", "build", "dist", "out",
        ".idea", ".vscode", ".gradle", ".settings", "bin", "obj",
        "coverage", ".nyc_output", "__pycache__", ".pytest_cache",
        ".mvn", ".github", ".gitignore", "logs", "*.dSYM"
    );
    
    // 排除的文件后缀
    private static final Set<String> NOISE_EXTENSIONS = Set.of(
        "class", "jar", "war", "ear", "zip", "tar", "gz", "rar",
        "7z", "exe", "dll", "so", "dylib", "o", "a", "lib",
        "log", "tmp", "temp", "cache", "pid", "seed", "ico",
        "png", "jpg", "jpeg", "gif", "bmp", "svg", "webp", "mp4",
        "mp3", "wav", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    );
    
    // 排除的文件名前缀/通配
    private static final Set<String> NOISE_FILES = Set.of(
        ".DS_Store", "Thumbs.db", "desktop.ini", "package-lock.json",
        "yarn.lock", "pnpm-lock.yaml", ".env.local", ".env.development",
        ".env.production", "npm-debug.log", "yarn-error.log"
    );
    
    // 最大允许扫描的文件大小（默认 2MB）
    private static final long MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024;
    
    // 最大扫描深度（防止 tree 无限展开）
    private static final int MAX_DEPTH = 12;
    
    /**
     * 判断路径是否为噪音
     */
    public static boolean isNoise(Path path) {
        if (path == null) return true;
        
        String fileName = path.getFileName() != null ? path.getFileName().toString() : "";
        String lowerName = fileName.toLowerCase();
        
        // 检查文件名是否在噪音列表中
        if (NOISE_FILES.contains(fileName) || NOISE_FILES.contains(lowerName)) {
            return true;
        }
        
        // 检查后缀
        int dotIndex = lowerName.lastIndexOf('.');
        if (dotIndex > 0) {
            String ext = lowerName.substring(dotIndex + 1);
            if (NOISE_EXTENSIONS.contains(ext)) {
                return true;
            }
        }
        
        // 检查路径中是否包含噪音目录
        for (Path part : path) {
            String partName = part.toString();
            if (NOISE_DIRS.contains(partName) || NOISE_DIRS.contains(partName.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 判断目录是否应该被跳过（不递归进入）
     */
    public static boolean shouldSkipDirectory(Path dir) {
        if (dir == null) return true;
        String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
        return NOISE_DIRS.contains(dirName) || NOISE_DIRS.contains(dirName.toLowerCase());
    }
    
    /**
     * 检查文件大小是否可接受
     */
    public static boolean isAcceptableSize(long sizeBytes) {
        return sizeBytes <= MAX_FILE_SIZE_BYTES;
    }
    
    /**
     * 检查扫描深度是否可接受
     */
    public static boolean isAcceptableDepth(int depth) {
        return depth <= MAX_DEPTH;
    }
    
    /**
     * 获取排除规则的描述（用于报告）
     */
    public static String getFilterSummary() {
        return String.format(
            "噪音过滤器：排除 %d 类目录、%d 类后缀、%d 个特定文件，最大文件 %dMB，最大深度 %d",
            NOISE_DIRS.size(), NOISE_EXTENSIONS.size(), NOISE_FILES.size(),
            MAX_FILE_SIZE_BYTES / (1024 * 1024), MAX_DEPTH
        );
    }
}

package com.jwcode.core.advanced.analyzer;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 取证收集器 - 根据项目指纹，精准收集高信息密度的核心文件
 * 
 * 设计原则：
 * 1. 排噪优先：扫描时实时过滤 .git / target / node_modules 等
 * 2. 深度控制：限制扫描层级，避免输出截断
 * 3. 大小控制：跳过超大二进制文件
 * 4. 优先级排序：配置文件 > 入口类 > 核心服务类 > 测试类
 */
public class EvidenceCollector {
    
    private final Path root;
    private final ProjectFingerprint fingerprint;
    
    public EvidenceCollector(Path root, ProjectFingerprint fingerprint) {
        this.root = root;
        this.fingerprint = fingerprint;
    }
    
    /**
     * 收集关键证据文件
     * 
     * @param maxFiles 最多返回的文件数（默认 30）
     * @return 按优先级排序的关键文件路径列表
     */
    public List<EvidenceFile> collect(int maxFiles) {
        List<EvidenceFile> evidence = new ArrayList<>();
        
        // 阶段 1：直接匹配高价值模式（glob）
        List<String> patterns = fingerprint.getHighValueFilePatterns();
        for (String pattern : patterns) {
            try (Stream<Path> stream = Files.find(root, 8, (path, attrs) -> {
                if (!Files.isRegularFile(path) || NoiseFilter.isNoise(path)) {
                    return false;
                }
                String relative = root.relativize(path).toString().replace('\\', '/');
                return matchGlob(relative, pattern);
            })) {
                stream.limit(5).forEach(p -> {
                    evidence.add(new EvidenceFile(p, calculatePriority(p), "pattern-match"));
                });
            } catch (Exception e) {
                // 忽略 glob 匹配错误
            }
        }
        
        // 阶段 2：按项目类型补充特定目录下的关键文件
        evidence.addAll(collectByProjectType());
        
        // 去重并按优先级排序
        List<EvidenceFile> result = evidence.stream()
            .distinct()
            .sorted(Comparator.comparingInt(EvidenceFile::priority).reversed())
            .limit(maxFiles)
            .collect(Collectors.toList());
        
        return result;
    }
    
    /**
     * 读取文件内容作为证据（带安全截断）
     */
    public String readEvidenceContent(Path file, int maxLines) {
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(file);
            if (lines.size() <= maxLines) {
                return String.join("\n", lines);
            }
            List<String> head = lines.subList(0, maxLines);
            return String.join("\n", head) + "\n... [truncated, total " + lines.size() + " lines]";
        } catch (IOException e) {
            return "[ERROR] 无法读取文件: " + e.getMessage();
        }
    }
    
    private List<EvidenceFile> collectByProjectType() {
        List<EvidenceFile> extra = new ArrayList<>();
        
        switch (fingerprint.getProjectType()) {
            case MAVEN_SPRING_BOOT -> {
                // 收集 controller / service / config
                extra.addAll(scanDirectory("src/main/java", "controller", 3));
                extra.addAll(scanDirectory("src/main/java", "service", 3));
                extra.addAll(scanDirectory("src/main/java", "config", 3));
            }
            case NPM_REACT -> {
                extra.addAll(scanDirectory("src", "components", 5));
                extra.addAll(scanDirectory("src", "views", 5));
            }
            case PYTHON -> {
                extra.addAll(scanRootByExtension("py", 5));
            }
            default -> {
                // 无额外补充
            }
        }
        
        return extra;
    }
    
    private List<EvidenceFile> scanDirectory(String baseDir, String subDirHint, int limit) {
        List<EvidenceFile> found = new ArrayList<>();
        Path base = root.resolve(baseDir);
        if (!Files.exists(base)) return found;
        
        try (Stream<Path> stream = Files.find(base, 4, (path, attrs) -> {
            if (!Files.isRegularFile(path) || NoiseFilter.isNoise(path)) return false;
            String rel = base.relativize(path).toString().toLowerCase().replace('\\', '/');
            return rel.contains(subDirHint.toLowerCase());
        })) {
            stream.limit(limit).forEach(p -> found.add(new EvidenceFile(p, calculatePriority(p), "dir-scan")));
        } catch (Exception e) {
            // ignore
        }
        return found;
    }
    
    private List<EvidenceFile> scanRootByExtension(String ext, int limit) {
        List<EvidenceFile> found = new ArrayList<>();
        try (Stream<Path> stream = Files.find(root, 2, (path, attrs) -> {
            if (!Files.isRegularFile(path) || NoiseFilter.isNoise(path)) return false;
            String name = path.getFileName().toString().toLowerCase();
            return name.endsWith("." + ext);
        })) {
            stream.limit(limit).forEach(p -> found.add(new EvidenceFile(p, calculatePriority(p), "ext-scan")));
        } catch (Exception e) {
            // ignore
        }
        return found;
    }
    
    private int calculatePriority(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        String rel = root.relativize(file).toString().toLowerCase().replace('\\', '/');
        
        // 最高优先级：根级配置
        if (name.equals("pom.xml") || name.equals("package.json") || name.equals("docker-compose.yml")) {
            return 100;
        }
        // 高优先级：应用配置和入口
        if (name.startsWith("application") || name.contains("application") || name.contains("app")) {
            return 90;
        }
        if (name.contains("main") || name.contains("application")) {
            return 85;
        }
        // 中高优先级：核心架构类
        if (rel.contains("/config/") || rel.contains("/configuration/")) {
            return 80;
        }
        if (rel.contains("/controller/") || rel.contains("/service/")) {
            return 75;
        }
        // 中等优先级：其他源码
        if (rel.contains("/src/main/") || rel.contains("/src/")) {
            return 60;
        }
        // 低优先级：测试、文档
        if (rel.contains("/test/")) {
            return 30;
        }
        return 50;
    }
    
    private boolean matchGlob(String relativePath, String pattern) {
        try {
            var matcher = root.getFileSystem().getPathMatcher("glob:" + pattern);
            Path relPath = root.resolve(relativePath);
            if (matcher.matches(relPath) || matcher.matches(Path.of(relativePath))) {
                return true;
            }
        } catch (Exception e) {
            // PathMatcher 失败时降级
        }
        
        // 降级：简单后缀和子串匹配
        String lowerPath = relativePath.toLowerCase().replace('\\', '/');
        String lowerPattern = pattern.toLowerCase();
        
        if (lowerPattern.startsWith("**/")) {
            String remainder = lowerPattern.substring(3);
            // 处理 **/src/main/java/**/*Application*.java 这种
            if (remainder.contains("/**/")) {
                String dirPart = remainder.substring(0, remainder.indexOf("/**/"));
                String filePart = remainder.substring(remainder.lastIndexOf('/') + 1);
                if (!lowerPath.contains(dirPart)) return false;
                String fileName = lowerPath.contains("/") 
                    ? lowerPath.substring(lowerPath.lastIndexOf('/') + 1) 
                    : lowerPath;
                // filePart 可能包含 *
                if (filePart.contains("*")) {
                    String[] fileSegments = filePart.split("\\*");
                    int fidx = 0;
                    for (String seg : fileSegments) {
                        if (seg.isEmpty()) continue;
                        int found = fileName.indexOf(seg, fidx);
                        if (found < 0) return false;
                        fidx = found + seg.length();
                    }
                    return true;
                }
                return fileName.equals(filePart);
            }
            // 简单 **/suffix
            String suffix = remainder;
            if (suffix.contains("*")) {
                String[] parts = suffix.split("\\*");
                int idx = 0;
                for (String part : parts) {
                    if (part.isEmpty()) continue;
                    int found = lowerPath.indexOf(part, idx);
                    if (found < 0) return false;
                    idx = found + part.length();
                }
                return true;
            }
            return lowerPath.endsWith(suffix);
        }
        
        return lowerPath.equals(lowerPattern);
    }
    
    public record EvidenceFile(Path path, int priority, String source) {
        public String relativePath(Path root) {
            return root.relativize(path).toString().replace('\\', '/');
        }
    }
}

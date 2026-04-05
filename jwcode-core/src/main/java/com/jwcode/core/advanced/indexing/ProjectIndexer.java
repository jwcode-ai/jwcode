package com.jwcode.core.advanced.indexing;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Project Indexing - 项目索引与分析
 * 
 * 参照 Kimi Code 的项目索引能力
 * 自动分析整个项目结构、依赖关系、代码模式
 */
public class ProjectIndexer {
    
    private final Path projectRoot;
    private final ProjectIndex index;
    private final Map<String, FileNode> fileGraph;
    
    public ProjectIndexer(String projectRoot) {
        this.projectRoot = Paths.get(projectRoot);
        this.index = new ProjectIndex();
        this.fileGraph = new ConcurrentHashMap<>();
    }
    
    /**
     * 执行完整索引
     */
    public ProjectIndex buildIndex() throws IOException {
        System.out.println("[ProjectIndex] 开始索引项目: " + projectRoot);
        long start = System.currentTimeMillis();
        
        // 1. 扫描文件结构
        scanFileStructure();
        
        // 2. 分析代码统计
        analyzeCodeStats();
        
        // 3. 构建依赖图
        buildDependencyGraph();
        
        // 4. 分析项目类型
        detectProjectType();
        
        // 5. 提取关键文件
        identifyKeyFiles();
        
        long duration = System.currentTimeMillis() - start;
        System.out.println("[ProjectIndex] 索引完成，耗时: " + duration + "ms");
        System.out.println("[ProjectIndex] 文件数: " + index.getTotalFiles() + ", 代码行数: " + index.getTotalLinesOfCode());
        
        return index;
    }
    
    /**
     * 扫描文件结构
     */
    private void scanFileStructure() throws IOException {
        Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName().toString();
                
                // 跳过隐藏文件和目录
                if (fileName.startsWith(".") || fileName.startsWith("_")) {
                    return FileVisitResult.CONTINUE;
                }
                
                // 跳过构建目录
                String pathStr = file.toString();
                if (pathStr.contains("/target/") || pathStr.contains("/build/") || 
                    pathStr.contains("/node_modules/") || pathStr.contains("/.git/")) {
                    return FileVisitResult.CONTINUE;
                }
                
                try {
                    FileNode node = analyzeFile(file);
                    fileGraph.put(file.toString(), node);
                    index.addFile(node);
                } catch (IOException e) {
                    System.out.println("[ProjectIndex] 无法分析文件: " + file);
                }
                
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    /**
     * 分析单个文件
     */
    private FileNode analyzeFile(Path file) throws IOException {
        String content = Files.readString(file);
        String fileName = file.getFileName().toString();
        String extension = getFileExtension(fileName);
        
        return FileNode.builder()
            .path(file.toString())
            .name(fileName)
            .extension(extension)
            .size(Files.size(file))
            .lineCount(content.split("\n").length)
            .lastModified(Files.getLastModifiedTime(file).toMillis())
            .build();
    }
    
    /**
     * 分析代码统计
     */
    private void analyzeCodeStats() {
        Map<String, Long> languageStats = fileGraph.values().stream()
            .collect(Collectors.groupingBy(FileNode::getExtension, Collectors.counting()));
        
        index.setLanguageDistribution(languageStats);
        
        long totalLines = fileGraph.values().stream()
            .mapToLong(FileNode::getLineCount)
            .sum();
        index.setTotalLinesOfCode(totalLines);
    }
    
    /**
     * 构建依赖图
     */
    private void buildDependencyGraph() {
        // 简化实现：基于 import/include 分析
        for (FileNode node : fileGraph.values()) {
            if (isCodeFile(node.getExtension())) {
                try {
                    String content = Files.readString(Paths.get(node.getPath()));
                    Set<String> dependencies = extractDependencies(content, node.getExtension());
                    node.setDependencies(dependencies);
                } catch (IOException e) {
                    System.out.println("[ProjectIndex] 无法读取文件: " + node.getPath());
                }
            }
        }
    }
    
    /**
     * 提取依赖
     */
    private Set<String> extractDependencies(String content, String extension) {
        Set<String> deps = new HashSet<>();
        
        switch (extension.toLowerCase()) {
            case "java":
                // 提取 Java imports
                for (String line : content.split("\n")) {
                    if (line.trim().startsWith("import ")) {
                        deps.add(line.trim().replace("import ", "").replace(";", ""));
                    }
                }
                break;
            case "py":
                // 提取 Python imports
                for (String line : content.split("\n")) {
                    if (line.trim().startsWith("import ") || line.trim().startsWith("from ")) {
                        deps.add(line.trim());
                    }
                }
                break;
            case "js":
            case "ts":
                // 提取 JS/TS imports
                for (String line : content.split("\n")) {
                    if (line.contains("require(") || line.contains("import ")) {
                        deps.add(line.trim());
                    }
                }
                break;
        }
        
        return deps;
    }
    
    /**
     * 检测项目类型
     */
    private void detectProjectType() {
        // 检测 Maven
        if (Files.exists(projectRoot.resolve("pom.xml"))) {
            index.setProjectType("Maven");
            index.setBuildTool("Maven");
        }
        // 检测 Gradle
        else if (Files.exists(projectRoot.resolve("build.gradle")) || 
                 Files.exists(projectRoot.resolve("build.gradle.kts"))) {
            index.setProjectType("Gradle");
            index.setBuildTool("Gradle");
        }
        // 检测 npm
        else if (Files.exists(projectRoot.resolve("package.json"))) {
            index.setProjectType("Node.js");
            index.setBuildTool("npm/yarn");
        }
        // 检测 Python
        else if (Files.exists(projectRoot.resolve("requirements.txt")) ||
                 Files.exists(projectRoot.resolve("pyproject.toml"))) {
            index.setProjectType("Python");
            index.setBuildTool("pip");
        }
        else {
            index.setProjectType("Generic");
            index.setBuildTool("Unknown");
        }
    }
    
    /**
     * 识别关键文件
     */
    private void identifyKeyFiles() {
        List<String> keyFiles = new ArrayList<>();
        
        // README
        for (String readme : List.of("README.md", "README.txt", "README")) {
            if (Files.exists(projectRoot.resolve(readme))) {
                keyFiles.add(readme);
            }
        }
        
        // 配置文件
        for (String config : List.of("pom.xml", "build.gradle", "package.json", 
                                     "application.yml", "application.properties")) {
            if (Files.exists(projectRoot.resolve(config))) {
                keyFiles.add(config);
            }
        }
        
        // 主类/入口
        fileGraph.values().stream()
            .filter(f -> f.getName().contains("Main") || f.getName().contains("Application"))
            .findFirst()
            .ifPresent(f -> keyFiles.add(f.getName()));
        
        index.setKeyFiles(keyFiles);
    }
    
    /**
     * 搜索文件
     */
    public List<FileNode> searchFiles(String query) {
        String lowerQuery = query.toLowerCase();
        return fileGraph.values().stream()
            .filter(f -> f.getName().toLowerCase().contains(lowerQuery) ||
                        f.getPath().toLowerCase().contains(lowerQuery))
            .collect(Collectors.toList());
    }
    
    /**
     * 获取文件关系图
     */
    public Map<String, Set<String>> getDependencyGraph() {
        Map<String, Set<String>> graph = new HashMap<>();
        for (FileNode node : fileGraph.values()) {
            if (!node.getDependencies().isEmpty()) {
                graph.put(node.getPath(), node.getDependencies());
            }
        }
        return graph;
    }
    
    /**
     * 生成项目摘要
     */
    public String generateSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔════════════════════════════════════════════════════════╗\n");
        sb.append("║           项目索引报告                                 ║\n");
        sb.append("╚════════════════════════════════════════════════════════╝\n");
        sb.append("\n");
        sb.append("项目路径: ").append(projectRoot).append("\n");
        sb.append("项目类型: ").append(index.getProjectType()).append("\n");
        sb.append("构建工具: ").append(index.getBuildTool()).append("\n");
        sb.append("\n");
        sb.append("统计:\n");
        sb.append("  总文件数: ").append(index.getTotalFiles()).append("\n");
        sb.append("  代码行数: ").append(index.getTotalLinesOfCode()).append("\n");
        sb.append("\n");
        sb.append("语言分布:\n");
        index.getLanguageDistribution().forEach((lang, count) -> {
            sb.append(String.format("  %-10s %d 文件%n", lang, count));
        });
        sb.append("\n");
        sb.append("关键文件:\n");
        index.getKeyFiles().forEach(f -> sb.append("  • ").append(f).append("\n"));
        
        return sb.toString();
    }
    
    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1) : "";
    }
    
    private boolean isCodeFile(String extension) {
        return Set.of("java", "py", "js", "ts", "go", "rs", "cpp", "c", "h").contains(extension.toLowerCase());
    }
    
    // ==================== 数据类 ====================
    
    public static class ProjectIndex {
        private String projectType;
        private String buildTool;
        private int totalFiles;
        private long totalLinesOfCode;
        private Map<String, Long> languageDistribution = new HashMap<>();
        private List<String> keyFiles = new ArrayList<>();
        private List<FileNode> allFiles = new ArrayList<>();
        
        public ProjectIndex() {}
        
        public void addFile(FileNode file) {
            allFiles.add(file);
            totalFiles++;
        }
        
        // Getters and Setters
        public String getProjectType() { return projectType; }
        public void setProjectType(String v) { this.projectType = v; }
        public String getBuildTool() { return buildTool; }
        public void setBuildTool(String v) { this.buildTool = v; }
        public int getTotalFiles() { return totalFiles; }
        public void setTotalFiles(int v) { this.totalFiles = v; }
        public long getTotalLinesOfCode() { return totalLinesOfCode; }
        public void setTotalLinesOfCode(long v) { this.totalLinesOfCode = v; }
        public Map<String, Long> getLanguageDistribution() { return languageDistribution; }
        public void setLanguageDistribution(Map<String, Long> v) { this.languageDistribution = v; }
        public List<String> getKeyFiles() { return keyFiles; }
        public void setKeyFiles(List<String> v) { this.keyFiles = v; }
        public List<FileNode> getAllFiles() { return allFiles; }
    }
    
    public static class FileNode {
        private String path;
        private String name;
        private String extension;
        private long size;
        private int lineCount;
        private long lastModified;
        private Set<String> dependencies = new HashSet<>();
        
        public FileNode() {}
        
        public String getPath() { return path; }
        public void setPath(String v) { this.path = v; }
        public String getName() { return name; }
        public void setName(String v) { this.name = v; }
        public String getExtension() { return extension; }
        public void setExtension(String v) { this.extension = v; }
        public long getSize() { return size; }
        public void setSize(long v) { this.size = v; }
        public int getLineCount() { return lineCount; }
        public void setLineCount(int v) { this.lineCount = v; }
        public long getLastModified() { return lastModified; }
        public void setLastModified(long v) { this.lastModified = v; }
        public Set<String> getDependencies() { return dependencies; }
        public void setDependencies(Set<String> v) { this.dependencies = v; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private final FileNode node = new FileNode();
            
            public Builder path(String v) { node.path = v; return this; }
            public Builder name(String v) { node.name = v; return this; }
            public Builder extension(String v) { node.extension = v; return this; }
            public Builder size(long v) { node.size = v; return this; }
            public Builder lineCount(int v) { node.lineCount = v; return this; }
            public Builder lastModified(long v) { node.lastModified = v; return this; }
            public Builder dependencies(Set<String> v) { node.dependencies = v; return this; }
            public FileNode build() { return node; }
        }
    }
}

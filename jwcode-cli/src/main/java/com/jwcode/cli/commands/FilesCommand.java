package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.*;

/**
 * /files 命令 - 文件列表
 * 
 * 显示项目文件列表，支持过滤和排序。
 */
public class FilesCommand implements Command {
    
    private static final int DEFAULT_MAX_RESULTS = 100;
    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("#,###");
    
    @Override
    public String getName() {
        return "files";
    }
    
    @Override
    public String getDescription() {
        return "显示项目文件列表";
    }
    
    @Override
    public String getUsage() {
        return "/files [路径] [--max N] [--type EXT] [--sort by|size|date] [--tree]";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        FileOptions options = parseArgs(args);
        
        try {
            return listFiles(options, context);
        } catch (IOException e) {
            return CommandResult.error("列出文件失败：" + e.getMessage());
        }
    }
    
    /**
     * 解析参数
     */
    private FileOptions parseArgs(String args) {
        FileOptions options = new FileOptions();
        
        if (args == null || args.trim().isEmpty()) {
            options.path = ".";
            return options;
        }
        
        String[] parts = args.trim().split("\\s+");
        boolean pathSet = false;
        
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            
            if ("--max".equals(part) && i + 1 < parts.length) {
                options.maxResults = Integer.parseInt(parts[++i]);
            } else if ("--type".equals(part) && i + 1 < parts.length) {
                options.typeFilter = parts[++i];
            } else if ("--sort".equals(part) && i + 1 < parts.length) {
                options.sortBy = parts[++i];
            } else if ("--tree".equals(part)) {
                options.treeView = true;
            } else if ("--long".equals(part)) {
                options.longFormat = true;
            } else if ("--all".equals(part) || "-a".equals(part)) {
                options.showHidden = true;
            } else if (!part.startsWith("-") && !pathSet) {
                options.path = part;
                pathSet = true;
            }
        }
        
        if (options.path == null) {
            options.path = ".";
        }
        
        return options;
    }
    
    /**
     * 列出文件
     */
    private CommandResult listFiles(FileOptions options, CommandContext context) 
            throws IOException {
        Path startPath = Paths.get(options.path);
        
        if (!Files.exists(startPath)) {
            return CommandResult.error("路径不存在：" + options.path);
        }
        
        List<FileInfo> files = new ArrayList<>();
        
        if (Files.isRegularFile(startPath)) {
            // 单个文件
            files.add(readFileInfo(startPath));
        } else {
            // 目录
            Files.walkFileTree(startPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (files.size() >= options.maxResults) {
                        return FileVisitResult.TERMINATE;
                    }
                    
                    String fileName = file.getFileName().toString();
                    
                    // 过滤隐藏文件
                    if (!options.showHidden && fileName.startsWith(".") && !fileName.equals("..")) {
                        return FileVisitResult.CONTINUE;
                    }
                    
                    // 类型过滤
                    if (options.typeFilter != null) {
                        if (!fileName.endsWith(options.typeFilter)) {
                            return FileVisitResult.CONTINUE;
                        }
                    }
                    
                    try {
                        files.add(readFileInfo(file));
                    } catch (IOException e) {
                        // 忽略无法读取的文件
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName().toString();
                    
                    // 跳过常见的大型无关目录
                    if (!options.showHidden && dirName.startsWith(".") && !dirName.equals("..")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    
                    if (dirName.equals("node_modules") || dirName.equals("target") || 
                        dirName.equals("build") || dirName.equals("dist") ||
                        dirName.equals(".git") || dirName.equals("__pycache__")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        
        // 排序
        sortFiles(files, options.sortBy);
        
        // 构建输出
        StringBuilder output = new StringBuilder();
        
        if (options.treeView) {
            output.append(buildTreeView(files, startPath));
        } else {
            output.append(buildFileList(files, options, startPath));
        }
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * 读取文件信息
     */
    private FileInfo readFileInfo(Path path) throws IOException {
        FileInfo info = new FileInfo();
        info.path = path;
        info.fileName = path.getFileName().toString();
        info.size = Files.size(path);
        info.isDirectory = Files.isDirectory(path);
        
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        info.lastModified = attrs.lastModifiedTime().toMillis();
        info.creationTime = attrs.creationTime().toMillis();
        
        return info;
    }
    
    /**
     * 排序文件
     */
    private void sortFiles(List<FileInfo> files, String sortBy) {
        if ("size".equals(sortBy)) {
            files.sort((a, b) -> Long.compare(b.size, a.size));
        } else if ("date".equals(sortBy) || "time".equals(sortBy)) {
            files.sort((a, b) -> Long.compare(b.lastModified, a.lastModified));
        } else {
            // 默认按名称排序，目录在前
            files.sort((a, b) -> {
                if (a.isDirectory != b.isDirectory) {
                    return a.isDirectory ? -1 : 1;
                }
                return a.fileName.compareToIgnoreCase(b.fileName);
            });
        }
    }
    
    /**
     * 构建文件列表输出
     */
    private String buildFileList(List<FileInfo> files, FileOptions options, Path basePath) {
        StringBuilder output = new StringBuilder();
        
        output.append("文件列表：").append(basePath.toAbsolutePath()).append("\n");
        output.append("总数：").append(files.size()).append("\n\n");
        
        if (options.longFormat) {
            // 详细格式
            output.append(String.format("%-10s %15s  %s\n", "类型", "大小", "名称"));
            output.append("-".repeat(60)).append("\n");
            
            for (FileInfo file : files) {
                String type = file.isDirectory ? "DIR" : "FILE";
                String size = file.isDirectory ? "-" : formatSize(file.size);
                output.append(String.format("%-10s %15s  %s\n", type, size, file.fileName));
            }
        } else {
            // 简洁格式
            for (FileInfo file : files) {
                if (file.isDirectory) {
                    output.append("📁 ").append(file.fileName).append("/\n");
                } else {
                    output.append("📄 ").append(file.fileName);
                    if (file.size > 0) {
                        output.append(" (").append(formatSize(file.size)).append(")");
                    }
                    output.append("\n");
                }
            }
        }
        
        // 统计信息
        long totalSize = files.stream().filter(f -> !f.isDirectory).mapToLong(f -> f.size).sum();
        int dirCount = (int) files.stream().filter(FileInfo::isDirectory).count();
        int fileCount = files.size() - dirCount;
        
        output.append("\n---\n");
        output.append("目录：").append(dirCount).append(", 文件：").append(fileCount);
        output.append(", 总大小：").append(formatSize(totalSize));
        
        return output.toString();
    }
    
    /**
     * 构建树形视图
     */
    private String buildTreeView(List<FileInfo> files, Path basePath) {
        StringBuilder output = new StringBuilder();
        
        output.append("文件树：").append(basePath.toAbsolutePath()).append("\n\n");
        
        // 按路径深度和名称排序
        Map<String, List<FileInfo>> tree = new TreeMap<>();
        
        for (FileInfo file : files) {
            Path relPath = basePath.relativize(file.path);
            int depth = relPath.getNameCount();
            
            String key = String.valueOf(depth);
            tree.computeIfAbsent(key, k -> new ArrayList<>()).add(file);
        }
        
        for (Map.Entry<String, List<FileInfo>> entry : tree.entrySet()) {
            int depth = Integer.parseInt(entry.getKey());
            String indent = "  ".repeat(depth - 1);
            
            for (FileInfo file : entry.getValue()) {
                output.append(indent);
                if (file.isDirectory) {
                    output.append("📁 ");
                } else {
                    output.append("📄 ");
                }
                output.append(file.fileName);
                if (!file.isDirectory && file.size > 0) {
                    output.append(" (").append(formatSize(file.size)).append(")");
                }
                output.append("\n");
            }
        }
        
        return output.toString();
    }
    
    /**
     * 格式化文件大小
     */
    private String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * 文件信息类
     */
    private static class FileInfo {
        Path path;
        String fileName;
        long size;
        boolean isDirectory;
        long lastModified;
        long creationTime;
        
        boolean isDirectory() {
            return isDirectory;
        }
    }
    
    /**
     * 文件选项类
     */
    private static class FileOptions {
        String path;
        int maxResults = DEFAULT_MAX_RESULTS;
        String typeFilter;
        String sortBy = "name";
        boolean treeView;
        boolean longFormat;
        boolean showHidden;
    }
}
package com.jwcode.core.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FileHistoryService - 文件历史跟踪服务
 * 
 * 功能说明：
 * 记录文件修改历史，支持查看文件变更历史、撤销/重做操作。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class FileHistoryService {
    
    private final Map<String, List<FileHistoryEntry>> historyStore;
    private final Map<String, String> fileContents;
    private final ExecutorService executor;
    private final int maxHistorySize;
    
    public FileHistoryService() {
        this.historyStore = new ConcurrentHashMap<>();
        this.fileContents = new ConcurrentHashMap<>();
        this.executor = Executors.newFixedThreadPool(2);
        this.maxHistorySize = 100; // 每个文件最多保留 100 条历史记录
    }
    
    /**
     * 记录文件修改
     */
    public void recordFileChange(String filePath, String oldContent, String newContent, 
                                  String changeDescription) {
        executor.submit(() -> {
            String absolutePath = Paths.get(filePath).toAbsolutePath().toString();
            
            // 保存旧内容
            fileContents.put(absolutePath, oldContent);
            
            // 创建历史记录
            FileHistoryEntry entry = new FileHistoryEntry();
            entry.timestamp = Instant.now().toString();
            entry.filePath = absolutePath;
            entry.oldContent = oldContent;
            entry.newContent = newContent;
            entry.description = changeDescription;
            
            // 添加到历史记录列表
            List<FileHistoryEntry> entries = historyStore.computeIfAbsent(
                    absolutePath, k -> new ArrayList<>());
            entries.add(entry);
            
            // 限制历史记录大小
            if (entries.size() > maxHistorySize) {
                entries.remove(0);
            }
        });
    }
    
    /**
     * 获取文件历史
     */
    public CompletableFuture<List<FileHistoryEntry>> getFileHistory(String filePath) {
        return CompletableFuture.supplyAsync(() -> {
            String absolutePath = Paths.get(filePath).toAbsolutePath().toString();
            List<FileHistoryEntry> entries = historyStore.get(absolutePath);
            return entries != null ? new ArrayList<>(entries) : new ArrayList<>();
        }, executor);
    }
    
    /**
     * 撤销文件修改
     */
    public CompletableFuture<Boolean> undo(String filePath) {
        return CompletableFuture.supplyAsync(() -> {
            String absolutePath = Paths.get(filePath).toAbsolutePath().toString();
            List<FileHistoryEntry> entries = historyStore.get(absolutePath);
            
            if (entries == null || entries.isEmpty()) {
                return false;
            }
            
            FileHistoryEntry lastEntry = entries.remove(entries.size() - 1);
            
            // 恢复旧内容
            try {
                Files.writeString(Paths.get(absolutePath), lastEntry.oldContent);
                return true;
            } catch (IOException e) {
                return false;
            }
        }, executor);
    }
    
    /**
     * 重做文件修改
     */
    public CompletableFuture<Boolean> redo(String filePath, FileHistoryEntry entry) {
        return CompletableFuture.supplyAsync(() -> {
            String absolutePath = Paths.get(filePath).toAbsolutePath().toString();
            
            try {
                Files.writeString(Paths.get(absolutePath), entry.newContent);
                
                // 重新添加到历史记录
                List<FileHistoryEntry> entries = historyStore.computeIfAbsent(
                        absolutePath, k -> new ArrayList<>());
                entries.add(entry);
                
                return true;
            } catch (IOException e) {
                return false;
            }
        }, executor);
    }
    
    /**
     * 清除文件历史
     */
    public void clearHistory(String filePath) {
        String absolutePath = Paths.get(filePath).toAbsolutePath().toString();
        historyStore.remove(absolutePath);
    }
    
    /**
     * 清除所有历史
     */
    public void clearAllHistory() {
        historyStore.clear();
    }
    
    /**
     * 获取历史记录数量
     */
    public int getHistoryCount(String filePath) {
        String absolutePath = Paths.get(filePath).toAbsolutePath().toString();
        List<FileHistoryEntry> entries = historyStore.get(absolutePath);
        return entries != null ? entries.size() : 0;
    }
    
    /**
     * 关闭服务
     */
    public void shutdown() {
        executor.shutdown();
    }
    
    /**
     * 文件历史条目
     */
    public static class FileHistoryEntry {
        public String timestamp;
        public String filePath;
        public String oldContent;
        public String newContent;
        public String description;
        
        public Map<String, Object> toMap() {
            return Map.of(
                    "timestamp", timestamp,
                    "filePath", filePath,
                    "description", description
            );
        }
    }
}
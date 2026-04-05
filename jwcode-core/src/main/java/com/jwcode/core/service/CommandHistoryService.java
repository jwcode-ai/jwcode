package com.jwcode.core.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

/**
 * CommandHistoryService - 命令历史服务
 * 
 * 功能说明：
 * 记录命令执行历史，支持查看历史、搜索历史命令。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class CommandHistoryService {
    
    private final List<CommandHistoryEntry> history;
    private final ExecutorService executor;
    private final int maxHistorySize;
    private final Map<String, Integer> commandFrequency;
    
    public CommandHistoryService() {
        this.history = new ArrayList<>();
        this.executor = Executors.newSingleThreadExecutor();
        this.maxHistorySize = 1000;
        this.commandFrequency = new ConcurrentHashMap<>();
    }
    
    public CommandHistoryService(int maxHistorySize) {
        this.history = new ArrayList<>();
        this.executor = Executors.newSingleThreadExecutor();
        this.maxHistorySize = maxHistorySize;
        this.commandFrequency = new ConcurrentHashMap<>();
    }
    
    /**
     * 记录命令执行
     */
    public void recordCommand(String commandName, String arguments, String result, 
                               boolean success, long executionTimeMs) {
        executor.submit(() -> {
            CommandHistoryEntry entry = new CommandHistoryEntry();
            entry.timestamp = Instant.now().toString();
            entry.commandName = commandName;
            entry.arguments = arguments;
            entry.result = result;
            entry.success = success;
            entry.executionTimeMs = executionTimeMs;
            
            synchronized (history) {
                history.add(entry);
                if (history.size() > maxHistorySize) {
                    history.remove(0);
                }
            }
            
            // 更新命令频率统计
            commandFrequency.merge(commandName, 1, Integer::sum);
        });
    }
    
    /**
     * 记录简单命令
     */
    public void recordCommand(String commandName, boolean success) {
        recordCommand(commandName, null, null, success, 0);
    }
    
    /**
     * 获取最近的命令历史
     */
    public List<CommandHistoryEntry> getRecentHistory(int limit) {
        synchronized (history) {
            int size = history.size();
            int start = Math.max(0, size - limit);
            return new ArrayList<>(history.subList(start, size));
        }
    }
    
    /**
     * 获取所有命令历史
     */
    public List<CommandHistoryEntry> getAllHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }
    
    /**
     * 搜索命令历史
     */
    public List<CommandHistoryEntry> searchHistory(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllHistory();
        }
        
        String lowerQuery = query.toLowerCase();
        synchronized (history) {
            List<CommandHistoryEntry> results = new ArrayList<>();
            for (CommandHistoryEntry entry : history) {
                if (entry.commandName.toLowerCase().contains(lowerQuery) ||
                    (entry.arguments != null && entry.arguments.toLowerCase().contains(lowerQuery)) ||
                    (entry.result != null && entry.result.toLowerCase().contains(lowerQuery))) {
                    results.add(entry);
                }
            }
            return results;
        }
    }
    
    /**
     * 按条件过滤命令历史
     */
    public List<CommandHistoryEntry> filterHistory(Predicate<CommandHistoryEntry> predicate) {
        synchronized (history) {
            List<CommandHistoryEntry> results = new ArrayList<>();
            for (CommandHistoryEntry entry : history) {
                if (predicate.test(entry)) {
                    results.add(entry);
                }
            }
            return results;
        }
    }
    
    /**
     * 获取失败的命令历史
     */
    public List<CommandHistoryEntry> getFailedCommands() {
        return filterHistory(entry -> !entry.success);
    }
    
    /**
     * 获取命令频率统计
     */
    public Map<String, Integer> getCommandFrequency() {
        return new ConcurrentHashMap<>(commandFrequency);
    }
    
    /**
     * 获取最常使用的命令
     */
    public List<Map.Entry<String, Integer>> getTopCommands(int limit) {
        return commandFrequency.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(limit)
                .toList();
    }
    
    /**
     * 清除命令历史
     */
    public void clearHistory() {
        synchronized (history) {
            history.clear();
        }
        commandFrequency.clear();
    }
    
    /**
     * 导出命令历史为 JSON
     */
    public String exportToJson() {
        synchronized (history) {
            StringBuilder json = new StringBuilder("[\n");
            for (int i = 0; i < history.size(); i++) {
                CommandHistoryEntry entry = history.get(i);
                json.append("  {\n");
                json.append("    \"timestamp\": \"").append(entry.timestamp).append("\",\n");
                json.append("    \"commandName\": \"").append(escapeJson(entry.commandName)).append("\",\n");
                json.append("    \"arguments\": \"").append(escapeJson(entry.arguments)).append("\",\n");
                json.append("    \"success\": ").append(entry.success).append(",\n");
                json.append("    \"executionTimeMs\": ").append(entry.executionTimeMs).append("\n");
                json.append("  }");
                if (i < history.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }
            json.append("]");
            return json.toString();
        }
    }
    
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
    
    /**
     * 关闭服务
     */
    public void shutdown() {
        executor.shutdown();
    }
    
    /**
     * 命令历史条目
     */
    public static class CommandHistoryEntry {
        public String timestamp;
        public String commandName;
        public String arguments;
        public String result;
        public boolean success;
        public long executionTimeMs;
        
        public Map<String, Object> toMap() {
            return Map.of(
                    "timestamp", timestamp,
                    "commandName", commandName,
                    "arguments", arguments != null ? arguments : "",
                    "result", result != null ? result : "",
                    "success", success,
                    "executionTimeMs", executionTimeMs
            );
        }
    }
}
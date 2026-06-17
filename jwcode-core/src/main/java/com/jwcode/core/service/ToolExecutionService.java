package com.jwcode.core.service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ToolExecutionService - 工具执行服务
 * 
 * 功能说明：
 * 统一管理工具调用，包括工具注册、执行、权限控制、缓存等。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ToolExecutionService {
    
    private final Map<String, ToolDefinition> registeredTools;
    private final Map<String, ToolExecutionRecord> executionHistory;
    private final Map<String, Object> resultCache;
    private final Map<String, ToolPermissions> toolPermissions;
    private final AtomicInteger executionCounter;
    private boolean requireApproval;
    private int cacheEnabled;
    
    public ToolExecutionService() {
        this.registeredTools = new ConcurrentHashMap<>();
        this.executionHistory = Collections.synchronizedMap(
            new LinkedHashMap<String, ToolExecutionRecord>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ToolExecutionRecord> eldest) {
                    return size() > 1000;
                }
            });
        this.resultCache = new ConcurrentHashMap<>();
        this.toolPermissions = new ConcurrentHashMap<>();
        this.executionCounter = new AtomicInteger(0);
        this.requireApproval = false;
        this.cacheEnabled = 300; // 5 分钟缓存
        registerBuiltInTools();
    }
    
    /**
     * 注册内置工具
     */
    private void registerBuiltInTools() {
        // 文件操作工具
        registerTool(new ToolDefinition("file_read", "读取文件", 
            new String[]{"path"}, "读取指定文件的内容"));
        registerTool(new ToolDefinition("file_write", "写入文件", 
            new String[]{"path", "content"}, "写入内容到指定文件"));
        registerTool(new ToolDefinition("bash", "执行命令", 
            new String[]{"command"}, "执行 shell 命令"));
        
        // 搜索工具
        registerTool(new ToolDefinition("grep", "文本搜索", 
            new String[]{"pattern", "path"}, "在文件中搜索文本"));
        registerTool(new ToolDefinition("glob", "文件匹配", 
            new String[]{"pattern"}, "匹配文件路径"));
        
        // 设置默认权限
        toolPermissions.put("file_read", ToolPermissions.READ_ONLY);
        toolPermissions.put("file_write", ToolPermissions.WRITE);
        toolPermissions.put("bash", ToolPermissions.DANGEROUS);
        toolPermissions.put("grep", ToolPermissions.READ_ONLY);
        toolPermissions.put("glob", ToolPermissions.READ_ONLY);
    }
    
    /**
     * 注册工具
     */
    public void registerTool(ToolDefinition tool) {
        registeredTools.put(tool.name, tool);
    }
    
    /**
     * 注销工具
     */
    public void unregisterTool(String toolName) {
        registeredTools.remove(toolName);
    }
    
    /**
     * 获取已注册工具列表
     */
    public List<ToolDefinition> getRegisteredTools() {
        return new ArrayList<>(registeredTools.values());
    }
    
    /**
     * 执行工具
     */
    public CompletableFuture<ToolExecutionResult> executeTool(String toolName, Map<String, Object> args) {
        return CompletableFuture.supplyAsync(() -> {
            ToolDefinition tool = registeredTools.get(toolName);
            if (tool == null) {
                return ToolExecutionResult.error("未知工具：" + toolName);
            }
            
            // 检查权限
            if (!checkPermission(toolName)) {
                return ToolExecutionResult.error("没有执行该工具的权限");
            }
            
            // 检查是否需要审批
            if (requireApproval && toolPermissions.get(toolName) == ToolPermissions.DANGEROUS) {
                return ToolExecutionResult.error("该操作需要审批");
            }
            
            // 检查缓存
            String cacheKey = generateCacheKey(toolName, args);
            if (resultCache.containsKey(cacheKey)) {
                return (ToolExecutionResult) resultCache.get(cacheKey);
            }
            
            // 执行工具
            long startTime = System.currentTimeMillis();
            ToolExecutionResult result = executeToolInternal(tool, args);
            long duration = System.currentTimeMillis() - startTime;
            
            // 记录执行历史
            recordExecution(toolName, args, result, duration);
            
            // 缓存结果
            if (result.success && cacheEnabled > 0) {
                resultCache.put(cacheKey, result);
            }
            
            return result;
        });
    }
    
    /**
     * 执行工具内部实现
     */
    private ToolExecutionResult executeToolInternal(ToolDefinition tool, Map<String, Object> args) {
        // 验证必需参数
        for (String requiredArg : tool.requiredArgs) {
            if (!args.containsKey(requiredArg)) {
                return ToolExecutionResult.error("缺少必需参数：" + requiredArg);
            }
        }
        
        // 模拟执行（实际应该调用具体工具实现）
        try {
            Thread.sleep(10); // 模拟执行延迟
            return ToolExecutionResult.success("执行成功：" + tool.name, args);
        } catch (Exception e) {
            return ToolExecutionResult.error("执行失败：" + e.getMessage());
        }
    }
    
    /**
     * 检查权限
     */
    private boolean checkPermission(String toolName) {
        ToolPermissions permission = toolPermissions.get(toolName);
        if (permission == null) {
            return true;
        }
        return permission != ToolPermissions.DENIED;
    }
    
    /**
     * 设置工具权限
     */
    public void setToolPermission(String toolName, ToolPermissions permission) {
        toolPermissions.put(toolName, permission);
    }
    
    /**
     * 生成缓存键
     */
    private String generateCacheKey(String toolName, Map<String, Object> args) {
        return toolName + ":" + args.hashCode();
    }
    
    /**
     * 记录执行历史
     */
    private void recordExecution(String toolName, Map<String, Object> args, 
                                  ToolExecutionResult result, long duration) {
        int id = executionCounter.incrementAndGet();
        ToolExecutionRecord record = new ToolExecutionRecord(
            id, toolName, args, result, duration, System.currentTimeMillis()
        );
        executionHistory.put(String.valueOf(id), record);
    }
    
    /**
     * 获取执行历史
     */
    public List<ToolExecutionRecord> getExecutionHistory(int limit) {
        List<ToolExecutionRecord> history = new ArrayList<>(executionHistory.values());
        history.sort((a, b) -> Integer.compare(b.id, a.id));
        return history.subList(0, Math.min(limit, history.size()));
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        resultCache.clear();
    }
    
    /**
     * 清除缓存（指定工具）
     */
    public void clearCache(String toolName) {
        resultCache.entrySet().removeIf(
            entry -> entry.getKey().startsWith(toolName + ":")
        );
    }
    
    /**
     * 设置是否需要审批
     */
    public void setRequireApproval(boolean require) {
        this.requireApproval = require;
    }
    
    /**
     * 设置缓存时间（秒）
     */
    public void setCacheTimeout(int seconds) {
        this.cacheEnabled = seconds;
    }
    
    /**
     * 获取执行统计
     */
    public Map<String, Integer> getToolUsageStats() {
        Map<String, Integer> stats = new HashMap<>();
        for (ToolExecutionRecord record : executionHistory.values()) {
            stats.merge(record.toolName, 1, Integer::sum);
        }
        return stats;
    }
    
    /**
     * 工具定义类
     */
    public static class ToolDefinition {
        public final String name;
        public final String description;
        public final String[] requiredArgs;
        public final Map<String, String> argDescriptions;
        
        public ToolDefinition(String name, String description, 
                             String[] requiredArgs, String fullDescription) {
            this.name = name;
            this.description = description;
            this.requiredArgs = requiredArgs;
            this.argDescriptions = new HashMap<>();
        }
        
        public void addArgDescription(String arg, String desc) {
            argDescriptions.put(arg, desc);
        }
    }
    
    /**
     * 工具执行结果类
     */
    public static class ToolExecutionResult {
        public final boolean success;
        public final String message;
        public final Map<String, Object> data;
        public final String error;
        
        private ToolExecutionResult(boolean success, String message, 
                          Map<String, Object> data, String error) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.error = error;
        }
        
        public static ToolExecutionResult success(String message, Map<String, Object> data) {
            return new ToolExecutionResult(true, message, data, null);
        }
        
        public static ToolExecutionResult success(String message) {
            return new ToolExecutionResult(true, message, null, null);
        }
        
        public static ToolExecutionResult error(String error) {
            return new ToolExecutionResult(false, null, null, error);
        }
        
        public Object getData() {
            return data != null ? data.get("result") : null;
        }
    }
    
    /**
     * 工具执行记录类
     */
    public static class ToolExecutionRecord {
        public final int id;
        public final String toolName;
        public final Map<String, Object> args;
        public final ToolExecutionResult result;
        public final long duration;
        public final long timestamp;
        
        public ToolExecutionRecord(int id, String toolName, Map<String, Object> args,
                                   ToolExecutionResult result, long duration, long timestamp) {
            this.id = id;
            this.toolName = toolName;
            this.args = args;
            this.result = result;
            this.duration = duration;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * 工具权限枚举
     */
    public enum ToolPermissions {
        READ_ONLY,      // 只读权限
        WRITE,          // 写入权限
        DANGEROUS,      // 危险操作（需要审批）
        DENIED          // 禁止使用
    }
}

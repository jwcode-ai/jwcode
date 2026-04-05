package com.jwcode.core.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * AgentMemory - Agent 内存管理
 * 
 * 功能说明：
 * 负责存储和检索 Agent 的上下文信息，包括短期记忆、长期记忆和工作记忆。
 * 
 * 内存类型：
 * - 短期记忆 (Short-term): 最近的操作和对话
 * - 长期记忆 (Long-term): 持久化的知识和经验
 * - 工作记忆 (Working): 当前任务的临时数据
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class AgentMemory {
    
    private static final int DEFAULT_SHORT_TERM_CAPACITY = 100;
    private static final int DEFAULT_WORKING_CAPACITY = 50;
    
    private final Map<String, AgentMemoryContext> memoryContexts;
    private final Map<String, LongTermMemory> longTermMemories;
    private MemoryStorage storage;
    private int shortTermCapacity;
    private int workingCapacity;
    
    public AgentMemory() {
        this(DEFAULT_SHORT_TERM_CAPACITY, DEFAULT_WORKING_CAPACITY);
    }
    
    public AgentMemory(int shortTermCapacity, int workingCapacity) {
        this.memoryContexts = new ConcurrentHashMap<>();
        this.longTermMemories = new ConcurrentHashMap<>();
        this.storage = new InMemoryStorage();
        this.shortTermCapacity = shortTermCapacity;
        this.workingCapacity = workingCapacity;
    }
    
    /**
     * 设置存储后端
     */
    public void setStorage(MemoryStorage storage) {
        this.storage = storage;
    }
    
    /**
     * 初始化 Agent 内存上下文
     */
    public void initializeContext(String agentId) {
        memoryContexts.put(agentId, new AgentMemoryContext(agentId));
        longTermMemories.put(agentId, new LongTermMemory());
    }
    
    /**
     * 清除 Agent 内存
     */
    public void clearContext(String agentId) {
        memoryContexts.remove(agentId);
        longTermMemories.remove(agentId);
    }
    
    /**
     * 添加到短期记忆
     */
    public void addToShortTerm(String agentId, MemoryItem item) {
        AgentMemoryContext context = memoryContexts.get(agentId);
        if (context == null) {
            initializeContext(agentId);
            context = memoryContexts.get(agentId);
        }
        
        context.shortTerm.add(item);
        
        // 超出容量时移除最早的记忆
        while (context.shortTerm.size() > shortTermCapacity) {
            context.shortTerm.remove(0);
        }
    }
    
    /**
     * 从短期记忆获取
     */
    public List<MemoryItem> getShortTerm(String agentId, int limit) {
        AgentMemoryContext context = memoryContexts.get(agentId);
        if (context == null) {
            return new ArrayList<>();
        }
        
        int start = Math.max(0, context.shortTerm.size() - limit);
        return new ArrayList<>(context.shortTerm.subList(start, context.shortTerm.size()));
    }
    
    /**
     * 搜索短期记忆
     */
    public List<MemoryItem> searchShortTerm(String agentId, String query) {
        List<MemoryItem> results = new ArrayList<>();
        List<MemoryItem> shortTerm = getShortTerm(agentId, shortTermCapacity);
        
        for (MemoryItem item : shortTerm) {
            if (matches(item, query)) {
                results.add(item);
            }
        }
        
        return results;
    }
    
    /**
     * 添加到长期记忆
     */
    public void addToLongTerm(String agentId, MemoryItem item) {
        LongTermMemory ltm = longTermMemories.get(agentId);
        if (ltm == null) {
            ltm = new LongTermMemory();
            longTermMemories.put(agentId, ltm);
        }
        
        ltm.items.add(item);
        
        // 异步持久化
        if (storage != null) {
            storage.save(agentId, ltm.items);
        }
    }
    
    /**
     * 从长期记忆获取
     */
    public List<MemoryItem> getLongTerm(String agentId) {
        LongTermMemory ltm = longTermMemories.get(agentId);
        if (ltm == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(ltm.items);
    }
    
    /**
     * 搜索长期记忆
     */
    public List<MemoryItem> searchLongTerm(String agentId, String query) {
        List<MemoryItem> results = new ArrayList<>();
        LongTermMemory ltm = longTermMemories.get(agentId);
        
        if (ltm == null) {
            return results;
        }
        
        for (MemoryItem item : ltm.items) {
            if (matches(item, query)) {
                results.add(item);
            }
        }
        
        return results;
    }
    
    /**
     * 添加到工作记忆
     */
    public void addToWorking(String agentId, String key, Object value) {
        AgentMemoryContext context = memoryContexts.get(agentId);
        if (context == null) {
            initializeContext(agentId);
            context = memoryContexts.get(agentId);
        }
        
        context.working.put(key, value);
        
        // 超出容量时移除最早的项目
        while (context.working.size() > workingCapacity) {
            String firstKey = context.working.keySet().iterator().next();
            context.working.remove(firstKey);
        }
    }
    
    /**
     * 从工作记忆获取
     */
    public Object getFromWorking(String agentId, String key) {
        AgentMemoryContext context = memoryContexts.get(agentId);
        if (context == null) {
            return null;
        }
        return context.working.get(key);
    }
    
    /**
     * 从工作记忆移除
     */
    public void removeFromWorking(String agentId, String key) {
        AgentMemoryContext context = memoryContexts.get(agentId);
        if (context != null) {
            context.working.remove(key);
        }
    }
    
    /**
     * 清空工作记忆
     */
    public void clearWorking(String agentId) {
        AgentMemoryContext context = memoryContexts.get(agentId);
        if (context != null) {
            context.working.clear();
        }
    }
    
    /**
     * 获取记忆摘要
     */
    public String getSummary(String agentId) {
        AgentMemoryContext context = memoryContexts.get(agentId);
        if (context == null) {
            return "无记忆数据";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("短期记忆：").append(context.shortTerm.size()).append(" 项\n");
        sb.append("工作记忆：").append(context.working.size()).append(" 项\n");
        
        LongTermMemory ltm = longTermMemories.get(agentId);
        if (ltm != null) {
            sb.append("长期记忆：").append(ltm.items.size()).append(" 项");
        }
        
        return sb.toString();
    }
    
    /**
     * 导出记忆
     */
    public CompletableFuture<Map<String, Object>> exportMemory(String agentId) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> export = new HashMap<>();
            
            AgentMemoryContext context = memoryContexts.get(agentId);
            if (context != null) {
                export.put("shortTerm", new ArrayList<>(context.shortTerm));
                export.put("working", new HashMap<>(context.working));
            }
            
            LongTermMemory ltm = longTermMemories.get(agentId);
            if (ltm != null) {
                export.put("longTerm", new ArrayList<>(ltm.items));
            }
            
            return export;
        });
    }
    
    /**
     * 导入记忆
     */
    public void importMemory(String agentId, Map<String, Object> data) {
        initializeContext(agentId);
        
        AgentMemoryContext context = memoryContexts.get(agentId);
        
        @SuppressWarnings("unchecked")
        List<MemoryItem> shortTerm = (List<MemoryItem>) data.get("shortTerm");
        if (shortTerm != null && context != null) {
            context.shortTerm.addAll(shortTerm);
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> working = (Map<String, Object>) data.get("working");
        if (working != null && context != null) {
            context.working.putAll(working);
        }
        
        @SuppressWarnings("unchecked")
        List<MemoryItem> longTerm = (List<MemoryItem>) data.get("longTerm");
        if (longTerm != null) {
            LongTermMemory ltm = longTermMemories.get(agentId);
            if (ltm != null) {
                ltm.items.addAll(longTerm);
            }
        }
    }
    
    /**
     * 检查记忆是否匹配查询
     */
    private boolean matches(MemoryItem item, String query) {
        String lowerQuery = query.toLowerCase();
        return item.content.toLowerCase().contains(lowerQuery) ||
               (item.type != null && item.type.toLowerCase().contains(lowerQuery)) ||
               (item.source != null && item.source.toLowerCase().contains(lowerQuery));
    }
    
    /**
     * 内存存储接口
     */
    public interface MemoryStorage {
        void save(String agentId, List<MemoryItem> items);
        List<MemoryItem> load(String agentId);
        void delete(String agentId);
    }
    
    /**
     * 内存存储实现（基于内存）
     */
    public static class InMemoryStorage implements MemoryStorage {
        private final Map<String, List<MemoryItem>> store = new ConcurrentHashMap<>();
        
        @Override
        public void save(String agentId, List<MemoryItem> items) {
            store.put(agentId, new ArrayList<>(items));
        }
        
        @Override
        public List<MemoryItem> load(String agentId) {
            List<MemoryItem> items = store.get(agentId);
            return items != null ? new ArrayList<>(items) : new ArrayList<>();
        }
        
        @Override
        public void delete(String agentId) {
            store.remove(agentId);
        }
    }
    
    /**
     * Agent 内存上下文类
     */
    public static class AgentMemoryContext {
        public final String agentId;
        public final List<MemoryItem> shortTerm;
        public final Map<String, Object> working;
        
        public AgentMemoryContext(String agentId) {
            this.agentId = agentId;
            this.shortTerm = new ArrayList<>();
            this.working = new HashMap<>();
        }
    }
    
    /**
     * 长期记忆类
     */
    public static class LongTermMemory {
        public final List<MemoryItem> items;
        
        public LongTermMemory() {
            this.items = new ArrayList<>();
        }
    }
    
    /**
     * 记忆条目类
     */
    public static class MemoryItem {
        public String content;
        public String type;
        public String source;
        public long timestamp;
        public Map<String, Object> metadata;
        
        public MemoryItem(String content) {
            this.content = content;
            this.timestamp = System.currentTimeMillis();
            this.metadata = new HashMap<>();
        }
        
        public MemoryItem type(String type) {
            this.type = type;
            return this;
        }
        
        public MemoryItem source(String source) {
            this.source = source;
            return this;
        }
        
        public MemoryItem metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }
        
        /**
         * 创建对话记忆
         */
        public static MemoryItem conversation(String content, String role) {
            return new MemoryItem(content)
                .type("conversation")
                .metadata("role", role);
        }
        
        /**
         * 创建代码记忆
         */
        public static MemoryItem code(String content, String language) {
            return new MemoryItem(content)
                .type("code")
                .metadata("language", language);
        }
        
        /**
         * 创建任务记忆
         */
        public static MemoryItem task(String content, String status) {
            return new MemoryItem(content)
                .type("task")
                .metadata("status", status);
        }
    }
}
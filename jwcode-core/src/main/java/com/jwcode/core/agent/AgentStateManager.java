package com.jwcode.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Agent 状态管理器 - Phase 2
 * 
 * 负责 Agent 状态的持久化和跨会话恢复
 * 支持：
 * - 状态保存到文件
 * - 状态从文件加载
 * - 自动备份和清理
 * - 状态版本管理
 */
public class AgentStateManager {
    
    private static final Logger logger = Logger.getLogger(AgentStateManager.class.getName());
    
    // 默认存储路径
    private static final String DEFAULT_STATE_DIR = System.getProperty("user.home") + 
        File.separator + ".jwcode" + File.separator + "agent-states";
    
    // 文件扩展名
    private static final String STATE_FILE_EXT = ".json";
    private static final String BACKUP_FILE_EXT = ".backup";
    
    // JSON 映射器
    private final ObjectMapper objectMapper;
    
    // 状态存储目录
    private final Path stateDirectory;
    
    // 内存缓存
    private final Map<String, AgentState> stateCache = new ConcurrentHashMap<>();
    
    // 配置
    private final StateManagerConfig config;
    
    /**
     * 状态管理配置
     */
    public static class StateManagerConfig {
        private boolean enableCache = true;
        private boolean autoBackup = true;
        private int maxBackupCount = 5;
        private long maxStateAgeDays = 30;
        private boolean prettyPrintJson = true;
        
        public boolean isEnableCache() { return enableCache; }
        public void setEnableCache(boolean enableCache) { this.enableCache = enableCache; }
        
        public boolean isAutoBackup() { return autoBackup; }
        public void setAutoBackup(boolean autoBackup) { this.autoBackup = autoBackup; }
        
        public int getMaxBackupCount() { return maxBackupCount; }
        public void setMaxBackupCount(int maxBackupCount) { this.maxBackupCount = maxBackupCount; }
        
        public long getMaxStateAgeDays() { return maxStateAgeDays; }
        public void setMaxStateAgeDays(long maxStateAgeDays) { this.maxStateAgeDays = maxStateAgeDays; }
        
        public boolean isPrettyPrintJson() { return prettyPrintJson; }
        public void setPrettyPrintJson(boolean prettyPrintJson) { this.prettyPrintJson = prettyPrintJson; }
        
        public static StateManagerConfig defaultConfig() {
            return new StateManagerConfig();
        }
    }
    
    /**
     * Agent 状态快照
     */
    public static class AgentState {
        private String agentId;
        private String agentType;
        private String name;
        private String role;
        private String color;
        private String status;
        private String currentTask;
        private String context;
        private String lastResult;
        private int completedTasks;
        private long createdAt;
        private long updatedAt;
        private Map<String, Object> memory = new HashMap<>();
        private Map<String, Object> metadata = new HashMap<>();
        private int version = 1;
        private String savedAt;
        
        // 构造函数
        public AgentState() {}
        
        public AgentState(String agentId) {
            this.agentId = agentId;
            this.savedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        
        // Getters 和 Setters
        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }
        
        public String getAgentType() { return agentType; }
        public void setAgentType(String agentType) { this.agentType = agentType; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getCurrentTask() { return currentTask; }
        public void setCurrentTask(String currentTask) { this.currentTask = currentTask; }
        
        public String getContext() { return context; }
        public void setContext(String context) { this.context = context; }
        
        public String getLastResult() { return lastResult; }
        public void setLastResult(String lastResult) { this.lastResult = lastResult; }
        
        public int getCompletedTasks() { return completedTasks; }
        public void setCompletedTasks(int completedTasks) { this.completedTasks = completedTasks; }
        
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        
        public Map<String, Object> getMemory() { return memory; }
        public void setMemory(Map<String, Object> memory) { this.memory = memory; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        
        public String getSavedAt() { return savedAt; }
        public void setSavedAt(String savedAt) { this.savedAt = savedAt; }
        
        /**
         * 增加版本号
         */
        public void incrementVersion() {
            this.version++;
            this.savedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        
        /**
         * 获取内存值
         */
        @SuppressWarnings("unchecked")
        public <T> T getMemory(String key) {
            return (T) memory.get(key);
        }
        
        /**
         * 设置内存值
         */
        public void setMemory(String key, Object value) {
            if (memory == null) memory = new HashMap<>();
            memory.put(key, value);
        }
        
        @Override
        public String toString() {
            return String.format("AgentState{id='%s', name='%s', status='%s', version=%d}",
                agentId, name, status, version);
        }
    }
    
    // ==================== 构造函数 ====================
    
    public AgentStateManager() {
        this(DEFAULT_STATE_DIR, StateManagerConfig.defaultConfig());
    }
    
    public AgentStateManager(String stateDirectory) {
        this(stateDirectory, StateManagerConfig.defaultConfig());
    }
    
    public AgentStateManager(StateManagerConfig config) {
        this(DEFAULT_STATE_DIR, config);
    }
    
    public AgentStateManager(String stateDirectory, StateManagerConfig config) {
        this.config = config;
        this.stateDirectory = Paths.get(stateDirectory);
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        
        if (config.isPrettyPrintJson()) {
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        }
        
        // 确保目录存在
        initializeDirectory();
    }
    
    // ==================== 核心方法 ====================
    
    /**
     * 保存 Agent 状态
     * 
     * @param agent 要保存的 Agent
     * @return 保存的状态文件路径
     */
    public Path saveState(Agent agent) {
        if (agent == null) {
            throw new IllegalArgumentException("Agent cannot be null");
        }
        
        String agentId = agent.getId();
        logger.info("Saving state for agent: " + agentId);
        
        try {
            // 创建备份
            if (config.isAutoBackup()) {
                createBackup(agentId);
            }
            
            // 构建状态
            AgentState state = buildAgentState(agent);
            
            // 更新版本
            AgentState existingState = loadStateFromFile(agentId);
            if (existingState != null) {
                state.setVersion(existingState.getVersion() + 1);
            }
            state.incrementVersion();
            
            // 保存到文件
            Path stateFile = getStateFilePath(agentId);
            String json = objectMapper.writeValueAsString(state);
            Files.writeString(stateFile, json, StandardCharsets.UTF_8);
            
            // 更新缓存
            if (config.isEnableCache()) {
                stateCache.put(agentId, state);
            }
            
            logger.info("State saved for agent: " + agentId + " at " + stateFile);
            return stateFile;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save state for agent: " + agentId, e);
            throw new RuntimeException("Failed to save agent state", e);
        }
    }
    
    /**
     * 加载 Agent 状态
     * 
     * @param agentId Agent ID
     * @return Agent 状态，如果不存在返回 null
     */
    public AgentState loadState(String agentId) {
        if (agentId == null || agentId.isEmpty()) {
            return null;
        }
        
        // 检查缓存
        if (config.isEnableCache() && stateCache.containsKey(agentId)) {
            return stateCache.get(agentId);
        }
        
        // 从文件加载
        AgentState state = loadStateFromFile(agentId);
        
        // 更新缓存
        if (config.isEnableCache() && state != null) {
            stateCache.put(agentId, state);
        }
        
        return state;
    }
    
    /**
     * 检查是否存在状态
     */
    public boolean hasState(String agentId) {
        if (agentId == null || agentId.isEmpty()) {
            return false;
        }
        return Files.exists(getStateFilePath(agentId));
    }
    
    /**
     * 删除 Agent 状态
     */
    public boolean deleteState(String agentId) {
        if (agentId == null || agentId.isEmpty()) {
            return false;
        }
        
        logger.info("Deleting state for agent: " + agentId);
        
        try {
            Path stateFile = getStateFilePath(agentId);
            boolean deleted = Files.deleteIfExists(stateFile);
            
            if (deleted) {
                stateCache.remove(agentId);
                logger.info("State deleted for agent: " + agentId);
            }
            
            return deleted;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to delete state for agent: " + agentId, e);
            return false;
        }
    }
    
    /**
     * 列出所有已保存的 Agent
     */
    public List<AgentState> listSavedAgents() {
        List<AgentState> states = new ArrayList<>();
        
        try {
            if (!Files.exists(stateDirectory)) {
                return states;
            }
            
            try (var stream = Files.list(stateDirectory)) {
                states = stream
                    .filter(p -> p.toString().endsWith(STATE_FILE_EXT))
                    .map(p -> {
                        try {
                            String json = Files.readString(p, StandardCharsets.UTF_8);
                            return objectMapper.readValue(json, AgentState.class);
                        } catch (IOException e) {
                            logger.log(Level.WARNING, "Failed to load state from: " + p, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted((a, b) -> {
                        // 按更新时间倒序
                        long timeA = a.getUpdatedAt();
                        long timeB = b.getUpdatedAt();
                        return Long.compare(timeB, timeA);
                    })
                    .collect(Collectors.toList());
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to list saved agents", e);
        }
        
        return states;
    }
    
    /**
     * 列出所有已保存的 Agent ID
     */
    public List<String> listSavedAgentIds() {
        try {
            if (!Files.exists(stateDirectory)) {
                return Collections.emptyList();
            }
            
            try (var stream = Files.list(stateDirectory)) {
                return stream
                    .filter(p -> p.toString().endsWith(STATE_FILE_EXT))
                    .map(p -> p.getFileName().toString())
                    .map(n -> n.substring(0, n.length() - STATE_FILE_EXT.length()))
                    .collect(Collectors.toList());
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to list saved agent IDs", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 恢复 Agent 状态到 Agent 对象
     */
    public <T extends Agent> T restoreState(T agent, AgentState state) {
        if (agent == null || state == null) {
            return agent;
        }
        
        logger.info("Restoring state for agent: " + state.getAgentId());
        
        // 如果 Agent 实现了 StatefulAgent 接口，调用其恢复方法
        if (agent instanceof StatefulAgent) {
            ((StatefulAgent) agent).restoreFromState(state);
        }
        
        return agent;
    }
    
    /**
     * 清除所有状态
     */
    public int clearAllStates() {
        logger.info("Clearing all agent states");
        
        int count = 0;
        try {
            List<String> agentIds = listSavedAgentIds();
            for (String agentId : agentIds) {
                if (deleteState(agentId)) {
                    count++;
                }
            }
            stateCache.clear();
            logger.info("Cleared " + count + " agent states");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to clear all states", e);
        }
        
        return count;
    }
    
    /**
     * 清理过期状态
     */
    public int cleanupOldStates() {
        long maxAgeMs = config.getMaxStateAgeDays() * 24 * 60 * 60 * 1000;
        long cutoffTime = System.currentTimeMillis() - maxAgeMs;
        
        logger.info("Cleaning up states older than " + config.getMaxStateAgeDays() + " days");
        
        int count = 0;
        List<AgentState> allStates = listSavedAgents();
        
        for (AgentState state : allStates) {
            if (state.getUpdatedAt() < cutoffTime) {
                if (deleteState(state.getAgentId())) {
                    count++;
                }
            }
        }
        
        logger.info("Cleaned up " + count + " old states");
        return count;
    }
    
    // ==================== 备份管理 ====================
    
    /**
     * 创建备份
     */
    public void createBackup(String agentId) {
        Path stateFile = getStateFilePath(agentId);
        if (!Files.exists(stateFile)) {
            return;
        }
        
        try {
            String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path backupFile = stateDirectory.resolve(
                agentId + "_" + timestamp + BACKUP_FILE_EXT);
            
            Files.copy(stateFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            
            // 清理旧备份
            cleanupOldBackups(agentId);
            
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to create backup for: " + agentId, e);
        }
    }
    
    /**
     * 列出所有备份
     */
    public List<Path> listBackups(String agentId) {
        try {
            if (!Files.exists(stateDirectory)) {
                return Collections.emptyList();
            }
            
            String prefix = agentId + "_";
            try (var stream = Files.list(stateDirectory)) {
                return stream
                    .filter(p -> p.toString().endsWith(BACKUP_FILE_EXT))
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toList());
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to list backups", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 从备份恢复
     */
    public AgentState restoreFromBackup(String agentId, String backupTimestamp) {
        Path backupFile = stateDirectory.resolve(
            agentId + "_" + backupTimestamp + BACKUP_FILE_EXT);
        
        if (!Files.exists(backupFile)) {
            return null;
        }
        
        try {
            String json = Files.readString(backupFile, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, AgentState.class);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to restore from backup: " + backupFile, e);
            return null;
        }
    }
    
    // ==================== 私有方法 ====================
    
    private void initializeDirectory() {
        try {
            Files.createDirectories(stateDirectory);
            logger.info("State directory initialized: " + stateDirectory);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to create state directory: " + stateDirectory, e);
            throw new RuntimeException("Failed to initialize state directory", e);
        }
    }
    
    private Path getStateFilePath(String agentId) {
        String safeId = agentId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return stateDirectory.resolve(safeId + STATE_FILE_EXT);
    }
    
    private AgentState loadStateFromFile(String agentId) {
        Path stateFile = getStateFilePath(agentId);
        
        if (!Files.exists(stateFile)) {
            return null;
        }
        
        try {
            String json = Files.readString(stateFile, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, AgentState.class);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load state from file: " + stateFile, e);
            return null;
        }
    }
    
    private AgentState buildAgentState(Agent agent) {
        AgentState state = new AgentState(agent.getId());
        state.setAgentType(agent.getClass().getSimpleName());
        state.setName(agent.getName());
        
        // 如果 Agent 实现了 StatefulAgent 接口，调用其保存方法
        if (agent instanceof StatefulAgent) {
            ((StatefulAgent) agent).saveToState(state);
        }
        
        state.setUpdatedAt(System.currentTimeMillis());
        return state;
    }
    
    private void cleanupOldBackups(String agentId) {
        List<Path> backups = listBackups(agentId);
        if (backups.size() <= config.getMaxBackupCount()) {
            return;
        }
        
        // 删除最旧的备份
        List<Path> toDelete = backups.subList(0, backups.size() - config.getMaxBackupCount());
        for (Path backup : toDelete) {
            try {
                Files.deleteIfExists(backup);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to delete old backup: " + backup, e);
            }
        }
    }
    
    // ==================== 状态接口 ====================
    
    /**
     * 有状态 Agent 接口
     */
    public interface StatefulAgent {
        /**
         * 保存状态
         */
        void saveToState(AgentState state);
        
        /**
         * 从状态恢复
         */
        void restoreFromState(AgentState state);
    }
    
    // ==================== Getter ====================
    
    public Path getStateDirectory() {
        return stateDirectory;
    }
    
    public StateManagerConfig getConfig() {
        return config;
    }
}

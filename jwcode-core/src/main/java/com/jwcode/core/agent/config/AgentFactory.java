package com.jwcode.core.agent.config;

import com.jwcode.core.agent.Agent;
import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolRegistry;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * AgentFactory - Agent 工厂
 * 
 * 功能说明：
 * 从配置文件（JSON/YAML）创建 Agent 实例，支持配置继承和覆盖。
 * 
 * 功能特性：
 * - 从配置文件加载 Agent
 * - 支持配置继承（extends）
 * - 热重载配置
 * - 配置验证
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class AgentFactory {
    
    private static final Logger logger = Logger.getLogger(AgentFactory.class.getName());
    
    private final ToolRegistry toolRegistry;
    private final Map<String, AgentConfig> configCache;
    private final Map<String, AgentConfigWrapper> agentCache;
    private final Path configDirectory;
    private final WatchService watchService;
    private boolean watching = false;
    
    public AgentFactory(ToolRegistry toolRegistry) throws IOException {
        this(toolRegistry, AgentConfig.getDefaultConfigDir());
    }
    
    public AgentFactory(ToolRegistry toolRegistry, Path configDirectory) throws IOException {
        this.toolRegistry = toolRegistry;
        this.configDirectory = configDirectory;
        this.configCache = new ConcurrentHashMap<>();
        this.agentCache = new ConcurrentHashMap<>();
        this.watchService = FileSystems.getDefault().newWatchService();
        
        // 确保配置目录存在
        Files.createDirectories(configDirectory);
        
        // 加载所有配置
        loadAllConfigs();
        
        // 启动文件监听
        startWatching();
    }
    
    /**
     * 加载所有配置文件
     */
    public void loadAllConfigs() {
        configCache.clear();
        agentCache.clear();
        
        File dir = configDirectory.toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            logger.warning("Agent 配置目录不存在: " + configDirectory);
            return;
        }
        
        File[] files = dir.listFiles((d, name) -> 
            name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json")
        );
        
        if (files == null) return;
        
        // 第一遍：加载所有原始配置
        Map<String, AgentConfig> rawConfigs = new HashMap<>();
        for (File file : files) {
            try {
                AgentConfig config = loadConfigFromFile(file);
                if (config != null && config.getName() != null) {
                    rawConfigs.put(config.getName(), config);
                    configCache.put(config.getName(), config);
                }
            } catch (Exception e) {
                logger.warning("加载 Agent 配置失败: " + file + " - " + e.getMessage());
            }
        }
        
        // 第二遍：处理继承关系
        for (AgentConfig config : configCache.values()) {
            if (config.getExtendsAgent() != null) {
                AgentConfig parent = rawConfigs.get(config.getExtendsAgent());
                if (parent != null) {
                    config.mergeWithParent(parent);
                } else {
                    logger.warning("找不到父 Agent 配置: " + config.getExtendsAgent());
                }
            }
        }
        
        logger.info("加载了 " + configCache.size() + " 个 Agent 配置");
    }
    
    /**
     * 从文件加载配置
     */
    private AgentConfig loadConfigFromFile(File file) throws IOException {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            return AgentConfig.fromYaml(file);
        } else if (name.endsWith(".json")) {
            return AgentConfig.fromJson(file);
        }
        return null;
    }
    
    /**
     * 创建 Agent 实例
     */
    public AgentConfigWrapper createAgent(String name) {
        // 检查缓存
        if (agentCache.containsKey(name)) {
            return agentCache.get(name);
        }
        
        AgentConfig config = configCache.get(name);
        if (config == null) {
            logger.warning("找不到 Agent 配置: " + name);
            return null;
        }
        
        AgentConfigWrapper agent = buildAgent(config);
        agentCache.put(name, agent);
        return agent;
    }
    
    /**
     * 根据配置构建 Agent
     */
    private AgentConfigWrapper buildAgent(AgentConfig config) {
        // 解析工具
        List<Tool<?, ?, ?>> tools = config.getTools().stream()
            .map(toolName -> toolRegistry.getTool(toolName))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        // 构建模型配置
        Agent.ModelConfig modelConfig = new Agent.ModelConfig(
            config.getModel().getName(),
            config.getModel().getTemperature(),
            config.getModel().getMaxTokens()
        );
        modelConfig.setTopP(config.getModel().getTopP());
        
        return new AgentConfigWrapper(
            config.getId(),
            config.getName(),
            config.getDescription(),
            config.getSystemPrompt(),
            tools,
            new HashMap<>(config.getMetadata()),
            modelConfig,
            config.getPermissions()
        );
    }
    
    /**
     * 获取所有可用的 Agent 名称
     */
    public List<String> getAvailableAgents() {
        return new ArrayList<>(configCache.keySet());
    }
    
    /**
     * 获取 Agent 配置
     */
    public AgentConfig getConfig(String name) {
        return configCache.get(name);
    }
    
    /**
     * 重新加载配置
     */
    public void reload() {
        loadAllConfigs();
        logger.info("Agent 配置已重新加载");
    }
    
    /**
     * 保存 Agent 配置
     */
    public void saveConfig(AgentConfig config, String filename) throws IOException {
        String name = filename.toLowerCase();
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            config.saveAsYaml(configDirectory.resolve(filename).toString());
        } else {
            config.saveAsJson(configDirectory.resolve(filename).toString());
        }
        
        // 更新缓存
        configCache.put(config.getName(), config);
        agentCache.remove(config.getName()); // 清除 Agent 缓存，下次重新创建
    }
    
    /**
     * 创建新的 Agent 配置
     */
    public AgentConfig createNewAgent(String name, String type) {
        AgentConfig config = AgentConfig.createDefault(name, type);
        
        try {
            saveConfig(config, name + ".yaml");
        } catch (IOException e) {
            logger.warning("保存 Agent 配置失败: " + e.getMessage());
        }
        
        return config;
    }
    
    /**
     * 启动文件监听（热重载）
     */
    private void startWatching() {
        try {
            configDirectory.register(watchService, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
            );
            watching = true;
            
            // 启动监听线程
            Thread watchThread = new Thread(this::watchLoop);
            watchThread.setDaemon(true);
            watchThread.setName("AgentConfig-Watcher");
            watchThread.start();
            
        } catch (IOException e) {
            logger.warning("启动配置监听失败: " + e.getMessage());
        }
    }
    
    /**
     * 文件监听循环
     */
    private void watchLoop() {
        while (watching) {
            try {
                WatchKey key = watchService.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path path = (Path) event.context();
                        String filename = path.toString().toLowerCase();
                        
                        if (filename.endsWith(".yaml") || filename.endsWith(".yml") || 
                            filename.endsWith(".json")) {
                            logger.info("检测到配置文件变更: " + filename + "，重新加载...");
                            reload();
                        }
                    }
                    key.reset();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * 停止监听
     */
    public void stopWatching() {
        watching = false;
        try {
            watchService.close();
        } catch (IOException e) {
            logger.warning("关闭 WatchService 失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取配置目录
     */
    public Path getConfigDirectory() {
        return configDirectory;
    }
    
    /**
     * 验证配置
     */
    public List<String> validateConfig(AgentConfig config) {
        List<String> errors = new ArrayList<>();
        
        if (config.getName() == null || config.getName().isEmpty()) {
            errors.add("Agent 名称不能为空");
        }
        
        if (config.getSystemPrompt() == null || config.getSystemPrompt().isEmpty()) {
            errors.add("系统提示词不能为空");
        }
        
        // 验证工具是否存在
        for (String toolName : config.getTools()) {
            if (toolRegistry.getTool(toolName) == null) {
                errors.add("未知工具: " + toolName);
            }
        }
        
        return errors;
    }
}

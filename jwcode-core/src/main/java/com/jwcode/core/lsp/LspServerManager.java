package com.jwcode.core.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * LspServerManager - LSP 服务器管理
 * 
 * 功能说明：
 * 管理 LSP（Language Server Protocol）服务器的生命周期。
 * 支持服务器启动、停止、重启、配置等功能。
 * 
 * 核心特性：
 * - 多语言服务器支持
 * - 自动启动和懒加载
 * - 服务器配置管理
 * - 健康检查
 * - 日志记录
 * 
 * 上下文关系：
 * - 被 LspClient 用来管理服务器连接
 * - 与 LspDiagnosticRegistry 协作
 * - 为 IDE 提供语言服务支持
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LspServerManager {
    
    /**
     * 服务器实例映射表（语言 ID -> 服务器实例）
     */
    private final Map<String, LspServerInstance> serverInstances;
    
    /**
     * 服务器配置映射表（语言 ID -> 配置）
     */
    private final Map<String, LspServerConfig> serverConfigs;
    
    /**
     * 服务器状态监听器
     */
    private final List<Consumer<ServerStatusEvent>> statusListeners;
    
    /**
     * 启动超时（秒）
     */
    private static final int STARTUP_TIMEOUT_SECONDS = 30;
    
    /**
     * 健康检查间隔（毫秒）
     */
    private static final long HEALTH_CHECK_INTERVAL_MS = 60000;
    
    /**
     * 构造函数
     */
    public LspServerManager() {
        this.serverInstances = new ConcurrentHashMap<>();
        this.serverConfigs = new ConcurrentHashMap<>();
        this.statusListeners = new CopyOnWriteArrayList<>();
        
        // 注册默认配置
        registerDefaultConfigs();
    }
    
    /**
     * 注册默认服务器配置
     */
    private void registerDefaultConfigs() {
        // Java 语言服务器
        registerConfig(new LspServerConfig(
                "java",
                "Java Language Server",
                "java-language-server",
                Arrays.asList("java", "javac"),
                Map.of("rootPath", System.getProperty("user.dir"))
        ));
        
        // TypeScript 语言服务器
        registerConfig(new LspServerConfig(
                "typescript",
                "TypeScript Language Server",
                "typescript-language-server",
                Arrays.asList("typescript-language-server", "tsserver"),
                Map.of()
        ));
        
        // Python 语言服务器
        registerConfig(new LspServerConfig(
                "python",
                "Python Language Server",
                "pylsp",
                Arrays.asList("pylsp", "pyright", "jedi-language-server"),
                Map.of()
        ));
        
        // Rust 语言服务器
        registerConfig(new LspServerConfig(
                "rust",
                "Rust Language Server",
                "rust-analyzer",
                Arrays.asList("rust-analyzer"),
                Map.of()
        ));
        
        // Go 语言服务器
        registerConfig(new LspServerConfig(
                "go",
                "Go Language Server",
                "gopls",
                Arrays.asList("gopls"),
                Map.of()
        ));
    }
    
    /**
     * 添加服务器状态监听器
     * 
     * @param listener 监听器
     */
    public void addStatusListener(Consumer<ServerStatusEvent> listener) {
        this.statusListeners.add(listener);
    }
    
    /**
     * 移除服务器状态监听器
     * 
     * @param listener 监听器
     */
    public void removeStatusListener(Consumer<ServerStatusEvent> listener) {
        this.statusListeners.remove(listener);
    }
    
    /**
     * 注册服务器配置
     * 
     * @param config 服务器配置
     */
    public void registerConfig(LspServerConfig config) {
        serverConfigs.put(config.getLanguageId(), config);
    }
    
    /**
     * 获取服务器配置
     * 
     * @param languageId 语言 ID
     * @return 服务器配置
     */
    public LspServerConfig getConfig(String languageId) {
        return serverConfigs.get(languageId);
    }
    
    /**
     * 获取所有配置
     * 
     * @return 配置列表
     */
    public List<LspServerConfig> getAllConfigs() {
        return new ArrayList<>(serverConfigs.values());
    }
    
    /**
     * 启动语言服务器
     * 
     * @param languageId 语言 ID
     * @return 服务器实例的 CompletableFuture
     */
    public CompletableFuture<LspServerInstance> startServer(String languageId) {
        return startServer(languageId, null);
    }
    
    /**
     * 启动语言服务器（带工作目录）
     * 
     * @param languageId 语言 ID
     * @param workspaceRoot 工作目录
     * @return 服务器实例的 CompletableFuture
     */
    public CompletableFuture<LspServerInstance> startServer(String languageId, String workspaceRoot) {
        CompletableFuture<LspServerInstance> future = new CompletableFuture<>();
        
        LspServerConfig config = serverConfigs.get(languageId);
        if (config == null) {
            future.completeExceptionally(new IllegalArgumentException("未知的语言 ID: " + languageId));
            return future;
        }
        
        // 检查是否已经运行
        LspServerInstance existing = serverInstances.get(languageId);
        if (existing != null && existing.isRunning()) {
            future.complete(existing);
            return future;
        }
        
        // 查找可用的服务器命令
        String command = findServerCommand(config.getCommands());
        if (command == null) {
            future.completeExceptionally(new IllegalStateException(
                    "未找到语言服务器：" + config.getName() + 
                    ". 请确保已安装：" + String.join(", ", config.getCommands())));
            return future;
        }
        
        try {
            // 构建进程
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            if (workspaceRoot != null) {
                pb.directory(Paths.get(workspaceRoot).toFile());
            }
            
            // 启动进程
            Process process = pb.start();
            
            // 创建服务器实例
            LspServerInstance instance = new LspServerInstance(
                    languageId,
                    config,
                    command,
                    process,
                    workspaceRoot != null ? workspaceRoot : System.getProperty("user.dir")
            );
            
            serverInstances.put(languageId, instance);
            
            // 启动健康检查
            scheduleHealthCheck(languageId);
            
            // 通知监听器
            notifyStatusListeners(new ServerStatusEvent(languageId, ServerStatus.STARTED));
            
            future.complete(instance);
            
        } catch (IOException e) {
            future.completeExceptionally(new RuntimeException("启动语言服务器失败：" + e.getMessage(), e));
        }
        
        return future;
    }
    
    /**
     * 停止语言服务器
     * 
     * @param languageId 语言 ID
     * @return CompletableFuture
     */
    public CompletableFuture<Void> stopServer(String languageId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        LspServerInstance instance = serverInstances.remove(languageId);
        if (instance != null) {
            instance.shutdown();
            notifyStatusListeners(new ServerStatusEvent(languageId, ServerStatus.STOPPED));
        }
        
        future.complete(null);
        return future;
    }
    
    /**
     * 重启语言服务器
     * 
     * @param languageId 语言 ID
     * @return 服务器实例的 CompletableFuture
     */
    public CompletableFuture<LspServerInstance> restartServer(String languageId) {
        return stopServer(languageId)
                .thenCompose(v -> startServer(languageId));
    }
    
    /**
     * 获取服务器实例
     * 
     * @param languageId 语言 ID
     * @return 服务器实例
     */
    public LspServerInstance getServerInstance(String languageId) {
        return serverInstances.get(languageId);
    }
    
    /**
     * 获取所有运行的服务器
     * 
     * @return 服务器实例列表
     */
    public List<LspServerInstance> getRunningServers() {
        List<LspServerInstance> running = new ArrayList<>();
        for (LspServerInstance instance : serverInstances.values()) {
            if (instance.isRunning()) {
                running.add(instance);
            }
        }
        return running;
    }
    
    /**
     * 检查服务器是否运行
     * 
     * @param languageId 语言 ID
     * @return true 如果服务器正在运行
     */
    public boolean isServerRunning(String languageId) {
        LspServerInstance instance = serverInstances.get(languageId);
        return instance != null && instance.isRunning();
    }
    
    /**
     * 停止所有服务器
     * 
     * @return CompletableFuture
     */
    public CompletableFuture<Void> stopAllServers() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String languageId : serverInstances.keySet()) {
            futures.add(stopServer(languageId));
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    /**
     * 查找可用的服务器命令
     */
    private String findServerCommand(List<String> commands) {
        for (String command : commands) {
            if (isCommandAvailable(command)) {
                return command;
            }
        }
        return null;
    }
    
    /**
     * 检查命令是否可用
     */
    private boolean isCommandAvailable(String command) {
        // 检查 PATH 中是否存在命令
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                ProcessBuilder pb = new ProcessBuilder("where", command);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                return process.waitFor() == 0;
            } else {
                ProcessBuilder pb = new ProcessBuilder("which", command);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                return process.waitFor() == 0;
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 调度健康检查
     */
    private void scheduleHealthCheck(String languageId) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            LspServerInstance instance = serverInstances.get(languageId);
            if (instance != null) {
                boolean healthy = instance.isHealthy();
                if (!healthy) {
                    notifyStatusListeners(new ServerStatusEvent(languageId, ServerStatus.UNHEALTHY));
                }
            }
        }, HEALTH_CHECK_INTERVAL_MS, HEALTH_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 通知状态监听器
     */
    private void notifyStatusListeners(ServerStatusEvent event) {
        for (Consumer<ServerStatusEvent> listener : statusListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                // 忽略监听器异常
            }
        }
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 服务器状态枚举
     */
    public enum ServerStatus {
        STARTED,
        STOPPED,
        UNHEALTHY,
        RESTARTING
    }
    
    /**
     * 服务器状态事件类
     */
    public static class ServerStatusEvent {
        private final String languageId;
        private final ServerStatus status;
        
        public ServerStatusEvent(String languageId, ServerStatus status) {
            this.languageId = languageId;
            this.status = status;
        }
        
        public String getLanguageId() {
            return languageId;
        }
        
        public ServerStatus getStatus() {
            return status;
        }
    }
    
    /**
     * LSP 服务器配置类
     */
    public static class LspServerConfig {
        private final String languageId;
        private final String name;
        private final String preferredCommand;
        private final List<String> commands;
        private final Map<String, Object> settings;
        
        public LspServerConfig(String languageId, String name, String preferredCommand,
                               List<String> commands, Map<String, Object> settings) {
            this.languageId = languageId;
            this.name = name;
            this.preferredCommand = preferredCommand;
            this.commands = commands;
            this.settings = settings != null ? settings : new HashMap<>();
        }
        
        public String getLanguageId() {
            return languageId;
        }
        
        public String getName() {
            return name;
        }
        
        public String getPreferredCommand() {
            return preferredCommand;
        }
        
        public List<String> getCommands() {
            return commands;
        }
        
        public Map<String, Object> getSettings() {
            return settings;
        }
    }
    
    /**
     * LSP 服务器实例类
     */
    public static class LspServerInstance {
        private final String languageId;
        private final LspServerConfig config;
        private final String command;
        private final Process process;
        private final String workspaceRoot;
        private final Instant startTime;
        private volatile boolean running;
        
        public LspServerInstance(String languageId, LspServerConfig config, String command,
                                 Process process, String workspaceRoot) {
            this.languageId = languageId;
            this.config = config;
            this.command = command;
            this.process = process;
            this.workspaceRoot = workspaceRoot;
            this.startTime = Instant.now();
            this.running = true;
        }
        
        public String getLanguageId() {
            return languageId;
        }
        
        public LspServerConfig getConfig() {
            return config;
        }
        
        public String getCommand() {
            return command;
        }
        
        public Process getProcess() {
            return process;
        }
        
        public String getWorkspaceRoot() {
            return workspaceRoot;
        }
        
        public Instant getStartTime() {
            return startTime;
        }
        
        public boolean isRunning() {
            if (!running) {
                return false;
            }
            try {
                process.exitValue();
                running = false;
                return false;
            } catch (IllegalThreadStateException e) {
                return true;
            }
        }
        
        public boolean isHealthy() {
            return isRunning() && process.isAlive();
        }
        
        /**
         * 关闭服务器
         */
        public void shutdown() {
            running = false;
            process.destroyForcibly();
        }
    }
}
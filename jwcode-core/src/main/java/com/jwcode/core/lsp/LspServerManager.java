package com.jwcode.core.lsp;

import com.jwcode.core.lsp.impl.LspClientImpl;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * LspServerManager - LSP 服务器管理器
 * 
 * 功能说明：
 * 管理多个 LSP 服务器的生命周期，支持自动检测项目类型，
 * 为不同语言启动相应的语言服务器，并提供统一的配置管理。
 * 
 * 核心特性：
 * - 多语言服务器支持（Java, JavaScript, Python, Go, Rust 等）
 * - 自动检测项目类型（pom.xml→Java, package.json→JS 等）
 * - 服务器配置管理
 * - 启动/停止服务器进程
 * - 健康检查和自动恢复
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LspServerManager {
    
    private static final Logger LOGGER = Logger.getLogger(LspServerManager.class.getName());
    
    /**
     * 服务器实例映射表（语言 ID -> 服务器包装器）
     */
    private final Map<String, ServerWrapper> serverWrappers;
    
    /**
     * 服务器配置映射表
     */
    private final Map<String, LspServerConfig> serverConfigs;
    
    /**
     * 项目类型检测器
     */
    private final List<ProjectTypeDetector> projectDetectors;
    
    /**
     * 状态监听器
     */
    private final List<Consumer<ServerStatusEvent>> statusListeners;
    
    /**
     * 诊断注册表
     */
    private final LspDiagnosticRegistry diagnosticRegistry;
    
    /**
     * 调度器
     */
    private final ScheduledExecutorService scheduler;
    
    /**
     * 工作区根目录
     */
    private String workspaceRoot;
    
    /**
     * 启动超时
     */
    private static final int STARTUP_TIMEOUT_SECONDS = 60;
    
    /**
     * 健康检查间隔
     */
    private static final long HEALTH_CHECK_INTERVAL_MS = 30000;
    
    /**
     * 服务器包装器（包含实例和客户端）
     */
    public static class ServerWrapper {
        private final LspServerInstance instance;
        private final LspClientImpl client;
        private final String languageId;
        private volatile boolean initialized;
        
        public ServerWrapper(String languageId, LspServerInstance instance, LspClientImpl client) {
            this.languageId = languageId;
            this.instance = instance;
            this.client = client;
            this.initialized = false;
        }
        
        public LspServerInstance getInstance() { return instance; }
        public LspClientImpl getClient() { return client; }
        public String getLanguageId() { return languageId; }
        public boolean isInitialized() { return initialized; }
        public void setInitialized(boolean initialized) { this.initialized = initialized; }
        
        public boolean isRunning() {
            return instance != null && instance.isRunning() && 
                   client != null && client.isConnected();
        }
    }
    
    /**
     * 项目类型枚举
     */
    public enum ProjectType {
        JAVA("java", List.of("pom.xml", "build.gradle", "build.gradle.kts", ".java-version")),
        JAVASCRIPT("javascript", List.of("package.json", "tsconfig.json")),
        TYPESCRIPT("typescript", List.of("package.json", "tsconfig.json")),
        PYTHON("python", List.of("requirements.txt", "setup.py", "pyproject.toml", "Pipfile", ".python-version")),
        RUST("rust", List.of("Cargo.toml")),
        GO("go", List.of("go.mod")),
        C_CPP("c", List.of("CMakeLists.txt", "Makefile", ".clangd")),
        CSHARP("csharp", List.of("*.csproj", "*.sln")),
        RUBY("ruby", List.of("Gemfile", "*.gemspec")),
        PHP("php", List.of("composer.json")),
        SWIFT("swift", List.of("Package.swift")),
        KOTLIN("kotlin", List.of("build.gradle.kts")),
        SCALA("scala", List.of("build.sbt")),
        UNKNOWN("", List.of());
        
        private final String languageId;
        private final List<String> indicators;
        
        ProjectType(String languageId, List<String> indicators) {
            this.languageId = languageId;
            this.indicators = indicators;
        }
        
        public String getLanguageId() { return languageId; }
        public List<String> getIndicators() { return indicators; }
        
        public static ProjectType fromLanguageId(String languageId) {
            for (ProjectType type : values()) {
                if (type.languageId.equals(languageId)) {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }
    
    /**
     * 项目类型检测器
     */
    @FunctionalInterface
    public interface ProjectTypeDetector {
        ProjectType detect(Path projectRoot);
    }
    
    /**
     * 服务器状态事件
     */
    public static class ServerStatusEvent {
        private final String languageId;
        private final ServerStatus status;
        private final String message;
        private final Instant timestamp;
        
        public ServerStatusEvent(String languageId, ServerStatus status) {
            this(languageId, status, null);
        }
        
        public ServerStatusEvent(String languageId, ServerStatus status, String message) {
            this.languageId = languageId;
            this.status = status;
            this.message = message;
            this.timestamp = Instant.now();
        }
        
        public String getLanguageId() { return languageId; }
        public ServerStatus getStatus() { return status; }
        public String getMessage() { return message; }
        public Instant getTimestamp() { return timestamp; }
    }
    
    /**
     * 服务器状态枚举
     */
    public enum ServerStatus {
        STARTING,
        STARTED,
        INITIALIZED,
        STOPPED,
        ERROR,
        UNHEALTHY,
        RESTARTING
    }
    
    /**
     * LSP 服务器配置
     */
    public static class LspServerConfig {
        private final String languageId;
        private final String name;
        private final String preferredCommand;
        private final List<String> commands;
        private final Map<String, Object> settings;
        private final List<String> fileExtensions;
        private final Map<String, String> environment;
        
        public LspServerConfig(String languageId, String name, String preferredCommand,
                               List<String> commands, Map<String, Object> settings) {
            this(languageId, name, preferredCommand, commands, settings, 
                 List.of(), new HashMap<>());
        }
        
        public LspServerConfig(String languageId, String name, String preferredCommand,
                               List<String> commands, Map<String, Object> settings,
                               List<String> fileExtensions, Map<String, String> environment) {
            this.languageId = languageId;
            this.name = name;
            this.preferredCommand = preferredCommand;
            this.commands = commands != null ? commands : new ArrayList<>();
            this.settings = settings != null ? settings : new HashMap<>();
            this.fileExtensions = fileExtensions != null ? fileExtensions : new ArrayList<>();
            this.environment = environment != null ? environment : new HashMap<>();
        }
        
        public String getLanguageId() { return languageId; }
        public String getName() { return name; }
        public String getPreferredCommand() { return preferredCommand; }
        public List<String> getCommands() { return commands; }
        public Map<String, Object> getSettings() { return settings; }
        public List<String> getFileExtensions() { return fileExtensions; }
        public Map<String, String> getEnvironment() { return environment; }
    }
    
    /**
     * LSP 服务器实例
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
        
        public String getLanguageId() { return languageId; }
        public LspServerConfig getConfig() { return config; }
        public String getCommand() { return command; }
        public Process getProcess() { return process; }
        public String getWorkspaceRoot() { return workspaceRoot; }
        public Instant getStartTime() { return startTime; }
        
        public boolean isRunning() {
            if (!running) return false;
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
        
        public Duration getUptime() {
            return Duration.between(startTime, Instant.now());
        }
        
        public void shutdown() {
            running = false;
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
    
    public LspServerManager() {
        this(System.getProperty("user.dir"));
    }
    
    public LspServerManager(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
        this.serverWrappers = new ConcurrentHashMap<>();
        this.serverConfigs = new ConcurrentHashMap<>();
        this.statusListeners = new CopyOnWriteArrayList<>();
        this.diagnosticRegistry = new LspDiagnosticRegistry();
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "LspServerManager-Scheduler");
            t.setDaemon(true);
            return t;
        });
        
        // 注册项目类型检测器
        this.projectDetectors = new ArrayList<>();
        registerDefaultProjectDetectors();
        
        // 注册默认配置
        registerDefaultConfigs();
        
        // 启动健康检查
        startHealthCheck();
    }
    
    /**
     * 注册默认项目类型检测器
     */
    private void registerDefaultProjectDetectors() {
        // 基于文件存在的检测
        projectDetectors.add(projectRoot -> {
            for (ProjectType type : ProjectType.values()) {
                if (type == ProjectType.UNKNOWN) continue;
                
                for (String indicator : type.getIndicators()) {
                    Path path = projectRoot.resolve(indicator);
                    if (Files.exists(path)) {
                        return type;
                    }
                }
            }
            return ProjectType.UNKNOWN;
        });
        
        // 基于文件扩展名的检测
        projectDetectors.add(projectRoot -> {
            try (Stream<Path> paths = Files.walk(projectRoot, 2)) {
                Map<String, Long> extensionCounts = paths
                    .filter(Files::isRegularFile)
                    .map(p -> {
                        String name = p.getFileName().toString();
                        int dot = name.lastIndexOf('.');
                        return dot > 0 ? name.substring(dot + 1).toLowerCase() : "";
                    })
                    .filter(ext -> !ext.isEmpty())
                    .collect(Collectors.groupingBy(ext -> ext, Collectors.counting()));
                
                // 根据最常见的扩展名推断项目类型
                if (extensionCounts.getOrDefault("java", 0L) > 0) return ProjectType.JAVA;
                if (extensionCounts.getOrDefault("py", 0L) > 0) return ProjectType.PYTHON;
                if (extensionCounts.getOrDefault("rs", 0L) > 0) return ProjectType.RUST;
                if (extensionCounts.getOrDefault("go", 0L) > 0) return ProjectType.GO;
                if (extensionCounts.getOrDefault("ts", 0L) > 0) return ProjectType.TYPESCRIPT;
                if (extensionCounts.getOrDefault("js", 0L) > 0) return ProjectType.JAVASCRIPT;
                if (extensionCounts.getOrDefault("swift", 0L) > 0) return ProjectType.SWIFT;
                if (extensionCounts.getOrDefault("kt", 0L) > 0) return ProjectType.KOTLIN;
                if (extensionCounts.getOrDefault("scala", 0L) > 0) return ProjectType.SCALA;
                
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error detecting project type", e);
            }
            return ProjectType.UNKNOWN;
        });
    }
    
    /**
     * 注册默认服务器配置
     */
    private void registerDefaultConfigs() {
        // Java - Eclipse JDT Language Server
        registerConfig(new LspServerConfig(
            "java",
            "Eclipse JDT Language Server",
            "jdtls",
            Arrays.asList("jdtls", "java-language-server"),
            Map.of("java.format.enabled", true, "java.import.maven.enabled", true),
            Arrays.asList(".java", "jav", "java"),
            new HashMap<>()
        ));
        
        // TypeScript/JavaScript
        registerConfig(new LspServerConfig(
            "typescript",
            "TypeScript Language Server",
            "typescript-language-server",
            Arrays.asList("typescript-language-server", "tsserver"),
            Map.of("typescript.format.enable", true),
            Arrays.asList(".ts", ".tsx", ".js", ".jsx"),
            new HashMap<>()
        ));
        
        // Python
        registerConfig(new LspServerConfig(
            "python",
            "Python Language Server",
            "pylsp",
            Arrays.asList("pylsp", "pyright-langserver", "jedi-language-server", "pyls"),
            Map.of("pylsp.plugins.pycodestyle.enabled", true),
            Arrays.asList(".py"),
            new HashMap<>()
        ));
        
        // Rust
        registerConfig(new LspServerConfig(
            "rust",
            "Rust Analyzer",
            "rust-analyzer",
            Arrays.asList("rust-analyzer"),
            Map.of("rust-analyzer.cargo.autoreload", true),
            Arrays.asList(".rs"),
            new HashMap<>()
        ));
        
        // Go
        registerConfig(new LspServerConfig(
            "go",
            "Go Language Server",
            "gopls",
            Arrays.asList("gopls"),
            Map.of("ui.diagnostic.annotations.bounds", true),
            Arrays.asList(".go"),
            new HashMap<>()
        ));
        
        // C/C++
        registerConfig(new LspServerConfig(
            "c",
            "clangd",
            "clangd",
            Arrays.asList("clangd"),
            Map.of("clangd.compile-commands-dir", "build"),
            Arrays.asList(".c", ".cpp", ".cc", ".h", ".hpp"),
            new HashMap<>()
        ));
        
        // C# - OmniSharp
        registerConfig(new LspServerConfig(
            "csharp",
            "OmniSharp",
            "omnisharp",
            Arrays.asList("omnisharp", "OmniSharp"),
            Map.of("omnisharp.enableRoslynAnalyzers", true),
            Arrays.asList(".cs"),
            new HashMap<>()
        ));
        
        // Ruby
        registerConfig(new LspServerConfig(
            "ruby",
            "Ruby LSP",
            "ruby-lsp",
            Arrays.asList("ruby-lsp", "solargraph", "srb"),
            Map.of(),
            Arrays.asList(".rb"),
            new HashMap<>()
        ));
        
        // PHP
        registerConfig(new LspServerConfig(
            "php",
            "PHP Language Server",
            "php-language-server",
            Arrays.asList("php-language-server", "intelephense"),
            Map.of(),
            Arrays.asList(".php"),
            new HashMap<>()
        ));
        
        // Swift
        registerConfig(new LspServerConfig(
            "swift",
            "SourceKit-LSP",
            "sourcekit-lsp",
            Arrays.asList("sourcekit-lsp"),
            Map.of(),
            Arrays.asList(".swift"),
            new HashMap<>()
        ));
    }
    
    /**
     * 自动检测项目类型
     */
    public ProjectType detectProjectType() {
        return detectProjectType(workspaceRoot);
    }
    
    public ProjectType detectProjectType(String projectPath) {
        Path root = Paths.get(projectPath);
        if (!Files.exists(root)) {
            return ProjectType.UNKNOWN;
        }
        
        for (ProjectTypeDetector detector : projectDetectors) {
            ProjectType type = detector.detect(root);
            if (type != ProjectType.UNKNOWN) {
                LOGGER.info("Detected project type: " + type + " for " + projectPath);
                return type;
            }
        }
        
        return ProjectType.UNKNOWN;
    }
    
    /**
     * 为检测到的项目类型自动启动服务器
     */
    public CompletableFuture<ServerWrapper> autoStartForProject() {
        ProjectType projectType = detectProjectType();
        if (projectType == ProjectType.UNKNOWN) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Could not detect project type"));
        }
        return startServer(projectType.getLanguageId());
    }
    
    /**
     * 注册服务器配置
     */
    public void registerConfig(LspServerConfig config) {
        serverConfigs.put(config.getLanguageId(), config);
        LOGGER.fine("Registered LSP config for: " + config.getLanguageId());
    }
    
    /**
     * 获取服务器配置
     */
    public LspServerConfig getConfig(String languageId) {
        return serverConfigs.get(languageId);
    }
    
    /**
     * 获取所有配置
     */
    public List<LspServerConfig> getAllConfigs() {
        return new ArrayList<>(serverConfigs.values());
    }
    
    /**
     * 添加状态监听器
     */
    public void addStatusListener(Consumer<ServerStatusEvent> listener) {
        statusListeners.add(listener);
    }
    
    /**
     * 移除状态监听器
     */
    public void removeStatusListener(Consumer<ServerStatusEvent> listener) {
        statusListeners.remove(listener);
    }
    
    /**
     * 启动语言服务器
     */
    public CompletableFuture<ServerWrapper> startServer(String languageId) {
        return startServer(languageId, workspaceRoot);
    }
    
    /**
     * 启动语言服务器（指定工作目录）
     */
    public CompletableFuture<ServerWrapper> startServer(String languageId, String workspaceRoot) {
        CompletableFuture<ServerWrapper> future = new CompletableFuture<>();
        
        LspServerConfig config = serverConfigs.get(languageId);
        if (config == null) {
            future.completeExceptionally(new IllegalArgumentException(
                "Unknown language ID: " + languageId));
            return future;
        }
        
        // 检查是否已运行
        ServerWrapper existing = serverWrappers.get(languageId);
        if (existing != null && existing.isRunning()) {
            future.complete(existing);
            return future;
        }
        
        notifyStatusListeners(new ServerStatusEvent(languageId, ServerStatus.STARTING));
        
        // 查找可用的服务器命令
        String command = findServerCommand(config.getCommands());
        if (command == null) {
            notifyStatusListeners(new ServerStatusEvent(languageId, ServerStatus.ERROR,
                "Language server not found: " + config.getName()));
            future.completeExceptionally(new IllegalStateException(
                "Language server not found: " + config.getName() + 
                ". Please install: " + String.join(", ", config.getCommands())));
            return future;
        }
        
        try {
            // 构建进程
            ProcessBuilder pb = new ProcessBuilder(parseCommand(command));
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            
            if (workspaceRoot != null) {
                pb.directory(Paths.get(workspaceRoot).toFile());
            }
            
            // 设置环境变量
            Map<String, String> env = pb.environment();
            env.putAll(config.getEnvironment());
            
            // 启动进程
            Process process = pb.start();
            
            // 创建服务器实例
            LspServerInstance instance = new LspServerInstance(
                languageId, config, command, process, workspaceRoot);
            
            // 创建 LSP 客户端
            LspClientImpl client = new LspClientImpl(
                languageId, instance, diagnosticRegistry);
            
            // 创建包装器
            ServerWrapper wrapper = new ServerWrapper(languageId, instance, client);
            serverWrappers.put(languageId, wrapper);
            
            // 连接并初始化
            client.connect()
                .thenCompose(v -> client.initialize(workspaceRoot))
                .thenAccept(result -> {
                    wrapper.setInitialized(true);
                    notifyStatusListeners(new ServerStatusEvent(languageId, 
                        ServerStatus.INITIALIZED));
                    future.complete(wrapper);
                })
                .exceptionally(ex -> {
                    LOGGER.log(Level.SEVERE, "Failed to initialize LSP client", ex);
                    notifyStatusListeners(new ServerStatusEvent(languageId, 
                        ServerStatus.ERROR, ex.getMessage()));
                    cleanupServer(languageId);
                    future.completeExceptionally(ex);
                    return null;
                });
            
            notifyStatusListeners(new ServerStatusEvent(languageId, ServerStatus.STARTED));
            
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to start language server", e);
            notifyStatusListeners(new ServerStatusEvent(languageId, ServerStatus.ERROR, 
                e.getMessage()));
            future.completeExceptionally(new RuntimeException(
                "Failed to start language server: " + e.getMessage(), e));
        }
        
        return future;
    }
    
    /**
     * 停止语言服务器
     */
    public CompletableFuture<Void> stopServer(String languageId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        ServerWrapper wrapper = serverWrappers.remove(languageId);
        if (wrapper != null) {
            notifyStatusListeners(new ServerStatusEvent(languageId, ServerStatus.STOPPED));
            
            // 断开客户端连接
            if (wrapper.getClient() != null) {
                wrapper.getClient().disconnect()
                    .thenRun(() -> {
                        // 终止进程
                        if (wrapper.getInstance() != null) {
                            wrapper.getInstance().shutdown();
                        }
                        future.complete(null);
                    })
                    .exceptionally(ex -> {
                        LOGGER.log(Level.WARNING, "Error disconnecting client", ex);
                        wrapper.getInstance().shutdown();
                        future.complete(null);
                        return null;
                    });
            } else {
                wrapper.getInstance().shutdown();
                future.complete(null);
            }
        } else {
            future.complete(null);
        }
        
        return future;
    }
    
    /**
     * 重启语言服务器
     */
    public CompletableFuture<ServerWrapper> restartServer(String languageId) {
        notifyStatusListeners(new ServerStatusEvent(languageId, ServerStatus.RESTARTING));
        return stopServer(languageId)
            .thenCompose(v -> startServer(languageId));
    }
    
    /**
     * 获取服务器客户端
     */
    public LspService getServerClient(String languageId) {
        ServerWrapper wrapper = serverWrappers.get(languageId);
        return wrapper != null ? wrapper.getClient() : null;
    }
    
    /**
     * 获取服务器包装器
     */
    public ServerWrapper getServerWrapper(String languageId) {
        return serverWrappers.get(languageId);
    }
    
    /**
     * 获取所有运行的服务器
     */
    public List<ServerWrapper> getRunningServers() {
        return serverWrappers.values().stream()
            .filter(ServerWrapper::isRunning)
            .collect(Collectors.toList());
    }
    
    /**
     * 检查服务器是否运行
     */
    public boolean isServerRunning(String languageId) {
        ServerWrapper wrapper = serverWrappers.get(languageId);
        return wrapper != null && wrapper.isRunning();
    }
    
    /**
     * 停止所有服务器
     */
    public CompletableFuture<Void> stopAllServers() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String languageId : serverWrappers.keySet()) {
            futures.add(stopServer(languageId));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    /**
     * 获取诊断注册表
     */
    public LspDiagnosticRegistry getDiagnosticRegistry() {
        return diagnosticRegistry;
    }
    
    /**
     * 设置工作区根目录
     */
    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }
    
    /**
     * 获取工作区根目录
     */
    public String getWorkspaceRoot() {
        return workspaceRoot;
    }
    
    /**
     * 清理服务器资源
     */
    private void cleanupServer(String languageId) {
        ServerWrapper wrapper = serverWrappers.remove(languageId);
        if (wrapper != null && wrapper.getInstance() != null) {
            wrapper.getInstance().shutdown();
        }
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
        String cmd = command.split(" ")[0]; // 处理带参数的命令
        String os = System.getProperty("os.name").toLowerCase();
        try {
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("where", cmd);
            } else {
                pb = new ProcessBuilder("which", cmd);
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 解析命令字符串
     */
    private List<String> parseCommand(String command) {
        // 简单解析，支持带空格的命令
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (char c : command.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        
        return parts;
    }
    
    /**
     * 启动健康检查
     */
    private void startHealthCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            for (Map.Entry<String, ServerWrapper> entry : serverWrappers.entrySet()) {
                String languageId = entry.getKey();
                ServerWrapper wrapper = entry.getValue();
                
                if (!wrapper.isRunning()) {
                    LOGGER.warning("Server " + languageId + " is not running, marking as unhealthy");
                    notifyStatusListeners(new ServerStatusEvent(languageId, 
                        ServerStatus.UNHEALTHY, "Server process terminated"));
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
                LOGGER.log(Level.WARNING, "Status listener error", e);
            }
        }
    }
    
    /**
     * 关闭管理器
     */
    public void shutdown() {
        stopAllServers().join();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

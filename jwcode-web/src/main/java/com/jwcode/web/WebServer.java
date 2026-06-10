package com.jwcode.web;

import com.jwcode.core.config.ConfigInitializer;
import com.jwcode.core.config.ConfigManager;
import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.config.YamlConfigLoader;
import com.jwcode.core.index.CodebaseIndexer;
import com.jwcode.core.index.EmbeddingService;
import com.jwcode.core.index.IndexConfig;
import com.jwcode.core.plugin.PluginManager;
import com.jwcode.core.task.TaskStore;
import com.jwcode.core.tool.ToolRegistry;
import com.jwcode.core.api.AgentFlowBroadcaster;
import com.jwcode.core.api.PlanTaskBroadcaster;
import com.jwcode.web.stream.StreamingWebSocketHandler;
import com.jwcode.web.terminal.TerminalHandler;
import com.jwcode.web.terminal.TerminalSession;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Web UI 服务器 - 提供浏览器界面
 * 
 * 参照 Kimi Code 的 web 界面
 * 新增 WebSocket 流式响应支持
 */
public class WebServer {
    
    private static final Logger logger = Logger.getLogger(WebServer.class.getName());
    
    private HttpServer server;
    private StreamingWebSocketHandler webSocketHandler;
    private TerminalHandler terminalHandler;
    private com.jwcode.core.tool.shell.DockerSandboxExecutor sandboxExecutor;
    private final int port;
    private final int wsPort;
    private final WebSessionManager sessionManager;
    private final ToolRegistry toolRegistry;
    private final String workspaceDir;
    private CodebaseIndexer codebaseIndexer;

    public WebServer(int port, int wsPort, ToolRegistry toolRegistry, String workspaceDir) {
        this.port = port;
        this.wsPort = wsPort;
        this.sessionManager = new WebSessionManager();
        this.toolRegistry = toolRegistry;
        this.workspaceDir = workspaceDir != null ? workspaceDir : System.getProperty("user.dir");
    }

    public WebServer(int port, int wsPort, ToolRegistry toolRegistry) {
        this(port, wsPort, toolRegistry, null);
    }

    public WebServer(int port, ToolRegistry toolRegistry) {
        this(port, port + 1, toolRegistry, null);
    }

    public WebServer(int port) {
        this(port, port + 1, ToolRegistry.createDefault(), null);
    }

    public WebServer() {
        this(8080, 8081, ToolRegistry.createDefault(), null);
    }
    
    /**
     * 启动服务器
     */
    public void start() throws IOException {
        // Initialize ~/.jwcode/ config templates on first run
        new ConfigInitializer().initialize();

        // 初始化安全沙箱 — Docker 容器隔离，不可用时降级 WorkspaceGuard
        try {
            sandboxExecutor = new com.jwcode.core.tool.shell.DockerSandboxExecutor(
                java.nio.file.Path.of(this.workspaceDir));
            com.jwcode.core.tool.BashTool.setBackgroundExecutor(sandboxExecutor);
            System.out.println("[WebServer] Docker sandbox initialized");
        } catch (Exception e) {
            System.err.println("[WebServer] Sandbox init failed (non-fatal): " + e.getMessage());
        }

        // 初始化插件系统 — 发现并加载 ~/.jwcode/plugins/ 和 ./.jwcode/plugins/ 中的插件
        try {
            int pluginCount = PluginManager.getInstance().discoverAndLoadAll();
            if (pluginCount > 0) {
                toolRegistry.registerPluginTools(PluginManager.getInstance());
                System.out.println("[WebServer] Plugin system initialized: " + pluginCount + " plugins loaded");
            }
        } catch (Exception e) {
            System.err.println("[WebServer] Plugin init failed (non-fatal): " + e.getMessage());
        }

        // 启动 HTTP 服务器
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // 注册路由
        Path distDir = java.nio.file.Paths.get(this.workspaceDir, "jwcode-web", "dist").toAbsolutePath().normalize();

        // 先注册静态资源 handler（assets），再注册兜底的 index handler
        server.createContext("/assets/", new StaticAssetHandler(distDir));
        server.createContext("/", new IndexHandler(distDir));
        server.createContext("/api/chat", new ChatHandler(sessionManager));
        server.createContext("/api/sessions", new SessionsHandler(sessionManager));
        server.createContext("/api/tools", new ToolsHandler(toolRegistry));
        server.createContext("/api/config", new ConfigHandler());
        server.createContext("/api/config/files", new ConfigFilesHandler());
        server.createContext("/api/skills", new SkillsHandler());
        server.createContext("/api/agents", new AgentsHandler());
        server.createContext("/api/templates", new TemplatesHandler());
        server.createContext("/api/models", new ModelInfoHandler());
        // 任务管理 API
        server.createContext("/api/tasks", new TaskHandler(TaskStore.getInstance()));
        server.createContext("/api/files", new FilesHandler());
        server.createContext("/api/system/status", new SystemStatusHandler());
        server.createContext("/api/checkpoints", new CheckpointsHandler());
        server.createContext("/api/observability", new ObservabilityHandler());
        MetricsHandler metricsHandler = new MetricsHandler();
        server.createContext("/api/metrics", metricsHandler);
        System.out.println("  ✓ Metrics API: /api/metrics (Prometheus + JSON)");

        // Logs API — browse and download server log files
        server.createContext("/api/logs", new LogsHandler());

        // Terminal — ttyd sidecar for web-based terminal access
        {
            String tsCliPath = java.nio.file.Paths.get(
                this.workspaceDir, "ts-cli", "dist", "cli.js"
            ).toAbsolutePath().normalize().toString();

            String ttydPath = TerminalSession.findTtyd();
            if (ttydPath == null) {
                logger.warning("ttyd not found in PATH — terminal tab will be unavailable");
                System.out.println("  ⚠ ttyd not found: install from https://github.com/tsl0922/ttyd/releases");
            } else {
                System.out.println("  ✓ ttyd found: " + ttydPath);
                System.out.println("  ✓ ts-cli path: " + tsCliPath);
            }

            terminalHandler = new TerminalHandler(ttydPath, tsCliPath);
            server.createContext("/api/terminal", terminalHandler);
        }

        // Hooks config management API
        {
            com.jwcode.core.hook.HookRegistry hookRegistry =
                com.jwcode.core.hook.HookSystemInitializer.getRegistry();
            if (hookRegistry == null) {
                // Fallback: create standalone registry if HookSystemInitializer not yet run
                java.nio.file.Path hooksFile = java.nio.file.Path.of(this.workspaceDir,
                    ".jwcode", "hooks.json");
                hookRegistry = new com.jwcode.core.hook.HookRegistry(hooksFile);
                logger.info("[WebServer] Created standalone HookRegistry for HooksHandler");
            }
            com.jwcode.core.agent.AgentRegistry agentRegistry =
                new com.jwcode.core.agent.AgentRegistry(toolRegistry);
            server.createContext("/api/hooks", new HooksHandler(hookRegistry, agentRegistry,
                java.nio.file.Path.of(this.workspaceDir, ".jwcode", "hooks.json")));
            System.out.println("  ✓ Hook config API: /api/hooks");
        }
        
        // 使用 ThreadPoolExecutor 替代 newFixedThreadPool，支持有界队列和拒绝策略
        server.setExecutor(new ThreadPoolExecutor(
            4, 10, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),
            new ThreadFactory() {
                private int count = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "http-worker-" + count++);
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        ));
        server.start();
        
        // 初始化代码库语义搜索索引（CodebaseIndexer + SemanticSearchTool）
        initializeCodebaseIndexer();
        
        // 启动 WebSocket 服务器（流式响应）
        webSocketHandler = new StreamingWebSocketHandler(wsPort, toolRegistry);
        webSocketHandler.setCodebaseIndexer(codebaseIndexer);
        webSocketHandler.setSessionManager(sessionManager);
        webSocketHandler.start();

        // 将 PlanTaskBroadcaster 接入主 WebSocket，使 plan_* 消息通过当前连接发送
        PlanTaskBroadcaster.setMessageSender((type, sid, data) ->
            webSocketHandler.sendMessage(sid,
                StreamingWebSocketHandler.MessageType.valueOf(type.toUpperCase()), data));

        // 将 AgentFlowBroadcaster 接入主 WebSocket，使 agent_flow_event 消息通过当前连接发送
        AgentFlowBroadcaster.setMessageSender((type, sid, data) ->
            webSocketHandler.sendMessage(sid,
                StreamingWebSocketHandler.MessageType.valueOf(type.toUpperCase()), data));
        
        logger.info("Web UI 服务器启动: http://localhost:" + port);
        logger.info("WebSocket 服务器启动: ws://localhost:" + wsPort);
        System.out.println("🌐 Web UI 已启动: http://localhost:" + port);
        System.out.println("📡 WebSocket 端口: " + wsPort);
    }
    
    /**
     * 停止服务器
     */
    public void stop() {
        // 1. 关闭代码库索引器（包括 indexExecutor, debounceScheduler, watcher）
        if (codebaseIndexer != null) {
            codebaseIndexer.shutdown();
        }

        // 2. 关闭 WebSocket 服务器（包括 heartbeatScheduler, queryExecutor）
        if (webSocketHandler != null) {
            try {
                webSocketHandler.shutdown();
            } catch (Exception e) {
                logger.warning("WebSocket shutdown error: " + e.getMessage());
            }
        }

        // 3. 关闭终端处理器
        if (terminalHandler != null) {
            terminalHandler.shutdown();
        }

        // 4. 关闭 Docker 沙箱执行器的线程池
        if (sandboxExecutor != null) {
            sandboxExecutor.shutdown();
        }

        // 5. 停止 HTTP 服务器（包括 http-worker 线程池）
        if (server != null) {
            server.stop(0);
            logger.info("Web UI 服务器已停止");
        }
    }

    /**
     * Restart the server in-process, reloading all config from disk.
     */
    public synchronized void restart() throws IOException {
        logger.info("Restarting server...");
        System.out.println("\n🔄 Restarting JWCode server...\n");

        stop();

        // Force config singletons to reload from disk
        YamlConfigLoader.resetInstance();
        ConfigManager.resetInstance();

        start();

        logger.info("Server restarted successfully");
        System.out.println("✅ Server restarted successfully\n");
    }

    /**
     * 初始化代码库语义搜索索引 — CodebaseIndexer + SemanticSearchTool。
     *
     * <p>优先从 YAML 配置读取 provider 创建 EmbeddingService（使用模型池），
     * 未配置 API key 时自动降级为本地 TF-IDF 风格 fallback embedding。</p>
     */
    private void initializeCodebaseIndexer() {
        try {
            java.nio.file.Path workspaceRoot = java.nio.file.Path.of(this.workspaceDir).toAbsolutePath().normalize();
            IndexConfig indexConfig = IndexConfig.forWorkspace(workspaceRoot);

            // 从 YAML 配置读取 provider，构建 EmbeddingService（模型池模式）
            EmbeddingService embeddingService = createEmbeddingServiceFromConfig();

            this.codebaseIndexer = new CodebaseIndexer(workspaceRoot, indexConfig, embeddingService);

            // 注册 SemanticSearchTool，使子 Agent 可用自然语言搜索代码库
            toolRegistry.registerSemanticSearch(codebaseIndexer);
            logger.info("SemanticSearchTool registered — 子 Agent 可自然语言搜索代码库");

            // 异步启动初始全量索引（后台执行，不阻塞启动）
            codebaseIndexer.reindexAsync()
                .thenAccept(fileCount -> {
                    if (fileCount > 0) {
                        logger.info("代码库索引完成: " + fileCount + " 个文件, "
                            + codebaseIndexer.getVectorCount() + " 个向量块");
                    } else {
                        logger.info("代码库索引完成（无新增文件）");
                    }
                })
                .exceptionally(e -> {
                    logger.warning("代码库索引失败: " + e.getMessage());
                    return null;
                });

            // 启动文件监控（增量索引）
            codebaseIndexer.startWatching();

            System.out.println("🔍 语义搜索: 已启用（后台索引中...）");
        } catch (Exception e) {
            logger.warning("代码库索引初始化失败（语义搜索不可用）: " + e.getMessage());
            this.codebaseIndexer = null;
        }
    }

    /**
     * 从 YAML 配置创建 EmbeddingService。
     *
     * <p>优先级：</p>
     * <ol>
     *   <li>YAML 配置中有 provider 且配置了 api-keys → 使用模型池模式</li>
     *   <li>环境变量 OPENAI_API_KEY → 使用单 key 模式</li>
     *   <li>均未配置 → 本地 fallback</li>
     * </ol>
     */
    private EmbeddingService createEmbeddingServiceFromConfig() {
        // 1. 尝试从 YAML 配置读取 provider
        try {
            YamlConfigLoader configLoader = YamlConfigLoader.getInstance();
            JwcodeConfig config = configLoader.getConfig();
            JwcodeConfig.ProviderConfig provider = config.getDefaultProvider();

            if (provider != null
                && provider.getApiKeys() != null
                && !provider.getApiKeys().isEmpty()
                && provider.getBaseUrl() != null
                && !provider.getBaseUrl().isBlank()) {

                // 检查 provider 是否配置了 embedding 模型
                boolean hasEmbeddingModel = false;
                if (provider.getModels() != null) {
                    for (JwcodeConfig.ModelDefinition model : provider.getModels()) {
                        if (model.getId() != null && model.getId().toLowerCase().contains("embedding")) {
                            hasEmbeddingModel = true;
                            break;
                        }
                    }
                }

                if (hasEmbeddingModel) {
                    String endpoint = provider.getBaseUrl() + "/v1/embeddings";
                    int dimension = config.getSettings() != null
                        && config.getSettings().getSearch() != null
                        && config.getSettings().getSearch().getEmbeddingDimension() > 0
                        ? config.getSettings().getSearch().getEmbeddingDimension() : 256;

                    String embeddingModel = "text-embedding-ada-002";
                    for (JwcodeConfig.ModelDefinition model : provider.getModels()) {
                        if (model.getId() != null && model.getId().toLowerCase().contains("embedding")) {
                            embeddingModel = model.getId();
                            break;
                        }
                    }

                    logger.info("使用 YAML 配置的 provider 创建 EmbeddingService: "
                        + "provider=" + (config.getDefaultProviderName() != null ? config.getDefaultProviderName() : "default")
                        + ", endpoint=" + endpoint
                        + ", model=" + embeddingModel
                        + ", keys=" + provider.getApiKeys().size());

                    return new EmbeddingService(provider, endpoint, embeddingModel, dimension);
                }

                // provider 没有配置 embedding 模型（如 DeepSeek），跳过 API 模式
                logger.info("Provider 未配置 embedding 模型，跳过 API 调用，使用本地 fallback");
            }
        } catch (Exception e) {
            logger.warning("从 YAML 配置创建 EmbeddingService 失败: " + e.getMessage()
                + "，将尝试其他方式");
        }

        // 2. 尝试从环境变量读取
        String envKey = System.getenv("OPENAI_API_KEY");
        String envEndpoint = System.getenv("OPENAI_API_ENDPOINT");
        if (envKey != null && !envKey.isBlank()) {
            String endpoint = (envEndpoint != null && !envEndpoint.isBlank())
                ? envEndpoint : "https://api.openai.com/v1/embeddings";
            logger.info("使用环境变量 OPENAI_API_KEY 创建 EmbeddingService");
            return new EmbeddingService(endpoint, envKey, "text-embedding-ada-002", 256);
        }

        // 3. 均未配置，使用本地 fallback
        logger.info("未检测到 API key 配置，使用本地 fallback embedding（TF-IDF 风格）");
        return EmbeddingService.createLocalFallback(256);
    }

    /**
     * 首页处理器 - 优先从本地 dist 目录加载，fallback 到 classpath
     */
    class IndexHandler implements HttpHandler {

        private final Path distDir;
        private final boolean distAvailable;

        IndexHandler(Path distDir) {
            this.distDir = distDir;
            this.distAvailable = Files.exists(distDir.resolve("index.html"));
            if (distAvailable) {
                logger.info("[IndexHandler] Local dist detected: " + distDir);
            }
        }

        private String cachedHtml = null;
        private long cachedHtmlTime = 0;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = getHtmlContent();
            sendResponse(exchange, 200, html, "text/html");
        }

        private synchronized String getHtmlContent() throws IOException {
            if (distAvailable) {
                Path htmlFile = distDir.resolve("index.html");
                if (Files.exists(htmlFile)) {
                    long lastMod = Files.getLastModifiedTime(htmlFile).toMillis();
                    if (cachedHtml == null || lastMod != cachedHtmlTime) {
                        cachedHtml = Files.readString(htmlFile, StandardCharsets.UTF_8);
                        cachedHtmlTime = lastMod;
                        logger.fine("[IndexHandler] Reloaded from dist: " + htmlFile);
                    }
                    return cachedHtml;
                }
            }
            if (cachedHtml == null) {
                var resourceUrl = getClass().getResource("/web/index.html");
                logger.info("[IndexHandler] Loading HTML from: " + resourceUrl);
                try (var inputStream = resourceUrl != null ? resourceUrl.openStream() : null) {
                    if (inputStream == null) {
                        logger.warning("无法找到 HTML 资源文件 /web/index.html");
                        return "<html><body><h1>Error: HTML resource not found</h1></body></html>";
                    }
                    cachedHtml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    logger.info("[IndexHandler] HTML loaded (" + cachedHtml.length() + " bytes), script: " +
                        (cachedHtml.contains("index-") ? cachedHtml.replaceAll("(?s).*?(index-[A-Za-z0-9]+\\.js).*", "$1") : "none"));
                } catch (IOException e) {
                    logger.severe("读取 HTML 资源文件失败: " + e.getMessage());
                    return "<html><body><h1>Error loading page</h1></body></html>";
                }
            }
            return cachedHtml;
        }
    }

    /**
     * 静态资源处理器 - 优先从本地 dist 目录加载，fallback 到 classpath
     */
    class StaticAssetHandler implements HttpHandler {

        private final Path distDir;

        StaticAssetHandler(Path distDir) {
            this.distDir = distDir;
        }

        private static final Map<String, String> CONTENT_TYPES = new HashMap<>();

        static {
            CONTENT_TYPES.put("js", "application/javascript");
            CONTENT_TYPES.put("mjs", "application/javascript");
            CONTENT_TYPES.put("css", "text/css");
            CONTENT_TYPES.put("html", "text/html");
            CONTENT_TYPES.put("htm", "text/html");
            CONTENT_TYPES.put("svg", "image/svg+xml");
            CONTENT_TYPES.put("png", "image/png");
            CONTENT_TYPES.put("jpg", "image/jpeg");
            CONTENT_TYPES.put("jpeg", "image/jpeg");
            CONTENT_TYPES.put("gif", "image/gif");
            CONTENT_TYPES.put("ico", "image/x-icon");
            CONTENT_TYPES.put("json", "application/json");
            CONTENT_TYPES.put("woff", "font/woff");
            CONTENT_TYPES.put("woff2", "font/woff2");
            CONTENT_TYPES.put("ttf", "font/ttf");
            CONTENT_TYPES.put("eot", "application/vnd.ms-fontobject");
            CONTENT_TYPES.put("otf", "font/otf");
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (!path.startsWith("/assets/")) {
                sendResponse(exchange, 404, "Not found", "text/plain");
                return;
            }

            Path localFile = distDir.resolve(path.substring(1));
            if (Files.exists(localFile) && Files.isRegularFile(localFile)) {
                byte[] data = Files.readAllBytes(localFile);
                String contentType = getContentType(localFile.toString());
                sendResponse(exchange, 200, data, contentType);
                return;
            }

            String resourcePath = "/web" + path;
            try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    sendResponse(exchange, 404, "Not found: " + path, "text/plain");
                    return;
                }
                byte[] data = is.readAllBytes();
                String contentType = getContentType(resourcePath);
                sendResponse(exchange, 200, data, contentType);
            }
        }

        private String getContentType(String path) {
            int dot = path.lastIndexOf('.');
            if (dot > 0) {
                String ext = path.substring(dot + 1).toLowerCase();
                String ct = CONTENT_TYPES.get(ext);
                if (ct != null) return ct;
            }
            return "application/octet-stream";
        }
    }

    /**
     * 发送 HTTP 响应
     */
    static void sendResponse(HttpExchange exchange, int statusCode, String content, String contentType) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void sendResponse(HttpExchange exchange, int statusCode, byte[] data, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
        exchange.sendResponseHeaders(statusCode, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }


    public int getPort() { return port; }
    public int getWsPort() { return wsPort; }
}

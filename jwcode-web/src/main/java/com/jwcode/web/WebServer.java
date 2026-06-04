package com.jwcode.web;

import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.config.YamlConfigLoader;
import com.jwcode.core.index.CodebaseIndexer;
import com.jwcode.core.index.EmbeddingService;
import com.jwcode.core.index.IndexConfig;
import com.jwcode.core.task.TaskStore;
import com.jwcode.core.tool.ToolRegistry;
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
        // 初始化安全沙箱 — Docker 容器隔离，不可用时降级 WorkspaceGuard
        try {
            com.jwcode.core.tool.BashTool.setBackgroundExecutor(
                new com.jwcode.core.tool.shell.DockerSandboxExecutor(
                    java.nio.file.Path.of(this.workspaceDir)));
            System.out.println("[WebServer] Docker sandbox initialized");
        } catch (Exception e) {
            System.err.println("[WebServer] Sandbox init failed (non-fatal): " + e.getMessage());
        }

        // 启动 HTTP 服务器
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // 注册路由
        server.createContext("/", new IndexHandler());
        server.createContext("/api/chat", new ChatHandler(sessionManager));
        server.createContext("/api/sessions", new SessionsHandler(sessionManager));
        server.createContext("/api/tools", new ToolsHandler(toolRegistry));
        server.createContext("/api/config", new ConfigHandler());
        server.createContext("/api/skills", new SkillsHandler());
        server.createContext("/api/agents", new AgentsHandler());
        server.createContext("/api/templates", new TemplatesHandler());
        server.createContext("/api/models", new ModelInfoHandler());
        // 任务管理 API
        server.createContext("/api/tasks", new TaskHandler(TaskStore.getInstance()));
        server.createContext("/api/files", new FilesHandler());
        server.createContext("/api/system/status", new SystemStatusHandler());
        server.createContext("/api/checkpoints", new CheckpointsHandler());
        server.createContext("/assets/", new StaticAssetHandler());

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
        webSocketHandler.start();

        // 将 PlanTaskBroadcaster 接入主 WebSocket，使 plan_* 消息通过当前连接发送
        PlanTaskBroadcaster.setMessageSender((type, sid, data) ->
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
        if (codebaseIndexer != null) {
            codebaseIndexer.shutdown();
        }
        if (webSocketHandler != null) {
            webSocketHandler.shutdown();
        }
        if (terminalHandler != null) {
            terminalHandler.shutdown();
        }
        if (server != null) {
            server.stop(0);
            logger.info("Web UI 服务器已停止");
        }
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
     * 首页处理器 - 从外部资源文件加载 HTML
     */
    static class IndexHandler implements HttpHandler {
        
        // 缓存 HTML 内容
        private static String cachedHtml = null;
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = getHtmlContent();
            sendResponse(exchange, 200, html, "text/html");
        }
        
        private synchronized String getHtmlContent() {
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

    
    /**
     * 静态资源处理器 - 从 classpath 加载前端构建产物 (JS/CSS/图片等)
     */
    static class StaticAssetHandler implements HttpHandler {
        
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
            // 将 /assets/xxx 映射到 classpath /web/assets/xxx
            if (!path.startsWith("/assets/")) {
                sendResponse(exchange, 404, "Not found", "text/plain");
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
    
    public int getPort() { return port; }
    public int getWsPort() { return wsPort; }
}

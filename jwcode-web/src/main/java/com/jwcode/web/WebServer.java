package com.jwcode.web;

import com.jwcode.core.api.AgentFlowBroadcaster;
import com.jwcode.core.api.PlanTaskBroadcaster;
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
import com.jwcode.web.stream.StreamingWebSocketHandler;
import com.jwcode.web.terminal.TerminalHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

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

    public void start() throws IOException {
        new ConfigInitializer().initialize();

        try {
            sandboxExecutor = new com.jwcode.core.tool.shell.DockerSandboxExecutor(Path.of(workspaceDir));
            com.jwcode.core.tool.BashTool.setBackgroundExecutor(sandboxExecutor);
            System.out.println("[WebServer] Docker sandbox initialized");
        } catch (Exception e) {
            System.err.println("[WebServer] Sandbox init failed: " + e.getMessage());
        }

        try {
            int pluginCount = PluginManager.getInstance().discoverAndLoadAll();
            if (pluginCount > 0) {
                toolRegistry.registerPluginTools(PluginManager.getInstance());
                System.out.println("[WebServer] Loaded plugins: " + pluginCount);
            }
        } catch (Exception e) {
            System.err.println("[WebServer] Plugin init failed: " + e.getMessage());
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        Path distDir = Path.of(workspaceDir, "jwcode-web", "dist").toAbsolutePath().normalize();

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
        server.createContext("/api/tasks", new TaskHandler(TaskStore.getInstance()));
        server.createContext("/api/commands",
            new CommandsHandler(com.jwcode.core.command.CommandRegistry.createFull(toolRegistry)));
        server.createContext("/api/files", new FilesHandler());
        server.createContext("/api/system", new SystemStatusHandler());
        server.createContext("/api/checkpoints", new CheckpointsHandler());
        server.createContext("/api/observability", new ObservabilityHandler());

        MetricsHandler metricsHandler = new MetricsHandler();
        server.createContext("/api/metrics", metricsHandler);
        server.createContext("/api/logs", new LogsHandler());

        terminalHandler = new TerminalHandler();
        terminalHandler.setWorkspaceRoot(workspaceDir);
        server.createContext("/api/terminal", terminalHandler);

        {
            com.jwcode.core.hook.HookRegistry hookRegistry =
                com.jwcode.core.hook.HookSystemInitializer.getRegistry();
            if (hookRegistry == null) {
                Path hooksFile = Path.of(workspaceDir, ".jwcode", "hooks.json");
                hookRegistry = new com.jwcode.core.hook.HookRegistry(hooksFile);
                logger.info("[WebServer] Created standalone HookRegistry");
            }
            com.jwcode.core.agent.AgentRegistry agentRegistry =
                new com.jwcode.core.agent.AgentRegistry(toolRegistry);
            server.createContext("/api/hooks", new HooksHandler(
                hookRegistry,
                agentRegistry,
                Path.of(workspaceDir, ".jwcode", "hooks.json")
            ));
        }

        {
            com.jwcode.core.channel.ChannelRegistry channelRegistry =
                new com.jwcode.core.channel.ChannelRegistry();
            channelRegistry.registerFactory("wechat",
                cfg -> new com.jwcode.core.channel.wechat.WechatChannelAdapter());
            channelRegistry.load();
            com.jwcode.core.channel.ChannelMessageDispatcher dispatcher =
                new com.jwcode.core.channel.ChannelMessageDispatcher(toolRegistry, channelRegistry);
            dispatcher.start();
            server.createContext("/api/channels", new ChannelsHandler(channelRegistry));
        }

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

        initializeCodebaseIndexer();

        webSocketHandler = new StreamingWebSocketHandler(wsPort, toolRegistry);
        webSocketHandler.setCodebaseIndexer(codebaseIndexer);
        webSocketHandler.setSessionManager(sessionManager);
        webSocketHandler.setCommandRegistry(
            com.jwcode.core.command.CommandRegistry.getInstance());
        webSocketHandler.start();

        PlanTaskBroadcaster.setMessageSender((type, sid, data) ->
            webSocketHandler.sendMessage(sid,
                StreamingWebSocketHandler.MessageType.valueOf(type.toUpperCase()), data));

        AgentFlowBroadcaster.setMessageSender((type, sid, data) ->
            webSocketHandler.sendMessage(sid,
                StreamingWebSocketHandler.MessageType.valueOf(type.toUpperCase()), data));

        logger.info("Web UI server started: http://localhost:" + port);
        logger.info("WebSocket server started: ws://localhost:" + wsPort);
        System.out.println("[WebServer] Web UI started: http://localhost:" + port);
        System.out.println("[WebServer] WebSocket port: " + wsPort);
    }

    public void stop() {
        if (codebaseIndexer != null) {
            codebaseIndexer.shutdown();
        }
        if (webSocketHandler != null) {
            try {
                webSocketHandler.shutdown();
            } catch (Exception e) {
                logger.warning("WebSocket shutdown error: " + e.getMessage());
            }
        }
        if (terminalHandler != null) {
            terminalHandler.shutdown();
        }
        if (sandboxExecutor != null) {
            sandboxExecutor.shutdown();
        }
        if (server != null) {
            server.stop(0);
            logger.info("Web UI server stopped");
        }
    }

    public synchronized void restart() throws IOException {
        logger.info("Restarting server...");
        stop();
        YamlConfigLoader.resetInstance();
        ConfigManager.resetInstance();
        start();
        logger.info("Server restarted successfully");
    }

    private void initializeCodebaseIndexer() {
        try {
            Path workspaceRoot = Path.of(workspaceDir).toAbsolutePath().normalize();
            IndexConfig indexConfig = IndexConfig.forWorkspace(workspaceRoot);
            EmbeddingService embeddingService = createEmbeddingServiceFromConfig();

            codebaseIndexer = new CodebaseIndexer(workspaceRoot, indexConfig, embeddingService);
            toolRegistry.registerSemanticSearch(codebaseIndexer);

            codebaseIndexer.reindexAsync()
                .thenAccept(fileCount -> {
                    if (fileCount > 0) {
                        logger.info("Indexed " + fileCount + " files, vectors="
                            + codebaseIndexer.getVectorCount());
                    } else {
                        logger.info("Index complete, no new files");
                    }
                })
                .exceptionally(e -> {
                    logger.warning("Codebase indexing failed: " + e.getMessage());
                    return null;
                });

            codebaseIndexer.startWatching();
            System.out.println("[WebServer] Semantic search enabled");
        } catch (Exception e) {
            logger.warning("Codebase index init failed: " + e.getMessage());
            codebaseIndexer = null;
        }
    }

    private EmbeddingService createEmbeddingServiceFromConfig() {
        try {
            YamlConfigLoader configLoader = YamlConfigLoader.getInstance();
            JwcodeConfig config = configLoader.getConfig();
            JwcodeConfig.ProviderConfig provider = config.getDefaultProvider();

            if (provider != null
                && provider.getApiKeys() != null
                && !provider.getApiKeys().isEmpty()
                && provider.getBaseUrl() != null
                && !provider.getBaseUrl().isBlank()) {

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
                    int dimension = 256;
                    if (config.getSettings() != null
                        && config.getSettings().getSearch() != null
                        && config.getSettings().getSearch().getEmbeddingDimension() > 0) {
                        dimension = config.getSettings().getSearch().getEmbeddingDimension();
                    }

                    String embeddingModel = "text-embedding-ada-002";
                    for (JwcodeConfig.ModelDefinition model : provider.getModels()) {
                        if (model.getId() != null && model.getId().toLowerCase().contains("embedding")) {
                            embeddingModel = model.getId();
                            break;
                        }
                    }

                    return new EmbeddingService(provider, endpoint, embeddingModel, dimension);
                }
            }
        } catch (Exception e) {
            logger.warning("Config-based embedding init failed: " + e.getMessage());
        }

        String envKey = System.getenv("OPENAI_API_KEY");
        String envEndpoint = System.getenv("OPENAI_API_ENDPOINT");
        if (envKey != null && !envKey.isBlank()) {
            String endpoint = (envEndpoint != null && !envEndpoint.isBlank())
                ? envEndpoint : "https://api.openai.com/v1/embeddings";
            return new EmbeddingService(endpoint, envKey, "text-embedding-ada-002", 256);
        }

        return EmbeddingService.createLocalFallback(256);
    }

    class IndexHandler implements HttpHandler {
        private final Path distDir;
        private final boolean distAvailable;
        private String cachedHtml = null;
        private long cachedHtmlTime = 0;

        IndexHandler(Path distDir) {
            this.distDir = distDir;
            this.distAvailable = Files.exists(distDir.resolve("index.html"));
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendResponse(exchange, 200, getHtmlContent(), "text/html");
        }

        private synchronized String getHtmlContent() throws IOException {
            if (distAvailable) {
                Path htmlFile = distDir.resolve("index.html");
                if (Files.exists(htmlFile)) {
                    long lastMod = Files.getLastModifiedTime(htmlFile).toMillis();
                    if (cachedHtml == null || lastMod != cachedHtmlTime) {
                        cachedHtml = Files.readString(htmlFile, StandardCharsets.UTF_8);
                        cachedHtmlTime = lastMod;
                    }
                    return cachedHtml;
                }
            }

            if (cachedHtml == null) {
                java.net.URL resourceUrl = getClass().getResource("/web/index.html");
                if (resourceUrl == null) {
                    return "<html><body><h1>Error: HTML resource not found</h1></body></html>";
                }
                try (InputStream inputStream = resourceUrl.openStream()) {
                    cachedHtml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            return cachedHtml;
        }
    }

    class StaticAssetHandler implements HttpHandler {
        private final Path distDir;
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

        StaticAssetHandler(Path distDir) {
            this.distDir = distDir;
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
                sendResponse(exchange, 200, data, getContentType(localFile.toString()));
                return;
            }

            String resourcePath = "/web" + path;
            try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    sendResponse(exchange, 404, "Not found: " + path, "text/plain");
                    return;
                }
                sendResponse(exchange, 200, is.readAllBytes(), getContentType(resourcePath));
            }
        }

        private String getContentType(String path) {
            int dot = path.lastIndexOf('.');
            if (dot > 0) {
                String ext = path.substring(dot + 1).toLowerCase();
                String ct = CONTENT_TYPES.get(ext);
                if (ct != null) {
                    return ct;
                }
            }
            return "application/octet-stream";
        }
    }

    static void sendResponse(HttpExchange exchange, int statusCode, String content, String contentType)
        throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void sendResponse(HttpExchange exchange, int statusCode, byte[] data, String contentType)
        throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
        exchange.sendResponseHeaders(statusCode, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    public int getPort() {
        return port;
    }

    public int getWsPort() {
        return wsPort;
    }
}


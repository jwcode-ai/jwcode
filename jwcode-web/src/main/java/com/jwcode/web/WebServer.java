package com.jwcode.web;

import com.jwcode.core.task.TaskStore;
import com.jwcode.core.tool.ToolRegistry;
import com.jwcode.web.stream.StreamingWebSocketHandler;
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
import java.util.concurrent.Executors;
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
    private final int port;
    private final int wsPort;
    private final WebSessionManager sessionManager;
    private final ToolRegistry toolRegistry;
    
    public WebServer(int port, int wsPort, ToolRegistry toolRegistry) {
        this.port = port;
        this.wsPort = wsPort;
        this.sessionManager = new WebSessionManager();
        this.toolRegistry = toolRegistry;
    }
    
    public WebServer(int port, ToolRegistry toolRegistry) {
        this(port, port + 1, toolRegistry);
    }
    
    public WebServer(int port) {
        this(port, port + 1, ToolRegistry.createDefault());
    }
    
    public WebServer() {
        this(8080, 8081, ToolRegistry.createDefault());
    }
    
    /**
     * 启动服务器
     */
    public void start() throws IOException {
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
        server.createContext("/assets/", new StaticAssetHandler());
        
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
        
        // 启动 WebSocket 服务器（流式响应）
        webSocketHandler = new StreamingWebSocketHandler(wsPort, toolRegistry);
        webSocketHandler.start();
        
        logger.info("Web UI 服务器启动: http://localhost:" + port);
        logger.info("WebSocket 服务器启动: ws://localhost:" + wsPort);
        System.out.println("🌐 Web UI 已启动: http://localhost:" + port);
        System.out.println("📡 WebSocket 端口: " + wsPort);
    }
    
    /**
     * 停止服务器
     */
    public void stop() {
        if (webSocketHandler != null) {
            webSocketHandler.shutdown();
        }
        if (server != null) {
            server.stop(0);
            logger.info("Web UI 服务器已停止");
        }
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
                try (var inputStream = getClass().getResourceAsStream("/web/index.html")) {
                    if (inputStream == null) {
                        logger.warning("无法找到 HTML 资源文件 /web/index.html");
                        return "<html><body><h1>Error: HTML resource not found</h1></body></html>";
                    }
                    cachedHtml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
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
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    static void sendResponse(HttpExchange exchange, int statusCode, byte[] data, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
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

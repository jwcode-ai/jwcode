package com.jwcode.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

/**
 * REST API Server - Web UI 的后端 API
 * 
 * 提供模型、工具、技能、Agent 等管理接口
 */
public class RestApiServer {
    
    private static final Logger logger = Logger.getLogger(RestApiServer.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();
    
    private HttpServer server;
    private final int port;
    
    public RestApiServer(int port) {
        this.port = port;
    }
    
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // 注册路由
        server.createContext("/api/models", new ModelsHandler());
        server.createContext("/api/tools", new ToolsHandler());
        server.createContext("/api/skills", new SkillsHandler());
        server.createContext("/api/agents", new AgentsHandler());
        server.createContext("/api/files", new FilesHandler());
        server.createContext("/api/sessions", new SessionsHandler());
        server.createContext("/api/system/status", new SystemStatusHandler());
        
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
        
        logger.info("REST API Server 启动: http://localhost:" + port);
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
            logger.info("REST API Server 已停止");
        }
    }
    
    // ============ 辅助方法 ============
    
    private void sendJson(HttpExchange exchange, int statusCode, Object data) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(data);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        ObjectNode error = mapper.createObjectNode();
        error.put("success", false);
        error.put("error", message);
        sendJson(exchange, statusCode, error);
    }
    
    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    
    // ============ 模型处理器 ============
    
    class ModelsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            
            try {
                if ("GET".equals(method)) {
                    List<ObjectNode> models = getModelsList();
                    sendJson(exchange, 200, models);
                } else if ("POST".equals(method) && path.matches("/api/models/[^/]+/test")) {
                    ObjectNode result = mapper.createObjectNode();
                    result.put("success", true);
                    result.put("message", "模型测试成功");
                    sendJson(exchange, 200, result);
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }
    
    private List<ObjectNode> getModelsList() {
        List<ObjectNode> models = new ArrayList<>();
        
        // 默认模型
        ObjectNode claude = mapper.createObjectNode();
        claude.put("id", "claude-3-5-sonnet");
        claude.put("name", "Claude 3.5 Sonnet");
        claude.put("provider", "anthropic");
        claude.put("status", "online");
        claude.put("load", 45);
        claude.put("maxLoad", 100);
        claude.put("tokens", 32000);
        claude.put("maxTokens", 200000);
        ObjectNode price1 = mapper.createObjectNode();
        price1.put("input", 3.0);
        price1.put("output", 15.0);
        claude.set("price", price1);
        models.add(claude);
        
        ObjectNode gpt4 = mapper.createObjectNode();
        gpt4.put("id", "gpt-4-turbo");
        gpt4.put("name", "GPT-4 Turbo");
        gpt4.put("provider", "openai");
        gpt4.put("status", "online");
        gpt4.put("load", 30);
        gpt4.put("maxLoad", 100);
        gpt4.put("tokens", 45000);
        gpt4.put("maxTokens", 128000);
        ObjectNode price2 = mapper.createObjectNode();
        price2.put("input", 10.0);
        price2.put("output", 30.0);
        gpt4.set("price", price2);
        models.add(gpt4);
        
        ObjectNode moonshot = mapper.createObjectNode();
        moonshot.put("id", "moonshot-v1-128k");
        moonshot.put("name", "Moonshot V1 128K");
        moonshot.put("provider", "moonshot");
        moonshot.put("status", "offline");
        moonshot.put("load", 0);
        moonshot.put("maxLoad", 100);
        moonshot.put("tokens", 0);
        moonshot.put("maxTokens", 128000);
        ObjectNode price3 = mapper.createObjectNode();
        price3.put("input", 0.0);
        price3.put("output", 0.0);
        moonshot.set("price", price3);
        models.add(moonshot);
        
        return models;
    }
    
    // ============ 工具处理器 ============
    
    class ToolsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            String method = exchange.getRequestMethod();
            
            try {
                if ("GET".equals(method)) {
                    List<ObjectNode> tools = getToolsList();
                    sendJson(exchange, 200, tools);
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }
    
    private List<ObjectNode> getToolsList() {
        List<ObjectNode> tools = new ArrayList<>();
        
        String[][] defaultTools = {
            {"read", "Read", "读取文件内容", "file"},
            {"write", "Write", "写入文件内容", "file"},
            {"edit", "Edit", "编辑文件内容", "file"},
            {"grep", "Grep", "搜索文件内容", "search"},
            {"glob", "Glob", "查找匹配的文件", "search"},
            {"bash", "Bash", "执行 shell 命令", "system"},
            {"web-fetch", "WebFetch", "获取网页内容", "web"},
            {"web-search", "WebSearch", "网络搜索", "web"},
            {"git", "Git", "Git 版本控制", "git"},
        };
        
        for (String[] tool : defaultTools) {
            ObjectNode t = mapper.createObjectNode();
            t.put("id", tool[0]);
            t.put("name", tool[1]);
            t.put("description", tool[2]);
            t.put("category", tool[3]);
            t.put("enabled", true);
            t.set("params", mapper.createArrayNode());
            tools.add(t);
        }
        
        return tools;
    }
    
    // ============ 技能处理器 ============
    
    class SkillsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            String method = exchange.getRequestMethod();
            
            try {
                if ("GET".equals(method)) {
                    List<ObjectNode> skills = getSkillsList();
                    sendJson(exchange, 200, skills);
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }
    
    private List<ObjectNode> getSkillsList() {
        List<ObjectNode> skills = new ArrayList<>();
        
        ObjectNode coding = mapper.createObjectNode();
        coding.put("id", "coding");
        coding.put("name", "代码编写");
        coding.put("description", "编写高质量代码，支持多种编程语言");
        coding.put("category", "coding");
        coding.put("enabled", true);
        coding.put("icon", "💻");
        skills.add(coding);
        
        ObjectNode analysis = mapper.createObjectNode();
        analysis.put("id", "analysis");
        analysis.put("name", "代码分析");
        analysis.put("description", "分析代码结构、依赖和潜在问题");
        analysis.put("category", "analysis");
        analysis.put("enabled", true);
        analysis.put("icon", "🔍");
        skills.add(analysis);
        
        ObjectNode refactor = mapper.createObjectNode();
        refactor.put("id", "refactor");
        refactor.put("name", "代码重构");
        refactor.put("description", "重构代码以提高可读性和性能");
        refactor.put("category", "refactor");
        refactor.put("enabled", true);
        refactor.put("icon", "🔧");
        skills.add(refactor);
        
        ObjectNode test = mapper.createObjectNode();
        test.put("id", "test");
        test.put("name", "测试编写");
        test.put("description", "编写单元测试和集成测试");
        test.put("category", "test");
        test.put("enabled", true);
        test.put("icon", "🧪");
        skills.add(test);
        
        ObjectNode deploy = mapper.createObjectNode();
        deploy.put("id", "deploy");
        deploy.put("name", "部署发布");
        deploy.put("description", "自动化部署和发布流程");
        deploy.put("category", "deploy");
        deploy.put("enabled", true);
        deploy.put("icon", "🚀");
        skills.add(deploy);
        
        return skills;
    }
    
    // ============ Agent 处理器 ============
    
    class AgentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            String method = exchange.getRequestMethod();
            
            try {
                if ("GET".equals(method)) {
                    List<ObjectNode> agents = getAgentsList();
                    sendJson(exchange, 200, agents);
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }
    
    private List<ObjectNode> getAgentsList() {
        List<ObjectNode> agents = new ArrayList<>();
        
        ObjectNode coder = mapper.createObjectNode();
        coder.put("id", "coder");
        coder.put("name", "Coder");
        coder.put("description", "主编码助手，负责代码编写和修改");
        coder.put("color", "#569cd6");
        coder.put("active", true);
        coder.put("state", "idle");
        agents.add(coder);
        
        ObjectNode reviewer = mapper.createObjectNode();
        reviewer.put("id", "reviewer");
        reviewer.put("name", "Reviewer");
        reviewer.put("description", "代码审查助手，负责代码审查和质量检查");
        reviewer.put("color", "#4ec9b0");
        reviewer.put("active", false);
        reviewer.put("state", "idle");
        agents.add(reviewer);
        
        ObjectNode debugger = mapper.createObjectNode();
        debugger.put("id", "debugger");
        debugger.put("name", "Debugger");
        debugger.put("description", "调试助手，负责问题定位和修复");
        debugger.put("color", "#ce9178");
        debugger.put("active", false);
        debugger.put("state", "idle");
        agents.add(debugger);
        
        return agents;
    }
    
    // ============ 文件处理器 ============
    
    class FilesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            String method = exchange.getRequestMethod();
            
            try {
                if ("GET".equals(method)) {
                    String query = exchange.getRequestURI().getQuery();
                    String dirPath = query != null && query.startsWith("path=") ? 
                        java.net.URLDecoder.decode(query.substring(5), "UTF-8") : System.getProperty("user.dir");
                    List<ObjectNode> files = listFiles(dirPath);
                    sendJson(exchange, 200, files);
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }
    
    private List<ObjectNode> listFiles(String dirPath) {
        List<ObjectNode> files = new ArrayList<>();
        java.io.File dir = new java.io.File(dirPath);
        
        if (dir.exists() && dir.isDirectory()) {
            java.io.File[] children = dir.listFiles();
            if (children != null) {
                Arrays.sort(children, (a, b) -> {
                    if (a.isDirectory() != b.isDirectory()) {
                        return a.isDirectory() ? -1 : 1;
                    }
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                
                for (java.io.File file : children) {
                    String name = file.getName();
                    // 跳过隐藏文件和常见忽略目录
                    if (name.startsWith(".")) continue;
                    if (name.equals("node_modules") || name.equals("target") || 
                        name.equals("build") || name.equals("dist")) continue;
                    
                    ObjectNode node = mapper.createObjectNode();
                    node.put("id", file.getAbsolutePath());
                    node.put("name", file.getName());
                    node.put("path", file.getAbsolutePath());
                    node.put("type", file.isDirectory() ? "directory" : "file");
                    files.add(node);
                }
            }
        }
        
        return files;
    }
    
    // ============ 会话处理器 ============
    
    class SessionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            String method = exchange.getRequestMethod();
            
            try {
                if ("GET".equals(method)) {
                    List<ObjectNode> sessions = getSessionsList();
                    sendJson(exchange, 200, sessions);
                } else if ("POST".equals(method)) {
                    ObjectNode session = createSession();
                    sendJson(exchange, 201, session);
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }
    
    private List<ObjectNode> getSessionsList() {
        List<ObjectNode> sessions = new ArrayList<>();
        ObjectNode session = mapper.createObjectNode();
        session.put("id", "default");
        session.put("title", "默认会话");
        session.put("createdAt", System.currentTimeMillis());
        session.put("updatedAt", System.currentTimeMillis());
        session.put("messageCount", 0);
        sessions.add(session);
        return sessions;
    }
    
    private ObjectNode createSession() {
        ObjectNode session = mapper.createObjectNode();
        session.put("id", "session-" + System.currentTimeMillis());
        session.put("title", "新会话");
        session.put("createdAt", System.currentTimeMillis());
        session.put("updatedAt", System.currentTimeMillis());
        session.put("messageCount", 0);
        return session;
    }
    
    // ============ 系统状态处理器 ============
    
    class SystemStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            try {
                ObjectNode status = mapper.createObjectNode();
                status.put("status", "running");
                status.put("uptime", Runtime.getRuntime().totalMemory());
                
                ObjectNode memory = mapper.createObjectNode();
                memory.put("used", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
                memory.put("total", Runtime.getRuntime().totalMemory());
                status.set("memory", memory);
                
                status.put("activeSessions", 1);
                status.put("timestamp", System.currentTimeMillis());
                
                sendJson(exchange, 200, status);
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }
}

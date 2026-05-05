package com.jwcode.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

/**
 * REST API Server - Web UI 的后端 API
 * 
 * 提供模型、工具、技能、Agent、文件、会话、任务等管理接口
 */
public class RestApiServer {
    
    private static final Logger logger = Logger.getLogger(RestApiServer.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();
    
    private HttpServer server;
    private final int port;
    
    // ==================== 数据存储 ====================
    
    // 模型存储
    private final Map<String, ObjectNode> modelStore = new ConcurrentHashMap<>();
    // 工具存储
    private final Map<String, ObjectNode> toolStore = new ConcurrentHashMap<>();
    // 技能存储
    private final Map<String, ObjectNode> skillStore = new ConcurrentHashMap<>();
    // Agent存储
    private final Map<String, ObjectNode> agentStore = new ConcurrentHashMap<>();
    // 会话存储
    private final Map<String, ObjectNode> sessionStore = new ConcurrentHashMap<>();
    // 任务存储
    private final Map<String, ObjectNode> taskStore = new ConcurrentHashMap<>();
    private final AtomicLong taskIdCounter = new AtomicLong(1);
    private final AtomicLong sessionIdCounter = new AtomicLong(1);
    
    public RestApiServer(int port) {
        this.port = port;
        initializeData();
    }
    
    private void initializeData() {
        // 初始化模型
        initModels();
        // 初始化工具
        initTools();
        // 初始化技能
        initSkills();
        // 初始化Agent
        initAgents();
        // 初始化会话
        initSessions();
    }
    
    private void initModels() {
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
        modelStore.put("claude-3-5-sonnet", claude);
        
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
        modelStore.put("gpt-4-turbo", gpt4);
        
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
        modelStore.put("moonshot-v1-128k", moonshot);
    }
    
    private void initTools() {
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
            toolStore.put(tool[0], t);
        }
    }
    
    private void initSkills() {
        String[][] defaultSkills = {
            {"coding", "代码编写", "编写高质量代码，支持多种编程语言", "coding", "💻"},
            {"analysis", "代码分析", "分析代码结构、依赖和潜在问题", "analysis", "🔍"},
            {"refactor", "代码重构", "重构代码以提高可读性和性能", "refactor", "🔧"},
            {"test", "测试编写", "编写单元测试和集成测试", "test", "🧪"},
            {"deploy", "部署发布", "自动化部署和发布流程", "deploy", "🚀"},
        };
        
        for (String[] skill : defaultSkills) {
            ObjectNode s = mapper.createObjectNode();
            s.put("id", skill[0]);
            s.put("name", skill[1]);
            s.put("description", skill[2]);
            s.put("category", skill[3]);
            s.put("enabled", true);
            s.put("icon", skill[4]);
            skillStore.put(skill[0], s);
        }
    }
    
    private void initAgents() {
        ObjectNode coder = mapper.createObjectNode();
        coder.put("id", "coder");
        coder.put("name", "Coder");
        coder.put("description", "主编码助手，负责代码编写和修改");
        coder.put("color", "#569cd6");
        coder.put("active", true);
        coder.put("state", "idle");
        agentStore.put("coder", coder);
        
        ObjectNode reviewer = mapper.createObjectNode();
        reviewer.put("id", "reviewer");
        reviewer.put("name", "Reviewer");
        reviewer.put("description", "代码审查助手，负责代码审查和质量检查");
        reviewer.put("color", "#4ec9b0");
        reviewer.put("active", false);
        reviewer.put("state", "idle");
        agentStore.put("reviewer", reviewer);
        
        ObjectNode debugger = mapper.createObjectNode();
        debugger.put("id", "debugger");
        debugger.put("name", "Debugger");
        debugger.put("description", "调试助手，负责问题定位和修复");
        debugger.put("color", "#ce9178");
        debugger.put("active", false);
        debugger.put("state", "idle");
        agentStore.put("debugger", debugger);
    }
    
    private void initSessions() {
        ObjectNode session = mapper.createObjectNode();
        session.put("id", "default");
        session.put("title", "默认会话");
        session.put("createdAt", System.currentTimeMillis());
        session.put("updatedAt", System.currentTimeMillis());
        session.put("messageCount", 0);
        sessionStore.put("default", session);
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
        server.createContext("/api/tasks", new TasksHandler());
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
    
    // ============ 主方法 ============
    
    public static void main(String[] args) throws IOException {
        // NOTE: WebServer (in jwcode-web) is the intended production entry point.
        // It already embeds the API endpoints and serves the React frontend.
        // Only start RestApiServer standalone for development/testing.
        if (Boolean.getBoolean("jwcode.webserver.enabled")) {
            System.out.println("WebServer is enabled. RestApiServer should not start independently.");
            System.out.println("Use WebServer as the unified entry point.");
            return;
        }
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        RestApiServer server = new RestApiServer(port);
        server.start();
        System.out.println("API Server running at http://localhost:" + port);
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
    
    private void sendListResponse(HttpExchange exchange, List<?> data) throws IOException {
        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ArrayNode dataArray = mapper.createArrayNode();
        for (Object item : data) {
            dataArray.addPOJO(item);
        }
        response.set("data", dataArray);
        sendJson(exchange, 200, response);
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
    
    private String extractId(String path, String prefix) {
        if (path.startsWith(prefix) && path.length() > prefix.length()) {
            String rest = path.substring(prefix.length());
            int slash = rest.indexOf('/');
            return slash > 0 ? rest.substring(0, slash) : rest;
        }
        return null;
    }
    
    // ============ 模型处理器 ============
    
    class ModelsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            
            try {
                // /api/models - 列表
                if ("/api/models".equals(path)) {
                    if ("GET".equals(method)) {
                        sendListResponse(exchange, new ArrayList<>(modelStore.values()));
                    } else {
                        sendError(exchange, 405, "Method not allowed");
                    }
                    return;
                }
                
                // /api/models/{id} - 获取/更新/删除
                String modelId = extractId(path, "/api/models/");
                if (modelId != null) {
                    if ("GET".equals(method)) {
                        ObjectNode model = modelStore.get(modelId);
                        if (model == null) {
                            sendError(exchange, 404, "Model not found");
                        } else {
                            sendJson(exchange, 200, model);
                        }
                    } else if ("PUT".equals(method)) {
                        ObjectNode updated = updateModel(modelId, readBody(exchange));
                        if (updated == null) {
                            sendError(exchange, 404, "Model not found");
                        } else {
                            sendJson(exchange, 200, updated);
                        }
                    } else if ("DELETE".equals(method)) {
                        if (modelStore.remove(modelId) == null) {
                            sendError(exchange, 404, "Model not found");
                        } else {
                            sendJson(exchange, 204, mapper.createObjectNode());
                        }
                    } else if ("POST".equals(method) && path.matches("/api/models/[^/]+/test")) {
                        ObjectNode result = mapper.createObjectNode();
                        result.put("success", true);
                        result.put("message", "模型测试成功");
                        sendJson(exchange, 200, result);
                    } else {
                        sendError(exchange, 405, "Method not allowed");
                    }
                    return;
                }
                
                sendError(exchange, 404, "Not found");
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }
    
    private ObjectNode updateModel(String id, String body) {
        ObjectNode existing = modelStore.get(id);
        if (existing == null) return null;
        
        try {
            ObjectNode input = mapper.readValue(body, ObjectNode.class);
            if (input.has("name")) existing.put("name", input.get("name").asText());
            if (input.has("status")) existing.put("status", input.get("status").asText());
            if (input.has("load")) existing.put("load", input.get("load").asInt());
            return existing;
        } catch (Exception e) {
            return existing;
        }
    }
    
    // ============ 工具处理器 ============
    
    class ToolsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            
            try {
                // /api/tools - 列表
                if ("/api/tools".equals(path)) {
                    if ("GET".equals(method)) {
                        sendJson(exchange, 200, new ArrayList<>(toolStore.values()));
                    } else {
                        sendError(exchange, 405, "Method not allowed");
                    }
                    return;
                }
                
                // /api/tools/{id} - 获取/更新/删除
                String toolId = extractId(path, "/api/tools/");
                if (toolId != null) {
                    if ("GET".equals(method)) {
                        ObjectNode tool = toolStore.get(toolId);
                        if (tool == null) {
                            sendError(exchange, 404, "Tool not found");
                        } else {
                            sendJson(exchange, 200, tool);
                        }
                    } else if ("PUT".equals(method)) {
                        ObjectNode updated = updateTool(toolId, readBody(exchange));
                        if (updated == null) {
                            sendError(exchange, 404, "Tool not found");
                        } else {
                            sendJson(exchange, 200, updated);
                        }
                    } else if ("DELETE".equals(method)) {
                        if (toolStore.remove(toolId) == null) {
                            sendError(exchange, 404, "Tool not found");
                        } else {
                            sendJson(exchange, 204, mapper.createObjectNode());
                        }
                    } else if ("POST".equals(method) && path.matches("/api/tools/[^/]+/toggle")) {
                        ObjectNode updated = toggleTool(toolId, readBody(exchange));
                        if (updated == null) {
                            sendError(exchange, 404, "Tool not found");
                        } else {
                            sendJson(exchange, 200, updated);
                        }
                    } else {
                        sendError(exchange, 405, "Method not allowed");
                    }
                    return;
                }
                
                sendError(exchange, 404, "Not found");
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }
    
    private ObjectNode updateTool(String id, String body) {
        ObjectNode existing = toolStore.get(id);
        if (existing == null) return null;
        
        try {
            ObjectNode input = mapper.readValue(body, ObjectNode.class);
            if (input.has("name")) existing.put("name", input.get("name").asText());
            if (input.has("description")) existing.put("description", input.get("description").asText());
            if (input.has("enabled")) existing.put("enabled", input.get("enabled").asBoolean());
            return existing;
        } catch (Exception e) {
            return existing;
        }
    }
    
    private ObjectNode toggleTool(String id, String body) {
        ObjectNode existing = toolStore.get(id);
        if (existing == null) return null;
        
        try {
            ObjectNode input = mapper.readValue(body, ObjectNode.class);
            if (input.has("enabled")) {
                existing.put("enabled", input.get("enabled").asBoolean());
            }
            return existing;
        } catch (Exception e) {
            return existing;
        }
    }
    
    // ============ 技能处理器 ============
    
    class SkillsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            
            try {
                // /api/skills - 列表
                if ("/api/skills".equals(path)) {
                    if ("GET".equals(method)) {
                        sendListResponse(exchange, new ArrayList<>(skillStore.values()));
                    } else {
                        sendError(exchange, 405, "Method not allowed");
                    }
                    return;
                }
                
                // /api/skills/{id} - 获取/更新/删除
                String skillId = extractId(path, "/api/skills/");
                if (skillId != null) {
                    if ("GET".equals(method)) {
                        ObjectNode skill = skillStore.get(skillId);
                        if (skill == null) {
                            sendError(exchange, 404, "Skill not found");
                        } else {
                            sendJson(exchange, 200, skill);
                        }
                    } else if ("PUT".equals(method)) {
                        ObjectNode updated = updateSkill(skillId, readBody(exchange));
                        if (updated == null) {
                            sendError(exchange, 404, "Skill not found");
                        } else {
                            sendJson(exchange, 200, updated);
                        }
                    } else if ("DELETE".equals(method)) {
                        if (skillStore.remove(skillId) == null) {
                            sendError(exchange, 404, "Skill not found");
                        } else {
                            sendJson(exchange, 204, mapper.createObjectNode());
                        }
                    } else if ("POST".equals(method) && path.matches("/api/skills/[^/]+/toggle")) {
                        ObjectNode updated = toggleSkill(skillId, readBody(exchange));
                        if (updated == null) {
                            sendError(exchange, 404, "Skill not found");
                        } else {
                            sendJson(exchange, 200, updated);
                        }
                    } else {
                        sendError(exchange, 405, "Method not allowed");
                    }
                    return;
                }
                
                sendError(exchange, 404, "Not found");
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }
    
    private ObjectNode updateSkill(String id, String body) {
        ObjectNode existing = skillStore.get(id);
        if (existing == null) return null;
        
        try {
            ObjectNode input = mapper.readValue(body, ObjectNode.class);
            if (input.has("name")) existing.put("name", input.get("name").asText());
            if (input.has("description")) existing.put("description", input.get("description").asText());
            if (input.has("enabled")) existing.put("enabled", input.get("enabled").asBoolean());
            return existing;
        } catch (Exception e) {
            return existing;
        }
    }
    
    private ObjectNode toggleSkill(String id, String body) {
        ObjectNode existing = skillStore.get(id);
        if (existing == null) return null;
        
        try {
            ObjectNode input = mapper.readValue(body, ObjectNode.class);
            if (input.has("enabled")) {
                existing.put("enabled", input.get("enabled").asBoolean());
            }
            return existing;
        } catch (Exception e) {
            return existing;
        }
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
            
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            
            try {
                // /api/agents - 列表/创建
                if ("/api/agents".equals(path)) {
                    if ("GET".equals(method)) {
                        sendJson(exchange, 200, new ArrayList<>(agentStore.values()));
                    } else if ("POST".equals(method)) {
                        ObjectNode agent = createAgent(readBody(exchange));
                        sendJson(exchange, 201, agent);
                    } else {
                        sendError(exchange, 405, "Method not allowed");
                    }
                    return;
                }
                
                // /api/agents/{id} - 获取/更新/删除
                String agentId = extractId(path, "/api/agents/");
                if (agentId != null) {
                    if ("GET".equals(method)) {
                        ObjectNode agent = agentStore.get(agentId);
                        if (agent == null) {
                            sendError(exchange, 404, "Agent not found");
                        } else {
                            sendJson(exchange, 200, agent);
                        }
                    } else if ("PUT".equals(method)) {
                        ObjectNode updated = updateAgent(agentId, readBody(exchange));
                        if (updated == null) {
                            sendError(exchange, 404, "Agent not found");
                        } else {
                            sendJson(exchange, 200, updated);
                        }
                    } else if ("DELETE".equals(method)) {
                        if (agentStore.remove(agentId) == null) {
                            sendError(exchange, 404, "Agent not found");
                        } else {
                            sendJson(exchange, 204, mapper.createObjectNode());
                        }
                    } else if ("POST".equals(method) && path.matches("/api/agents/[^/]+/activate")) {
                        ObjectNode updated = setActiveAgent(agentId);
                        if (updated == null) {
                            sendError(exchange, 404, "Agent not found");
                        } else {
                            sendJson(exchange, 200, updated);
                        }
                    } else {
                        sendError(exchange, 405, "Method not allowed");
                    }
                    return;
                }
                
                sendError(exchange, 404, "Not found");
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }
    
    private ObjectNode createAgent(String body) {
        try {
            ObjectNode input = mapper.readValue(body, ObjectNode.class);
            String id = input.has("id") ? input.get("id").asText() : "agent-" + System.currentTimeMillis();
            ObjectNode agent = mapper.createObjectNode();
            agent.put("id", id);
            agent.put("name", input.has("name") ? input.get("name").asText() : "新Agent");
            agent.put("description", input.has("description") ? input.get("description").asText() : "");
            agent.put("color", input.has("color") ? input.get("color").asText() : "#888888");
            agent.put("active", input.has("active") ? input.get("active").asBoolean() : false);
            agent.put("state", "idle");
            agentStore.put(id, agent);
            return agent;
        } catch (Exception e) {
            return null;
        }
    }
    
    private ObjectNode updateAgent(String id, String body) {
        ObjectNode existing = agentStore.get(id);
        if (existing == null) return null;
        
        try {
            ObjectNode input = mapper.readValue(body, ObjectNode.class);
            if (input.has("name")) existing.put("name", input.get("name").asText());
            if (input.has("description")) existing.put("description", input.get("description").asText());
            if (input.has("color")) existing.put("color", input.get("color").asText());
            if (input.has("active")) existing.put("active", input.get("active").asBoolean());
            return existing;
        } catch (Exception e) {
            return existing;
        }
    }
    
    private ObjectNode setActiveAgent(String id) {
        // 先取消所有Agent的active状态
        for (ObjectNode agent : agentStore.values()) {
            agent.put("active", false);
        }
        // 激活指定Agent
        ObjectNode agent = agentStore.get(id);
        if (agent != null) {
            agent.put("active", true);
        }
        return agent;
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
            
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            
            try {
                // /api/files - 列表
                if ("/api/files".equals(path)) {
                    if ("GET".equals(method)) {
                        String query = exchange.getRequestURI().getQuery();
                        String dirPath = query != null && query.startsWith("path=") ? 
                            java.net.URLDecoder.decode(query.substring(5), "UTF-8") : System.getProperty("user.dir");
                        sendJson(exchange, 200, listFiles(dirPath));
                    } else if ("POST".equals(method)) {
                        createFile(readBody(exchange));
                        sendJson(exchange, 201, mapper.createObjectNode());
                    } else if ("DELETE".equals(method)) {
                        String query = exchange.getRequestURI().getQuery();
                        if (query != null && query.startsWith("path=")) {
                            String filePath = java.net.URLDecoder.decode(query.substring(5), "UTF-8");
                            deleteFile(filePath);
                        }
                        sendJson(exchange, 204, mapper.createObjectNode());
                    } else {
                        sendError(exchange, 405, "Method not allowed");
                    }
                    return;
                }
                
                // /api/files/read - 读取文件
                if (path.equals("/api/files/read")) {
                    if ("GET".equals(method)) {
                        String query = exchange.getRequestURI().getQuery();
                        String filePath = query != null && query.startsWith("path=") ? 
                            java.net.URLDecoder.decode(query.substring(5), "UTF-8") : null;
                        if (filePath == null) {
                            sendError(exchange, 400, "Missing path parameter");
                        } else {
                            String content = readFile(filePath);
                            if (content == null) {
                                sendError(exchange, 404, "File not found");
                            } else {
                                sendJson(exchange, 200, content);
                            }
                        }
                    } else {
                        sendError(exchange, 405, "Method not allowed");
                    }
                    return;
                }
                
                // /api/files/write - 写入文件
                if (path.equals("/api/files/write")) {
                    if ("PUT".equals(method)) {
                        writeFile(readBody(exchange));
                        sendJson(exchange, 200, mapper.createObjectNode());
                    } else {
                        sendError(exchange, 405, "Method not allowed");
                    }
                    return;
                }
                
                sendError(exchange, 404, "Not found");
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
    
    private String readFile(String filePath) {
        try {
            return new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
    
    private boolean createFile(String body) {
        try {
            ObjectNode input = mapper.readValue(body, ObjectNode.class);
            String filePath = input.get("path").asText();
            String content = input.has("content") ? input.get("content").asText() : "";
            Files.write(Paths.get(filePath), content.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean writeFile(String body) {
        return createFile(body);
    }
    
    private boolean deleteFile(String filePath) {
        try {
            Files.delete(Paths.get(filePath));
            return true;
        } catch (IOException e) {
            return false;
        }
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
            
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            
            try {
                // /api/sessions - 列表/创建
                if ("/api/sessions".equals(path)) {
                    if ("GET".equals(method)) {
                        sendListResponse(exchange, new ArrayList<>(sessionStore.values()));
                    } else if ("POST".equals(method)) {
                        ObjectNode session = createSession(readBody(exchange));
                        sendJson(exchange, 201, session);
                    } else {
                        sendError(exchange, 405, "Method not allowed");
                    }
                    return;
                }
                
                // /api/sessions/{id} - 获取/删除
                String sessionId = extractId(path, "/api/sessions/");
                if (sessionId != null) {
                    if ("GET".equals(method)) {
                        ObjectNode session = sessionStore.get(sessionId);
                        if (session == null) {
                            sendError(exchange, 404, "Session not found");
                        } else {
                            sendJson(exchange, 200, session);
                        }
                    } else if ("DELETE".equals(method)) {
                        if (sessionStore.remove(sessionId) == null) {
                            sendError(exchange, 404, "Session not found");
                        } else {
                            sendJson(exchange, 204, mapper.createObjectNode());
                        }
                    } else {
                        sendError(exchange, 405, "Method not allowed");
                    }
                    return;
                }
                
                sendError(exchange, 404, "Not found");
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }
    
    private ObjectNode createSession(String body) {
        String id = "session-" + sessionIdCounter.getAndIncrement();
        ObjectNode session = mapper.createObjectNode();
        session.put("id", id);
        
        try {
            ObjectNode input = mapper.readValue(body, ObjectNode.class);
            session.put("title", input.has("title") ? input.get("title").asText() : "新会话");
        } catch (Exception e) {
            session.put("title", "新会话");
        }
        
        session.put("createdAt", System.currentTimeMillis());
        session.put("updatedAt", System.currentTimeMillis());
        session.put("messageCount", 0);
        sessionStore.put(id, session);
        return session;
    }
    
    // ============ 任务处理器 ============
    
    class TasksHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            
            try {
                // /api/tasks - 列表/创建/清除
                if ("/api/tasks".equals(path)) {
                    if ("GET".equals(method)) {
                        sendJson(exchange, 200, getTasksList());
                    } else if ("POST".equals(method)) {
                        ObjectNode task = createTask(readBody(exchange));
                        sendJson(exchange, 201, task);
                    } else if ("DELETE".equals(method)) {
                        ObjectNode result = mapper.createObjectNode();
                        result.put("deleted", clearCompletedTasks());
                        sendJson(exchange, 200, result);
                    } else {
                        sendError(exchange, 405, "Method not allowed");
                    }
                    return;
                }
                
                // /api/tasks/{id} - 获取/更新/删除/更新状态
                String taskId = extractId(path, "/api/tasks/");
                if (taskId != null) {
                    if ("GET".equals(method)) {
                        ObjectNode task = taskStore.get(taskId);
                        if (task == null) {
                            sendError(exchange, 404, "Task not found");
                        } else {
                            sendJson(exchange, 200, task);
                        }
                    } else if ("PUT".equals(method)) {
                        ObjectNode task = updateTask(taskId, readBody(exchange));
                        if (task == null) {
                            sendError(exchange, 404, "Task not found");
                        } else {
                            sendJson(exchange, 200, task);
                        }
                    } else if ("DELETE".equals(method)) {
                        if (taskStore.remove(taskId) == null) {
                            sendError(exchange, 404, "Task not found");
                        } else {
                            sendJson(exchange, 204, mapper.createObjectNode());
                        }
                    } else if ("PATCH".equals(method) && path.matches("/api/tasks/[^/]+/status")) {
                        ObjectNode task = updateTaskStatus(taskId, readBody(exchange));
                        if (task == null) {
                            sendError(exchange, 404, "Task not found");
                        } else {
                            sendJson(exchange, 200, task);
                        }
                    } else {
                        sendError(exchange, 405, "Method not allowed");
                    }
                    return;
                }
                
                sendError(exchange, 404, "Not found");
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }
    
    private List<ObjectNode> getTasksList() {
        List<ObjectNode> tasks = new ArrayList<>(taskStore.values());
        tasks.sort((a, b) -> {
            int pa = a.has("priority") ? a.get("priority").asInt() : 0;
            int pb = b.has("priority") ? b.get("priority").asInt() : 0;
            if (pa != pb) return Integer.compare(pb, pa);
            long ca = a.has("createdAt") ? a.get("createdAt").asLong() : 0;
            long cb = b.has("createdAt") ? b.get("createdAt").asLong() : 0;
            return Long.compare(cb, ca);
        });
        return tasks;
    }
    
    private ObjectNode createTask(String body) {
        String id = "task-" + taskIdCounter.getAndIncrement();
        ObjectNode task = mapper.createObjectNode();
        task.put("id", id);
        
        try {
            ObjectNode input = mapper.readValue(body, ObjectNode.class);
            task.put("title", input.has("title") ? input.get("title").asText() : "新任务");
            task.put("description", input.has("description") ? input.get("description").asText() : "");
            task.put("status", input.has("status") ? input.get("status").asText() : "PENDING");
            task.put("priority", input.has("priority") ? input.get("priority").asInt() : 0);
            task.put("progress", input.has("progress") ? input.get("progress").asInt() : 0);
        } catch (Exception e) {
            task.put("title", "新任务");
            task.put("description", "");
            task.put("status", "PENDING");
            task.put("priority", 0);
            task.put("progress", 0);
        }
        
        task.put("createdAt", System.currentTimeMillis());
        task.put("updatedAt", System.currentTimeMillis());
        taskStore.put(id, task);
        return task;
    }
    
    private ObjectNode updateTask(String id, String body) {
        ObjectNode existing = taskStore.get(id);
        if (existing == null) return null;
        
        try {
            ObjectNode input = mapper.readValue(body, ObjectNode.class);
            if (input.has("title")) existing.put("title", input.get("title").asText());
            if (input.has("description")) existing.put("description", input.get("description").asText());
            if (input.has("status")) existing.put("status", input.get("status").asText());
            if (input.has("priority")) existing.put("priority", input.get("priority").asInt());
            if (input.has("progress")) existing.put("progress", input.get("progress").asInt());
            existing.put("updatedAt", System.currentTimeMillis());
            return existing;
        } catch (Exception e) {
            return existing;
        }
    }
    
    private ObjectNode updateTaskStatus(String id, String body) {
        ObjectNode existing = taskStore.get(id);
        if (existing == null) return null;
        
        try {
            ObjectNode input = mapper.readValue(body, ObjectNode.class);
            if (input.has("status")) {
                existing.put("status", input.get("status").asText());
                existing.put("updatedAt", System.currentTimeMillis());
            }
            return existing;
        } catch (Exception e) {
            return existing;
        }
    }
    
    private int clearCompletedTasks() {
        int count = 0;
        Iterator<ObjectNode> it = taskStore.values().iterator();
        while (it.hasNext()) {
            ObjectNode task = it.next();
            if ("COMPLETED".equals(task.get("status").asText()) || "FAILED".equals(task.get("status").asText())) {
                it.remove();
                count++;
            }
        }
        return count;
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
                
                status.put("activeSessions", sessionStore.size());
                status.put("totalTasks", taskStore.size());
                status.put("totalModels", modelStore.size());
                status.put("totalTools", toolStore.size());
                status.put("totalSkills", skillStore.size());
                status.put("totalAgents", agentStore.size());
                status.put("timestamp", System.currentTimeMillis());
                
                sendJson(exchange, 200, status);
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }
}
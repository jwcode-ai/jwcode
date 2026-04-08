package com.jwcode.web;

import com.jwcode.core.tool.ToolRegistry;
import com.jwcode.web.stream.StreamingWebSocketHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
        server.createContext("/api/tools", new ToolsHandler());
        server.createContext("/api/config", new ConfigHandler());
        server.createContext("/static", new StaticHandler());
        
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
     * 首页处理器 - 支持流式响应的现代化界面
     */
    static class IndexHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = generateHTML();
            sendResponse(exchange, 200, html, "text/html");
        }
        
        private String generateHTML() {
            return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>JwCode Web - 流式响应</title>
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                            background: #0d1117;
                            color: #c9d1d9;
                            height: 100vh;
                            display: flex;
                            flex-direction: column;
                        }
                        .header {
                            background: #161b22;
                            padding: 12px 20px;
                            border-bottom: 1px solid #30363d;
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                        }
                        .logo { font-size: 18px; font-weight: 600; color: #58a6ff; display: flex; align-items: center; gap: 8px; }
                        .logo::before { content: "◆"; color: #238636; }
                        .nav { display: flex; gap: 12px; }
                        .nav button {
                            background: #21262d;
                            border: 1px solid #30363d;
                            color: #c9d1d9;
                            padding: 6px 12px;
                            border-radius: 6px;
                            cursor: pointer;
                            font-size: 13px;
                            transition: all 0.2s;
                        }
                        .nav button:hover { background: #30363d; }
                        .main { flex: 1; display: flex; overflow: hidden; }
                        .sidebar {
                            width: 260px;
                            background: #161b22;
                            border-right: 1px solid #30363d;
                            display: flex;
                            flex-direction: column;
                        }
                        .sidebar-header {
                            padding: 16px;
                            border-bottom: 1px solid #30363d;
                        }
                        .new-chat-btn {
                            width: 100%;
                            padding: 10px;
                            background: #238636;
                            border: none;
                            color: white;
                            border-radius: 6px;
                            cursor: pointer;
                            font-size: 14px;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            gap: 6px;
                        }
                        .new-chat-btn:hover { background: #2ea043; }
                        .session-list {
                            flex: 1;
                            overflow-y: auto;
                            padding: 8px;
                        }
                        .session-item {
                            padding: 10px 12px;
                            margin: 4px 0;
                            background: #0d1117;
                            border: 1px solid #21262d;
                            border-radius: 6px;
                            cursor: pointer;
                            font-size: 13px;
                            transition: all 0.2s;
                            display: flex;
                            align-items: center;
                            gap: 8px;
                        }
                        .session-item:hover { background: #21262d; border-color: #30363d; }
                        .session-item.active { background: #1f6feb; border-color: #388bfd; }
                        .session-item::before { content: "💬"; }
                        .sidebar-footer {
                            padding: 16px;
                            border-top: 1px solid #30363d;
                            font-size: 12px;
                            color: #8b949e;
                        }
                        .chat-container {
                            flex: 1;
                            display: flex;
                            flex-direction: column;
                            background: #0d1117;
                        }
                        .messages {
                            flex: 1;
                            overflow-y: auto;
                            padding: 20px;
                            display: flex;
                            flex-direction: column;
                            gap: 16px;
                        }
                        .message {
                            max-width: 85%;
                            padding: 14px 18px;
                            border-radius: 12px;
                            font-size: 14px;
                            line-height: 1.6;
                            animation: fadeIn 0.3s ease;
                        }
                        @keyframes fadeIn {
                            from { opacity: 0; transform: translateY(10px); }
                            to { opacity: 1; transform: translateY(0); }
                        }
                        .message.user {
                            background: #1f6feb;
                            color: white;
                            margin-left: auto;
                            border-bottom-right-radius: 4px;
                        }
                        .message.assistant {
                            background: #161b22;
                            border: 1px solid #30363d;
                            margin-right: auto;
                            border-bottom-left-radius: 4px;
                        }
                        .message.thinking {
                            background: transparent;
                            border: 1px dashed #30363d;
                            color: #8b949e;
                            font-style: italic;
                        }
                        .message.tool-call {
                            background: #21262d;
                            border: 1px solid #388bfd;
                            font-family: 'SF Mono', Monaco, monospace;
                            font-size: 12px;
                        }
                        .tool-call-header {
                            color: #58a6ff;
                            margin-bottom: 8px;
                            display: flex;
                            align-items: center;
                            gap: 6px;
                        }
                        .tool-call-content {
                            background: #0d1117;
                            padding: 10px;
                            border-radius: 6px;
                            overflow-x: auto;
                        }
                        .input-area {
                            padding: 20px;
                            background: #161b22;
                            border-top: 1px solid #30363d;
                        }
                        .input-wrapper {
                            display: flex;
                            gap: 12px;
                            background: #0d1117;
                            border: 1px solid #30363d;
                            border-radius: 12px;
                            padding: 8px;
                        }
                        .input-wrapper:focus-within { border-color: #58a6ff; }
                        .input-area textarea {
                            flex: 1;
                            background: transparent;
                            border: none;
                            color: #c9d1d9;
                            font-size: 14px;
                            resize: none;
                            min-height: 24px;
                            max-height: 200px;
                            outline: none;
                            font-family: inherit;
                        }
                        .input-area textarea::placeholder { color: #8b949e; }
                        .send-btn {
                            background: #238636;
                            border: none;
                            color: white;
                            width: 36px;
                            height: 36px;
                            border-radius: 8px;
                            cursor: pointer;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            transition: all 0.2s;
                            align-self: flex-end;
                        }
                        .send-btn:hover { background: #2ea043; }
                        .send-btn:disabled { background: #21262d; cursor: not-allowed; }
                        .status-bar {
                            background: #161b22;
                            padding: 8px 20px;
                            border-top: 1px solid #30363d;
                            display: flex;
                            justify-content: space-between;
                            font-size: 12px;
                            color: #8b949e;
                        }
                        .status-indicator {
                            display: flex;
                            align-items: center;
                            gap: 6px;
                        }
                        .status-dot {
                            width: 8px;
                            height: 8px;
                            border-radius: 50%;
                            background: #238636;
                        }
                        .status-dot.connecting { background: #d29922; }
                        .status-dot.disconnected { background: #da3633; }
                        .typing-indicator {
                            display: flex;
                            gap: 4px;
                            padding: 4px 0;
                        }
                        .typing-indicator span {
                            width: 8px;
                            height: 8px;
                            background: #8b949e;
                            border-radius: 50%;
                            animation: typing 1.4s infinite;
                        }
                        .typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
                        .typing-indicator span:nth-child(3) { animation-delay: 0.4s; }
                        @keyframes typing {
                            0%, 60%, 100% { transform: translateY(0); }
                            30% { transform: translateY(-10px); }
                        }
                        pre {
                            background: #0d1117;
                            padding: 12px;
                            border-radius: 6px;
                            overflow-x: auto;
                            font-family: 'SF Mono', Monaco, monospace;
                            font-size: 12px;
                        }
                        code {
                            background: #21262d;
                            padding: 2px 6px;
                            border-radius: 4px;
                            font-family: 'SF Mono', Monaco, monospace;
                            font-size: 12px;
                        }
                        
                        /* 日志抽屉样式 */
                        .log-drawer {
                            position: fixed;
                            right: -450px;
                            top: 60px;
                            width: 430px;
                            height: calc(100vh - 70px);
                            background: #161b22;
                            border-left: 1px solid #30363d;
                            border-radius: 12px 0 0 12px;
                            box-shadow: -4px 0 20px rgba(0,0,0,0.5);
                            display: flex;
                            flex-direction: column;
                            transition: right 0.3s ease;
                            z-index: 1000;
                        }
                        .log-drawer.open {
                            right: 10px;
                        }
                        .log-drawer-header {
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            padding: 12px 16px;
                            border-bottom: 1px solid #30363d;
                            background: #0d1117;
                            border-radius: 12px 0 0 0;
                        }
                        .log-drawer-title {
                            display: flex;
                            align-items: center;
                            gap: 8px;
                            font-weight: 600;
                            font-size: 14px;
                        }
                        .log-badge {
                            background: #238636;
                            color: white;
                            font-size: 11px;
                            padding: 2px 6px;
                            border-radius: 10px;
                            min-width: 20px;
                            text-align: center;
                        }
                        .log-badge.new {
                            animation: pulse 1s infinite;
                        }
                        @keyframes pulse {
                            0%, 100% { transform: scale(1); }
                            50% { transform: scale(1.1); }
                        }
                        .log-drawer-actions {
                            display: flex;
                            gap: 8px;
                        }
                        .log-drawer-actions button {
                            background: #21262d;
                            border: 1px solid #30363d;
                            color: #c9d1d9;
                            padding: 4px 8px;
                            border-radius: 6px;
                            cursor: pointer;
                            font-size: 12px;
                            transition: all 0.2s;
                        }
                        .log-drawer-actions button:hover {
                            background: #30363d;
                        }
                        .log-drawer-content {
                            flex: 1;
                            overflow-y: auto;
                            padding: 12px;
                            font-family: 'SF Mono', Monaco, monospace;
                            font-size: 12px;
                            line-height: 1.5;
                        }
                        .log-empty {
                            color: #8b949e;
                            text-align: center;
                            padding: 40px 20px;
                            font-style: italic;
                        }
                        .log-entry {
                            display: flex;
                            gap: 10px;
                            padding: 6px 8px;
                            border-radius: 6px;
                            margin-bottom: 4px;
                            animation: slideIn 0.2s ease;
                        }
                        @keyframes slideIn {
                            from { opacity: 0; transform: translateX(-10px); }
                            to { opacity: 1; transform: translateX(0); }
                        }
                        .log-entry:hover {
                            background: #21262d;
                        }
                        .log-entry.error {
                            background: rgba(248, 81, 73, 0.1);
                            border-left: 3px solid #f85149;
                        }
                        .log-entry.warn {
                            background: rgba(210, 153, 34, 0.1);
                            border-left: 3px solid #d29922;
                        }
                        .log-entry.success {
                            background: rgba(35, 134, 54, 0.1);
                            border-left: 3px solid #238636;
                        }
                        .log-entry.tool {
                            background: rgba(88, 166, 255, 0.1);
                            border-left: 3px solid #58a6ff;
                        }
                        .log-time {
                            color: #8b949e;
                            flex-shrink: 0;
                            font-size: 11px;
                            min-width: 65px;
                        }
                        .log-level {
                            flex-shrink: 0;
                            font-size: 11px;
                            padding: 1px 4px;
                            border-radius: 3px;
                            min-width: 36px;
                            text-align: center;
                        }
                        .log-level.info { background: #1f6feb; color: white; }
                        .log-level.warn { background: #d29922; color: black; }
                        .log-level.error { background: #f85149; color: white; }
                        .log-level.success { background: #238636; color: white; }
                        .log-level.tool { background: #58a6ff; color: black; }
                        .log-message {
                            color: #c9d1d9;
                            word-break: break-word;
                            flex: 1;
                        }
                        .log-drawer-footer {
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            padding: 10px 16px;
                            border-top: 1px solid #30363d;
                            background: #0d1117;
                            font-size: 12px;
                        }
                        .log-autoscroll {
                            display: flex;
                            align-items: center;
                            gap: 6px;
                            cursor: pointer;
                            color: #8b949e;
                        }
                        .log-autoscroll input[type="checkbox"] {
                            accent-color: #238636;
                        }
                        .log-status {
                            color: #8b949e;
                        }
                        .log-status.connected {
                            color: #238636;
                        }
                        .log-status.error {
                            color: #f85149;
                        }
                    </style>
                </head>
                <body>
                    <div class="header">
                        <div class="logo">JwCode Web</div>
                        <div class="nav">
                            <button onclick="toggleLogDrawer()" title="查看后台日志">📋 日志</button>
                            <button onclick="clearChat()">🗑️ 清空</button>
                            <button onclick="exportChat()">📥 导出</button>
                            <button onclick="toggleTheme()">🌙 主题</button>
                        </div>
                    </div>
                    
                    <div class="main">
                        <div class="sidebar">
                            <div class="sidebar-header">
                                <button class="new-chat-btn" onclick="newChat()">
                                    <span>+</span> 新会话
                                </button>
                            </div>
                            <div class="session-list" id="sessionList">
                                <div class="session-item active">新会话</div>
                            </div>
                            <div class="sidebar-footer">
                                <div>⚡ JwCode v1.0.0</div>
                                <div style="margin-top: 4px;">流式响应已启用</div>
                            </div>
                        </div>
                        
                        <div class="chat-container">
                            <div class="messages" id="messages">
                                <div class="message assistant">
                                    你好！我是 JwCode Web，支持流式响应。\n\n我可以帮你：
                                    <br>• 💻 编写和优化代码
                                    <br>• 🔍 搜索和分析信息
                                    <br>• 🛠️ 执行工具和命令
                                    <br>• 📚 解答技术问题
                                </div>
                            </div>
                            
                            <div class="input-area">
                                <div class="input-wrapper">
                                    <textarea id="input" placeholder="输入消息... (Shift+Enter 换行, Enter 发送)" rows="1"></textarea>
                                    <button class="send-btn" id="sendBtn" onclick="sendMessage()">➤</button>
                                </div>
                            </div>
                            
                            <div class="status-bar">
                                <div class="status-indicator">
                                    <span class="status-dot" id="statusDot"></span>
                                    <span id="statusText">已连接</span>
                                </div>
                                <div id="tokenCount">Token: 0 / 8,000</div>
                            </div>
                        </div>
                    </div>
                    
                    <!-- 日志抽屉 -->
                    <div class="log-drawer" id="logDrawer">
                        <div class="log-drawer-header">
                            <div class="log-drawer-title">
                                <span>📋</span>
                                <span>后台日志</span>
                                <span class="log-badge" id="logBadge">0</span>
                            </div>
                            <div class="log-drawer-actions">
                                <button onclick="clearLogs()" title="清空日志">🗑️</button>
                                <button onclick="toggleLogDrawer()" title="关闭">✕</button>
                            </div>
                        </div>
                        <div class="log-drawer-content" id="logContent">
                            <div class="log-empty">暂无日志，点击"📋 日志"按钮开始跟踪</div>
                        </div>
                        <div class="log-drawer-footer">
                            <label class="log-autoscroll">
                                <input type="checkbox" id="logAutoscroll" checked>
                                <span>自动滚动</span>
                            </label>
                            <span class="log-status" id="logStatus">未连接</span>
                        </div>
                    </div>
                    
                    <script>
                        const messagesEl = document.getElementById('messages');
                        const inputEl = document.getElementById('input');
                        const sendBtn = document.getElementById('sendBtn');
                        const statusDot = document.getElementById('statusDot');
                        const statusText = document.getElementById('statusText');
                        
                        let ws = null;
                        let currentSessionId = 'session-' + Date.now();
                        let isGenerating = false;
                        let currentMessageEl = null;
                        
                        // 自动调整输入框高度
                        inputEl.addEventListener('input', function() {
                            this.style.height = 'auto';
                            this.style.height = Math.min(this.scrollHeight, 200) + 'px';
                        });
                        
                        // 连接 WebSocket
                        function connectWebSocket() {
                            const wsUrl = 'ws://' + window.location.hostname + ':8081';
                            ws = new WebSocket(wsUrl);
                            
                            ws.onopen = function() {
                                statusDot.className = 'status-dot';
                                statusText.textContent = '已连接';
                                logStatus.textContent = isLogSubscribed ? '已连接' : '未订阅';
                                logStatus.className = isLogSubscribed ? 'log-status connected' : 'log-status';
                                console.log('WebSocket 连接成功');
                                
                                // 如果抽屉是打开的，重新订阅日志
                                if (logDrawer.classList.contains('open') && !isLogSubscribed) {
                                    subscribeLogs();
                                }
                            };
                            
                            ws.onclose = function() {
                                statusDot.className = 'status-dot disconnected';
                                statusText.textContent = '已断开';
                                logStatus.textContent = '已断开';
                                logStatus.className = 'log-status error';
                                isLogSubscribed = false;
                                console.log('WebSocket 连接关闭');
                                // 3秒后重连
                                setTimeout(connectWebSocket, 3000);
                            };
                            
                            ws.onerror = function(error) {
                                statusDot.className = 'status-dot disconnected';
                                statusText.textContent = '连接错误';
                                logStatus.textContent = '错误';
                                logStatus.className = 'log-status error';
                                console.error('WebSocket 错误:', error);
                            };
                            
                            ws.onmessage = function(event) {
                                handleMessage(JSON.parse(event.data));
                            };
                        }
                        
                        // 处理 WebSocket 消息
                        function handleMessage(msg) {
                            switch(msg.type) {
                                case 'connected':
                                    console.log('服务器:', msg.data);
                                    break;
                                case 'start':
                                    isGenerating = true;
                                    sendBtn.disabled = true;
                                    currentMessageEl = createMessageElement('', 'assistant');
                                    break;
                                case 'content':
                                    if (currentMessageEl) {
                                        appendContent(currentMessageEl, msg.data);
                                    }
                                    break;
                                case 'thinking':
                                    showThinking(msg.data);
                                    break;
                                case 'tool_call':
                                    try {
                                        showToolCall(JSON.parse(msg.data));
                                    } catch (e) {
                                        // 如果解析失败，直接显示原始数据
                                        showToolCall({name: 'Unknown', args: msg.data});
                                    }
                                    break;
                                case 'complete':
                                    isGenerating = false;
                                    sendBtn.disabled = false;
                                    currentMessageEl = null;
                                    break;
                                case 'error':
                                    isGenerating = false;
                                    sendBtn.disabled = false;
                                    addMessage('❌ 错误: ' + msg.data, 'assistant');
                                    break;
                            }
                        }
                        
                        // 创建消息元素
                        function createMessageElement(text, type) {
                            const div = document.createElement('div');
                            div.className = 'message ' + type;
                            div.innerHTML = text;
                            messagesEl.appendChild(div);
                            scrollToBottom();
                            return div;
                        }
                        
                        // 追加内容
                        function appendContent(el, text) {
                            el.innerHTML += text.replace(/\\n/g, '<br>');
                            scrollToBottom();
                        }
                        
                        // 显示思考过程
                        function showThinking(text) {
                            const div = document.createElement('div');
                            div.className = 'message thinking';
                            div.innerHTML = '🤔 ' + text;
                            messagesEl.appendChild(div);
                            scrollToBottom();
                        }
                        
                        // 显示工具调用
                        function showToolCall(data) {
                            const div = document.createElement('div');
                            div.className = 'message tool-call';
                            div.innerHTML = `
                                <div class="tool-call-header">🔧 工具调用: ${data.name}</div>
                                <div class="tool-call-content"><pre>${JSON.stringify(data.args, null, 2)}</pre></div>
                            `;
                            messagesEl.appendChild(div);
                            scrollToBottom();
                        }
                        
                        // 添加消息
                        function addMessage(text, type) {
                            createMessageElement(text, type);
                        }
                        
                        // 发送消息
                        function sendMessage() {
                            const text = inputEl.value.trim();
                            if (!text || isGenerating) return;
                            
                            addMessage(text, 'user');
                            inputEl.value = '';
                            inputEl.style.height = 'auto';
                            
                            if (ws && ws.readyState === WebSocket.OPEN) {
                                ws.send(JSON.stringify({
                                    type: 'chat',
                                    sessionId: currentSessionId,
                                    message: text
                                }));
                            } else {
                                addMessage('⚠️ WebSocket 未连接，请刷新页面重试', 'assistant');
                            }
                        }
                        
                        // 滚动到底部
                        function scrollToBottom() {
                            messagesEl.scrollTop = messagesEl.scrollHeight;
                        }
                        
                        // 键盘事件
                        inputEl.addEventListener('keydown', (e) => {
                            if (e.key === 'Enter' && !e.shiftKey) {
                                e.preventDefault();
                                sendMessage();
                            }
                        });
                        
                        // 新会话
                        function newChat() {
                            currentSessionId = 'session-' + Date.now();
                            messagesEl.innerHTML = `
                                <div class="message assistant">
                                    新会话已创建。有什么我可以帮你的吗？
                                </div>
                            `;
                        }
                        
                        // 清空对话
                        function clearChat() {
                            if (confirm('确定要清空当前对话吗？')) {
                                messagesEl.innerHTML = '';
                            }
                        }
                        
                        // 导出对话
                        function exportChat() {
                            const messages = Array.from(messagesEl.children).map(el => el.textContent).join('\\n\\n');
                            const blob = new Blob([messages], { type: 'text/plain' });
                            const url = URL.createObjectURL(blob);
                            const a = document.createElement('a');
                            a.href = url;
                            a.download = 'chat-' + currentSessionId + '.txt';
                            a.click();
                        }
                        
                        // 切换主题
                        function toggleTheme() {
                            // 主题切换功能
                            alert('主题切换功能开发中...');
                        }
                        
                        // 初始化
                        connectWebSocket();
                        
                        // ============ 日志抽屉功能 ============
                        
                        const logDrawer = document.getElementById('logDrawer');
                        const logContent = document.getElementById('logContent');
                        const logBadge = document.getElementById('logBadge');
                        const logStatus = document.getElementById('logStatus');
                        const logAutoscroll = document.getElementById('logAutoscroll');
                        
                        let logCount = 0;
                        let isLogSubscribed = false;
                        let unreadLogCount = 0;
                        
                        // 切换日志抽屉
                        function toggleLogDrawer() {
                            logDrawer.classList.toggle('open');
                            if (logDrawer.classList.contains('open')) {
                                // 打开时订阅日志
                                if (!isLogSubscribed) {
                                    subscribeLogs();
                                }
                                unreadLogCount = 0;
                                updateLogBadge();
                            }
                        }
                        
                        // 订阅日志
                        function subscribeLogs() {
                            if (ws && ws.readyState === WebSocket.OPEN) {
                                ws.send(JSON.stringify({type: 'subscribe_logs'}));
                                isLogSubscribed = true;
                                logStatus.textContent = '已连接';
                                logStatus.className = 'log-status connected';
                            }
                        }
                        
                        // 取消订阅日志
                        function unsubscribeLogs() {
                            if (ws && ws.readyState === WebSocket.OPEN) {
                                ws.send(JSON.stringify({type: 'unsubscribe_logs'}));
                                isLogSubscribed = false;
                                logStatus.textContent = '未连接';
                                logStatus.className = 'log-status';
                            }
                        }
                        
                        // 添加日志条目
                        function addLogEntry(level, source, message, timestamp) {
                            // 如果之前是空的，清空内容
                            if (logCount === 0) {
                                logContent.innerHTML = '';
                            }
                            
                            logCount++;
                            
                            const time = timestamp ? new Date(timestamp).toLocaleTimeString('zh-CN', {
                                hour: '2-digit',
                                minute: '2-digit',
                                second: '2-digit'
                            }) : new Date().toLocaleTimeString('zh-CN', {
                                hour: '2-digit',
                                minute: '2-digit',
                                second: '2-digit'
                            });
                            
                            const entry = document.createElement('div');
                            entry.className = `log-entry ${level}`;
                            entry.innerHTML = `
                                <span class="log-time">${time}</span>
                                <span class="log-level ${level}">${level.toUpperCase()}</span>
                                <span class="log-message">${escapeHtml(message)}</span>
                            `;
                            
                            logContent.appendChild(entry);
                            
                            // 限制日志条数
                            while (logContent.children.length > 500) {
                                logContent.removeChild(logContent.firstChild);
                            }
                            
                            // 自动滚动
                            if (logAutoscroll.checked) {
                                logContent.scrollTop = logContent.scrollHeight;
                            }
                            
                            // 如果抽屉未打开，增加未读计数
                            if (!logDrawer.classList.contains('open')) {
                                unreadLogCount++;
                                updateLogBadge();
                            }
                        }
                        
                        // 更新日志徽章
                        function updateLogBadge() {
                            logBadge.textContent = unreadLogCount > 0 ? unreadLogCount : logCount;
                            if (unreadLogCount > 0) {
                                logBadge.classList.add('new');
                            } else {
                                logBadge.classList.remove('new');
                            }
                        }
                        
                        // 清空日志
                        function clearLogs() {
                            logContent.innerHTML = '<div class="log-empty">暂无日志</div>';
                            logCount = 0;
                            unreadLogCount = 0;
                            updateLogBadge();
                        }
                        
                        // HTML 转义
                        function escapeHtml(text) {
                            const div = document.createElement('div');
                            div.textContent = text;
                            return div.innerHTML;
                        }
                        
                        // 扩展原有的 handleMessage 函数来处理日志
                        const originalHandleMessage = handleMessage;
                        handleMessage = function(msg) {
                            // 处理日志消息
                            if (msg.type === 'log') {
                                try {
                                    const logData = typeof msg.data === 'string' ? JSON.parse(msg.data) : msg.data;
                                    addLogEntry(
                                        logData.level || 'info',
                                        logData.source || 'System',
                                        logData.message || '',
                                        logData.timestamp
                                    );
                                } catch (e) {
                                    console.error('解析日志消息失败:', e);
                                }
                                return;
                            }
                            
                            // 调用原函数处理其他消息
                            originalHandleMessage(msg);
                        };
                    </script>
                </body>
                </html>
                """;
        }
    }
    
    /**
     * 配置处理器
     */
    static class ConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            
            if ("GET".equals(method)) {
                // 返回当前配置
                String json = """
                    {
                        "streaming": true,
                        "websocketPort": 8081,
                        "version": "1.0.0"
                    }
                    """;
                sendResponse(exchange, 200, json, "application/json");
            } else {
                sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            }
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
    
    public int getPort() { return port; }
    public int getWsPort() { return wsPort; }
}

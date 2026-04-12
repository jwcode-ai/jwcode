package com.jwcode.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 静态资源处理器 - 提供 Markdown 渲染支持的前端界面
 * 
 * 参考 JWClaude 的设计风格：
 * - 使用 marked.js 解析 Markdown
 * - 使用 highlight.js 进行代码高亮
 * - 美观的暗色主题
 * - 消息气泡区分用户和助手
 */
public class StaticHandler implements HttpHandler {
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        
        if (path.endsWith(".css")) {
            serveCSS(exchange);
        } else if (path.endsWith(".js")) {
            serveJS(exchange);
        } else {
            send404(exchange);
        }
    }
    
    private void serveCSS(HttpExchange exchange) throws IOException {
        String css = """
            /* JWCode Web UI - 参考 JWClaude 设计风格 */
            :root {
                --bg-primary: #0d1117;
                --bg-secondary: #161b22;
                --bg-tertiary: #21262d;
                --border-color: #30363d;
                --text-primary: #e6edf3;
                --text-secondary: #7d8590;
                --accent-color: #2f81f7;
                --accent-hover: #388bfd;
                --user-msg-bg: #1f6feb;
                --assistant-msg-bg: #21262d;
                --code-bg: #161b22;
                --success-color: #3fb950;
                --warning-color: #d29922;
                --error-color: #f85149;
            }
            
            * {
                margin: 0;
                padding: 0;
                box-sizing: border-box;
            }
            
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans', Helvetica, Arial, sans-serif;
                background: var(--bg-primary);
                color: var(--text-primary);
                height: 100vh;
                overflow: hidden;
                line-height: 1.6;
            }
            
            /* Header */
            .header {
                background: var(--bg-secondary);
                padding: 16px 24px;
                border-bottom: 1px solid var(--border-color);
                display: flex;
                align-items: center;
                justify-content: space-between;
            }
            
            .header h1 {
                font-size: 18px;
                font-weight: 600;
                display: flex;
                align-items: center;
                gap: 10px;
            }
            
            .header h1::before {
                content: "◆";
                color: var(--accent-color);
            }
            
            .header-actions {
                display: flex;
                gap: 12px;
            }
            
            .header-btn {
                padding: 6px 12px;
                background: var(--bg-tertiary);
                border: 1px solid var(--border-color);
                border-radius: 6px;
                color: var(--text-primary);
                font-size: 12px;
                cursor: pointer;
                transition: all 0.2s;
            }
            
            .header-btn:hover {
                background: var(--border-color);
            }
            
            /* Main Layout */
            .main {
                display: flex;
                height: calc(100vh - 60px);
            }
            
            /* Sidebar */
            .sidebar {
                width: 260px;
                background: var(--bg-secondary);
                border-right: 1px solid var(--border-color);
                display: flex;
                flex-direction: column;
            }
            
            .sidebar-header {
                padding: 16px;
                border-bottom: 1px solid var(--border-color);
            }
            
            .new-chat-btn {
                width: 100%;
                padding: 10px;
                background: var(--accent-color);
                border: none;
                border-radius: 6px;
                color: white;
                font-size: 14px;
                cursor: pointer;
                transition: background 0.2s;
            }
            
            .new-chat-btn:hover {
                background: var(--accent-hover);
            }
            
            .session-list {
                flex: 1;
                overflow-y: auto;
                padding: 8px;
            }
            
            .session-item {
                padding: 10px 12px;
                border-radius: 6px;
                cursor: pointer;
                font-size: 13px;
                color: var(--text-secondary);
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
                transition: all 0.2s;
            }
            
            .session-item:hover {
                background: var(--bg-tertiary);
                color: var(--text-primary);
            }
            
            .session-item.active {
                background: var(--bg-tertiary);
                color: var(--text-primary);
            }
            
            /* Chat Area */
            .chat-container {
                flex: 1;
                display: flex;
                flex-direction: column;
                background: var(--bg-primary);
            }
            
            .messages {
                flex: 1;
                overflow-y: auto;
                padding: 20px 0;
            }
            
            /* Message Styles */
            .message {
                display: flex;
                padding: 16px 24px;
                gap: 16px;
            }
            
            .message.user {
                background: var(--bg-primary);
            }
            
            .message.assistant {
                background: var(--bg-secondary);
                border-bottom: 1px solid var(--border-color);
            }
            
            .message-avatar {
                width: 32px;
                height: 32px;
                border-radius: 6px;
                display: flex;
                align-items: center;
                justify-content: center;
                font-size: 14px;
                flex-shrink: 0;
            }
            
            .message.user .message-avatar {
                background: var(--user-msg-bg);
            }
            
            .message.assistant .message-avatar {
                background: var(--success-color);
            }
            
            .message-content {
                flex: 1;
                max-width: 800px;
                font-size: 14px;
                line-height: 1.7;
            }
            
            /* Markdown Styles */
            .message-content h1,
            .message-content h2,
            .message-content h3,
            .message-content h4 {
                margin: 20px 0 12px;
                font-weight: 600;
                color: var(--text-primary);
            }
            
            .message-content h1 { font-size: 22px; border-bottom: 1px solid var(--border-color); padding-bottom: 8px; }
            .message-content h2 { font-size: 18px; border-bottom: 1px solid var(--border-color); padding-bottom: 6px; }
            .message-content h3 { font-size: 16px; }
            .message-content h4 { font-size: 14px; }
            
            .message-content p {
                margin: 8px 0;
            }
            
            .message-content ul,
            .message-content ol {
                margin: 8px 0;
                padding-left: 24px;
            }
            
            .message-content li {
                margin: 4px 0;
            }
            
            .message-content code {
                background: var(--bg-tertiary);
                padding: 2px 6px;
                border-radius: 4px;
                font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
                font-size: 85%;
                color: var(--text-primary);
            }
            
            .message-content pre {
                background: var(--code-bg);
                border: 1px solid var(--border-color);
                border-radius: 8px;
                padding: 16px;
                margin: 12px 0;
                overflow-x: auto;
                position: relative;
            }
            
            .message-content pre code {
                background: transparent;
                padding: 0;
                font-size: 13px;
                line-height: 1.5;
                color: var(--text-primary);
            }
            
            /* Code Block Header */
            .code-block-wrapper {
                position: relative;
                margin: 12px 0;
            }
            
            .code-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                padding: 8px 16px;
                background: var(--bg-tertiary);
                border: 1px solid var(--border-color);
                border-bottom: none;
                border-radius: 8px 8px 0 0;
                font-size: 12px;
                color: var(--text-secondary);
            }
            
            .code-header .lang-label {
                text-transform: uppercase;
                font-weight: 600;
            }
            
            .copy-btn {
                padding: 4px 8px;
                background: transparent;
                border: 1px solid var(--border-color);
                border-radius: 4px;
                color: var(--text-secondary);
                font-size: 11px;
                cursor: pointer;
                transition: all 0.2s;
            }
            
            .copy-btn:hover {
                background: var(--border-color);
                color: var(--text-primary);
            }
            
            .copy-btn.copied {
                background: var(--success-color);
                border-color: var(--success-color);
                color: white;
            }
            
            .code-block-wrapper pre {
                margin: 0;
                border-radius: 0 0 8px 8px;
            }
            
            /* Highlight.js Custom Theme (GitHub Dark style) */
            .hljs {
                background: transparent !important;
                color: var(--text-primary);
            }
            
            .hljs-keyword { color: #ff7b72; }
            .hljs-string { color: #a5d6ff; }
            .hljs-number { color: #79c0ff; }
            .hljs-comment { color: #8b949e; font-style: italic; }
            .hljs-function { color: #d2a8ff; }
            .hljs-class { color: #ffa657; }
            .hljs-variable { color: #ffa657; }
            .hljs-operator { color: #ff7b72; }
            .hljs-punctuation { color: #c9d1d9; }
            
            /* Tables */
            .message-content table {
                width: 100%;
                border-collapse: collapse;
                margin: 12px 0;
                font-size: 13px;
            }
            
            .message-content th,
            .message-content td {
                padding: 8px 12px;
                border: 1px solid var(--border-color);
                text-align: left;
            }
            
            .message-content th {
                background: var(--bg-tertiary);
                font-weight: 600;
            }
            
            .message-content tr:nth-child(even) {
                background: var(--bg-secondary);
            }
            
            /* Blockquote */
            .message-content blockquote {
                border-left: 4px solid var(--accent-color);
                padding-left: 16px;
                margin: 12px 0;
                color: var(--text-secondary);
            }
            
            /* Links */
            .message-content a {
                color: var(--accent-color);
                text-decoration: none;
            }
            
            .message-content a:hover {
                text-decoration: underline;
            }
            
            /* Horizontal Rule */
            .message-content hr {
                border: none;
                border-top: 1px solid var(--border-color);
                margin: 20px 0;
            }
            
            /* Input Area */
            .input-container {
                padding: 16px 24px;
                background: var(--bg-secondary);
                border-top: 1px solid var(--border-color);
            }
            
            .input-wrapper {
                max-width: 800px;
                margin: 0 auto;
                display: flex;
                gap: 12px;
                align-items: flex-end;
            }
            
            .input-box {
                flex: 1;
                background: var(--bg-tertiary);
                border: 1px solid var(--border-color);
                border-radius: 12px;
                padding: 12px 16px;
                color: var(--text-primary);
                font-size: 14px;
                resize: none;
                min-height: 52px;
                max-height: 200px;
                font-family: inherit;
            }
            
            .input-box:focus {
                outline: none;
                border-color: var(--accent-color);
            }
            
            .input-box::placeholder {
                color: var(--text-secondary);
            }
            
            .send-btn {
                padding: 12px 20px;
                background: var(--accent-color);
                border: none;
                border-radius: 8px;
                color: white;
                font-size: 14px;
                font-weight: 500;
                cursor: pointer;
                transition: background 0.2s;
                display: flex;
                align-items: center;
                gap: 6px;
            }
            
            .send-btn:hover {
                background: var(--accent-hover);
            }
            
            .send-btn:disabled {
                background: var(--bg-tertiary);
                color: var(--text-secondary);
                cursor: not-allowed;
            }
            
            /* Loading Indicator */
            .typing-indicator {
                display: none;
                padding: 16px 24px;
                gap: 16px;
            }
            
            .typing-indicator.active {
                display: flex;
            }
            
            .typing-dots {
                display: flex;
                gap: 4px;
                align-items: center;
            }
            
            .typing-dots span {
                width: 8px;
                height: 8px;
                background: var(--text-secondary);
                border-radius: 50%;
                animation: typing 1.4s infinite;
            }
            
            .typing-dots span:nth-child(2) { animation-delay: 0.2s; }
            .typing-dots span:nth-child(3) { animation-delay: 0.4s; }
            
            @keyframes typing {
                0%, 60%, 100% { transform: translateY(0); }
                30% { transform: translateY(-10px); }
            }
            
            /* Scrollbar */
            ::-webkit-scrollbar {
                width: 8px;
                height: 8px;
            }
            
            ::-webkit-scrollbar-track {
                background: var(--bg-primary);
            }
            
            ::-webkit-scrollbar-thumb {
                background: var(--border-color);
                border-radius: 4px;
            }
            
            ::-webkit-scrollbar-thumb:hover {
                background: var(--text-secondary);
            }
            
            /* Tool Call Styles */
            .tool-call {
                background: var(--bg-tertiary);
                border: 1px solid var(--border-color);
                border-radius: 8px;
                padding: 12px;
                margin: 12px 0;
            }
            
            .tool-call-header {
                display: flex;
                align-items: center;
                gap: 8px;
                font-size: 13px;
                color: var(--text-secondary);
                margin-bottom: 8px;
            }
            
            .tool-call-header .tool-icon {
                color: var(--warning-color);
            }
            
            .tool-call-content {
                font-family: 'SFMono-Regular', Consolas, monospace;
                font-size: 12px;
                background: var(--code-bg);
                padding: 10px;
                border-radius: 6px;
                overflow-x: auto;
            }
            
            /* Thinking Section */
            .thinking {
                background: linear-gradient(90deg, var(--bg-tertiary) 0%, transparent 100%);
                border-left: 3px solid var(--accent-color);
                padding: 12px 16px;
                margin: 12px 0;
                border-radius: 0 8px 8px 0;
            }
            
            .thinking-header {
                font-size: 12px;
                color: var(--accent-color);
                font-weight: 600;
                margin-bottom: 8px;
                display: flex;
                align-items: center;
                gap: 6px;
            }
            
            .thinking-content {
                font-size: 13px;
                color: var(--text-secondary);
                font-style: italic;
            }
            """;
        
        sendResponse(exchange, 200, css, "text/css");
    }
    
    private void serveJS(HttpExchange exchange) throws IOException {
        String js = """
            // JWCode Web Frontend - Markdown Support
            
            // Configure marked.js
            marked.setOptions({
                highlight: function(code, lang) {
                    const language = hljs.getLanguage(lang) ? lang : 'plaintext';
                    return hljs.highlight(code, { language }).value;
                },
                langPrefix: 'hljs language-',
                breaks: true,
                gfm: true
            });
            
            // Custom renderer for code blocks with copy button
            const renderer = new marked.Renderer();
            
            renderer.code = function(code, language) {
                const validLang = language && hljs.getLanguage(language) ? language : 'plaintext';
                const highlighted = hljs.highlight(code, { language: validLang }).value;
                
                return `
                    <div class="code-block-wrapper">
                        <div class="code-header">
                            <span class="lang-label">${validLang}</span>
                            <button class="copy-btn" onclick="copyCode(this)">复制</button>
                        </div>
                        <pre><code class="hljs language-${validLang}">${highlighted}</code></pre>
                    </div>
                `;
            };
            
            marked.use({ renderer });
            
            // Copy code function
            function copyCode(btn) {
                const codeBlock = btn.closest('.code-block-wrapper').querySelector('code');
                const code = codeBlock.textContent;
                
                navigator.clipboard.writeText(code).then(() => {
                    btn.textContent = '已复制!';
                    btn.classList.add('copied');
                    setTimeout(() => {
                        btn.textContent = '复制';
                        btn.classList.remove('copied');
                    }, 2000);
                });
            }
            
            // Add message to UI with Markdown rendering
            function addMessage(text, role, isThinking = false) {
                const messages = document.getElementById('messages');
                const messageDiv = document.createElement('div');
                messageDiv.className = `message ${role}`;
                
                const avatar = role === 'user' ? '👤' : '🤖';
                
                // Parse markdown for assistant messages
                let contentHtml;
                if (role === 'assistant' && !isThinking) {
                    contentHtml = marked.parse(text);
                } else {
                    contentHtml = `<p>${escapeHtml(text)}</p>`;
                }
                
                messageDiv.innerHTML = `
                    <div class="message-avatar">${avatar}</div>
                    <div class="message-content">${contentHtml}</div>
                `;
                
                messages.appendChild(messageDiv);
                messages.scrollTop = messages.scrollHeight;
                
                return messageDiv;
            }
            
            // Add thinking section
            function addThinking(text, parentMessage) {
                const thinkingDiv = document.createElement('div');
                thinkingDiv.className = 'thinking';
                thinkingDiv.innerHTML = `
                    <div class="thinking-header">💭 思考过程</div>
                    <div class="thinking-content">${escapeHtml(text)}</div>
                `;
                parentMessage.querySelector('.message-content').appendChild(thinkingDiv);
            }
            
            // Add tool call display
            function addToolCall(toolName, args, result, parentMessage) {
                const toolDiv = document.createElement('div');
                toolDiv.className = 'tool-call';
                toolDiv.innerHTML = `
                    <div class="tool-call-header">
                        <span class="tool-icon">🔧</span>
                        <span>使用工具: ${escapeHtml(toolName)}</span>
                    </div>
                    <div class="tool-call-content">${escapeHtml(JSON.stringify(args, null, 2))}</div>
                `;
                parentMessage.querySelector('.message-content').appendChild(toolDiv);
            }
            
            // Escape HTML
            function escapeHtml(text) {
                const div = document.createElement('div');
                div.textContent = text;
                return div.innerHTML;
            }
            
            // Send message
            async function sendMessage() {
                const input = document.getElementById('input');
                const message = input.value.trim();
                if (!message) return;
                
                // Add user message
                addMessage(message, 'user');
                input.value = '';
                input.style.height = 'auto';
                
                // Show typing indicator
                showTyping(true);
                
                try {
                    const response = await fetch('/api/chat', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ message, sessionId: 'default' })
                    });
                    
                    const data = await response.json();
                    showTyping(false);
                    
                    if (data.success) {
                        // Support different response types
                        if (data.thinking) {
                            const msgDiv = addMessage(data.message || '', 'assistant');
                            addThinking(data.thinking, msgDiv);
                        } else if (data.toolCalls) {
                            const msgDiv = addMessage(data.message || '', 'assistant');
                            data.toolCalls.forEach(tc => addToolCall(tc.name, tc.args, tc.result, msgDiv));
                        } else {
                            addMessage(data.message, 'assistant');
                        }
                    } else {
                        addMessage('❌ 错误: ' + data.error, 'assistant');
                    }
                } catch (e) {
                    showTyping(false);
                    addMessage('❌ 网络错误: ' + e.message, 'assistant');
                }
            }
            
            // Show/hide typing indicator
            function showTyping(show) {
                const indicator = document.getElementById('typing-indicator');
                if (show) {
                    indicator.classList.add('active');
                } else {
                    indicator.classList.remove('active');
                }
            }
            
            // Handle Enter key
            document.addEventListener('DOMContentLoaded', () => {
                const input = document.getElementById('input');
                
                input.addEventListener('keydown', (e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                        e.preventDefault();
                        sendMessage();
                    }
                });
                
                // Auto-resize textarea
                input.addEventListener('input', () => {
                    input.style.height = 'auto';
                    input.style.height = Math.min(input.scrollHeight, 200) + 'px';
                });
            });
            
            // Load session list
            async function loadSessions() {
                try {
                    const response = await fetch('/api/sessions');
                    const data = await response.json();
                    
                    const list = document.getElementById('session-list');
                    list.innerHTML = '';
                    
                    if (data.sessions) {
                        data.sessions.forEach(session => {
                            const div = document.createElement('div');
                            div.className = 'session-item';
                            div.textContent = session.title || '新会话';
                            div.onclick = () => loadSession(session.id);
                            list.appendChild(div);
                        });
                    }
                } catch (e) {
                    console.error('Failed to load sessions:', e);
                }
            }
            
            // Load specific session
            async function loadSession(sessionId) {
                try {
                    const response = await fetch(`/api/sessions/${sessionId}`);
                    const data = await response.json();
                    
                    if (data.success && data.messages) {
                        const messages = document.getElementById('messages');
                        messages.innerHTML = '';
                        
                        data.messages.forEach(msg => {
                            addMessage(msg.content, msg.role);
                        });
                    }
                } catch (e) {
                    console.error('Failed to load session:', e);
                }
            }
            
            // New chat
            function newChat() {
                document.getElementById('messages').innerHTML = '';
                // Add welcome message
                addMessage('你好！我是 JWCode，你的 AI 编程助手。\\n\\n我可以帮助你：\\n\\n- 📝 编写和修改代码\\n- 🔍 搜索和分析项目文件\\n- 🐛 调试和解决问题\\n- 📚 查找文档和学习资源\\n- ⚙️ 执行各种开发任务\\n\\n请告诉我你需要什么帮助！', 'assistant');
            }
            
            // Initialize
            document.addEventListener('DOMContentLoaded', () => {
                loadSessions();
                newChat();
            });
            """;
        
        sendResponse(exchange, 200, js, "application/javascript");
    }
    
    private void send404(HttpExchange exchange) throws IOException {
        sendResponse(exchange, 404, "Not Found", "text/plain");
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String content, String contentType) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}

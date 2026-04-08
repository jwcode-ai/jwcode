package com.jwcode.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 静态资源处理器
 */
public class StaticHandler implements HttpHandler {
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        
        // 简化实现：返回基本的 CSS/JS
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
            body { margin: 0; font-family: sans-serif; background: #1a1a2e; color: #eee; }
            .header { background: #16213e; padding: 15px; border-bottom: 1px solid #0f3460; }
            .main { display: flex; height: calc(100vh - 60px); }
            .sidebar { width: 250px; background: #16213e; padding: 15px; }
            .chat { flex: 1; padding: 20px; display: flex; flex-direction: column; }
            .messages { flex: 1; overflow-y: auto; background: #16213e; border-radius: 8px; padding: 15px; }
            .input-area { display: flex; gap: 10px; margin-top: 15px; }
            input { flex: 1; padding: 12px; border-radius: 8px; border: 1px solid #0f3460; background: #1a1a2e; color: #eee; }
            button { padding: 12px 25px; background: #e94560; color: white; border: none; border-radius: 8px; cursor: pointer; }
            """;
        
        sendResponse(exchange, 200, css, "text/css");
    }
    
    private void serveJS(HttpExchange exchange) throws IOException {
        String js = """
            console.log('JwCode Web loaded');
            
            async function sendMessage() {
                const input = document.getElementById('input');
                const message = input.value.trim();
                if (!message) return;
                
                // Add user message to UI
                addMessage(message, 'user');
                input.value = '';
                
                try {
                    const response = await fetch('/api/chat', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ message, sessionId: 'default' })
                    });
                    
                    const data = await response.json();
                    if (data.success) {
                        addMessage(data.message, 'assistant');
                    } else {
                        addMessage('Error: ' + data.error, 'assistant');
                    }
                } catch (e) {
                    addMessage('Error: ' + e.message, 'assistant');
                }
            }
            
            function addMessage(text, role) {
                const messages = document.getElementById('messages');
                const div = document.createElement('div');
                div.className = 'message ' + role;
                div.textContent = text;
                messages.appendChild(div);
                messages.scrollTop = messages.scrollHeight;
            }
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

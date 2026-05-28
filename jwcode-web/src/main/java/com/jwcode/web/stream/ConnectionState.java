package com.jwcode.web.stream;

import org.java_websocket.WebSocket;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * 待重放的消息体（连接断开时暂存，重连后重放）。
 */
record PendingMessage(StreamingWebSocketHandler.MessageType type, String data) {
    PendingMessage {
        // compact canonical constructor
    }
}

/**
 * ConnectionState — WebSocket 连接管理委托类。
 *
 * <p>从 StreamingWebSocketHandler 提取，封装所有连接跟踪状态：
 * 认证、心跳、会话绑定、消息暂存、日志监听、查询 Future 跟踪。</p>
 */
class ConnectionState {
    private static final Logger logger = Logger.getLogger(ConnectionState.class.getName());
    static final long PING_INTERVAL_MS = 30_000;
    static final long PONG_TIMEOUT_MS = 90_000;
    static final long CONNECTION_TIMEOUT_MS = 300_000;
    static final int MAX_PENDING_MESSAGES = 200;

    final Map<WebSocket, Boolean> authenticated = new ConcurrentHashMap<>();
    final Map<WebSocket, String> sessionBindings = new ConcurrentHashMap<>();
    final Map<String, WebSocket> activeSessions = new ConcurrentHashMap<>();
    final Map<WebSocket, Long> lastPongTime = new ConcurrentHashMap<>();
    final Map<WebSocket, Long> lastActivityTime = new ConcurrentHashMap<>();
    final Map<WebSocket, Consumer<StreamingWebSocketHandler.LogEntry>> logListeners = new ConcurrentHashMap<>();
    final Map<String, Future<?>> runningQueryFutures = new ConcurrentHashMap<>();
    final Set<String> pausedQuerySessions = ConcurrentHashMap.newKeySet();
    final Map<String, Queue<PendingMessage>> pendingMessages = new ConcurrentHashMap<>();

    void markAuthenticated(WebSocket conn) { authenticated.put(conn, true); }
    boolean isAuthenticated(WebSocket conn) { return Boolean.TRUE.equals(authenticated.get(conn)); }
    void removeConnection(WebSocket conn) {
        authenticated.remove(conn);
        String sid = sessionBindings.remove(conn);
        if (sid != null) {
            activeSessions.remove(sid, conn);
        }
        lastPongTime.remove(conn);
        lastActivityTime.remove(conn);
        logListeners.remove(conn);
    }

    void bindSession(WebSocket conn, String sessionId) {
        sessionBindings.put(conn, sessionId);
        activeSessions.put(sessionId, conn);
    }

    WebSocket getConnection(String sessionId) { return activeSessions.get(sessionId); }

    void touchActivity(WebSocket conn) { lastActivityTime.put(conn, System.currentTimeMillis()); }
    void touchPong(WebSocket conn) { lastPongTime.put(conn, System.currentTimeMillis()); }

    boolean isPongTimeout(WebSocket conn) {
        Long last = lastPongTime.get(conn);
        return last != null && System.currentTimeMillis() - last > PONG_TIMEOUT_MS;
    }

    boolean isConnectionTimeout(WebSocket conn) {
        Long last = lastActivityTime.get(conn);
        return last != null && System.currentTimeMillis() - last > CONNECTION_TIMEOUT_MS;
    }

    void registerLogListener(WebSocket conn, Consumer<StreamingWebSocketHandler.LogEntry> listener) {
        logListeners.put(conn, listener);
    }

    void broadcastLog(StreamingWebSocketHandler.LogEntry entry) {
        for (Consumer<StreamingWebSocketHandler.LogEntry> listener : logListeners.values()) {
            try { listener.accept(entry); } catch (Exception ignored) {}
        }
    }

    void queueMessage(String sessionId, String type, String data) {
        Queue<PendingMessage> queue = pendingMessages.computeIfAbsent(
            sessionId, k -> new ConcurrentLinkedQueue<>());
        while (queue.size() >= MAX_PENDING_MESSAGES) queue.poll();
        queue.offer(new PendingMessage(
            StreamingWebSocketHandler.MessageType.valueOf(type), data));
    }

    Queue<PendingMessage> drainPending(String sessionId) {
        return pendingMessages.remove(sessionId);
    }

    boolean hasPending(String sessionId) {
        Queue<PendingMessage> q = pendingMessages.get(sessionId);
        return q != null && !q.isEmpty();
    }

    void trackQueryFuture(String sessionId, Future<?> future) {
        runningQueryFutures.put(sessionId, future);
    }

    void cancelQuery(String sessionId) {
        Future<?> f = runningQueryFutures.remove(sessionId);
        if (f != null) f.cancel(true);
    }

    boolean isPaused(String sessionId) { return pausedQuerySessions.contains(sessionId); }
    void markPaused(String sessionId) { pausedQuerySessions.add(sessionId); }
    void markResumed(String sessionId) { pausedQuerySessions.remove(sessionId); }

    int activeCount() { return (int) authenticated.values().stream().filter(Boolean.TRUE::equals).count(); }
}

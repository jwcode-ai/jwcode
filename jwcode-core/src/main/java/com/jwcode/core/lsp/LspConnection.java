package com.jwcode.core.lsp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LspConnection - LSP 连接管理器
 * 
 * 功能说明：
 * 管理与语言服务器的连接，提供连接生命周期管理、重连机制和心跳检测。
 * 确保与服务器的稳定通信，自动处理连接中断和恢复。
 * 
 * 核心特性：
 * - 连接生命周期管理（建立、维护、关闭）
 * - 自动重连机制（指数退避）
 * - 心跳检测
 * - 连接状态监控
 * - 优雅关闭
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LspConnection {
    
    private static final Logger LOGGER = Logger.getLogger(LspConnection.class.getName());
    
    // 连接配置常量
    private static final int INITIAL_RECONNECT_DELAY_MS = 1000;
    private static final int MAX_RECONNECT_DELAY_MS = 30000;
    private static final int RECONNECT_MULTIPLIER = 2;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int HEARTBEAT_INTERVAL_MS = 30000;
    private static final int CONNECTION_TIMEOUT_MS = 10000;
    
    /**
     * 连接状态枚举
     */
    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        ERROR
    }
    
    /**
     * 服务器进程
     */
    private final Process serverProcess;
    
    /**
     * 连接配置
     */
    private final ConnectionConfig config;
    
    /**
     * 当前连接状态
     */
    private final AtomicReference<ConnectionState> state;
    
    /**
     * 输入输出流
     */
    private volatile BufferedReader inputReader;
    private volatile BufferedWriter outputWriter;
    
    /**
     * 重连相关
     */
    private final AtomicInteger reconnectAttempts;
    private volatile int currentReconnectDelay;
    private volatile ScheduledFuture<?> reconnectTask;
    
    /**
     * 心跳相关
     */
    private volatile ScheduledFuture<?> heartbeatTask;
    private final AtomicLong lastActivityTime;
    
    /**
     * 消息读取
     */
    private volatile CompletableFuture<Void> messageReaderFuture;
    private final ExecutorService executorService;
    
    /**
     * 消息处理器
     */
    private final Consumer<String> messageHandler;
    private final Consumer<Throwable> errorHandler;
    private final Consumer<ConnectionState> stateChangeHandler;
    
    /**
     * 关闭标志
     */
    private final AtomicBoolean shutdownRequested;
    
    /**
     * 连接配置类
     */
    public static class ConnectionConfig {
        private final int connectionTimeoutMs;
        private final int heartbeatIntervalMs;
        private final boolean autoReconnect;
        private final int maxReconnectAttempts;
        private final int initialReconnectDelayMs;
        private final int maxReconnectDelayMs;
        
        public ConnectionConfig() {
            this(CONNECTION_TIMEOUT_MS, HEARTBEAT_INTERVAL_MS, true, 
                 MAX_RECONNECT_ATTEMPTS, INITIAL_RECONNECT_DELAY_MS, MAX_RECONNECT_DELAY_MS);
        }
        
        public ConnectionConfig(int connectionTimeoutMs, int heartbeatIntervalMs,
                               boolean autoReconnect, int maxReconnectAttempts,
                               int initialReconnectDelayMs, int maxReconnectDelayMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
            this.heartbeatIntervalMs = heartbeatIntervalMs;
            this.autoReconnect = autoReconnect;
            this.maxReconnectAttempts = maxReconnectAttempts;
            this.initialReconnectDelayMs = initialReconnectDelayMs;
            this.maxReconnectDelayMs = maxReconnectDelayMs;
        }
        
        // Builder 模式
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private int connectionTimeoutMs = CONNECTION_TIMEOUT_MS;
            private int heartbeatIntervalMs = HEARTBEAT_INTERVAL_MS;
            private boolean autoReconnect = true;
            private int maxReconnectAttempts = MAX_RECONNECT_ATTEMPTS;
            private int initialReconnectDelayMs = INITIAL_RECONNECT_DELAY_MS;
            private int maxReconnectDelayMs = MAX_RECONNECT_DELAY_MS;
            
            public Builder connectionTimeoutMs(int timeout) {
                this.connectionTimeoutMs = timeout;
                return this;
            }
            
            public Builder heartbeatIntervalMs(int interval) {
                this.heartbeatIntervalMs = interval;
                return this;
            }
            
            public Builder autoReconnect(boolean autoReconnect) {
                this.autoReconnect = autoReconnect;
                return this;
            }
            
            public Builder maxReconnectAttempts(int maxAttempts) {
                this.maxReconnectAttempts = maxAttempts;
                return this;
            }
            
            public ConnectionConfig build() {
                return new ConnectionConfig(connectionTimeoutMs, heartbeatIntervalMs,
                    autoReconnect, maxReconnectAttempts, initialReconnectDelayMs, maxReconnectDelayMs);
            }
        }
        
        public int getConnectionTimeoutMs() { return connectionTimeoutMs; }
        public int getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
        public boolean isAutoReconnect() { return autoReconnect; }
        public int getMaxReconnectAttempts() { return maxReconnectAttempts; }
        public int getInitialReconnectDelayMs() { return initialReconnectDelayMs; }
        public int getMaxReconnectDelayMs() { return maxReconnectDelayMs; }
    }
    
    /**
     * 连接统计信息
     */
    public static class ConnectionStats {
        private final long connectTime;
        private final int reconnectCount;
        private final long messagesSent;
        private final long messagesReceived;
        private final long lastActivityTime;
        
        public ConnectionStats(long connectTime, int reconnectCount,
                              long messagesSent, long messagesReceived,
                              long lastActivityTime) {
            this.connectTime = connectTime;
            this.reconnectCount = reconnectCount;
            this.messagesSent = messagesSent;
            this.messagesReceived = messagesReceived;
            this.lastActivityTime = lastActivityTime;
        }
        
        public long getConnectTime() { return connectTime; }
        public int getReconnectCount() { return reconnectCount; }
        public long getMessagesSent() { return messagesSent; }
        public long getMessagesReceived() { return messagesReceived; }
        public long getLastActivityTime() { return lastActivityTime; }
        
        public long getUptimeMs() {
            return System.currentTimeMillis() - connectTime;
        }
        
        public long getIdleTimeMs() {
            return System.currentTimeMillis() - lastActivityTime;
        }
    }
    
    // 统计信息
    private volatile long connectTime;
    private final AtomicInteger reconnectCount;
    private final AtomicLong messagesSent;
    private final AtomicLong messagesReceived;
    
    public LspConnection(Process serverProcess, ConnectionConfig config,
                        Consumer<String> messageHandler,
                        Consumer<Throwable> errorHandler,
                        Consumer<ConnectionState> stateChangeHandler) {
        this.serverProcess = serverProcess;
        this.config = config != null ? config : new ConnectionConfig();
        this.messageHandler = messageHandler;
        this.errorHandler = errorHandler;
        this.stateChangeHandler = stateChangeHandler;
        
        this.state = new AtomicReference<>(ConnectionState.DISCONNECTED);
        this.reconnectAttempts = new AtomicInteger(0);
        this.currentReconnectDelay = this.config.getInitialReconnectDelayMs();
        this.lastActivityTime = new AtomicLong(System.currentTimeMillis());
        this.shutdownRequested = new AtomicBoolean(false);
        
        this.reconnectCount = new AtomicInteger(0);
        this.messagesSent = new AtomicLong(0);
        this.messagesReceived = new AtomicLong(0);
        
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "LspConnection-Worker");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * 建立连接
     */
    public synchronized CompletableFuture<Void> connect() {
        if (state.get() == ConnectionState.CONNECTED || 
            state.get() == ConnectionState.CONNECTING) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                setState(ConnectionState.CONNECTING);
                
                // 初始化 IO 流
                inputReader = new BufferedReader(
                    new InputStreamReader(serverProcess.getInputStream(), StandardCharsets.UTF_8));
                outputWriter = new BufferedWriter(
                    new OutputStreamWriter(serverProcess.getOutputStream(), StandardCharsets.UTF_8));
                
                // 启动消息读取器
                startMessageReader();
                
                // 启动心跳检测
                if (config.getHeartbeatIntervalMs() > 0) {
                    startHeartbeat();
                }
                
                connectTime = System.currentTimeMillis();
                reconnectAttempts.set(0);
                currentReconnectDelay = config.getInitialReconnectDelayMs();
                
                setState(ConnectionState.CONNECTED);
                LOGGER.info("LSP connection established");
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to establish LSP connection", e);
                setState(ConnectionState.ERROR);
                throw new CompletionException(e);
            }
        }, executorService);
    }
    
    /**
     * 断开连接
     */
    public synchronized CompletableFuture<Void> disconnect() {
        shutdownRequested.set(true);
        
        return CompletableFuture.runAsync(() -> {
            try {
                setState(ConnectionState.DISCONNECTED);
                
                // 取消重连任务
                if (reconnectTask != null) {
                    reconnectTask.cancel(false);
                    reconnectTask = null;
                }
                
                // 取消心跳任务
                if (heartbeatTask != null) {
                    heartbeatTask.cancel(false);
                    heartbeatTask = null;
                }
                
                // 取消消息读取
                if (messageReaderFuture != null) {
                    messageReaderFuture.cancel(true);
                    messageReaderFuture = null;
                }
                
                // 关闭流
                closeStreams();
                
                LOGGER.info("LSP connection disconnected");
                
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error during disconnect", e);
            }
        }, executorService);
    }
    
    /**
     * 发送消息
     */
    public synchronized void sendMessage(String message) throws IOException {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected");
        }
        
        try {
            byte[] content = message.getBytes(StandardCharsets.UTF_8);
            String header = String.format("Content-Length: %d\r\n\r\n", content.length);
            
            outputWriter.write(header);
            outputWriter.write(message);
            outputWriter.flush();
            
            messagesSent.incrementAndGet();
            lastActivityTime.set(System.currentTimeMillis());
            
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to send message", e);
            handleConnectionError(e);
            throw e;
        }
    }
    
    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return state.get() == ConnectionState.CONNECTED && 
               serverProcess != null && 
               serverProcess.isAlive();
    }
    
    /**
     * 获取当前连接状态
     */
    public ConnectionState getState() {
        return state.get();
    }
    
    /**
     * 获取连接统计信息
     */
    public ConnectionStats getStats() {
        return new ConnectionStats(
            connectTime,
            reconnectCount.get(),
            messagesSent.get(),
            messagesReceived.get(),
            lastActivityTime.get()
        );
    }
    
    /**
     * 设置状态并通知监听器
     */
    private void setState(ConnectionState newState) {
        ConnectionState oldState = state.getAndSet(newState);
        if (oldState != newState && stateChangeHandler != null) {
            try {
                stateChangeHandler.accept(newState);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "State change handler error", e);
            }
        }
    }
    
    /**
     * 启动消息读取器
     */
    private void startMessageReader() {
        messageReaderFuture = CompletableFuture.runAsync(() -> {
            try {
                StringBuilder headerBuilder = new StringBuilder();
                int contentLength = -1;
                
                while (!shutdownRequested.get() && !Thread.currentThread().isInterrupted()) {
                    String line = inputReader.readLine();
                    
                    if (line == null) {
                        // 连接关闭
                        if (!shutdownRequested.get()) {
                            LOGGER.warning("LSP server closed connection unexpectedly");
                            handleConnectionError(new IOException("Connection closed by server"));
                        }
                        break;
                    }
                    
                    if (line.isEmpty()) {
                        // 头部结束，读取内容
                        if (contentLength > 0) {
                            char[] content = new char[contentLength];
                            int read = 0;
                            while (read < contentLength) {
                                int r = inputReader.read(content, read, contentLength - read);
                                if (r == -1) break;
                                read += r;
                            }
                            
                            if (read == contentLength) {
                                String message = new String(content, 0, read);
                                handleMessage(message);
                            }
                        }
                        
                        headerBuilder.setLength(0);
                        contentLength = -1;
                    } else {
                        headerBuilder.append(line).append("\r\n");
                        if (line.startsWith("Content-Length:")) {
                            contentLength = Integer.parseInt(line.substring(16).trim());
                        }
                    }
                }
            } catch (IOException e) {
                if (!shutdownRequested.get()) {
                    handleConnectionError(e);
                }
            }
        }, executorService);
    }
    
    /**
     * 处理接收到的消息
     */
    private void handleMessage(String message) {
        messagesReceived.incrementAndGet();
        lastActivityTime.set(System.currentTimeMillis());
        
        if (messageHandler != null) {
            try {
                messageHandler.accept(message);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Message handler error", e);
            }
        }
    }
    
    /**
     * 处理连接错误
     */
    private void handleConnectionError(Throwable error) {
        if (shutdownRequested.get()) {
            return;
        }
        
        LOGGER.log(Level.WARNING, "LSP connection error", error);
        
        if (errorHandler != null) {
            try {
                errorHandler.accept(error);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error handler exception", e);
            }
        }
        
        // 触发重连
        if (config.isAutoReconnect()) {
            scheduleReconnect();
        } else {
            setState(ConnectionState.ERROR);
        }
    }
    
    /**
     * 调度重连
     */
    private synchronized void scheduleReconnect() {
        if (shutdownRequested.get() || state.get() == ConnectionState.RECONNECTING) {
            return;
        }
        
        int attempts = reconnectAttempts.incrementAndGet();
        
        if (attempts > config.getMaxReconnectAttempts()) {
            LOGGER.severe("Max reconnect attempts reached, giving up");
            setState(ConnectionState.ERROR);
            return;
        }
        
        setState(ConnectionState.RECONNECTING);
        reconnectCount.incrementAndGet();
        
        LOGGER.info(String.format("Scheduling reconnect attempt %d/%d in %d ms",
            attempts, config.getMaxReconnectAttempts(), currentReconnectDelay));
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        reconnectTask = scheduler.schedule(() -> {
            try {
                LOGGER.info("Attempting to reconnect...");
                
                // 清理旧资源
                closeStreams();
                
                // 重新初始化连接
                connect().get(config.getConnectionTimeoutMs(), TimeUnit.MILLISECONDS);
                
                LOGGER.info("Reconnection successful");
                reconnectAttempts.set(0);
                currentReconnectDelay = config.getInitialReconnectDelayMs();
                
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Reconnection failed", e);
                
                // 指数退避
                currentReconnectDelay = Math.min(
                    currentReconnectDelay * RECONNECT_MULTIPLIER,
                    config.getMaxReconnectDelayMs()
                );
                
                // 继续尝试
                scheduleReconnect();
            }
        }, currentReconnectDelay, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 启动心跳检测
     */
    private void startHeartbeat() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "LSP-Heartbeat");
            t.setDaemon(true);
            return t;
        });
        
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            if (!isConnected() || shutdownRequested.get()) {
                return;
            }
            
            long idleTime = System.currentTimeMillis() - lastActivityTime.get();
            
            if (idleTime > config.getHeartbeatIntervalMs() * 2) {
                // 连接可能已断开
                LOGGER.warning("Connection appears to be idle, triggering reconnect");
                handleConnectionError(new IOException("Connection timeout"));
            }
        }, config.getHeartbeatIntervalMs(), config.getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);
    }
    
    /**
     * 关闭流
     */
    private void closeStreams() {
        if (inputReader != null) {
            try {
                inputReader.close();
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Error closing input reader", e);
            }
            inputReader = null;
        }
        
        if (outputWriter != null) {
            try {
                outputWriter.close();
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Error closing output writer", e);
            }
            outputWriter = null;
        }
    }
    
    /**
     * 优雅关闭
     */
    public void shutdown() {
        disconnect().join();
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

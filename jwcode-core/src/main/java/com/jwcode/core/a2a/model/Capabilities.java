package com.jwcode.core.a2a.model;

/**
 * Capabilities — Agent 通信能力声明。
 *
 * <p>描述 Agent 支持的通信方式，如流式推送、SSE、WebSocket 等。</p>
 */
public class Capabilities {

    /** 是否支持流式输出（SSE） */
    private final boolean streaming;

    /** 是否支持推送通知 */
    private final boolean pushNotifications;

    /** 是否支持 WebSocket 通信 */
    private final boolean websocket;

    /** 是否支持批量任务 */
    private final boolean batchProcessing;

    /** 最大并发任务数（0 表示不限制） */
    private final int maxConcurrentTasks;

    public Capabilities(boolean streaming, boolean pushNotifications,
                        boolean websocket, boolean batchProcessing,
                        int maxConcurrentTasks) {
        this.streaming = streaming;
        this.pushNotifications = pushNotifications;
        this.websocket = websocket;
        this.batchProcessing = batchProcessing;
        this.maxConcurrentTasks = maxConcurrentTasks;
    }

    // ==================== Getters ====================

    public boolean isStreaming() { return streaming; }
    public boolean isPushNotifications() { return pushNotifications; }
    public boolean isWebsocket() { return websocket; }
    public boolean isBatchProcessing() { return batchProcessing; }
    public int getMaxConcurrentTasks() { return maxConcurrentTasks; }

    /**
     * 返回默认能力（本地 Agent 的默认配置）
     */
    public static Capabilities defaultCapabilities() {
        return new Capabilities(false, false, false, false, 1);
    }

    /**
     * 返回完整能力（支持流式、WebSocket 的远程 Agent）
     */
    public static Capabilities fullCapabilities() {
        return new Capabilities(true, true, true, true, 10);
    }

    @Override
    public String toString() {
        return "Capabilities{streaming=" + streaming + ", ws=" + websocket +
               ", batch=" + batchProcessing + ", maxConcurrent=" + maxConcurrentTasks + "}";
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean streaming;
        private boolean pushNotifications;
        private boolean websocket;
        private boolean batchProcessing;
        private int maxConcurrentTasks = 1;

        public Builder streaming(boolean streaming) { this.streaming = streaming; return this; }
        public Builder pushNotifications(boolean pushNotifications) { this.pushNotifications = pushNotifications; return this; }
        public Builder websocket(boolean websocket) { this.websocket = websocket; return this; }
        public Builder batchProcessing(boolean batchProcessing) { this.batchProcessing = batchProcessing; return this; }
        public Builder maxConcurrentTasks(int maxConcurrentTasks) { this.maxConcurrentTasks = maxConcurrentTasks; return this; }

        public Capabilities build() {
            return new Capabilities(streaming, pushNotifications, websocket, batchProcessing, maxConcurrentTasks);
        }
    }
}

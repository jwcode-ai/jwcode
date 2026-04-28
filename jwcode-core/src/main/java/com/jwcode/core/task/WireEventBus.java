package com.jwcode.core.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * WireEventBus — 跨组件事件总线。
 *
 * <p>实现 Kimi Code 的 Wire 事件驱动机制：</p>
 * <ul>
 *   <li>后台任务完成/失败时自动发布事件</li>
 *   <li>主控 Agent 订阅事件，空闲时自动发起新轮次处理结果</li>
 *   <li>支持多订阅者，失败隔离</li>
 * </ul>
 */
public class WireEventBus {

    private static final Logger logger = LoggerFactory.getLogger(WireEventBus.class);

    private static volatile WireEventBus instance;
    private static final Object LOCK = new Object();

    public static WireEventBus getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new WireEventBus();
                }
            }
        }
        return instance;
    }

    private final List<Consumer<WireEvent>> subscribers = new CopyOnWriteArrayList<>();

    private WireEventBus() {}

    public void subscribe(Consumer<WireEvent> subscriber) {
        subscribers.add(subscriber);
    }

    public void unsubscribe(Consumer<WireEvent> subscriber) {
        subscribers.remove(subscriber);
    }

    public void publish(WireEvent event) {
        logger.debug("[WireEventBus] 发布事件 | type={} | source={}", event.getType(), event.getSourceId());
        for (Consumer<WireEvent> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                logger.warn("[WireEventBus] 订阅者异常", e);
            }
        }
    }

    // ==================== 事件基类 ====================

    public static abstract class WireEvent {
        private final String type;
        private final String sourceId;
        private final long timestamp;

        protected WireEvent(String type, String sourceId) {
            this.type = type;
            this.sourceId = sourceId;
            this.timestamp = System.currentTimeMillis();
        }

        public String getType() { return type; }
        public String getSourceId() { return sourceId; }
        public long getTimestamp() { return timestamp; }
    }

    public static class TaskCompletedEvent extends WireEvent {
        private final boolean success;
        private final int exitCode;

        public TaskCompletedEvent(String taskId, boolean success, int exitCode) {
            super("task.completed", taskId);
            this.success = success;
            this.exitCode = exitCode;
        }

        public boolean isSuccess() { return success; }
        public int getExitCode() { return exitCode; }
    }

    public static class TaskOutputEvent extends WireEvent {
        private final String outputChunk;

        public TaskOutputEvent(String taskId, String outputChunk) {
            super("task.output", taskId);
            this.outputChunk = outputChunk;
        }

        public String getOutputChunk() { return outputChunk; }
    }

    public static class AgentStateChangedEvent extends WireEvent {
        private final String fromState;
        private final String toState;

        public AgentStateChangedEvent(String agentId, String fromState, String toState) {
            super("agent.state_changed", agentId);
            this.fromState = fromState;
            this.toState = toState;
        }

        public String getFromState() { return fromState; }
        public String getToState() { return toState; }
    }
}

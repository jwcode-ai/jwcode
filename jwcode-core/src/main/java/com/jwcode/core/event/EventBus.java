package com.jwcode.core.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * EventBus — 进程内事件发布/订阅总线。
 *
 * <p>支持按 {@link SessionEvent.EventType} 过滤订阅。
 * 用于实时分发 {@link SessionEvent} 到 {@link SessionProjector} 和其他监听器。
 *
 * <p>线程安全：所有写入操作用 CopyOnWriteArrayList、读取无锁。
 */
public class EventBus {

    private static final Logger logger = Logger.getLogger(EventBus.class.getName());

    private final Map<SessionEvent.EventType, List<Consumer<SessionEvent>>> subscribers;
    private final List<Consumer<SessionEvent>> wildcardSubscribers;

    public EventBus() {
        this.subscribers = new ConcurrentHashMap<>();
        this.wildcardSubscribers = new CopyOnWriteArrayList<>();
    }

    // ==================== 订阅 ====================

    /**
     * 订阅指定类型的事件。
     *
     * @param type     事件类型
     * @param handler  事件处理器
     * @return 取消订阅的 Runnable（调用即取消）
     */
    public Runnable subscribe(SessionEvent.EventType type, Consumer<SessionEvent> handler) {
        subscribers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
        return () -> {
            List<Consumer<SessionEvent>> list = subscribers.get(type);
            if (list != null) list.remove(handler);
        };
    }

    /**
     * 订阅所有类型的事件。
     */
    public Runnable subscribeAll(Consumer<SessionEvent> handler) {
        wildcardSubscribers.add(handler);
        return () -> wildcardSubscribers.remove(handler);
    }

    // ==================== 发布 ====================

    /**
     * 发布事件到所有匹配的订阅者。
     */
    public void publish(SessionEvent event) {
        // 精确类型匹配
        List<Consumer<SessionEvent>> exact = subscribers.get(event.getType());
        if (exact != null) {
            for (Consumer<SessionEvent> handler : exact) {
                try {
                    handler.accept(event);
                } catch (Exception e) {
                    logger.warning("[EventBus] Subscriber error for " + event.getType()
                        + ": " + e.getMessage());
                }
            }
        }

        // 通配符订阅
        for (Consumer<SessionEvent> handler : wildcardSubscribers) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                logger.warning("[EventBus] Wildcard subscriber error: " + e.getMessage());
            }
        }
    }

    // ==================== 统计 ====================

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        Map<String, Integer> byType = new java.util.LinkedHashMap<>();
        subscribers.forEach((type, list) -> byType.put(type.name(), list.size()));
        stats.put("subscribersByType", byType);
        stats.put("wildcardSubscribers", wildcardSubscribers.size());
        return stats;
    }
}

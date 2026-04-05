package com.jwcode.core.mcp;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * McpChannelNotification - MCP 通道通知管理
 * 
 * 功能说明：
 * 管理 MCP 通道的通知消息，支持订阅/发布模式。
 * 处理服务器推送的通知、进度更新、资源变更等事件。
 * 
 * 核心特性：
 * - 订阅/发布模式
 * - 通知类型过滤
 * - 通知历史记录
 * - 异步通知处理
 * - 通知优先级
 * 
 * 上下文关系：
 * - 被 McpClient 用来处理服务器通知
 * - 与 McpConnectionManager 协作
 * - 为 UI 层提供通知订阅接口
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class McpChannelNotification {
    
    /**
     * 通知订阅者映射表（主题 -> 订阅者列表）
     */
    private final Map<String, List<NotificationSubscriber>> subscribersByTopic;
    
    /**
     * 通知历史记录
     */
    private final List<NotificationMessage> notificationHistory;
    
    /**
     * 最大历史记录数量
     */
    private static final int MAX_HISTORY_SIZE = 1000;
    
    /**
     * 构造函数
     */
    public McpChannelNotification() {
        this.subscribersByTopic = new ConcurrentHashMap<>();
        this.notificationHistory = new CopyOnWriteArrayList<>();
    }
    
    /**
     * 订阅通知主题
     * 
     * @param topic 主题
     * @param callback 回调函数
     * @return 订阅 ID
     */
    public String subscribe(String topic, Consumer<NotificationMessage> callback) {
        return subscribe(topic, callback, NotificationPriority.NORMAL);
    }
    
    /**
     * 订阅通知主题（带优先级）
     * 
     * @param topic 主题
     * @param callback 回调函数
     * @param priority 优先级
     * @return 订阅 ID
     */
    public String subscribe(String topic, Consumer<NotificationMessage> callback, NotificationPriority priority) {
        String subscriptionId = UUID.randomUUID().toString();
        NotificationSubscriber subscriber = new NotificationSubscriber(subscriptionId, topic, callback, priority);
        
        subscribersByTopic.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(subscriber);
        
        return subscriptionId;
    }
    
    /**
     * 取消订阅
     * 
     * @param subscriptionId 订阅 ID
     */
    public void unsubscribe(String subscriptionId) {
        for (List<NotificationSubscriber> subscribers : subscribersByTopic.values()) {
            subscribers.removeIf(sub -> sub.getSubscriptionId().equals(subscriptionId));
        }
    }
    
    /**
     * 取消订阅（按主题）
     * 
     * @param topic 主题
     */
    public void unsubscribeByTopic(String topic) {
        subscribersByTopic.remove(topic);
    }
    
    /**
     * 发布通知
     * 
     * @param topic 主题
     * @param type 通知类型
     * @param data 通知数据
     */
    public void publish(String topic, NotificationType type, Object data) {
        publish(topic, type, data, NotificationPriority.NORMAL);
    }
    
    /**
     * 发布通知（带优先级）
     * 
     * @param topic 主题
     * @param type 通知类型
     * @param data 通知数据
     * @param priority 优先级
     */
    public void publish(String topic, NotificationType type, Object data, NotificationPriority priority) {
        NotificationMessage message = new NotificationMessage(
                UUID.randomUUID().toString(),
                topic,
                type,
                data,
                priority,
                Instant.now()
        );
        
        // 添加到历史记录
        addToHistory(message);
        
        // 通知订阅者
        List<NotificationSubscriber> subscribers = subscribersByTopic.get(topic);
        if (subscribers != null) {
            // 按优先级排序订阅者
            List<NotificationSubscriber> sortedSubscribers = new ArrayList<>(subscribers);
            sortedSubscribers.sort((a, b) -> b.getPriority().compareTo(a.getPriority()));
            
            for (NotificationSubscriber subscriber : sortedSubscribers) {
                try {
                    subscriber.getCallback().accept(message);
                } catch (Exception e) {
                    // 订阅者处理失败，记录日志但不影响其他订阅者
                    System.err.println("通知订阅者处理失败：" + e.getMessage());
                }
            }
        }
        
        // 广播到所有主题的特殊订阅者
        List<NotificationSubscriber> allSubscribers = subscribersByTopic.get("*");
        if (allSubscribers != null) {
            for (NotificationSubscriber subscriber : allSubscribers) {
                try {
                    subscriber.getCallback().accept(message);
                } catch (Exception e) {
                    System.err.println("通知订阅者处理失败：" + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 添加到历史记录
     */
    private void addToHistory(NotificationMessage message) {
        notificationHistory.add(message);
        
        // 限制历史记录大小
        while (notificationHistory.size() > MAX_HISTORY_SIZE) {
            notificationHistory.remove(0);
        }
    }
    
    /**
     * 获取通知历史
     * 
     * @param limit 限制数量
     * @return 通知历史列表
     */
    public List<NotificationMessage> getHistory(int limit) {
        int size = notificationHistory.size();
        if (limit >= size) {
            return new ArrayList<>(notificationHistory);
        }
        return new ArrayList<>(notificationHistory.subList(size - limit, size));
    }
    
    /**
     * 获取通知历史（按主题过滤）
     * 
     * @param topic 主题
     * @param limit 限制数量
     * @return 通知历史列表
     */
    public List<NotificationMessage> getHistoryByTopic(String topic, int limit) {
        List<NotificationMessage> filtered = new ArrayList<>();
        for (NotificationMessage msg : notificationHistory) {
            if (msg.getTopic().equals(topic)) {
                filtered.add(msg);
            }
        }
        
        int size = filtered.size();
        if (limit >= size) {
            return filtered;
        }
        return filtered.subList(size - limit, size);
    }
    
    /**
     * 清除通知历史
     */
    public void clearHistory() {
        notificationHistory.clear();
    }
    
    /**
     * 清除通知历史（按主题）
     * 
     * @param topic 主题
     */
    public void clearHistoryByTopic(String topic) {
        notificationHistory.removeIf(msg -> msg.getTopic().equals(topic));
    }
    
    /**
     * 获取所有订阅者数量
     * 
     * @return 订阅者数量
     */
    public int getSubscriberCount() {
        int count = 0;
        for (List<NotificationSubscriber> subscribers : subscribersByTopic.values()) {
            count += subscribers.size();
        }
        return count;
    }
    
    /**
     * 获取主题的订阅者数量
     * 
     * @param topic 主题
     * @return 订阅者数量
     */
    public int getSubscriberCount(String topic) {
        List<NotificationSubscriber> subscribers = subscribersByTopic.get(topic);
        return subscribers != null ? subscribers.size() : 0;
    }
    
    /**
     * 获取所有订阅的主题
     * 
     * @return 主题集合
     */
    public Set<String> getAllTopics() {
        return subscribersByTopic.keySet();
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 通知优先级枚举
     */
    public enum NotificationPriority {
        /** 低优先级 */
        LOW(0),
        /** 普通优先级 */
        NORMAL(1),
        /** 高优先级 */
        HIGH(2),
        /** 紧急优先级 */
        URGENT(3);
        
        private final int value;
        
        NotificationPriority(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    /**
     * 通知类型枚举
     */
    public enum NotificationType {
        /** 工具列表变更 */
        TOOLS_CHANGED("tools_changed"),
        /** 资源列表变更 */
        RESOURCES_CHANGED("resources_changed"),
        /** 提示列表变更 */
        PROMPTS_CHANGED("prompts_changed"),
        /** 进度更新 */
        PROGRESS_UPDATE("progress_update"),
        /** 错误通知 */
        ERROR("error"),
        /** 警告通知 */
        WARNING("warning"),
        /** 信息通知 */
        INFO("info"),
        /** 日志消息 */
        LOG("log"),
        /** 自定义通知 */
        CUSTOM("custom");
        
        private final String value;
        
        NotificationType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static NotificationType fromValue(String value) {
            for (NotificationType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return CUSTOM;
        }
    }
    
    /**
     * 通知消息类
     */
    public static class NotificationMessage {
        private final String id;
        private final String topic;
        private final NotificationType type;
        private final Object data;
        private final NotificationPriority priority;
        private final Instant timestamp;
        
        public NotificationMessage(String id, String topic, NotificationType type,
                                   Object data, NotificationPriority priority, Instant timestamp) {
            this.id = id;
            this.topic = topic;
            this.type = type;
            this.data = data;
            this.priority = priority;
            this.timestamp = timestamp;
        }
        
        public String getId() {
            return id;
        }
        
        public String getTopic() {
            return topic;
        }
        
        public NotificationType getType() {
            return type;
        }
        
        public Object getData() {
            return data;
        }
        
        public NotificationPriority getPriority() {
            return priority;
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
        
        @Override
        public String toString() {
            return String.format("NotificationMessage{id=%s, topic=%s, type=%s, priority=%s, timestamp=%s}",
                    id, topic, type, priority, timestamp);
        }
    }
    
    /**
     * 通知订阅者类
     */
    public static class NotificationSubscriber {
        private final String subscriptionId;
        private final String topic;
        private final Consumer<NotificationMessage> callback;
        private final NotificationPriority priority;
        
        public NotificationSubscriber(String subscriptionId, String topic,
                                      Consumer<NotificationMessage> callback,
                                      NotificationPriority priority) {
            this.subscriptionId = subscriptionId;
            this.topic = topic;
            this.callback = callback;
            this.priority = priority;
        }
        
        public String getSubscriptionId() {
            return subscriptionId;
        }
        
        public String getTopic() {
            return topic;
        }
        
        public Consumer<NotificationMessage> getCallback() {
            return callback;
        }
        
        public NotificationPriority getPriority() {
            return priority;
        }
    }
    
    /**
     * 通知构建器
     */
    public static class NotificationBuilder {
        private String topic;
        private NotificationType type = NotificationType.INFO;
        private Object data;
        private NotificationPriority priority = NotificationPriority.NORMAL;
        
        public NotificationBuilder topic(String topic) {
            this.topic = topic;
            return this;
        }
        
        public NotificationBuilder type(NotificationType type) {
            this.type = type;
            return this;
        }
        
        public NotificationBuilder data(Object data) {
            this.data = data;
            return this;
        }
        
        public NotificationBuilder priority(NotificationPriority priority) {
            this.priority = priority;
            return this;
        }
        
        public NotificationMessage build() {
            return new NotificationMessage(
                    UUID.randomUUID().toString(),
                    topic,
                    type,
                    data,
                    priority,
                    Instant.now()
            );
        }
    }
    
    /**
     * 创建通知构建器
     * 
     * @return 新的构建器实例
     */
    public static NotificationBuilder builder() {
        return new NotificationBuilder();
    }
}
package com.jwcode.core.agent;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Agent 消息总线 - Phase 2
 * 
 * 实现 Agent 间消息传递，支持：
 * - 发布/订阅模式
 * - 点对点消息
 * - 消息队列管理
 * - 消息持久化（可选）
 * - 消息过滤和路由
 */
public class AgentMessageBus {
    
    private static final Logger logger = Logger.getLogger(AgentMessageBus.class.getName());
    
    // 消息ID生成器
    private final AtomicLong messageIdGenerator = new AtomicLong(0);
    
    // 订阅管理
    private final Map<String, Set<MessageSubscriber>> topicSubscribers = new ConcurrentHashMap<>();
    private final Map<String, Set<MessageSubscriber>> agentSubscribers = new ConcurrentHashMap<>();
    private final Set<MessageSubscriber> broadcastSubscribers = ConcurrentHashMap.newKeySet();
    
    // 消息队列
    private final Map<String, BlockingQueue<AgentMessage>> agentQueues = new ConcurrentHashMap<>();
    private final BlockingQueue<AgentMessage> deadLetterQueue = new LinkedBlockingQueue<>();
    
    // 消息历史（用于查询）
    private final List<AgentMessage> messageHistory = Collections.synchronizedList(new ArrayList<>());
    private final int maxHistorySize;
    
    // 线程池
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;
    
    // 配置
    private final MessageBusConfig config;
    
    // 状态
    private volatile boolean running = true;
    
    /**
     * 消息总线配置
     */
    public static class MessageBusConfig {
        private int defaultQueueSize = 1000;
        private int maxHistorySize = 10000;
        private long defaultMessageTTL = 60000; // 60秒
        private boolean persistMessages = false;
        private int threadPoolSize = Runtime.getRuntime().availableProcessors();
        private long messageCleanupInterval = 300000; // 5分钟
        
        public int getDefaultQueueSize() { return defaultQueueSize; }
        public void setDefaultQueueSize(int defaultQueueSize) { this.defaultQueueSize = defaultQueueSize; }
        
        public int getMaxHistorySize() { return maxHistorySize; }
        public void setMaxHistorySize(int maxHistorySize) { this.maxHistorySize = maxHistorySize; }
        
        public long getDefaultMessageTTL() { return defaultMessageTTL; }
        public void setDefaultMessageTTL(long defaultMessageTTL) { this.defaultMessageTTL = defaultMessageTTL; }
        
        public boolean isPersistMessages() { return persistMessages; }
        public void setPersistMessages(boolean persistMessages) { this.persistMessages = persistMessages; }
        
        public int getThreadPoolSize() { return threadPoolSize; }
        public void setThreadPoolSize(int threadPoolSize) { this.threadPoolSize = threadPoolSize; }
        
        public long getMessageCleanupInterval() { return messageCleanupInterval; }
        public void setMessageCleanupInterval(long messageCleanupInterval) { this.messageCleanupInterval = messageCleanupInterval; }
        
        public static MessageBusConfig defaultConfig() {
            return new MessageBusConfig();
        }
    }
    
    /**
     * Agent 消息
     */
    public static class AgentMessage {
        private final long id;
        private final String type;
        private final String sender;
        private final String recipient;
        private final String topic;
        private final Object payload;
        private final Map<String, Object> headers;
        private final long timestamp;
        private final long ttl;
        private volatile boolean delivered = false;
        private volatile int deliveryAttempts = 0;
        
        private AgentMessage(Builder builder) {
            this.id = builder.id;
            this.type = builder.type;
            this.sender = builder.sender;
            this.recipient = builder.recipient;
            this.topic = builder.topic;
            this.payload = builder.payload;
            this.headers = builder.headers != null ? new HashMap<>(builder.headers) : new HashMap<>();
            this.timestamp = builder.timestamp;
            this.ttl = builder.ttl;
        }
        
        // Getters
        public long getId() { return id; }
        public String getType() { return type; }
        public String getSender() { return sender; }
        public String getRecipient() { return recipient; }
        public String getTopic() { return topic; }
        public Object getPayload() { return payload; }
        public Map<String, Object> getHeaders() { return Collections.unmodifiableMap(headers); }
        public long getTimestamp() { return timestamp; }
        public long getTtl() { return ttl; }
        public boolean isDelivered() { return delivered; }
        public int getDeliveryAttempts() { return deliveryAttempts; }
        
        public boolean isExpired() {
            return ttl > 0 && System.currentTimeMillis() > timestamp + ttl;
        }
        
        void markDelivered() {
            this.delivered = true;
        }
        
        void incrementDeliveryAttempts() {
            this.deliveryAttempts++;
        }
        
        public Object getHeader(String key) {
            return headers.get(key);
        }
        
        public boolean hasHeader(String key) {
            return headers.containsKey(key);
        }
        
        @Override
        public String toString() {
            return String.format("AgentMessage{id=%d, type='%s', sender='%s', recipient='%s', topic='%s'}",
                id, type, sender, recipient, topic);
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private long id;
            private String type = "DEFAULT";
            private String sender;
            private String recipient;
            private String topic;
            private Object payload;
            private Map<String, Object> headers = new HashMap<>();
            private long timestamp = System.currentTimeMillis();
            private long ttl = 0;
            
            public Builder id(long id) { this.id = id; return this; }
            public Builder type(String type) { this.type = type; return this; }
            public Builder sender(String sender) { this.sender = sender; return this; }
            public Builder recipient(String recipient) { this.recipient = recipient; return this; }
            public Builder topic(String topic) { this.topic = topic; return this; }
            public Builder payload(Object payload) { this.payload = payload; return this; }
            public Builder headers(Map<String, Object> headers) { this.headers = headers; return this; }
            public Builder addHeader(String key, Object value) { this.headers.put(key, value); return this; }
            public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }
            public Builder ttl(long ttl) { this.ttl = ttl; return this; }
            
            public AgentMessage build() {
                if (id == 0) {
                    id = System.currentTimeMillis();
                }
                return new AgentMessage(this);
            }
        }
    }
    
    /**
     * 消息订阅者
     */
    @FunctionalInterface
    public interface MessageSubscriber {
        void onMessage(AgentMessage message);
        
        default boolean filter(AgentMessage message) {
            return true;
        }
    }
    
    /**
     * 消息过滤器
     */
    @FunctionalInterface
    public interface MessageFilter {
        boolean accept(AgentMessage message);
    }
    
    // ==================== 构造函数 ====================
    
    public AgentMessageBus() {
        this(MessageBusConfig.defaultConfig());
    }
    
    public AgentMessageBus(MessageBusConfig config) {
        this.config = config;
        this.maxHistorySize = config.getMaxHistorySize();
        
        this.executorService = Executors.newFixedThreadPool(
            config.getThreadPoolSize(),
            r -> {
                Thread t = new Thread(r, "MessageBus-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            }
        );
        
        this.scheduledExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "MessageBus-Scheduler");
            t.setDaemon(true);
            return t;
        });
        
        // 启动清理任务
        startCleanupTask();
        
        logger.info("AgentMessageBus initialized");
    }
    
    // ==================== 发布/订阅 ====================
    
    /**
     * 订阅主题
     */
    public void subscribe(String topic, MessageSubscriber subscriber) {
        topicSubscribers.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet()).add(subscriber);
        logger.fine("Subscriber added for topic: " + topic);
    }
    
    /**
     * 取消订阅
     */
    public void unsubscribe(String topic, MessageSubscriber subscriber) {
        Set<MessageSubscriber> subscribers = topicSubscribers.get(topic);
        if (subscribers != null) {
            subscribers.remove(subscriber);
        }
    }
    
    /**
     * 发布到主题
     */
    public void publish(String topic, Object payload) {
        publish(topic, payload, null);
    }
    
    /**
     * 发布到主题（带发送者）
     */
    public void publish(String topic, Object payload, String sender) {
        AgentMessage message = AgentMessage.builder()
            .id(messageIdGenerator.incrementAndGet())
            .type("PUBLISH")
            .topic(topic)
            .sender(sender)
            .payload(payload)
            .ttl(config.getDefaultMessageTTL())
            .build();
        
        publish(message);
    }
    
    /**
     * 发布消息
     */
    public void publish(AgentMessage message) {
        if (!running) {
            throw new IllegalStateException("MessageBus is not running");
        }
        
        // 记录历史
        addToHistory(message);
        
        // 异步分发
        executorService.submit(() -> {
            try {
                dispatchToTopicSubscribers(message);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error dispatching message: " + message, e);
                handleDeliveryFailure(message, e);
            }
        });
    }
    
    // ==================== 点对点消息 ====================
    
    /**
     * 发送消息给指定 Agent
     */
    public void send(String recipient, Object payload) {
        send(recipient, payload, null);
    }
    
    /**
     * 发送消息给指定 Agent（带发送者）
     */
    public void send(String recipient, Object payload, String sender) {
        AgentMessage message = AgentMessage.builder()
            .id(messageIdGenerator.incrementAndGet())
            .type("DIRECT")
            .sender(sender)
            .recipient(recipient)
            .payload(payload)
            .ttl(config.getDefaultMessageTTL())
            .build();
        
        send(message);
    }
    
    /**
     * 发送消息
     */
    public void send(AgentMessage message) {
        if (!running) {
            throw new IllegalStateException("MessageBus is not running");
        }
        
        addToHistory(message);
        
        executorService.submit(() -> {
            try {
                boolean delivered = dispatchToAgent(message);
                if (!delivered) {
                    // 放入消息队列
                    queueMessageForAgent(message);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error sending message: " + message, e);
                handleDeliveryFailure(message, e);
            }
        });
    }
    
    /**
     * 注册 Agent 消息队列
     */
    public void registerAgentQueue(String agentId) {
        agentQueues.computeIfAbsent(agentId, k -> 
            new LinkedBlockingQueue<>(config.getDefaultQueueSize()));
    }
    
    /**
     * 注销 Agent 消息队列
     */
    public void unregisterAgentQueue(String agentId) {
        agentQueues.remove(agentId);
        agentSubscribers.remove(agentId);
    }
    
    /**
     * 订阅 Agent 消息
     */
    public void subscribeToAgent(String agentId, MessageSubscriber subscriber) {
        agentSubscribers.computeIfAbsent(agentId, k -> ConcurrentHashMap.newKeySet()).add(subscriber);
        registerAgentQueue(agentId);
    }
    
    /**
     * 接收消息（阻塞）
     */
    public AgentMessage receive(String agentId) throws InterruptedException {
        BlockingQueue<AgentMessage> queue = agentQueues.get(agentId);
        if (queue == null) {
            return null;
        }
        return queue.take();
    }
    
    /**
     * 接收消息（带超时）
     */
    public AgentMessage receive(String agentId, long timeout, TimeUnit unit) throws InterruptedException {
        BlockingQueue<AgentMessage> queue = agentQueues.get(agentId);
        if (queue == null) {
            return null;
        }
        return queue.poll(timeout, unit);
    }
    
    /**
     * 轮询消息（非阻塞）
     */
    public AgentMessage poll(String agentId) {
        BlockingQueue<AgentMessage> queue = agentQueues.get(agentId);
        if (queue == null) {
            return null;
        }
        return queue.poll();
    }
    
    // ==================== 广播 ====================
    
    /**
     * 广播消息
     */
    public void broadcast(Object payload) {
        broadcast(payload, null);
    }
    
    /**
     * 广播消息（带发送者）
     */
    public void broadcast(Object payload, String sender) {
        AgentMessage message = AgentMessage.builder()
            .id(messageIdGenerator.incrementAndGet())
            .type("BROADCAST")
            .sender(sender)
            .payload(payload)
            .ttl(config.getDefaultMessageTTL())
            .build();
        
        broadcast(message);
    }
    
    /**
     * 广播消息
     */
    public void broadcast(AgentMessage message) {
        addToHistory(message);
        
        executorService.submit(() -> {
            for (MessageSubscriber subscriber : broadcastSubscribers) {
                try {
                    if (subscriber.filter(message)) {
                        subscriber.onMessage(message);
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error in broadcast subscriber", e);
                }
            }
        });
    }
    
    /**
     * 订阅广播
     */
    public void subscribeToBroadcast(MessageSubscriber subscriber) {
        broadcastSubscribers.add(subscriber);
    }
    
    /**
     * 取消订阅广播
     */
    public void unsubscribeFromBroadcast(MessageSubscriber subscriber) {
        broadcastSubscribers.remove(subscriber);
    }
    
    // ==================== 消息查询 ====================
    
    /**
     * 获取消息历史
     */
    public List<AgentMessage> getMessageHistory() {
        return new ArrayList<>(messageHistory);
    }
    
    /**
     * 根据条件查询消息
     */
    public List<AgentMessage> queryMessages(Predicate<AgentMessage> filter) {
        return messageHistory.stream()
            .filter(filter)
            .collect(Collectors.toList());
    }
    
    /**
     * 查询发送者的消息
     */
    public List<AgentMessage> queryBySender(String sender) {
        return queryMessages(m -> sender.equals(m.getSender()));
    }
    
    /**
     * 查询接收者的消息
     */
    public List<AgentMessage> queryByRecipient(String recipient) {
        return queryMessages(m -> recipient.equals(m.getRecipient()));
    }
    
    /**
     * 查询主题的消息
     */
    public List<AgentMessage> queryByTopic(String topic) {
        return queryMessages(m -> topic.equals(m.getTopic()));
    }
    
    /**
     * 查询类型的消息
     */
    public List<AgentMessage> queryByType(String type) {
        return queryMessages(m -> type.equals(m.getType()));
    }
    
    /**
     * 获取队列大小
     */
    public int getQueueSize(String agentId) {
        BlockingQueue<AgentMessage> queue = agentQueues.get(agentId);
        return queue != null ? queue.size() : 0;
    }
    
    /**
     * 清除消息历史
     */
    public void clearHistory() {
        messageHistory.clear();
        logger.info("Message history cleared");
    }
    
    // ==================== 生命周期管理 ====================
    
    /**
     * 关闭消息总线
     */
    public void shutdown() {
        logger.info("Shutting down AgentMessageBus...");
        running = false;
        
        executorService.shutdown();
        scheduledExecutor.shutdown();
        
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 清空队列
        agentQueues.clear();
        topicSubscribers.clear();
        agentSubscribers.clear();
        broadcastSubscribers.clear();
        
        logger.info("AgentMessageBus shutdown complete");
    }
    
    /**
     * 检查是否运行中
     */
    public boolean isRunning() {
        return running;
    }
    
    // ==================== 私有方法 ====================
    
    private void dispatchToTopicSubscribers(AgentMessage message) {
        String topic = message.getTopic();
        if (topic == null) return;
        
        Set<MessageSubscriber> subscribers = topicSubscribers.get(topic);
        if (subscribers == null) return;
        
        for (MessageSubscriber subscriber : subscribers) {
            try {
                if (subscriber.filter(message)) {
                    subscriber.onMessage(message);
                    message.markDelivered();
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error in topic subscriber", e);
            }
        }
    }
    
    private boolean dispatchToAgent(AgentMessage message) {
        String recipient = message.getRecipient();
        if (recipient == null) return false;
        
        Set<MessageSubscriber> subscribers = agentSubscribers.get(recipient);
        if (subscribers == null) return false;
        
        boolean delivered = false;
        for (MessageSubscriber subscriber : subscribers) {
            try {
                if (subscriber.filter(message)) {
                    subscriber.onMessage(message);
                    delivered = true;
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error in agent subscriber", e);
            }
        }
        
        if (delivered) {
            message.markDelivered();
        }
        
        return delivered;
    }
    
    private void queueMessageForAgent(AgentMessage message) {
        String recipient = message.getRecipient();
        BlockingQueue<AgentMessage> queue = agentQueues.get(recipient);
        
        if (queue != null) {
            boolean added = queue.offer(message);
            if (!added) {
                logger.warning("Agent queue full for: " + recipient + ", message dropped");
                deadLetterQueue.offer(message);
            }
        }
    }
    
    private void handleDeliveryFailure(AgentMessage message, Exception e) {
        message.incrementDeliveryAttempts();
        
        if (message.getDeliveryAttempts() >= 3) {
            deadLetterQueue.offer(message);
            logger.warning("Message moved to dead letter queue: " + message);
        }
    }
    
    private void addToHistory(AgentMessage message) {
        messageHistory.add(message);
        
        // 限制历史大小
        if (messageHistory.size() > maxHistorySize) {
            synchronized (messageHistory) {
                while (messageHistory.size() > maxHistorySize) {
                    messageHistory.remove(0);
                }
            }
        }
    }
    
    private void startCleanupTask() {
        scheduledExecutor.scheduleAtFixedRate(
            this::cleanupExpiredMessages,
            config.getMessageCleanupInterval(),
            config.getMessageCleanupInterval(),
            TimeUnit.MILLISECONDS
        );
    }
    
    private void cleanupExpiredMessages() {
        try {
            // 清理过期的历史消息
            messageHistory.removeIf(AgentMessage::isExpired);
            
            // 清理过期队列消息
            for (BlockingQueue<AgentMessage> queue : agentQueues.values()) {
                queue.removeIf(AgentMessage::isExpired);
            }
            
            logger.fine("Expired messages cleaned up");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error cleaning up expired messages", e);
        }
    }
    
    // ==================== 统计信息 ====================
    
    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMessages", messageIdGenerator.get());
        stats.put("historySize", messageHistory.size());
        stats.put("deadLetterQueueSize", deadLetterQueue.size());
        stats.put("registeredQueues", agentQueues.size());
        stats.put("topicCount", topicSubscribers.size());
        stats.put("running", running);
        return stats;
    }
}

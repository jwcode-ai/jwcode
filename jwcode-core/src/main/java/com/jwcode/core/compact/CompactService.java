package com.jwcode.core.compact;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * CompactService - 会话压缩服务
 * 
 * 功能说明：
 * 管理会话的自动压缩功能，当会话 Token 数量达到阈值时，
 * 自动对旧消息进行压缩和摘要，以保持上下文在限制范围内。
 * 
 * 核心特性：
 * - Token 计数和跟踪
 * - 自动压缩触发
 * - 智能摘要生成
 * - 历史归档
 * - 选择性保留
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class CompactService {
    
    /**
     * 压缩配置
     */
    private final CompactConfig config;
    
    /**
     * 自动压缩触发器
     */
    private final AutoCompactTrigger trigger;
    
    /**
     * 压缩历史记录
     */
    private final List<CompactRecord> compactHistory;
    
    /**
     * 压缩监听器
     */
    private final List<Consumer<CompactEvent>> compactListeners;
    
    /**
     * 最大历史记录数量
     */
    private static final int MAX_HISTORY_SIZE = 100;
    
    /**
     * 构造函数
     */
    public CompactService() {
        this(CompactConfig.defaultConfig());
    }
    
    /**
     * 构造函数
     * 
     * @param config 压缩配置
     */
    public CompactService(CompactConfig config) {
        this.config = config;
        this.trigger = new AutoCompactTrigger(config);
        this.compactHistory = new CopyOnWriteArrayList<>();
        this.compactListeners = new CopyOnWriteArrayList<>();
    }
    
    /**
     * 添加压缩监听器
     */
    public void addCompactListener(Consumer<CompactEvent> listener) {
        this.compactListeners.add(listener);
    }
    
    /**
     * 移除压缩监听器
     */
    public void removeCompactListener(Consumer<CompactEvent> listener) {
        this.compactListeners.remove(listener);
    }
    
    /**
     * 获取配置
     */
    public CompactConfig getConfig() {
        return config;
    }
    
    /**
     * 检查是否需要压缩
     * 
     * @param session 会话信息
     * @return true 如果需要压缩
     */
    public boolean needsCompact(SessionInfo session) {
        return trigger.shouldCompact(session);
    }
    
    /**
     * 执行压缩
     * 
     * @param session 会话信息
     * @return 压缩结果
     */
    public CompactResult compact(SessionInfo session) {
        return compact(session, config.getStrategy());
    }
    
    /**
     * 执行压缩（指定策略）
     * 
     * @param session 会话信息
     * @param strategy 压缩策略
     * @return 压缩结果
     */
    public CompactResult compact(SessionInfo session, CompactStrategy strategy) {
        Instant startTime = Instant.now();
        
        CompactResult result;
        switch (strategy) {
            case SUMMARIZE:
                result = summarizeCompact(session);
                break;
            case TRUNCATE:
                result = truncateCompact(session);
                break;
            case KEY_POINTS:
                result = keyPointsCompact(session);
                break;
            case HYBRID:
                result = hybridCompact(session);
                break;
            default:
                result = summarizeCompact(session);
        }
        
        Instant endTime = Instant.now();
        result.setDurationMs(java.time.Duration.between(startTime, endTime).toMillis());
        
        // 记录压缩历史
        recordCompact(session.getId(), result);
        
        // 通知监听器
        emitCompactEvent(new CompactEvent(CompactEventType.COMPACTED, session.getId(), result));
        
        return result;
    }
    
    /**
     * 摘要压缩
     */
    private CompactResult summarizeCompact(SessionInfo session) {
        CompactResult result = new CompactResult();
        result.setStrategy(CompactStrategy.SUMMARIZE);
        
        // 计算需要压缩的消息数量
        int messagesToCompact = calculateMessagesToCompact(session);
        
        if (messagesToCompact <= 0) {
            result.setSuccess(true);
            result.setMessagesCompacted(0);
            result.setTokensSaved(0);
            return result;
        }
        
        // 生成摘要
        String summary = generateSummary(session.getMessages().subList(0, messagesToCompact));
        result.setSummary(summary);
        
        // 保留摘要和剩余消息
        List<SessionMessage> retainedMessages = new ArrayList<>();
        retainedMessages.add(new SessionMessage("system", summary));
        retainedMessages.addAll(session.getMessages().subList(messagesToCompact, session.getMessages().size()));
        
        result.setRetainedMessages(retainedMessages);
        result.setMessagesCompacted(messagesToCompact);
        result.setTokensSaved(estimateTokens(messagesToCompact, session));
        result.setSuccess(true);
        
        return result;
    }
    
    /**
     * 截断压缩
     */
    private CompactResult truncateCompact(SessionInfo session) {
        CompactResult result = new CompactResult();
        result.setStrategy(CompactStrategy.TRUNCATE);
        
        int messagesToKeep = config.getMaxMessages();
        
        if (session.getMessages().size() <= messagesToKeep) {
            result.setSuccess(true);
            result.setMessagesCompacted(0);
            result.setTokensSaved(0);
            return result;
        }
        
        int messagesToRemove = session.getMessages().size() - messagesToKeep;
        List<SessionMessage> retainedMessages = session.getMessages()
                .subList(messagesToRemove, session.getMessages().size());
        
        result.setRetainedMessages(retainedMessages);
        result.setMessagesCompacted(messagesToRemove);
        result.setTokensSaved(estimateTokens(messagesToRemove, session));
        result.setSuccess(true);
        
        return result;
    }
    
    /**
     * 关键点压缩
     */
    private CompactResult keyPointsCompact(SessionInfo session) {
        CompactResult result = new CompactResult();
        result.setStrategy(CompactStrategy.KEY_POINTS);
        
        // 提取关键点
        List<String> keyPoints = extractKeyPoints(session);
        
        // 构建保留的消息
        List<SessionMessage> retainedMessages = new ArrayList<>();
        
        // 添加关键点摘要
        String summary = "关键信息摘要:\n" + String.join("\n", keyPoints);
        retainedMessages.add(new SessionMessage("system", summary));
        
        // 保留最近的对话
        int recentMessagesToKeep = Math.min(10, session.getMessages().size());
        if (recentMessagesToKeep > 0) {
            retainedMessages.addAll(session.getMessages()
                    .subList(session.getMessages().size() - recentMessagesToKeep, 
                            session.getMessages().size()));
        }
        
        result.setRetainedMessages(retainedMessages);
        result.setMessagesCompacted(session.getMessages().size() - retainedMessages.size());
        result.setSuccess(true);
        
        return result;
    }
    
    /**
     * 混合压缩
     */
    private CompactResult hybridCompact(SessionInfo session) {
        CompactResult result = new CompactResult();
        result.setStrategy(CompactStrategy.HYBRID);
        
        // 首先尝试摘要
        CompactResult summaryResult = summarizeCompact(session);
        
        // 如果摘要后仍然超出限制，进行截断
        if (estimateTokens(summaryResult.getRetainedMessages().size(), session) > config.getTargetTokens()) {
            int maxMessages = config.getMaxMessages();
            List<SessionMessage> truncatedMessages = summaryResult.getRetainedMessages();
            
            if (truncatedMessages.size() > maxMessages) {
                truncatedMessages = truncatedMessages.subList(
                        truncatedMessages.size() - maxMessages, 
                        truncatedMessages.size());
            }
            summaryResult.setRetainedMessages(truncatedMessages);
        }
        
        return summaryResult;
    }
    
    /**
     * 计算需要压缩的消息数量
     */
    private int calculateMessagesToCompact(SessionInfo session) {
        int currentTokens = session.getTokenCount();
        int targetTokens = config.getTargetTokens();
        
        if (currentTokens <= targetTokens) {
            return 0;
        }
        
        // 估算需要移除的消息数量
        int tokensToSave = currentTokens - targetTokens;
        int avgTokensPerMessage = currentTokens / Math.max(1, session.getMessages().size());
        
        return Math.min(
                (int) Math.ceil((double) tokensToSave / avgTokensPerMessage),
                session.getMessages().size() - config.getMinMessages()
        );
    }
    
    /**
     * 生成摘要（简化实现）
     */
    private String generateSummary(List<SessionMessage> messages) {
        StringBuilder summary = new StringBuilder();
        summary.append("[会话摘要] ");
        summary.append("共 ").append(messages.size()).append(" 条消息。");
        
        // 提取关键信息
        Set<String> topics = new HashSet<>();
        for (SessionMessage msg : messages) {
            if (msg.getRole().equals("user")) {
                // 简化：提取消息的前几个字符作为主题
                String content = msg.getContent();
                if (content != null && content.length() > 0) {
                    topics.add(content.substring(0, Math.min(20, content.length())));
                }
            }
        }
        
        if (!topics.isEmpty()) {
            summary.append("讨论主题：").append(String.join(", ", topics));
        }
        
        return summary.toString();
    }
    
    /**
     * 提取关键点（简化实现）
     */
    private List<String> extractKeyPoints(SessionInfo session) {
        List<String> keyPoints = new ArrayList<>();
        
        // 查找包含重要关键词的消息
        List<String> importantKeywords = Arrays.asList(
                "重要", "注意", "记住", "关键", "必须", "应该", "不要"
        );
        
        for (SessionMessage msg : session.getMessages()) {
            String content = msg.getContent();
            if (content != null) {
                for (String keyword : importantKeywords) {
                    if (content.contains(keyword)) {
                        keyPoints.add("- " + content.substring(0, Math.min(50, content.length())));
                        break;
                    }
                }
            }
        }
        
        return keyPoints.subList(0, Math.min(5, keyPoints.size()));
    }
    
    /**
     * 估算 Token 数量
     */
    private int estimateTokens(int messageCount, SessionInfo session) {
        if (session.getMessages().isEmpty()) {
            return 0;
        }
        int avgTokensPerMessage = session.getTokenCount() / session.getMessages().size();
        return messageCount * avgTokensPerMessage;
    }
    
    /**
     * 估算消息 Token 数量
     */
    public int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // 简化估算：中文字符约 1.5 个 Token，英文单词约 1.3 个 Token
        int chineseChars = 0;
        int englishChars = 0;
        
        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fff') {
                chineseChars++;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                englishChars++;
            }
        }
        
        int englishWords = englishChars / 5; // 估算英文单词数
        return (int) (chineseChars * 1.5 + englishWords * 1.3);
    }
    
    /**
     * 记录压缩历史
     */
    private void recordCompact(String sessionId, CompactResult result) {
        CompactRecord record = new CompactRecord(
                UUID.randomUUID().toString(),
                sessionId,
                result.getStrategy(),
                result.getMessagesCompacted(),
                result.getTokensSaved(),
                result.getDurationMs(),
                Instant.now()
        );
        
        compactHistory.add(record);
        
        // 限制历史记录大小
        while (compactHistory.size() > MAX_HISTORY_SIZE) {
            compactHistory.remove(0);
        }
    }
    
    /**
     * 获取压缩历史
     */
    public List<CompactRecord> getHistory(int limit) {
        int size = compactHistory.size();
        if (limit >= size) {
            return new ArrayList<>(compactHistory);
        }
        return new ArrayList<>(compactHistory.subList(size - limit, size));
    }
    
    /**
     * 清除压缩历史
     */
    public void clearHistory() {
        compactHistory.clear();
    }
    
    /**
     * 获取统计信息
     */
    public CompactStats getStats() {
        int totalCompactions = compactHistory.size();
        int totalMessagesCompacted = 0;
        int totalTokensSaved = 0;
        long totalDuration = 0;
        
        for (CompactRecord record : compactHistory) {
            totalMessagesCompacted += record.getMessagesCompacted();
            totalTokensSaved += record.getTokensSaved();
            totalDuration += record.getDurationMs();
        }
        
        return new CompactStats(
                totalCompactions,
                totalMessagesCompacted,
                totalTokensSaved,
                totalCompactions > 0 ? totalDuration / totalCompactions : 0
        );
    }
    
    /**
     * 发射压缩事件
     */
    private void emitCompactEvent(CompactEvent event) {
        for (Consumer<CompactEvent> listener : compactListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                // 忽略监听器异常
            }
        }
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 会话信息类
     */
    public static class SessionInfo {
        private final String id;
        private final List<SessionMessage> messages;
        private final int tokenCount;
        
        public SessionInfo(String id, List<SessionMessage> messages, int tokenCount) {
            this.id = id;
            this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
            this.tokenCount = tokenCount;
        }
        
        public String getId() {
            return id;
        }
        
        public List<SessionMessage> getMessages() {
            return new ArrayList<>(messages);
        }
        
        public int getTokenCount() {
            return tokenCount;
        }
    }
    
    /**
     * 会话消息类
     */
    public static class SessionMessage {
        private final String role;
        private final String content;
        
        public SessionMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
        
        public String getRole() {
            return role;
        }
        
        public String getContent() {
            return content;
        }
    }
    
    /**
     * 压缩事件类
     */
    public static class CompactEvent {
        private final CompactEventType type;
        private final String sessionId;
        private final CompactResult result;
        private final Instant timestamp;
        
        public CompactEvent(CompactEventType type, String sessionId, CompactResult result) {
            this.type = type;
            this.sessionId = sessionId;
            this.result = result;
            this.timestamp = Instant.now();
        }
        
        public CompactEventType getType() {
            return type;
        }
        
        public String getSessionId() {
            return sessionId;
        }
        
        public CompactResult getResult() {
            return result;
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
    }
    
    /**
     * 压缩事件类型枚举
     */
    public enum CompactEventType {
        TRIGGERED,
        COMPACTED,
        FAILED,
        SKIPPED
    }
}
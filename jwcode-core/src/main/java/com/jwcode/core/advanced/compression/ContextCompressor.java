package com.jwcode.core.advanced.compression;

import com.jwcode.core.model.Message;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Context Compression - 上下文压缩
 * 
 * 参照 Kimi Code 的 /compact 命令
 * 当上下文接近限制时，自动压缩历史对话保留关键信息
 */
public class ContextCompressor {
    
    private static final int DEFAULT_MAX_MESSAGES = 50;
    private static final double COMPRESSION_RATIO = 0.5;  // 压缩到原来的一半
    
    private final CompressionConfig config;
    private final List<CompressedSegment> compressionHistory;
    
    public ContextCompressor() {
        this.config = CompressionConfig.defaultConfig();
        this.compressionHistory = new ArrayList<>();
    }
    
    /**
     * 检查是否需要压缩
     */
    public boolean needsCompression(List<Message> messages) {
        return messages.size() > config.getMaxMessagesBeforeCompression() ||
               estimateTokenCount(messages) > config.getTokenThreshold();
    }
    
    /**
     * 执行压缩
     */
    public CompressionResult compress(List<Message> messages) {
        System.out.println("[ContextCompression] 开始压缩，原始消息数: " + messages.size());
        
        // 1. 分析消息重要性
        List<MessageImportance> scoredMessages = scoreMessages(messages);
        
        // 2. 分离保留和压缩的消息
        int keepCount = (int) (messages.size() * (1 - COMPRESSION_RATIO));
        List<Message> keepMessages = scoredMessages.stream()
            .sorted(Comparator.comparingInt(MessageImportance::getImportance).reversed())
            .limit(keepCount)
            .map(MessageImportance::getMessage)
            .sorted(Comparator.comparingInt(messages::indexOf))  // 保持原始顺序
            .collect(Collectors.toList());
        
        // 3. 压缩其余消息为摘要
        List<MessageImportance> compressMessages = scoredMessages.stream()
            .sorted(Comparator.comparingInt(MessageImportance::getImportance).reversed())
            .skip(keepCount)
            .collect(Collectors.toList());
        
        String summary = generateSummary(compressMessages);
        
        // 4. 创建压缩段
        CompressedSegment segment = CompressedSegment.builder()
            .originalMessageCount(messages.size())
            .compressedMessageCount(keepMessages.size() + 1)  // +1 for summary
            .summary(summary)
            .timestamp(System.currentTimeMillis())
            .build();
        
        compressionHistory.add(segment);
        
        CompressionResult result = CompressionResult.builder()
            .originalCount(messages.size())
            .compressedCount(keepMessages.size() + 1)
            .compressionRatio((double) (keepMessages.size() + 1) / messages.size())
            .summary(summary)
            .keptMessages(keepMessages.size())
            .savedTokens(estimateTokenCount(messages) - estimateTokenCount(keepMessages) - summary.length() / 4)
            .build();
        
        System.out.println("[ContextCompression] 压缩完成: " + result.getOriginalCount() + " -> " + 
                 result.getCompressedCount() + " (" + String.format("%.1f%%", result.getCompressionRatio() * 100) + ")");
        
        return result;
    }
    
    /**
     * 为消息评分（重要性）
     */
    private List<MessageImportance> scoreMessages(List<Message> messages) {
        List<MessageImportance> scored = new ArrayList<>();
        
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            int importance = calculateImportance(msg, i, messages.size());
            scored.add(new MessageImportance(msg, importance));
        }
        
        return scored;
    }
    
    /**
     * 计算消息重要性
     */
    private int calculateImportance(Message message, int index, int total) {
        int score = 50;  // 基础分
        String content = getMessageContent(message).toLowerCase();
        
        // 1. 最新消息更重要（时间衰减）
        double recencyBonus = ((double) index / total) * 30;
        score += (int) recencyBonus;
        
        // 2. 包含关键信息加分
        if (content.contains("error") || content.contains("exception")) score += 20;
        if (content.contains("fix") || content.contains("解决")) score += 15;
        if (content.contains("TODO") || content.contains("FIXME")) score += 10;
        if (content.contains("important") || content.contains("关键")) score += 15;
        
        // 3. 包含代码块加分
        if (content.contains("```")) score += 10;
        
        // 4. 系统消息和工具结果加分
        if (message.getRole() == Message.Role.SYSTEM) score += 25;
        
        // 5. 长消息可能包含更多信息（适度加分）
        int length = content.length();
        if (length > 500) score += 5;
        
        return Math.min(100, score);  // 最高100分
    }
    
    /**
     * 生成摘要
     */
    private String generateSummary(List<MessageImportance> messages) {
        StringBuilder summary = new StringBuilder();
        summary.append("【历史对话摘要】\n");
        summary.append("共 ").append(messages.size()).append(" 条消息被压缩\n\n");
        
        // 按主题分组
        Map<String, List<String>> topics = new HashMap<>();
        
        for (MessageImportance mi : messages) {
            String content = getMessageContent(mi.getMessage());
            String topic = extractTopic(content);
            topics.computeIfAbsent(topic, k -> new ArrayList<>()).add(content.substring(0, Math.min(100, content.length())));
        }
        
        // 生成主题摘要
        topics.forEach((topic, contents) -> {
            summary.append("• ").append(topic).append(" (").append(contents.size()).append(" 条)\n");
        });
        
        // 提取关键决策点
        List<String> decisions = extractDecisions(messages);
        if (!decisions.isEmpty()) {
            summary.append("\n关键决策点:\n");
            decisions.forEach(d -> summary.append("  - ").append(d).append("\n"));
        }
        
        return summary.toString();
    }
    
    /**
     * 提取主题
     */
    private String extractTopic(String content) {
        if (content.contains("bug") || content.contains("fix")) return "Bug修复";
        if (content.contains("test")) return "测试";
        if (content.contains("refactor")) return "重构";
        if (content.contains("doc")) return "文档";
        if (content.contains("feature")) return "功能开发";
        if (content.contains("error")) return "错误处理";
        return "一般讨论";
    }
    
    /**
     * 提取决策点
     */
    private List<String> extractDecisions(List<MessageImportance> messages) {
        List<String> decisions = new ArrayList<>();
        
        for (MessageImportance mi : messages) {
            String content = getMessageContent(mi.getMessage());
            // 寻找决策性语句
            if (content.contains("决定") || content.contains("decide") ||
                content.contains("使用") || content.contains("采用") ||
                content.contains("选择") || content.contains("方案")) {
                String decision = content.replaceAll("(?s).*?(决定|decide|使用|采用)", "$1")
                                        .substring(0, Math.min(50, content.length()));
                if (!decisions.contains(decision)) {
                    decisions.add(decision);
                }
            }
        }
        
        return decisions.stream().limit(5).collect(Collectors.toList());
    }
    
    /**
     * 创建摘要消息（简化实现）
     */
    private String createSummaryMessage(String summary) {
        return "[COMPACTED] " + summary;
    }
    
    /**
     * 估算 token 数
     */
    private int estimateTokenCount(List<Message> messages) {
        int totalChars = messages.stream()
            .mapToInt(m -> getMessageContent(m).length())
            .sum();
        // 粗略估算：1 token ≈ 4 字符
        return totalChars / 4;
    }
    
    private String getMessageContent(Message message) {
        // 简化实现
        return message.toString();
    }
    
    /**
     * 获取压缩历史
     */
    public List<CompressedSegment> getCompressionHistory() {
        return new ArrayList<>(compressionHistory);
    }
    
    /**
     * 生成压缩报告
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("╔════════════════════════════════════════════════════════╗\n");
        report.append("║           上下文压缩报告                               ║\n");
        report.append("╚════════════════════════════════════════════════════════╝\n\n");
        report.append("压缩次数: ").append(compressionHistory.size()).append("\n");
        
        if (!compressionHistory.isEmpty()) {
            int totalSaved = compressionHistory.stream()
                .mapToInt(s -> s.getOriginalMessageCount() - s.getCompressedMessageCount())
                .sum();
            report.append("累计节省消息: ").append(totalSaved).append("\n\n");
            
            report.append("压缩历史:\n");
            for (int i = 0; i < compressionHistory.size(); i++) {
                CompressedSegment seg = compressionHistory.get(i);
                report.append(String.format("  %d. %d -> %d 消息%n", 
                    i + 1, seg.getOriginalMessageCount(), seg.getCompressedMessageCount()));
            }
        }
        
        return report.toString();
    }
    
    // ==================== 数据类 ====================
    
    public static class CompressionConfig {
        private int maxMessagesBeforeCompression = 50;
        private int tokenThreshold = 8000;
        
        public CompressionConfig() {}
        
        public int getMaxMessagesBeforeCompression() { return maxMessagesBeforeCompression; }
        public void setMaxMessagesBeforeCompression(int v) { this.maxMessagesBeforeCompression = v; }
        public int getTokenThreshold() { return tokenThreshold; }
        public void setTokenThreshold(int v) { this.tokenThreshold = v; }
        
        public static CompressionConfig defaultConfig() {
            return new CompressionConfig();
        }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private int maxMessagesBeforeCompression = 50;
            private int tokenThreshold = 8000;
            
            public Builder maxMessagesBeforeCompression(int v) { this.maxMessagesBeforeCompression = v; return this; }
            public Builder tokenThreshold(int v) { this.tokenThreshold = v; return this; }
            public CompressionConfig build() {
                CompressionConfig cfg = new CompressionConfig();
                cfg.maxMessagesBeforeCompression = this.maxMessagesBeforeCompression;
                cfg.tokenThreshold = this.tokenThreshold;
                return cfg;
            }
        }
    }
    
    public static class CompressionResult {
        private int originalCount;
        private int compressedCount;
        private double compressionRatio;
        private String summary;
        private int keptMessages;
        private int savedTokens;
        
        public CompressionResult() {}
        
        public int getOriginalCount() { return originalCount; }
        public void setOriginalCount(int v) { this.originalCount = v; }
        public int getCompressedCount() { return compressedCount; }
        public void setCompressedCount(int v) { this.compressedCount = v; }
        public double getCompressionRatio() { return compressionRatio; }
        public void setCompressionRatio(double v) { this.compressionRatio = v; }
        public String getSummary() { return summary; }
        public void setSummary(String v) { this.summary = v; }
        public int getKeptMessages() { return keptMessages; }
        public void setKeptMessages(int v) { this.keptMessages = v; }
        public int getSavedTokens() { return savedTokens; }
        public void setSavedTokens(int v) { this.savedTokens = v; }
        
        public String formatReport() {
            return String.format("压缩完成: %d -> %d 消息 (%.1f%%), 节省 %d tokens",
                originalCount, compressedCount, compressionRatio * 100, savedTokens);
        }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private int originalCount;
            private int compressedCount;
            private double compressionRatio;
            private String summary;
            private int keptMessages;
            private int savedTokens;
            
            public Builder originalCount(int v) { this.originalCount = v; return this; }
            public Builder compressedCount(int v) { this.compressedCount = v; return this; }
            public Builder compressionRatio(double v) { this.compressionRatio = v; return this; }
            public Builder summary(String v) { this.summary = v; return this; }
            public Builder keptMessages(int v) { this.keptMessages = v; return this; }
            public Builder savedTokens(int v) { this.savedTokens = v; return this; }
            public CompressionResult build() {
                CompressionResult r = new CompressionResult();
                r.originalCount = this.originalCount;
                r.compressedCount = this.compressedCount;
                r.compressionRatio = this.compressionRatio;
                r.summary = this.summary;
                r.keptMessages = this.keptMessages;
                r.savedTokens = this.savedTokens;
                return r;
            }
        }
    }
    
    public static class CompressedSegment {
        private long timestamp;
        private int originalMessageCount;
        private int compressedMessageCount;
        private String summary;
        
        public CompressedSegment() {}
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long v) { this.timestamp = v; }
        public int getOriginalMessageCount() { return originalMessageCount; }
        public void setOriginalMessageCount(int v) { this.originalMessageCount = v; }
        public int getCompressedMessageCount() { return compressedMessageCount; }
        public void setCompressedMessageCount(int v) { this.compressedMessageCount = v; }
        public String getSummary() { return summary; }
        public void setSummary(String v) { this.summary = v; }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private long timestamp;
            private int originalMessageCount;
            private int compressedMessageCount;
            private String summary;
            
            public Builder timestamp(long v) { this.timestamp = v; return this; }
            public Builder originalMessageCount(int v) { this.originalMessageCount = v; return this; }
            public Builder compressedMessageCount(int v) { this.compressedMessageCount = v; return this; }
            public Builder summary(String v) { this.summary = v; return this; }
            public CompressedSegment build() {
                CompressedSegment s = new CompressedSegment();
                s.timestamp = this.timestamp;
                s.originalMessageCount = this.originalMessageCount;
                s.compressedMessageCount = this.compressedMessageCount;
                s.summary = this.summary;
                return s;
            }
        }
    }
    
    private static class MessageImportance {
        private final Message message;
        private final int importance;
        
        MessageImportance(Message message, int importance) {
            this.message = message;
            this.importance = importance;
        }
        
        public Message getMessage() { return message; }
        public int getImportance() { return importance; }
    }
}

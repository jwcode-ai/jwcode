package com.jwcode.core.service.structured;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 评估结果
 * 
 * AI 评估返回的结构化结果，包含保留和丢弃的建议
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class EvaluationResult {
    
    private final String sessionId;
    private final EvaluationTrigger triggerReason;
    private final int activeCountBefore;
    private final int activeCountAfter;
    private final List<RetainRecommendation> retain;
    private final List<DropRecommendation> drop;
    private final String summary;
    
    public EvaluationResult(String sessionId, EvaluationTrigger triggerReason, 
                       int activeCountBefore, int activeCountAfter,
                       List<RetainRecommendation> retain, List<DropRecommendation> drop, String summary) {
        this.sessionId = sessionId;
        this.triggerReason = triggerReason;
        this.activeCountBefore = activeCountBefore;
        this.activeCountAfter = activeCountAfter;
        this.retain = retain;
        this.drop = drop;
        this.summary = summary;
    }
    
    // Getters
    public String getSessionId() { return sessionId; }
    public EvaluationTrigger getTriggerReason() { return triggerReason; }
    public int getActiveCountBefore() { return activeCountBefore; }
    public int getActiveCountAfter() { return activeCountAfter; }
    public List<RetainRecommendation> getRetain() { return retain; }
    public List<DropRecommendation> getDrop() { return drop; }
    public String getSummary() { return summary; }
    
    /**
     * 获取建议保留的消息ID列表
     */
    public List<String> getRetainMessageIds() {
        List<String> ids = new ArrayList<>();
        for (RetainRecommendation r : retain) {
            ids.add(r.getMessageId());
        }
        return ids;
    }
    
    /**
     * 获取建议丢弃的消息ID列表
     */
    public List<String> getDropMessageIds() {
        List<String> ids = new ArrayList<>();
        for (DropRecommendation d : drop) {
            ids.add(d.getMessageId());
        }
        return ids;
    }
    
    /**
     * 保留建议
     */
    public static class RetainRecommendation {
        private final String messageId;
        private final String reason;
        
        public RetainRecommendation(String messageId, String reason) {
            this.messageId = messageId;
            this.reason = reason;
        }
        
        public String getMessageId() { return messageId; }
        public String getReason() { return reason; }
    }
    
    /**
     * 丢弃建议
     */
    public static class DropRecommendation {
        private final String messageId;
        private final String reason;
        private final boolean isRecoverable;
        
        public DropRecommendation(String messageId, String reason, boolean isRecoverable) {
            this.messageId = messageId;
            this.reason = reason;
            this.isRecoverable = isRecoverable;
        }
        
        public String getMessageId() { return messageId; }
        public String getReason() { return reason; }
        public boolean isRecoverable() { return isRecoverable; }
    }
    
    @Override
    public String toString() {
        return String.format(
            "EvaluationResult{trigger=%s, before=%d, after=%d, retain=%d, drop=%d, summary=%s}",
            triggerReason, activeCountBefore, activeCountAfter, retain.size(), drop.size(), summary
        );
    }
}
package com.jwcode.core.service.structured;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 上下文安全守卫
 * 
 * 对 AI 评估结果进行校验，防止过度丢弃导致上下文丢失
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ContextSafetyGuard {
    
    private static final Logger logger = Logger.getLogger(ContextSafetyGuard.class.getName());
    
    // 最少保留消息数（默认）
    private int minRetainCount = 5;
    // 最近N条消息保护
    private int recentMessageProtection = 5;
    // 是否启用意图保护
    private boolean enableIntentProtection = true;
    // 是否启用引用计数保护
    private boolean enableRefCountProtection = true;
    // 是否启用最近消息保护
    private boolean enableRecentProtection = true;
    
    public ContextSafetyGuard() {
    }
    
    public ContextSafetyGuard(int minRetainCount) {
        this.minRetainCount = minRetainCount;
    }
    
    // Setters
    public void setMinRetainCount(int minRetainCount) { this.minRetainCount = minRetainCount; }
    public void setRecentMessageProtection(int recentMessageProtection) { this.recentMessageProtection = recentMessageProtection; }
    public void setEnableIntentProtection(boolean enableIntentProtection) { this.enableIntentProtection = enableIntentProtection; }
    public void setEnableRefCountProtection(boolean enableRefCountProtection) { this.enableRefCountProtection = enableRefCountProtection; }
    public void setEnableRecentProtection(boolean enableRecentProtection) { this.enableRecentProtection = enableRecentProtection; }
    
    // Getters
    public int getMinRetainCount() { return minRetainCount; }
    public int getRecentMessageProtection() { return recentMessageProtection; }
    
    /**
     * 校验评估结果是否安全
     * 
     * @param activeMessages 当前活跃消息列表
     * @param evaluationResult AI 评估结果
     * @return 校验后的安全丢弃列表
     */
    public List<String> validate(List<StructuredMessage> activeMessages, EvaluationResult evaluationResult) {
        List<String> safeDropIds = new ArrayList<>();
        List<String> proposedDropIds = evaluationResult.getDropMessageIds();
        
        for (String dropId : proposedDropIds) {
            if (canDrop(activeMessages, dropId)) {
                safeDropIds.add(dropId);
            } else {
                logger.info("[ContextSafetyGuard] 阻止丢弃消息: " + dropId + " (安全规则保护)");
            }
        }
        
        // 检查最少保留条数
        int remainingCount = activeMessages.size() - safeDropIds.size();
        if (remainingCount < minRetainCount) {
            // 恢复一些消息直到达到最少保留数
            int needToRestore = minRetainCount - remainingCount;
            logger.info("[ContextSafetyGuard] 达到最少保留限制，需要恢复 " + needToRestore + " 条消息");
            
            // 从丢弃列表中移除最近的消息
            List<String> safeDropList = new ArrayList<>(safeDropIds);
            for (int i = safeDropList.size() - 1; i >= 0 && needToRestore > 0; i--) {
                String msgId = safeDropList.get(i);
                safeDropIds.remove(msgId);
                needToRestore--;
            }
        }
        
        return safeDropIds;
    }
    
    /**
     * 检查指定消息是否可以丢弃
     */
    private boolean canDrop(List<StructuredMessage> activeMessages, String messageId) {
        // 查找消息
        StructuredMessage targetMsg = findMessage(activeMessages, messageId);
        if (targetMsg == null) {
            return false; // 消息不存在
        }
        
        // 规则1：不能丢弃用户意图
        if (enableIntentProtection && targetMsg.getType() == MessageType.INTENT) {
            logger.info("[ContextSafetyGuard] 阻止丢弃 INTENT 消息: " + messageId);
            return false;
        }
        
        // 规则2：如果消息被引用，不能丢弃
        if (enableRefCountProtection && targetMsg.getRefCount() > 0) {
            logger.info("[ContextSafetyGuard] 阻止丢弃引用消息: " + messageId + " (refCount=" + targetMsg.getRefCount() + ")");
            return false;
        }
        
        // 规则3：最近N条消息不能丢弃
        if (enableRecentProtection) {
            int recentIndex = activeMessages.size() - recentMessageProtection;
            for (int i = Math.max(0, recentIndex); i < activeMessages.size(); i++) {
                if (activeMessages.get(i).getId().equals(messageId)) {
                    logger.info("[ContextSafetyGuard] 阻止丢弃最近消息: " + messageId);
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * 在消息列表中查找消息
     */
    private StructuredMessage findMessage(List<StructuredMessage> messages, String messageId) {
        for (StructuredMessage msg : messages) {
            if (msg.getId().equals(messageId)) {
                return msg;
            }
        }
        return null;
    }
    
    /**
     * 规则1：检查最少保留条数
     */
    public boolean checkMinRetainCount(List<StructuredMessage> activeMessages, int dropCount) {
        return (activeMessages.size() - dropCount) >= minRetainCount;
    }
    
    /**
     * 规则2：检查用户意图是否被保留
     */
    public boolean checkIntentPreservation(List<StructuredMessage> activeMessages, List<String> dropIds) {
        for (StructuredMessage msg : activeMessages) {
            if (msg.getType() == MessageType.INTENT && dropIds.contains(msg.getId())) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 规则3：检查引用计数
     */
    public boolean checkRefCount(List<StructuredMessage> activeMessages, List<String> dropIds) {
        for (StructuredMessage msg : activeMessages) {
            if (dropIds.contains(msg.getId()) && msg.getRefCount() > 0) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 规则5：检查最近消息
     */
    public boolean checkRecentMessages(List<StructuredMessage> messages, int n, List<String> dropIds) {
        int startIndex = Math.max(0, messages.size() - n);
        for (int i = startIndex; i < messages.size(); i++) {
            if (dropIds.contains(messages.get(i).getId())) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 检查任务链是否完整
     */
    public boolean checkTaskChain(TaskContext task, List<String> dropIds) {
        if (task == null) {
            return true;
        }
        for (String keyMsgId : task.getKeyMessageIds()) {
            if (dropIds.contains(keyMsgId)) {
                return false;
            }
        }
        return true;
    }
}
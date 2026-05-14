package com.jwcode.core.service.structured;

import com.jwcode.core.aicl.BlockLifecycle;
import com.jwcode.core.aicl.BlockPriority;
import com.jwcode.core.aicl.ContextBlock;
import com.jwcode.core.aicl.ContextAssembler;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 上下文安全守卫
 * 
 * 对 AI 评估结果进行校验，防止过度丢弃导致上下文丢失。
 * v1.1 新增 AICL 感知的安全规则（TTL、代际上限、pinned 保护）。
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

    // ===== AICL 安全规则（v1.1） =====
    /** 最大压缩代际（防止无限摘要） */
    private int maxGeneration = 2;
    /** 是否启用 pinned 保护 */
    private boolean enablePinnedProtection = true;
    /** 是否启用受保护优先级保护（critical+） */
    private boolean enableProtectedPriority = true;
    
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
    
    // AICL Setters
    public void setMaxGeneration(int maxGeneration) { this.maxGeneration = maxGeneration; }
    public void setEnablePinnedProtection(boolean enablePinnedProtection) { this.enablePinnedProtection = enablePinnedProtection; }
    public void setEnableProtectedPriority(boolean enableProtectedPriority) { this.enableProtectedPriority = enableProtectedPriority; }
    
    // Getters
    public int getMinRetainCount() { return minRetainCount; }
    public int getRecentMessageProtection() { return recentMessageProtection; }
    public int getMaxGeneration() { return maxGeneration; }
    
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

    // ==================== AICL 安全规则（v1.1） ====================

    /**
     * 校验 AICL ContextBlock 是否可以淘汰。
     */
    public boolean canEvictBlock(ContextBlock block) {
        // 规则 A1: pinned 块永不淘汰
        if (enablePinnedProtection && block.getPriority() == BlockPriority.PINNED) {
            logger.fine("[ContextSafetyGuard] pinned block protected: " + block.getId());
            return false;
        }

        // 规则 A2: critical 块限制淘汰动作
        if (enableProtectedPriority && block.getPriority().isProtected()) {
            // 仅允许 trim_comments，不允许 archive/summarize/remove
            if (block.getState() == BlockLifecycle.COMPRESSED
                    || block.getState() == BlockLifecycle.SUMMARIZED
                    || block.getState() == BlockLifecycle.ARCHIVED) {
                logger.fine("[ContextSafetyGuard] critical block over-compressed: " + block.getId());
                return false;
            }
        }

        // 规则 A3: 代际保护 — 超过 maxGeneration 的不再压缩（应直接移除）
        if (block.getGeneration() >= maxGeneration) {
            // 超过代际上限，可以移除
            if (block.getState() == BlockLifecycle.ARCHIVED
                    || block.getState() == BlockLifecycle.DEPRECATED) {
                return true;
            }
        }

        // 规则 A4: TTL 未到期的不应强制淘汰
        // 允许淘汰，但优先级最低

        return true; // 默认允许
    }

    /**
     * 校验 AICL 淘汰统计是否安全。
     */
    public boolean isEvictionSafe(ContextAssembler.EvictionStats stats, int totalBlocks) {
        // 至少保留最小数量的活跃块
        int removedTotal = stats.getRemovedCount() + stats.getArchivedCount();
        int remainingBlocks = totalBlocks - removedTotal;
        return remainingBlocks >= minRetainCount;
    }
}
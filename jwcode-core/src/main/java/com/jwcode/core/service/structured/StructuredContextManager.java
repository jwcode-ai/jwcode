package com.jwcode.core.service.structured;

import com.jwcode.core.aicl.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 结构化上下文管理器
 * 
 * 核心组件：管理活跃消息、任务上下文、归档记录
 * 支持 AI 评估与主动遗忘机制。v1.1 新增 AICL 协议集成。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class StructuredContextManager {
    
    private static final Logger logger = Logger.getLogger(StructuredContextManager.class.getName());
    
    // ==================== 配置 ====================
    private final String sessionId;
    private final int maxActiveSize;
    private final int minRetainCount;
    private final boolean enableArchive;
    private final int periodicEvalInterval;
    
    // ==================== 核心数据结构 ====================
    private final Map<String, StructuredMessage> messageStore;    // ID -> Message
    private final List<StructuredMessage> activeMessages;      // 有序活跃消息列表
    private final Map<String, TaskContext> taskContexts;          // TaskId -> TaskContext
    private final Map<String, ArchiveRecord> archiveStore;    // MessageId -> ArchiveRecord
    
    // ==================== 状态 ====================
    private ContextSafetyGuard safetyGuard;
    private final AtomicInteger totalDiscarded;
    private final AtomicInteger totalArchived;
    private Instant lastEvaluationTime;
    private int evaluationCount;
    private int messageCounter;

    // ===== AICL 集成（v1.1） =====
    private ContextAssembler aiclAssembler;
    private AICLSerializer aiclSerializer;
    private boolean aiclEnabled;
    
    // ==================== AI 评估器（可选）====================
    private AiEvaluator aiEvaluator;
    
    public StructuredContextManager() {
        this("default", 50, 5, true, 10);
    }
    
    public StructuredContextManager(String sessionId, int maxActiveSize, int minRetainCount, 
                         boolean enableArchive, int periodicEvalInterval) {
        this.sessionId = sessionId;
        this.maxActiveSize = maxActiveSize;
        this.minRetainCount = minRetainCount;
        this.enableArchive = enableArchive;
        this.periodicEvalInterval = periodicEvalInterval;
        
        this.messageStore = new ConcurrentHashMap<>();
        this.activeMessages = new ArrayList<>();
        this.taskContexts = new ConcurrentHashMap<>();
        this.archiveStore = new HashMap<>();
        
        this.safetyGuard = new ContextSafetyGuard(minRetainCount);
        this.totalDiscarded = new AtomicInteger(0);
        this.totalArchived = new AtomicInteger(0);
        this.lastEvaluationTime = Instant.now();
        this.messageCounter = 0;
        
        logger.info("[StructuredContextManager] 初始化完成: sessionId=" + sessionId 
                + ", maxActiveSize=" + maxActiveSize + ", minRetainCount=" + minRetainCount);
    }
    
    // ==================== 核心方法 ====================
    
    /**
     * 添加消息到活跃列表
     */
    public void addMessage(StructuredMessage message) {
        if (message == null || message.getContent() == null) {
            return;
        }
        
        // 为消息分配ID和设置索引
        if (message.getId() == null) {
            message = new StructuredMessage(
                "msg_" + messageCounter++,
                message.getRole(),
                message.getContent(),
                message.getMetadata(),
                message.getToolUseId(),
                message.getToolName(),
                message.getTimestamp()
            );
        }
        
        // 存储消息
        messageStore.put(message.getId(), message);
        activeMessages.add(message);
        
        // 更新引用计数（如果有依赖）
        for (String depId : message.getDependsOn()) {
            StructuredMessage depMsg = messageStore.get(depId);
            if (depMsg != null) {
                depMsg.incrementRefCount();
            }
        }
        
        logger.info("[StructuredContextManager] 添加消息: " + message.getId() 
                + ", activeCount=" + activeMessages.size());
        
        // 检查是否触发评估
        checkAndTriggerEvaluation();
    }
    
    /**
     * 添加消息（简化版本）
     */
    public void addMessage(String role, String content) {
        addMessage(new StructuredMessage(role, content));
    }
    
    /**
     * 添加消息（带类型）
     */
    public void addMessage(String role, String content, MessageType type) {
        MessageMetadata metadata = new MessageMetadata(type);
        addMessage(new StructuredMessage(role, content, metadata));
    }
    
    /**
     * 获取当前活跃消息列表
     */
    public List<StructuredMessage> getActiveMessages() {
        return new ArrayList<>(activeMessages);
    }
    
    /**
     * 获取活跃消息数量
     */
    public int getActiveCount() {
        return activeMessages.size();
    }
    
    /**
     * 获取指定消息
     */
    public StructuredMessage getMessage(String messageId) {
        return messageStore.get(messageId);
    }
    
    /**
     * 检查并触发 AI 评估
     */
    private void checkAndTriggerEvaluation() {
        // 阈值触发
        if (activeMessages.size() > maxActiveSize) {
            logger.info("[StructuredContextManager] 触发阈值评估: " + activeMessages.size() + " > " + maxActiveSize);
            triggerEvaluation(EvaluationTrigger.THRESHOLD);
            return;
        }
        
        // 定时触发
        if (periodicEvalInterval > 0 && messageCounter % periodicEvalInterval == 0) {
            logger.info("[StructuredContextManager] 触发定时评估");
            triggerEvaluation(EvaluationTrigger.PERIODIC);
        }
    }
    
    /**
     * 触发 AI 评估
     */
    public EvaluationResult triggerEvaluation(EvaluationTrigger trigger) {
        if (activeMessages.size() <= minRetainCount) {
            logger.info("[StructuredContextManager] 消息数过少，跳过评估");
            return null;
        }
        
        logger.info("[StructuredContextManager] 开始 AI 评估: trigger=" + trigger + ", activeCount=" + activeMessages.size());
        
        // 如果没有配置 AI 评估器，使用默认评估
        if (aiEvaluator == null) {
            return defaultEvaluation(trigger);
        }
        
        // 调用 AI 评估器
        try {
            EvaluationResult result = aiEvaluator.evaluate(this, trigger);
            if (result != null) {
                applyEvaluation(result);
            }
            return result;
        } catch (Exception e) {
            logger.warning("[StructuredContextManager] AI 评估异常: " + e.getMessage());
            return defaultEvaluation(trigger);
        }
    }
    
    /**
     * 默认评估策略（基于规则的简单评估）
     */
    private EvaluationResult defaultEvaluation(EvaluationTrigger trigger) {
        List<EvaluationResult.RetainRecommendation> retain = new ArrayList<>();
        List<EvaluationResult.DropRecommendation> drop = new ArrayList<>();
        
        int count = 0;
        for (StructuredMessage msg : activeMessages) {
            boolean shouldRetain = true;
            String reason = "";
            
            // 保留规则
            MessageType type = msg.getType();
            if (type == MessageType.INTENT) {
                shouldRetain = true;
                reason = "用户核心意图";
            } else if (msg.getRefCount() > 0) {
                shouldRetain = true;
                reason = "被后续消息引用";
            } else if (msg.isUserMessage() && msg.getContent().length() > 20) {
                shouldRetain = true;
                reason = "用户长消息";
            } else if (count >= activeMessages.size() - minRetainCount) {
                shouldRetain = true;
                reason = "最近消息";
            } else if (msg.isToolResultMessage()) {
                // 工具结果可以丢弃
                shouldRetain = false;
                reason = "工具执行结果";
            } else {
                shouldRetain = false;
                reason = "中间过程消息";
            }
            
            if (shouldRetain) {
                retain.add(new EvaluationResult.RetainRecommendation(msg.getId(), reason));
            } else {
                drop.add(new EvaluationResult.DropRecommendation(msg.getId(), reason, true));
            }
            count++;
        }
        
        String summary = "基于规则评估: 保留 " + retain.size() + " 条，丢弃 " + drop.size() + " 条";
        return new EvaluationResult(sessionId, trigger, activeMessages.size(), 
                               retain.size(), retain, drop, summary);
    }
    
    /**
     * 应用评估结果
     */
    public void applyEvaluation(EvaluationResult result) {
        if (result == null) {
            return;
        }
        
        // 先经过安全守卫校验
        List<String> safeDropIds = safetyGuard.validate(activeMessages, result);
        
        // 执行丢弃
        for (String msgId : safeDropIds) {
            dropMessage(msgId, "AI评估", "AI决定丢弃");
        }
        
        lastEvaluationTime = Instant.now();
        evaluationCount++;
        
        logger.info("[StructuredContextManager] 应用评估结果: 丢弃 " + safeDropIds.size() 
                + " 条，保留 " + (activeMessages.size() - safeDropIds.size()) + " 条");
    }
    
    /**
     * 丢弃消息
     */
    public void dropMessage(String messageId, String droppedBy, String reason) {
        StructuredMessage msg = messageStore.get(messageId);
        if (msg == null) {
            return;
        }
        
        // 从活跃列表移除
        activeMessages.remove(msg);
        msg.getMetadata().setDroppable(true);
        msg.getMetadata().setDropReason(reason);
        
        // 归档
        if (enableArchive) {
            ArchiveRecord record = new ArchiveRecord(
                messageId,
                sessionId,
                msg.getRole(),
                msg.getContent(),
                msg.getMetadata(),
                droppedBy,
                reason,
                "AI_EVALUATION"
            );
            archiveStore.put(messageId, record);
            totalArchived.incrementAndGet();
        }
        
        totalDiscarded.incrementAndGet();
        
        logger.info("[StructuredContextManager] 丢弃消息: " + messageId + ", reason=" + reason);
    }
    
    /**
     * 从归档恢复消息
     */
    public StructuredMessage restoreMessage(String messageId) {
        ArchiveRecord record = archiveStore.get(messageId);
        if (record == null) {
            logger.warning("[StructuredContextManager] 归档中找不到消息: " + messageId);
            return null;
        }
        
        // 从归档移除
        archiveStore.remove(messageId);
        
        // 重建消息
        StructuredMessage msg = new StructuredMessage(
            record.getMessageId(),
            record.getRole(),
            record.getContent(),
            record.getMetadata(),
            null,
            null,
            record.getArchivedTime()
        );
        
        // 添加回活跃列表
        messageStore.put(messageId, msg);
        activeMessages.add(msg);
        
        logger.info("[StructuredContextManager] 恢复消息: " + messageId);
        
        return msg;
    }
    
    // ==================== 任务管理 ====================
    
    /**
     * 开始新任务
     */
    public TaskContext startTask(String originalIntent) {
        TaskContext task = new TaskContext(originalIntent);
        taskContexts.put(task.getTaskId(), task);
        logger.info("[StructuredContextManager] 开始新任务: " + task.getTaskId() + ", intent=" + originalIntent);
        return task;
    }
    
    /**
     * 完成当前任务
     */
    public void completeTask(String taskId, String result) {
        TaskContext task = taskContexts.get(taskId);
        if (task != null) {
            task.setStatus(TaskStatus.COMPLETED);
            task.setResult(result);
            logger.info("[StructuredContextManager] 任务完成: " + taskId);
        }
    }
    
    /**
     * 获取当前活跃任务
     */
    public TaskContext getCurrentTask() {
        for (TaskContext task : taskContexts.values()) {
            if (task.isActive()) {
                return task;
            }
        }
        return null;
    }
    
    // ==================== 统计和报告 ====================
    
    /**
     * 生成状态报告
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("============ 上下文状态报告 ============\n");
        sb.append("Session: ").append(sessionId).append("\n");
        sb.append("活跃消息数: ").append(activeMessages.size()).append("\n");
        sb.append("归档消息数: ").append(archiveStore.size()).append("\n");
        sb.append("累计丢弃: ").append(totalDiscarded.get()).append("\n");
        sb.append("累计归档: ").append(totalArchived.get()).append("\n");
        sb.append("评估次数: ").append(evaluationCount).append("\n");
        sb.append("最近评估: ").append(lastEvaluationTime).append("\n");
        sb.append("任务数: ").append(taskContexts.size()).append("\n");
        
        TaskContext currentTask = getCurrentTask();
        if (currentTask != null) {
            sb.append("当前任务: ").append(currentTask.getTaskId()).append("\n");
            sb.append("  - ").append(currentTask.getOriginalIntent()).append("\n");
        }
        
        sb.append("========================================\n");
        return sb.toString();
    }
    
    /**
     * 重置上下文
     */
    public void reset() {
        activeMessages.clear();
        messageStore.clear();
        taskContexts.clear();
        // 保留归档
        totalDiscarded.set(0);
        totalArchived.set(0);
        messageCounter = 0;
        logger.info("[StructuredContextManager] 上下文已重置");
    }
    
    // ==================== Setters ====================
    
    public void setAiEvaluator(AiEvaluator aiEvaluator) {
        this.aiEvaluator = aiEvaluator;
    }
    
    public void updateSafetyGuard(ContextSafetyGuard safetyGuard) {
        this.safetyGuard = safetyGuard;
    }
    
    // ==================== Getters ====================
    
    public String getSessionId() { return sessionId; }
    public int getMaxActiveSize() { return maxActiveSize; }
    public int getMinRetainCount() { return minRetainCount; }
    public boolean isEnableArchive() { return enableArchive; }
    public ContextSafetyGuard getSafetyGuard() { return safetyGuard; }
    public Map<String, ArchiveRecord> getArchiveStore() { return archiveStore; }
    public Map<String, TaskContext> getTaskContexts() { return taskContexts; }
    public Instant getLastEvaluationTime() { return lastEvaluationTime; }

    // ==================== AICL 协议集成（v1.1） ====================

    /**
     * 启用 AICL 协议支持。
     */
    public void enableAicl(ContextControl.EvictionConfig evictionConfig) {
        this.aiclAssembler = new ContextAssembler(new ContextControl(
                new ContextControl.ContextBudget(8000, 0),
                evictionConfig != null ? evictionConfig : ContextControl.EvictionConfig.DEFAULT,
                Set.of("sys", "usr"),
                ContextControl.LifecycleDefaults.DEFAULT
        ));
        this.aiclSerializer = new AICLSerializer();
        this.aiclEnabled = true;
        logger.info("[StructuredContextManager] AICL protocol enabled");
    }

    /**
     * 将活跃消息转换为 AICL ContextBlock 并注册到 Assembler。
     */
    public ContextBlock registerMessageAsBlock(StructuredMessage msg) {
        if (!aiclEnabled || aiclAssembler == null) return null;

        ContextBlock block = ContextBlock.builder(msg.getId())
                .type(detectBlockType(msg))
                .role(msg.getRole())
                .priority(msg.getPriority())
                .format("markdown")
                .state(msg.getState())
                .ttl(msg.getTtl())
                .lastAccess(msg.getLastAccess())
                .accessCount(msg.getAccessCount())
                .generation(msg.getGeneration())
                .label(getMessageLabel(msg))
                .blockAbstract(getMessageAbstract(msg))
                .content(msg.getContent())
                .build();

        aiclAssembler.registerBlock(block);
        return block;
    }

    /**
     * 批量将活跃消息注册为 AICL 块。
     */
    public void syncToAicl() {
        if (!aiclEnabled || aiclAssembler == null) return;
        for (StructuredMessage msg : activeMessages) {
            registerMessageAsBlock(msg);
        }
        logger.fine("[StructuredContextManager] synced " + activeMessages.size() + " messages to AICL");
    }

    /**
     * 将当前上下文导出为 AICL XML 字符串。
     */
    public String toAiclXml(int currentTurn) {
        if (!aiclEnabled || aiclAssembler == null) {
            logger.warning("[StructuredContextManager] AICL not enabled, cannot export XML");
            return "";
        }
        syncToAicl();

        // 刷新预算
        aiclAssembler.getControl().getBudget().recalculate(aiclAssembler.getAllBlocks());

        AICLContext context = new AICLContext(
                sessionId,
                currentTurn,
                aiclAssembler.getControl(),
                aiclAssembler.getAllBlocks()
        );
        return aiclSerializer.serialize(context);
    }

    /**
     * 执行 AICL 淘汰。
     */
    public ContextAssembler.EvictionStats evictAicl() {
        if (!aiclEnabled || aiclAssembler == null) return null;
        syncToAicl();
        return aiclAssembler.evict();
    }

    /**
     * AICL 轮次推进（每轮对话结束调用）。
     */
    public void tickAicl() {
        if (!aiclEnabled || aiclAssembler == null) return;
        syncToAicl();
        aiclAssembler.tick();
    }

    /** 是否启用了 AICL */
    public boolean isAiclEnabled() { return aiclEnabled; }

    /** 获取 AICL Assembler */
    public ContextAssembler getAiclAssembler() { return aiclAssembler; }

    // ==================== AICL 辅助方法 ====================

    private String detectBlockType(StructuredMessage msg) {
        if (msg.isSystemMessage()) return "system";
        if (msg.isUserMessage()) return "user";
        if (msg.isAssistantMessage()) return "assistant";
        if (msg.isToolResultMessage()) return "tool";
        return "general";
    }

    private String getMessageLabel(StructuredMessage msg) {
        String roleLabel = switch (msg.getRole().toLowerCase()) {
            case "user" -> "用户消息";
            case "assistant" -> "助手回复";
            case "system" -> "系统指令";
            case "tool" -> "工具结果";
            default -> "消息";
        };
        MessageType type = msg.getType();
        if (type != null) {
            return roleLabel + " [" + type.name() + "]";
        }
        return roleLabel;
    }

    private String getMessageAbstract(StructuredMessage msg) {
        String content = msg.getContent();
        if (content == null || content.isEmpty()) return "";
        return content.length() > 100 ? content.substring(0, 100) + "..." : content;
    }
}
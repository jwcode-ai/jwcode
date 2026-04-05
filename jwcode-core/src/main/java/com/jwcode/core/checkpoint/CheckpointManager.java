package com.jwcode.core.checkpoint;

import com.jwcode.core.session.Session;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 检查点管理器 - 管理会话的所有检查点
 * 
 * 功能：
 * - 创建检查点
 * - 恢复到指定检查点
 * - 列出所有检查点
 * - 时间旅行支持
 */
public class CheckpointManager {
    
    private static final Logger logger = Logger.getLogger(CheckpointManager.class.getName());
    
    private final String sessionId;
    private final List<Checkpoint> checkpoints = new ArrayList<>();
    private final Map<String, Checkpoint> checkpointMap = new ConcurrentHashMap<>();
    private int currentStep = 0;
    
    public CheckpointManager(String sessionId) {
        this.sessionId = sessionId;
    }
    
    /**
     * 创建检查点
     * 
     * @param session 当前会话
     * @param description 检查点描述
     * @return 创建的检查点
     */
    public Checkpoint createCheckpoint(Session session, String description) {
        currentStep++;
        Checkpoint checkpoint = Checkpoint.fromSession(session, currentStep, description);
        
        checkpoints.add(checkpoint);
        checkpointMap.put(checkpoint.getId(), checkpoint);
        
        logger.info("创建检查点 [" + currentStep + "]: " + description);
        
        return checkpoint;
    }
    
    /**
     * 恢复到指定检查点
     * 
     * @param session 当前会话
     * @param checkpointId 检查点 ID
     * @return 是否恢复成功
     */
    public boolean revertTo(Session session, String checkpointId) {
        Checkpoint checkpoint = checkpointMap.get(checkpointId);
        if (checkpoint == null) {
            logger.warning("检查点不存在: " + checkpointId);
            return false;
        }
        
        // 恢复到检查点状态
        checkpoint.restoreTo(session);
        currentStep = checkpoint.getStepNumber();
        
        logger.info("恢复到检查点 [" + checkpoint.getStepNumber() + "]: " + checkpoint.getDescription());
        
        return true;
    }
    
    /**
     * 恢复到上一步
     */
    public boolean revertToPrevious(Session session) {
        if (checkpoints.size() < 2) {
            logger.warning("没有足够的检查点可以恢复");
            return false;
        }
        
        // 找到当前步骤之前的最后一个检查点
        Checkpoint target = null;
        for (int i = checkpoints.size() - 1; i >= 0; i--) {
            if (checkpoints.get(i).getStepNumber() < currentStep) {
                target = checkpoints.get(i);
                break;
            }
        }
        
        if (target == null) {
            logger.warning("没有可恢复的检查点");
            return false;
        }
        
        return revertTo(session, target.getId());
    }
    
    /**
     * 获取所有检查点
     */
    public List<Checkpoint> getAllCheckpoints() {
        return List.copyOf(checkpoints);
    }
    
    /**
     * 获取检查点数量
     */
    public int getCheckpointCount() {
        return checkpoints.size();
    }
    
    /**
     * 获取当前步骤
     */
    public int getCurrentStep() {
        return currentStep;
    }
    
    /**
     * 获取检查点历史（用于显示）
     */
    public String getCheckpointHistory() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 检查点历史 ===\n");
        
        for (Checkpoint cp : checkpoints) {
            String marker = cp.getStepNumber() == currentStep ? " → " : "   ";
            sb.append(marker).append(cp.getSummary()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 清理旧检查点（保留最近的 N 个）
     */
    public void cleanupOldCheckpoints(int keepCount) {
        if (checkpoints.size() <= keepCount) {
            return;
        }
        
        int removeCount = checkpoints.size() - keepCount;
        for (int i = 0; i < removeCount; i++) {
            Checkpoint removed = checkpoints.remove(0);
            checkpointMap.remove(removed.getId());
        }
        
        logger.info("清理了 " + removeCount + " 个旧检查点");
    }
    
    /**
     * 创建 D-Mail (时间旅行)
     * 
     * 特殊检查点，用于回到过去某个时间点
     */
    public DMail createDMail(Session session, String targetCheckpointId, String message) {
        Checkpoint target = checkpointMap.get(targetCheckpointId);
        if (target == null) {
            throw new IllegalArgumentException("目标检查点不存在: " + targetCheckpointId);
        }
        
        return new DMail(
            generateDMailId(),
            target,
            message,
            currentStep
        );
    }
    
    private String generateDMailId() {
        return "dmail_" + System.currentTimeMillis();
    }
    
    /**
     * D-Mail (时间邮件)
     * 用于记录时间旅行操作
     */
    public static class DMail {
        private final String id;
        private final Checkpoint target;
        private final String message;
        private final int fromStep;
        private final long timestamp;
        
        public DMail(String id, Checkpoint target, String message, int fromStep) {
            this.id = id;
            this.target = target;
            this.message = message;
            this.fromStep = fromStep;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getId() { return id; }
        public Checkpoint getTarget() { return target; }
        public String getMessage() { return message; }
        public int getFromStep() { return fromStep; }
        public long getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return String.format("D-Mail [%s]: Step %d → Step %d - %s", 
                id, fromStep, target.getStepNumber(), message);
        }
    }
}

package com.jwcode.core.compact;

/**
 * CompactStats - 压缩统计
 * 
 * 功能说明：
 * 提供会话压缩服务的统计信息。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class CompactStats {
    
    private final int totalCompactions;
    private final int totalMessagesCompacted;
    private final int totalTokensSaved;
    private final long averageDurationMs;
    
    public CompactStats(int totalCompactions, int totalMessagesCompacted,
                       int totalTokensSaved, long averageDurationMs) {
        this.totalCompactions = totalCompactions;
        this.totalMessagesCompacted = totalMessagesCompacted;
        this.totalTokensSaved = totalTokensSaved;
        this.averageDurationMs = averageDurationMs;
    }
    
    public int getTotalCompactions() {
        return totalCompactions;
    }
    
    public int getTotalMessagesCompacted() {
        return totalMessagesCompacted;
    }
    
    public int getTotalTokensSaved() {
        return totalTokensSaved;
    }
    
    public long getAverageDurationMs() {
        return averageDurationMs;
    }
    
    /**
     * 获取平均每次压缩节省的 Token 数量
     */
    public double getAverageTokensSavedPerCompaction() {
        if (totalCompactions == 0) {
            return 0;
        }
        return (double) totalTokensSaved / totalCompactions;
    }
    
    /**
     * 获取平均每次压缩的消息数量
     */
    public double getAverageMessagesCompactedPerCompaction() {
        if (totalCompactions == 0) {
            return 0;
        }
        return (double) totalMessagesCompacted / totalCompactions;
    }
    
    @Override
    public String toString() {
        return String.format("CompactStats{totalCompactions=%d, totalTokensSaved=%d, avgDuration=%dms}",
                totalCompactions, totalTokensSaved, averageDurationMs);
    }
}
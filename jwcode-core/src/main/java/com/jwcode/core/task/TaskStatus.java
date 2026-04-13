package com.jwcode.core.task;

/**
 * 任务状态枚举
 * 
 * 定义任务生命周期中的各种状态，每个状态都有对应的中文描述
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public enum TaskStatus {
    
    /**
     * 待处理 - 任务已创建但尚未开始执行
     */
    PENDING("待处理"),
    
    /**
     * 运行中 - 任务正在执行
     */
    RUNNING("运行中"),
    
    /**
     * 已完成 - 任务成功完成
     */
    COMPLETED("已完成"),
    
    /**
     * 失败 - 任务执行过程中发生错误
     */
    FAILED("失败"),
    
    /**
     * 已停止 - 任务被外部停止
     */
    STOPPED("已停止"),
    
    /**
     * 已取消 - 任务被取消（通常在开始前）
     */
    CANCELLED("已取消");
    
    private final String description;
    
    TaskStatus(String description) {
        this.description = description;
    }
    
    /**
     * 获取状态的中文描述
     * 
     * @return 状态描述
     */
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return description;
    }
    
    /**
     * 检查任务是否处于活跃状态（可以执行或正在执行）
     * 
     * @return true 如果是 PENDING 或 RUNNING
     */
    public boolean isActive() {
        return this == PENDING || this == RUNNING;
    }
    
    /**
     * 检查任务是否已完成（无论成功或失败）
     * 
     * @return true 如果是 COMPLETED, FAILED, STOPPED 或 CANCELLED
     */
    public boolean isFinished() {
        return this == COMPLETED || this == FAILED || this == STOPPED || this == CANCELLED;
    }
    
    /**
     * 检查任务是否成功完成
     * 
     * @return true 如果是 COMPLETED
     */
    public boolean isSuccessful() {
        return this == COMPLETED;
    }
    
    /**
     * 从字符串解析状态
     * 
     * @param status 状态字符串
     * @return TaskStatus 枚举值，如果解析失败返回 PENDING
     */
    public static TaskStatus fromString(String status) {
        if (status == null || status.trim().isEmpty()) {
            return PENDING;
        }
        try {
            return valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 尝试匹配中文描述
            for (TaskStatus ts : values()) {
                if (ts.description.equals(status) || ts.description.equals(status.trim())) {
                    return ts;
                }
            }
            return PENDING;
        }
    }
}

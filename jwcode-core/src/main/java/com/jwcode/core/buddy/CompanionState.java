package com.jwcode.core.buddy;

import java.time.Instant;

/**
 * CompanionState - 伙伴状态管理
 * 
 * 功能说明：
 * 管理 Companion 伙伴的各种状态，包括活动状态、情绪状态等。
 * 状态变化会触发相应的动画和提示。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class CompanionState {
    
    /**
     * 活动状态枚举
     */
    public enum ActivityState {
        /** 空闲 */
        IDLE,
        /** 思考中 */
        THINKING,
        /** 工作中 */
        WORKING,
        /** 庆祝 */
        CELEBRATING,
        /** 休眠 */
        SLEEPING,
        /** 兴奋 */
        EXCITED,
        /** 困惑 */
        CONFUSED,
        /** 等待用户输入 */
        WAITING_FOR_INPUT
    }
    
    /**
     * 情绪状态枚举
     */
    public enum MoodState {
        /** 中性 */
        NEUTRAL,
        /** 开心 */
        HAPPY,
        /** 兴奋 */
        EXCITED,
        /** 专注 */
        FOCUSED,
        /** 困惑 */
        CONFUSED,
        /** 失望 */
        DISAPPOINTED,
        /** 疲惫 */
        TIRED,
        /** 惊讶 */
        SURPRISED
    }
    
    /**
     * 能量级别枚举
     */
    public enum EnergyLevel {
        /** 低 */
        LOW,
        /** 中 */
        MEDIUM,
        /** 高 */
        HIGH
    }
    
    private ActivityState activityState;
    private MoodState moodState;
    private EnergyLevel energyLevel;
    private Instant lastStateChange;
    private String currentAnimation;
    private int taskCompletedCount;
    private int taskFailedCount;
    
    /**
     * 构造函数
     */
    public CompanionState() {
        this.activityState = ActivityState.IDLE;
        this.moodState = MoodState.NEUTRAL;
        this.energyLevel = EnergyLevel.MEDIUM;
        this.lastStateChange = Instant.now();
        this.currentAnimation = "idle";
        this.taskCompletedCount = 0;
        this.taskFailedCount = 0;
    }
    
    /**
     * 获取活动状态
     */
    public ActivityState getActivityState() {
        return activityState;
    }
    
    /**
     * 设置活动状态
     */
    public void setActivityState(ActivityState activityState) {
        this.activityState = activityState;
        this.lastStateChange = Instant.now();
        updateAnimation();
    }
    
    /**
     * 获取情绪状态
     */
    public MoodState getMoodState() {
        return moodState;
    }
    
    /**
     * 设置情绪状态
     */
    public void setMoodState(MoodState moodState) {
        this.moodState = moodState;
        this.lastStateChange = Instant.now();
    }
    
    /**
     * 获取能量级别
     */
    public EnergyLevel getEnergyLevel() {
        return energyLevel;
    }
    
    /**
     * 设置能量级别
     */
    public void setEnergyLevel(EnergyLevel energyLevel) {
        this.energyLevel = energyLevel;
    }
    
    /**
     * 获取上次状态变化时间
     */
    public Instant getLastStateChange() {
        return lastStateChange;
    }
    
    /**
     * 获取当前动画
     */
    public String getCurrentAnimation() {
        return currentAnimation;
    }
    
    /**
     * 设置当前动画
     */
    public void setCurrentAnimation(String currentAnimation) {
        this.currentAnimation = currentAnimation;
    }
    
    /**
     * 获取任务完成数量
     */
    public int getTaskCompletedCount() {
        return taskCompletedCount;
    }
    
    /**
     * 增加任务完成计数
     */
    public void incrementTaskCompleted() {
        this.taskCompletedCount++;
        updateMoodAfterTaskSuccess();
    }
    
    /**
     * 获取任务失败数量
     */
    public int getTaskFailedCount() {
        return taskFailedCount;
    }
    
    /**
     * 增加任务失败计数
     */
    public void incrementTaskFailed() {
        this.taskFailedCount++;
        updateMoodAfterTaskFailure();
    }
    
    /**
     * 获取总任务数
     */
    public int getTotalTaskCount() {
        return taskCompletedCount + taskFailedCount;
    }
    
    /**
     * 获取成功率
     */
    public double getSuccessRate() {
        int total = getTotalTaskCount();
        if (total == 0) {
            return 1.0;
        }
        return (double) taskCompletedCount / total;
    }
    
    /**
     * 更新动画
     */
    private void updateAnimation() {
        switch (activityState) {
            case IDLE:
                this.currentAnimation = "idle";
                break;
            case THINKING:
                this.currentAnimation = "think";
                break;
            case WORKING:
                this.currentAnimation = "work";
                break;
            case CELEBRATING:
                this.currentAnimation = "celebrate";
                break;
            case SLEEPING:
                this.currentAnimation = "sleep";
                break;
            case EXCITED:
                this.currentAnimation = "excited";
                break;
            case CONFUSED:
                this.currentAnimation = "confused";
                break;
            case WAITING_FOR_INPUT:
                this.currentAnimation = "wait";
                break;
            default:
                this.currentAnimation = "idle";
        }
    }
    
    /**
     * 任务成功后更新情绪
     */
    private void updateMoodAfterTaskSuccess() {
        double successRate = getSuccessRate();
        if (successRate >= 0.9) {
            this.moodState = MoodState.EXCITED;
        } else if (successRate >= 0.7) {
            this.moodState = MoodState.HAPPY;
        } else {
            this.moodState = MoodState.FOCUSED;
        }
    }
    
    /**
     * 任务失败后更新情绪
     */
    private void updateMoodAfterTaskFailure() {
        double successRate = getSuccessRate();
        if (successRate < 0.3) {
            this.moodState = MoodState.DISAPPOINTED;
        } else if (successRate < 0.5) {
            this.moodState = MoodState.CONFUSED;
        } else {
            this.moodState = MoodState.FOCUSED;
        }
    }
    
    /**
     * 检查是否处于活跃状态
     */
    public boolean isActive() {
        return activityState != ActivityState.SLEEPING && 
               activityState != ActivityState.IDLE;
    }
    
    /**
     * 重置状态
     */
    public void reset() {
        this.activityState = ActivityState.IDLE;
        this.moodState = MoodState.NEUTRAL;
        this.energyLevel = EnergyLevel.MEDIUM;
        this.currentAnimation = "idle";
        this.lastStateChange = Instant.now();
    }
    
    @Override
    public String toString() {
        return String.format("CompanionState{activity=%s, mood=%s, energy=%s, animation='%s'}",
                activityState, moodState, energyLevel, currentAnimation);
    }
}
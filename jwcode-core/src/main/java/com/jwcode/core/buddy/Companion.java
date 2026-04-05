package com.jwcode.core.buddy;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Companion - 伙伴精灵主类
 * 
 * 功能说明：
 * JWCode 的伙伴精灵系统，提供视觉反馈、情绪表达和上下文提示。
 * 伙伴会根据任务执行情况改变状态和情绪，为用户提供更友好的交互体验。
 * 
 * 核心特性：
 * - 情绪系统：根据任务成功/失败显示不同情绪
 * - 动作系统：庆祝、思考、等待等动画
 * - 提示气泡：显示上下文相关提示
 * - 成就系统：记录用户里程碑
 * - 自动休眠：长时间无操作自动休眠
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class Companion {
    
    /**
     * 伙伴名称
     */
    private final String name;
    
    /**
     * 伙伴状态
     */
    private final CompanionState state;
    
    /**
     * 伙伴配置
     */
    private final CompanionConfig config;
    
    /**
     * 成就列表
     */
    private final Set<String> achievements;
    
    /**
     * 状态变化监听器
     */
    private final List<Consumer<CompanionEvent>> eventListeners;
    
    /**
     * 提示队列
     */
    private final Queue<CompanionPrompt> promptQueue;
    
    /**
     * 调度器
     */
    private final ScheduledExecutorService scheduler;
    
    /**
     * 最后交互时间
     */
    private Instant lastInteractionTime;
    
    /**
     * 休眠超时（毫秒）
     */
    private static final long SLEEP_TIMEOUT_MS = 300000; // 5 分钟
    
    /**
     * 提示最大数量
     */
    private static final int MAX_PROMPTS = 10;
    
    /**
     * 构造函数
     */
    public Companion() {
        this("Buddy", CompanionConfig.defaultConfig());
    }
    
    /**
     * 构造函数
     * 
     * @param name 伙伴名称
     * @param config 配置文件
     */
    public Companion(String name, CompanionConfig config) {
        this.name = name;
        this.config = config;
        this.state = new CompanionState();
        this.achievements = ConcurrentHashMap.newKeySet();
        this.eventListeners = new CopyOnWriteArrayList<>();
        this.promptQueue = new ConcurrentLinkedQueue<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.lastInteractionTime = Instant.now();
        
        // 启动休眠检查任务
        startSleepCheck();
    }
    
    /**
     * 获取伙伴名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取伙伴状态
     */
    public CompanionState getState() {
        return state;
    }
    
    /**
     * 获取配置文件
     */
    public CompanionConfig getConfig() {
        return config;
    }
    
    /**
     * 添加事件监听器
     */
    public void addEventListener(Consumer<CompanionEvent> listener) {
        this.eventListeners.add(listener);
    }
    
    /**
     * 移除事件监听器
     */
    public void removeEventListener(Consumer<CompanionEvent> listener) {
        this.eventListeners.remove(listener);
    }
    
    /**
     * 记录交互时间
     */
    public void recordInteraction() {
        this.lastInteractionTime = Instant.now();
        
        // 从休眠中唤醒
        if (state.getActivityState() == CompanionState.ActivityState.SLEEPING) {
            wakeUp();
        }
    }
    
    /**
     * 开始思考
     */
    public void startThinking() {
        recordInteraction();
        state.setActivityState(CompanionState.ActivityState.THINKING);
        state.setMoodState(CompanionState.MoodState.FOCUSED);
        emitEvent(new CompanionEvent(CompanionEventType.STATE_CHANGED, 
                Map.of("state", "thinking")));
    }
    
    /**
     * 停止思考
     */
    public void stopThinking() {
        state.setActivityState(CompanionState.ActivityState.IDLE);
        emitEvent(new CompanionEvent(CompanionEventType.STATE_CHANGED, 
                Map.of("state", "idle")));
    }
    
    /**
     * 开始工作
     */
    public void startWorking() {
        recordInteraction();
        state.setActivityState(CompanionState.ActivityState.WORKING);
        emitEvent(new CompanionEvent(CompanionEventType.STATE_CHANGED, 
                Map.of("state", "working")));
    }
    
    /**
     * 停止工作
     */
    public void stopWorking() {
        state.setActivityState(CompanionState.ActivityState.IDLE);
        emitEvent(new CompanionEvent(CompanionEventType.STATE_CHANGED, 
                Map.of("state", "idle")));
    }
    
    /**
     * 任务成功
     */
    public void onTaskSuccess() {
        recordInteraction();
        state.incrementTaskCompleted();
        state.setActivityState(CompanionState.ActivityState.CELEBRATING);
        state.setMoodState(CompanionState.MoodState.HAPPY);
        
        emitEvent(new CompanionEvent(CompanionEventType.TASK_SUCCESS, 
                Map.of("successRate", state.getSuccessRate())));
        
        // 检查成就
        checkAchievements();
        
        // 延迟返回空闲状态
        scheduler.schedule(() -> {
            if (state.getActivityState() == CompanionState.ActivityState.CELEBRATING) {
                state.setActivityState(CompanionState.ActivityState.IDLE);
            }
        }, 3, TimeUnit.SECONDS);
    }
    
    /**
     * 任务失败
     */
    public void onTaskFailure() {
        recordInteraction();
        state.incrementTaskFailed();
        state.setActivityState(CompanionState.ActivityState.CONFUSED);
        
        emitEvent(new CompanionEvent(CompanionEventType.TASK_FAILURE, 
                Map.of("successRate", state.getSuccessRate())));
    }
    
    /**
     * 显示庆祝动画
     */
    public void showCelebration() {
        recordInteraction();
        state.setActivityState(CompanionState.ActivityState.CELEBRATING);
        state.setMoodState(CompanionState.MoodState.EXCITED);
        state.setCurrentAnimation("celebrate");
        
        emitEvent(new CompanionEvent(CompanionEventType.ANIMATION, 
                Map.of("animation", "celebrate")));
    }
    
    /**
     * 显示鼓励提示
     */
    public void showEncouragement() {
        String[] messages = {
            "加油！你可以的！",
            "继续努力！",
            "别放弃！",
            "做得好！",
            "继续保持！"
        };
        String message = messages[new Random().nextInt(messages.length)];
        addPrompt(CompanionPrompt.info(message));
    }
    
    /**
     * 显示等待动画
     */
    public void showWaiting() {
        state.setActivityState(CompanionState.ActivityState.WAITING_FOR_INPUT);
        state.setCurrentAnimation("wait");
        
        emitEvent(new CompanionEvent(CompanionEventType.ANIMATION, 
                Map.of("animation", "wait")));
    }
    
    /**
     * 进入休眠
     */
    public void goToSleep() {
        state.setActivityState(CompanionState.ActivityState.SLEEPING);
        state.setCurrentAnimation("sleep");
        
        emitEvent(new CompanionEvent(CompanionEventType.STATE_CHANGED, 
                Map.of("state", "sleeping")));
    }
    
    /**
     * 从休眠中唤醒
     */
    public void wakeUp() {
        state.setActivityState(CompanionState.ActivityState.IDLE);
        state.setCurrentAnimation("idle");
        
        emitEvent(new CompanionEvent(CompanionEventType.STATE_CHANGED, 
                Map.of("state", "awake")));
    }
    
    /**
     * 添加提示
     */
    public void addPrompt(CompanionPrompt prompt) {
        while (promptQueue.size() >= MAX_PROMPTS) {
            promptQueue.poll(); // 移除最旧的提示
        }
        promptQueue.offer(prompt);
        
        emitEvent(new CompanionEvent(CompanionEventType.PROMPT_ADDED, 
                Map.of("prompt", prompt)));
    }
    
    /**
     * 获取下一个提示
     */
    public CompanionPrompt getNextPrompt() {
        return promptQueue.poll();
    }
    
    /**
     * 获取所有待处理提示
     */
    public List<CompanionPrompt> getAllPrompts() {
        return new ArrayList<>(promptQueue);
    }
    
    /**
     * 清除所有提示
     */
    public void clearPrompts() {
        promptQueue.clear();
    }
    
    /**
     * 解锁成就
     */
    public void unlockAchievement(String achievementId) {
        if (achievements.add(achievementId)) {
            emitEvent(new CompanionEvent(CompanionEventType.ACHIEVEMENT_UNLOCKED, 
                    Map.of("achievement", achievementId)));
            showCelebration();
        }
    }
    
    /**
     * 检查是否已解锁成就
     */
    public boolean hasAchievement(String achievementId) {
        return achievements.contains(achievementId);
    }
    
    /**
     * 获取所有成就
     */
    public Set<String> getAchievements() {
        return new HashSet<>(achievements);
    }
    
    /**
     * 获取成就数量
     */
    public int getAchievementCount() {
        return achievements.size();
    }
    
    /**
     * 检查成就
     */
    private void checkAchievements() {
        // 第一次成功
        if (state.getTaskCompletedCount() == 1) {
            unlockAchievement("first_success");
        }
        
        // 10 次成功
        if (state.getTaskCompletedCount() == 10) {
            unlockAchievement("ten_successes");
        }
        
        // 100 次成功
        if (state.getTaskCompletedCount() == 100) {
            unlockAchievement("hundred_successes");
        }
        
        // 完美表现（成功率 100% 且至少 5 次任务）
        if (state.getSuccessRate() == 1.0 && state.getTaskCompletedCount() >= 5) {
            unlockAchievement("perfect_record");
        }
        
        // 永不放弃（失败后继续成功）
        if (state.getTaskFailedCount() > 0 && state.getSuccessRate() >= 0.8) {
            unlockAchievement("never_give_up");
        }
    }
    
    /**
     * 启动休眠检查
     */
    private void startSleepCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            Duration idleTime = Duration.between(lastInteractionTime, Instant.now());
            if (idleTime.toMillis() > SLEEP_TIMEOUT_MS) {
                if (state.getActivityState() != CompanionState.ActivityState.SLEEPING) {
                    goToSleep();
                }
            }
        }, 60, 60, TimeUnit.SECONDS);
    }
    
    /**
     * 发射事件
     */
    private void emitEvent(CompanionEvent event) {
        for (Consumer<CompanionEvent> listener : eventListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                // 忽略监听器异常
            }
        }
    }
    
    /**
     * 关闭伙伴系统
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 获取统计信息
     */
    public CompanionStats getStats() {
        return new CompanionStats(
                state.getTaskCompletedCount(),
                state.getTaskFailedCount(),
                state.getSuccessRate(),
                achievements.size(),
                state.getMoodState(),
                state.getActivityState()
        );
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 伙伴事件类型枚举
     */
    public enum CompanionEventType {
        STATE_CHANGED,
        MOOD_CHANGED,
        ANIMATION,
        TASK_SUCCESS,
        TASK_FAILURE,
        PROMPT_ADDED,
        ACHIEVEMENT_UNLOCKED,
        SLEEP,
        WAKE_UP
    }
    
    /**
     * 伙伴事件类
     */
    public static class CompanionEvent {
        private final CompanionEventType type;
        private final Map<String, Object> data;
        private final Instant timestamp;
        
        public CompanionEvent(CompanionEventType type, Map<String, Object> data) {
            this.type = type;
            this.data = data != null ? new HashMap<>(data) : new HashMap<>();
            this.timestamp = Instant.now();
        }
        
        public CompanionEventType getType() {
            return type;
        }
        
        public Map<String, Object> getData() {
            return new HashMap<>(data);
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
    }
    
    /**
     * 伙伴统计类
     */
    public static class CompanionStats {
        private final int tasksCompleted;
        private final int tasksFailed;
        private final double successRate;
        private final int achievementsUnlocked;
        private final CompanionState.MoodState currentMood;
        private final CompanionState.ActivityState currentState;
        
        public CompanionStats(int tasksCompleted, int tasksFailed, double successRate,
                             int achievementsUnlocked, CompanionState.MoodState currentMood,
                             CompanionState.ActivityState currentState) {
            this.tasksCompleted = tasksCompleted;
            this.tasksFailed = tasksFailed;
            this.successRate = successRate;
            this.achievementsUnlocked = achievementsUnlocked;
            this.currentMood = currentMood;
            this.currentState = currentState;
        }
        
        public int getTasksCompleted() {
            return tasksCompleted;
        }
        
        public int getTasksFailed() {
            return tasksFailed;
        }
        
        public double getSuccessRate() {
            return successRate;
        }
        
        public int getAchievementsUnlocked() {
            return achievementsUnlocked;
        }
        
        public CompanionState.MoodState getCurrentMood() {
            return currentMood;
        }
        
        public CompanionState.ActivityState getCurrentState() {
            return currentState;
        }
    }
}
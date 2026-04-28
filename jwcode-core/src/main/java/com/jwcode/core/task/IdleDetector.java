package com.jwcode.core.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * IdleDetector — 空闲自动检测器。
 *
 * <p>实现 Kimi Code 的「空闲自动触发」机制：</p>
 * <ul>
 *   <li>持续检测用户输入状态</li>
 *   <li>当检测到后台任务完成且用户未输入时，自动发起 Agent 轮次处理结果</li>
 *   <li>避免后台任务完成后挂起等待</li>
 * </ul>
 */
public class IdleDetector {

    private static final Logger logger = LoggerFactory.getLogger(IdleDetector.class);

    private static final long CHECK_INTERVAL_MS = 500;
    private static final long IDLE_THRESHOLD_MS = 1000;

    private final ScheduledExecutorService scheduler;
    private final TaskStore taskStore;
    private volatile long lastUserInputTime = System.currentTimeMillis();
    private volatile boolean running = false;
    private Consumer<Task> onIdleTaskComplete;

    public IdleDetector(TaskStore taskStore) {
        this.taskStore = taskStore;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "IdleDetector");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 设置当空闲时检测到已完成后台任务时的回调。
     */
    public void setOnIdleTaskComplete(Consumer<Task> callback) {
        this.onIdleTaskComplete = callback;
    }

    /**
     * 启动检测。
     */
    public void start() {
        if (running) return;
        running = true;

        // 订阅 Wire 事件，后台任务完成时标记检查点
        WireEventBus.getInstance().subscribe(event -> {
            if (event instanceof WireEventBus.TaskCompletedEvent) {
                // 任务完成事件到达，将在下次检查时处理
                logger.debug("[IdleDetector] 收到任务完成事件，准备空闲检查");
            }
        });

        scheduler.scheduleAtFixedRate(this::check, CHECK_INTERVAL_MS, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        logger.info("[IdleDetector] 已启动");
    }

    /**
     * 通知用户有输入（重置空闲计时器）。
     */
    public void notifyUserInput() {
        this.lastUserInputTime = System.currentTimeMillis();
    }

    /**
     * 停止检测。
     */
    public void stop() {
        running = false;
        scheduler.shutdownNow();
        logger.info("[IdleDetector] 已停止");
    }

    // ==================== 内部检查 ====================

    private void check() {
        if (!running) return;

        long idleDuration = System.currentTimeMillis() - lastUserInputTime;
        if (idleDuration < IDLE_THRESHOLD_MS) {
            return; // 用户仍在活跃输入
        }

        // 查找已完成但尚未被处理的后台任务
        for (Task task : taskStore.listByStatus(TaskStatus.COMPLETED)) {
            if (task.getTags() != null && task.getTags().contains("_idleHandled")) {
                continue;
            }

            logger.info("[IdleDetector] 空闲时检测到已完成任务 | taskId={} | idle={}ms",
                task.getId(), idleDuration);

            task.addTag("_idleHandled");
            taskStore.update(task);

            if (onIdleTaskComplete != null) {
                try {
                    onIdleTaskComplete.accept(task);
                } catch (Exception e) {
                    logger.warn("[IdleDetector] 回调异常", e);
                }
            }
        }
    }
}

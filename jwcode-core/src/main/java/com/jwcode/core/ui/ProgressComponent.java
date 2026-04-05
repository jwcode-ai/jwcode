package com.jwcode.core.ui;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * ProgressComponent - 进度显示组件
 * 
 * 功能说明：
 * 显示任务执行进度，支持动画进度条和状态更新。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ProgressComponent {
    
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;
    private String currentMessage;
    private int currentProgress;
    private Thread spinnerThread;
    private volatile boolean spinnerActive;
    
    // 进度条字符
    private static final String[] SPINNER_FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final String PROGRESS_BAR_FULL = "█";
    private static final String PROGRESS_BAR_EMPTY = "░";
    private static final int PROGRESS_BAR_WIDTH = 30;
    
    public ProgressComponent() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.running = new AtomicBoolean(false);
        this.currentMessage = "";
        this.currentProgress = 0;
        this.spinnerActive = false;
    }
    
    /**
     * 开始显示进度
     */
    public void start(String message) {
        this.currentMessage = message;
        this.running.set(true);
        this.spinnerActive = true;
        
        spinnerThread = new Thread(() -> {
            int frameIndex = 0;
            while (spinnerActive && running.get()) {
                String spinner = SPINNER_FRAMES[frameIndex % SPINNER_FRAMES.length];
                System.out.print("\r" + spinner + " " + currentMessage + "   ");
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                frameIndex++;
            }
            // 清除 spinner
            if (running.get()) {
                System.out.print("\r   \r");
            }
        });
        spinnerThread.start();
    }
    
    /**
     * 更新进度
     */
    public void updateProgress(int progress) {
        this.currentProgress = Math.max(0, Math.min(100, progress));
        renderProgressBar();
    }
    
    /**
     * 更新消息
     */
    public void updateMessage(String message) {
        this.currentMessage = message;
    }
    
    /**
     * 更新进度和消息
     */
    public void update(int progress, String message) {
        this.currentProgress = Math.max(0, Math.min(100, progress));
        this.currentMessage = message;
        renderProgressBar();
    }
    
    /**
     * 完成进度显示
     */
    public void complete(String successMessage) {
        spinnerActive = false;
        running.set(false);
        
        if (spinnerThread != null) {
            try {
                spinnerThread.join(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        System.out.print("\r✓ " + successMessage + "   \n");
    }
    
    /**
     * 失败结束
     */
    public void fail(String errorMessage) {
        spinnerActive = false;
        running.set(false);
        
        if (spinnerThread != null) {
            try {
                spinnerThread.join(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        System.out.print("\r✗ " + errorMessage + "   \n");
    }
    
    /**
     * 停止进度显示
     */
    public void stop() {
        spinnerActive = false;
        running.set(false);
        
        if (spinnerThread != null) {
            try {
                spinnerThread.join(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 渲染进度条
     */
    private void renderProgressBar() {
        if (!running.get()) return;
        
        StringBuilder bar = new StringBuilder();
        int filledWidth = (currentProgress * PROGRESS_BAR_WIDTH) / 100;
        
        bar.append("\r[");
        for (int i = 0; i < PROGRESS_BAR_WIDTH; i++) {
            if (i < filledWidth) {
                bar.append(PROGRESS_BAR_FULL);
            } else {
                bar.append(PROGRESS_BAR_EMPTY);
            }
        }
        bar.append("] ").append(currentProgress).append("% ").append(currentMessage);
        bar.append("   ");
        
        System.out.print(bar.toString());
    }
    
    /**
     * 静态方法：显示简单进度条
     */
    public static void showProgress(String message, int totalSteps, Runnable task) {
        ProgressComponent progress = new ProgressComponent();
        progress.start(message);
        
        try {
            task.run();
            progress.complete("完成");
        } catch (Exception e) {
            progress.fail("失败：" + e.getMessage());
        }
    }
    
    /**
     * 静态方法：带进度回调的任务
     */
    public static void showProgressWithCallback(String message, Consumer<Consumer<Integer>> task) {
        ProgressComponent progress = new ProgressComponent();
        progress.start(message);
        
        try {
            task.accept(progress::updateProgress);
            progress.complete("完成");
        } catch (Exception e) {
            progress.fail("失败：" + e.getMessage());
        }
    }
    
    /**
     * 关闭调度器
     */
    public void shutdown() {
        stop();
        scheduler.shutdown();
    }
}
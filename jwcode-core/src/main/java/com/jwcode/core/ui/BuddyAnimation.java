package com.jwcode.core.ui;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BuddyAnimation - Buddy 精灵动画
 * 
 * 功能说明：
 * 终端小助手动画，显示思考、工作、成功、错误等状态。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class BuddyAnimation {
    
    // Buddy 动画帧
    private static final String[] THINKING_FRAMES = {
        "  ⸜(ᵔᵕᵔ)⸝  思考中...",
        "  ⸜(ᵔᵕᵔ)⸝  思考中...",
        "  ⸜(ᵔᵕᵔ)⸝  思考中...",
        "  ⸜(ᵔᵕᵔ)⸝  思考中..."
    };
    
    private static final String[] WORKING_FRAMES = {
        "  (ง •_•)ง  工作中...",
        "  (ง •̀_•́)ง 工作中...",
        "  (ง •̀_•́)ง 工作中...",
        "  (ง •_•)ง  工作中..."
    };
    
    private static final String[] WAITING_FRAMES = {
        "  (｡•́︿•̀｡) 等待中...",
        "  (｡•́︿•̀｡) 等待中...",
        "  (｡•́︿•̀｡) 等待中..."
    };
    
    private static final String[] SUCCESS_FRAMES = {
        "  ✧ (≧◡≦) 完成！",
        "  ✧ (≧◡≦) 完成！"
    };
    
    private static final String[] ERROR_FRAMES = {
        "  (×_×) 出错了...",
        "  (×_×;) 出错了..."
    };
    
    private static final String[] GREETING_FRAMES = {
        "  (◕‿◕) 你好！",
        "  (◕‿◕)♪ 你好！",
        "  (◕‿◕) 你好！"
    };
    
    private static final String[] IDLE_FRAMES = {
        "  (｡•́︿•̀｡) 待机中...",
        "  (｡•́︿•̀｡) ...",
        "  (｡•́︿•̀｡) 待机中..."
    };
    
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;
    private Thread animationThread;
    private String[] currentFrames;
    private String currentMessage;
    private volatile boolean animationActive;
    
    public BuddyAnimation() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.running = new AtomicBoolean(false);
        this.animationActive = false;
        this.currentFrames = IDLE_FRAMES;
        this.currentMessage = "";
    }
    
    /**
     * 开始动画
     */
    public void start(String[] frames, String message) {
        this.currentFrames = frames;
        this.currentMessage = message;
        this.running.set(true);
        this.animationActive = true;
        
        animationThread = new Thread(() -> {
            int frameIndex = 0;
            while (animationActive && running.get()) {
                String frame = currentFrames[frameIndex % currentFrames.length];
                System.out.print("\r" + frame + "   ");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                frameIndex++;
            }
        });
        animationThread.start();
    }
    
    /**
     * 显示思考状态
     */
    public void thinking() {
        start(THINKING_FRAMES, "思考中");
    }
    
    /**
     * 显示工作状态
     */
    public void working() {
        start(WORKING_FRAMES, "工作中");
    }
    
    /**
     * 显示等待状态
     */
    public void waiting() {
        start(WAITING_FRAMES, "等待中");
    }
    
    /**
     * 显示成功状态
     */
    public void success(String message) {
        stop();
        String successMsg = message != null ? message : "完成！";
        System.out.println("\r  ✧ (≧◡≦) " + successMsg + "   ");
    }
    
    /**
     * 显示错误状态
     */
    public void error(String message) {
        stop();
        String errorMsg = message != null ? message : "出错了...";
        System.out.println("\r  (×_×;) " + errorMsg + "   ");
    }
    
    /**
     * 显示问候
     */
    public void greeting() {
        start(GREETING_FRAMES, "你好！");
        scheduler.schedule(this::stop, 2000, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 显示待机状态
     */
    public void idle() {
        start(IDLE_FRAMES, "待机中");
    }
    
    /**
     * 停止动画
     */
    public void stop() {
        animationActive = false;
        running.set(false);
        
        if (animationThread != null) {
            try {
                animationThread.join(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // 清除当前行
        System.out.print("\r   \r");
    }
    
    /**
     * 显示欢迎消息
     */
    public void showWelcome() {
        System.out.println();
        System.out.println("  ╭────────────────────────────────────╮");
        System.out.println("  │  (◕‿◕)  JWCode 助手为您服务！    │");
        System.out.println("  │                                    │");
        System.out.println("  │  输入 /help 查看可用命令           │");
        System.out.println("  ╰────────────────────────────────────╯");
        System.out.println();
    }
    
    /**
     * 显示提示消息
     */
    public void showTip(String tip) {
        System.out.println();
        System.out.println("  ╭────────────────────────────────────╮");
        System.out.println("  │  (・ω<)  提示：                    │");
        System.out.println("  │  " + padRight(tip, 36) + "│");
        System.out.println("  ╰────────────────────────────────────╯");
        System.out.println();
    }
    
    /**
     * 显示进度消息
     */
    public void showProgress(int percent, String message) {
        StringBuilder bar = new StringBuilder();
        int filled = percent / 5;
        
        for (int i = 0; i < 20; i++) {
            if (i < filled) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }
        
        String face = getProgressFace(percent);
        System.out.print("\r  " + face + " [" + bar + "] " + percent + "% " + message + "   ");
    }
    
    private String getProgressFace(int percent) {
        if (percent < 30) return "(ง •_•)ง";
        if (percent < 60) return "(ง •̀_•́)ง";
        if (percent < 90) return "(ง ڡ•_•)ง";
        return "✧ (≧◡≦)";
    }
    
    /**
     * 关闭调度器
     */
    public void shutdown() {
        stop();
        scheduler.shutdown();
    }
    
    private String padRight(String s, int n) {
        if (s.length() >= n) return s.substring(0, n);
        return s + " ".repeat(n - s.length());
    }
}
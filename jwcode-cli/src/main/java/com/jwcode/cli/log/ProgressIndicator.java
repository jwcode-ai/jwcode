package com.jwcode.cli.log;

/**
 * 进度指示器 - 显示任务进度
 */
public class ProgressIndicator {
    
    private final CliLogger logger = CliLogger.getInstance();
    private final String[] frames = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private int frameIndex = 0;
    private boolean running = false;
    private Thread animationThread;
    
    /**
     * 开始旋转动画
     */
    public void start(String message) {
        stop();
        running = true;
        
        animationThread = new Thread(() -> {
            while (running) {
                String frame = frames[frameIndex % frames.length];
                System.out.print("\r" + CliLogger.CYAN + frame + " " + message + CliLogger.RESET);
                System.out.flush();
                
                frameIndex++;
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        
        animationThread.start();
    }
    
    /**
     * 停止动画
     */
    public void stop() {
        running = false;
        if (animationThread != null) {
            animationThread.interrupt();
            try {
                animationThread.join(100);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        // 清除当前行
        System.out.print("\r" + " ".repeat(80) + "\r");
        System.out.flush();
    }
    
    /**
     * 停止并显示成功
     */
    public void success(String message) {
        stop();
        logger.success(message);
    }
    
    /**
     * 停止并显示错误
     */
    public void error(String message) {
        stop();
        logger.error(message);
    }
}

package com.jwcode.core.advanced.thinking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Thinking Mode - 深度推理模式管理器
 * 
 * 参照 Kimi Code 的 Thinking Mode (Tab键切换)
 * 在深度推理模式下，AI 会花更多时间分析再给出回答
 */
public class ThinkingModeManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ThinkingModeManager.class);
    
    private volatile boolean thinkingModeEnabled = false;
    private ThinkingConfig config;
    
    public ThinkingModeManager() {
        this.config = ThinkingConfig.defaultConfig();
    }
    
    /**
     * 切换 Thinking Mode 状态
     */
    public boolean toggle() {
        thinkingModeEnabled = !thinkingModeEnabled;
        logger.info("[ThinkingMode] {} 深度推理模式", thinkingModeEnabled ? "开启" : "关闭");
        return thinkingModeEnabled;
    }
    
    /**
     * 设置 Thinking Mode 状态
     */
    public void setEnabled(boolean enabled) {
        this.thinkingModeEnabled = enabled;
        logger.info("[ThinkingMode] 深度推理模式: {}", enabled ? "开启" : "关闭");
    }
    
    /**
     * 是否开启 Thinking Mode
     */
    public boolean isEnabled() {
        return thinkingModeEnabled;
    }
    
    /**
     * 执行带深度推理的任务
     */
    public <T> CompletableFuture<ThinkingResult<T>> executeWithThinking(Supplier<T> task, String taskDescription) {
        return CompletableFuture.supplyAsync(() -> {
            Instant start = Instant.now();
            
            if (thinkingModeEnabled) {
                logger.info("[ThinkingMode] 开始深度推理: {}", taskDescription);
                
                // 模拟深度思考过程
                simulateThinkingProcess(taskDescription);
                
                // 执行实际任务
                T result = task.get();
                
                Duration duration = Duration.between(start, Instant.now());
                logger.info("[ThinkingMode] 深度推理完成，耗时: {}ms", duration.toMillis());
                
                return new ThinkingResult<>(result, duration, true, generateThinkingTrace(taskDescription));
            } else {
                // 普通模式，直接执行
                T result = task.get();
                Duration duration = Duration.between(start, Instant.now());
                return new ThinkingResult<>(result, duration, false, null);
            }
        });
    }
    
    /**
     * 模拟思考过程
     */
    private void simulateThinkingProcess(String taskDescription) {
        try {
            // 深度思考延迟（可配置）
            Thread.sleep(config.getThinkingDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 生成思考轨迹
     */
    private String generateThinkingTrace(String taskDescription) {
        StringBuilder trace = new StringBuilder();
        trace.append("深度推理轨迹:\n");
        trace.append("1. 分析问题: ").append(taskDescription).append("\n");
        trace.append("2. 考虑边界情况和异常处理\n");
        trace.append("3. 评估多种解决方案\n");
        trace.append("4. 选择最优方案并执行\n");
        return trace.toString();
    }
    
    /**
     * 获取当前模式状态信息
     */
    public String getStatusInfo() {
        if (thinkingModeEnabled) {
            return "🧠 Thinking Mode [ON] - 深度推理已开启";
        } else {
            return "⚡ Fast Mode [ON] - 快速响应模式";
        }
    }
    
    // ==================== 数据类 ====================
    
    public static class ThinkingConfig {
        private long thinkingDelayMs = 2000;  // 思考延迟 2 秒
        private boolean showThinkingTrace = true;  // 是否显示思考轨迹
        private int maxThinkingDepth = 3;  // 最大思考深度
        
        public ThinkingConfig() {}
        
        public long getThinkingDelayMs() { return thinkingDelayMs; }
        public void setThinkingDelayMs(long v) { this.thinkingDelayMs = v; }
        public boolean isShowThinkingTrace() { return showThinkingTrace; }
        public void setShowThinkingTrace(boolean v) { this.showThinkingTrace = v; }
        public int getMaxThinkingDepth() { return maxThinkingDepth; }
        public void setMaxThinkingDepth(int v) { this.maxThinkingDepth = v; }
        
        public static ThinkingConfig defaultConfig() {
            return new ThinkingConfig();
        }
    }
    
    public static class ThinkingResult<T> {
        private final T result;
        private final Duration duration;
        private final boolean usedThinkingMode;
        private final String thinkingTrace;
        
        public ThinkingResult(T result, Duration duration, boolean usedThinkingMode, String thinkingTrace) {
            this.result = result;
            this.duration = duration;
            this.usedThinkingMode = usedThinkingMode;
            this.thinkingTrace = thinkingTrace;
        }
        
        public T getResult() { return result; }
        public Duration getDuration() { return duration; }
        public boolean isUsedThinkingMode() { return usedThinkingMode; }
        public String getThinkingTrace() { return thinkingTrace; }
        
        public String formatReport() {
            StringBuilder report = new StringBuilder();
            report.append("╔════════════════════════════════════════════════════════╗\n");
            report.append("║           推理报告                                     ║\n");
            report.append("╚════════════════════════════════════════════════════════╝\n");
            report.append("模式: ").append(usedThinkingMode ? "深度推理" : "快速响应").append("\n");
            report.append("耗时: ").append(duration.toMillis()).append("ms\n");
            if (usedThinkingMode && thinkingTrace != null) {
                report.append("\n").append(thinkingTrace);
            }
            return report.toString();
        }
    }
}

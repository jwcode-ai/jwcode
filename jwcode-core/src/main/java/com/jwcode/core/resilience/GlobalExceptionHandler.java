package com.jwcode.core.resilience;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 全局异常处理器
 * 
 * 统一处理系统异常，提供恢复策略和监控
 */
@Slf4j
public class GlobalExceptionHandler {
    
    private static final GlobalExceptionHandler INSTANCE = new GlobalExceptionHandler();
    
    private final ConcurrentHashMap<String, ErrorStats> errorStats = new ConcurrentHashMap<>();
    private final AtomicLong totalErrors = new AtomicLong(0);
    private volatile Consumer<ErrorContext> errorListener;
    private volatile boolean recoveryMode = false;
    
    private GlobalExceptionHandler() {
        // 设置默认未捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            handle(throwable, ErrorSource.UNCAUGHT, thread.getName());
        });
    }
    
    public static GlobalExceptionHandler getInstance() {
        return INSTANCE;
    }
    
    /**
     * 处理异常
     */
    public void handle(Throwable throwable, ErrorSource source, String context) {
        totalErrors.incrementAndGet();
        
        String errorType = throwable.getClass().getSimpleName();
        ErrorStats stats = errorStats.computeIfAbsent(errorType, k -> new ErrorStats());
        stats.record(throwable, context);
        
        // 记录日志
        log.error(String.format(
            "[ExceptionHandler] 异常: %s, 来源: %s, 上下文: %s, 次数: %d",
            errorType,
            source,
            context,
            stats.getCount()
        ));
        
        // 尝试恢复
        RecoveryAction action = attemptRecovery(throwable, source);
        
        // 通知监听器
        if (errorListener != null) {
            try {
                errorListener.accept(ErrorContext.builder()
                    .throwable(throwable)
                    .source(source)
                    .context(context)
                    .recoveryAction(action)
                    .build());
            } catch (Exception e) {
                log.error("[ExceptionHandler] 监听器异常: " + e.getMessage());
            }
        }
    }
    
    /**
     * 处理异常（简化版）
     */
    public void handle(Throwable throwable) {
        handle(throwable, ErrorSource.UNKNOWN, "");
    }
    
    /**
     * 包装 Runnable 添加异常处理
     */
    public Runnable wrap(Runnable runnable, String context) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                handle(t, ErrorSource.TASK, context);
            }
        };
    }
    
    /**
     * 尝试恢复
     */
    private RecoveryAction attemptRecovery(Throwable throwable, ErrorSource source) {
        if (recoveryMode) {
            return RecoveryAction.SKIPPED;
        }
        
        recoveryMode = true;
        try {
            // OOM 恢复
            if (throwable instanceof OutOfMemoryError) {
                System.gc();
                log.warn("[ExceptionHandler] 触发 GC 应对 OOM");
                return RecoveryAction.GC_TRIGGERED;
            }
            
            // 线程中断恢复
            if (throwable instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                return RecoveryAction.THREAD_INTERRUPTED;
            }
            
            // API 限流恢复
            String msg = throwable.getMessage();
            if (msg != null && (msg.contains("429") || msg.contains("rate limit"))) {
                try {
                    Thread.sleep(5000);
                    log.info("[ExceptionHandler] 限流等待 5 秒后恢复");
                    return RecoveryAction.RATE_LIMIT_WAIT;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return RecoveryAction.THREAD_INTERRUPTED;
                }
            }
            
            return RecoveryAction.NONE;
        } finally {
            recoveryMode = false;
        }
    }
    
    /**
     * 设置错误监听器
     */
    public void setErrorListener(Consumer<ErrorContext> listener) {
        this.errorListener = listener;
    }
    
    /**
     * 获取错误统计
     */
    public ErrorStats getStats(String errorType) {
        return errorStats.get(errorType);
    }
    
    /**
     * 获取总错误数
     */
    public long getTotalErrors() {
        return totalErrors.get();
    }
    
    /**
     * 生成错误报告
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("╔════════════════════════════════════════════════════════╗\n");
        report.append("║              错误统计报告                               ║\n");
        report.append("╚════════════════════════════════════════════════════════╝\n\n");
        report.append("总错误数: ").append(totalErrors.get()).append("\n\n");
        
        if (!errorStats.isEmpty()) {
            report.append("按类型分类:\n");
            errorStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().getCount(), a.getValue().getCount()))
                .forEach(entry -> {
                    report.append(String.format("  %-30s %5d 次\n", 
                        entry.getKey(), entry.getValue().getCount()));
                });
        }
        
        return report.toString();
    }
    
    /**
     * 重置统计
     */
    public void resetStats() {
        errorStats.clear();
        totalErrors.set(0);
    }
    
    // ==================== 枚举和内部类 ====================
    
    public enum ErrorSource {
        TASK,           // 任务执行
        API,            // API 调用
        TOOL,           // 工具执行
        UNCAUGHT,       // 未捕获异常
        UNKNOWN         // 未知来源
    }
    
    public enum RecoveryAction {
        NONE,           // 无恢复操作
        GC_TRIGGERED,   // 触发 GC
        THREAD_INTERRUPTED, // 线程中断
        RATE_LIMIT_WAIT, // 限流等待
        SKIPPED         // 跳过（已在恢复中）
    }
    
    @Data
    @Builder
    public static class ErrorContext {
        private Throwable throwable;
        private ErrorSource source;
        private String context;
        private RecoveryAction recoveryAction;
        
        public String getStackTrace() {
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            return sw.toString();
        }
    }
    
    public static class ErrorStats {
        private final AtomicLong count = new AtomicLong(0);
        private volatile String lastContext;
        private volatile long lastTime;
        private volatile Throwable lastException;
        
        void record(Throwable t, String context) {
            count.incrementAndGet();
            lastContext = context;
            lastTime = System.currentTimeMillis();
            lastException = t;
        }
        
        public long getCount() {
            return count.get();
        }
    }
}

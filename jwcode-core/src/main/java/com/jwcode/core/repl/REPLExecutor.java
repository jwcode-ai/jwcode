package com.jwcode.core.repl;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.*;

/**
 * REPL 执行器抽象类
 * 提供代码执行的基础功能，包括超时控制、内存限制和输出捕获
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public abstract class REPLExecutor {
    
    protected final String language;
    protected final long timeoutMillis;
    protected final long maxMemoryMB;
    protected final ExecutorService executor;
    
    /**
     * 执行结果
     */
    public record ExecutionResult(
        boolean success,
        String output,
        String error,
        long executionTimeMs,
        String exitCode
    ) {
        public static ExecutionResult success(String output, long executionTimeMs) {
            return new ExecutionResult(true, output, null, executionTimeMs, "0");
        }
        
        public static ExecutionResult error(String error, String exitCode) {
            return new ExecutionResult(false, null, error, 0, exitCode);
        }
        
        public static ExecutionResult error(String error, long executionTimeMs, String exitCode) {
            return new ExecutionResult(false, null, error, executionTimeMs, exitCode);
        }
        
        public static ExecutionResult timeout(long timeoutMillis) {
            return new ExecutionResult(false, null, 
                "Execution timeout after " + timeoutMillis + "ms", timeoutMillis, "TIMEOUT");
        }
        
        public static ExecutionResult memoryExceeded(long maxMemoryMB) {
            return new ExecutionResult(false, null,
                "Memory limit exceeded: " + maxMemoryMB + "MB", 0, "MEMORY_EXCEEDED");
        }
    }
    
    /**
     * 输出捕获器
     */
    protected static class OutputCapture {
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        private final PrintStream originalOut;
        private final PrintStream originalErr;
        
        public OutputCapture() {
            this.originalOut = System.out;
            this.originalErr = System.err;
        }
        
        public void start() {
            System.setOut(new PrintStream(stdout));
            System.setErr(new PrintStream(stderr));
        }
        
        public void stop() {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        
        public String getStdout() {
            return stdout.toString();
        }
        
        public String getStderr() {
            return stderr.toString();
        }
        
        public String getCombinedOutput() {
            String out = stdout.toString();
            String err = stderr.toString();
            if (err.isEmpty()) {
                return out;
            }
            return out + (out.isEmpty() ? "" : "\n") + "[STDERR]\n" + err;
        }
    }
    
    /**
     * 创建 REPL 执行器
     * 
     * @param language 编程语言
     * @param timeoutMillis 超时时间（毫秒）
     * @param maxMemoryMB 最大内存限制（MB）
     */
    protected REPLExecutor(String language, long timeoutMillis, long maxMemoryMB) {
        this.language = language;
        this.timeoutMillis = timeoutMillis;
        this.maxMemoryMB = maxMemoryMB;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * 执行代码
     * 
     * @param code 要执行的代码
     * @return 执行结果
     */
    public abstract ExecutionResult execute(String code);
    
    /**
     * 执行代码（带语言参数，用于多语言支持）
     * 
     * @param code 要执行的代码
     * @param language 编程语言
     * @return 执行结果
     */
    public ExecutionResult execute(String code, String language) {
        if (!this.language.equalsIgnoreCase(language)) {
            return ExecutionResult.error(
                "Language mismatch: expected " + this.language + ", got " + language, 
                "LANGUAGE_MISMATCH"
            );
        }
        return execute(code);
    }
    
    /**
     * 带超时控制的执行
     * 
     * @param callable 要执行的任务
     * @return 执行结果
     */
    protected ExecutionResult executeWithTimeout(Callable<ExecutionResult> callable) {
        long startTime = System.currentTimeMillis();
        Future<ExecutionResult> future = executor.submit(callable);
        
        try {
            ExecutionResult result = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            long executionTime = System.currentTimeMillis() - startTime;
            return new ExecutionResult(
                result.success(),
                result.output(),
                result.error(),
                executionTime,
                result.exitCode()
            );
        } catch (TimeoutException e) {
            future.cancel(true);
            return ExecutionResult.timeout(timeoutMillis);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return ExecutionResult.error("Execution interrupted", "INTERRUPTED");
        } catch (ExecutionException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return ExecutionResult.error(
                "Execution failed: " + e.getCause().getMessage(),
                executionTime,
                "EXECUTION_ERROR"
            );
        }
    }
    
    /**
     * 检查内存使用是否超过限制
     * 
     * @return 如果超过限制返回 true
     */
    protected boolean isMemoryExceeded() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        return usedMemoryMB > maxMemoryMB;
    }
    
    /**
     * 获取语言
     */
    public String getLanguage() {
        return language;
    }
    
    /**
     * 获取超时时间
     */
    public long getTimeoutMillis() {
        return timeoutMillis;
    }
    
    /**
     * 获取最大内存限制
     */
    public long getMaxMemoryMB() {
        return maxMemoryMB;
    }
    
    /**
     * 关闭执行器
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 重置执行器状态（清除变量等）
     */
    public abstract void reset();
    
    /**
     * 检查执行器是否可用
     */
    public abstract boolean isAvailable();
}

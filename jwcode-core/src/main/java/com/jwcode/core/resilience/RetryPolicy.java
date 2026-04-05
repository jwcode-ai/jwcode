package com.jwcode.core.resilience;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * 重试策略
 * 
 * 支持指数退避、固定间隔等重试策略
 */
@Slf4j
public class RetryPolicy {
    
    private final int maxAttempts;
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double backoffMultiplier;
    private final Predicate<Throwable> retryableException;
    
    @Builder
    public RetryPolicy(Integer maxAttempts, Long initialDelayMs, Long maxDelayMs, 
                       Double backoffMultiplier, Predicate<Throwable> retryableException) {
        this.maxAttempts = maxAttempts != null ? maxAttempts : 3;
        this.initialDelayMs = initialDelayMs != null ? initialDelayMs : 1000;
        this.maxDelayMs = maxDelayMs != null ? maxDelayMs : 30000;
        this.backoffMultiplier = backoffMultiplier != null ? backoffMultiplier : 2.0;
        this.retryableException = retryableException != null ? retryableException : e -> true;
    }
    
    /**
     * 执行带重试的操作
     */
    public <T> T execute(Callable<T> callable) throws RetryExhaustedException {
        int attempt = 1;
        long delay = initialDelayMs;
        Throwable lastException = null;
        
        while (attempt <= maxAttempts) {
            try {
                log.debug("[RetryPolicy] 尝试 " + attempt + "/" + maxAttempts);
                T result = callable.call();
                if (attempt > 1) {
                    log.info("[RetryPolicy] 第 " + attempt + " 次尝试成功");
                }
                return result;
            } catch (Exception e) {
                lastException = e;
                
                if (!retryableException.test(e) || attempt >= maxAttempts) {
                    break;
                }
                
                log.warn("[RetryPolicy] 尝试 " + attempt + " 失败: " + e.getMessage() + 
                           "，" + delay + "ms 后重试...");
                
                try {
                    TimeUnit.MILLISECONDS.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RetryExhaustedException("重试被中断", ie, attempt);
                }
                
                delay = Math.min((long)(delay * backoffMultiplier), maxDelayMs);
                attempt++;
            }
        }
        
        throw new RetryExhaustedException(
            "重试 " + maxAttempts + " 次后仍然失败: " + lastException.getMessage(),
            lastException,
            attempt
        );
    }
    
    /**
     * 异步执行带重试的操作
     */
    public <T> java.util.concurrent.CompletableFuture<T> executeAsync(Callable<T> callable) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return execute(callable);
            } catch (RetryExhaustedException e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * 创建默认重试策略
     */
    public static RetryPolicy defaultPolicy() {
        return RetryPolicy.builder().build();
    }
    
    /**
     * 创建 API 调用重试策略
     */
    public static RetryPolicy forApiCalls() {
        return RetryPolicy.builder()
            .maxAttempts(3)
            .initialDelayMs(1000L)
            .maxDelayMs(10000L)
            .backoffMultiplier(2.0)
            .retryableException(e -> {
                // 只重试网络相关异常
                String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                return msg.contains("timeout") 
                    || msg.contains("connection") 
                    || msg.contains("network")
                    || msg.contains("503")
                    || msg.contains("429");
            })
            .build();
    }
    
    @FunctionalInterface
    public interface Callable<T> {
        T call() throws Exception;
    }
    
    public static class RetryExhaustedException extends Exception {
        @Getter
        private final int attempts;
        
        public RetryExhaustedException(String message, Throwable cause, int attempts) {
            super(message, cause);
            this.attempts = attempts;
        }
    }
}

package com.jwcode.core.resilience;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 限流器
 * 
 * 控制请求速率，防止系统过载
 * 支持令牌桶和固定窗口两种算法
 */
@Slf4j
public class RateLimiter {
    
    public enum Algorithm {
        TOKEN_BUCKET,    // 令牌桶（平滑限流）
        FIXED_WINDOW     // 固定窗口
    }
    
    @Getter
    private final String name;
    private final Algorithm algorithm;
    private final long maxRequests;
    private final long windowSizeMs;
    
    // 令牌桶状态
    private final AtomicLong tokens;
    private final AtomicLong lastRefillTime;
    private final long refillRate; // 每毫秒填充的令牌数
    
    // 固定窗口状态
    private final AtomicLong windowStart;
    private final AtomicLong windowCount;
    
    public RateLimiter(String name, long maxRequests, long windowSizeMs) {
        this(name, Algorithm.TOKEN_BUCKET, maxRequests, windowSizeMs);
    }
    
    public RateLimiter(String name, Algorithm algorithm, long maxRequests, long windowSizeMs) {
        this.name = name;
        this.algorithm = algorithm;
        this.maxRequests = maxRequests;
        this.windowSizeMs = windowSizeMs;
        
        if (algorithm == Algorithm.TOKEN_BUCKET) {
            this.tokens = new AtomicLong(maxRequests);
            this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
            this.refillRate = maxRequests * 1000 / windowSizeMs; // 每秒填充量
            this.windowStart = null;
            this.windowCount = null;
        } else {
            this.tokens = null;
            this.lastRefillTime = null;
            this.refillRate = 0;
            this.windowStart = new AtomicLong(System.currentTimeMillis());
            this.windowCount = new AtomicLong(0);
        }
    }
    
    /**
     * 尝试获取许可
     * 
     * @return true if allowed, false if rate limited
     */
    public boolean tryAcquire() {
        return tryAcquire(1);
    }
    
    /**
     * 尝试获取多个许可
     */
    public boolean tryAcquire(int permits) {
        if (algorithm == Algorithm.TOKEN_BUCKET) {
            return tryAcquireTokenBucket(permits);
        } else {
            return tryAcquireFixedWindow(permits);
        }
    }
    
    /**
     * 阻塞式获取许可
     */
    public void acquire() throws InterruptedException {
        while (!tryAcquire()) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
    }
    
    private boolean tryAcquireTokenBucket(int permits) {
        refillTokens();
        
        while (true) {
            long currentTokens = tokens.get();
            if (currentTokens < permits) {
                return false;
            }
            if (tokens.compareAndSet(currentTokens, currentTokens - permits)) {
                return true;
            }
        }
    }
    
    private void refillTokens() {
        long now = System.currentTimeMillis();
        long lastRefill = lastRefillTime.get();
        long elapsed = now - lastRefill;
        
        if (elapsed > 0) {
            long newTokens = (elapsed * refillRate) / 1000;
            if (newTokens > 0) {
                if (lastRefillTime.compareAndSet(lastRefill, now)) {
                    tokens.updateAndGet(current -> Math.min(maxRequests, current + newTokens));
                }
            }
        }
    }
    
    private boolean tryAcquireFixedWindow(int permits) {
        long now = System.currentTimeMillis();
        long currentWindow = windowStart.get();
        
        // 检查是否需要重置窗口
        if (now - currentWindow >= windowSizeMs) {
            if (windowStart.compareAndSet(currentWindow, now)) {
                windowCount.set(0);
            }
        }
        
        // 尝试增加计数
        long currentCount = windowCount.get();
        if (currentCount + permits > maxRequests) {
            return false;
        }
        
        return windowCount.compareAndSet(currentCount, currentCount + permits);
    }
    
    /**
     * 获取当前令牌数（仅令牌桶模式）
     */
    public long getAvailableTokens() {
        if (algorithm == Algorithm.TOKEN_BUCKET) {
            refillTokens();
            return tokens.get();
        }
        return maxRequests - windowCount.get();
    }
    
    /**
     * 获取当前窗口请求数（仅固定窗口模式）
     */
    public long getCurrentWindowCount() {
        if (algorithm == Algorithm.FIXED_WINDOW) {
            return windowCount.get();
        }
        return 0;
    }
    
    /**
     * 重置限流器
     */
    public void reset() {
        if (algorithm == Algorithm.TOKEN_BUCKET) {
            tokens.set(maxRequests);
            lastRefillTime.set(System.currentTimeMillis());
        } else {
            windowStart.set(System.currentTimeMillis());
            windowCount.set(0);
        }
        log.info("[RateLimiter] " + name + " 已重置");
    }
    
    /**
     * 获取状态报告
     */
    public String getReport() {
        if (algorithm == Algorithm.TOKEN_BUCKET) {
            return String.format(
                "限流器[%s]: 算法=%s, 令牌=%d/%d, 填充率=%d/秒",
                name,
                algorithm,
                getAvailableTokens(),
                maxRequests,
                refillRate
            );
        } else {
            return String.format(
                "限流器[%s]: 算法=%s, 窗口请求=%d/%d",
                name,
                algorithm,
                getCurrentWindowCount(),
                maxRequests
            );
        }
    }
}

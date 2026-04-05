package com.jwcode.core.resilience;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 熔断器
 * 
 * 防止级联故障，保护系统稳定性
 * 参考 Hystrix/Resilience4j 设计
 */
@Slf4j
public class CircuitBreaker {
    
    public enum State {
        CLOSED,      // 关闭 - 正常放行
        OPEN,        // 打开 - 拒绝请求
        HALF_OPEN    // 半开 - 尝试放行少量请求测试
    }
    
    @Getter
    private final String name;
    
    // 配置参数
    private final int failureThreshold;        // 失败次数阈值
    private final long openDurationMs;         // 熔断持续时间
    private final int halfOpenMaxCalls;        // 半开状态最大测试请求数
    private final int successThreshold;        // 半开成功次数阈值（恢复关闭）
    
    // 状态
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger halfOpenCalls = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicLong stateChangedAt = new AtomicLong(System.currentTimeMillis());
    
    // 统计
    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    private final AtomicLong totalSuccesses = new AtomicLong(0);
    
    public CircuitBreaker(String name) {
        this(name, 5, 30000, 3, 2);
    }
    
    public CircuitBreaker(String name, int failureThreshold, long openDurationMs, 
                          int halfOpenMaxCalls, int successThreshold) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.openDurationMs = openDurationMs;
        this.halfOpenMaxCalls = halfOpenMaxCalls;
        this.successThreshold = successThreshold;
    }
    
    /**
     * 检查是否允许请求通过
     */
    public boolean allowRequest() {
        State currentState = state.get();
        
        switch (currentState) {
            case CLOSED:
                return true;
                
            case OPEN:
                // 检查是否应该进入半开状态
                if (System.currentTimeMillis() - stateChangedAt.get() >= openDurationMs) {
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        stateChangedAt.set(System.currentTimeMillis());
                        halfOpenCalls.set(0);
                        successCount.set(0);
                        log.info("[CircuitBreaker] " + name + " 进入半开状态");
                    }
                    return true;
                }
                return false;
                
            case HALF_OPEN:
                // 限制半开状态的测试请求数
                int currentCalls = halfOpenCalls.incrementAndGet();
                if (currentCalls <= halfOpenMaxCalls) {
                    return true;
                }
                halfOpenCalls.decrementAndGet();
                return false;
                
            default:
                return false;
        }
    }
    
    /**
     * 记录成功
     */
    public void recordSuccess() {
        totalCalls.incrementAndGet();
        totalSuccesses.incrementAndGet();
        
        State currentState = state.get();
        
        if (currentState == State.HALF_OPEN) {
            int successes = successCount.incrementAndGet();
            failureCount.set(0);
            
            if (successes >= successThreshold) {
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    stateChangedAt.set(System.currentTimeMillis());
                    resetCounters();
                    log.info("[CircuitBreaker] " + name + " 关闭（恢复正常）");
                }
            }
        } else if (currentState == State.CLOSED) {
            // 重置失败计数
            if (failureCount.get() > 0) {
                failureCount.set(0);
            }
        }
    }
    
    /**
     * 记录失败
     */
    public void recordFailure() {
        totalCalls.incrementAndGet();
        totalFailures.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        
        State currentState = state.get();
        
        if (currentState == State.HALF_OPEN) {
            // 半开状态失败，重新熔断
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                stateChangedAt.set(System.currentTimeMillis());
                log.info("[CircuitBreaker] " + name + " 重新打开（半开测试失败）");
            }
        } else if (currentState == State.CLOSED) {
            int failures = failureCount.incrementAndGet();
            
            if (failures >= failureThreshold) {
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    stateChangedAt.set(System.currentTimeMillis());
                    log.warn("[CircuitBreaker] " + name + " 打开（失败次数: " + failures + "）");
                }
            }
        }
    }
    
    /**
     * 执行带熔断保护的操作
     */
    public <T> T execute(Callable<T> callable) throws Exception {
        if (!allowRequest()) {
            throw new CircuitBreakerOpenException("熔断器打开，请求被拒绝: " + name);
        }
        
        try {
            T result = callable.call();
            recordSuccess();
            return result;
        } catch (Exception e) {
            recordFailure();
            throw e;
        }
    }
    
    /**
     * 手动重置熔断器
     */
    public void reset() {
        state.set(State.CLOSED);
        resetCounters();
        stateChangedAt.set(System.currentTimeMillis());
        log.info("[CircuitBreaker] " + name + " 手动重置");
    }
    
    /**
     * 强制打开熔断器
     */
    public void forceOpen() {
        state.set(State.OPEN);
        stateChangedAt.set(System.currentTimeMillis());
        log.info("[CircuitBreaker] " + name + " 强制打开");
    }
    
    private void resetCounters() {
        failureCount.set(0);
        successCount.set(0);
        halfOpenCalls.set(0);
    }
    
    public State getState() {
        return state.get();
    }
    
    public long getFailureCount() {
        return failureCount.get();
    }
    
    public long getTotalCalls() {
        return totalCalls.get();
    }
    
    public double getFailureRate() {
        long total = totalCalls.get();
        if (total == 0) return 0.0;
        return (double) totalFailures.get() / total * 100;
    }
    
    /**
     * 获取状态报告
     */
    public String getReport() {
        return String.format(
            "熔断器[%s]: 状态=%s, 失败=%d/%d (%.1f%%), 熔断次数=%d",
            name,
            state.get(),
            totalFailures.get(),
            totalCalls.get(),
            getFailureRate(),
            totalFailures.get() / Math.max(failureThreshold, 1)
        );
    }
    
    @FunctionalInterface
    public interface Callable<T> {
        T call() throws Exception;
    }
    
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}

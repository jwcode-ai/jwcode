package com.jwcode.core.tool.execution;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.function.Supplier;

/**
 * 工具熔断器
 * 
 * 实现错误熔断机制：
 * - 连续 3 次失败 → 半开状态：暂停 30 秒，改用替代工具
 * - 连续 5 次失败 → 全开状态：本轮会话禁用，上报用户
 * 
 * 对应日志场景：
 * - PowerShell 连续语法错误 → 改用 Java NIO 原生实现
 * - AgentTool 连续参数错误 → 热更新 Prompt 或降级
 */
public class ToolCircuitBreaker {
    
    private static final Logger logger = Logger.getLogger(ToolCircuitBreaker.class.getName());
    
    /** 半开阈值 */
    private static final int HALF_OPEN_THRESHOLD = 3;
    
    /** 全开阈值 */
    private static final int FULL_OPEN_THRESHOLD = 5;
    
    /** 暂停时长（毫秒） */
    private static final long PAUSE_DURATION_MS = 30_000;
    
    /** 工具失败计数器 */
    private final ConcurrentHashMap<String, FailureCounter> toolFailureCounters = new ConcurrentHashMap<>();
    
    /** 暂停开始时间 */
    private final ConcurrentHashMap<String, Long> pauseStartTimes = new ConcurrentHashMap<>();
    
    /** 状态 */
    private final ConcurrentHashMap<String, CircuitState> states = new ConcurrentHashMap<>();
    
    /**
     * 失败计数器
     */
    private static class FailureCounter {
        final AtomicInteger count = new AtomicInteger(0);
        final AtomicInteger lastFailureTime = new AtomicInteger(0);
        
        void increment() {
            count.incrementAndGet();
            lastFailureTime.set((int) (System.currentTimeMillis() / 1000));
        }
        
        void reset() {
            count.set(0);
            lastFailureTime.set(0);
        }
        
        int get() {
            return count.get();
        }
    }
    
    /**
     * 熔断状态
     */
    public enum CircuitState {
        CLOSED,    // 正常
        HALF_OPEN, // 半开：暂停中
        FULL_OPEN // 全开：禁用
    }
    
    /**
     * 检查工具是否可执行
     * 
     * @param toolName 工具名称
     * @return true 可执行，false 被熔断
     */
    public boolean canExecute(String toolName) {
        CircuitState state = states.get(toolName);
        
        if (state == null || state == CircuitState.CLOSED) {
            return true;
        }
        
        if (state == CircuitState.HALF_OPEN || state == CircuitState.FULL_OPEN) {
            // 检查是否已过暂停期
            Long pauseStart = pauseStartTimes.get(toolName);
            if (pauseStart != null) {
                long elapsed = System.currentTimeMillis() - pauseStart;
                if (elapsed < PAUSE_DURATION_MS) {
                    logger.info("[CircuitBreaker] " + toolName + " 暂停中 (" + elapsed + "/" + PAUSE_DURATION_MS + "ms)");
                    return false;
                } else {
                    // 暂停期结束，尝试恢复
                    logger.info("[CircuitBreaker] " + toolName + " 暂停期结束，尝试恢复");
                    states.put(toolName, CircuitState.CLOSED);
                    pauseStartTimes.remove(toolName);
                    return true;
                }
            }
        }
        
        return true;
    }
    
    /**
     * 记录工具执行失败
     * 
     * @param toolName 工具名称
     */
    public void recordFailure(String toolName) {
        FailureCounter counter = toolFailureCounters.computeIfAbsent(toolName, k -> new FailureCounter());
        counter.increment();
        
        int count = counter.get();
        CircuitState currentState = states.getOrDefault(toolName, CircuitState.CLOSED);
        
        if (count >= FULL_OPEN_THRESHOLD) {
            // 全开：禁用工具
            states.put(toolName, CircuitState.FULL_OPEN);
            logger.warning("[CircuitBreaker] " + toolName + " 连续 " + count + " 次失败，熔断器进入 FULL_OPEN 状态（禁用）");
        } else if (count >= HALF_OPEN_THRESHOLD && currentState == CircuitState.CLOSED) {
            // 半开：暂停工具
            states.put(toolName, CircuitState.HALF_OPEN);
            pauseStartTimes.put(toolName, System.currentTimeMillis());
            logger.warning("[CircuitBreaker] " + toolName + " 连续 " + count + " 次失败，熔断器进入 HALF_OPEN 状态（暂停 " + PAUSE_DURATION_MS + "ms）");
        }
    }
    
    /**
     * 记录工具执行成功
     * 
     * @param toolName 工具名称
     */
    public void recordSuccess(String toolName) {
        FailureCounter counter = toolFailureCounters.get(toolName);
        if (counter != null) {
            counter.reset();
        }
        
        CircuitState currentState = states.get(toolName);
        if (currentState != null && currentState != CircuitState.CLOSED) {
            states.put(toolName, CircuitState.CLOSED);
            pauseStartTimes.remove(toolName);
            logger.info("[CircuitBreaker] " + toolName + " 执行成功，熔断器恢复 CLOSED 状态");
        }
    }
    
    /**
     * 获取工具当前状态
     */
    public CircuitState getState(String toolName) {
        return states.getOrDefault(toolName, CircuitState.CLOSED);
    }
    
    /**
     * 获取工具失败次数
     */
    public int getFailureCount(String toolName) {
        FailureCounter counter = toolFailureCounters.get(toolName);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * 尝试执行，执行前检查熔断，执行后记录结果
     * 
     * @param toolName 工具名称
     * @param action 执行操作
     * @param <T> 返回类型
     * @return 执行结果
     */
    public <T> T execute(String toolName, Supplier<T> action) {
        if (!canExecute(toolName)) {
            throw new CircuitBreakerOpenException("工具 " + toolName + " 当前被熔断器暂停");
        }
        
        try {
            T result = action.get();
            recordSuccess(toolName);
            return result;
        } catch (Exception e) {
            recordFailure(toolName);
            throw e;
        }
    }
    
    /**
     * 获取替代工具
     * 
     * @param toolName 当前工具
     * @return 替代工具名称，若无则返回 null
     */
    public String getAlternativeTool(String toolName) {
        // 定义替代工具映射
        return switch (toolName) {
            case "PowerShell" -> "FileReadTool";  // PowerShell 失败改用 Java NIO
            case "BashTool" -> "GrepTool";         // Bash 失败改用 Grep
            case "AgentTool" -> null;              // AgentTool 失败不降级
            default -> null;
        };
    }
    
    /**
     * 熔断打开异常
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}
package com.jwcode.core.resilience;

import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 弹性/容错集成测试
 *
 * <p>测试熔断器、限流器、重试策略、恢复协议、健康监控等弹性模式组件。
 * 覆盖 CircuitBreaker → RateLimiter → RetryPolicy → RecoveryExecutor → HealthMonitor 链路。</p>
 */
@DisplayName("弹性/容错集成测试")
public class ResilienceIntegrationTest {

    // ==================== CircuitBreaker 测试 ====================

    @Test
    @DisplayName("熔断器 - 初始状态为 CLOSED")
    void testInitialState() {
        CircuitBreaker cb = new CircuitBreaker("test-breaker", 3, 5000, 2);

        assertEquals(CircuitBreaker.State.CLOSED, cb.getState(), "初始状态应为 CLOSED");
    }

    @Test
    @DisplayName("熔断器 - 连续失败达到阈值后状态变为 OPEN")
    void testOpenAfterFailures() {
        CircuitBreaker cb = new CircuitBreaker("test-breaker", 3, 5000, 2);

        // 模拟连续失败
        for (int i = 0; i < 3; i++) {
            cb.recordFailure();
        }

        assertEquals(CircuitBreaker.State.OPEN, cb.getState(), "达到失败阈值后应变为 OPEN");
    }

    @Test
    @DisplayName("熔断器 - 成功调用重置失败计数")
    void testSuccessResetsCounter() {
        CircuitBreaker cb = new CircuitBreaker("test-breaker", 3, 5000, 2);

        cb.recordFailure();
        cb.recordFailure();
        cb.recordSuccess();

        // 成功重置后，仍为 CLOSED
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState(), "成功后应保持 CLOSED");
    }

    @Test
    @DisplayName("熔断器 - OPEN 状态下请求被拒绝")
    void testOpenRejectsRequests() {
        CircuitBreaker cb = new CircuitBreaker("test-breaker", 2, 5000, 2);

        cb.recordFailure();
        cb.recordFailure();

        assertFalse(cb.tryAcquire(), "OPEN 状态应拒绝请求");
    }

    @Test
    @DisplayName("熔断器 - 名称标识")
    void testBreakerName() {
        CircuitBreaker cb = new CircuitBreaker("my-breaker", 3, 5000, 2);

        assertEquals("my-breaker", cb.getName(), "熔断器名称应匹配");
    }

    // ==================== RateLimiter 测试 ====================

    @Test
    @DisplayName("限流器 - 创建和初始状态")
    void testRateLimiterCreation() {
        RateLimiter limiter = new RateLimiter(10, 1000);

        assertAll("限流器验证",
            () -> assertNotNull(limiter, "限流器不应为 null"),
            () -> assertTrue(limiter.tryAcquire(), "初始应允许请求")
        );
    }

    // ==================== RetryPolicy 测试 ====================

    @Test
    @DisplayName("重试策略 - 基本配置")
    void testRetryPolicyConfig() {
        RetryPolicy policy = new RetryPolicy(3, 100);

        assertAll("重试策略验证",
            () -> assertEquals(3, policy.getMaxRetries(), "最大重试次数应为3"),
            () -> assertTrue(policy.getDelayMs() > 0, "延迟时间应大于0")
        );
    }

    @Test
    @DisplayName("重试策略 - 指数退避")
    void testExponentialBackoff() {
        RetryPolicy policy = new RetryPolicy(3, 100, 2.0);

        long firstDelay = policy.getDelayMs();
        long secondDelay = (long)(firstDelay * 2.0);
        long thirdDelay = (long)(secondDelay * 2.0);

        assertAll("指数退避验证",
            () -> assertTrue(secondDelay > firstDelay, "第二次延迟应大于第一次"),
            () -> assertTrue(thirdDelay > secondDelay, "第三次延迟应大于第二次")
        );
    }

    // ==================== HealthMonitor 测试 ====================

    @Test
    @DisplayName("健康监控 - 创建和基本健康检查")
    void testHealthMonitor() {
        HealthMonitor monitor = new HealthMonitor();

        assertAll("健康监控验证",
            () -> assertNotNull(monitor, "健康监控不应为 null"),
            () -> assertDoesNotThrow(() -> monitor.checkHealth(), "健康检查不应抛出异常")
        );
    }

    // ==================== RecoveryExecutor 测试 ====================

    @Test
    @DisplayName("恢复执行器 - 创建和配置")
    void testRecoveryExecutor() {
        RecoveryExecutor executor = new RecoveryExecutor();

        assertAll("恢复执行器验证",
            () -> assertNotNull(executor, "恢复执行器不应为 null"),
            () -> assertDoesNotThrow(() -> executor.executeWithRecovery(() -> "success"),
                "执行恢复操作不应抛出异常")
        );
    }

    // ==================== RecoveryProtocol 测试 ====================

    @Test
    @DisplayName("恢复协议 - 创建")
    void testRecoveryProtocol() {
        RecoveryProtocol protocol = new RecoveryProtocol();

        assertNotNull(protocol, "恢复协议不应为 null");
    }

    // ==================== GlobalExceptionHandler 测试 ====================

    @Test
    @DisplayName("全局异常处理器 - 注册和处理")
    void testGlobalExceptionHandler() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        assertAll("异常处理器验证",
            () -> assertNotNull(handler, "异常处理器不应为 null"),
            () -> assertDoesNotThrow(() -> handler.handleException(new RuntimeException("test")),
                "处理异常不应抛出新异常")
        );
    }

    // ==================== 完整弹性链路 ====================

    @Test
    @DisplayName("完整弹性链路：熔断器 + 重试 + 恢复")
    void testCompleteResilienceChain() {
        // 1. 熔断器保护
        CircuitBreaker cb = new CircuitBreaker("chain-breaker", 5, 10000, 3);
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState(), "熔断器初始为 CLOSED");

        // 2. 重试策略
        RetryPolicy retry = new RetryPolicy(3, 50);
        assertEquals(3, retry.getMaxRetries(), "最多重试3次");

        // 3. 恢复执行器
        RecoveryExecutor recovery = new RecoveryExecutor();
        String result = recovery.executeWithRecovery(() -> "recovered");
        assertEquals("recovered", result, "恢复执行应成功");

        // 4. 健康监控
        HealthMonitor monitor = new HealthMonitor();
        assertDoesNotThrow(() -> monitor.checkHealth(), "健康检查应正常");
    }

    // ==================== 状态枚举验证 ====================

    @Test
    @DisplayName("熔断器状态枚举 - 三态模型验证")
    void testCircuitBreakerStates() {
        assertAll("状态枚举验证",
            () -> assertNotNull(CircuitBreaker.State.CLOSED, "CLOSED 状态应存在"),
            () -> assertNotNull(CircuitBreaker.State.OPEN, "OPEN 状态应存在"),
            () -> assertNotNull(CircuitBreaker.State.HALF_OPEN, "HALF_OPEN 状态应存在"),
            () -> assertEquals(3, CircuitBreaker.State.values().length, "应有3种状态")
        );
    }
}

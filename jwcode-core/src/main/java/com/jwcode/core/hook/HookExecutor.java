package com.jwcode.core.hook;

import java.util.concurrent.CompletableFuture;

/**
 * HookExecutor — Hook 执行器接口（核心抽象）。
 *
 * <p>所有 Hook 实现（Shell / HTTP / Prompt / Agent）必须实现此接口。
 * 执行器接收 {@link HookContext}，返回 {@link HookResult}。</p>
 *
 * <h3>契约</h3>
 * <ul>
 *   <li>执行器必须是<strong>线程安全</strong>的（多个事件可能并发触发）</li>
 *   <li>超时后应返回 {@code ALLOW}（fail-open）或按配置返回 {@code DENY}（fail-closed）</li>
 *   <li>执行异常不得向上抛出，应捕获并返回错误结果</li>
 *   <li>不得修改传入的 {@code HookContext}（不可变语义）</li>
 * </ul>
 *
 * @author JWCode Team
 * @since 2.1.0
 */
public interface HookExecutor {

    /**
     * 执行 Hook 决策。
     *
     * @param context Hook 上下文
     * @return 决策结果的 CompletableFuture
     */
    CompletableFuture<HookResult> execute(HookContext context);

    /**
     * 获取此执行器的实现形态。
     */
    HookImplementationType getType();

    /**
     * 获取此执行器的唯一名称。
     */
    String getName();

    /**
     * 是否支持指定事件类型。
     * <p>默认返回 {@code true}，子类可覆盖以优化事件分发。</p>
     */
    default boolean supportsEvent(HookEventType eventType) {
        return true;
    }

    /**
     * 获取此执行器的优先级。
     */
    default HookPriority getPriority() {
        return HookPriority.USER;
    }

    /**
     * 是否启用。
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * 获取超时时间（毫秒）。
     */
    default long getTimeoutMs() {
        return getType().getDefaultTimeoutMs();
    }

    /**
     * 执行超时时是否采用 fail-open（ALLOW）策略。
     * <p>安全级 Hook 应返回 {@code false}（fail-closed）。</p>
     */
    default boolean isFailOpen() {
        return true;
    }
}

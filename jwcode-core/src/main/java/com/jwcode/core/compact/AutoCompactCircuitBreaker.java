package com.jwcode.core.compact;

import java.time.Instant;
import java.util.logging.Logger;

/**
 * AutoCompactCircuitBreaker — 自动压缩断路器（对标 Claude Code autoCompact.ts 电路断路器）。
 *
 * <p>防止自动压缩在不可恢复的场景（如 prompt_too_long）中持续重试，
 * 浪费 API 调用。Claude Code 记录显示：1,279 个会话在单次会话中经历了 50+
 * 次连续失败（最高 3,272 次），造成每天约 250K 次浪费的 API 调用。</p>
 *
 * <h3>断路器策略</h3>
 * <ul>
 *   <li>连续失败 ≥ {@link #MAX_CONSECUTIVE_FAILURES} (3) 次后跳闸</li>
 *   <li>成功后重置计数器</li>
 *   <li>跳闸后本会话不再尝试自动压缩（但仍可手动触发 /compact）</li>
 * </ul>
 *
 * <h3>状态机</h3>
 * <pre>
 *   CLOSED --[3 consecutive failures]--> OPEN
 *   OPEN   --[manual /compact success]--> CLOSED (reset)
 *   OPEN   --[success]--> CLOSED
 * </pre>
 *
 * @author JWCode Team
 * @since 3.0.0
 */
public class AutoCompactCircuitBreaker {

    private static final Logger logger = Logger.getLogger(AutoCompactCircuitBreaker.class.getName());

    /** 连续失败多少次后跳闸。
     *  BQ 2026-03-10: Claude Code 从 1,279 个会话的遥测数据中确定此值。 */
    public static final int MAX_CONSECUTIVE_FAILURES = 3;

    /** 断路器状态 */
    public enum State {
        /** 关闭 — 正常运行 */
        CLOSED,
        /** 打开 — 跳过自动压缩 */
        OPEN,
        /** 半开 — 允许一次尝试以测试恢复 */
        HALF_OPEN
    }

    /** 当前状态 */
    private State state = State.CLOSED;

    /** 连续失败计数 */
    private int consecutiveFailures = 0;

    /** 总失败次数（整个会话） */
    private int totalFailures = 0;

    /** 总成功次数 */
    private int totalSuccesses = 0;

    /** 跳闸时间 */
    private Instant trippedAt;

    /** 最后一次失败的错误信息 */
    private String lastErrorMessage;

    /**
     * 记录一次成功的自动压缩。
     * 重置连续失败计数器，关闭断路器。
     */
    public void recordSuccess() {
        consecutiveFailures = 0;
        totalSuccesses++;
        if (state == State.OPEN || state == State.HALF_OPEN) {
            logger.info("[CircuitBreaker] 重置 — 压缩成功，断路器关闭");
            state = State.CLOSED;
        }
    }

    /**
     * 记录一次失败的自动压缩。
     * 递增连续失败计数，达到阈值时跳闸。
     *
     * @param errorMessage 失败原因
     * @return 是否已跳闸（供调用方判断是否需要警告）
     */
    public boolean recordFailure(String errorMessage) {
        consecutiveFailures++;
        totalFailures++;
        lastErrorMessage = errorMessage;

        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            if (state != State.OPEN) {
                state = State.OPEN;
                trippedAt = Instant.now();
                logger.warning("[CircuitBreaker] 跳闸！连续 " + consecutiveFailures
                    + " 次失败。跳过本会话后续自动压缩。"
                    + " 最后错误: " + errorMessage);
            }
            return true;
        }

        logger.fine("[CircuitBreaker] 连续失败 " + consecutiveFailures
            + "/" + MAX_CONSECUTIVE_FAILURES + ": " + errorMessage);
        return false;
    }

    /**
     * 检查是否允许自动压缩。
     * 断路器打开时返回 false。
     */
    public boolean allowAutoCompact() {
        return state != State.OPEN;
    }

    /**
     * 半开断路器 — 允许一次尝试。
     * 下次压缩成功则关闭，失败则重新打开。
     */
    public void halfOpen() {
        if (state == State.OPEN) {
            state = State.HALF_OPEN;
            logger.info("[CircuitBreaker] 半开 — 允许一次尝试");
        }
    }

    // ==================== 查询方法 ====================

    public State getState() { return state; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public int getTotalFailures() { return totalFailures; }
    public int getTotalSuccesses() { return totalSuccesses; }
    public Instant getTrippedAt() { return trippedAt; }
    public String getLastErrorMessage() { return lastErrorMessage; }

    /**
     * 是否已跳闸。
     */
    public boolean isTripped() {
        return state == State.OPEN;
    }

    /**
     * 获取状态摘要（用于 UI/日志）。
     */
    public String getStatusSummary() {
        return String.format(
            "CircuitBreaker[%s] failures=%d/%d successes=%d lastError=%s",
            state, consecutiveFailures, totalFailures, totalSuccesses,
            lastErrorMessage != null ? lastErrorMessage : "N/A"
        );
    }

    /**
     * 重置所有状态（用于测试或手动重置）。
     */
    public void reset() {
        state = State.CLOSED;
        consecutiveFailures = 0;
        totalFailures = 0;
        totalSuccesses = 0;
        trippedAt = null;
        lastErrorMessage = null;
    }
}

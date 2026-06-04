package com.jwcode.core.tool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Tracks consecutive same-tool failures to enable early-exit strategy switching.
 *
 * <p>When the same tool fails 3 consecutive times with a similar error signature,
 * the tracker signals that the agent should change strategy rather than retrying.
 * Any successful call resets the counter for that tool.</p>
 *
 * <p>This prevents error-stacking pollution in the context window — the agent
 * receives an explicit "change strategy" signal instead of burning turns on
 * repeated failures.</p>
 */
public class ConsecutiveFailureTracker {

    private static final Logger logger = Logger.getLogger(ConsecutiveFailureTracker.class.getName());

    /** Threshold: consecutive failures before signalling strategy change */
    private static final int FAILURE_THRESHOLD = 3;

    /** Per-tool failure state */
    private final Map<String, FailureState> stateMap = new ConcurrentHashMap<>();

    /**
     * Record a failed tool execution. Returns a strategy-change message if the
     * threshold has been reached, or null if the agent should keep trying.
     */
    public String recordFailure(String toolName, String errorMessage) {
        FailureState state = stateMap.computeIfAbsent(toolName, k -> new FailureState());
        String errorSignature = extractSignature(errorMessage);

        synchronized (state) {
            if (errorSignature.equals(state.lastErrorSignature)) {
                state.consecutiveCount++;
            } else {
                state.consecutiveCount = 1;
                state.lastErrorSignature = errorSignature;
            }

            if (state.consecutiveCount >= FAILURE_THRESHOLD) {
                logger.warning("[EarlyExit] " + toolName + " failed "
                    + state.consecutiveCount + " consecutive times with same pattern. "
                    + "Signalling strategy change.");
                String message = "\n\n⚠️ 【策略提醒】工具 '" + toolName + "' 已连续失败 "
                    + state.consecutiveCount + " 次，错误模式相同。"
                    + "\n请停止重复尝试，改用以下策略之一："
                    + "\n1. 使用替代工具完成相同目标"
                    + "\n2. 调整参数或前置条件后重试"
                    + "\n3. 向用户确认需求或环境是否正确"
                    + "\n4. 标记当前任务为受阻，继续下一个可执行任务"
                    + "\n错误摘要: " + truncate(errorMessage, 200);
                return message;
            }
        }
        return null;
    }

    /** Record a successful tool execution — resets the failure counter for that tool. */
    public void recordSuccess(String toolName) {
        FailureState state = stateMap.get(toolName);
        if (state != null) {
            synchronized (state) {
                state.consecutiveCount = 0;
                state.lastErrorSignature = null;
            }
        }
    }

    /** Reset all failure counters (e.g., on session reset). */
    public void reset() {
        stateMap.clear();
    }

    /** Reset failure counter for a specific tool. */
    public void reset(String toolName) {
        stateMap.remove(toolName);
    }

    /**
     * Extract a stable error signature from the error message.
     * Strips variable parts (file paths, timestamps, IDs) to detect
     * "same root cause" across consecutive failures.
     */
    private String extractSignature(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return "(empty)";
        }
        // Normalize: strip variable substrings and keep structural parts
        return errorMessage
            .replaceAll("/[^\\s]+\\.java", "/<file>.java")
            .replaceAll("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}", "<timestamp>")
            .replaceAll("0x[0-9a-fA-F]+", "<hex>")
            .replaceAll("\\b\\d{5,}\\b", "<id>")
            .replaceAll("line \\d+", "line <N>")
            .lines()
            .findFirst()
            .orElse(errorMessage)
            .trim();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private static class FailureState {
        int consecutiveCount = 0;
        String lastErrorSignature;
    }
}

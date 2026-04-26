package com.jwcode.core.resilience;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 恢复执行器 — 实现三阶段错误恢复协议。
 *
 * <p>将错误恢复从"重试 3 次然后报错"升级为"可预测、可控制、可审计"的降级流程：</p>
 * <ol>
 *   <li>AutoRetry — 指数退避重试</li>
 *   <li>AiRepair — AI 分析并调整策略（占位符，待上层接入）</li>
 *   <li>HumanEscalation — 向用户报告并等待决策</li>
 * </ol>
 */
public class RecoveryExecutor {

    private static final Logger logger = Logger.getLogger(RecoveryExecutor.class.getName());

    /**
     * 带恢复策略的执行
     *
     * @param operation 要执行的操作
     * @param protocol 恢复协议
     * @param operationName 操作名称（用于日志）
     * @return 操作结果；如果所有恢复阶段都失败，返回包含错误信息的 CompletableFuture
     */
    public static <T> CompletableFuture<T> executeWithRecovery(
            Supplier<CompletableFuture<T>> operation,
            RecoveryProtocol protocol,
            String operationName) {

        return executeInternal(operation, protocol, operationName, 0);
    }

    private static <T> CompletableFuture<T> executeInternal(
            Supplier<CompletableFuture<T>> operation,
            RecoveryProtocol protocol,
            String operationName,
            int depth) {

        if (depth > 5) {
            return CompletableFuture.failedFuture(
                new RecoveryException("Recovery depth exceeded for: " + operationName)
            );
        }

        return operation.get().exceptionallyCompose(error -> {
            logger.log(Level.WARNING,
                String.format("[%s] Operation failed (depth=%d), applying recovery: %s",
                    operationName, depth, protocol.getClass().getSimpleName()),
                error);

            if (protocol instanceof RecoveryProtocol.AutoRetry autoRetry) {
                return handleAutoRetry(operation, autoRetry, operationName, depth, error);
            } else if (protocol instanceof RecoveryProtocol.AiRepair aiRepair) {
                return handleAiRepair(operation, aiRepair, operationName, depth, error);
            } else if (protocol instanceof RecoveryProtocol.HumanEscalation escalation) {
                return handleHumanEscalation(escalation, operationName, error);
            }

            return CompletableFuture.failedFuture(error);
        });
    }

    private static <T> CompletableFuture<T> handleAutoRetry(
            Supplier<CompletableFuture<T>> operation,
            RecoveryProtocol.AutoRetry autoRetry,
            String operationName,
            int depth,
            Throwable error) {

        if (depth >= autoRetry.maxAttempts()) {
            logger.warning(String.format("[%s] AutoRetry exhausted (%d/%d attempts)",
                operationName, depth, autoRetry.maxAttempts()));
            // 降级到 AiRepair
            return executeInternal(operation, new RecoveryProtocol.AiRepair(), operationName, depth + 1);
        }

        Duration delay = autoRetry.getDelayForAttempt(depth);
        logger.info(String.format("[%s] AutoRetry attempt %d/%d after %dms",
            operationName, depth + 1, autoRetry.maxAttempts(), delay.toMillis()));

        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }).thenCompose(v -> executeInternal(operation, autoRetry, operationName, depth + 1));
    }

    private static <T> CompletableFuture<T> handleAiRepair(
            Supplier<CompletableFuture<T>> operation,
            RecoveryProtocol.AiRepair aiRepair,
            String operationName,
            int depth,
            Throwable error) {

        logger.info(String.format("[%s] AiRepair triggered: %s", operationName, aiRepair.errorAnalysisPrompt()));
        // AiRepair 是 AI 驱动修复的占位符 — 实际实现需要上层 LLM 调用
        // 目前策略：记录分析提示，然后降级到 HumanEscalation
        return executeInternal(operation, new RecoveryProtocol.HumanEscalation(
            "Operation '" + operationName + "' failed after AutoRetry. Error: " + error.getMessage()),
            operationName, depth + 1);
    }

    private static <T> CompletableFuture<T> handleHumanEscalation(
            RecoveryProtocol.HumanEscalation escalation,
            String operationName,
            Throwable error) {

        String message = String.format("[%s] HumanEscalation: %s. Original error: %s",
            operationName, escalation.contextSummary(), error.getMessage());
        logger.severe(message);
        return CompletableFuture.failedFuture(new RecoveryException(message, error));
    }

    public static class RecoveryException extends RuntimeException {
        public RecoveryException(String message) {
            super(message);
        }

        public RecoveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

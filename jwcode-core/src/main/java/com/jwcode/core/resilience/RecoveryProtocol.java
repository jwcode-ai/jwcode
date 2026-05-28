package com.jwcode.core.resilience;

import java.time.Duration;

/**
 * 恢复协议 — 工具执行错误的三阶段降级策略。
 *
 * <p>解决"无限重试烧 token"的核心痛点：</p>
 * <ol>
 *   <li><strong>AutoRetry</strong> — 指数退避自动重试（耗时 3 秒）</li>
 *   <li><strong>AiRepair</strong> — AI 分析错误并调整策略（耗时 30 秒）</li>
 *   <li><strong>HumanEscalation</strong> — 需要用户确认（耗时无限，但可控）</li>
 * </ol>
 */
public sealed interface RecoveryProtocol {

    /**
     * 第一阶段：自动重试（带指数退避）
     */
    record AutoRetry(int maxAttempts, Duration initialDelay) implements RecoveryProtocol {
        public AutoRetry {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be >= 1");
            }
            if (initialDelay == null || initialDelay.isNegative()) {
                initialDelay = Duration.ofSeconds(1);
            }
        }

        public AutoRetry() {
            this(3, Duration.ofSeconds(1));
        }

        public Duration getDelayForAttempt(int attempt) {
            long millis = initialDelay.toMillis() * (1L << attempt);
            return Duration.ofMillis(Math.min(millis, 30_000)); // cap at 30s
        }
    }

    /**
     * 第二阶段：AI 自主修复
     */
    record AiRepair(String errorAnalysisPrompt, com.jwcode.core.llm.LLMService llmService) implements RecoveryProtocol {
        public AiRepair {
            if (errorAnalysisPrompt == null || errorAnalysisPrompt.isBlank()) {
                errorAnalysisPrompt = "Analyze the error and determine the best repair strategy.";
            }
        }

        public AiRepair() { this("Analyze the error and determine the best repair strategy.", null); }
        public AiRepair(com.jwcode.core.llm.LLMService llmService) { this("Analyze the error and determine the best repair strategy.", llmService); }
    }

    /**
     * 第三阶段：人类介入
     */
    record HumanEscalation(String contextSummary) implements RecoveryProtocol {
        public HumanEscalation {
            if (contextSummary == null) {
                contextSummary = "An unrecoverable error occurred. User intervention required.";
            }
        }

        public HumanEscalation() {
            this("An unrecoverable error occurred. User intervention required.");
        }
    }
}

package com.jwcode.core.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * 分析观察者 — 聚合执行统计，用于成本追踪与性能分析。
 *
 * <p>统计项：LLM 调用次数、工具调用次数、Token 消耗、错误次数、总耗时。</p>
 */
public class AnalyticsObserver implements ObservationPipeline.Observer {

    private static final Logger logger = Logger.getLogger(AnalyticsObserver.class.getName());

    private final AtomicInteger llmCallCount = new AtomicInteger(0);
    private final AtomicInteger toolCallCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);
    private final AtomicLong totalCacheCreationTokens = new AtomicLong(0);
    private final AtomicLong totalCacheReadTokens = new AtomicLong(0);
    private volatile Instant sessionStart;

    public AnalyticsObserver() {
        this.sessionStart = Instant.now();
    }

    @Override
    public void onEvent(ObservationEvent event) {
        if (event instanceof ObservationEvent.ToolCall) {
            toolCallCount.incrementAndGet();
        } else if (event instanceof ObservationEvent.TokenUsage e) {
            llmCallCount.incrementAndGet();
            totalPromptTokens.addAndGet(e.promptTokens());
            totalCompletionTokens.addAndGet(e.completionTokens());
            totalCacheCreationTokens.addAndGet(e.cacheCreationInputTokens());
            totalCacheReadTokens.addAndGet(e.cacheReadInputTokens());
        } else if (event instanceof ObservationEvent.Error) {
            errorCount.incrementAndGet();
        } else if (event instanceof ObservationEvent.StepStart e) {
            if (sessionStart == null) {
                sessionStart = e.timestamp();
            }
        }
        // 其他事件不统计
    }

    @Override
    public String getObserverName() {
        return "AnalyticsObserver";
    }

    /**
     * 生成执行摘要
     */
    public ExecutionSummary getSummary() {
        Duration elapsed = sessionStart != null ? Duration.between(sessionStart, Instant.now()) : Duration.ZERO;
        return new ExecutionSummary(
            llmCallCount.get(),
            toolCallCount.get(),
            errorCount.get(),
            totalPromptTokens.get(),
            totalCompletionTokens.get(),
            totalCacheCreationTokens.get(),
            totalCacheReadTokens.get(),
            elapsed
        );
    }

    public void reset() {
        llmCallCount.set(0);
        toolCallCount.set(0);
        errorCount.set(0);
        totalPromptTokens.set(0);
        totalCompletionTokens.set(0);
        totalCacheCreationTokens.set(0);
        totalCacheReadTokens.set(0);
        sessionStart = Instant.now();
    }

    public record ExecutionSummary(
        int llmCalls,
        int toolCalls,
        int errors,
        long promptTokens,
        long completionTokens,
        long cacheCreationTokens,
        long cacheReadTokens,
        Duration elapsed
    ) {
        public long totalTokens() {
            return promptTokens + completionTokens;
        }

        public long cacheTotal() {
            return cacheReadTokens + cacheCreationTokens + promptTokens;
        }

        public double cacheHitRate() {
            long total = cacheTotal();
            return total > 0 ? (double) cacheReadTokens / total : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                "ExecutionSummary{llmCalls=%d, toolCalls=%d, errors=%d, tokens=%d, cacheHitRate=%.1f%%, elapsed=%.1fs}",
                llmCalls, toolCalls, errors, totalTokens(), cacheHitRate() * 100, elapsed.toMillis() / 1000.0
            );
        }
    }
}

package com.jwcode.core.observability;

import java.time.Duration;
import java.time.Instant;

/**
 * 终端观察者 — 将关键事件输出到控制台。
 *
 * <p>轻量级实现，仅展示高价值事件（工具调用、错误、检查点），
 * 避免流式内容造成的终端刷屏。</p>
 */
public class TerminalObserver implements ObservationPipeline.Observer {

    private final boolean verbose;
    private Instant lastToolCallStart;

    public TerminalObserver() {
        this(false);
    }

    public TerminalObserver(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public void onEvent(ObservationEvent event) {
        if (event instanceof ObservationEvent.StepStart e) {
            if (verbose) {
                System.out.println("[→] " + e.stepName() + ": " + e.description());
            }
        } else if (event instanceof ObservationEvent.ToolCall e) {
            lastToolCallStart = e.timestamp();
            System.out.println("[?] 调用工具: " + e.toolName());
        } else if (event instanceof ObservationEvent.ToolResult e) {
            Duration elapsed = e.elapsed() != null ? e.elapsed() :
                (lastToolCallStart != null ? Duration.between(lastToolCallStart, e.timestamp()) : Duration.ZERO);
            String status = e.success() ? "OK" : "FAIL";
            System.out.println("[" + status + "] " + e.toolName() + " 完成 (" + formatDuration(elapsed) + ")");
        } else if (event instanceof ObservationEvent.Error e) {
            System.err.println("[!] 错误 [" + e.source() + "]: " + e.message());
            if (e.recoveryHint() != null) {
                System.err.println("    建议: " + e.recoveryHint());
            }
        } else if (event instanceof ObservationEvent.Checkpoint e) {
            System.out.println("[*] 检查点: " + e.summary());
        } else if (event instanceof ObservationEvent.TokenUsage e) {
            if (verbose) {
                System.out.println("[i] Token: +" + e.completionTokens() + " (累计 " + e.totalTokens() + ") [" + e.model() + "]");
            }
        }
        // 其他事件不输出到终端
    }

    @Override
    public String getObserverName() {
        return "TerminalObserver";
    }

    private String formatDuration(Duration d) {
        long ms = d.toMillis();
        if (ms < 1000) {
            return ms + "ms";
        }
        return (ms / 1000.0) + "s";
    }
}

package com.jwcode.core.tool.search;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 轻量级搜索取消令牌 — 用于中断长时间运行的搜索操作。
 */
public class SearchCancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final long deadlineNanos;

    public SearchCancellationToken(long timeoutMs) {
        this.deadlineNanos = System.nanoTime() + timeoutMs * 1_000_000L;
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get() || isTimeout();
    }

    public boolean isTimeout() {
        return System.nanoTime() > deadlineNanos;
    }

    public void throwIfCancelled() {
        if (isCancelled()) {
            throw new SearchCancelledException();
        }
    }

    public static SearchCancellationToken withTimeout(long timeoutMs) {
        return new SearchCancellationToken(timeoutMs);
    }

    public static SearchCancellationToken none() {
        return new SearchCancellationToken(Long.MAX_VALUE);
    }

    public static class SearchCancelledException extends RuntimeException {
        public SearchCancelledException() {
            super("搜索已取消或超时");
        }
    }
}

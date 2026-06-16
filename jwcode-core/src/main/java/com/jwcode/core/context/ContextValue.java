package com.jwcode.core.context;

import java.util.Objects;

/**
 * ContextValue — 上下文源加载的结果包装。
 *
 * <p>有三种状态：
 * <ul>
 *   <li>{@link #available(Object)} — 值可用</li>
 *   <li>{@link #unavailable()} — 值暂时不可用（如插件未加载）</li>
 *   <li>{@link #error(String)} — 加载时出错</li>
 * </ul>
 */
public final class ContextValue<A> {

    public enum Status { AVAILABLE, UNAVAILABLE, ERROR }

    private final Status status;
    private final A value;
    private final String errorMessage;

    private ContextValue(Status status, A value, String errorMessage) {
        this.status = status;
        this.value = value;
        this.errorMessage = errorMessage;
    }

    public static <A> ContextValue<A> available(A value) {
        return new ContextValue<>(Status.AVAILABLE, Objects.requireNonNull(value), null);
    }

    @SuppressWarnings("unchecked")
    public static <A> ContextValue<A> unavailable() {
        return (ContextValue<A>) UnavailableHolder.INSTANCE;
    }

    public static <A> ContextValue<A> error(String msg) {
        return new ContextValue<>(Status.ERROR, null, Objects.requireNonNull(msg));
    }

    public Status getStatus() { return status; }
    public A getValue() {
        if (status != Status.AVAILABLE) throw new IllegalStateException("Value not available: " + status);
        return value;
    }
    public String getErrorMessage() { return errorMessage; }
    public boolean isAvailable() { return status == Status.AVAILABLE; }
    public boolean isUnavailable() { return status == Status.UNAVAILABLE; }
    public boolean isError() { return status == Status.ERROR; }

    public A orElse(A fallback) { return isAvailable() ? value : fallback; }

    private static class UnavailableHolder {
        @SuppressWarnings("rawtypes")
        static final ContextValue INSTANCE = new ContextValue<>(Status.UNAVAILABLE, null, null);
    }

    @Override
    public String toString() {
        return switch (status) {
            case AVAILABLE -> "ContextValue{AVAILABLE, value=" + value + "}";
            case UNAVAILABLE -> "ContextValue{UNAVAILABLE}";
            case ERROR -> "ContextValue{ERROR, msg=" + errorMessage + "}";
        };
    }
}

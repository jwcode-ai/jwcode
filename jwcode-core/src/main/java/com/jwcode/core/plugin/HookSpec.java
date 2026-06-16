package com.jwcode.core.plugin;

import com.jwcode.core.hook.HookEventType;
import com.jwcode.core.hook.HookResult;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * HookSpec — 类型安全的 Hook 定义。
 *
 * <p>允许插件声明 typed hook handler，替代原始 {@code HookExecutor} 接口。
 * 通过 {@code inputClass} / {@code outputClass} 提供类型约束。
 *
 * @param <I> hook 输入类型
 * @param <O> hook 输出类型
 */
public class HookSpec<I, O> {

    private final String name;
    private final HookEventType eventType;
    private final Class<I> inputClass;
    private final Class<O> outputClass;
    private final Function<I, CompletableFuture<O>> handler;

    public HookSpec(String name, HookEventType eventType,
                    Class<I> inputClass, Class<O> outputClass,
                    Function<I, CompletableFuture<O>> handler) {
        this.name = Objects.requireNonNull(name);
        this.eventType = Objects.requireNonNull(eventType);
        this.inputClass = Objects.requireNonNull(inputClass);
        this.outputClass = Objects.requireNonNull(outputClass);
        this.handler = Objects.requireNonNull(handler);
    }

    public String getName() { return name; }
    public HookEventType getEventType() { return eventType; }
    public Class<I> getInputClass() { return inputClass; }
    public Class<O> getOutputClass() { return outputClass; }

    /**
     * 执行 hook，返回转型后的结果。
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<HookResult> execute(Object input) {
        I typedInput = inputClass.cast(input);
        return handler.apply(typedInput)
            .thenApply(output -> HookResult.allow(name, "HookSpec executed"))
            .exceptionally(e -> HookResult.error(name, e.getMessage()));
    }

    /**
     * 判断是否支持指定事件类型。
     */
    public boolean supports(HookEventType type) {
        return this.eventType == type;
    }

    // ==================== 链式工厂 ====================

    /**
     * HookSpec 注册表 — 管理所有已注册的 HookSpec。
     */
    public static class Registry {
        private final Map<String, HookSpec<?, ?>> specs = new java.util.concurrent.ConcurrentHashMap<>();

        public <I, O> void register(HookSpec<I, O> spec) {
            specs.put(spec.getName(), spec);
        }

        public void unregister(String name) {
            specs.remove(name);
        }

        @SuppressWarnings("unchecked")
        public <I, O> HookSpec<I, O> get(String name) {
            return (HookSpec<I, O>) specs.get(name);
        }

        public java.util.List<HookSpec<?, ?>> getForEvent(HookEventType type) {
            return specs.values().stream()
                .filter(s -> s.supports(type))
                .toList();
        }

        public Map<String, HookSpec<?, ?>> getAll() {
            return java.util.Collections.unmodifiableMap(specs);
        }
    }
}

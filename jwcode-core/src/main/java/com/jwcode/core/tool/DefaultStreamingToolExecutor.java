package com.jwcode.core.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * DefaultStreamingToolExecutor — StreamingToolExecutor 的默认实现。
 *
 * 内部维护一个 CopyOnWriteArrayList 存放已完成结果（线程安全），
 * 并在每个工具完成后同步触发已注册的回调。
 * 所有 future 通过 CompletableFuture.allOf 聚合，支持 whenAllComplete。
 */
public class DefaultStreamingToolExecutor<T> implements StreamingToolExecutor<T> {

    private static final Logger logger = Logger.getLogger(DefaultStreamingToolExecutor.class.getName());

    private final CopyOnWriteArrayList<T> completedResults = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<T>> callbacks = new CopyOnWriteArrayList<>();
    private final List<CompletableFuture<T>> pendingFutures = java.util.Collections.synchronizedList(new ArrayList<>());
    private volatile CompletableFuture<Void> allOf = null;

    @Override
    public CompletableFuture<T> submit(CompletableFuture<T> executionFuture) {
        pendingFutures.add(executionFuture);

        // 重建 allOf future
        synchronized (pendingFutures) {
            allOf = null; // 失效，在 whenAllComplete 中惰性重建
        }

        // 工具完成后：收集结果 + 触发回调
        return executionFuture.whenComplete((result, throwable) -> {
            if (throwable != null) {
                logger.warning("[StreamingToolExecutor] Tool execution failed: " + throwable.getMessage());
                return;
            }
            if (result != null) {
                completedResults.add(result);
                for (Consumer<T> cb : callbacks) {
                    try {
                        cb.accept(result);
                    } catch (Exception e) {
                        logger.warning("[StreamingToolExecutor] Callback error: " + e.getMessage());
                    }
                }
            }
        });
    }

    @Override
    public void onToolComplete(Consumer<T> callback) {
        callbacks.add(callback);
    }

    @Override
    public synchronized CompletableFuture<Void> whenAllComplete() {
        if (allOf == null) {
            CompletableFuture<T>[] futuresArray;
            synchronized (pendingFutures) {
                futuresArray = pendingFutures.toArray(new CompletableFuture[0]);
            }
            allOf = CompletableFuture.allOf(futuresArray);
        }
        return allOf;
    }

    @Override
    public List<T> getCompletedResults() {
        return new ArrayList<>(completedResults);
    }

    @Override
    public int pendingCount() {
        int total;
        synchronized (pendingFutures) {
            total = pendingFutures.size();
        }
        return Math.max(0, total - completedResults.size());
    }

    @Override
    public void discard() {
        completedResults.clear();
        callbacks.clear();
        synchronized (pendingFutures) {
            pendingFutures.clear();
        }
        allOf = null;
    }
}

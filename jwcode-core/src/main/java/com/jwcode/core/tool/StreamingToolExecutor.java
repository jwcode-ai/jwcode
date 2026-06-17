package com.jwcode.core.tool;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * StreamingToolExecutor — 流式工具执行器。
 *
 * 与 {@link ToolExecutor#executeBatch} 的「全部完成 → 统一返回」不同，
 * StreamingToolExecutor 在每完成一个工具时就触发 onToolComplete 回调，
 * 使上层可以立即将结果 yield 给前端，无需等待所有工具执行完毕。
 *
 * @param <T> 工具执行结果类型
 */
public interface StreamingToolExecutor<T> {

    /**
     * 提交一个工具执行任务。任务会异步执行，完成后触发注册的回调。
     *
     * @param executionFuture 工具执行的 CompletableFuture
     * @return 传入的 future 本身
     */
    CompletableFuture<T> submit(CompletableFuture<T> executionFuture);

    /**
     * 注册工具完成回调。每个工具执行完毕后会依次调用所有已注册的回调。
     * 回调在当前工具完成的线程中同步执行。
     */
    void onToolComplete(Consumer<T> callback);

    /**
     * 返回一个在所有已提交工具完成后完成的 future。
     */
    CompletableFuture<Void> whenAllComplete();

    /**
     * 返回当前已完成的工具结果列表（线程安全）。
     */
    List<T> getCompletedResults();

    /**
     * 返回尚未完成的工具数量。
     */
    int pendingCount();

    /**
     * 丢弃所有未完成的任务并清空结果。
     */
    void discard();
}

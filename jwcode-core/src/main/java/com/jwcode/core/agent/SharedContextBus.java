package com.jwcode.core.agent;

import com.jwcode.core.agent.parallel.SubAgentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * SharedContextBus — 子 Agent 间中间成果共享总线。
 *
 * <p>实现 Kimi Code Claw Groups 的「中间成果共享」机制：</p>
 * <ul>
 *   <li>子 Agent 执行过程中可发布<strong>中间成果</strong>（非最终结果）</li>
 *   <li>依赖该 Agent 的其他任务可<strong>提前消费</strong>中间成果，无需等待全部完成</li>
 *   <li>最终结果通过 {@link CompletableFuture} 正常返回</li>
 * </ul>
 */
public class SharedContextBus {

    private static final Logger logger = LoggerFactory.getLogger(SharedContextBus.class);

    private final Map<String, CompletableFuture<SubAgentResult>> resultFutures = new ConcurrentHashMap<>();
    private final Map<String, String> intermediateArtifacts = new ConcurrentHashMap<>();
    private final Map<String, Consumer<String>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, Object> blackboard = new ConcurrentHashMap<>();

    /**
     * 注册一个子 Agent 任务的结果 Future。
     */
    public void registerTask(String taskId, CompletableFuture<SubAgentResult> future) {
        resultFutures.put(taskId, future);
        logger.debug("[SharedContextBus] 注册任务 | taskId={}", taskId);
    }

    /**
     * 发布中间成果 — 任务尚未完成，但已有可共享的中间产出。
     */
    public void publishIntermediate(String taskId, String artifact) {
        intermediateArtifacts.put(taskId + ":" + System.currentTimeMillis(), artifact);
        logger.info("[SharedContextBus] 中间成果 | taskId={} | length={}", taskId, artifact.length());

        // 通知订阅者
        Consumer<String> subscriber = subscribers.get(taskId);
        if (subscriber != null) {
            subscriber.accept(artifact);
        }
    }

    /**
     * 订阅某任务的中间成果。
     */
    public void subscribe(String taskId, Consumer<String> consumer) {
        subscribers.put(taskId, consumer);
    }

    /**
     * 获取某任务的中间成果（如果已有）。
     */
    public String getLatestIntermediate(String taskId) {
        return intermediateArtifacts.entrySet().stream()
            .filter(e -> e.getKey().startsWith(taskId + ":"))
            .max(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .orElse(null);
    }

    /**
     * 等待任务最终结果。
     */
    public CompletableFuture<SubAgentResult> awaitResult(String taskId) {
        CompletableFuture<SubAgentResult> future = resultFutures.get(taskId);
        if (future == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Task not registered: " + taskId)
            );
        }
        return future;
    }

    public void cleanup(String taskId) {
        resultFutures.remove(taskId);
        subscribers.remove(taskId);
        intermediateArtifacts.keySet().removeIf(k -> k.startsWith(taskId + ":"));
    }

    public void put(String key, Object value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Blackboard key must not be blank");
        }
        if (value == null) {
            blackboard.remove(key);
        } else {
            blackboard.put(key, value);
        }
    }

    public Object get(String key) {
        return blackboard.get(key);
    }

    public Map<String, Object> snapshot() {
        return new ConcurrentHashMap<>(blackboard);
    }

    public void clearBlackboard() {
        blackboard.clear();
    }

    public Object remove(String key) {
        return blackboard.remove(key);
    }

    public Map<String, String> intermediateSnapshot() {
        return new ConcurrentHashMap<>(intermediateArtifacts);
    }
}

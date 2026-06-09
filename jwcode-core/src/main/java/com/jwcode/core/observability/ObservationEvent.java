package com.jwcode.core.observability;

import java.time.Duration;
import java.time.Instant;

/**
 * 可观测事件 — AI 原生执行引擎的事件源。
 *
 * <p>将 LLMQueryEngine 的整个思考-行动-观察循环变成结构化事件流，
 * 取代传统的 {@code StepCallback} 回调接口。所有消费者（终端、Web UI、
 * 成本分析、日志）以独立 Observer 身份订阅，零耦合。</p>
 *
 * <p>设计原则：事件是不可变值对象，携带完整上下文，便于追溯与重放。</p>
 */
public sealed interface ObservationEvent {

    Instant timestamp();

    /**
     * 步骤开始 — 某阶段任务启动
     */
    record StepStart(String stepName, String description, Instant timestamp) implements ObservationEvent {
        public StepStart(String stepName, String description) {
            this(stepName, description, Instant.now());
        }
    }

    /**
     * AI 思考内容 — 模型内部推理过程
     */
    record Thinking(String stepName, String content, Instant timestamp) implements ObservationEvent {
        public Thinking(String stepName, String content) {
            this(stepName, content, Instant.now());
        }
    }

    /**
     * 工具调用 — AI 决定调用某个工具
     */
    record ToolCall(String toolName, String arguments, String toolCallId, Instant timestamp) implements ObservationEvent {
        public ToolCall(String toolName, String arguments, String toolCallId) {
            this(toolName, arguments, toolCallId, Instant.now());
        }
    }

    /**
     * 工具结果 — 工具执行完成
     */
    record ToolResult(String toolName, String result, boolean success, Duration elapsed, String toolCallId, Instant timestamp) implements ObservationEvent {
        public ToolResult(String toolName, String result, boolean success, Duration elapsed, String toolCallId) {
            this(toolName, result, success, elapsed, toolCallId, Instant.now());
        }
    }

    /**
     * Token 使用统计 — 每次 LLM 调用的消耗
     */
    record TokenUsage(
        int promptTokens,
        int completionTokens,
        String model,
        int cacheCreationInputTokens,
        int cacheReadInputTokens,
        Instant timestamp
    ) implements ObservationEvent {
        /** Backward-compatible: no cache data. */
        public TokenUsage(int promptTokens, int completionTokens, String model) {
            this(promptTokens, completionTokens, model, 0, 0, Instant.now());
        }

        /** Full constructor with cache data. */
        public TokenUsage(int promptTokens, int completionTokens, String model,
                          int cacheCreationInputTokens, int cacheReadInputTokens) {
            this(promptTokens, completionTokens, model, cacheCreationInputTokens, cacheReadInputTokens, Instant.now());
        }

        public int totalTokens() {
            return promptTokens + completionTokens;
        }

        /** Total tokens that touched the cache layer (read + creation + uncached input). */
        public int cacheTotal() {
            return cacheReadInputTokens + cacheCreationInputTokens + promptTokens;
        }

        /** Cache read rate: read / (read + creation + uncached). 0..1, or 0 if no cache activity. */
        public double cacheHitRate() {
            int total = cacheTotal();
            return total > 0 ? (double) cacheReadInputTokens / total : 0.0;
        }
    }

    /**
     * 错误 — 执行过程中的异常或失败
     */
    record Error(String source, String message, String recoveryHint, Throwable cause, Instant timestamp) implements ObservationEvent {
        public Error(String source, String message, String recoveryHint) {
            this(source, message, recoveryHint, null, Instant.now());
        }

        public Error(String source, String message, String recoveryHint, Throwable cause) {
            this(source, message, recoveryHint, cause, Instant.now());
        }
    }

    /**
     * 检查点 — 关键决策点或会话摘要
     */
    record Checkpoint(String summary, String detail, Instant timestamp) implements ObservationEvent {
        public Checkpoint(String summary) {
            this(summary, null, Instant.now());
        }

        public Checkpoint(String summary, String detail) {
            this(summary, detail, Instant.now());
        }
    }

    /**
     * 步骤完成 — 某阶段任务结束
     */
    record StepComplete(String stepName, String result, Instant timestamp) implements ObservationEvent {
        public StepComplete(String stepName, String result) {
            this(stepName, result, Instant.now());
        }
    }

    /**
     * 流式内容片段 — 实时生成的文本
     */
    record ContentChunk(String chunk, Instant timestamp) implements ObservationEvent {
        public ContentChunk(String chunk) {
            this(chunk, Instant.now());
        }
    }

    /**
     * 流式思考片段 — 实时生成的推理文本
     */
    record ThinkingChunk(String chunk, Instant timestamp) implements ObservationEvent {
        public ThinkingChunk(String chunk) {
            this(chunk, Instant.now());
        }
    }

    /**
     * 任务状态变更 — 当前激活任务的状态流转
     */
    record TaskStateChanged(String taskId, String taskDescription,
                            com.jwcode.core.task.TaskStatus oldStatus,
                            com.jwcode.core.task.TaskStatus newStatus,
                            String reason, Instant timestamp) implements ObservationEvent {
        public TaskStateChanged(String taskId, String taskDescription,
                                com.jwcode.core.task.TaskStatus oldStatus,
                                com.jwcode.core.task.TaskStatus newStatus,
                                String reason) {
            this(taskId, taskDescription, oldStatus, newStatus, reason, Instant.now());
        }
    }

    /**
     * 任务计划更新 — 步骤进度变化
     */
    record TaskPlanUpdated(String taskId, int totalSteps, int completedSteps,
                           String currentStepDescription, Instant timestamp) implements ObservationEvent {
        public TaskPlanUpdated(String taskId, int totalSteps, int completedSteps,
                               String currentStepDescription) {
            this(taskId, totalSteps, completedSteps, currentStepDescription, Instant.now());
        }
    }

    /**
     * 等待用户输入 — 任务阻塞等待补充信息
     */
    record WaitingForInput(String taskId, String question,
                           Instant timestamp) implements ObservationEvent {
        public WaitingForInput(String taskId, String question) {
            this(taskId, question, Instant.now());
        }
    }

    /**
     * 上下文压缩 — 自动压缩发生时通知前端
     */
    record ContextCompressed(int originalCount, int compressedCount,
                             long estimatedTokensSaved, String summary,
                             Instant timestamp) implements ObservationEvent {
        public ContextCompressed(int originalCount, int compressedCount,
                                 long estimatedTokensSaved, String summary) {
            this(originalCount, compressedCount, estimatedTokensSaved, summary, Instant.now());
        }
    }

    /**
     * 步骤AI提示 — 进入新步骤时向AI注入上下文提示
     */
    record StepPrompt(String taskId, int stepIndex, String description,
                      String action, String stepPrompt, String agentType,
                      Instant timestamp) implements ObservationEvent {
        public StepPrompt(String taskId, int stepIndex, String description,
                         String action, String stepPrompt, String agentType) {
            this(taskId, stepIndex, description, action, stepPrompt, agentType, Instant.now());
        }
    }

    /**
     * Swarm subtask start - a sub-task in Agent Swarm has begun.
     */
    record SwarmTaskStarted(String agentId, String taskId, String description, String type, int priority, Instant timestamp) implements ObservationEvent {
        public SwarmTaskStarted(String agentId, String taskId, String description, String type, int priority) {
            this(agentId, taskId, description, type, priority, Instant.now());
        }
    }

    /**
     * Swarm subtask completed - a sub-task in Agent Swarm has finished.
     */
    record SwarmTaskCompleted(String agentId, String taskId, String description, boolean success, long durationMs, Instant timestamp) implements ObservationEvent {
        public SwarmTaskCompleted(String agentId, String taskId, String description, boolean success, long durationMs) {
            this(agentId, taskId, description, success, durationMs, Instant.now());
        }
    }

    /**
     * Swarm progress update - real-time execution progress report.
     */
    record SwarmProgress(int completedTasks, int totalTasks, Instant timestamp) implements ObservationEvent {
        public SwarmProgress(int completedTasks, int totalTasks) {
            this(completedTasks, totalTasks, Instant.now());
        }
    }
}

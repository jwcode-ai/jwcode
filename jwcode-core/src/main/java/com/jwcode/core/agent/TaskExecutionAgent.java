package com.jwcode.core.agent;

import com.jwcode.core.a2a.A2AFacade;
import com.jwcode.core.a2a.model.A2ATask;
import com.jwcode.core.a2a.model.TaskOutput;
import com.jwcode.core.api.PlanTaskBroadcaster;
import com.jwcode.core.model.StructuredTask;
import com.jwcode.core.model.StructuredTask.ExecutionMode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * TaskExecutionAgent — 任务执行Agent。
 *
 * <p>职责：按照结构化任务列表逐步执行任务。
 * <ul>
 *   <li>自动处理并发/串行执行模式</li>
 *   <li>管理任务依赖关系（拓扑排序）</li>
 *   <li>通过 A2AFacade 将子任务派发给对应的子Agent</li>
 *   <li>通过 PlanTaskBroadcaster 实时向前端推送任务状态</li>
 * </ul>
 * </p>
 *
 * <p>执行策略：
 * <ol>
 *   <li>解析 StructuredTask 列表，构建依赖图（DAG）</li>
 *   <li>按拓扑顺序执行：无依赖的任务先执行</li>
 *   <li>同一并发组内的任务使用线程池并行执行</li>
 *   <li>每个任务执行时通过 A2AFacade 派发给对应的子Agent</li>
 *   <li>实时广播任务状态到前端（plan_task_start / plan_task_update / plan_task_result）</li>
 * </ol>
 * </p>
 */
public class TaskExecutionAgent {

    private static final Logger logger = Logger.getLogger(TaskExecutionAgent.class.getName());

    // 线程池配置：IO 密集型（子Agent LLM调用），核心=CPU*2，最大=CPU*4，有界队列
    private static final int EXEC_CORE_POOL_SIZE = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
    private static final int EXEC_MAX_POOL_SIZE = Math.max(8, Runtime.getRuntime().availableProcessors() * 4);
    private static final int EXEC_QUEUE_CAPACITY = 50;
    private static final long EXEC_KEEPALIVE_SEC = 120;

    private final A2AFacade a2aFacade;
    private final PlanTaskBroadcaster broadcaster;
    private final String sessionId;

    // 线程池用于并发任务执行
    private final ExecutorService executorService;

    // 执行上下文
    private final Map<String, Object> sharedContext;

    // 工作目录记忆 Agent（任务完成后自动记忆）
    private MemoryAgent memoryAgent;

    public TaskExecutionAgent(A2AFacade a2aFacade,
                               PlanTaskBroadcaster broadcaster,
                               String sessionId) {
        this(a2aFacade, broadcaster, sessionId, null);
    }

    public TaskExecutionAgent(A2AFacade a2aFacade,
                               PlanTaskBroadcaster broadcaster,
                               String sessionId,
                               MemoryAgent memoryAgent) {
        this.a2aFacade = a2aFacade;
        this.broadcaster = broadcaster;
        this.sessionId = sessionId;
        this.memoryAgent = memoryAgent;
        // IO 密集型线程池：核心 CPU*2，最大 CPU*4，有界队列 + 降级策略
        this.executorService = new ThreadPoolExecutor(
            EXEC_CORE_POOL_SIZE,
            EXEC_MAX_POOL_SIZE,
            EXEC_KEEPALIVE_SEC, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(EXEC_QUEUE_CAPACITY),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "task-exec-" + counter.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            },
            (r, executor) -> {
                // 超载降级：记录告警，降级为同步串行执行当前批次
                logger.warning("[TaskExecutionAgent] 线程池已满 (core=" + EXEC_CORE_POOL_SIZE
                    + ", max=" + EXEC_MAX_POOL_SIZE + "), 降级为同步执行");
                r.run();
            }
        );
        this.sharedContext = new ConcurrentHashMap<>();
    }

    /**
     * 设置 MemoryAgent
     */
    public void setMemoryAgent(MemoryAgent memoryAgent) {
        this.memoryAgent = memoryAgent;
    }

    /**
     * 执行结构化的任务列表。
     *
     * @param tasks     结构化任务列表
     * @param goal      任务总体目标
     * @return 执行结果摘要
     */
    public ExecutionResult execute(List<StructuredTask> tasks, String goal) {
        ExecutionResult result = new ExecutionResult();
        result.startTime = LocalDateTime.now();

        if (tasks == null || tasks.isEmpty()) {
            result.summary = "No tasks to execute.";
            result.endTime = LocalDateTime.now();
            return result;
        }

        logger.info("[TaskExecutionAgent] Starting execution of " + countAllTasks(tasks)
            + " tasks for goal: " + goal);

        // 广播计划开始
        broadcastPlanStart(goal);

        try {
            // 按阶段顺序执行
            for (int phaseIdx = 0; phaseIdx < tasks.size(); phaseIdx++) {
                StructuredTask phaseTask = tasks.get(phaseIdx);

                // 广播阶段信息
                broadcastPhaseInfo(phaseTask, phaseIdx + 1);

                List<StructuredTask> phaseChildren = phaseTask.getChildren();
                if (phaseChildren == null || phaseChildren.isEmpty()) {
                    // 没有子任务的阶段，直接执行阶段任务本身
                    executeSingleTask(phaseTask, goal, result);
                } else {
                    // 按执行模式处理
                    ExecutionMode mode = phaseTask.getExecutionMode();
                    if (mode == ExecutionMode.CONCURRENT) {
                        executeConcurrently(phaseChildren, goal, result);
                    } else {
                        executeSequentially(phaseChildren, goal, result);
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("[TaskExecutionAgent] Execution failed: " + e.getMessage());
            result.errors.add(e.getMessage());
            broadcastPlanError(e.getMessage());
        } finally {
            result.endTime = LocalDateTime.now();
            result.duration = Duration.between(result.startTime, result.endTime);
            broadcastPlanComplete(result);
        }

        logger.info("[TaskExecutionAgent] Execution completed. "
            + "Success: " + result.successCount
            + ", Failed: " + result.failedCount
            + ", Duration: " + result.duration.toMillis() + "ms");

        return result;
    }

    /**
     * 串行执行任务列表（按依赖拓扑排序）
     */
    private void executeSequentially(List<StructuredTask> tasks, String goal, ExecutionResult result) {
        // 拓扑排序
        List<StructuredTask> sorted = topologicalSort(tasks);

        for (StructuredTask task : sorted) {
            if (task.getStatus().equals("failed")) continue;

            // 检查依赖是否满足
            if (!areDependenciesMet(task, sorted)) {
                task.setStatus("skipped");
                result.skippedCount++;
                broadcastTaskResult(task, "skipped", "Dependencies not met", null);
                continue;
            }

            executeSingleTask(task, goal, result);

            // 如果关键任务失败，停止后续任务
            if (task.getStatus().equals("failed")) {
                logger.warning("[TaskExecutionAgent] Task " + task.getId()
                    + " failed, stopping sequential execution.");
                break;
            }
        }
    }

    /**
     * 并发执行任务列表（同一并发组内并行执行）
     */
    private void executeConcurrently(List<StructuredTask> tasks, String goal, ExecutionResult result) {
        // 复制任务列表，避免修改原始列表
        List<StructuredTask> remaining = new ArrayList<>(tasks);

        // 使用拓扑排序思想：每次取无依赖的任务并行执行，完成后重新计算依赖状态
        while (!remaining.isEmpty()) {
            // 找出当前批次中依赖已满足的任务
            List<StructuredTask> ready = remaining.stream()
                .filter(t -> t.getDependencies().isEmpty()
                    || t.getDependencies().stream().allMatch(depId ->
                        tasks.stream().anyMatch(pt ->
                            pt.getId().equals(depId) && "completed".equals(pt.getStatus()))))
                .collect(Collectors.toList());

            if (ready.isEmpty()) {
                // 死锁检测：有剩余任务但无就绪任务，说明存在循环依赖
                logger.severe("[TaskExecutionAgent] 依赖死锁: 剩余 " + remaining.size()
                    + " 个任务因循环依赖无法执行: "
                    + remaining.stream().map(StructuredTask::getId).collect(Collectors.joining(", ")));
                for (StructuredTask stuck : remaining) {
                    stuck.setStatus("failed");
                    stuck.appendResult("依赖死锁: 依赖的任务未完成或存在循环依赖");
                    result.addFailure(stuck.getId(), "依赖死锁");
                }
                break;
            }

            // 从剩余列表中移除就绪任务
            remaining.removeAll(ready);

            // 并行执行就绪任务（orTimeout 防止单任务卡死整个批次）
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (StructuredTask task : ready) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    executeSingleTask(task, goal, result);
                }, executorService)
                .orTimeout(600, TimeUnit.SECONDS)  // 单任务 10 分钟超时
                .exceptionally(ex -> {
                    logger.warning("[TaskExecutionAgent] 并发任务超时或失败: " + task.getId()
                        + " - " + ex.getMessage());
                    task.setStatus("failed");
                    task.setError("Timeout or error: " + ex.getMessage());
                    result.addFailure(task.getId(), ex.getMessage());
                    return null;
                });
                futures.add(future);
            }

            // 等待当前批次所有并行任务完成（异常已被 exceptionally 吞掉，不会抛异常）
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    }

    /**
     * 执行单个任务
     */
    private void executeSingleTask(StructuredTask task, String goal, ExecutionResult result) {
        task.setStatus("running");
        task.setStartedAt(System.currentTimeMillis());

        // 广播任务开始
        broadcastTaskStart(task);

        try {
            // 构建 A2A 任务输入
            Map<String, Object> input = new HashMap<>();
            input.put("goal", goal);
            input.put("taskTitle", task.getTitle());
            input.put("taskDescription", task.getDescription());
            input.put("phase", task.getPhase().name());
            input.put("stepNumber", task.getStepNumber());

            // 注入任务专属上下文（TaskAgent 提取的文件、模块、约束等）
            if (task.getContext() != null && !task.getContext().isEmpty()) {
                input.put("context", new HashMap<>(task.getContext()));
                logger.fine("[TaskExecutionAgent] Context injected for task " + task.getId()
                    + ": " + task.getContext().keySet());
            }

            // 注入共享上下文（上游任务的结果）
            if (!sharedContext.isEmpty()) {
                input.put("sharedContext", new HashMap<>(sharedContext));
            }

            // 创建 A2ATask
            A2ATask a2aTask = A2ATask.create(
                getSkillIdForAgent(task.getAgentType()),
                task.getTitle(),
                input
            );

            logger.info("[TaskExecutionAgent] Dispatching task " + task.getId()
                + " to agent " + task.getAgentType());

            // 通过 A2AFacade 提交任务（同步等待）
            TaskOutput output;
            if (a2aFacade != null) {
                output = a2aFacade.submitTaskSync(task.getAgentType(), a2aTask);
            } else {
                // 降级：模拟输出
                output = TaskOutput.success("Task " + task.getId()
                    + " completed (mock - A2A facade unavailable)");
            }

            // 处理结果
            if (output.isSuccess()) {
                task.setStatus("completed");
                task.setResult(output.getSummary());
                task.setProgress(100);
                result.successCount++;

                // 将结果存入共享上下文
                sharedContext.put(task.getId() + "_result", output.getSummary());

                broadcastTaskResult(task, "completed", output.getSummary(), null);
            } else {
                task.setStatus("failed");
                task.setError(output.getSummary());
                task.setProgress(0);
                result.failedCount++;

                broadcastTaskResult(task, "failed", null, output.getSummary());
            }

            // 【新增】MemoryAgent 自动记忆任务完成
            if (memoryAgent != null && memoryAgent.isEnabled()) {
                try {
                    List<String> changedFiles = output.getFileChanges() != null
                        ? output.getFileChanges().stream()
                            .map(fc -> fc.getFilePath())
                            .collect(Collectors.toList())
                        : Collections.emptyList();
                    memoryAgent.recordTaskCompletion(
                        task.getTitle(),
                        task.getAgentType(),
                        task.getStatus(),
                        output.getSummary(),
                        changedFiles
                    );
                } catch (Exception memEx) {
                    logger.fine("[TaskExecutionAgent] MemoryAgent record skipped: " + memEx.getMessage());
                }
            }

        } catch (Exception e) {
            logger.severe("[TaskExecutionAgent] Task " + task.getId() + " failed: " + e.getMessage());
            task.setStatus("failed");
            task.setError(e.getMessage());
            task.setProgress(0);
            result.failedCount++;
            result.errors.add(task.getId() + ": " + e.getMessage());

            broadcastTaskResult(task, "failed", null, e.getMessage());
        }

        task.setCompletedAt(System.currentTimeMillis());

        // 广播进度更新
        broadcastTaskUpdate(task);
    }

    /**
     * 拓扑排序任务列表
     */
    private List<StructuredTask> topologicalSort(List<StructuredTask> tasks) {
        // 构建依赖图
        Map<String, StructuredTask> taskMap = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjList = new HashMap<>();

        for (StructuredTask t : tasks) {
            taskMap.put(t.getId(), t);
            inDegree.putIfAbsent(t.getId(), 0);
            adjList.putIfAbsent(t.getId(), new ArrayList<>());
        }

        for (StructuredTask t : tasks) {
            for (String depId : t.getDependencies()) {
                if (taskMap.containsKey(depId)) {
                    adjList.get(depId).add(t.getId());
                    inDegree.merge(t.getId(), 1, Integer::sum);
                }
            }
        }

        // Kahn 算法
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<StructuredTask> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            StructuredTask task = taskMap.get(id);
            if (task != null) {
                sorted.add(task);
            }
            for (String neighbor : adjList.getOrDefault(id, Collections.emptyList())) {
                int newDegree = inDegree.merge(neighbor, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        // 如果有循环依赖，将剩余任务追加到末尾
        if (sorted.size() < tasks.size()) {
            for (StructuredTask t : tasks) {
                if (!sorted.contains(t)) {
                    sorted.add(t);
                }
            }
        }

        return sorted;
    }

    /**
     * 检查依赖是否满足
     */
    private boolean areDependenciesMet(StructuredTask task, List<StructuredTask> allTasks) {
        if (task.getDependencies().isEmpty()) return true;

        for (String depId : task.getDependencies()) {
            StructuredTask dep = allTasks.stream()
                .filter(t -> t.getId().equals(depId))
                .findFirst()
                .orElse(null);

            if (dep == null || !dep.getStatus().equals("completed")) {
                return false;
            }
        }
        return true;
    }

    /**
     * 统计所有任务数（包括子任务）
     */
    private int countAllTasks(List<StructuredTask> tasks) {
        int count = 0;
        for (StructuredTask t : tasks) {
            count++;
            if (t.getChildren() != null) {
                count += countAllTasks(t.getChildren());
            }
        }
        return count;
    }

    // ==================== 广播辅助方法 ====================

    private void broadcastPlanStart(String goal) {
        if (broadcaster != null && sessionId != null) {
            broadcaster.broadcastPlanStart(sessionId, goal);
        }
    }

    private void broadcastPhaseInfo(StructuredTask phaseTask, int phaseNumber) {
        if (broadcaster != null && sessionId != null) {
            broadcaster.broadcastPlanThinking(sessionId,
                "📌 阶段 " + phaseNumber + ": " + phaseTask.getTitle()
                + " [" + phaseTask.getExecutionMode().name() + "]");
        }
    }

    private void broadcastTaskStart(StructuredTask task) {
        if (broadcaster != null && sessionId != null) {
            broadcaster.broadcastPlanTaskStart(sessionId, task.getId(), task.getAgentType());
        }
    }

    private void broadcastTaskUpdate(StructuredTask task) {
        if (broadcaster != null && sessionId != null) {
            broadcaster.broadcastPlanTaskUpdate(sessionId, task.getId(),
                task.getProgress() != null ? task.getProgress() : 0,
                task.getLogs() != null && !task.getLogs().isEmpty()
                    ? String.join("\n", task.getLogs()) : null);
        }
    }

    private void broadcastTaskResult(StructuredTask task, String status, String result, String error) {
        if (broadcaster != null && sessionId != null) {
            broadcaster.broadcastPlanTaskResult(sessionId, task.getId(), status, result, error);
        }
    }

    private void broadcastPlanComplete(ExecutionResult result) {
        if (broadcaster != null && sessionId != null) {
            String summary = String.format(
                "✅ 成功: %d, ❌ 失败: %d, ⏭️ 跳过: %d, 耗时: %dms",
                result.successCount, result.failedCount,
                result.skippedCount, result.duration.toMillis());
            broadcaster.broadcastPlanComplete(sessionId, summary);
        }
    }

    private void broadcastPlanError(String error) {
        if (broadcaster != null && sessionId != null) {
            broadcaster.broadcastPlanError(sessionId, error);
        }
    }

    /**
     * 根据 Agent 类型获取 Skill ID
     */
    private String getSkillIdForAgent(String agentType) {
        if (agentType == null) return "default";
        switch (agentType.toLowerCase()) {
            case "coder": return "code-generation";
            case "debug": return "debugging";
            case "reviewer": return "code-review";
            case "test": return "test-generation";
            case "doc": return "documentation";
            case "explore": return "code-exploration";
            case "architect": return "architecture-design";
            default: return "default";
        }
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 执行结果 ====================

    public static class ExecutionResult {
        public LocalDateTime startTime;
        public LocalDateTime endTime;
        public Duration duration;
        public int successCount;
        public int failedCount;
        public int skippedCount;
        public String summary;
        public final List<String> errors = new ArrayList<>();

        public void addFailure(String taskId, String reason) {
            this.failedCount++;
            this.errors.add(taskId + ": " + reason);
        }
    }
}

package com.jwcode.core.a2a.service;

import com.jwcode.core.a2a.model.A2ATask;
import com.jwcode.core.a2a.model.TaskOutput;
import com.jwcode.core.a2a.registry.A2ARegistry;
import com.jwcode.core.a2a.registry.AgentSession;
import com.jwcode.core.a2a.retry.RetryOrchestrator;
import com.jwcode.core.a2a.retry.RetryStrategy;
import com.jwcode.core.a2a.router.AgentRouter;
import com.jwcode.core.a2a.router.RoutingStrategy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * TaskService — 任务分发与状态管理服务。
 *
 * <p>核心职责：
 * <ul>
 *   <li>接收任务请求，按能力匹配空闲 Agent</li>
 *   <li>通过 WebSocket 推送任务到 Agent</li>
 *   <li>管理任务状态机：PENDING → ASSIGNED → RUNNING → COMPLETED/FAILED/TIMEOUT</li>
 *   <li>处理 Agent 回调结果，更新任务状态</li>
 *   <li>任务超时检测与重试机制</li>
 * </ul>
 * </p>
 */
public class TaskService {

    private static final Logger logger = Logger.getLogger(TaskService.class.getName());

    /** 单例 */
    private static volatile TaskService instance;

    /** 任务存储：taskId -> A2ATask */
    private final ConcurrentHashMap<String, A2ATask> tasks = new ConcurrentHashMap<>();

    /** 任务状态监听器 */
    private final List<Consumer<TaskEvent>> listeners = new CopyOnWriteArrayList<>();

    /** 任务分发回调（由 WebSocket 层注入，用于实际推送消息到 Agent） */
    private volatile TaskDispatcher taskDispatcher;

    /** A2A Registry */
    private final A2ARegistry registry = A2ARegistry.getInstance();

    /** Agent 路由器 */
    private final AgentRouter agentRouter = new AgentRouter();

    /** 重试编排器 */
    private final RetryOrchestrator retryOrchestrator = new RetryOrchestrator();

    /** 超时检测调度器 */
    private final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "task-timeout");
        t.setDaemon(true);
        return t;
    });

    /** 默认任务超时时间（秒） */
    private volatile long defaultTimeoutSeconds = 300;

    /** 超时检测间隔（秒） */
    private static final long TIMEOUT_CHECK_INTERVAL = 30;

    private TaskService() {
        startTimeoutChecker();
    }

    public static TaskService getInstance() {
        if (instance == null) {
            synchronized (TaskService.class) {
                if (instance == null) {
                    instance = new TaskService();
                }
            }
        }
        return instance;
    }

    /**
     * 重置单例（主要用于测试）
     */
    public static void resetInstance() {
        synchronized (TaskService.class) {
            if (instance != null) {
                instance.timeoutScheduler.shutdownNow();
                instance.tasks.clear();
                instance.listeners.clear();
            }
            instance = null;
        }
    }

    // ==================== 任务提交 ====================

    /**
     * 提交一个任务
     */
    public A2ATask submitTask(A2ATask task) {
        Objects.requireNonNull(task, "task must not be null");

        tasks.put(task.getTaskId(), task);
        logger.info("[TaskService] Task submitted: " + task.getTaskId()
            + " skill=" + task.getSkillId());

        notifyListeners(TaskEvent.Type.SUBMITTED, task);

        // 尝试分发任务
        dispatchTask(task.getTaskId());

        return task;
    }

    /**
     * 提交一个简单任务（便捷方法）
     */
    public A2ATask submitSimpleTask(String name, String skillId,
                                     String description, int priority) {
        A2ATask task = A2ATask.builder()
            .taskId(UUID.randomUUID().toString().substring(0, 8))
            .skillId(skillId)
            .description(description)
            .status(A2ATask.TaskStatus.PENDING)
            .priority(priority)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        return submitTask(task);
    }

    // ==================== 任务分发 ====================

    /**
     * 分发指定任务到匹配的 Agent
     */
    public boolean dispatchTask(String taskId) {
        A2ATask task = tasks.get(taskId);
        if (task == null) {
            logger.warning("[TaskService] Task not found: " + taskId);
            return false;
        }

        if (task.getStatus() != A2ATask.TaskStatus.PENDING) {
            logger.warning("[TaskService] Task " + taskId + " is not PENDING, current=" + task.getStatus());
            return false;
        }

        // 通过路由器选择 Agent
        Optional<AgentSession> selected = agentRouter.selectAgent(
            registry, task.getSkillId(), RoutingStrategy.LEAST_LOAD);

        if (selected.isEmpty()) {
            logger.warning("[TaskService] No available agent for task: " + taskId
                + " skill=" + task.getSkillId());
            updateTaskStatus(taskId, A2ATask.TaskStatus.FAILED, "No available agent");
            return false;
        }

        AgentSession agent = selected.get();

        // 更新任务状态为 ASSIGNED
        updateTaskStatus(taskId, A2ATask.TaskStatus.ASSIGNED,
            "Assigned to agent: " + agent.getAgentName());

        // 增加 Agent 负载
        agent.incrementLoad();

        // 通过 dispatcher 推送任务到 Agent
        if (taskDispatcher != null) {
            try {
                taskDispatcher.dispatch(agent, task);
                logger.info("[TaskService] Task " + taskId + " dispatched to agent: "
                    + agent.getAgentName());
                return true;
            } catch (Exception e) {
                logger.warning("[TaskService] Failed to dispatch task " + taskId
                    + " to agent " + agent.getAgentName() + ": " + e.getMessage());
                agent.decrementLoad();
                updateTaskStatus(taskId, A2ATask.TaskStatus.FAILED,
                    "Dispatch failed: " + e.getMessage());
                return false;
            }
        } else {
            logger.warning("[TaskService] No taskDispatcher configured, cannot dispatch task " + taskId);
            updateTaskStatus(taskId, A2ATask.TaskStatus.FAILED, "No task dispatcher configured");
            agent.decrementLoad();
            return false;
        }
    }

    // ==================== 任务状态管理 ====================

    /**
     * 更新任务状态
     */
    public void updateTaskStatus(String taskId, A2ATask.TaskStatus newStatus, String message) {
        A2ATask task = tasks.get(taskId);
        if (task == null) return;

        // 通过 Builder 创建新状态的任务副本
        A2ATask.Builder builder = A2ATask.builder()
            .taskId(task.getTaskId())
            .skillId(task.getSkillId())
            .description(task.getDescription())
            .input(task.getInput())
            .status(newStatus)
            .output(task.getOutput())
            .errorMessage(task.getErrorMessage())
            .createdAt(task.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .tags(task.getTags())
            .priority(task.getPriority());

        if (message != null) {
            builder.errorMessage(message);
        }

        A2ATask updated = builder.build();
        tasks.put(taskId, updated);

        logger.info("[TaskService] Task " + taskId + " status: " + newStatus);
        notifyListeners(TaskEvent.Type.STATUS_CHANGED, updated);
    }

    /**
     * 处理任务结果回调（由 Agent 通过 WebSocket 上报）
     */
    public void handleTaskResult(String taskId, String agentId,
                                  A2ATask.TaskStatus status, String result, String error) {
        A2ATask task = tasks.get(taskId);
        if (task == null) {
            logger.warning("[TaskService] Received result for unknown task: " + taskId);
            return;
        }

        logger.info("[TaskService] Task result received: " + taskId
            + " status=" + status + " from agent=" + agentId);

        A2ATask.TaskStatus newStatus;
        if (status == A2ATask.TaskStatus.COMPLETED) {
            newStatus = A2ATask.TaskStatus.COMPLETED;
        } else if (status == A2ATask.TaskStatus.FAILED) {
            // 检查是否需要重试
            if (shouldRetry(task)) {
                newStatus = A2ATask.TaskStatus.PENDING;
                logger.info("[TaskService] Retrying task: " + taskId);
            } else {
                newStatus = A2ATask.TaskStatus.FAILED;
            }
        } else {
            newStatus = status;
        }

        String content = result != null ? result : error;
        updateTaskStatus(taskId, newStatus, content);

        // 如果需要重试，重新分发
        if (newStatus == A2ATask.TaskStatus.PENDING) {
            dispatchTask(taskId);
        }
    }

    /**
     * 处理任务超时
     */
    public void handleTaskTimeout(String taskId) {
        A2ATask task = tasks.get(taskId);
        if (task == null) return;

        if (task.getStatus() == A2ATask.TaskStatus.RUNNING
            || task.getStatus() == A2ATask.TaskStatus.ASSIGNED) {
            logger.warning("[TaskService] Task timeout: " + taskId);
            updateTaskStatus(taskId, A2ATask.TaskStatus.TIMEOUT, "Task timed out");
        }
    }

    // ==================== 查询方法 ====================

    /**
     * 按 ID 查询任务
     */
    public Optional<A2ATask> getTask(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    /**
     * 查询所有任务
     */
    public List<A2ATask> getAllTasks() {
        return new ArrayList<>(tasks.values());
    }

    /**
     * 按状态查询任务
     */
    public List<A2ATask> getTasksByStatus(A2ATask.TaskStatus status) {
        return tasks.values().stream()
            .filter(t -> t.getStatus() == status)
            .collect(Collectors.toList());
    }

    /**
     * 获取任务统计
     */
    public TaskStats getStats() {
        long total = tasks.size();
        long pending = tasks.values().stream().filter(t -> t.getStatus() == A2ATask.TaskStatus.PENDING).count();
        long running = tasks.values().stream().filter(t -> t.getStatus() == A2ATask.TaskStatus.RUNNING).count();
        long completed = tasks.values().stream().filter(t -> t.getStatus() == A2ATask.TaskStatus.COMPLETED).count();
        long failed = tasks.values().stream().filter(t -> t.getStatus() == A2ATask.TaskStatus.FAILED).count();
        long timeout = tasks.values().stream().filter(t -> t.getStatus() == A2ATask.TaskStatus.TIMEOUT).count();
        return new TaskStats(total, pending, running, completed, failed, timeout);
    }

    // ==================== 配置 ====================

    public void setTaskDispatcher(TaskDispatcher dispatcher) {
        this.taskDispatcher = dispatcher;
    }

    public void setDefaultTimeoutSeconds(long timeoutSeconds) {
        this.defaultTimeoutSeconds = timeoutSeconds;
    }

    public void addListener(Consumer<TaskEvent> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<TaskEvent> listener) {
        listeners.remove(listener);
    }

    // ==================== 内部方法 ====================

    private boolean shouldRetry(A2ATask task) {
        // 简单重试逻辑：默认最多重试 3 次
        // 实际重试次数可通过 RetryOrchestrator 管理
        return false; // 暂不自动重试，由上层决定
    }

    private void notifyListeners(TaskEvent.Type type, A2ATask task) {
        TaskEvent event = new TaskEvent(type, task, Instant.now());
        for (Consumer<TaskEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                logger.log(Level.WARNING, "TaskListener threw exception", e);
            }
        }
    }

    private void startTimeoutChecker() {
        timeoutScheduler.scheduleAtFixedRate(() -> {
            try {
                Instant now = Instant.now();
                for (A2ATask task : tasks.values()) {
                    if (task.getStatus() == A2ATask.TaskStatus.RUNNING
                        || task.getStatus() == A2ATask.TaskStatus.ASSIGNED) {
                        LocalDateTime start = task.getCreatedAt();
                        long elapsed = Duration.between(start, now).getSeconds();
                        if (elapsed > defaultTimeoutSeconds) {
                            handleTaskTimeout(task.getTaskId());
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("[TaskService] Timeout check error: " + e.getMessage());
            }
        }, TIMEOUT_CHECK_INTERVAL, TIMEOUT_CHECK_INTERVAL, TimeUnit.SECONDS);
    }

    // ==================== 内部类 ====================

    /**
     * 任务事件
     */
    public record TaskEvent(Type type, A2ATask task, Instant timestamp) {
        public enum Type {
            SUBMITTED,
            STATUS_CHANGED,
            COMPLETED,
            FAILED
        }
    }

    /**
     * 任务统计
     */
    public record TaskStats(long total, long pending, long running,
                             long completed, long failed, long timeout) {
    }

    /**
     * 任务分发器接口（由 WebSocket 层实现）
     */
    @FunctionalInterface
    public interface TaskDispatcher {
        void dispatch(AgentSession agent, A2ATask task) throws Exception;
    }
}

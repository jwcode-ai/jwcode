package com.jwcode.core.coordinator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CoordinatorService - Coordinator 模式服务
 * 
 * 功能说明：
 * 任务协调器模式，负责任务分解、分配、进度跟踪和结果汇总。
 * Coordinator 将复杂任务分解为子任务，分配给不同的执行者，然后汇总结果。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class CoordinatorService {
    
    private final ExecutorService executor;
    private final Map<String, CoordinatedTask> tasks;
    private final BlockingQueue<SubTask> pendingTasks;
    private final AtomicInteger activeWorkers;
    private final AtomicBoolean running;
    private int maxWorkers;
    private CoordinationStrategy strategy;
    
    public CoordinatorService() {
        this.executor = Executors.newFixedThreadPool(16);
        this.tasks = new ConcurrentHashMap<>();
        this.pendingTasks = new LinkedBlockingQueue<>();
        this.activeWorkers = new AtomicInteger(0);
        this.running = new AtomicBoolean(false);
        this.maxWorkers = 8;
        this.strategy = CoordinationStrategy.BALANCED;
    }
    
    /**
     * 启动协调器服务
     */
    public void start() {
        running.set(true);
        startWorkerLoop();
    }
    
    /**
     * 启动工作线程循环
     */
    private void startWorkerLoop() {
        for (int i = 0; i < maxWorkers; i++) {
            executor.submit(this::workerLoop);
        }
    }
    
    /**
     * 工作线程循环
     */
    private void workerLoop() {
        while (running.get()) {
            try {
                SubTask task = pendingTasks.take();
                activeWorkers.incrementAndGet();
                
                try {
                    task.execute();
                } finally {
                    activeWorkers.decrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * 停止协调器服务
     */
    public void stop() {
        running.set(false);
        executor.shutdown();
    }
    
    /**
     * 设置协调策略
     */
    public void setStrategy(CoordinationStrategy strategy) {
        this.strategy = strategy;
    }
    
    /**
     * 设置最大工作线程数
     */
    public void setMaxWorkers(int maxWorkers) {
        this.maxWorkers = Math.max(1, maxWorkers);
    }
    
    /**
     * 创建协调任务
     */
    public CompletableFuture<CoordinatedTask> createTask(String description) {
        return CompletableFuture.supplyAsync(() -> {
            String taskId = "coord_" + System.currentTimeMillis();
            CoordinatedTask task = new CoordinatedTask(taskId, description);
            tasks.put(taskId, task);
            return task;
        }, executor);
    }
    
    /**
     * 分解任务为子任务
     */
    public CompletableFuture<CoordinatedTask> decomposeTask(String taskId, List<String> subTaskDescriptions) {
        return CompletableFuture.supplyAsync(() -> {
            CoordinatedTask task = tasks.get(taskId);
            if (task == null) {
                throw new RuntimeException("任务不存在：" + taskId);
            }
            
            for (String desc : subTaskDescriptions) {
                SubTask subTask = new SubTask(taskId, desc);
                task.addSubTask(subTask);
            }
            
            return task;
        }, executor);
    }
    
    /**
     * 执行协调任务
     */
    public CompletableFuture<CoordinationResult> executeTask(String taskId) {
        return CompletableFuture.supplyAsync(() -> {
            CoordinatedTask task = tasks.get(taskId);
            if (task == null) {
                return new CoordinationResult(false, "任务不存在：" + taskId);
            }
            
            task.setStatus(TaskStatus.RUNNING);
            
            // 根据策略执行任务
            switch (strategy) {
                case SEQUENTIAL:
                    return executeSequential(task);
                case PARALLEL:
                    return executeParallel(task);
                case BALANCED:
                    return executeBalanced(task);
                default:
                    return executeBalanced(task);
            }
        }, executor);
    }
    
    /**
     * 顺序执行
     */
    private CoordinationResult executeSequential(CoordinatedTask task) {
        List<String> results = new ArrayList<>();
        
        for (SubTask subTask : task.subTasks) {
            try {
                subTask.execute();
                results.add(subTask.result);
            } catch (Exception e) {
                task.setStatus(TaskStatus.FAILED);
                return new CoordinationResult(false, "子任务执行失败：" + e.getMessage());
            }
        }
        
        task.setStatus(TaskStatus.COMPLETED);
        return new CoordinationResult(true, results);
    }
    
    /**
     * 并行执行
     */
    private CoordinationResult executeParallel(CoordinatedTask task) {
        List<CompletableFuture<String>> futures = new ArrayList<>();
        
        for (SubTask subTask : task.subTasks) {
            pendingTasks.offer(subTask);
            futures.add(subTask.future);
        }
        
        // 等待所有子任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        List<String> results = new ArrayList<>();
        for (CompletableFuture<String> future : futures) {
            results.add(future.join());
        }
        
        task.setStatus(TaskStatus.COMPLETED);
        return new CoordinationResult(true, results);
    }
    
    /**
     * 平衡执行（混合顺序和并行）
     */
    private CoordinationResult executeBalanced(CoordinatedTask task) {
        // 将子任务分组，组内并行，组间顺序
        int groupSize = Math.max(1, maxWorkers / 2);
        List<List<SubTask>> groups = new ArrayList<>();
        
        List<SubTask> currentGroup = new ArrayList<>();
        for (SubTask subTask : task.subTasks) {
            currentGroup.add(subTask);
            if (currentGroup.size() >= groupSize) {
                groups.add(new ArrayList<>(currentGroup));
                currentGroup.clear();
            }
        }
        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
        }
        
        List<String> results = new ArrayList<>();
        
        // 顺序执行每组，组内并行
        for (List<SubTask> group : groups) {
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (SubTask subTask : group) {
                pendingTasks.offer(subTask);
                futures.add(subTask.future);
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            for (CompletableFuture<String> future : futures) {
                results.add(future.join());
            }
        }
        
        task.setStatus(TaskStatus.COMPLETED);
        return new CoordinationResult(true, results);
    }
    
    /**
     * 获取任务进度
     */
    public TaskProgress getTaskProgress(String taskId) {
        CoordinatedTask task = tasks.get(taskId);
        if (task == null) {
            return null;
        }
        
        int total = task.subTasks.size();
        int completed = 0;
        int failed = 0;
        
        for (SubTask subTask : task.subTasks) {
            if (subTask.completed) {
                if (subTask.success) {
                    completed++;
                } else {
                    failed++;
                }
            }
        }
        
        return new TaskProgress(total, completed, failed, activeWorkers.get());
    }
    
    /**
     * 获取任务状态
     */
    public CoordinatedTask getTaskStatus(String taskId) {
        return tasks.get(taskId);
    }
    
    /**
     * 取消任务
     */
    public boolean cancelTask(String taskId) {
        CoordinatedTask task = tasks.remove(taskId);
        if (task != null) {
            task.setStatus(TaskStatus.CANCELLED);
            for (SubTask subTask : task.subTasks) {
                subTask.cancel();
            }
            return true;
        }
        return false;
    }
    
    /**
     * 获取活动任务列表
     */
    public List<CoordinatedTask> getActiveTasks() {
        List<CoordinatedTask> active = new ArrayList<>();
        for (CoordinatedTask task : tasks.values()) {
            if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.PENDING) {
                active.add(task);
            }
        }
        return active;
    }
    
    /**
     * 获取工作线程状态
     */
    public WorkerStatus getWorkerStatus() {
        return new WorkerStatus(maxWorkers, activeWorkers.get(), pendingTasks.size());
    }
    
    /**
     * 协调任务类
     */
    public static class CoordinatedTask {
        public final String id;
        public final String description;
        public final List<SubTask> subTasks;
        public TaskStatus status;
        public long createdAt;
        public long completedAt;
        
        public CoordinatedTask(String id, String description) {
            this.id = id;
            this.description = description;
            this.subTasks = new ArrayList<>();
            this.status = TaskStatus.PENDING;
            this.createdAt = System.currentTimeMillis();
        }
        
        public void addSubTask(SubTask subTask) {
            this.subTasks.add(subTask);
        }
        
        public void setStatus(TaskStatus status) {
            this.status = status;
            if (status == TaskStatus.COMPLETED) {
                this.completedAt = System.currentTimeMillis();
            }
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("description", description);
            map.put("status", status.toString());
            map.put("subTaskCount", subTasks.size());
            return map;
        }
    }
    
    /**
     * 子任务类
     */
    public static class SubTask {
        public final String taskId;
        public final String description;
        public final CompletableFuture<String> future;
        public String result;
        public boolean completed;
        public boolean success;
        
        public SubTask(String taskId, String description) {
            this.taskId = taskId;
            this.description = description;
            this.future = new CompletableFuture<>();
            this.completed = false;
            this.success = false;
        }
        
        public void execute() {
            try {
                // 模拟子任务执行
                Thread.sleep(100);
                this.result = "完成：" + description;
                this.success = true;
                this.completed = true;
                future.complete(result);
            } catch (Exception e) {
                this.result = "失败：" + e.getMessage();
                this.success = false;
                this.completed = true;
                future.completeExceptionally(e);
            }
        }
        
        public void cancel() {
            if (!completed) {
                this.result = "已取消";
                this.completed = true;
                future.cancel(true);
            }
        }
    }
    
    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    /**
     * 协调策略枚举
     */
    public enum CoordinationStrategy {
        SEQUENTIAL,   // 顺序执行
        PARALLEL,     // 并行执行
        BALANCED      // 平衡执行
    }
    
    /**
     * 协调结果类
     */
    public static class CoordinationResult {
        public final boolean success;
        public final Object data;
        
        public CoordinationResult(boolean success, Object data) {
            this.success = success;
            this.data = data;
        }
    }
    
    /**
     * 任务进度类
     */
    public static class TaskProgress {
        public final int total;
        public final int completed;
        public final int failed;
        public final int activeWorkers;
        
        public TaskProgress(int total, int completed, int failed, int activeWorkers) {
            this.total = total;
            this.completed = completed;
            this.failed = failed;
            this.activeWorkers = activeWorkers;
        }
        
        public int getProgressPercent() {
            if (total == 0) return 0;
            return (completed * 100) / total;
        }
    }
    
    /**
     * 工作线程状态类
     */
    public static class WorkerStatus {
        public final int maxWorkers;
        public final int activeWorkers;
        public final int pendingTasks;
        
        public WorkerStatus(int maxWorkers, int activeWorkers, int pendingTasks) {
            this.maxWorkers = maxWorkers;
            this.activeWorkers = activeWorkers;
            this.pendingTasks = pendingTasks;
        }
        
        public int getIdleWorkers() {
            return maxWorkers - activeWorkers;
        }
    }
}
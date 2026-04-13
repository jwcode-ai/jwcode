package com.jwcode.core.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务调度器
 * 
 * 负责任务的执行调度、超时检测和并发控制。
 * 使用 ScheduledExecutorService 实现任务调度。
 * 
 * 功能：
 * - 任务提交和执行
 * - 任务超时检测
 * - 并发控制（最大并行任务数）
 * - 任务取消
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class TaskScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(TaskScheduler.class);
    
    /**
     * 默认最大并发任务数
     */
    private static final int DEFAULT_MAX_CONCURRENT = 10;
    
    /**
     * 默认任务超时时间（分钟）
     */
    private static final long DEFAULT_TIMEOUT_MINUTES = 30;
    
    /**
     * 调度器线程池
     */
    private final ScheduledExecutorService scheduler;
    
    /**
     * 任务执行线程池
     */
    private final ExecutorService executor;
    
    /**
     * 正在执行的任务 Future 映射
     */
    private final Map<String, Future<?>> runningFutures;
    
    /**
     * 任务超时映射
     */
    private final Map<String, ScheduledFuture<?>> timeoutFutures;
    
    /**
     * 活跃任务计数
     */
    private final AtomicInteger activeCount;
    
    /**
     * 最大并发任务数
     */
    private final int maxConcurrent;
    
    /**
     * 任务存储
     */
    private final TaskStore taskStore;
    
    /**
     * 单例实例
     */
    private static volatile TaskScheduler instance;
    private static final Object INSTANCE_LOCK = new Object();
    
    /**
     * 获取单例实例
     * 
     * @return TaskScheduler 实例
     */
    public static TaskScheduler getInstance() {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new TaskScheduler();
                }
            }
        }
        return instance;
    }
    
    /**
     * 默认构造函数
     */
    public TaskScheduler() {
        this(DEFAULT_MAX_CONCURRENT, TaskStore.getInstance());
    }
    
    /**
     * 指定参数的构造函数
     * 
     * @param maxConcurrent 最大并发数
     * @param taskStore 任务存储
     */
    public TaskScheduler(int maxConcurrent, TaskStore taskStore) {
        this.maxConcurrent = Math.max(1, maxConcurrent);
        this.taskStore = taskStore;
        
        // 创建调度线程池（用于超时检测）
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "task-scheduler-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        
        // 创建任务执行线程池
        this.executor = new ThreadPoolExecutor(
            this.maxConcurrent,
            this.maxConcurrent * 2,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "task-worker-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        this.runningFutures = new ConcurrentHashMap<>();
        this.timeoutFutures = new ConcurrentHashMap<>();
        this.activeCount = new AtomicInteger(0);
        
        logger.info("TaskScheduler initialized with maxConcurrent={}", this.maxConcurrent);
    }
    
    /**
     * 提交任务执行
     * 
     * @param task 任务对象
     * @param taskRunnable 任务执行逻辑
     * @return CompletableFuture 任务未来
     */
    public CompletableFuture<Task> submit(Task task, Runnable taskRunnable) {
        return submit(task, taskRunnable, DEFAULT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    }
    
    /**
     * 提交任务执行（带超时）
     * 
     * @param task 任务对象
     * @param taskRunnable 任务执行逻辑
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return CompletableFuture 任务未来
     */
    public CompletableFuture<Task> submit(Task task, Runnable taskRunnable, long timeout, TimeUnit unit) {
        if (task == null || taskRunnable == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Task and taskRunnable cannot be null")
            );
        }
        
        // 检查并发限制
        if (activeCount.get() >= maxConcurrent) {
            task.markFailed("任务队列已满，请稍后重试");
            taskStore.update(task);
            return CompletableFuture.failedFuture(
                new RejectedExecutionException("Max concurrent tasks reached: " + maxConcurrent)
            );
        }
        
        // 更新任务状态为运行中
        task.markRunning();
        taskStore.update(task);
        
        // 增加活跃计数
        activeCount.incrementAndGet();
        
        // 提交任务执行
        CompletableFuture<Task> future = CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Task started: {}", task.getId());
                taskRunnable.run();
                
                // 如果任务没有显式完成，自动标记为完成
                if (!task.getStatus().isFinished()) {
                    task.markCompleted();
                }
                
                logger.info("Task completed: {}", task.getId());
                return task;
                
            } catch (Exception e) {
                logger.error("Task failed: {}", task.getId(), e);
                task.markFailed(e.getMessage());
                throw new CompletionException(e);
                
            } finally {
                // 取消超时检测
                cancelTimeout(task.getId());
                // 减少活跃计数
                activeCount.decrementAndGet();
                // 从运行中移除
                runningFutures.remove(task.getId());
                // 更新存储
                taskStore.update(task);
            }
        }, executor);
        
        // 保存 future 以便可以取消
        runningFutures.put(task.getId(), future);
        
        // 设置超时检测
        if (timeout > 0) {
            ScheduledFuture<?> timeoutFuture = scheduler.schedule(() -> {
                if (!task.getStatus().isFinished()) {
                    logger.warn("Task timeout: {}", task.getId());
                    task.markFailed("任务执行超时（" + timeout + " " + unit + "）");
                    taskStore.update(task);
                    cancel(task.getId());
                }
            }, timeout, unit);
            
            timeoutFutures.put(task.getId(), timeoutFuture);
        }
        
        return future;
    }
    
    /**
     * 取消任务
     * 
     * @param taskId 任务ID
     * @return true 如果取消成功
     */
    public boolean cancel(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return false;
        }
        
        Task task = taskStore.get(taskId);
        if (task == null) {
            logger.warn("Cannot cancel unknown task: {}", taskId);
            return false;
        }
        
        // 取消超时检测
        cancelTimeout(taskId);
        
        // 取消任务执行
        Future<?> future = runningFutures.get(taskId);
        if (future != null) {
            boolean cancelled = future.cancel(true);
            runningFutures.remove(taskId);
            
            if (cancelled && !task.getStatus().isFinished()) {
                task.markStopped();
                taskStore.update(task);
                activeCount.decrementAndGet();
                logger.info("Task cancelled: {}", taskId);
            }
            
            return cancelled;
        }
        
        // 任务未在执行中，但状态需要更新
        if (!task.getStatus().isFinished()) {
            task.markStopped();
            taskStore.update(task);
        }
        
        return true;
    }
    
    /**
     * 取消超时检测
     * 
     * @param taskId 任务ID
     */
    private void cancelTimeout(String taskId) {
        ScheduledFuture<?> timeoutFuture = timeoutFutures.remove(taskId);
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
        }
    }
    
    /**
     * 获取活跃任务数
     * 
     * @return 活跃任务数
     */
    public int getActiveCount() {
        return activeCount.get();
    }
    
    /**
     * 获取正在运行的任务数
     * 
     * @return 运行中任务数
     */
    public int getRunningCount() {
        return (int) runningFutures.values().stream()
            .filter(f -> !f.isDone() && !f.isCancelled())
            .count();
    }
    
    /**
     * 获取最大并发数
     * 
     * @return 最大并发数
     */
    public int getMaxConcurrent() {
        return maxConcurrent;
    }
    
    /**
     * 检查任务是否正在执行
     * 
     * @param taskId 任务ID
     * @return true 如果正在执行
     */
    public boolean isRunning(String taskId) {
        Future<?> future = runningFutures.get(taskId);
        return future != null && !future.isDone() && !future.isCancelled();
    }
    
    /**
     * 关闭调度器
     */
    public void shutdown() {
        logger.info("Shutting down TaskScheduler...");
        
        // 取消所有任务
        for (String taskId : runningFutures.keySet()) {
            cancel(taskId);
        }
        
        // 取消所有超时检测
        for (ScheduledFuture<?> future : timeoutFutures.values()) {
            future.cancel(false);
        }
        
        // 关闭线程池
        scheduler.shutdown();
        executor.shutdown();
        
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("TaskScheduler shutdown complete");
    }
    
    /**
     * 等待所有任务完成
     * 
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return true 如果所有任务都已完成
     * @throws InterruptedException 如果等待被中断
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }
}

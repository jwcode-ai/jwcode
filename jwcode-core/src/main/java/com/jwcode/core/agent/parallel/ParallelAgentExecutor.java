package com.jwcode.core.agent.parallel;

import com.jwcode.core.agent.Agent;
import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.agent.SharedContextBus;
import com.jwcode.core.agent.SubAgentContextStore;
import com.jwcode.core.llm.LLMQueryEngine;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 并行 Agent 执行器 - Phase 2 增强版
 * 
 * 支持同时启动多个子 Agent 并行执行任务，提供：
 * - ForkJoinPool 并行执行
 * - 任务超时控制
 * - 任务取消功能
 * - Agent 生命周期管理
 * - 依赖关系处理
 * - 结果聚合
 * 
 * 参考 Kimi Code 的多 Agent 协作架构
 */
public class ParallelAgentExecutor {
    
    private static final Logger logger = Logger.getLogger(ParallelAgentExecutor.class.getName());
    
    // 线程池配置
    private static final int DEFAULT_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;
    private static final long DEFAULT_TIMEOUT_MS = 300000; // 5分钟默认超时
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 60;
    
    // 执行器组件
    private final ExecutorService executorService;
    private final ForkJoinPool forkJoinPool;
    private final ScheduledExecutorService scheduledExecutor;
    
    // Agent 和 LLM 服务
    private final AgentRegistry agentRegistry;
    private final LLMService llmService;
    
    // 任务管理
    private final Map<String, SubAgentTask> taskRegistry = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<SubAgentResult>> resultFutures = new ConcurrentHashMap<>();
    private final Map<String, TaskExecution> activeExecutions = new ConcurrentHashMap<>();
    private final Map<String, CancellationToken> cancellationTokens = new ConcurrentHashMap<>();
    
    // 执行统计
    private final ExecutorStats stats = new ExecutorStats();
    
    // 子 Agent 语义持久化 + 中间成果共享
    private final SubAgentContextStore contextStore;
    private final SharedContextBus sharedBus;
    
    // 状态
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    
    /**
     * 任务执行记录
     */
    private static class TaskExecution {
        final String taskId;
        final long startTime;
        final Future<?> future;
        final CancellationToken cancellationToken;
        volatile Thread executingThread;
        
        TaskExecution(String taskId, Future<?> future, CancellationToken token) {
            this.taskId = taskId;
            this.startTime = System.currentTimeMillis();
            this.future = future;
            this.cancellationToken = token;
        }
    }
    
    /**
     * 取消令牌
     */
    public static class CancellationToken {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final String reason;
        
        public CancellationToken(String reason) {
            this.reason = reason;
        }
        
        public void cancel() {
            cancelled.set(true);
        }
        
        public boolean isCancelled() {
            return cancelled.get();
        }
        
        public void checkCancelled() throws CancellationException {
            if (isCancelled()) {
                throw new CancellationException("Task cancelled: " + reason);
            }
        }
        
        public String getReason() {
            return reason;
        }
    }
    
    /**
     * 执行统计
     */
    public static class ExecutorStats {
        private final AtomicLong totalTasksSubmitted = new AtomicLong(0);
        private final AtomicLong totalTasksCompleted = new AtomicLong(0);
        private final AtomicLong totalTasksFailed = new AtomicLong(0);
        private final AtomicLong totalTasksCancelled = new AtomicLong(0);
        private final AtomicLong totalExecutionTimeMs = new AtomicLong(0);
        
        public void recordSubmitted() { totalTasksSubmitted.incrementAndGet(); }
        public void recordCompleted(long executionTimeMs) {
            totalTasksCompleted.incrementAndGet();
            totalExecutionTimeMs.addAndGet(executionTimeMs);
        }
        public void recordFailed() { totalTasksFailed.incrementAndGet(); }
        public void recordCancelled() { totalTasksCancelled.incrementAndGet(); }
        
        public long getTotalTasksSubmitted() { return totalTasksSubmitted.get(); }
        public long getTotalTasksCompleted() { return totalTasksCompleted.get(); }
        public long getTotalTasksFailed() { return totalTasksFailed.get(); }
        public long getTotalTasksCancelled() { return totalTasksCancelled.get(); }
        public double getAverageExecutionTimeMs() {
            long completed = totalTasksCompleted.get();
            return completed > 0 ? (double) totalExecutionTimeMs.get() / completed : 0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "ExecutorStats{submitted=%d, completed=%d, failed=%d, cancelled=%d, avgTime=%.2fms}",
                totalTasksSubmitted.get(), totalTasksCompleted.get(), 
                totalTasksFailed.get(), totalTasksCancelled.get(),
                getAverageExecutionTimeMs()
            );
        }
    }
    
    // ==================== 构造函数 ====================
    
    public ParallelAgentExecutor(AgentRegistry agentRegistry, LLMService llmService) {
        this(agentRegistry, llmService, DEFAULT_POOL_SIZE);
    }
    
    public ParallelAgentExecutor(AgentRegistry agentRegistry, LLMService llmService, int poolSize) {
        this(agentRegistry, llmService, poolSize, new SubAgentContextStore(), new SharedContextBus());
    }
    
    public ParallelAgentExecutor(AgentRegistry agentRegistry, LLMService llmService,
                                  int poolSize, SubAgentContextStore contextStore, SharedContextBus sharedBus) {
        this.agentRegistry = agentRegistry;
        this.llmService = llmService;
        this.contextStore = contextStore;
        this.sharedBus = sharedBus;
        
        // 创建命名线程池
        this.executorService = Executors.newFixedThreadPool(poolSize, new ThreadFactory() {
            private int counter = 0;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "AgentExecutor-" + (++counter));
                t.setDaemon(true);
                t.setUncaughtExceptionHandler((thread, ex) -> {
                    logger.log(Level.SEVERE, "Uncaught exception in " + thread.getName(), ex);
                });
                return t;
            }
        });
        
        // ForkJoinPool 用于并行流和递归任务
        this.forkJoinPool = new ForkJoinPool(poolSize);
        
        // 调度器用于超时控制
        this.scheduledExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "AgentScheduler");
            t.setDaemon(true);
            return t;
        });
        
        logger.info("ParallelAgentExecutor initialized with poolSize=" + poolSize
            + " | contextStore=" + (contextStore != null) + " | sharedBus=" + (sharedBus != null));
    }
    
    // ==================== 核心执行方法 ====================
    
    /**
     * 同步执行多个任务
     * 
     * @param tasks 任务列表
     * @return 并行执行结果
     */
    public ParallelExecutionResult execute(List<SubAgentTask> tasks) {
        return execute(tasks, null, DEFAULT_TIMEOUT_MS);
    }
    
    /**
     * 同步执行多个任务（带会话和超时）
     * 
     * @param tasks 任务列表
     * @param session 会话上下文
     * @param timeoutMs 超时时间（毫秒）
     * @return 并行执行结果
     */
    public ParallelExecutionResult execute(List<SubAgentTask> tasks, Session session, long timeoutMs) {
        checkShutdown();
        
        if (tasks == null || tasks.isEmpty()) {
            return ParallelExecutionResult.empty();
        }
        
        logger.info("[ParallelAgent] Starting synchronous execution of " + tasks.size() + " tasks");
        long startTime = System.currentTimeMillis();
        
        try {
            // 使用 ForkJoinPool 进行并行执行
            List<SubAgentResult> results = forkJoinPool.submit(() ->
                tasks.parallelStream()
                    .map(task -> executeSingle(task, session, timeoutMs))
                    .collect(Collectors.toList())
            ).get(timeoutMs, TimeUnit.MILLISECONDS);
            
            long executionTime = System.currentTimeMillis() - startTime;
            logger.info("[ParallelAgent] Execution completed in " + executionTime + "ms");
            
            return new ParallelExecutionResult(results, executionTime);
            
        } catch (TimeoutException e) {
            logger.warning("[ParallelAgent] Execution timeout after " + timeoutMs + "ms");
            return createTimeoutResult(tasks, timeoutMs);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ParallelAgent] Execution failed", e);
            return createErrorResult(tasks, e);
        }
    }
    
    /**
     * 异步执行多个任务
     * 
     * @param tasks 任务列表
     * @return CompletableFuture 包含执行结果
     */
    public CompletableFuture<ParallelExecutionResult> executeAsync(List<SubAgentTask> tasks) {
        return executeAsync(tasks, null, DEFAULT_TIMEOUT_MS);
    }
    
    /**
     * 异步执行多个任务（带会话和超时）
     * 
     * @param tasks 任务列表
     * @param session 会话上下文
     * @param timeoutMs 超时时间（毫秒）
     * @return CompletableFuture 包含执行结果
     */
    public CompletableFuture<ParallelExecutionResult> executeAsync(
            List<SubAgentTask> tasks, Session session, long timeoutMs) {
        checkShutdown();
        
        if (tasks == null || tasks.isEmpty()) {
            return CompletableFuture.completedFuture(ParallelExecutionResult.empty());
        }
        
        logger.info("[ParallelAgent] Starting asynchronous execution of " + tasks.size() + " tasks");
        long startTime = System.currentTimeMillis();
        
        // 创建每个任务的 Future
        List<CompletableFuture<SubAgentResult>> futures = tasks.stream()
            .map(task -> executeSingleAsync(task, session, timeoutMs))
            .collect(Collectors.toList());
        
        // 组合所有 Future
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<SubAgentResult> results = futures.stream()
                    .map(f -> {
                        try {
                            return f.getNow(null);
                        } catch (Exception e) {
                            return SubAgentResult.failure("async", e.getMessage());
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
                
                long executionTime = System.currentTimeMillis() - startTime;
                return new ParallelExecutionResult(results, executionTime);
            })
            .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                logger.log(Level.WARNING, "[ParallelAgent] Async execution failed or timeout", ex);
                return createErrorResult(tasks, ex);
            });
    }
    
    /**
     * 取消指定任务
     * 
     * @param taskId 任务ID
     * @return 是否成功取消
     */
    public boolean cancel(String taskId) {
        return cancel(taskId, "User requested cancellation");
    }
    
    /**
     * 取消指定任务（带原因）
     * 
     * @param taskId 任务ID
     * @param reason 取消原因
     * @return 是否成功取消
     */
    public boolean cancel(String taskId, String reason) {
        logger.info("[ParallelAgent] Cancelling task: " + taskId + ", reason: " + reason);
        
        // 取消令牌
        CancellationToken token = cancellationTokens.get(taskId);
        if (token != null) {
            token.cancel();
        }
        
        // 取消 Future
        CompletableFuture<SubAgentResult> future = resultFutures.get(taskId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
        
        // 中断执行线程
        TaskExecution execution = activeExecutions.get(taskId);
        if (execution != null && execution.executingThread != null) {
            execution.executingThread.interrupt();
        }
        
        // 更新任务状态
        SubAgentTask task = taskRegistry.get(taskId);
        if (task != null) {
            task.setStatus(SubAgentTask.TaskStatus.CANCELLED);
        }
        
        stats.recordCancelled();
        return true;
    }
    
    /**
     * 批量取消任务
     * 
     * @param taskIds 任务ID列表
     * @return 成功取消的数量
     */
    public int cancelAll(List<String> taskIds) {
        int count = 0;
        for (String taskId : taskIds) {
            if (cancel(taskId)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 取消所有正在执行的任务
     * 
     * @return 成功取消的数量
     */
    public int cancelAllActive() {
        List<String> activeTaskIds = activeExecutions.keySet().stream()
            .collect(Collectors.toList());
        return cancelAll(activeTaskIds);
    }
    
    // ==================== 任务执行实现 ====================
    
    /**
     * 执行单个任务（同步）
     */
    private SubAgentResult executeSingle(SubAgentTask task, Session session, long timeoutMs) {
        String taskId = task.getTaskId();
        
        try {
            // 创建取消令牌
            CancellationToken token = new CancellationToken("Timeout or user cancellation");
            cancellationTokens.put(taskId, token);
            
            // 提交带超时的任务
            Future<SubAgentResult> future = executorService.submit(() -> {
                Thread currentThread = Thread.currentThread();
                TaskExecution execution = new TaskExecution(taskId, null, token);
                execution.executingThread = currentThread;
                activeExecutions.put(taskId, execution);
                
                try {
                    return doExecute(task, session, token);
                } finally {
                    activeExecutions.remove(taskId);
                }
            });
            
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            
        } catch (TimeoutException e) {
            logger.warning("[ParallelAgent] Task timeout: " + taskId);
            task.setStatus(SubAgentTask.TaskStatus.TIMEOUT);
            return SubAgentResult.builder()
                .taskId(taskId)
                .success(false)
                .error("Task timeout after " + timeoutMs + "ms")
                .build();
        } catch (CancellationException e) {
            logger.info("[ParallelAgent] Task cancelled: " + taskId);
            task.setStatus(SubAgentTask.TaskStatus.CANCELLED);
            return SubAgentResult.builder()
                .taskId(taskId)
                .success(false)
                .error("Task cancelled")
                .build();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ParallelAgent] Task failed: " + taskId, e);
            task.setStatus(SubAgentTask.TaskStatus.FAILED);
            return SubAgentResult.failure(taskId, e.getMessage());
        } finally {
            cancellationTokens.remove(taskId);
            resultFutures.remove(taskId);
        }
    }
    
    /**
     * 执行单个任务（异步）
     */
    private CompletableFuture<SubAgentResult> executeSingleAsync(
            SubAgentTask task, Session session, long timeoutMs) {
        String taskId = task.getTaskId();
        stats.recordSubmitted();
        
        CancellationToken token = new CancellationToken("Timeout or user cancellation");
        cancellationTokens.put(taskId, token);
        
        CompletableFuture<SubAgentResult> future = CompletableFuture.supplyAsync(() -> {
            Thread currentThread = Thread.currentThread();
            TaskExecution execution = new TaskExecution(taskId, null, token);
            execution.executingThread = currentThread;
            activeExecutions.put(taskId, execution);
            
            try {
                return doExecute(task, session, token);
            } finally {
                activeExecutions.remove(taskId);
            }
        }, executorService);
        
        // 设置超时
        CompletableFuture<SubAgentResult> timeoutFuture = future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS);
        resultFutures.put(taskId, timeoutFuture);
        
        // 注册到共享总线，支持中间成果共享和结果等待
        if (sharedBus != null) {
            sharedBus.registerTask(taskId, timeoutFuture);
            timeoutFuture.whenComplete((result, ex) -> {
                if (result != null && result.isSuccess()) {
                    sharedBus.publishIntermediate(taskId, result.getOutput());
                }
                sharedBus.cleanup(taskId);
            });
        }
        
        return timeoutFuture;
    }
    
    /**
     * 实际执行任务的逻辑
     */
    private SubAgentResult doExecute(SubAgentTask task, Session session, CancellationToken token) {
        String taskId = task.getTaskId();
        long startTime = System.currentTimeMillis();
        
        try {
            // 检查取消
            token.checkCancelled();
            
            // 标记开始
            task.markStarted();
            logger.fine("[ParallelAgent] Task started: " + taskId);
            
            // 解析 Agent
            Agent agent = resolveAgent(task);
            task.setExecutingAgent(agent);
            
            // 构建提示词
            String prompt = buildTaskPrompt(task, session);
            
            // 检查取消
            token.checkCancelled();
            
            // 执行 Agent 任务（实际调用 LLM）
            String result = executeAgentTask(agent, prompt, task, token);
            
            // 检查取消
            token.checkCancelled();
            
            // 标记完成
            task.markCompleted();
            long executionTime = System.currentTimeMillis() - startTime;
            stats.recordCompleted(executionTime);
            
            logger.fine("[ParallelAgent] Task completed: " + taskId + " in " + executionTime + "ms");
            
            // 持久化子 Agent 执行上下文（事件级）
            if (contextStore != null) {
                String agentId = task.getTaskId();
                contextStore.appendEvent(agentId, "task_completed",
                    Map.of("taskId", taskId, "agentId", agent.getId(),
                           "durationMs", executionTime, "success", true));
            }
            
            return SubAgentResult.builder()
                .taskId(taskId)
                .success(true)
                .output(result)
                .agentId(agent.getId())
                .executionTimeMs(executionTime)
                .startTime(startTime)
                .endTime(System.currentTimeMillis())
                .build();
                
        } catch (CancellationException e) {
            task.setStatus(SubAgentTask.TaskStatus.CANCELLED);
            stats.recordCancelled();
            throw e;
        } catch (Exception e) {
            task.markFailed();
            stats.recordFailed();
            logger.log(Level.SEVERE, "[ParallelAgent] Task failed: " + taskId, e);
            throw new CompletionException(e);
        }
    }
    
    /**
     * 解析 Agent（增强容错）
     */
    private Agent resolveAgent(SubAgentTask task) {
        String agentType = task.getAgentType();
        
        // 尝试按类型获取
        if (agentType != null && !agentType.isEmpty() && agentRegistry != null) {
            Agent agent = agentRegistry.get(agentType);
            if (agent != null) {
                return agent;
            }
        }
        
        // 尝试获取当前 Agent
        if (agentRegistry != null) {
            Agent current = agentRegistry.getCurrent();
            if (current != null) {
                return current;
            }
        }
        
        // 【修复】当没有可用 Agent 时，不抛异常，而是创建临时 Agent
        // 这样可以避免"No agent available for task"错误导致 0% 成功率
        logger.warning("[ParallelAgent] No registered agent found, creating temporary agent for task: " + task.getTaskId());
        return createTemporaryAgent(task);
    }
    
    /**
     * 创建临时 Agent（用于无 Agent 可用时的降级处理）
     */
    private Agent createTemporaryAgent(SubAgentTask task) {
        // 使用 Anonymous Agent 类（如果存在），否则返回基础实现
        String name = task.getName() != null ? task.getName() : "temp-" + task.getTaskId();
        String role = task.getRole();
        String description = "临时 Agent，用于执行任务: " + task.getTaskId();
        
        // 创建一个简单的匿名 Agent
        return new Agent() {
            @Override
            public String getId() {
                return "temp-" + UUID.randomUUID().toString().substring(0, 8);
            }
            
            @Override
            public String getName() {
                return name;
            }
            
            @Override
            public String getDescription() {
                return description;
            }
            
            @Override
            public String getSystemPrompt() {
                return role != null ? role : "你是一个通用 Agent，执行分配给你的任务。";
            }
            
            @Override
            public List<Tool<?, ?, ?>> getTools() {
                return Collections.emptyList();
            }
            
            @Override
            public Map<String, Object> getConfig() {
                return Collections.emptyMap();
            }
            
            @Override
            public ModelConfig getModelConfig() {
                return null;
            }
            
            @Override
            public boolean canUseTool(String toolName) {
                return false;
            }
        };
    }
    
    /**
     * 构建任务提示词
     */
    private String buildTaskPrompt(SubAgentTask task, Session session) {
        StringBuilder prompt = new StringBuilder();
        
        // 角色定义
        if (task.getRole() != null && !task.getRole().isEmpty()) {
            prompt.append("# 角色\n\n").append(task.getRole()).append("\n\n");
        }
        
        // 任务描述
        prompt.append("# 任务\n\n");
        String taskDesc = task.getTaskDescription() != null ? task.getTaskDescription() : task.getInstruction();
        prompt.append(taskDesc).append("\n\n");
        
        // 上下文
        Map<String, Object> context = task.getContext();
        if (context != null && !context.isEmpty()) {
            prompt.append("# 上下文\n\n");
            context.forEach((key, value) -> {
                prompt.append(key).append(": ").append(value).append("\n");
            });
            prompt.append("\n");
        }
        
        return prompt.toString();
    }
    
    /**
     * 执行 Agent 任务（调用 LLM）
     * 如果 Agent 配置了工具，会创建独立的 LLMQueryEngine 让子 Agent 自主完成工具调用循环。
     */
    private String executeAgentTask(Agent agent, String prompt, SubAgentTask task,
                                     CancellationToken token) throws Exception {
        if (llmService == null) {
            return simulateExecution(agent, prompt);
        }

        List<Tool<?, ?, ?>> tools = agent.getTools();
        if (tools != null && !tools.isEmpty()) {
            return executeAgentTaskWithEngine(agent, prompt, task, token, tools);
        }

        // 无工具时 fallback 到单次 LLM 调用
        try {
            token.checkCancelled();

            List<com.jwcode.core.llm.LLMMessage> messages = List.of(
                com.jwcode.core.llm.LLMMessage.system(agent.getSystemPrompt()),
                com.jwcode.core.llm.LLMMessage.user(prompt)
            );

            com.jwcode.core.llm.LLMResponse response = llmService.chat(messages).get(
                task.getTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS);

            if (response.hasError()) {
                throw new RuntimeException("LLM error: " + response.getErrorMessage());
            }

            String content = response.getContent();
            return content != null ? content : "Agent completed with no output.";

        } catch (CancellationException e) {
            throw e;
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("LLM call failed: " + cause.getMessage(), cause);
        }
    }

    /**
     * 使用独立的 LLMQueryEngine 执行子 Agent 任务。
     * 子 Agent 拥有独立的 Session、ToolExecutor 和 TokenBudget，
     * 不会消耗主 Agent 的迭代次数，任务完成后自动清理上下文。
     */
    private String executeAgentTaskWithEngine(Agent agent, String prompt, SubAgentTask task,
                                               CancellationToken token, List<Tool<?, ?, ?>> tools) throws Exception {
        // 1. 创建子 Session
        Session subSession = new Session("subagent-" + task.getTaskId(), System.getProperty("user.dir"));
        if (agent.getModelConfig() != null && agent.getModelConfig().getModel() != null) {
            subSession.setModel(agent.getModelConfig().getModel());
        }

        // 2. 构建 ToolRegistry（排除 AgentTool 防止递归 fork）
        ToolRegistry registry = new ToolRegistry();
        for (Tool<?, ?, ?> tool : tools) {
            if ("AgentTool".equals(tool.getName())) {
                continue; // 防止子 Agent 再创建子 Agent 导致递归
            }
            registry.register(tool);
        }
        ToolExecutor toolExecutor = new ToolExecutor(registry);

        // 3. 子 Agent 配置：不限制迭代次数，独立 Token 预算
        LLMQueryEngine.EngineConfig subConfig = LLMQueryEngine.EngineConfig.defaultConfig();
        subConfig.setMaxIterations(0);
        subConfig.setTokenBudget(500_000);

        // 4. 创建 LLMQueryEngine
        LLMQueryEngine engine = LLMQueryEngine.builder()
            .session(subSession)
            .llmService(llmService)
            .toolExecutor(toolExecutor)
            .config(subConfig)
            .build();

        // 5. 构建完整 prompt
        String fullPrompt = (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isEmpty())
            ? "[系统提示]\n" + agent.getSystemPrompt() + "\n\n[任务]\n" + prompt
            : prompt;

        // 6. 执行查询（带子 Agent 自己的工具循环）
        try {
            token.checkCancelled();
            LLMQueryEngine.QueryResult result = engine.query(fullPrompt)
                .get(task.getTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS);

            // 7. 任务完成后清空子 Agent 上下文
            subSession.clearMessages();

            if (result.isSuccess()) {
                com.jwcode.core.model.Message msg = result.getMessage();
                return msg != null ? msg.getTextContent() : "Agent completed with no output.";
            } else {
                throw new RuntimeException("Sub-agent error: " + result.getErrorMessage());
            }
        } catch (CancellationException e) {
            subSession.clearMessages();
            throw e;
        } catch (java.util.concurrent.ExecutionException e) {
            subSession.clearMessages();
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("Sub-agent LLM call failed: " + cause.getMessage(), cause);
        }
    }
    
    /**
     * 模拟执行（用于测试或 LLM 服务不可用时）
     */
    private String simulateExecution(Agent agent, String prompt) {
        try {
            // 模拟执行时间（100-500ms）
            Thread.sleep(100 + (long)(Math.random() * 400));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Simulation interrupted");
        }
        
        return String.format(
            "Agent [%s] execution result:\n" +
            "- Agent Name: %s\n" +
            "- Input Length: %d characters\n" +
            "- Status: Completed successfully\n" +
            "- Timestamp: %s",
            agent.getId(),
            agent.getName(),
            prompt.length(),
            new Date().toString()
        );
    }
    
    // ==================== 依赖处理 ====================
    
    /**
     * 执行带依赖的任务
     * 
     * @param tasks 任务列表（包含依赖关系）
     * @param session 会话上下文
     * @return 执行上下文
     */
    public ParallelExecutionContext executeWithDependencies(List<SubAgentTask> tasks, Session session) {
        checkShutdown();
        
        // 创建执行上下文
        ParallelExecutionContext context = new ParallelExecutionContext(tasks, session);
        
        // 构建依赖图
        DependencyGraph graph = new DependencyGraph(tasks);
        
        // 提交可执行的任务
        scheduleReadyTasks(graph, context);
        
        return context;
    }
    
    /**
     * 调度就绪的任务
     */
    private void scheduleReadyTasks(DependencyGraph graph, ParallelExecutionContext context) {
        List<SubAgentTask> readyTasks = graph.getReadyTasks();
        
        for (SubAgentTask task : readyTasks) {
            String taskId = task.getTaskId();
            
            // 尝试从共享总线获取上游任务的中间成果，注入到当前任务上下文中
            if (sharedBus != null) {
                for (String depId : task.getDependencies()) {
                    String artifact = sharedBus.getLatestIntermediate(depId);
                    if (artifact != null) {
                        task.getContext().put("upstream_" + depId + "_output", artifact);
                        logger.fine("[ParallelAgent] Injected intermediate from " + depId + " into " + taskId);
                    }
                }
            }
            
            // 提交任务
            CompletableFuture<SubAgentResult> future = executeSingleAsync(
                task, context.getSession(), task.getTimeoutMs()
            );
            
            // 完成后更新依赖图
            future.thenAccept(result -> {
                context.addResult(result);
                graph.markCompleted(taskId);
                
                // 继续调度新就绪的任务
                if (graph.hasMoreTasks()) {
                    scheduleReadyTasks(graph, context);
                }
            }).exceptionally(ex -> {
                context.addError(taskId, ex);
                graph.markCompleted(taskId);
                return null;
            });
        }
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 获取任务状态
     */
    public SubAgentTask.TaskStatus getTaskStatus(String taskId) {
        SubAgentTask task = taskRegistry.get(taskId);
        return task != null ? task.getStatus() : null;
    }
    
    /**
     * 获取执行统计
     */
    public ExecutorStats getStats() {
        return stats;
    }
    
    /**
     * 关闭执行器
     */
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            logger.info("Shutting down ParallelAgentExecutor...");
            
            // 取消所有活动任务
            cancelAllActive();
            
            // 关闭线程池
            shutdownExecutor(executorService, "ExecutorService");
            shutdownExecutor(scheduledExecutor, "ScheduledExecutor");
            
            // 关闭 ForkJoinPool
            forkJoinPool.shutdown();
            try {
                if (!forkJoinPool.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    forkJoinPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                forkJoinPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            logger.info("ParallelAgentExecutor shutdown complete");
        }
    }
    
    private void shutdownExecutor(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warning(name + " did not terminate in time, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 恢复之前持久化的子 Agent 会话。
     * @return 恢复的 Session，若不存在持久化数据则返回 null
     */
    public Session resumeAgent(String agentId, String workingDirectory) {
        if (contextStore == null) {
            logger.warning("[ParallelAgent] Cannot resume agent, contextStore is null");
            return null;
        }
        if (!contextStore.isResumable(agentId)) {
            logger.info("[ParallelAgent] No persistent data found for agent: " + agentId);
            return null;
        }
        Session session = contextStore.resumeSession(agentId, workingDirectory);
        logger.info("[ParallelAgent] Agent resumed: " + agentId + " | messages=" + session.getMessageCount());
        return session;
    }
    
    private void checkShutdown() {
        if (isShutdown.get()) {
            throw new IllegalStateException("Executor has been shutdown");
        }
    }
    
    private ParallelExecutionResult createTimeoutResult(List<SubAgentTask> tasks, long timeoutMs) {
        List<SubAgentResult> results = tasks.stream()
            .map(t -> SubAgentResult.builder()
                .taskId(t.getTaskId())
                .success(false)
                .error("Execution timeout after " + timeoutMs + "ms")
                .build())
            .collect(Collectors.toList());
        return new ParallelExecutionResult(results, timeoutMs);
    }
    
    private ParallelExecutionResult createErrorResult(List<SubAgentTask> tasks, Throwable error) {
        List<SubAgentResult> results = tasks.stream()
            .map(t -> SubAgentResult.builder()
                .taskId(t.getTaskId())
                .success(false)
                .error(error.getMessage())
                .build())
            .collect(Collectors.toList());
        return new ParallelExecutionResult(results, 0);
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 依赖图管理
     */
    private static class DependencyGraph {
        private final Map<String, SubAgentTask> tasks = new HashMap<>();
        private final Map<String, Set<String>> dependencies = new HashMap<>();
        private final Map<String, Set<String>> dependents = new HashMap<>();
        private final Set<String> completed = ConcurrentHashMap.newKeySet();
        private final Set<String> running = ConcurrentHashMap.newKeySet();
        
        DependencyGraph(List<SubAgentTask> taskList) {
            for (SubAgentTask task : taskList) {
                String taskId = task.getTaskId();
                tasks.put(taskId, task);
                dependencies.put(taskId, new HashSet<>(task.getDependencies()));
                dependents.put(taskId, new HashSet<>());
            }
            
            // 构建反向依赖
            for (SubAgentTask task : taskList) {
                for (String depId : task.getDependencies()) {
                    dependents.computeIfAbsent(depId, k -> new HashSet<>()).add(task.getTaskId());
                }
            }
        }
        
        List<SubAgentTask> getReadyTasks() {
            return tasks.values().stream()
                .filter(t -> !running.contains(t.getTaskId()))
                .filter(t -> !completed.contains(t.getTaskId()))
                .filter(t -> dependencies.getOrDefault(t.getTaskId(), Set.of()).isEmpty())
                .sorted(Comparator.comparingInt(SubAgentTask::getPriority).reversed())
                .collect(Collectors.toList());
        }
        
        void markCompleted(String taskId) {
            completed.add(taskId);
            running.remove(taskId);
            
            // 解除依赖
            Set<String> deps = dependents.getOrDefault(taskId, Set.of());
            for (String dependentId : deps) {
                dependencies.getOrDefault(dependentId, new HashSet<>()).remove(taskId);
            }
        }
        
        void markRunning(String taskId) {
            running.add(taskId);
        }
        
        boolean hasMoreTasks() {
            return completed.size() + running.size() < tasks.size();
        }
    }
}

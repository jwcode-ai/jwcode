package com.jwcode.core.agent.parallel;

import com.jwcode.core.agent.Agent;
import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.session.Session;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.logging.Logger;

/**
 * 并行 Agent 执行器
 * 
 * 支持同时启动多个子 Agent 并行执行任务，自动处理依赖关系
 * 参考 Kimi Code 的多 Agent 协作架构
 */
public class ParallelAgentExecutor {
    
    private static final Logger log = Logger.getLogger(ParallelAgentExecutor.class.getName());
    
    private final AgentRegistry agentRegistry;
    private final ExecutorService executorService;
    private final Map<String, SubAgentTask> taskRegistry = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<SubAgentResult>> resultFutures = new ConcurrentHashMap<>();
    
    /**
     * 默认线程池大小（可配置）
     */
    private static final int DEFAULT_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;
    
    public ParallelAgentExecutor(AgentRegistry agentRegistry) {
        this(agentRegistry, DEFAULT_POOL_SIZE);
    }
    
    public ParallelAgentExecutor(AgentRegistry agentRegistry, int poolSize) {
        this.agentRegistry = agentRegistry;
        this.executorService = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "SubAgent-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * 提交单个任务
     */
    public CompletableFuture<SubAgentResult> submit(SubAgentTask task, Session session) {
        taskRegistry.put(task.getTaskId(), task);
        
        CompletableFuture<SubAgentResult> future = CompletableFuture.supplyAsync(() -> {
            return executeTask(task, session);
        }, executorService);
        
        resultFutures.put(task.getTaskId(), future);
        task.setFuture(future);
        
        return future;
    }
    
    /**
     * 批量提交任务（自动处理依赖关系）
     */
    public ParallelExecutionContext submitBatch(List<SubAgentTask> tasks, Session session) {
        // 注册所有任务
        for (SubAgentTask task : tasks) {
            taskRegistry.put(task.getTaskId(), task);
        }
        
        // 创建执行上下文
        ParallelExecutionContext context = new ParallelExecutionContext(tasks, session);
        
        // 启动依赖调度器
        scheduleWithDependenciesNew(tasks, context);
        
        return context;
    }
    
    /**
     * 执行单个任务
     */
    private SubAgentResult executeTask(SubAgentTask task, Session session) {
        task.markStarted();
        log.info("[ParallelAgent] 开始执行任务: " + task.getTaskId());
        
        try {
            // 获取 Agent
            Agent agent = resolveAgent(task);
            task.setExecutingAgent(agent);
            
            // 构建任务提示词
            String prompt = buildTaskPrompt(task, session);
            
            // 执行任务（这里简化实现，实际应调用 QueryEngine）
            // TODO: 集成实际的 QueryEngine 调用
            String result = simulateAgentExecution(agent, prompt);
            
            task.markCompleted();
            log.info("[ParallelAgent] 任务完成: " + task.getTaskId() + " (" + task.getExecutionTimeMs() + "ms)");
            
            return SubAgentResult.builder()
                .taskId(task.getTaskId())
                .success(true)
                .output(result)
                .agentId(agent.getId())
                .executionTimeMs(task.getExecutionTimeMs())
                .build();
                
        } catch (Exception e) {
            task.markFailed();
            log.severe("[ParallelAgent] 任务失败: " + task.getTaskId() + " - " + e.getMessage());
            
            return SubAgentResult.failure(task.getTaskId(), e.getMessage());
        }
    }
    
    /**
     * 带依赖关系的任务调度（新实现）
     */
    private void scheduleWithDependenciesNew(List<SubAgentTask> tasks, ParallelExecutionContext context) {
        Map<String, SubAgentTask> taskMap = tasks.stream()
            .collect(Collectors.toMap(SubAgentTask::getTaskId, t -> t));
        
        Map<String, CompletableFuture<SubAgentResult>> futures = new ConcurrentHashMap<>();
        Set<String> submitted = ConcurrentHashMap.newKeySet();
        
        // 提交所有无依赖任务
        for (SubAgentTask task : tasks) {
            if (task.getDependencies().isEmpty()) {
                submitTaskWithDeps(task, taskMap, futures, submitted, context);
            }
        }
        
        // 提交有依赖的任务
        for (SubAgentTask task : tasks) {
            if (!task.getDependencies().isEmpty()) {
                submitTaskWithDeps(task, taskMap, futures, submitted, context);
            }
        }
    }
    
    private void submitTaskWithDeps(SubAgentTask task, Map<String, SubAgentTask> taskMap,
                                     Map<String, CompletableFuture<SubAgentResult>> futures,
                                     Set<String> submitted, ParallelExecutionContext context) {
        if (submitted.contains(task.getTaskId())) {
            return;
        }
        submitted.add(task.getTaskId());
        
        if (task.getDependencies().isEmpty()) {
            // 无依赖，直接提交
            CompletableFuture<SubAgentResult> future = submit(task, context.getSession())
                .thenApply(result -> {
                    context.addResult(result);
                    return result;
                });
            futures.put(task.getTaskId(), future);
        } else {
            // 有依赖，先提交所有依赖
            List<CompletableFuture<SubAgentResult>> depFutures = new ArrayList<>();
            for (String depId : task.getDependencies()) {
                SubAgentTask depTask = taskMap.get(depId);
                if (depTask != null) {
                    submitTaskWithDeps(depTask, taskMap, futures, submitted, context);
                    CompletableFuture<SubAgentResult> depFuture = futures.get(depId);
                    if (depFuture != null) {
                        depFutures.add(depFuture);
                    }
                }
            }
            
            // 等待依赖完成后提交
            CompletableFuture<SubAgentResult> future = CompletableFuture.allOf(
                depFutures.toArray(new CompletableFuture[0])
            ).thenCompose(v -> submit(task, context.getSession()))
             .thenApply(result -> {
                 context.addResult(result);
                 return result;
             });
            
            futures.put(task.getTaskId(), future);
        }
    }
    
    /**
     * 解析 Agent
     */
    private Agent resolveAgent(SubAgentTask task) {
        String agentType = task.getAgentType();
        
        if (agentType != null && !agentType.isEmpty()) {
            Agent agent = agentRegistry.get(agentType);
            if (agent != null) {
                return agent;
            }
        }
        
        // 使用默认 Agent
        return agentRegistry.getCurrent();
    }
    
    /**
     * 构建任务提示词
     */
    private String buildTaskPrompt(SubAgentTask task, Session session) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("# 任务\n\n");
        prompt.append(task.getInstruction()).append("\n\n");
        
        if (!task.getContext().isEmpty()) {
            prompt.append("# 上下文\n\n");
            task.getContext().forEach((key, value) -> {
                prompt.append(key).append(": ").append(value).append("\n");
            });
        }
        
        return prompt.toString();
    }
    
    /**
     * 模拟 Agent 执行（实际应替换为真实实现）
     */
    private String simulateAgentExecution(Agent agent, String prompt) {
        // 模拟执行时间
        try {
            Thread.sleep(100 + (long)(Math.random() * 200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return "Agent [" + agent.getId() + "] 执行结果:\n" + 
               "处理了任务，输入长度: " + prompt.length() + " 字符";
    }
    
    /**
     * 关闭执行器
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 依赖图
     */
    private static class DependencyGraph {
        private final Map<String, SubAgentTask> tasks = new HashMap<>();
        private final Map<String, Set<String>> dependencies = new HashMap<>();
        private final Map<String, Set<String>> dependents = new HashMap<>();
        
        DependencyGraph(List<SubAgentTask> taskList) {
            for (SubAgentTask task : taskList) {
                tasks.put(task.getTaskId(), task);
                dependencies.put(task.getTaskId(), new HashSet<>(task.getDependencies()));
                dependents.put(task.getTaskId(), new HashSet<>());
            }
            
            // 构建反向依赖关系
            for (SubAgentTask task : taskList) {
                for (String depId : task.getDependencies()) {
                    dependents.computeIfAbsent(depId, k -> new HashSet<>()).add(task.getTaskId());
                }
            }
        }
        
        /**
         * 获取可以立即执行的任务（无依赖或依赖已完成）
         */
        List<SubAgentTask> getReadyTasks() {
            return tasks.values().stream()
                .filter(t -> t.getStatus() == SubAgentTask.TaskStatus.PENDING)
                .filter(t -> dependencies.getOrDefault(t.getTaskId(), Set.of()).isEmpty())
                .sorted(Comparator.comparingInt(SubAgentTask::getPriority).reversed())
                .collect(Collectors.toList());
        }
    }
}

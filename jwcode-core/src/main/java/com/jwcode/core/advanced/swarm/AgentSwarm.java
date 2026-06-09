package com.jwcode.core.advanced.swarm;

import com.jwcode.core.advanced.swarm.ai.AITaskDecomposer;
import com.jwcode.core.advanced.swarm.ai.AITaskDecomposer.DecompositionResult;
import com.jwcode.core.advanced.swarm.ai.AITaskDecomposer.SubTaskDef;
import com.jwcode.core.llm.LLMMessage;
import com.jwcode.core.llm.LLMResponse;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.observability.ObservationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Consumer;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Agent Swarm - 智能体集群
 */
public class AgentSwarm {

    private static final Logger log = LoggerFactory.getLogger(AgentSwarm.class);

    private static final int MAX_SWARM_SIZE = 100;
    private static final int DEFAULT_MAX_PARALLEL = 16;

    private final ExecutorService executor;
    private final SwarmConfig config;
    private final AtomicInteger agentCounter = new AtomicInteger(0);
    private final Map<String, SwarmAgent> swarmAgents = new ConcurrentHashMap<>();
    private final List<SubTask> taskHistory = new CopyOnWriteArrayList<>();
    private Consumer<ObservationEvent> eventConsumer;
    private final LLMService llmService;
    private final AITaskDecomposer aiDecomposer;

    public AgentSwarm() {
        this(null);
    }

    public AgentSwarm(LLMService llmService) {
        this.config = SwarmConfig.defaultConfig();
        this.llmService = llmService;
        this.aiDecomposer = llmService != null ? new AITaskDecomposer(llmService) : null;
        this.executor = Executors.newFixedThreadPool(DEFAULT_MAX_PARALLEL, r -> {
            Thread t = new Thread(r, "SwarmAgent-" + agentCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    public SwarmExecutionResult executeComplexTask(String taskDescription, Object context) {
        return executeComplexTask(taskDescription, context, null);
    }

    public SwarmExecutionResult executeComplexTask(String taskDescription, Object context, Consumer<ObservationEvent> eventConsumer) {
        this.eventConsumer = eventConsumer;
        log.info("[AgentSwarm] 开始执行复杂任务: " + taskDescription.substring(0, Math.min(50, taskDescription.length())) + "...");
        long startTime = System.currentTimeMillis();

        List<SubTask> subTasks = decomposeTask(taskDescription);
        log.info("[AgentSwarm] 任务分解为 " + subTasks.size() + " 个子任务");

        List<SwarmAgent> agents = createSwarmForTasks(subTasks);
        log.info("[AgentSwarm] 创建 " + agents.size() + " 个 Swarm Agent");

        List<SubTaskResult> results = executeInParallel(subTasks, agents, context);

        Object finalResult = aggregateResults(results, taskDescription);

        long duration = System.currentTimeMillis() - startTime;

        SwarmExecutionResult executionResult = SwarmExecutionResult.builder()
            .taskDescription(taskDescription)
            .subTaskCount(subTasks.size())
            .agentCount(agents.size())
            .durationMs(duration)
            .results(results)
            .finalResult(finalResult)
            .speedup(calculateSpeedup(subTasks.size(), duration))
            .build();

        log.info("[AgentSwarm] 任务完成，耗时: " + duration + "ms");

        taskHistory.addAll(subTasks);
        return executionResult;
    }

    private List<SubTask> decomposeTask(String taskDescription) {
        // Try AI-based decomposition first
        if (aiDecomposer != null) {
            try {
                DecompositionResult aiResult = aiDecomposer.decomposeWithAI(taskDescription)
                    .get(30, TimeUnit.SECONDS);
                if (aiResult.getSubTasks() != null && !aiResult.getSubTasks().isEmpty()) {
                    return convertFromAIResult(aiResult);
                }
            } catch (Exception e) {
                log.warn("[AgentSwarm] AI 分解失败，回退到规则分解: " + e.getMessage());
            }
        }
        return decomposeByRules(taskDescription);
    }

    private List<SubTask> convertFromAIResult(DecompositionResult aiResult) {
        List<SubTask> subTasks = new ArrayList<>();
        for (SubTaskDef def : aiResult.getSubTasks()) {
            TaskType type = mapType(def.getType());
            Set<String> deps = def.getDependencies() != null
                ? new HashSet<>(def.getDependencies()) : new HashSet<>();
            subTasks.add(SubTask.builder()
                .id(def.getId())
                .description(def.getDescription())
                .type(type)
                .priority(def.getPriority())
                .dependencies(deps)
                .build());
        }
        return subTasks;
    }

    private TaskType mapType(String type) {
        if (type == null) return TaskType.EXECUTION;
        return switch (type.toLowerCase()) {
            case "explore", "analysis" -> TaskType.ANALYSIS;
            case "code", "execution" -> TaskType.EXECUTION;
            case "test", "review", "verification" -> TaskType.VERIFICATION;
            case "doc" -> TaskType.PLANNING;
            default -> TaskType.EXECUTION;
        };
    }

    private List<SubTask> decomposeByRules(String taskDescription) {
        List<SubTask> subTasks = new ArrayList<>();
        String lowerTask = taskDescription.toLowerCase();

        if (lowerTask.contains("refactor") || lowerTask.contains("重构")) {
            subTasks.add(SubTask.builder().id("analyze-code").description("分析现有代码结构").type(TaskType.ANALYSIS).priority(10).build());
            subTasks.add(SubTask.builder().id("identify-issues").description("识别代码问题").type(TaskType.ANALYSIS).dependencies(Set.of("analyze-code")).priority(9).build());
            subTasks.add(SubTask.builder().id("plan-refactor").description("制定重构计划").type(TaskType.PLANNING).dependencies(Set.of("identify-issues")).priority(8).build());
            subTasks.add(SubTask.builder().id("execute-refactor").description("执行重构").type(TaskType.EXECUTION).dependencies(Set.of("plan-refactor")).priority(7).build());
            subTasks.add(SubTask.builder().id("verify-refactor").description("验证重构结果").type(TaskType.VERIFICATION).dependencies(Set.of("execute-refactor")).priority(6).build());
        } else if (lowerTask.contains("feature") || lowerTask.contains("功能")) {
            subTasks.add(SubTask.builder().id("analyze-requirements").description("分析需求").type(TaskType.ANALYSIS).priority(10).build());
            subTasks.add(SubTask.builder().id("design-architecture").description("设计架构").type(TaskType.PLANNING).dependencies(Set.of("analyze-requirements")).priority(9).build());
            subTasks.add(SubTask.builder().id("implement-core").description("实现核心功能").type(TaskType.EXECUTION).dependencies(Set.of("design-architecture")).priority(8).build());
            subTasks.add(SubTask.builder().id("implement-ui").description("实现界面").type(TaskType.EXECUTION).dependencies(Set.of("design-architecture")).priority(8).build());
            subTasks.add(SubTask.builder().id("write-tests").description("编写测试").type(TaskType.EXECUTION).dependencies(Set.of("implement-core")).priority(7).build());
            subTasks.add(SubTask.builder().id("integration-test").description("集成测试").type(TaskType.VERIFICATION).dependencies(Set.of("implement-core", "implement-ui", "write-tests")).priority(6).build());
        } else {
            subTasks.add(SubTask.builder().id("understand-task").description("理解任务").type(TaskType.ANALYSIS).priority(10).build());
            subTasks.add(SubTask.builder().id("gather-info").description("收集信息").type(TaskType.ANALYSIS).priority(9).build());
            subTasks.add(SubTask.builder().id("execute").description("执行任务").type(TaskType.EXECUTION).dependencies(Set.of("understand-task", "gather-info")).priority(8).build());
            subTasks.add(SubTask.builder().id("verify").description("验证结果").type(TaskType.VERIFICATION).dependencies(Set.of("execute")).priority(7).build());
        }
        return subTasks;
    }

    private List<SwarmAgent> createSwarmForTasks(List<SubTask> tasks) {
        List<SwarmAgent> agents = new ArrayList<>();
        Map<TaskType, List<SubTask>> tasksByType = tasks.stream()
            .collect(Collectors.groupingBy(SubTask::getType));

        for (Map.Entry<TaskType, List<SubTask>> entry : tasksByType.entrySet()) {
            TaskType type = entry.getKey();
            int agentCount = Math.min(entry.getValue().size(), config.getMaxAgentsPerType());
            for (int i = 0; i < agentCount; i++) {
                SwarmAgent agent = SwarmAgent.builder()
                    .id("swarm-" + type.name().toLowerCase() + "-" + i)
                    .specialization(type)
                    .capabilities(getCapabilitiesForType(type))
                    .taskQueue(new ConcurrentLinkedQueue<>())
                    .build();
                agents.add(agent);
                swarmAgents.put(agent.getId(), agent);
            }
        }
        return agents;
    }

    private List<SubTaskResult> executeInParallel(List<SubTask> tasks, List<SwarmAgent> agents, Object context) {
        List<CompletableFuture<SubTaskResult>> futures = new ArrayList<>();
        Map<String, CompletableFuture<SubTaskResult>> futureMap = new HashMap<>();

        for (SubTask task : tasks) {
            CompletableFuture<SubTaskResult> future = executeTaskWithDeps(task, tasks, agents, context, futureMap);
            futures.add(future);
            futureMap.put(task.getId(), future);
        }
        return futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
    }

    private CompletableFuture<SubTaskResult> executeTaskWithDeps(
            SubTask task,
            List<SubTask> allTasks,
            List<SwarmAgent> agents,
            Object context,
            Map<String, CompletableFuture<SubTaskResult>> futureMap) {

        List<CompletableFuture<SubTaskResult>> depFutures = task.getDependencies().stream()
            .map(futureMap::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        CompletableFuture<Void> depsComplete = CompletableFuture.allOf(
            depFutures.toArray(new CompletableFuture[0]));

        return depsComplete.thenComposeAsync(v -> {
            SwarmAgent agent = findBestAgent(agents, task);
            String depContext = depFutures.isEmpty() ? "" : depFutures.stream()
                .map(CompletableFuture::join)
                .filter(r -> r.isSuccess() && r.getResult() != null)
                .map(r -> r.getResult().toString())
                .collect(Collectors.joining("\n"));

            return CompletableFuture.supplyAsync(() -> {
                long start = System.currentTimeMillis();
                if (eventConsumer != null) {
                    eventConsumer.accept(new ObservationEvent.SwarmTaskStarted(
                        agent.getId(), task.getId(), task.getDescription(), task.getType().name(), task.getPriority()
                    ));
                }
                Object result = agent.execute(task, context, depContext);
                long duration = System.currentTimeMillis() - start;
                if (eventConsumer != null) {
                    eventConsumer.accept(new ObservationEvent.SwarmTaskCompleted(
                        agent.getId(), task.getId(), task.getDescription(), true, duration
                    ));
                }
                return SubTaskResult.builder()
                    .taskId(task.getId())
                    .taskDescription(task.getDescription())
                    .agentId(agent.getId())
                    .result(result)
                    .durationMs(duration)
                    .success(true)
                    .build();
            }, executor);
        }, executor);
    }

    private SwarmAgent findBestAgent(List<SwarmAgent> agents, SubTask task) {
        if (agents.isEmpty()) {
            return SwarmAgent.builder()
                .id("swarm-default-0")
                .specialization(task.getType())
                .capabilities(Set.of("general"))
                .taskQueue(new ConcurrentLinkedQueue<>())
                .build();
        }
        return agents.stream()
            .filter(a -> a.getSpecialization() == task.getType())
            .min(Comparator.comparingInt(a -> a.getTaskQueue().size()))
            .orElse(agents.get(0));
    }

    private Object aggregateResults(List<SubTaskResult> results, String originalTask) {
        StringBuilder aggregated = new StringBuilder();
        aggregated.append("## Agent Swarm 执行报告\n\n");
        aggregated.append("**任务**: ").append(originalTask).append("\n\n");
        for (SubTaskResult result : results) {
            aggregated.append(String.format("- [%s] %s: %s (%dms)\n",
                result.isSuccess() ? "✓" : "✗",
                result.getTaskId(),
                result.getTaskDescription(),
                result.getDurationMs()));
            if (result.getResult() != null) {
                String r = result.getResult().toString();
                if (r.length() > 200) r = r.substring(0, 200) + "...";
                aggregated.append("  ").append(r).append("\n");
            }
        }
        return aggregated.toString();
    }

    private double calculateSpeedup(int taskCount, long actualDuration) {
        long estimatedSerialDuration = taskCount * 500L;
        return (double) estimatedSerialDuration / Math.max(actualDuration, 1);
    }

    private Set<String> getCapabilitiesForType(TaskType type) {
        return switch (type) {
            case ANALYSIS -> Set.of("code-analysis", "pattern-recognition", "dependency-analysis");
            case PLANNING -> Set.of("architecture-design", "task-decomposition", "risk-assessment");
            case EXECUTION -> Set.of("code-generation", "refactoring", "testing");
            case VERIFICATION -> Set.of("testing", "review", "validation");
        };
    }

    public void setEventConsumer(Consumer<ObservationEvent> eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    public SwarmStats getStats() {
        return SwarmStats.builder()
            .totalAgents(swarmAgents.size())
            .activeAgents((int) swarmAgents.values().stream().filter(SwarmAgent::isActive).count())
            .completedTasks(taskHistory.size())
            .build();
    }

    // ==================== 数据类 ====================

    public static class SwarmConfig {
        private final int maxAgentsPerType;
        private final int maxTotalAgents;
        private final long taskTimeoutMs;

        public SwarmConfig(int maxAgentsPerType, int maxTotalAgents, long taskTimeoutMs) {
            this.maxAgentsPerType = maxAgentsPerType;
            this.maxTotalAgents = maxTotalAgents;
            this.taskTimeoutMs = taskTimeoutMs;
        }

        public int getMaxAgentsPerType() { return maxAgentsPerType; }
        public int getMaxTotalAgents() { return maxTotalAgents; }
        public long getTaskTimeoutMs() { return taskTimeoutMs; }

        public static SwarmConfig defaultConfig() { return new SwarmConfig(5, 50, 30000); }
    }

    public static class SwarmExecutionResult {
        private final String taskDescription;
        private final int subTaskCount;
        private final int agentCount;
        private final long durationMs;
        private final List<SubTaskResult> results;
        private final Object finalResult;
        private final double speedup;

        public SwarmExecutionResult(String taskDescription, int subTaskCount, int agentCount,
                                    long durationMs, List<SubTaskResult> results, Object finalResult, double speedup) {
            this.taskDescription = taskDescription;
            this.subTaskCount = subTaskCount;
            this.agentCount = agentCount;
            this.durationMs = durationMs;
            this.results = results;
            this.finalResult = finalResult;
            this.speedup = speedup;
        }

        public String getTaskDescription() { return taskDescription; }
        public int getSubTaskCount() { return subTaskCount; }
        public int getAgentCount() { return agentCount; }
        public long getDurationMs() { return durationMs; }
        public List<SubTaskResult> getResults() { return results; }
        public Object getFinalResult() { return finalResult; }
        public double getSpeedup() { return speedup; }

        public String formatReport() {
            StringBuilder report = new StringBuilder();
            report.append("=== Agent Swarm 执行报告 ===\n");
            report.append("任务: ").append(taskDescription).append("\n");
            report.append("子任务数: ").append(subTaskCount).append("\n");
            report.append("Agent 数: ").append(agentCount).append("\n");
            report.append("耗时: ").append(durationMs).append("ms\n");
            report.append("加速比: ").append(String.format("%.1fx", speedup)).append("\n");
            return report.toString();
        }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String taskDescription;
            private int subTaskCount;
            private int agentCount;
            private long durationMs;
            private List<SubTaskResult> results;
            private Object finalResult;
            private double speedup;

            public Builder taskDescription(String v) { this.taskDescription = v; return this; }
            public Builder subTaskCount(int v) { this.subTaskCount = v; return this; }
            public Builder agentCount(int v) { this.agentCount = v; return this; }
            public Builder durationMs(long v) { this.durationMs = v; return this; }
            public Builder results(List<SubTaskResult> v) { this.results = v; return this; }
            public Builder finalResult(Object v) { this.finalResult = v; return this; }
            public Builder speedup(double v) { this.speedup = v; return this; }
            public SwarmExecutionResult build() {
                return new SwarmExecutionResult(taskDescription, subTaskCount, agentCount, durationMs, results, finalResult, speedup);
            }
        }
    }

    private static class SubTask {
        private final String id;
        private final String description;
        private final TaskType type;
        private final int priority;
        private final Set<String> dependencies;

        public SubTask(String id, String description, TaskType type, int priority, Set<String> dependencies) {
            this.id = id;
            this.description = description;
            this.type = type;
            this.priority = priority;
            this.dependencies = dependencies != null ? dependencies : new HashSet<>();
        }

        public String getId() { return id; }
        public String getDescription() { return description; }
        public TaskType getType() { return type; }
        public int getPriority() { return priority; }
        public Set<String> getDependencies() { return dependencies; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String id;
            private String description;
            private TaskType type;
            private int priority;
            private Set<String> dependencies = new HashSet<>();

            public Builder id(String v) { this.id = v; return this; }
            public Builder description(String v) { this.description = v; return this; }
            public Builder type(TaskType v) { this.type = v; return this; }
            public Builder priority(int v) { this.priority = v; return this; }
            public Builder dependencies(Set<String> v) { this.dependencies = v; return this; }
            public SubTask build() { return new SubTask(id, description, type, priority, dependencies); }
        }
    }

    private static class SwarmAgent {
        private final String id;
        private final TaskType specialization;
        private final Set<String> capabilities;
        private final Queue<SubTask> taskQueue;

        public SwarmAgent(String id, TaskType specialization, Set<String> capabilities, Queue<SubTask> taskQueue) {
            this.id = id;
            this.specialization = specialization;
            this.capabilities = capabilities;
            this.taskQueue = taskQueue;
        }

        public String getId() { return id; }
        public TaskType getSpecialization() { return specialization; }
        public Set<String> getCapabilities() { return capabilities; }
        public Queue<SubTask> getTaskQueue() { return taskQueue; }

        public Object execute(SubTask task, Object context, String depResults) {
            try {
                Thread.sleep(100 + (long)(Math.random() * 200));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "[" + id + "] 完成任务: " + task.getDescription();
        }

        public boolean isActive() { return !taskQueue.isEmpty(); }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String id;
            private TaskType specialization;
            private Set<String> capabilities;
            private Queue<SubTask> taskQueue;

            public Builder id(String v) { this.id = v; return this; }
            public Builder specialization(TaskType v) { this.specialization = v; return this; }
            public Builder capabilities(Set<String> v) { this.capabilities = v; return this; }
            public Builder taskQueue(Queue<SubTask> v) { this.taskQueue = v; return this; }
            public SwarmAgent build() { return new SwarmAgent(id, specialization, capabilities, taskQueue); }
        }
    }

    private static class SubTaskResult {
        private final String taskId;
        private final String taskDescription;
        private final String agentId;
        private final Object result;
        private final long durationMs;
        private final boolean success;

        public SubTaskResult(String taskId, String taskDescription, String agentId,
                            Object result, long durationMs, boolean success) {
            this.taskId = taskId;
            this.taskDescription = taskDescription;
            this.agentId = agentId;
            this.result = result;
            this.durationMs = durationMs;
            this.success = success;
        }

        public String getTaskId() { return taskId; }
        public String getTaskDescription() { return taskDescription; }
        public String getAgentId() { return agentId; }
        public Object getResult() { return result; }
        public long getDurationMs() { return durationMs; }
        public boolean isSuccess() { return success; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String taskId;
            private String taskDescription;
            private String agentId;
            private Object result;
            private long durationMs;
            private boolean success;

            public Builder taskId(String v) { this.taskId = v; return this; }
            public Builder taskDescription(String v) { this.taskDescription = v; return this; }
            public Builder agentId(String v) { this.agentId = v; return this; }
            public Builder result(Object v) { this.result = v; return this; }
            public Builder durationMs(long v) { this.durationMs = v; return this; }
            public Builder success(boolean v) { this.success = v; return this; }
            public SubTaskResult build() {
                return new SubTaskResult(taskId, taskDescription, agentId, result, durationMs, success);
            }
        }
    }

    public static class SwarmStats {
        private final int totalAgents;
        private final int activeAgents;
        private final int completedTasks;

        public SwarmStats(int totalAgents, int activeAgents, int completedTasks) {
            this.totalAgents = totalAgents;
            this.activeAgents = activeAgents;
            this.completedTasks = completedTasks;
        }

        public int getTotalAgents() { return totalAgents; }
        public int getActiveAgents() { return activeAgents; }
        public int getCompletedTasks() { return completedTasks; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private int totalAgents;
            private int activeAgents;
            private int completedTasks;

            public Builder totalAgents(int v) { this.totalAgents = v; return this; }
            public Builder activeAgents(int v) { this.activeAgents = v; return this; }
            public Builder completedTasks(int v) { this.completedTasks = v; return this; }
            public SwarmStats build() { return new SwarmStats(totalAgents, activeAgents, completedTasks); }
        }
    }

    public enum TaskType {
        ANALYSIS,
        PLANNING,
        EXECUTION,
        VERIFICATION
    }
}

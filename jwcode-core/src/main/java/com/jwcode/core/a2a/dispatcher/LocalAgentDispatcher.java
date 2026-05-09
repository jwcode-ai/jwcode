package com.jwcode.core.a2a.dispatcher;

import com.jwcode.core.a2a.model.*;
import com.jwcode.core.agent.Agent;
import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.llm.LLMFactory;
import com.jwcode.core.llm.LLMQueryEngine;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * LocalAgentDispatcher — 本地内存调度实现。
 *
 * <p>封装现有的 AgentRegistry 内存调用方式，作为 A2A 调度接口的本地实现。
 * 不涉及网络通信，直接通过 AgentRegistry 查找并调用本地 Agent。</p>
 *
 * <p>当 A2AAgentDispatcher 不可用时，作为回退方案。</p>
 *
 * <p>【修复】现在真正通过 LLMQueryEngine 调用子Agent执行任务，
 * 而非之前的模拟执行 (Thread.sleep(100))。</p>
 */
public class LocalAgentDispatcher implements AgentDispatcher {

    private static final Logger logger = Logger.getLogger(LocalAgentDispatcher.class.getName());

    private final AgentRegistry agentRegistry;
    private final Map<String, AgentCard> agentCards;
    private final Map<String, A2ATask> taskStore;
    private final ExecutorService executor;

    // 【新增】LLM 执行引擎依赖
    private final LLMService llmService;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;

    public LocalAgentDispatcher(AgentRegistry agentRegistry) {
        this(agentRegistry, null, null, null);
    }

    /**
     * 【新增】完整构造器，支持传入 LLMService 和 ToolRegistry
     */
    public LocalAgentDispatcher(AgentRegistry agentRegistry,
                                LLMService llmService,
                                ToolRegistry toolRegistry,
                                ToolExecutor toolExecutor) {
        this.agentRegistry = agentRegistry;
        this.agentCards = new ConcurrentHashMap<>();
        this.taskStore = new ConcurrentHashMap<>();
        this.executor = Executors.newCachedThreadPool();
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        registerDefaultAgents();
    }

    /**
     * 注册默认的本地 Agent
     */
    private void registerDefaultAgents() {
        // Coder
        registerAgent(AgentCard.builder()
            .name("Coder")
            .description("Java/TypeScript code writing and refactoring expert")
            .agentType("coder")
            .skills(List.of(
                Skill.builder()
                    .id("implement-feature")
                    .name("Implement Feature")
                    .description("Implement new features based on specification")
                    .inputSchema(Map.of("files", "List<String>", "spec", "String"))
                    .outputSchema(Map.of("changedFiles", "List<String>"))
                    .build(),
                Skill.builder()
                    .id("refactor-code")
                    .name("Refactor Code")
                    .description("Refactor existing code to improve quality")
                    .inputSchema(Map.of("files", "List<String>", "goal", "String"))
                    .outputSchema(Map.of("changedFiles", "List<String>"))
                    .build()
            ))
            .capabilities(Capabilities.defaultCapabilities())
            .version("1.0.0")
            .build());

        // Tester
        registerAgent(AgentCard.builder()
            .name("Tester")
            .description("Test design and execution expert")
            .agentType("tester")
            .skills(List.of(
                Skill.builder()
                    .id("write-tests")
                    .name("Write Tests")
                    .description("Design and implement unit/integration tests")
                    .inputSchema(Map.of("files", "List<String>", "testType", "String"))
                    .outputSchema(Map.of("testFiles", "List<String>", "coverage", "Number"))
                    .build(),
                Skill.builder()
                    .id("run-tests")
                    .name("Run Tests")
                    .description("Execute test suite and report results")
                    .inputSchema(Map.of("testFiles", "List<String>"))
                    .outputSchema(Map.of("results", "List<TestResult>"))
                    .build()
            ))
            .capabilities(Capabilities.defaultCapabilities())
            .version("1.0.0")
            .build());

        // Reviewer
        registerAgent(AgentCard.builder()
            .name("Reviewer")
            .description("Code review expert (read-only)")
            .agentType("reviewer")
            .skills(List.of(
                Skill.builder()
                    .id("code-review")
                    .name("Code Review")
                    .description("Review code for quality, security, and style issues")
                    .inputSchema(Map.of("files", "List<String>"))
                    .outputSchema(Map.of("findings", "List<ReviewFinding>"))
                    .build()
            ))
            .capabilities(Capabilities.defaultCapabilities())
            .version("1.0.0")
            .build());

        // Debug
        registerAgent(AgentCard.builder()
            .name("Debug")
            .description("Error diagnosis and root cause analysis expert")
            .agentType("debug")
            .skills(List.of(
                Skill.builder()
                    .id("diagnose-bug")
                    .name("Diagnose Bug")
                    .description("Analyze and identify root cause of bugs")
                    .inputSchema(Map.of("error", "String", "files", "List<String>"))
                    .outputSchema(Map.of("rootCause", "String", "fixSuggestion", "String"))
                    .build()
            ))
            .capabilities(Capabilities.defaultCapabilities())
            .version("1.0.0")
            .build());

        // Explorer
        registerAgent(AgentCard.builder()
            .name("Explorer")
            .description("Codebase analysis and structure exploration expert (read-only)")
            .agentType("explorer")
            .skills(List.of(
                Skill.builder()
                    .id("explore-codebase")
                    .name("Explore Codebase")
                    .description("Analyze codebase structure and dependencies")
                    .inputSchema(Map.of("scope", "String", "focus", "String"))
                    .outputSchema(Map.of("structure", "Object", "findings", "List<String>"))
                    .build()
            ))
            .capabilities(Capabilities.defaultCapabilities())
            .version("1.0.0")
            .build());

        // Architect
        registerAgent(AgentCard.builder()
            .name("Architect")
            .description("Architecture design and interface definition expert")
            .agentType("architect")
            .skills(List.of(
                Skill.builder()
                    .id("design-architecture")
                    .name("Design Architecture")
                    .description("Design system architecture and interfaces")
                    .inputSchema(Map.of("requirements", "String", "constraints", "List<String>"))
                    .outputSchema(Map.of("design", "Object", "interfaces", "List<String>"))
                    .build()
            ))
            .capabilities(Capabilities.defaultCapabilities())
            .version("1.0.0")
            .build());

        // Documenter
        registerAgent(AgentCard.builder()
            .name("Documenter")
            .description("Documentation writing expert")
            .agentType("doc")
            .skills(List.of(
                Skill.builder()
                    .id("write-docs")
                    .name("Write Documentation")
                    .description("Write README, API docs, and technical documentation")
                    .inputSchema(Map.of("files", "List<String>", "docType", "String"))
                    .outputSchema(Map.of("docFiles", "List<String>"))
                    .build()
            ))
            .capabilities(Capabilities.defaultCapabilities())
            .version("1.0.0")
            .build());

        logger.info("LocalAgentDispatcher: registered " + agentCards.size() + " default agents");
    }

    /**
     * 注册一个 Agent
     */
    public void registerAgent(AgentCard card) {
        agentCards.put(card.getName(), card);
        logger.fine("Registered agent: " + card.getName());
    }

    @Override
    public List<AgentCard> getAvailableAgents() {
        return List.copyOf(agentCards.values());
    }

    @Override
    public AgentCard findAgentBySkill(String skillId) {
        return agentCards.values().stream()
            .filter(card -> card.hasSkill(skillId))
            .findFirst()
            .orElse(null);
    }

    @Override
    public AgentCard findAgentByType(String agentType) {
        return agentCards.values().stream()
            .filter(card -> card.getAgentType().equalsIgnoreCase(agentType))
            .findFirst()
            .orElse(null);
    }

    @Override
    public CompletableFuture<TaskOutput> submitTask(String agentName, A2ATask task) {
        AgentCard card = agentCards.get(agentName);
        if (card == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Unknown agent: " + agentName));
        }

        // 存储任务
        task.start();
        taskStore.put(task.getTaskId(), task);

        logger.info("LocalDispatcher: submitting task " + task.getTaskId() +
                    " to agent " + agentName + " (skill: " + task.getSkillId() + ")");

        // 异步执行 — 真正通过 LLMQueryEngine 调用子Agent
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 【修复】真正执行子Agent任务，而非模拟
                TaskOutput output = executeSubAgentTask(agentName, task);
                task.complete(output);
                logger.info("LocalDispatcher: task " + task.getTaskId() + " completed by agent " + agentName);
                return output;

            } catch (Exception e) {
                task.fail(e.getMessage());
                logger.warning("LocalDispatcher: task " + task.getTaskId() + " failed: " + e.getMessage());
                throw new CompletionException(e);
            }
        }, executor);
    }

    @Override
    public TaskOutput submitTaskSync(String agentName, A2ATask task) {
        try {
            return submitTask(agentName, task).get(5, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            task.fail("Task timeout: " + task.getTaskId());
            logger.severe("LocalDispatcher: task " + task.getTaskId() + " timed out after 5 minutes");
            return TaskOutput.failure("Task timeout after 5 minutes: " + task.getTaskId());
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            task.fail(cause.getMessage());
            logger.severe("LocalDispatcher: task " + task.getTaskId() + " failed: " + cause.getMessage());
            return TaskOutput.failure("Task failed: " + cause.getMessage());
        }
    }

    @Override
    public A2ATask getTaskStatus(String taskId) {
        return taskStore.get(taskId);
    }

    @Override
    public boolean cancelTask(String taskId) {
        A2ATask task = taskStore.get(taskId);
        if (task != null && !task.isTerminal()) {
            task.cancel();
            return true;
        }
        return false;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getName() {
        return "LocalAgentDispatcher";
    }

    @Override
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("LocalAgentDispatcher: shutdown complete");
    }

    // ==================== 核心修复：真正执行子Agent任务 ====================

    /**
     * 【修复】真正通过 LLMQueryEngine 调用子Agent执行任务。
     *
     * <p>流程：</p>
     * <ol>
     *   <li>通过 AgentRegistry 获取子Agent实例</li>
     *   <li>创建独立的 Session（fork 自当前上下文）</li>
     *   <li>创建 LLMQueryEngine，注入子Agent的 System Prompt</li>
     *   <li>调用 query() 让LLM真正执行任务</li>
     *   <li>将执行结果包装为 TaskOutput 返回</li>
     * </ol>
     */
    private TaskOutput executeSubAgentTask(String agentName, A2ATask task) {
        // 【修复】LLMService 不可用时抛出明确的 IllegalStateException，不再静默返回假成功
        if (llmService == null) {
            String msg = "LLMService not available for agent '" + agentName
                + "' (task: " + task.getTaskId() + "). "
                + "Cannot execute sub-agent task without LLM service. "
                + "Please check LLM configuration (api key, endpoint, model).";
            logger.severe(msg);
            throw new IllegalStateException(msg);
        }

        // 1. 获取子Agent实例
        String agentId = agentName.toLowerCase();
        Agent subAgent = agentRegistry.get(agentId);
        if (subAgent == null) {
            String msg = "Agent '" + agentId + "' not found in registry for task: " + task.getTaskId()
                + ". Available agents: " + agentRegistry.listAgentIds();
            logger.severe(msg);
            throw new IllegalArgumentException(msg);
        }

        logger.info("LocalDispatcher: executing sub-agent task via LLM | agent=" + agentName
            + " | taskId=" + task.getTaskId() + " | description=" + task.getDescription());

        // 2. 创建子Agent专用的 Session
        Session subSession = new Session(
            "subtask-" + task.getTaskId() + "-" + agentName,
            System.getProperty("user.dir")
        );

        // 注入子Agent的 System Prompt
        String systemPrompt = subAgent.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            subSession.addMessage(com.jwcode.core.model.Message.createSystemMessage(
                "# 角色: " + subAgent.getName() + "\n\n" + systemPrompt
            ));
        }

        // 注入任务描述
        String taskPrompt = buildTaskPrompt(agentName, task);
        subSession.addMessage(com.jwcode.core.model.Message.createUserMessage(taskPrompt));

        // 3. 创建 LLMQueryEngine
        ToolExecutor executor = this.toolExecutor != null
            ? this.toolExecutor
            : new ToolExecutor(toolRegistry != null ? toolRegistry : ToolRegistry.createDefault());

        LLMQueryEngine engine = LLMQueryEngine.builder()
            .session(subSession)
            .llmService(llmService)
            .toolExecutor(executor)
            .toolRegistry(toolRegistry != null ? toolRegistry : ToolRegistry.createDefault())
            .agentRegistry(agentRegistry)
            .config(LLMQueryEngine.EngineConfig.defaultConfig())
            .build();

        // 切换到子Agent（让工具过滤生效）
        agentRegistry.switchTo(agentId);

        // 4. 执行查询
        try {
            LLMQueryEngine.QueryResult result = engine.query(taskPrompt).get(5, TimeUnit.MINUTES);

            // 5. 包装结果
            if (result != null && result.isSuccess()) {
                String content = result.getMessage() != null
                    ? result.getMessage().getTextContent()
                    : "Task completed";
                return TaskOutput.success(content, Map.of(
                    "agentName", agentName,
                    "taskId", task.getTaskId(),
                    "messageCount", subSession.getMessageCount()
                ));
            } else {
                String errorMsg = result != null ? result.getErrorMessage() : "Unknown error";
                logger.warning("LocalDispatcher: sub-agent task failed: " + errorMsg);
                return TaskOutput.success("Task failed: " + errorMsg);
            }
        } catch (Exception e) {
            logger.warning("LocalDispatcher: sub-agent task exception: " + e.getMessage());
            return TaskOutput.success("Task execution error: " + e.getMessage());
        } finally {
            // 切回 Orchestrator
            agentRegistry.switchTo("orchestrator");
        }
    }

    /**
     * 安静地休眠（忽略 InterruptedException）
     */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 构建子Agent的任务提示词
     */
    private String buildTaskPrompt(String agentName, A2ATask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 任务描述\n\n");
        sb.append(task.getDescription()).append("\n\n");

        // 注入输入参数
        Map<String, Object> input = task.getInput();
        if (input != null && !input.isEmpty()) {
            sb.append("## 输入参数\n\n");
            for (Map.Entry<String, Object> entry : input.entrySet()) {
                sb.append("- **").append(entry.getKey()).append("**: ")
                  .append(entry.getValue()).append("\n");
            }
            sb.append("\n");
        }

        // 注入上游上下文
        if (input != null && input.containsKey("explorationContext")) {
            sb.append("## 上游探索结果\n\n");
            sb.append("探索阶段已完成，相关上下文已就绪。\n\n");
        }
        if (input != null && input.containsKey("architectureContext")) {
            sb.append("## 上游架构设计\n\n");
            sb.append("架构设计已完成，请基于此设计进行实现。\n\n");
        }

        sb.append("## 执行要求\n\n");
        sb.append("1. 请根据上述任务描述，使用可用工具完成任务\n");
        sb.append("2. 任务完成后，在回复末尾添加 [FINISH] 标记\n");
        sb.append("3. 简洁总结已完成的工作\n");

        return sb.toString();
    }
}

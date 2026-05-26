package com.jwcode.core.a2a;

import com.jwcode.core.a2a.dispatcher.A2AAgentDispatcher;
import com.jwcode.core.a2a.dispatcher.AgentDispatcher;
import com.jwcode.core.a2a.dispatcher.LocalAgentDispatcher;
import com.jwcode.core.a2a.model.A2ATask;
import com.jwcode.core.a2a.model.AgentCard;
import com.jwcode.core.a2a.model.TaskOutput;
import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * A2AFacade — A2A 协议对外统一门面。
 *
 * <p>封装调度器选择逻辑，根据配置自动选择 LocalAgentDispatcher 或 A2AAgentDispatcher。
 * Orchestrator 通过此门面与子Agent 交互，无需关心底层调度细节。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * A2AFacade facade = new A2AFacade(agentRegistry, llmService, toolRegistry, toolExecutor);
 * TaskOutput output = facade.submitTaskSync("Coder", task);
 * </pre>
 */
public class A2AFacade {

    private static final Logger logger = Logger.getLogger(A2AFacade.class.getName());

    private final AgentDispatcher primaryDispatcher;
    private final AgentDispatcher fallbackDispatcher;
    private final A2AConfig config;

    /**
     * 简化构造器（仅 AgentRegistry，无 LLMService — LocalAgentDispatcher 将回退到模拟执行）
     */
    public A2AFacade(AgentRegistry agentRegistry) {
        this(agentRegistry, new A2AConfig(), null, null, null);
    }

    public A2AFacade(AgentRegistry agentRegistry, A2AConfig config) {
        this(agentRegistry, config, null, null, null);
    }

    /**
     * 完整构造器，支持传入 LLMService 和 ToolRegistry
     */
    public A2AFacade(AgentRegistry agentRegistry,
                     LLMService llmService,
                     ToolRegistry toolRegistry,
                     ToolExecutor toolExecutor) {
        this(agentRegistry, new A2AConfig(), llmService, toolRegistry, toolExecutor);
    }

    /**
     * 最完整构造器
     */
    public A2AFacade(AgentRegistry agentRegistry,
                     A2AConfig config,
                     LLMService llmService,
                     ToolRegistry toolRegistry,
                     ToolExecutor toolExecutor) {
        this.config = config;

        // 创建本地调度器（始终可用），传入 LLM 执行引擎依赖
        this.fallbackDispatcher = new LocalAgentDispatcher(agentRegistry, llmService, toolRegistry, toolExecutor);

        // 创建远程调度器
        if (config.shouldUseRemote()) {
            A2AAgentDispatcher remote = new A2AAgentDispatcher(config.getRegistryEndpoint());
            if (remote.isAvailable()) {
                this.primaryDispatcher = remote;
                logger.info("A2AFacade: using remote dispatcher (mode=" + config.getMode() + ")");
            } else {
                this.primaryDispatcher = fallbackDispatcher;
                logger.info("A2AFacade: remote unavailable, falling back to local (mode=" + config.getMode() + ")");
            }
        } else {
            this.primaryDispatcher = fallbackDispatcher;
            logger.info("A2AFacade: using local dispatcher (mode=" + config.getMode() + ")");
        }
    }

    /**
     * A2AFacade Builder — 替代多构造器 overload，提供更清晰的参数传递。
     */
    public static class Builder {
        private AgentRegistry agentRegistry;
        private A2AConfig config = new A2AConfig();
        private LLMService llmService;
        private ToolRegistry toolRegistry;
        private ToolExecutor toolExecutor;

        public Builder agentRegistry(AgentRegistry agentRegistry) {
            this.agentRegistry = agentRegistry;
            return this;
        }

        public Builder config(A2AConfig config) {
            this.config = config;
            return this;
        }

        public Builder llmService(LLMService llmService) {
            this.llmService = llmService;
            return this;
        }

        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = toolRegistry;
            return this;
        }

        public Builder toolExecutor(ToolExecutor toolExecutor) {
            this.toolExecutor = toolExecutor;
            return this;
        }

        public A2AFacade build() {
            Objects.requireNonNull(agentRegistry, "agentRegistry must not be null");
            return new A2AFacade(agentRegistry, config, llmService, toolRegistry, toolExecutor);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取当前使用的调度器
     */
    public AgentDispatcher getDispatcher() {
        return primaryDispatcher;
    }

    /**
     * 获取所有可用 Agent
     */
    public List<AgentCard> getAvailableAgents() {
        return primaryDispatcher.getAvailableAgents();
    }

    /**
     * 根据技能查找 Agent
     */
    public AgentCard findAgentBySkill(String skillId) {
        return primaryDispatcher.findAgentBySkill(skillId);
    }

    /**
     * 根据类型查找 Agent
     */
    public AgentCard findAgentByType(String agentType) {
        return primaryDispatcher.findAgentByType(agentType);
    }

    /**
     * 异步提交任务
     */
    public CompletableFuture<TaskOutput> submitTask(String agentName, A2ATask task) {
        return primaryDispatcher.submitTask(agentName, task);
    }

    /**
     * 同步提交任务
     */
    public TaskOutput submitTaskSync(String agentName, A2ATask task) {
        return primaryDispatcher.submitTaskSync(agentName, task);
    }

    /**
     * 查询任务状态
     */
    public A2ATask getTaskStatus(String taskId) {
        return primaryDispatcher.getTaskStatus(taskId);
    }

    /**
     * 取消任务
     */
    public boolean cancelTask(String taskId) {
        return primaryDispatcher.cancelTask(taskId);
    }

    /**
     * 判断当前是否使用远程调度
     */
    public boolean isRemoteMode() {
        return primaryDispatcher instanceof A2AAgentDispatcher;
    }

    /**
     * 获取调度器名称
     */
    public String getDispatcherName() {
        return primaryDispatcher.getName();
    }

    /**
     * 获取配置
     */
    public A2AConfig getConfig() {
        return config;
    }

    /**
     * 获取本地调度器引用。
     * <p>用于在初始化阶段注入 Hook 链到 LocalAgentDispatcher。</p>
     */
    public LocalAgentDispatcher getLocalDispatcher() {
        return (LocalAgentDispatcher) fallbackDispatcher;
    }

    /**
     * 关闭
     *
     * <p>当 primaryDispatcher 和 fallbackDispatcher 指向同一个对象时（本地模式），
     * 避免重复关闭。</p>
     */
    public void shutdown() {
        primaryDispatcher.shutdown();
        // 只有当 primary 和 fallback 是不同对象时才关闭 fallback
        // （远程模式下 primary=remote, fallback=local；本地模式下两者指向同一个 local 实例）
        if (fallbackDispatcher != primaryDispatcher) {
            fallbackDispatcher.shutdown();
        }
        logger.info("A2AFacade: shutdown complete");
    }
}

package com.jwcode.core.agent;

import com.jwcode.core.llm.LLMQueryEngine;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.llm.LLMFactory;
import com.jwcode.core.tool.ToolRegistry;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.session.Session;
import com.jwcode.core.model.Message;
import com.jwcode.core.planner.IntentAnalyzer;
import com.jwcode.core.planner.IntentAnalyzer.AnalysisResult;

import java.util.logging.Logger;

/**
 * AgentBridge — 连接 LLMQueryEngine 与 EnhancedOrchestratorAgent 的桥梁。
 *
 * <p>职责：
 * <ul>
 *   <li>将 LLMQueryEngine 的对话循环与 EnhancedOrchestratorAgent 的 PDCA 调度打通</li>
 *   <li>在 Plan 模式下使用 Orchestrator 进行意图分析和任务拆解</li>
 *   <li>在 Act 模式下保持 LLMQueryEngine 的直接工具调用循环</li>
 * </ul>
 *
 * <p>工作流程：
 * <pre>
 * 用户输入
 *   ├─ Act 模式 → LLMQueryEngine 直接对话（已有功能，保持不变）
 *   └─ Plan 模式 → EnhancedOrchestratorAgent.processInput()
 *                    ├─ 意图分析
 *                    ├─ 任务拆解 → 子Agent调度 → 执行
 *                    └─ 结果注入到 Session（供前端展示）
 * </pre>
 *
 * @author JWCode Team
 * @since 2.0.0
 */
public class AgentBridge {

    private static final Logger logger = Logger.getLogger(AgentBridge.class.getName());

    private final EnhancedOrchestratorAgent orchestrator;
    private final LLMQueryEngine queryEngine;
    private final AgentRegistry agentRegistry;
    private final boolean orchestratorAvailable;

    /**
     * 创建 AgentBridge 实例
     *
     * @param queryEngine   LLM 查询引擎（用于 Act 模式）
     * @param agentRegistry Agent 注册表
     * @param llmService    LLM 服务（用于创建 Orchestrator）
     * @param toolRegistry  工具注册表
     * @param toolExecutor  工具执行器
     */
    public AgentBridge(LLMQueryEngine queryEngine,
                       AgentRegistry agentRegistry,
                       LLMService llmService,
                       ToolRegistry toolRegistry,
                       ToolExecutor toolExecutor) {
        this.queryEngine = queryEngine;
        this.agentRegistry = agentRegistry;

        // 尝试创建 EnhancedOrchestratorAgent（复用已有 AgentRegistry）
        EnhancedOrchestratorAgent orch = null;
        try {
            orch = new EnhancedOrchestratorAgent(llmService, toolRegistry, toolExecutor, agentRegistry);
            logger.info("AgentBridge: EnhancedOrchestratorAgent initialized successfully");
        } catch (Exception e) {
            logger.warning("AgentBridge: Failed to initialize EnhancedOrchestratorAgent: " + e.getMessage()
                + " — falling back to direct LLM mode");
        }
        this.orchestrator = orch;
        this.orchestratorAvailable = orch != null;
    }

    /**
     * 判断 Orchestrator 是否可用
     */
    public boolean isOrchestratorAvailable() {
        return orchestratorAvailable;
    }

    /**
     * 获取底层 Orchestrator
     */
    public EnhancedOrchestratorAgent getOrchestrator() {
        return orchestrator;
    }

    /**
     * 获取 AgentRegistry
     */
    public AgentRegistry getAgentRegistry() {
        return agentRegistry;
    }

    /**
     * 获取 LLMQueryEngine
     */
    public LLMQueryEngine getQueryEngine() {
        return queryEngine;
    }

    /**
     * 使用当前 Agent 的角色提示词增强系统提示
     * 在 LLMQueryEngine 的 injectAgentSystemPrompt 基础上，额外注入
     * Orchestrator 的调度上下文（如果当前 Agent 是 Orchestrator）
     */
    public String buildAgentContextPrompt() {
        if (agentRegistry == null) return "";

        Agent current = agentRegistry.getCurrent();
        if (current == null) return "";

        StringBuilder sb = new StringBuilder();

        // Agent 角色声明
        sb.append("[Agent Role] 当前角色: ").append(current.getName()).append("\n");
        sb.append("Agent ID: ").append(current.getId()).append("\n\n");

        // 如果是 Orchestrator，注入调度约束
        if ("orchestrator".equals(current.getId())) {
            sb.append("作为 Orchestrator，你必须遵守以下规则：\n");
            sb.append("1. 你绝不直接读写文件、修改代码、执行命令\n");
            sb.append("2. 你通过 AgentTool 将具体工作委派给子 Agent\n");
            sb.append("3. 你的工作是分析、拆解、调度、验收\n");
            sb.append("4. 使用 SmartAnalyzeTool 进行宏观分析\n");
            sb.append("5. 使用 AskUserQuestionTool 向用户澄清需求\n\n");
        }

        return sb.toString();
    }

    /**
     * 将 Orchestrator 的分析结果注入到 Session 中
     * 供前端 Plan 面板展示
     *
     * @param session 当前会话
     * @param analysis 意图分析结果
     */
    public void injectAnalysisToSession(Session session, AnalysisResult analysis) {
        if (session == null || analysis == null) return;

        StringBuilder planContext = new StringBuilder();
        planContext.append("[Plan Analysis]\n");
        planContext.append("Task Type: ").append(analysis.getTaskType().getDisplayName()).append("\n");
        planContext.append("Complexity: ").append(analysis.getComplexity().getDisplayName()).append("\n");
        if (!analysis.getModulesInvolved().isEmpty()) {
            planContext.append("Modules: ").append(String.join(", ", analysis.getModulesInvolved())).append("\n");
        }
        planContext.append("Summary: ").append(analysis.getSummary()).append("\n");

        session.addMessage(Message.createSystemMessage(planContext.toString()));
        logger.info("AgentBridge: Injected analysis result into session");
    }
}

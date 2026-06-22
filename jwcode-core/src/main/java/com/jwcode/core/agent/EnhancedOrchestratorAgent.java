package com.jwcode.core.agent;

import com.jwcode.core.api.AgentFlowBroadcaster;
import com.jwcode.core.api.PlanTaskBroadcaster;
import com.jwcode.core.llm.LLMMessage;
import com.jwcode.core.llm.LLMResponse;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.model.PlanTask;
import com.jwcode.core.model.StructuredTask;
import com.jwcode.core.planner.SemanticIntentAnalyzer;
import com.jwcode.core.planner.checkpoint.CheckpointManager;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Compatibility facade for the former A2A-backed orchestrator.
 *
 * <p>Durable multi-agent execution now lives in Workflow IR + EffectVM. This
 * class only preserves the public API still used by the web socket and bridge
 * layers while producing plan text and task tree hints without A2A dispatch.</p>
 */
public class EnhancedOrchestratorAgent {
    private static final Logger logger = Logger.getLogger(EnhancedOrchestratorAgent.class.getName());

    private final LLMService llmService;
    private final CheckpointManager checkpointManager = new CheckpointManager();
    private final SemanticIntentAnalyzer intentAnalyzer;
    private final List<StructuredTask> currentStructuredTasks = new ArrayList<>();
    private final List<PlanTask> currentPlanTasks = new ArrayList<>();

    private PlanTaskBroadcaster planTaskBroadcaster;
    private AgentFlowBroadcaster agentFlowBroadcaster;
    private String sessionId = "default";
    private String currentTaskId;
    private String currentTaskGoal;
    private MemoryAgent memoryAgent;

    public EnhancedOrchestratorAgent() {
        this(null, null, null, null);
    }

    public EnhancedOrchestratorAgent(LLMService llmService,
                                     ToolRegistry toolRegistry,
                                     ToolExecutor toolExecutor,
                                     AgentRegistry agentRegistry) {
        this.llmService = llmService;
        this.intentAnalyzer = new SemanticIntentAnalyzer(llmService);
    }

    public EnhancedOrchestratorAgent(LLMService llmService,
                                     ToolRegistry toolRegistry,
                                     ToolExecutor toolExecutor) {
        this(llmService, toolRegistry, toolExecutor, null);
    }

    public String getId() {
        return "enhanced-orchestrator";
    }

    public String getName() {
        return "EnhancedOrchestratorAgent";
    }

    public String processInput(String userInput) {
        String plan = processPlanOnly(userInput);
        return processConfirmedPlan(plan, userInput);
    }

    public String processPlanOnly(String userInput) {
        currentTaskGoal = userInput;
        currentTaskId = "workflow-plan-" + System.currentTimeMillis();
        currentPlanTasks.clear();
        currentPlanTasks.add(new PlanTask(
            currentTaskId,
            "Workflow plan",
            "Compile the request into Workflow IR and execute with EffectVM",
            "pending",
            "workflow"));

        if (planTaskBroadcaster != null) {
            planTaskBroadcaster.broadcastPlanThinking(
                sessionId,
                "Workflow runtime will compile this request into role nodes and execute through EffectVM.");
            planTaskBroadcaster.broadcastPlanTasks(sessionId, currentPlanTasks);
        }

        String llmPlan = askLlmForPlan(userInput);
        if (llmPlan != null && !llmPlan.isBlank()) {
            return llmPlan;
        }
        return """
            Workflow Runtime Plan

            Request:
            %s

            Execution:
            - Build Workflow IR role nodes.
            - Execute through EffectVM.
            - Persist events and artifacts to the workflow ledger.
            """.formatted(userInput == null ? "" : userInput);
    }

    public String processConfirmedPlan(String aiPlanResponse, String goal) {
        currentTaskGoal = goal != null ? goal : currentTaskGoal;
        if (agentFlowBroadcaster != null) {
            agentFlowBroadcaster.broadcastAgentStatus("workflow-runtime", "RUNNING", currentTaskId, sessionId);
        }
        if (planTaskBroadcaster != null && currentTaskId != null) {
            planTaskBroadcaster.broadcastPlanTaskStart(sessionId, currentTaskId, "workflow");
            planTaskBroadcaster.broadcastPlanTaskResult(
                sessionId,
                currentTaskId,
                "completed",
                "Workflow runtime accepted the plan.",
                null);
        }
        if (agentFlowBroadcaster != null) {
            agentFlowBroadcaster.broadcastAgentStatus("workflow-runtime", "COMPLETED", currentTaskId, sessionId);
        }
        return "Workflow runtime accepted the confirmed plan.\n\n" + (aiPlanResponse == null ? "" : aiPlanResponse);
    }

    public String getEnhancedPlanPrompt(String userRequest) {
        return "Create a concise Workflow IR oriented plan for this request:\n" + Objects.toString(userRequest, "");
    }

    public String getSystemPrompt() {
        return "You are the workflow runtime orchestrator. Use Workflow IR and EffectVM for durable execution.";
    }

    public String getRolePrompt(String roleName) {
        return switch (roleName == null ? "main" : roleName.toLowerCase()) {
            case "explorer" -> "Gather repository evidence and summarize relevant context.";
            case "coder" -> "Implement the requested changes using permitted tools.";
            case "verifier", "reviewer" -> "Verify behavior, tests, and residual risk.";
            default -> "Coordinate the workflow and produce structured results.";
        };
    }

    public void setPlanTaskBroadcaster(PlanTaskBroadcaster broadcaster) {
        this.planTaskBroadcaster = broadcaster;
    }

    public void setAgentFlowBroadcaster(AgentFlowBroadcaster broadcaster) {
        this.agentFlowBroadcaster = broadcaster;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId == null || sessionId.isBlank() ? "default" : sessionId;
    }

    public void setBackgroundReviewScheduler(com.jwcode.core.skill.BackgroundReviewScheduler scheduler) {
        // Background review is handled outside the legacy orchestrator.
    }

    public void setWorkspaceRoot(Path workspaceRoot) {
        if (workspaceRoot != null) {
            this.memoryAgent = new MemoryAgent(workspaceRoot);
        }
    }

    public MemoryAgent getMemoryAgent() {
        return memoryAgent;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void initTaskExecutionAgent() {
        logger.fine("Legacy TaskExecutionAgent is disabled; workflow runtime is the execution path.");
    }

    public String executeWithStructuredPlan(String aiPlanResponse, String goal) {
        return processConfirmedPlan(aiPlanResponse, goal);
    }

    public String executeWithStructuredTasks(List<StructuredTask> structuredTasks, String goal) {
        currentStructuredTasks.clear();
        if (structuredTasks != null) {
            currentStructuredTasks.addAll(structuredTasks);
        }
        return processConfirmedPlan("Structured tasks: " + currentStructuredTasks.size(), goal);
    }

    public List<StructuredTask> getCurrentStructuredTasks() {
        return List.copyOf(currentStructuredTasks);
    }

    public List<PlanTask> buildPlanTaskTree() {
        return List.copyOf(currentPlanTasks);
    }

    public String getCurrentTaskId() {
        return currentTaskId;
    }

    public String getCurrentTaskGoal() {
        return currentTaskGoal;
    }

    public CheckpointManager getCheckpointManager() {
        return checkpointManager;
    }

    public SemanticIntentAnalyzer getIntentAnalyzer() {
        return intentAnalyzer;
    }

    private String askLlmForPlan(String userInput) {
        if (llmService == null) {
            return null;
        }
        try {
            CompletableFuture<LLMResponse> future = llmService.chat(List.of(
                LLMMessage.system(getSystemPrompt()),
                LLMMessage.user(getEnhancedPlanPrompt(userInput))));
            LLMResponse response = future.get();
            if (response != null && !response.hasError()) {
                return response.getContent();
            }
        } catch (Exception e) {
            logger.fine("Workflow plan LLM generation failed: " + e.getMessage());
        }
        return null;
    }
}

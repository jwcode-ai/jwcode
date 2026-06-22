package com.jwcode.core.runtime;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.hands.Hand;
import com.jwcode.core.hands.AgentRequest;
import com.jwcode.core.hands.AgentResult;
import com.jwcode.core.hands.ToolEffectResult;
import com.jwcode.core.hands.ToolRequest;
import com.jwcode.core.llm.LLMQueryEngine;
import com.jwcode.core.workflow.EffectVM;
import com.jwcode.core.workflow.WorkflowArtifactStore;
import com.jwcode.core.workflow.WorkflowBudget;
import com.jwcode.core.workflow.WorkflowIR;
import com.jwcode.core.workflow.WorkflowInput;
import com.jwcode.core.workflow.WorkflowLedger;
import com.jwcode.core.workflow.WorkflowResult;
import com.jwcode.core.workflow.WorkflowRun;
import com.jwcode.core.workflow.WorkflowStatus;
import com.jwcode.core.workflow.ir.AgentNode;
import com.jwcode.core.workflow.ir.PhaseNode;
import com.jwcode.core.workflow.ir.PipelineNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultAgentRuntime implements AgentRuntime {
    private final LLMQueryEngine queryEngine;
    private final Path workflowRoot;
    private final Hand<AgentRequest, AgentResult> agentHand;
    private final Hand<ToolRequest, ToolEffectResult> toolHand;
    private final Map<String, WorkflowIR> workflowDefinitions = new ConcurrentHashMap<>();

    public DefaultAgentRuntime(LLMQueryEngine queryEngine, Path workflowRoot,
                               Hand<AgentRequest, AgentResult> agentHand,
                               Hand<ToolRequest, ToolEffectResult> toolHand) {
        this.queryEngine = queryEngine;
        this.workflowRoot = workflowRoot == null
            ? Path.of(System.getProperty("user.home"), ".jwcode", "workflows")
            : workflowRoot;
        this.agentHand = agentHand;
        this.toolHand = toolHand;
    }

    @Override
    public RuntimeResult handleChat(String sessionId, String message) {
        if (isComplex(message)) {
            ObjectNode payload = JsonNodeFactory.instance.objectNode();
            payload.put("message", message);
            return RuntimeResult.workflow(startWorkflow(sessionId, WorkflowInput.of(sessionId, payload)));
        }
        if (queryEngine == null) {
            return RuntimeResult.chat(message);
        }
        try {
            LLMQueryEngine.QueryResult result = queryEngine.query(message).get();
            if (!result.isSuccess()) {
                return RuntimeResult.error(RuntimeMode.CHAT, result.getErrorMessage());
            }
            return RuntimeResult.chat(result.getMessage() == null ? "" : result.getMessage().getTextContent());
        } catch (Exception e) {
            return RuntimeResult.error(RuntimeMode.CHAT, e.getMessage());
        }
    }

    @Override
    public RuntimeResult handlePlan(String sessionId, String message) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("message", message);
        payload.put("mode", "plan");
        return RuntimeResult.workflow(startWorkflow(sessionId, WorkflowInput.of(sessionId, payload)));
    }

    @Override
    public WorkflowRun startWorkflow(String sessionId, WorkflowInput input) {
        String runId = UUID.randomUUID().toString();
        WorkflowIR ir = defaultWorkflow(runId);
        workflowDefinitions.put(runId, ir);
        WorkflowResult result = runWorkflow(runId, ir, input);
        return run(runId, sessionId, result.status());
    }

    @Override
    public WorkflowRun resumeWorkflow(String runId) {
        WorkflowIR ir = workflowDefinitions.get(runId);
        if (ir == null) {
            return run(runId, null, getWorkflowStatus(runId));
        }
        WorkflowInput input = WorkflowInput.of(runId, JsonNodeFactory.instance.objectNode());
        WorkflowResult result = runWorkflow(runId, ir, input);
        return run(runId, input.sessionId(), result.status());
    }

    @Override
    public void cancelWorkflow(String runId) {
        WorkflowLedger ledger = ledger(runId);
        ledger.append("run.cancelled", Map.of());
    }

    @Override
    public WorkflowStatus getWorkflowStatus(String runId) {
        Path dir = workflowRoot.resolve(runId);
        if (!Files.exists(dir.resolve("events.jsonl"))) {
            return WorkflowStatus.CREATED;
        }
        return new WorkflowLedger(runId, dir).replayState().status();
    }

    private WorkflowResult runWorkflow(String runId, WorkflowIR ir, WorkflowInput input) {
        Path runDir = workflowRoot.resolve(runId);
        WorkflowLedger ledger = new WorkflowLedger(runId, runDir);
        EffectVM vm = new EffectVM(ledger, new WorkflowArtifactStore(runDir), agentHand, toolHand);
        return vm.execute(runId, ir, input);
    }

    private WorkflowLedger ledger(String runId) {
        return new WorkflowLedger(runId, workflowRoot.resolve(runId));
    }

    private WorkflowRun run(String runId, String sessionId, WorkflowStatus status) {
        Instant now = Instant.now();
        return new WorkflowRun(runId, sessionId, status, workflowRoot.resolve(runId), now, now);
    }

    private static boolean isComplex(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.length() > 500
            || lower.contains("refactor")
            || lower.contains("workflow")
            || lower.contains("implement")
            || lower.contains("plan")
            || lower.contains("test");
    }

    private static WorkflowIR defaultWorkflow(String id) {
        return new WorkflowIR(
            id,
            new PipelineNode("pipeline", List.of(
                new PhaseNode("explore", "explore", List.of(
                    new AgentNode("explorer", "explorer", "Explore the user request and repository context.", List.of(), null, 0, 0))),
                new PhaseNode("code", "code", List.of(
                    new AgentNode("coder", "coder", "Implement the required changes.", List.of(), null, 0, 0))),
                new PhaseNode("verify", "verify", List.of(
                    new AgentNode("verifier", "verifier", "Verify the implementation and summarize risks.", List.of(), null, 0, 0)))
            ), null),
            WorkflowBudget.defaults(),
            "workflow-ir.v1");
    }
}

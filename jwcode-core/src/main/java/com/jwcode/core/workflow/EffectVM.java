package com.jwcode.core.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.hands.AgentRequest;
import com.jwcode.core.hands.AgentResult;
import com.jwcode.core.hands.Hand;
import com.jwcode.core.hands.HandContext;
import com.jwcode.core.hands.LocalAgentHand;
import com.jwcode.core.hands.ToolEffectResult;
import com.jwcode.core.hands.ToolRequest;
import com.jwcode.core.memory.Checkpoint;
import com.jwcode.core.memory.MemoryCheckpointTrigger;
import com.jwcode.core.memory.MemoryLayer;
import com.jwcode.core.workflow.ir.AgentNode;
import com.jwcode.core.workflow.ir.ConditionNode;
import com.jwcode.core.workflow.ir.ErrorMode;
import com.jwcode.core.workflow.ir.LoopNode;
import com.jwcode.core.workflow.ir.ParallelNode;
import com.jwcode.core.workflow.ir.PhaseNode;
import com.jwcode.core.workflow.ir.PipelineNode;
import com.jwcode.core.workflow.ir.SynthesizeNode;
import com.jwcode.core.workflow.ir.ToolNode;
import com.jwcode.core.workflow.ir.WorkflowNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class EffectVM {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkflowLedger ledger;
    private final WorkflowArtifactStore artifactStore;
    private final Hand<AgentRequest, AgentResult> agentHand;
    private final Hand<ToolRequest, ToolEffectResult> toolHand;
    private final MemoryLayer memoryLayer;
    private final MemoryCheckpointTrigger checkpointTrigger;

    public EffectVM(WorkflowLedger ledger, WorkflowArtifactStore artifactStore,
                    Hand<AgentRequest, AgentResult> agentHand,
                    Hand<ToolRequest, ToolEffectResult> toolHand) {
        this(ledger, artifactStore, agentHand, toolHand, null, new MemoryCheckpointTrigger());
    }

    public EffectVM(WorkflowLedger ledger, WorkflowArtifactStore artifactStore,
                    Hand<AgentRequest, AgentResult> agentHand,
                    Hand<ToolRequest, ToolEffectResult> toolHand,
                    MemoryLayer memoryLayer) {
        this(ledger, artifactStore, agentHand, toolHand, memoryLayer, new MemoryCheckpointTrigger());
    }

    public EffectVM(WorkflowLedger ledger, WorkflowArtifactStore artifactStore,
                    Hand<AgentRequest, AgentResult> agentHand,
                    Hand<ToolRequest, ToolEffectResult> toolHand,
                    MemoryLayer memoryLayer,
                    MemoryCheckpointTrigger checkpointTrigger) {
        this.ledger = ledger;
        this.artifactStore = artifactStore;
        this.agentHand = agentHand == null ? new LocalAgentHand() : agentHand;
        this.toolHand = toolHand;
        this.memoryLayer = memoryLayer;
        this.checkpointTrigger = checkpointTrigger == null ? new MemoryCheckpointTrigger() : checkpointTrigger;
    }

    public WorkflowResult execute(String runId, WorkflowIR ir, WorkflowInput input) {
        WorkflowShape shape = WorkflowValidator.validate(ir);
        ledger.setWorkflowProgressModel(shape, ir.budget());
        WorkflowState state = ledger.replayState();
        try {
            if (state.status() == WorkflowStatus.CANCELLED && !input.forceResume()) {
                return WorkflowResult.failed(runId, WorkflowStatus.CANCELLED, "Cannot resume cancelled workflow without forceResume=true");
            }
            if (state.status() == WorkflowStatus.CREATED) {
                ledger.append("run.started", Map.of("workflowId", ir.id(), "sessionId", input.sessionId()));
            } else {
                ledger.append("run.resumed", Map.of("workflowId", ir.id(), "sessionId", input.sessionId()));
            }
            JsonNode output = executeNode(runId, ir, ir.root(), input.payload(), input, ledger.replayState(), ir.budget());
            ledger.append("run.finished", Map.of("workflowId", ir.id()));
            writeCheckpoint(input, ir, "run.finished");
            WorkflowState finalState = ledger.replayState();
            finalState.lastOutput(output);
            ledger.saveState(finalState);
            return WorkflowResult.success(runId, output);
        } catch (WorkflowCancelledException e) {
            return WorkflowResult.failed(runId, WorkflowStatus.CANCELLED, e.getMessage());
        } catch (WorkflowPausedException e) {
            return WorkflowResult.failed(runId, WorkflowStatus.PAUSED, e.getMessage());
        } catch (WorkflowBudgetExceededException e) {
            ledger.append("budget.exceeded", Map.of("error", e.getMessage()));
            return WorkflowResult.failed(runId, e.status(), e.getMessage());
        } catch (Exception e) {
            ledger.append("run.failed", Map.of("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            return WorkflowResult.failed(runId, WorkflowStatus.FAILED, e.getMessage());
        }
    }

    public WorkflowResult resume(String runId, WorkflowIR ir, WorkflowInput input) {
        return execute(runId, ir, input);
    }

    private JsonNode executeNode(String runId, WorkflowIR ir, WorkflowNode node, JsonNode nodeInput,
                                 WorkflowInput workflowInput, WorkflowState state, WorkflowBudget budget) throws Exception {
        checkRunnable();
        if (node instanceof AgentNode agentNode) {
            return executeAgent(runId, ir, agentNode, nodeInput, workflowInput, state, budget);
        }
        if (node instanceof ToolNode toolNode) {
            return executeTool(runId, ir, toolNode, nodeInput, workflowInput, state, budget);
        }
        if (node instanceof ParallelNode parallelNode) {
            return executeParallel(runId, ir, parallelNode, nodeInput, workflowInput, budget);
        }
        if (node instanceof PipelineNode pipelineNode) {
            JsonNode current = nodeInput;
            ArrayNode outputs = MAPPER.createArrayNode();
            for (WorkflowNode step : pipelineNode.steps()) {
                checkRunnable();
                current = executeNode(runId, ir, step, current, workflowInput, ledger.replayState(), budget);
                outputs.add(current);
            }
            return outputs;
        }
        if (node instanceof PhaseNode phaseNode) {
            ledger.append("phase.started", Map.of("phaseId", phaseNode.id(), "name", phaseNode.name()));
            JsonNode current = nodeInput;
            ArrayNode outputs = MAPPER.createArrayNode();
            for (WorkflowNode child : phaseNode.body()) {
                checkRunnable();
                current = executeNode(runId, ir, child, current, workflowInput, ledger.replayState(), budget);
                outputs.add(current);
            }
            ledger.append("phase.completed", Map.of("phaseId", phaseNode.id(), "name", phaseNode.name()));
            writeCheckpoint(workflowInput, ir, "phase:" + phaseNode.id());
            ledger.saveState(ledger.replayState());
            return outputs;
        }
        if (node instanceof ConditionNode conditionNode) {
            JsonNode conditionValue = pointer(nodeInput, conditionNode.inputPointer());
            ledger.append("condition.evaluated", Map.of("nodeId", conditionNode.id(), "value", conditionValue.asBoolean(false)));
            WorkflowNode branch = conditionValue.asBoolean(false) ? conditionNode.thenNode() : conditionNode.elseNode();
            return branch == null ? JsonNodeFactory.instance.nullNode()
                : executeNode(runId, ir, branch, nodeInput, workflowInput, ledger.replayState(), budget);
        }
        if (node instanceof LoopNode loopNode) {
            JsonNode current = nodeInput;
            int limit = Math.min(loopNode.maxIterations(), budget.maxLoopIterations());
            for (int i = 0; i < limit; i++) {
                checkRunnable();
                if (pointer(current, loopNode.donePointer()).asBoolean(false)) {
                    return current;
                }
                ledger.append("loop.iteration", Map.of("nodeId", loopNode.id(), "iteration", i + 1));
                current = executeNode(runId, ir, loopNode.body(), current, workflowInput, ledger.replayState(), budget);
            }
            throw new WorkflowBudgetExceededException("Loop exceeded max iterations for node " + loopNode.id());
        }
        if (node instanceof SynthesizeNode synthesizeNode) {
            ObjectNode output = MAPPER.createObjectNode();
            output.put("nodeId", synthesizeNode.id());
            output.put("prompt", synthesizeNode.prompt());
            output.set("input", nodeInput);
            return output;
        }
        throw new IllegalArgumentException("Unsupported workflow node: " + node.getClass().getName());
    }

    private JsonNode executeAgent(String runId, WorkflowIR ir, AgentNode node, JsonNode nodeInput,
                                  WorkflowInput workflowInput, WorkflowState state, WorkflowBudget budget) throws Exception {
        checkRunnable();
        checkBudget(state, budget, true);
        String effectId = EffectId.explicit(runId, node.id());
        var cached = state.completedEffect(effectId);
        if (cached.isPresent()) {
            ledger.append("effect.cache_hit", Map.of("effectId", effectId, "nodeId", node.id(), "kind", "agent"));
            return artifactStore.readJson(cached.get().artifactRef());
        }

        ledger.append("effect.scheduled", Map.of("effectId", effectId, "nodeId", node.id(), "kind", "agent", "role", node.role()));
        AgentResult result = agentHand.execute(
            new AgentRequest(node.role(), node.prompt(), node.tools(), node.schema(), nodeInput),
            new HandContext(runId, effectId, workflowInput.sessionId(), workflowInput, state, node.tools()));
        if (!result.success()) {
            String error = errorText(result.errorMessage());
            ledger.append("effect.failed", Map.of("effectId", effectId, "nodeId", node.id(), "kind", "agent", "error", error));
            throw new IllegalStateException(error);
        }
        JsonNode output = MAPPER.valueToTree(result);
        String artifactRef = artifactStore.writeJson(effectId, output);
        ledger.append("artifact.written", Map.of("effectId", effectId, "artifactRef", artifactRef));
        ledger.append("effect.completed", Map.of(
            "effectId", effectId,
            "nodeId", node.id(),
            "kind", "agent",
            "artifactRef", artifactRef,
            "tokens", result.tokenUsage(),
            "durationMs", result.durationMs()));
        maybeWriteTokenCheckpoint(workflowInput, ir, "effect:" + node.id());
        return output;
    }

    private JsonNode executeTool(String runId, WorkflowIR ir, ToolNode node, JsonNode nodeInput,
                                 WorkflowInput workflowInput, WorkflowState state, WorkflowBudget budget) throws Exception {
        checkRunnable();
        checkBudget(state, budget, false);
        JsonNode effectiveInput = node.input() == null ? nodeInput : node.input();
        String effectId = EffectId.explicit(runId, node.id());
        var cached = state.completedEffect(effectId);
        if (cached.isPresent()) {
            ledger.append("effect.cache_hit", Map.of("effectId", effectId, "nodeId", node.id(), "kind", "tool"));
            return artifactStore.readJson(cached.get().artifactRef());
        }
        if (toolHand == null) {
            throw new IllegalStateException("ToolHand is not configured");
        }

        ledger.append("effect.scheduled", Map.of("effectId", effectId, "nodeId", node.id(), "kind", "tool", "toolName", node.toolName()));
        ToolEffectResult result = toolHand.execute(
            new ToolRequest(node.toolName(), effectiveInput),
            new HandContext(runId, effectId, workflowInput.sessionId(), workflowInput, state, List.of(node.toolName())));
        if (!result.success()) {
            String error = errorText(result.errorMessage());
            ledger.append("effect.failed", Map.of("effectId", effectId, "nodeId", node.id(), "kind", "tool", "error", error));
            throw new IllegalStateException(error);
        }
        JsonNode output = MAPPER.valueToTree(result);
        String artifactRef = artifactStore.writeJson(effectId, output);
        ledger.append("artifact.written", Map.of("effectId", effectId, "artifactRef", artifactRef));
        ledger.append("effect.completed", Map.of(
            "effectId", effectId,
            "nodeId", node.id(),
            "kind", "tool",
            "artifactRef", artifactRef,
            "durationMs", result.durationMs()));
        maybeWriteTokenCheckpoint(workflowInput, ir, "effect:" + node.id());
        return output;
    }

    private JsonNode executeParallel(String runId, WorkflowIR ir, ParallelNode node, JsonNode nodeInput,
                                     WorkflowInput workflowInput, WorkflowBudget budget) throws Exception {
        if (node.concurrency() > budget.maxParallelism()) {
            throw new WorkflowBudgetExceededException("Parallel concurrency exceeds workflow budget for node " + node.id());
        }
        checkRunnable();
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, node.concurrency()));
        try {
            List<Callable<JsonNode>> tasks = new ArrayList<>();
            for (WorkflowNode branch : node.branches()) {
                tasks.add(() -> executeNode(runId, ir, branch, nodeInput, workflowInput, ledger.replayState(), budget));
            }
            List<Future<JsonNode>> futures = executor.invokeAll(tasks);
            ArrayNode outputs = MAPPER.createArrayNode();
            for (Future<JsonNode> future : futures) {
                checkRunnable();
                try {
                    outputs.add(future.get());
                } catch (Exception e) {
                    if (node.errorMode() == ErrorMode.FAIL_FAST || node.errorMode() == ErrorMode.REQUIRED) {
                        throw e;
                    }
                    outputs.add(JsonNodeFactory.instance.nullNode());
                }
            }
            return outputs;
        } finally {
            executor.shutdownNow();
        }
    }

    private void checkRunnable() {
        if (Thread.currentThread().isInterrupted() || ledger.isCancelled()) {
            throw new WorkflowCancelledException("Workflow run was cancelled");
        }
        if (ledger.isPaused()) {
            throw new WorkflowPausedException("Workflow run was paused");
        }
    }

    private void maybeWriteTokenCheckpoint(WorkflowInput workflowInput, WorkflowIR ir, String reason) {
        if (memoryLayer == null || !workflowInput.memoryEnabled()) {
            return;
        }
        WorkflowState state = ledger.replayState();
        long maxTokens = ir.budget().maxTokens();
        if (maxTokens <= 0 || maxTokens == Long.MAX_VALUE) {
            return;
        }
        double ratio = (double) state.tokensUsed() / (double) maxTokens;
        if (checkpointTrigger.shouldTrigger(workflowInput.sessionId(), ratio)) {
            writeCheckpoint(workflowInput, ir, reason + ":token-threshold");
        }
    }

    private void writeCheckpoint(WorkflowInput workflowInput, WorkflowIR ir, String reason) {
        if (memoryLayer == null || !workflowInput.memoryEnabled()) {
            return;
        }
        WorkflowState state = ledger.replayState();
        Checkpoint checkpoint = new Checkpoint(
            textAt(workflowInput.payload(), "/message"),
            "Continue workflow from " + reason,
            "Use Workflow IR + EffectVM; preserve ToolExecutor, permission, and hook boundaries.",
            List.of(),
            "Workflow " + ir.id() + " is " + state.status(),
            involvedFiles(workflowInput.payload()),
            "",
            "",
            "runId=" + state.runId()
                + ", status=" + state.status()
                + ", effects=" + state.completedEffectsCount()
                + ", phases=" + state.completedPhasesCount()
                + ", tokens=" + state.tokensUsed(),
            "JSON IR only; do not execute JS-like workflow text.",
            "checkpointReason=" + reason);
        memoryLayer.writeCheckpoint(workflowInput.sessionId(), checkpoint);
        ledger.append("memory.checkpoint_saved", Map.of("sessionId", workflowInput.sessionId(), "reason", reason));
    }

    private static List<String> involvedFiles(JsonNode payload) {
        JsonNode files = payload == null ? null : payload.at("/files");
        if (files == null || !files.isArray()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        files.forEach(file -> {
            if (file.isTextual() && !file.asText().isBlank()) {
                out.add(file.asText());
            }
        });
        return List.copyOf(out);
    }

    private static String textAt(JsonNode node, String pointer) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.at(pointer);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private void checkBudget(WorkflowState state, WorkflowBudget budget, boolean agentCall) {
        Instant startedAt = state.startedAt() == null ? Instant.now() : state.startedAt();
        if (Duration.between(startedAt, Instant.now()).compareTo(budget.maxWallTime()) > 0) {
            throw new WorkflowBudgetExceededException("Workflow wall-time budget exceeded");
        }
        if (agentCall && state.agentCalls() >= budget.maxAgentCalls()) {
            throw new WorkflowBudgetExceededException("Workflow agent-call budget exceeded");
        }
        if (!agentCall && state.toolCalls() >= budget.maxToolCalls()) {
            throw new WorkflowBudgetExceededException("Workflow tool-call budget exceeded");
        }
        if (state.tokensUsed() >= budget.maxTokens()) {
            if (budget.tokenPolicy() == TokenPolicy.WARN) {
                ledger.append("budget.warn", Map.of("resource", "tokens", "used", state.tokensUsed(), "max", budget.maxTokens()));
                return;
            }
            WorkflowStatus status = budget.tokenPolicy() == TokenPolicy.PAUSE_ASK
                ? WorkflowStatus.PAUSED_BUDGET
                : WorkflowStatus.FAILED_BUDGET;
            throw new WorkflowBudgetExceededException("Workflow token budget exceeded", status);
        }
    }

    private static JsonNode pointer(JsonNode input, String pointer) {
        if (input == null || pointer == null || pointer.isBlank()) {
            return JsonNodeFactory.instance.nullNode();
        }
        JsonNode value = input.at(pointer);
        return value.isMissingNode() ? JsonNodeFactory.instance.nullNode() : value;
    }

    private static String errorText(String error) {
        return error == null || error.isBlank() ? "Workflow effect failed" : error;
    }

    private static class WorkflowCancelledException extends RuntimeException {
        WorkflowCancelledException(String message) {
            super(message);
        }
    }

    private static class WorkflowPausedException extends RuntimeException {
        WorkflowPausedException(String message) {
            super(message);
        }
    }
}

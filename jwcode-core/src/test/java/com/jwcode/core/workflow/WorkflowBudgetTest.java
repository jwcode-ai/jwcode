package com.jwcode.core.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.hands.AgentResult;
import com.jwcode.core.hands.LocalAgentHand;
import com.jwcode.core.workflow.ir.AgentNode;
import com.jwcode.core.workflow.ir.PipelineNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowBudgetTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void agentCallLimitFailsBudget() {
        WorkflowBudget budget = new WorkflowBudget(1_000, 1, 10, Duration.ofMinutes(1), 4, 4);
        WorkflowIR ir = new WorkflowIR("wf", new PipelineNode("pipeline", List.of(
            new AgentNode("a", "explorer", "a", List.of(), null, 0, 0),
            new AgentNode("b", "coder", "b", List.of(), null, 0, 0)
        ), null), budget, "v1");
        LocalAgentHand hand = new LocalAgentHand(request ->
            AgentResult.success(request.role(), request.prompt(), mapper.createObjectNode(), 0, 1));

        Path runDir = tempDir.resolve("run");
        EffectVM vm = new EffectVM(new WorkflowLedger("run", runDir), new WorkflowArtifactStore(runDir), hand, null);
        WorkflowResult result = vm.execute("run", ir, WorkflowInput.of("session", mapper.createObjectNode()));

        assertEquals(WorkflowStatus.FAILED_BUDGET, result.status());
    }
}

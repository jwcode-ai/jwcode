package com.jwcode.core.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.hands.AgentResult;
import com.jwcode.core.hands.LocalAgentHand;
import com.jwcode.core.workflow.ir.AgentNode;
import com.jwcode.core.workflow.ir.PhaseNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowEventProgressTest {
    @TempDir
    Path tempDir;

    @Test
    void eventsCarryStaticProgressAndResourceFields() {
        ObjectMapper mapper = new ObjectMapper();
        WorkflowIR ir = new WorkflowIR("wf", new PhaseNode("phase", "phase", List.of(
            new AgentNode("a", "explorer", "a", List.of(), null, 0, 0),
            new AgentNode("b", "explorer", "b", List.of(), null, 0, 0)
        )), WorkflowBudget.defaults(), "v1");
        LocalAgentHand hand = new LocalAgentHand(request ->
            AgentResult.success(request.role(), request.prompt(), mapper.createObjectNode(), 7, 1));
        Path runDir = tempDir.resolve("run");
        WorkflowLedger ledger = new WorkflowLedger("run", runDir);

        new EffectVM(ledger, new WorkflowArtifactStore(runDir), hand, null)
            .execute("run", ir, WorkflowInput.of("session", mapper.createObjectNode()));

        WorkflowEvent lastCompleted = ledger.replay().stream()
            .filter(event -> "effect.completed".equals(event.type()))
            .reduce((first, second) -> second)
            .orElseThrow();

        assertEquals(2, lastCompleted.completedEffects());
        assertEquals(2, lastCompleted.totalEffects());
        assertEquals(1, lastCompleted.totalPhases());
        assertEquals(14, lastCompleted.tokensUsed());
    }
}

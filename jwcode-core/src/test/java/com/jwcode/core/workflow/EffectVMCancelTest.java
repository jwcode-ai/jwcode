package com.jwcode.core.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.hands.AgentResult;
import com.jwcode.core.hands.LocalAgentHand;
import com.jwcode.core.workflow.ir.AgentNode;
import com.jwcode.core.workflow.ir.PipelineNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EffectVMCancelTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void cancelledRunDoesNotScheduleNewEffects() {
        AtomicInteger calls = new AtomicInteger();
        LocalAgentHand hand = new LocalAgentHand(request -> {
            calls.incrementAndGet();
            return AgentResult.success(request.role(), request.prompt(), mapper.createObjectNode(), 0, 1);
        });
        WorkflowIR ir = new WorkflowIR("wf", new AgentNode("a", "explorer", "a", List.of(), null, 0, 0),
            WorkflowBudget.defaults(), "v1");
        Path runDir = tempDir.resolve("run");
        WorkflowLedger ledger = new WorkflowLedger("run", runDir);
        ledger.append("run.cancelled", Map.of());

        WorkflowResult result = new EffectVM(ledger, new WorkflowArtifactStore(runDir), hand, null)
            .resume("run", ir, WorkflowInput.of("session", mapper.createObjectNode()));

        assertEquals(WorkflowStatus.CANCELLED, result.status());
        assertEquals(0, calls.get());
    }

    @Test
    void cancellationAfterCurrentEffectStopsNextEffect() {
        AtomicInteger calls = new AtomicInteger();
        Path runDir = tempDir.resolve("mid-run");
        WorkflowLedger ledger = new WorkflowLedger("run", runDir);
        LocalAgentHand hand = new LocalAgentHand(request -> {
            calls.incrementAndGet();
            ledger.append("run.cancelled", Map.of());
            return AgentResult.success(request.role(), request.prompt(), mapper.createObjectNode(), 0, 1);
        });
        WorkflowIR ir = new WorkflowIR("wf", new PipelineNode("pipeline", List.of(
            new AgentNode("a", "explorer", "a", List.of(), null, 0, 0),
            new AgentNode("b", "explorer", "b", List.of(), null, 0, 0)
        ), null), WorkflowBudget.defaults(), "v1");

        WorkflowResult result = new EffectVM(ledger, new WorkflowArtifactStore(runDir), hand, null)
            .execute("run", ir, WorkflowInput.of("session", mapper.createObjectNode()));

        assertEquals(WorkflowStatus.CANCELLED, result.status());
        assertEquals(1, calls.get());
        assertEquals(WorkflowStatus.CANCELLED, ledger.replayState().status());
    }
}

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

class EffectVMPauseTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void pauseAfterCurrentEffectStopsNextEffect() {
        AtomicInteger calls = new AtomicInteger();
        Path runDir = tempDir.resolve("pause-run");
        WorkflowLedger ledger = new WorkflowLedger("run", runDir);
        LocalAgentHand hand = new LocalAgentHand(request -> {
            calls.incrementAndGet();
            ledger.append("run.paused", Map.of());
            return AgentResult.success(request.role(), request.prompt(), mapper.createObjectNode(), 0, 1);
        });
        WorkflowIR ir = new WorkflowIR("wf", new PipelineNode("pipeline", List.of(
            new AgentNode("a", "explorer", "a", List.of(), null, 0, 0),
            new AgentNode("b", "explorer", "b", List.of(), null, 0, 0)
        ), null), WorkflowBudget.defaults(), "v1");

        WorkflowResult result = new EffectVM(ledger, new WorkflowArtifactStore(runDir), hand, null)
            .execute("run", ir, WorkflowInput.of("session", mapper.createObjectNode()));

        assertEquals(WorkflowStatus.PAUSED, result.status());
        assertEquals(1, calls.get());
        assertEquals(WorkflowStatus.PAUSED, ledger.replayState().status());
    }

    @Test
    void pausedRunCanResumeAndSkipCompletedEffects() {
        AtomicInteger calls = new AtomicInteger();
        Path runDir = tempDir.resolve("resume-paused-run");
        WorkflowLedger ledger = new WorkflowLedger("run", runDir);
        LocalAgentHand pauseAfterFirst = new LocalAgentHand(request -> {
            calls.incrementAndGet();
            ledger.append("run.paused", Map.of());
            return AgentResult.success(request.role(), request.prompt(), mapper.createObjectNode(), 0, 1);
        });
        WorkflowIR ir = new WorkflowIR("wf", new PipelineNode("pipeline", List.of(
            new AgentNode("a", "explorer", "a", List.of(), null, 0, 0),
            new AgentNode("b", "explorer", "b", List.of(), null, 0, 0)
        ), null), WorkflowBudget.defaults(), "v1");

        new EffectVM(ledger, new WorkflowArtifactStore(runDir), pauseAfterFirst, null)
            .execute("run", ir, WorkflowInput.of("session", mapper.createObjectNode()));

        LocalAgentHand finishRemaining = new LocalAgentHand(request -> {
            calls.incrementAndGet();
            return AgentResult.success(request.role(), request.prompt(), mapper.createObjectNode(), 0, 1);
        });
        WorkflowResult result = new EffectVM(new WorkflowLedger("run", runDir), new WorkflowArtifactStore(runDir), finishRemaining, null)
            .resume("run", ir, WorkflowInput.of("session", mapper.createObjectNode()));

        assertEquals(WorkflowStatus.COMPLETED, result.status());
        assertEquals(2, calls.get());
    }
}

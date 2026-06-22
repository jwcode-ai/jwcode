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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EffectVMResumeTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void resumeSkipsCompletedEffects() {
        AtomicInteger calls = new AtomicInteger();
        LocalAgentHand hand = new LocalAgentHand(request -> {
            calls.incrementAndGet();
            return AgentResult.success(request.role(), request.prompt(), mapper.createObjectNode().put("role", request.role()), 0, 1);
        });
        WorkflowIR ir = new WorkflowIR("wf", new PipelineNode("pipeline", List.of(
            new AgentNode("explore", "explorer", "explore", List.of(), null, 0, 0),
            new AgentNode("verify", "verifier", "verify", List.of(), null, 0, 0)
        ), null), WorkflowBudget.defaults(), "v1");

        Path runDir = tempDir.resolve("run");
        EffectVM vm = new EffectVM(new WorkflowLedger("run", runDir), new WorkflowArtifactStore(runDir), hand, null);
        vm.execute("run", ir, WorkflowInput.of("session", mapper.createObjectNode()));
        assertEquals(2, calls.get());

        EffectVM resumed = new EffectVM(new WorkflowLedger("run", runDir), new WorkflowArtifactStore(runDir), hand, null);
        resumed.resume("run", ir, WorkflowInput.of("session", mapper.createObjectNode()));
        assertEquals(2, calls.get());
    }

    @Test
    void resumeUsesExplicitNodeIdEvenWhenInputChanges() {
        AtomicInteger calls = new AtomicInteger();
        LocalAgentHand hand = new LocalAgentHand(request -> {
            calls.incrementAndGet();
            return AgentResult.success(request.role(), request.input().toString(), mapper.createObjectNode(), 0, 1);
        });
        WorkflowIR ir = new WorkflowIR("wf",
            new AgentNode("stable-node-id", "explorer", "explore", List.of(), null, 0, 0),
            WorkflowBudget.defaults(),
            "v1");

        Path runDir = tempDir.resolve("dynamic-input-run");
        EffectVM vm = new EffectVM(new WorkflowLedger("run", runDir), new WorkflowArtifactStore(runDir), hand, null);
        vm.execute("run", ir, WorkflowInput.of("session", mapper.createObjectNode().put("timestamp", 1)));
        assertEquals(1, calls.get());

        EffectVM resumed = new EffectVM(new WorkflowLedger("run", runDir), new WorkflowArtifactStore(runDir), hand, null);
        resumed.resume("run", ir, WorkflowInput.of("session", mapper.createObjectNode().put("timestamp", 2)));
        assertEquals(1, calls.get());
    }
}

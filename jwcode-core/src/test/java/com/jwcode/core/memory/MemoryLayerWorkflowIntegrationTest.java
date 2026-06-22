package com.jwcode.core.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.hands.AgentResult;
import com.jwcode.core.hands.LocalAgentHand;
import com.jwcode.core.workflow.EffectVM;
import com.jwcode.core.workflow.WorkflowArtifactStore;
import com.jwcode.core.workflow.WorkflowBudget;
import com.jwcode.core.workflow.WorkflowIR;
import com.jwcode.core.workflow.WorkflowInput;
import com.jwcode.core.workflow.WorkflowLedger;
import com.jwcode.core.workflow.ir.AgentNode;
import com.jwcode.core.workflow.ir.PhaseNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryLayerWorkflowIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void phaseCompletionWritesCheckpoint() {
        ObjectMapper mapper = new ObjectMapper();
        FileMemoryLayer memory = new FileMemoryLayer(tempDir.resolve("memory"));
        WorkflowIR ir = new WorkflowIR("wf", new PhaseNode("phase", "phase", List.of(
            new AgentNode("a", "explorer", "a", List.of(), null, 0, 0)
        )), WorkflowBudget.defaults(), "v1");
        Path runDir = tempDir.resolve("run");
        LocalAgentHand hand = new LocalAgentHand(request ->
            AgentResult.success(request.role(), request.prompt(), mapper.createObjectNode(), 0, 1));
        WorkflowInput input = new WorkflowInput("session", mapper.createObjectNode().put("message", "do work"),
            Map.of("memoryEnabled", true));

        new EffectVM(new WorkflowLedger("run", runDir), new WorkflowArtifactStore(runDir), hand, null, memory)
            .execute("run", ir, input);

        Checkpoint checkpoint = memory.readCheckpoint("session");
        assertEquals("do work", checkpoint.intent());
        assertTrue(checkpoint.runtimeState().contains("status=COMPLETED"));
        assertTrue(Files.exists(tempDir.resolve("memory").resolve("sessions").resolve("session").resolve("checkpoint.json")));
    }

    @Test
    void tokenThresholdWritesCheckpoint() {
        ObjectMapper mapper = new ObjectMapper();
        FileMemoryLayer memory = new FileMemoryLayer(tempDir.resolve("memory-token"));
        WorkflowIR ir = new WorkflowIR("wf",
            new AgentNode("a", "explorer", "a", List.of(), null, 0, 0),
            new WorkflowBudget(100, 10, 10, Duration.ofMinutes(1), 2, 2),
            "v1");
        Path runDir = tempDir.resolve("token-run");
        WorkflowLedger ledger = new WorkflowLedger("run", runDir);
        LocalAgentHand hand = new LocalAgentHand(request ->
            AgentResult.success(request.role(), request.prompt(), mapper.createObjectNode(), 45, 1));

        new EffectVM(ledger, new WorkflowArtifactStore(runDir), hand, null, memory)
            .execute("run", ir, new WorkflowInput("session", mapper.createObjectNode(), Map.of("memoryEnabled", true)));

        assertTrue(ledger.replay().stream()
            .anyMatch(event -> "memory.checkpoint_saved".equals(event.type())
                && String.valueOf(event.data().get("reason")).contains("token-threshold")));
    }
}

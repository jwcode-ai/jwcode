package com.jwcode.core.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.hands.AgentResult;
import com.jwcode.core.hands.LocalAgentHand;
import com.jwcode.core.workflow.ir.AgentNode;
import com.jwcode.core.workflow.ir.ErrorMode;
import com.jwcode.core.workflow.ir.ParallelNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelNullSemanticsTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void partialFailureReturnsNullAndKeepsOrder() {
        LocalAgentHand hand = new LocalAgentHand(request -> {
            if (request.prompt().contains("fail")) {
                return AgentResult.failure(request.role(), "expected failure", 1);
            }
            return AgentResult.success(request.role(), request.prompt(), mapper.createObjectNode().put("prompt", request.prompt()), 0, 1);
        });
        WorkflowIR ir = new WorkflowIR("wf", new ParallelNode("parallel", List.of(
            new AgentNode("a", "explorer", "ok-a", List.of(), null, 0, 0),
            new AgentNode("b", "explorer", "fail-b", List.of(), null, 0, 0),
            new AgentNode("c", "explorer", "ok-c", List.of(), null, 0, 0)
        ), 3, ErrorMode.NULL), WorkflowBudget.defaults(), "v1");

        Path runDir = tempDir.resolve("run");
        EffectVM vm = new EffectVM(new WorkflowLedger("run", runDir), new WorkflowArtifactStore(runDir), hand, null);
        WorkflowResult result = vm.execute("run", ir, WorkflowInput.of("session", mapper.createObjectNode()));

        assertEquals(WorkflowStatus.COMPLETED, result.status());
        assertEquals("ok-a", result.output().get(0).get("content").asText());
        assertTrue(result.output().get(1).isNull());
        assertEquals("ok-c", result.output().get(2).get("content").asText());
    }
}

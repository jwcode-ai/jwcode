package com.jwcode.core.workflow;

import com.jwcode.core.workflow.ir.AgentNode;
import com.jwcode.core.workflow.ir.ParallelNode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowValidatorTest {
    @Test
    void rejectsDuplicateNodeIds() {
        WorkflowIR ir = new WorkflowIR("wf", new ParallelNode("parallel", List.of(
            new AgentNode("same", "explorer", "a", List.of(), null, 0, 0),
            new AgentNode("same", "explorer", "b", List.of(), null, 0, 0)
        ), 2, null), WorkflowBudget.defaults(), "v1");

        assertThrows(IllegalArgumentException.class, () -> WorkflowValidator.validate(ir));
    }

    @Test
    void countsEffectsAndPhases() {
        WorkflowIR ir = new WorkflowIR("wf", new ParallelNode("parallel", List.of(
            new AgentNode("a", "explorer", "a", List.of(), null, 0, 0),
            new AgentNode("b", "explorer", "b", List.of(), null, 0, 0)
        ), 2, null), WorkflowBudget.defaults(), "v1");

        WorkflowShape shape = WorkflowValidator.validate(ir);
        assertEquals(2, shape.totalEffects());
        assertEquals(0, shape.totalPhases());
    }

    @Test
    void rejectsParallelConcurrencyAboveBudget() {
        WorkflowIR ir = new WorkflowIR("wf", new ParallelNode("parallel", List.of(
            new AgentNode("a", "explorer", "a", List.of(), null, 0, 0),
            new AgentNode("b", "explorer", "b", List.of(), null, 0, 0)
        ), 2, null), new WorkflowBudget(1000, 10, 10, Duration.ofMinutes(1), 1, 10), "v1");

        assertThrows(IllegalArgumentException.class, () -> WorkflowValidator.validate(ir));
    }
}

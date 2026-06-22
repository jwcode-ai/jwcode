package com.jwcode.core.workflow;

import com.jwcode.core.workflow.ir.AgentNode;
import com.jwcode.core.workflow.ir.ConditionNode;
import com.jwcode.core.workflow.ir.LoopNode;
import com.jwcode.core.workflow.ir.ParallelNode;
import com.jwcode.core.workflow.ir.PhaseNode;
import com.jwcode.core.workflow.ir.PipelineNode;
import com.jwcode.core.workflow.ir.SynthesizeNode;
import com.jwcode.core.workflow.ir.ToolNode;
import com.jwcode.core.workflow.ir.WorkflowNode;

import java.util.HashSet;
import java.util.Set;

public final class WorkflowValidator {
    private WorkflowValidator() {
    }

    public static WorkflowShape validate(WorkflowIR ir) {
        if (ir == null || ir.root() == null) {
            throw new IllegalArgumentException("Workflow IR root is required");
        }
        Counter counter = new Counter();
        visit(ir.root(), new HashSet<>(), counter, ir.budget());
        return new WorkflowShape(counter.effects, counter.phases);
    }

    private static void visit(WorkflowNode node, Set<String> ids, Counter counter, WorkflowBudget budget) {
        if (node.id() == null || node.id().isBlank()) {
            throw new IllegalArgumentException("Workflow node id is required");
        }
        if (!ids.add(node.id())) {
            throw new IllegalArgumentException("Duplicate workflow node id: " + node.id());
        }
        if (node instanceof AgentNode || node instanceof ToolNode) {
            counter.effects++;
            return;
        }
        if (node instanceof PhaseNode phaseNode) {
            counter.phases++;
            phaseNode.body().forEach(child -> visit(child, ids, counter, budget));
        } else if (node instanceof ParallelNode parallelNode) {
            if (parallelNode.concurrency() > budget.maxParallelism()) {
                throw new IllegalArgumentException("Parallel concurrency exceeds workflow budget: " + parallelNode.id());
            }
            parallelNode.branches().forEach(child -> visit(child, ids, counter, budget));
        } else if (node instanceof PipelineNode pipelineNode) {
            pipelineNode.steps().forEach(child -> visit(child, ids, counter, budget));
        } else if (node instanceof ConditionNode conditionNode) {
            if (conditionNode.thenNode() != null) visit(conditionNode.thenNode(), ids, counter, budget);
            if (conditionNode.elseNode() != null) visit(conditionNode.elseNode(), ids, counter, budget);
        } else if (node instanceof LoopNode loopNode) {
            visit(loopNode.body(), ids, counter, budget);
        } else if (node instanceof SynthesizeNode) {
            counter.effects++;
        }
    }

    private static final class Counter {
        int effects;
        int phases;
    }
}

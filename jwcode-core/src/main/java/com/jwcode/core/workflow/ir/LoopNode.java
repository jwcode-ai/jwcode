package com.jwcode.core.workflow.ir;

public record LoopNode(
    String id,
    String donePointer,
    WorkflowNode body,
    int maxIterations
) implements WorkflowNode {
    public LoopNode {
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("LoopNode maxIterations must be positive");
        }
    }
}

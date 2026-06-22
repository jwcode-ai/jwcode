package com.jwcode.core.workflow.ir;

import java.util.List;

public record PipelineNode(
    String id,
    List<WorkflowNode> steps,
    ErrorMode errorMode
) implements WorkflowNode {
    public PipelineNode {
        steps = steps == null ? List.of() : List.copyOf(steps);
        errorMode = errorMode == null ? ErrorMode.FAIL_FAST : errorMode;
    }
}

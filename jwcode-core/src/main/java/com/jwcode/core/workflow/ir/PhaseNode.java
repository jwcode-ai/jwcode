package com.jwcode.core.workflow.ir;

import java.util.List;

public record PhaseNode(
    String id,
    String name,
    List<WorkflowNode> body
) implements WorkflowNode {
    public PhaseNode {
        body = body == null ? List.of() : List.copyOf(body);
    }
}

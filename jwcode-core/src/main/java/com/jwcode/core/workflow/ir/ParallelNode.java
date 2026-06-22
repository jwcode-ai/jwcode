package com.jwcode.core.workflow.ir;

import java.util.List;

public record ParallelNode(
    String id,
    List<WorkflowNode> branches,
    int concurrency,
    ErrorMode errorMode
) implements WorkflowNode {
    public ParallelNode {
        branches = branches == null ? List.of() : List.copyOf(branches);
        concurrency = concurrency <= 0 ? Math.max(1, branches.size()) : concurrency;
        errorMode = errorMode == null ? ErrorMode.NULL : errorMode;
    }
}

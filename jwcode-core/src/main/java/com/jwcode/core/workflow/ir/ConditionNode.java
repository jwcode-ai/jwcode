package com.jwcode.core.workflow.ir;

public record ConditionNode(
    String id,
    String inputPointer,
    WorkflowNode thenNode,
    WorkflowNode elseNode
) implements WorkflowNode {
}

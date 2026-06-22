package com.jwcode.core.workflow.ir;

public record SynthesizeNode(
    String id,
    String prompt
) implements WorkflowNode {
}

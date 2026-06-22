package com.jwcode.core.workflow.ir;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AgentNode.class, name = "agent"),
    @JsonSubTypes.Type(value = ToolNode.class, name = "tool"),
    @JsonSubTypes.Type(value = ParallelNode.class, name = "parallel"),
    @JsonSubTypes.Type(value = PipelineNode.class, name = "pipeline"),
    @JsonSubTypes.Type(value = PhaseNode.class, name = "phase"),
    @JsonSubTypes.Type(value = ConditionNode.class, name = "condition"),
    @JsonSubTypes.Type(value = LoopNode.class, name = "loop"),
    @JsonSubTypes.Type(value = SynthesizeNode.class, name = "synthesize")
})
public sealed interface WorkflowNode
    permits AgentNode, ToolNode, ParallelNode, PipelineNode,
            PhaseNode, ConditionNode, LoopNode, SynthesizeNode {
    String id();
}

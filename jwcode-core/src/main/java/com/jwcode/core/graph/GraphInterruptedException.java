package com.jwcode.core.graph;

/**
 * Thrown by {@link CompiledAgentGraph.PregelLoop} when execution is paused
 * due to an {@code interrupt_before} or {@code interrupt_after} configuration.
 * The caller can inspect the checkpoint and resume via
 * {@link CompiledAgentGraph#resume}.
 */
public class GraphInterruptedException extends RuntimeException {

    private final String nodeName;
    private final String reason;
    private final int step;

    public GraphInterruptedException(String nodeName, String reason, int step) {
        super("Graph interrupted at step " + step + " (" + reason + ": " + nodeName + ")");
        this.nodeName = nodeName;
        this.reason = reason;
        this.step = step;
    }

    public String getNodeName() { return nodeName; }
    public String getInterruptReason() { return reason; }
    public int getStep() { return step; }
}

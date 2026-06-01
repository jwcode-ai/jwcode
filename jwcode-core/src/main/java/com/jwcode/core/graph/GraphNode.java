package com.jwcode.core.graph;

import com.jwcode.core.agent.Agent;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

/**
 * A node in the agent graph — wraps an {@link Agent} with metadata that
 * controls how the Pregel execution loop schedules and executes it.
 * <p>
 * Modeled after LangGraph's {@code StateNodeSpec}.
 */
public class GraphNode {

    private final String name;
    private final Agent agent;
    private final Set<String> triggers;   // channels this node subscribes to
    private final Set<String> writes;     // channels this node may write to
    private final RetryPolicy retryPolicy;
    private final Duration timeout;
    private final boolean defer;          // run at end of all other nodes
    private final String errorHandlerNode;

    private GraphNode(Builder builder) {
        this.name = builder.name;
        this.agent = builder.agent;
        this.triggers = Collections.unmodifiableSet(builder.triggers);
        this.writes = Collections.unmodifiableSet(builder.writes);
        this.retryPolicy = builder.retryPolicy;
        this.timeout = builder.timeout;
        this.defer = builder.defer;
        this.errorHandlerNode = builder.errorHandlerNode;
    }

    public String getName() { return name; }
    public Agent getAgent() { return agent; }
    public Set<String> getTriggers() { return triggers; }
    public Set<String> getWrites() { return writes; }
    public RetryPolicy getRetryPolicy() { return retryPolicy; }
    public Duration getTimeout() { return timeout; }
    public boolean isDefer() { return defer; }
    public String getErrorHandlerNode() { return errorHandlerNode; }

    public static Builder builder(String name, Agent agent) {
        return new Builder(name, agent);
    }

    public static class Builder {
        private final String name;
        private final Agent agent;
        private Set<String> triggers = Collections.emptySet();
        private Set<String> writes = Collections.emptySet();
        private RetryPolicy retryPolicy;
        private Duration timeout;
        private boolean defer;
        private String errorHandlerNode;

        private Builder(String name, Agent agent) {
            this.name = name;
            this.agent = agent;
        }

        public Builder triggers(Set<String> channels) { this.triggers = channels; return this; }
        public Builder writes(Set<String> channels) { this.writes = channels; return this; }
        public Builder retryPolicy(RetryPolicy policy) { this.retryPolicy = policy; return this; }
        public Builder timeout(Duration d) { this.timeout = d; return this; }
        public Builder defer(boolean v) { this.defer = v; return this; }
        public Builder errorHandler(String nodeName) { this.errorHandlerNode = nodeName; return this; }

        public GraphNode build() { return new GraphNode(this); }
    }

    @Override
    public String toString() {
        return "GraphNode{name='" + name + "', agent=" + agent.getName() + '}';
    }
}

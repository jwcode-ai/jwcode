package com.jwcode.core.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of executing a single graph node — a set of channel writes
 * and optional routing instructions.
 * <p>
 * Modeled after the writes produced by a node in LangGraph's Pregel model:
 * each node reads from its trigger channels, executes, and produces writes
 * to its output channels.
 */
public class NodeResult {

    private final String nodeName;
    private final Map<String, List<Object>> writes;  // channel -> values
    private final boolean success;
    private final Throwable error;

    private NodeResult(Builder builder) {
        this.nodeName = builder.nodeName;
        this.writes = Collections.unmodifiableMap(builder.writes);
        this.success = builder.success;
        this.error = builder.error;
    }

    public String getNodeName() { return nodeName; }
    public Map<String, List<Object>> getWrites() { return writes; }
    public boolean isSuccess() { return success; }
    public Throwable getError() { return error; }

    /** Convenience: get the first write value for a channel. */
    @SuppressWarnings("unchecked")
    public <T> T getWrite(String channelName) {
        List<Object> vals = writes.get(channelName);
        return (vals != null && !vals.isEmpty()) ? (T) vals.get(vals.size() - 1) : null;
    }

    public static Builder success(String nodeName) {
        return new Builder(nodeName, true);
    }

    public static Builder failure(String nodeName, Throwable error) {
        return new Builder(nodeName, false).error(error);
    }

    public static class Builder {
        private final String nodeName;
        private final Map<String, List<Object>> writes = new LinkedHashMap<>();
        private final boolean success;
        private Throwable error;

        private Builder(String nodeName, boolean success) {
            this.nodeName = nodeName;
            this.success = success;
        }

        public Builder write(String channel, Object value) {
            writes.computeIfAbsent(channel, k -> new ArrayList<>()).add(value);
            return this;
        }

        public Builder error(Throwable e) { this.error = e; return this; }

        public NodeResult build() {
            return new NodeResult(this);
        }
    }

    @Override
    public String toString() {
        return "NodeResult{node='" + nodeName + "', success=" + success
                + ", channels=" + writes.keySet() + '}';
    }
}

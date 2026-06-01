package com.jwcode.core.graph;

import java.util.Collections;
import java.util.Map;

/**
 * A snapshot of graph execution state at a single superstep, modeled after
 * LangGraph's {@code Checkpoint} typed dict.
 *
 * <p>Compared to the existing {@code com.jwcode.core.checkpoint.Checkpoint}:
 * this tracks channel-level versions and per-node seen-versions, enabling
 * fine-grained "which node saw what" tracking for incremental execution.</p>
 */
public class GraphCheckpoint {

    private final String id;
    private final String ts;
    private final Map<String, Object> channelValues;
    private final Map<String, Long> channelVersions;
    private final Map<String, Map<String, Long>> versionsSeen;
    private final Iterable<String> updatedChannels;
    private final int step;
    private final String source;

    public GraphCheckpoint(String id,
                           String ts,
                           Map<String, Object> channelValues,
                           Map<String, Long> channelVersions,
                           Map<String, Map<String, Long>> versionsSeen,
                           Iterable<String> updatedChannels,
                           int step,
                           String source) {
        this.id = id;
        this.ts = ts;
        this.channelValues = Collections.unmodifiableMap(channelValues);
        this.channelVersions = Collections.unmodifiableMap(channelVersions);
        this.versionsSeen = Collections.unmodifiableMap(versionsSeen);
        this.updatedChannels = updatedChannels;
        this.step = step;
        this.source = source;
    }

    public String getId() { return id; }
    public String getTs() { return ts; }
    public Map<String, Object> getChannelValues() { return channelValues; }
    public Map<String, Long> getChannelVersions() { return channelVersions; }
    public Map<String, Map<String, Long>> getVersionsSeen() { return versionsSeen; }
    public Iterable<String> getUpdatedChannels() { return updatedChannels; }
    public int getStep() { return step; }
    public String getSource() { return source; }

    @Override
    public String toString() {
        return "GraphCheckpoint{id='" + id + "', step=" + step
                + ", source='" + source + "', channels=" + channelValues.size() + '}';
    }
}

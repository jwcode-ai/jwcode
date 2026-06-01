package com.jwcode.core.graph;

import com.jwcode.core.graph.channel.Channel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The typed state of a running agent graph — a collection of named
 * {@link Channel} instances, each representing a state slot.
 * <p>
 * Modeled after LangGraph's state-as-channels model, where each field in the
 * state schema resolves to a channel with specific update semantics (last-value,
 * reducer-merge, topic, etc.).
 */
public class GraphState {

    private final Map<String, Channel<?>> channels;
    private final Map<String, Set<String>> channelSubscribers;

    public GraphState() {
        this.channels = new LinkedHashMap<>();
        this.channelSubscribers = new LinkedHashMap<>();
    }

    /** Register a channel under the given name. */
    public <T> GraphState addChannel(String name, Channel<T> channel) {
        channels.put(name, channel);
        channelSubscribers.putIfAbsent(name, new LinkedHashSet<>());
        return this;
    }

    /** Declare that a node subscribes to (is triggered by) a channel. */
    public GraphState subscribe(String channelName, String nodeName) {
        channelSubscribers.computeIfAbsent(channelName, k -> new LinkedHashSet<>())
                .add(nodeName);
        return this;
    }

    /** Get the channel by name. */
    @SuppressWarnings("unchecked")
    public <T> Channel<T> getChannel(String name) {
        return (Channel<T>) channels.get(name);
    }

    /** Read the current value from a named channel. */
    @SuppressWarnings("unchecked")
    public <T> T getValue(String name) {
        Channel<T> ch = (Channel<T>) channels.get(name);
        return ch != null ? ch.get() : null;
    }

    /** Get the set of nodes subscribed to a channel. */
    public Set<String> getSubscribers(String channelName) {
        return channelSubscribers.getOrDefault(channelName, Collections.emptySet());
    }

    /** All channel names. */
    public Set<String> channelNames() {
        return Collections.unmodifiableSet(channels.keySet());
    }

    /** All channels. */
    public Map<String, Channel<?>> getChannels() {
        return Collections.unmodifiableMap(channels);
    }

    /** Create a deep copy of the entire state (for fork/snapshot). */
    public GraphState copy() {
        GraphState copy = new GraphState();
        for (var entry : channels.entrySet()) {
            copy.channels.put(entry.getKey(), entry.getValue().copy());
        }
        for (var entry : channelSubscribers.entrySet()) {
            copy.channelSubscribers.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        return copy;
    }

    /** Build a mapping of channel name → checkpoint value for serialization. */
    public Map<String, Object> toCheckpointValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (var entry : channels.entrySet()) {
            Object cp = entry.getValue().checkpoint();
            if (cp != null) {
                values.put(entry.getKey(), cp);
            }
        }
        return values;
    }

    /** Restore channel values from a previously saved checkpoint map. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void fromCheckpointValues(Map<String, Object> values) {
        for (var entry : values.entrySet()) {
            Channel ch = channels.get(entry.getKey());
            if (ch != null) {
                ch.fromCheckpoint(entry.getValue());
            }
        }
    }

    @Override
    public String toString() {
        return "GraphState{channels=" + channels.keySet() + '}';
    }
}

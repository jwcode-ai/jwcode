package com.jwcode.core.graph.channel;

import java.util.List;

/**
 * A named slot in the graph state that nodes read from and write to.
 * <p>
 * Modeled after LangGraph's {@code BaseChannel}: each channel holds a typed value,
 * accepts updates from one or more concurrent nodes, and can be checkpointed.
 * The {@link #update} method may be called multiple times per superstep; the
 * channel's reducer semantics determine how values are merged.
 *
 * @param <T> the type of value held by this channel
 */
public interface Channel<T> {

    /** Returns the current merged value. */
    T get();

    /**
     * Apply a batch of writes from one or more nodes within a single superstep.
     * @return true if the channel value changed (triggers downstream nodes)
     */
    boolean update(List<T> values);

    /** Signal end-of-superstep for this channel (e.g. for barrier semantics). */
    default void stepComplete() {}

    /** Signal the graph has finished — may flush final values. */
    default boolean finish() {
        return false;
    }

    /**
     * Mark that a downstream consumer has read this channel's current value.
     * Called by the execution engine to implement the "versions seen" trigger
     * mechanism. Returns true if the channel was freshly consumed (had unread data).
     */
    default boolean consume() {
        return false;
    }

    /** Snapshot the channel value for checkpoint serialization. */
    Object checkpoint();

    /** Restore from a previously serialized checkpoint value. */
    @SuppressWarnings("unchecked")
    void fromCheckpoint(Object saved);

    /** Whether this channel is currently available (can trigger downstream tasks). */
    default boolean isAvailable() {
        return true;
    }

    /** Create a deep copy of this channel for fork/isolated execution. */
    Channel<T> copy();
}

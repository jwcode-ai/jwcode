package com.jwcode.core.graph.channel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A pub/sub channel that accumulates writes into a list — equivalent to
 * LangGraph's {@code Topic} channel.
 * <p>
 * Each node writes a {@code List<E>} of items; the channel appends all items
 * from all nodes into a single accumulated list. On {@link #consume()}, the
 * list is drained so each message is processed exactly once.
 * <p>
 * This is the basis for the {@code Send} API: nodes push messages onto a
 * topic channel, and the Pregel loop reads them off to spawn new tasks.
 */
public class TopicChannel<E> implements Channel<List<E>> {

    private final List<E> values;
    private boolean consumed;

    public TopicChannel() {
        this.values = new ArrayList<>();
        this.consumed = false;
    }

    @Override
    public List<E> get() {
        this.consumed = true;
        return Collections.unmodifiableList(values);
    }

    @Override
    public boolean update(List<List<E>> incoming) {
        if (incoming.isEmpty()) return false;
        boolean added = false;
        for (List<E> batch : incoming) {
            if (!batch.isEmpty()) {
                added |= this.values.addAll(batch);
            }
        }
        if (added) this.consumed = false;
        return added;
    }

    @Override
    public boolean consume() {
        if (consumed) return false;
        consumed = true;
        this.values.clear();
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object checkpoint() {
        return new ArrayList<>(values);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void fromCheckpoint(Object saved) {
        this.values.clear();
        if (saved instanceof List) {
            this.values.addAll((List<? extends E>) saved);
        }
        this.consumed = false;
    }

    @Override
    public Channel<List<E>> copy() {
        TopicChannel<E> c = new TopicChannel<>();
        c.values.addAll(this.values);
        c.consumed = this.consumed;
        return c;
    }

    @Override
    public String toString() {
        return "TopicChannel{size=" + values.size() + '}';
    }
}

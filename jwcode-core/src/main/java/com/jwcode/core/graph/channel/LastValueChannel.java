package com.jwcode.core.graph.channel;

import java.util.List;
import java.util.Objects;

/**
 * A channel that stores only the last-written value — equivalent to LangGraph's
 * {@code LastValue}. Each update replaces the previous value. If multiple nodes
 * write to this channel in the same superstep, the last writer wins.
 * <p>
 * This is the default channel type for state fields that don't declare a reducer.
 */
public class LastValueChannel<T> implements Channel<T> {

    private T value;
    private boolean consumed;

    public LastValueChannel(T initialValue) {
        this.value = initialValue;
        this.consumed = false;
    }

    public LastValueChannel() {
        this(null);
    }

    @Override
    public T get() {
        this.consumed = true;
        return value;
    }

    @Override
    public boolean update(List<T> values) {
        if (values.isEmpty()) return false;
        T last = values.get(values.size() - 1);
        if (Objects.equals(value, last)) return false;
        this.value = last;
        this.consumed = false;
        return true;
    }

    @Override
    public boolean consume() {
        if (consumed) return false;
        consumed = true;
        return true;
    }

    @Override
    public Object checkpoint() {
        return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void fromCheckpoint(Object saved) {
        this.value = (T) saved;
        this.consumed = false;
    }

    @Override
    public Channel<T> copy() {
        LastValueChannel<T> c = new LastValueChannel<>(this.value);
        c.consumed = this.consumed;
        return c;
    }

    @Override
    public String toString() {
        return "LastValueChannel{value=" + value + '}';
    }
}

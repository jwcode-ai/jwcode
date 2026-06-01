package com.jwcode.core.graph.channel;

import java.util.List;
import java.util.Objects;

/**
 * A channel whose value is never persisted in checkpoints — equivalent to
 * LangGraph's {@code EphemeralValue}. Useful for temporary scratch-pad data
 * or UI-only signals that should not survive a restart.
 */
public class EphemeralChannel<T> implements Channel<T> {

    private T value;
    private boolean consumed;

    public EphemeralChannel(T initialValue) {
        this.value = initialValue;
        this.consumed = false;
    }

    public EphemeralChannel() {
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

    /**
     * Ephemeral channels return null from checkpoint — the engine must skip
     * serialization for these channels.
     */
    @Override
    public Object checkpoint() {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void fromCheckpoint(Object saved) {
        if (saved != null) {
            this.value = (T) saved;
        }
        this.consumed = false;
    }

    @Override
    public Channel<T> copy() {
        EphemeralChannel<T> c = new EphemeralChannel<>(this.value);
        c.consumed = this.consumed;
        return c;
    }

    @Override
    public String toString() {
        return "EphemeralChannel{value=" + value + '}';
    }
}

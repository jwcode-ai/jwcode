package com.jwcode.core.graph.channel;

import java.util.List;
import java.util.Objects;
import java.util.function.BinaryOperator;

/**
 * A channel that merges writes using a reducer function — equivalent to
 * LangGraph's {@code BinaryOperatorAggregate}.
 * <p>
 * This is the channel type for state fields annotated with a reducer.
 * For example, a {@code messages} channel that appends lists would use
 * {@code BinaryOpChannel.ofReducing(listA, listB -> { listA.addAll(listB); return listA; })}.
 */
public class BinaryOpChannel<T> implements Channel<T> {

    private T value;
    private final BinaryOperator<T> reducer;
    private boolean consumed;

    /**
     * Create a channel with an initial value and a reducer.
     * The reducer is called as {@code reducer.apply(current, incoming)} for each
     * write in the batch, in order.
     */
    public BinaryOpChannel(T initialValue, BinaryOperator<T> reducer) {
        this.value = initialValue;
        this.reducer = Objects.requireNonNull(reducer);
        this.consumed = false;
    }

    public BinaryOpChannel(BinaryOperator<T> reducer) {
        this(null, reducer);
    }

    @Override
    public T get() {
        this.consumed = true;
        return value;
    }

    @Override
    public boolean update(List<T> values) {
        if (values.isEmpty()) return false;
        T before = value;
        for (T v : values) {
            if (value == null) {
                value = v;
            } else {
                value = reducer.apply(value, v);
            }
        }
        if (Objects.equals(before, value)) return false;
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
        BinaryOpChannel<T> c = new BinaryOpChannel<>(this.value, this.reducer);
        c.consumed = this.consumed;
        return c;
    }

    @Override
    public String toString() {
        return "BinaryOpChannel{value=" + value + '}';
    }
}

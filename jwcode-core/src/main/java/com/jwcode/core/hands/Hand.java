package com.jwcode.core.hands;

public interface Hand<I, O> {
    O execute(I input, HandContext context) throws Exception;
}

package com.jwcode.core.memory;

import java.util.List;

public record TaskNode(
    String id,
    String title,
    String status,
    List<TaskNode> children
) {
    public TaskNode {
        children = children == null ? List.of() : List.copyOf(children);
    }
}

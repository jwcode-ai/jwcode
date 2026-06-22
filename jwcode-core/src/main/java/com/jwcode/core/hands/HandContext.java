package com.jwcode.core.hands;

import com.jwcode.core.workflow.WorkflowInput;
import com.jwcode.core.workflow.WorkflowState;

import java.util.List;

public record HandContext(
    String runId,
    String effectId,
    String sessionId,
    WorkflowInput workflowInput,
    WorkflowState workflowState,
    List<String> allowedTools
) {
    public HandContext {
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }
}

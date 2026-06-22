package com.jwcode.core.workflow;

import java.nio.file.Path;
import java.time.Instant;

public record WorkflowRun(
    String runId,
    String sessionId,
    WorkflowStatus status,
    Path directory,
    Instant createdAt,
    Instant updatedAt
) {
}

package com.jwcode.core.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowLedgerTest {
    @TempDir
    Path tempDir;

    @Test
    void appendAndReplayPreserveOrder() {
        WorkflowLedger ledger = new WorkflowLedger("run-1", tempDir.resolve("run-1"));
        ledger.append("run.started", Map.of("sessionId", "s1"));
        ledger.append("effect.scheduled", Map.of("effectId", "e1", "nodeId", "n1"));
        ledger.append("effect.completed", Map.of("effectId", "e1", "nodeId", "n1", "kind", "agent", "artifactRef", "artifacts/e1.json"));

        var events = ledger.replay();
        assertEquals(3, events.size());
        assertEquals("run.started", events.get(0).type());
        assertEquals(1, events.get(0).sequence());
        assertEquals(3, events.get(2).sequence());

        WorkflowState state = ledger.replayState();
        assertEquals(WorkflowStatus.RUNNING, state.status());
        assertTrue(state.completedEffect("e1").isPresent());
    }
}

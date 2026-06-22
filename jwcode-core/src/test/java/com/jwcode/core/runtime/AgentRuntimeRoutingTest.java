package com.jwcode.core.runtime;

import com.jwcode.core.hands.AgentResult;
import com.jwcode.core.hands.LocalAgentHand;
import com.jwcode.core.workflow.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeRoutingTest {
    @TempDir
    Path tempDir;

    @Test
    void simpleChatDoesNotCreateWorkflow() {
        DefaultAgentRuntime runtime = new DefaultAgentRuntime(null, tempDir, new LocalAgentHand(), null);

        RuntimeResult result = runtime.handleChat("session", "hello");

        assertTrue(result.success());
        assertEquals(RuntimeMode.CHAT, result.mode());
        assertEquals("hello", result.message());
    }

    @Test
    void complexChatCreatesWorkflowRun() {
        DefaultAgentRuntime runtime = new DefaultAgentRuntime(null, tempDir, new LocalAgentHand(request ->
            AgentResult.success(request.role(), request.prompt(), null, 0, 1)), null);

        RuntimeResult result = runtime.handleChat("session", "please implement this change and test it");

        assertTrue(result.success());
        assertEquals(RuntimeMode.WORKFLOW, result.mode());
        assertNotNull(result.workflowRun());
        assertEquals(WorkflowStatus.COMPLETED, runtime.getWorkflowStatus(result.workflowRun().runId()));
    }
}

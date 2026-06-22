package com.jwcode.core.tool;

import com.jwcode.core.session.Session;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.workflow.WorkflowCompiler;
import com.jwcode.core.workflow.WorkflowIR;
import com.jwcode.core.workflow.ir.AgentNode;
import com.jwcode.core.workflow.ir.ErrorMode;
import com.jwcode.core.workflow.ir.ParallelNode;
import com.jwcode.core.workflow.ir.PhaseNode;
import com.jwcode.core.workflow.ir.PipelineNode;
import com.jwcode.core.workflow.ir.WorkflowNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolWorkflowRuntimeTest {
    @TempDir
    Path tempDir;

    @Test
    void executeActionUsesWorkflowRuntimeAndWritesLedger() throws Exception {
        String oldRoot = System.getProperty("jwcode.workflow.root");
        System.setProperty("jwcode.workflow.root", tempDir.resolve("workflows").toString());
        try {
            AgentTool tool = new AgentTool();
            Session session = new Session("s-agent-tool", tempDir.toString());
            ToolExecutionContext context = ToolExecutionContext.builder()
                .session(session)
                .workingDirectory(tempDir)
                .interactive(false)
                .build();

            ToolResult<Map<String, Object>> result = tool.call(Map.of(
                "action", "execute",
                "parallel", true,
                "tasks", List.of(
                    Map.of("name", "explore", "role", "explorer", "task", "inspect structure"),
                    Map.of("name", "verify", "role", "verifier", "task", "check result")
                )
            ), context, null).get();

            assertTrue(result.isSuccess());
            assertNotNull(result.getContent());
            assertNotNull(result.getData());
            assertEquals(true, result.getMetadata().get("workflow_runtime"));
            String runId = String.valueOf(result.getMetadata().get("workflow_run_id"));
            assertNotNull(runId);
            assertEquals("COMPLETED", result.getMetadata().get("workflow_status"));
            assertTrue(Files.exists(tempDir.resolve("workflows").resolve(runId).resolve("events.jsonl")));
            assertTrue(Files.exists(tempDir.resolve("workflows").resolve(runId).resolve("ir.json")));
        } finally {
            if (oldRoot != null) {
                System.setProperty("jwcode.workflow.root", oldRoot);
            } else {
                System.clearProperty("jwcode.workflow.root");
            }
        }
    }

    @Test
    void parallelExecuteCompilesToPhaseWithParallelNode() throws Exception {
        withWorkflowRoot(() -> {
            ToolResult<Map<String, Object>> result = execute(List.of(
                Map.of("name", "explore", "role", "explorer", "task", "inspect structure"),
                Map.of("name", "verify", "role", "verifier", "task", "check result")
            ), true);

            WorkflowNode body = firstPhaseBody(result);
            ParallelNode parallel = assertInstanceOf(ParallelNode.class, body);
            assertEquals(ErrorMode.NULL, parallel.errorMode());
            assertEquals(2, parallel.branches().size());
            assertInstanceOf(AgentNode.class, parallel.branches().get(0));
        });
    }

    @Test
    void sequentialExecuteCompilesToPhaseWithPipelineNode() throws Exception {
        withWorkflowRoot(() -> {
            ToolResult<Map<String, Object>> result = execute(List.of(
                Map.of("name", "explore", "role", "explorer", "task", "inspect structure"),
                Map.of("name", "code", "role", "coder", "task", "implement change")
            ), false);

            WorkflowNode body = firstPhaseBody(result);
            PipelineNode pipeline = assertInstanceOf(PipelineNode.class, body);
            assertEquals(ErrorMode.FAIL_FAST, pipeline.errorMode());
            assertEquals(2, pipeline.steps().size());
            assertInstanceOf(AgentNode.class, pipeline.steps().get(0));
        });
    }

    @Test
    void singleExecuteTaskStillCompilesToAgentNodeInsideWorkflowShape() throws Exception {
        withWorkflowRoot(() -> {
            ToolResult<Map<String, Object>> result = execute(List.of(
                Map.of("name", "solo", "role", "explorer", "task", "inspect one thing")
            ), false);

            PipelineNode pipeline = assertInstanceOf(PipelineNode.class, firstPhaseBody(result));
            AgentNode agent = assertInstanceOf(AgentNode.class, pipeline.steps().get(0));
            assertEquals("explorer", agent.role());
        });
    }

    @Test
    void disabledWorkflowRuntimeFlagIsIgnored() throws Exception {
        String oldEnabled = System.getProperty("agent_tool.workflow.enabled");
        System.setProperty("agent_tool.workflow.enabled", "false");
        try {
            withWorkflowRoot(() -> {
                ToolResult<Map<String, Object>> result = execute(List.of(
                    Map.of("name", "legacy-explore", "role", "explorer", "task", "inspect via legacy runtime")
                ), false, true);

                assertNotNull(result.getContent());
                assertNotNull(result.getData());
                assertNotNull(result.getMetadata());
                assertEquals(true, result.getMetadata().get("workflow_runtime"));
                assertNotNull(result.getMetadata().get("workflow_run_id"));
                assertEquals("COMPLETED", result.getMetadata().get("workflow_status"));
                assertTrue(result.getMetadata().containsKey("total_count"));
                assertTrue(result.getMetadata().containsKey("success_count"));
                assertTrue(result.getMetadata().containsKey("failure_count"));
                assertTrue(result.getMetadata().containsKey("results"));
            });
        } finally {
            if (oldEnabled != null) {
                System.setProperty("agent_tool.workflow.enabled", oldEnabled);
            } else {
                System.clearProperty("agent_tool.workflow.enabled");
            }
        }
    }

    private ToolResult<Map<String, Object>> execute(List<Map<String, Object>> tasks, boolean parallel) throws Exception {
        return execute(tasks, parallel, true);
    }

    private ToolResult<Map<String, Object>> execute(List<Map<String, Object>> tasks, boolean parallel,
                                                   boolean expectWorkflowRuntime) throws Exception {
        AgentTool tool = new AgentTool();
        Session session = new Session("s-agent-tool-" + System.nanoTime(), tempDir.toString());
        ToolExecutionContext context = ToolExecutionContext.builder()
            .session(session)
            .workingDirectory(tempDir)
            .interactive(false)
            .build();
        ToolResult<Map<String, Object>> result = tool.call(Map.of(
            "action", "execute",
            "parallel", parallel,
            "tasks", tasks
        ), context, null).get();
        if (expectWorkflowRuntime) {
            assertTrue(result.isSuccess());
            assertEquals(true, result.getMetadata().get("workflow_runtime"));
            assertNotNull(result.getMetadata().get("workflow_run_id"));
            assertEquals("COMPLETED", result.getMetadata().get("workflow_status"));
        }
        return result;
    }

    private WorkflowNode firstPhaseBody(ToolResult<Map<String, Object>> result) throws Exception {
        String runId = String.valueOf(result.getMetadata().get("workflow_run_id"));
        Path irFile = tempDir.resolve("workflows").resolve(runId).resolve("ir.json");
        WorkflowIR ir = new WorkflowCompiler().fromJson(Files.readString(irFile));
        PhaseNode phase = assertInstanceOf(PhaseNode.class, ir.root());
        return phase.body().get(0);
    }

    private void withWorkflowRoot(ThrowingRunnable action) throws Exception {
        String oldRoot = System.getProperty("jwcode.workflow.root");
        System.setProperty("jwcode.workflow.root", tempDir.resolve("workflows").toString());
        try {
            action.run();
        } finally {
            if (oldRoot != null) {
                System.setProperty("jwcode.workflow.root", oldRoot);
            } else {
                System.clearProperty("jwcode.workflow.root");
            }
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}

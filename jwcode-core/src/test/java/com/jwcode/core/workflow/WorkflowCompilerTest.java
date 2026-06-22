package com.jwcode.core.workflow;

import com.jwcode.core.workflow.ir.AgentNode;
import com.jwcode.core.workflow.ir.PhaseNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class WorkflowCompilerTest {
    @Test
    void readsHandWrittenJsonIr() {
        String json = """
            {
              "id": "manual",
              "schemaVersion": "workflow-ir.v1",
              "root": {
                "type": "phase",
                "id": "explore-phase",
                "name": "explore",
                "body": [
                  {
                    "type": "agent",
                    "id": "explorer-1",
                    "role": "explorer",
                    "prompt": "inspect",
                    "tools": ["FileReadTool"],
                    "schema": null,
                    "maxRetries": 0,
                    "timeoutMs": 0
                  }
                ]
              }
            }
            """;

        WorkflowIR ir = new WorkflowCompiler().fromJson(json);

        assertEquals("manual", ir.id());
        PhaseNode phase = assertInstanceOf(PhaseNode.class, ir.root());
        AgentNode agent = assertInstanceOf(AgentNode.class, phase.body().get(0));
        assertEquals("explorer", agent.role());
        assertEquals("FileReadTool", agent.tools().get(0));
    }
}

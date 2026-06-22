package com.jwcode.core.memory;

import com.jwcode.core.model.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileMemoryLayerTest {
    @TempDir
    Path tempDir;

    @Test
    void checkpointAndProjectMemoryAreMergedIntoRebuiltContext() {
        FileMemoryLayer memory = new FileMemoryLayer(tempDir);
        Checkpoint checkpoint = new Checkpoint(
            "refactor workflow runtime",
            "run focused tests",
            "preserve ToolExecutor boundary",
            List.of(new TaskNode("t1", "Implement memory layer", "done", List.of())),
            "adding MiMo checkpoint support",
            List.of("jwcode-core/src/main/java/com/jwcode/core/workflow/EffectVM.java"),
            "Effect ids must be stable",
            "none",
            "runId=abc",
            "JSON IR only for v1",
            "notes");

        memory.writeCheckpoint("s1", checkpoint);
        memory.writeProjectMemory("p1", "architecture", "Project memory body");

        List<Message> rebuilt = memory.rebuildContext("s1", List.of(
            Message.createUserMessage("original request"),
            Message.createAssistantMessage("older answer"),
            Message.createUserMessage("latest user text")));

        assertEquals("Task checklist:\n- [done] Implement memory layer\n", rebuilt.get(0).getTextContent());
        assertTrue(rebuilt.get(1).getTextContent().contains("refactor workflow runtime"));
        assertEquals("original request", rebuilt.get(2).getTextContent());
        assertEquals("latest user text", rebuilt.get(3).getTextContent());
        assertTrue(rebuilt.get(4).getTextContent().contains("Project memory body"));
        assertTrue(rebuilt.get(5).getTextContent().contains("adding MiMo checkpoint support"));
        assertTrue(rebuilt.get(6).getTextContent().contains("EffectVM.java"));
    }
}

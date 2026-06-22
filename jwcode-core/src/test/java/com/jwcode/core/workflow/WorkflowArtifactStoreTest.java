package com.jwcode.core.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowArtifactStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void writesAndReadsJsonArtifacts() {
        ObjectMapper mapper = new ObjectMapper();
        WorkflowArtifactStore store = new WorkflowArtifactStore(tempDir.resolve("run"));

        String ref = store.writeJson("effect/1", mapper.createObjectNode().put("answer", 42));
        assertEquals(42, store.readJson(ref).get("answer").asInt());
    }
}

package com.jwcode.core.hands;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.llm.LLMMessage;
import com.jwcode.core.llm.LLMResponse;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.llm.LLMTool;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolProgress;
import com.jwcode.core.tool.ToolRegistry;
import com.jwcode.core.tool.ToolResult;
import com.jwcode.core.tool.ToolWhitelistManager;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.workflow.WorkflowInput;
import com.jwcode.core.workflow.WorkflowState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAgentHandIsolationTest {
    @TempDir
    Path tempDir;

    @Test
    void whitelistIsClearedAfterDelegateExecution() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AtomicBoolean restrictedDuringCall = new AtomicBoolean(false);
        LocalAgentHand hand = new LocalAgentHand(request -> {
            ToolWhitelistManager manager = ToolWhitelistManager.getInstance();
            manager.setWhitelist(java.util.Set.of("FileReadTool"));
            restrictedDuringCall.set(manager.hasRestriction());
            manager.clearWhitelist();
            return AgentResult.success(request.role(), request.prompt(), mapper.createObjectNode(), 0, 1);
        });

        hand.execute(new AgentRequest("explorer", "inspect", List.of(), null, mapper.createObjectNode()),
            new HandContext("run", "effect", "session",
                WorkflowInput.of("session", mapper.createObjectNode()), new WorkflowState("run"), List.of()));

        assertTrue(restrictedDuringCall.get());
        assertFalse(ToolWhitelistManager.getInstance().hasRestriction());
    }

    @Test
    void toolExecutorRejectsWriteToolOutsideExplorerWhitelist() throws Exception {
        ToolRegistry registry = new ToolRegistry().register(new FakeJsonTool("FileWriteTool"));
        ToolExecutor executor = new ToolExecutor(registry);
        ToolWhitelistManager.getInstance().setWhitelist(Set.of("FileReadTool"));
        try {
            ToolExecutor.ToolExecutionResult result = executor.execute(
                "FileWriteTool",
                new ObjectMapper().createObjectNode(),
                ToolExecutionContext.builder()
                    .session(new Session("session-explorer", tempDir.toString()))
                    .workingDirectory(tempDir)
                    .interactive(false)
                    .build()).get();

            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("Whitelist"));
        } finally {
            ToolWhitelistManager.getInstance().clearWhitelist();
        }
    }

    @Test
    void toolHandDelegatesThroughToolExecutorWithChildSessionAndClearsWhitelist() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RecordingToolExecutor executor = new RecordingToolExecutor();
        ToolHand hand = new ToolHand(executor, tempDir);

        ToolEffectResult result = hand.execute(
            new ToolRequest("FileWriteTool", mapper.createObjectNode()),
            new HandContext("run", "effect-1", "parent-session",
                WorkflowInput.of("parent-session", mapper.createObjectNode()),
                new WorkflowState("run"),
                List.of("FileWriteTool")));

        assertTrue(result.success());
        assertEquals("FileWriteTool", executor.toolName.get());
        assertEquals("parent-session", executor.sessionId.get());
        assertTrue(executor.restrictedDuringCall.get());
        assertFalse(ToolWhitelistManager.getInstance().hasRestriction());
    }

    @Test
    void localAgentHandUsesStableChildSessionIdWithoutMutatingParentSession() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LocalAgentHand hand = new LocalAgentHand(
            new FinishingLLMService(),
            new ToolExecutor(new ToolRegistry()),
            tempDir,
            null);

        AgentResult result = hand.execute(
            new AgentRequest("coder", "implement", List.of(), null, mapper.createObjectNode()),
            new HandContext("run", "effect-2", "parent-session",
                WorkflowInput.of("parent-session", mapper.createObjectNode()),
                new WorkflowState("run"),
                List.of()));

        assertTrue(result.success());
        assertNotNull(result.structuredOutput());
        assertEquals("parent-session", result.structuredOutput().get("childSessionId").asText());
    }

    @Test
    void concurrentWhitelistsDoNotPolluteEachOther() throws Exception {
        CompletableFuture<Boolean> explorer = CompletableFuture.supplyAsync(() -> {
            ToolWhitelistManager manager = ToolWhitelistManager.getInstance();
            manager.setWhitelist(Set.of("FileReadTool"));
            try {
                return manager.isAllowed("FileReadTool") && !manager.isAllowed("FileWriteTool");
            } finally {
                manager.clearWhitelist();
            }
        });
        CompletableFuture<Boolean> coder = CompletableFuture.supplyAsync(() -> {
            ToolWhitelistManager manager = ToolWhitelistManager.getInstance();
            manager.setWhitelist(Set.of("FileWriteTool"));
            try {
                return manager.isAllowed("FileWriteTool") && !manager.isAllowed("FileReadTool");
            } finally {
                manager.clearWhitelist();
            }
        });

        assertTrue(explorer.get());
        assertTrue(coder.get());
        assertFalse(ToolWhitelistManager.getInstance().hasRestriction());
    }

    private static final class RecordingToolExecutor extends ToolExecutor {
        final AtomicReference<String> toolName = new AtomicReference<>();
        final AtomicReference<String> sessionId = new AtomicReference<>();
        final AtomicBoolean restrictedDuringCall = new AtomicBoolean(false);

        @Override
        public CompletableFuture<ToolExecutionResult> execute(String toolName, JsonNode inputJson,
                                                              ToolExecutionContext context) {
            this.toolName.set(toolName);
            this.sessionId.set(context.getSession().getId());
            this.restrictedDuringCall.set(ToolWhitelistManager.getInstance().hasRestriction());
            ToolResult<String> result = ToolResult.success("ok");
            result.setContent("ok");
            return CompletableFuture.completedFuture(ToolExecutionResult.success(toolName, result));
        }
    }

    private static final class FakeJsonTool implements Tool<JsonNode, String, Void> {
        private final String name;

        private FakeJsonTool(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public TypeReference<JsonNode> getInputType() {
            return new TypeReference<>() {};
        }

        @Override
        public TypeReference<String> getOutputType() {
            return new TypeReference<>() {};
        }

        @Override
        public CompletableFuture<ToolResult<String>> call(JsonNode input, ToolExecutionContext context,
                                                          Consumer<ToolProgress<Void>> onProgress) {
            ToolResult<String> result = ToolResult.success("ok");
            result.setContent("ok");
            return CompletableFuture.completedFuture(result);
        }
    }

    private static final class FinishingLLMService implements LLMService {
        @Override
        public CompletableFuture<LLMResponse> chat(List<LLMMessage> messages) {
            LLMResponse response = LLMResponse.success("done");
            response.setFinishReason("stop");
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<LLMResponse> chatWithTools(List<LLMMessage> messages, List<LLMTool> tools) {
            return chat(messages);
        }

        @Override
        public CompletableFuture<LLMResponse> chatStream(List<LLMMessage> messages, Consumer<String> contentConsumer) {
            return chat(messages);
        }

        @Override
        public CompletableFuture<LLMResponse> chatStreamWithTools(
                List<LLMMessage> messages,
                List<LLMTool> tools,
                Consumer<String> contentConsumer,
                Consumer<String> thinkingConsumer,
                Consumer<LLMService.StreamToolCallEvent> toolCallConsumer) {
            return chat(messages);
        }

        @Override
        public CompletableFuture<com.jwcode.core.llm.LLMTestResult> test() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public String getModelName() {
            return "test-model";
        }

        @Override
        public void close() {
        }
    }
}

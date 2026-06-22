package com.jwcode.core.hands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.llm.LLMQueryEngine;
import com.jwcode.core.llm.LLMMessage;
import com.jwcode.core.llm.LLMResponse;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.memory.MemoryLayer;
import com.jwcode.core.model.Message;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolWhitelistManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class LocalAgentHand implements Hand<AgentRequest, AgentResult> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Function<AgentRequest, AgentResult> delegate;
    private final LLMService llmService;
    private final ToolExecutor toolExecutor;
    private final Path workingDirectory;
    private final LLMQueryEngine.EngineConfig engineConfig;
    private final MemoryLayer memoryLayer;
    private final LLMQueryEngine.StepCallback stepCallback;

    public LocalAgentHand(Function<AgentRequest, AgentResult> delegate) {
        this.delegate = delegate;
        this.llmService = null;
        this.toolExecutor = null;
        this.workingDirectory = null;
        this.engineConfig = null;
        this.memoryLayer = null;
        this.stepCallback = null;
    }

    public LocalAgentHand(LLMService llmService, ToolExecutor toolExecutor, Path workingDirectory,
                          LLMQueryEngine.EngineConfig engineConfig) {
        this(llmService, toolExecutor, workingDirectory, engineConfig, null);
    }

    public LocalAgentHand(LLMService llmService, ToolExecutor toolExecutor, Path workingDirectory,
                          LLMQueryEngine.EngineConfig engineConfig, MemoryLayer memoryLayer) {
        this(llmService, toolExecutor, workingDirectory, engineConfig, memoryLayer, null);
    }

    public LocalAgentHand(LLMService llmService, ToolExecutor toolExecutor, Path workingDirectory,
                          LLMQueryEngine.EngineConfig engineConfig, MemoryLayer memoryLayer,
                          LLMQueryEngine.StepCallback stepCallback) {
        this.delegate = null;
        this.llmService = llmService;
        this.toolExecutor = toolExecutor;
        this.workingDirectory = workingDirectory;
        this.engineConfig = engineConfig;
        this.memoryLayer = memoryLayer;
        this.stepCallback = stepCallback;
    }

    public LocalAgentHand() {
        this(request -> {
            ObjectNode output = MAPPER.createObjectNode();
            output.put("role", request.role());
            output.put("content", request.prompt());
            return AgentResult.success(request.role(), request.prompt(), output, 0, 0);
        });
    }

    @Override
    public AgentResult execute(AgentRequest input, HandContext context) throws Exception {
        long start = System.currentTimeMillis();
        if (delegate != null) {
            return delegate.apply(input);
        }
        if (llmService == null) {
            return AgentResult.failure(input.role(), "LocalAgentHand is not configured with an LLMQueryEngine delegate", 0);
        }
        String prompt = promptWithInput(input);
        List<Message> rebuiltContext = rebuildMemoryContext(context, prompt);
        if (toolExecutor == null) {
            List<LLMMessage> messages = new ArrayList<>();
            messages.add(LLMMessage.system(rolePrompt(input.role())));
            for (Message message : rebuiltContext) {
                messages.add(toLLMMessage(message));
            }
            messages.add(LLMMessage.user(prompt));
            LLMResponse response = llmService.chat(messages).get();
            long duration = System.currentTimeMillis() - start;
            if (response == null || response.hasError()) {
                String error = response == null ? "LLM response was null" : response.getErrorMessage();
                return AgentResult.failure(input.role(), error, duration);
            }
            String content = response.getContent() == null ? "" : response.getContent();
            ObjectNode structured = MAPPER.createObjectNode();
            structured.put("content", content);
            structured.put("role", input.role());
            return AgentResult.success(input.role(), content, structured, 0, duration);
        }

        Session child = new Session(context.sessionId(),
            workingDirectory != null ? workingDirectory.toString() : System.getProperty("user.dir"));
        for (Message message : rebuiltContext) {
            child.addMessage(message);
        }
        child.addMessage(Message.createSystemMessage(rolePrompt(input.role())));

        Set<String> whitelist = new HashSet<>(input.tools().isEmpty() ? defaultToolsForRole(input.role()) : input.tools());
        try {
            ToolWhitelistManager.getInstance().setWhitelist(whitelist);
            LLMQueryEngine engine = new LLMQueryEngine(child, llmService, toolExecutor, engineConfig);
            if (stepCallback != null) {
                engine.setStepCallback(stepCallback);
            }
            LLMQueryEngine.QueryResult result = engine.query(prompt).get();
            long duration = System.currentTimeMillis() - start;
            if (!result.isSuccess()) {
                return AgentResult.failure(input.role(), result.getErrorMessage(), duration);
            }
            String content = result.getMessage() == null ? "" : result.getMessage().getTextContent();
            ObjectNode structured = MAPPER.createObjectNode();
            structured.put("content", content);
            structured.put("role", input.role());
            structured.put("childSessionId", child.getId());
            return AgentResult.success(input.role(), content, structured, 0, duration);
        } finally {
            ToolWhitelistManager.getInstance().clearWhitelist();
        }
    }

    private static String rolePrompt(String role) {
        return switch (role == null ? "main" : role) {
            case "explorer" -> "You are the explorer role. Gather evidence using read-only tools and summarize findings.";
            case "coder" -> "You are the coder role. Implement the assigned code changes using permitted tools.";
            case "verifier" -> "You are the verifier role. Test, review, and report regressions or residual risk.";
            default -> "You are the main role. Coordinate the task and produce structured results.";
        };
    }

    private List<Message> rebuildMemoryContext(HandContext context, String prompt) {
        if (memoryLayer == null || context == null || context.workflowInput() == null
            || !context.workflowInput().memoryEnabled()) {
            return List.of();
        }
        return memoryLayer.rebuildContext(context.sessionId(), List.of(Message.createUserMessage(prompt)));
    }

    private static LLMMessage toLLMMessage(Message message) {
        if (message == null) {
            return LLMMessage.system("");
        }
        return switch (message.getRole()) {
            case USER -> LLMMessage.user(message.getTextContent());
            case ASSISTANT -> LLMMessage.assistant(message.getTextContent(), message.getReasoningContent());
            case TOOL -> LLMMessage.user("Tool result:\n" + message.getTextContent());
            case SYSTEM -> LLMMessage.system(message.getTextContent());
        };
    }

    private static Set<String> defaultToolsForRole(String role) {
        return switch (role == null ? "main" : role) {
            case "explorer" -> Set.of("FileReadTool", "BatchReadTool", "GrepTool", "GlobTool", "ToolSearch", "SmartAnalyzeTool");
            case "coder" -> Set.of("FileReadTool", "BatchReadTool", "GrepTool", "GlobTool", "FileEditTool",
                "FileWriteTool", "BashTool", "PowerShell", "TodoWrite");
            case "verifier" -> Set.of("FileReadTool", "BatchReadTool", "GrepTool", "GlobTool", "BashTool",
                "PowerShell", "GitTool", "LSPTool");
            default -> Set.of();
        };
    }

    private static String promptWithInput(AgentRequest input) {
        if (input.input() == null || input.input().isNull()) {
            return input.prompt();
        }
        return input.prompt() + "\n\nWorkflow input:\n" + input.input();
    }
}

package com.jwcode.core.hands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolWhitelistManager;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.nio.file.Path;
import java.util.HashSet;

public class ToolHand implements Hand<ToolRequest, ToolEffectResult> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ToolExecutor toolExecutor;
    private final Path workingDirectory;

    public ToolHand(ToolExecutor toolExecutor, Path workingDirectory) {
        this.toolExecutor = toolExecutor;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public ToolEffectResult execute(ToolRequest input, HandContext context) throws Exception {
        long start = System.currentTimeMillis();
        if (toolExecutor == null) {
            return ToolEffectResult.failure(input.toolName(), "ToolHand is not configured with a ToolExecutor", 0);
        }
        Session session = new Session(context.sessionId(), workingDirectory != null
            ? workingDirectory.toString()
            : System.getProperty("user.dir"));
        ToolExecutionContext toolContext = ToolExecutionContext.builder()
            .session(session)
            .workingDirectory(workingDirectory != null ? workingDirectory : Path.of(System.getProperty("user.dir")))
            .interactive(true)
            .build();
        try {
            ToolWhitelistManager.getInstance().setWhitelist(new HashSet<>(context.allowedTools()));
            ToolExecutor.ToolExecutionResult result = toolExecutor.execute(input.toolName(), input.input(), toolContext).get();
            long duration = System.currentTimeMillis() - start;
            if (!result.isSuccess()) {
                return ToolEffectResult.failure(input.toolName(), result.getErrorMessage(), duration);
            }
            ObjectNode output = MAPPER.createObjectNode();
            output.put("toolName", input.toolName());
            output.set("data", MAPPER.valueToTree(result.getResult() == null ? null : result.getResult().getData()));
            output.put("content", result.getResult() == null ? null : result.getResult().getContent());
            return ToolEffectResult.success(input.toolName(), output, duration);
        } finally {
            ToolWhitelistManager.getInstance().clearWhitelist();
        }
    }
}

package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class GitTool implements Tool<GitTool.Input, GitTool.Output, GitTool.Progress> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override public String getName() { return "GitTool"; }
    @Override public String getDescription() { return "Run git commands for version control operations."; }
    @Override public String getPrompt() { return "Use GitTool to run git commands."; }

    @Override
    public JsonNode getInputSchema() {
        try { return MAPPER.readTree("{\"type\": \"object\", \"properties\": {\"command\": {\"type\": \"string\"}}, \"required\": [\"command\"]}"); }
        catch (Exception e) { return null; }
    }

    @Override
    public TypeReference<Input> getInputType() { return new TypeReference<Input>() {}; }
    @Override
    public TypeReference<Output> getOutputType() { return new TypeReference<Output>() {}; }

    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input args, ToolExecutionContext context,
            Consumer<ToolProgress<Progress>> onProgress) {
        return CompletableFuture.completedFuture(ToolResult.success(new Output()));
    }

    public static class Input { public String command; public String args; public String cwd; }
    public static class Output { public boolean success; public String output; }
    public static class Progress {}
}

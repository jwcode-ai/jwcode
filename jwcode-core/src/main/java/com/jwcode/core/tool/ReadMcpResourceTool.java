package com.jwcode.core.tool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ReadMcpResourceTool implements Tool<ReadMcpResourceTool.Input, ReadMcpResourceTool.Output, ReadMcpResourceTool.Progress> {
    @Override public String getName() { return "ReadMcpResource"; }
    @Override public String getDescription() { return "读取 MCP 资源"; }
    @Override public String getPrompt() { return "Read MCP resources by URI. Use ListMcpResources first to discover URIs."; }
    @Override public TypeReference<Input> getInputType() { return new TypeReference<Input>() {}; }
    @Override public TypeReference<Output> getOutputType() { return new TypeReference<Output>() {}; }

public static class Input { public String uri; }
    public static class Output { public boolean success; public String content; }
    public static class Progress {}
}

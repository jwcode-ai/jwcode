package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class SleepTool implements Tool<SleepTool.Input, SleepTool.Output, SleepTool.Progress> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override public String getName() { return "Sleep"; }
    @Override public String getDescription() { return "等待指定时间（秒）。用于延迟执行或轮询等待。"; }
    
    @Override
    public String getPrompt() {
        return """
               使用 Sleep 工具等待指定的时间（秒）。
               
               参数:
               - seconds: 等待的秒数（必需，整数）
               
               示例:
               - {"seconds": 5} - 等待 5 秒
               - {"seconds": 60} - 等待 1 分钟
               
               注意:
               - 仅用于需要延迟的场景，如轮询等待、速率限制等
               - 不要滥用此工具，避免不必要的等待
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try { return MAPPER.readTree("{\"type\": \"object\", \"properties\": {\"seconds\": {\"type\": \"integer\", \"description\": \"等待的秒数\"}}, \"required\": [\"seconds\"]}"); } 
        catch (Exception e) { return null; }
    }
    
    @Override
    public TypeReference<Input> getInputType() {
        return new TypeReference<Input>() {};
    }
    
    @Override
    public TypeReference<Output> getOutputType() {
        return new TypeReference<Output>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Progress>> onProgress) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int seconds = Math.max(0, input.seconds);
                if (seconds > 0) {
                    Thread.sleep(seconds * 1000L);
                }
                Output output = new Output();
                output.success = true;
                return ToolResult.success(output);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ToolResult.error("Sleep interrupted: " + e.getMessage());
            }
        });
    }
    
    public static class Input { public int seconds; }
    public static class Output { public boolean success; }
    public static class Progress {}
}

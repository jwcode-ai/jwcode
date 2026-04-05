package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;

public class SendMessageTool implements Tool<SendMessageTool.Input, SendMessageTool.Output, SendMessageTool.Progress> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override public String getName() { return "SendMessage"; }
    @Override public String getDescription() { return "发送消息到指定渠道。支持内部通知和外部通信。"; }
    
    @Override
    public String getPrompt() {
        return """
               使用 SendMessage 工具发送消息。
               
               参数:
               - message: 消息内容（必需）
               - channel: 发送渠道（可选，如 "slack", "email", "webhook"）
               
               示例:
               - {"message": "构建完成"} - 发送简单消息
               - {"message": "部署成功", "channel": "slack"} - 发送到 Slack
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try { return MAPPER.readTree("{\"type\": \"object\", \"properties\": {\"message\": {\"type\": \"string\", \"description\": \"消息内容\"}, \"channel\": {\"type\": \"string\", \"description\": \"发送渠道\"}}, \"required\": [\"message\"]}"); } 
        catch (Exception e) { return null; }
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(Input args, ToolContext context, CanUseToolFn canUseTool, Object parentMessage, java.util.function.Consumer<ToolProgress<Progress>> onProgress) {
        return CompletableFuture.completedFuture(ToolResult.success(new Output()));
    }
    
    public static class Input { public String message; public String channel; }
    public static class Output { public boolean success; }
    public static class Progress {}
}

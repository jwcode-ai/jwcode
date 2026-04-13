package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.message.MessageResult;
import com.jwcode.core.message.MessageService;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 消息发送工具
 * 发送消息到指定渠道（console, slack, email, webhook）
 */
public class SendMessageTool implements Tool<SendMessageTool.Input, SendMessageTool.Output, Void> {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final MessageService messageService;
    
    public SendMessageTool() {
        this.messageService = MessageService.getInstance();
    }
    
    @Override
    public String getName() {
        return "SendMessage";
    }
    
    @Override
    public String getDescription() {
        return "发送消息到指定渠道。支持内部通知和外部通信。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 SendMessage 工具发送消息。
               
               参数:
               - message: 消息内容（必需）
               - channel: 发送渠道（可选，默认 console）
                        可选值: console, slack, email, webhook
               - template: 模板名称（可选）
               - params: 额外参数（可选，如 webhook URL、邮件主题等）
               
               示例:
               - {"message": "构建完成"} - 发送简单消息到控制台
               - {"message": "部署成功", "channel": "slack"} - 发送到 Slack
               - {"template": "build_success", "channel": "slack", "params": {"project": "myapp"}} - 使用模板
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "message": {
                            "type": "string",
                            "description": "消息内容"
                        },
                        "channel": {
                            "type": "string",
                            "description": "发送渠道 (console, slack, email, webhook)",
                            "enum": ["console", "slack", "email", "webhook"]
                        },
                        "template": {
                            "type": "string",
                            "description": "模板名称"
                        },
                        "params": {
                            "type": "object",
                            "description": "额外参数"
                        }
                    },
                    "required": ["message"]
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Void>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 参数验证
                if (input == null || input.message == null || input.message.trim().isEmpty()) {
                    return ToolResult.error("消息内容不能为空");
                }
                
                // 确定渠道
                String channelStr = input.channel != null ? input.channel.toLowerCase() : "console";
                MessageService.Channel channel = parseChannel(channelStr);
                
                // 准备额外参数
                Map<String, String> extraParams = new HashMap<>();
                if (input.params != null) {
                    extraParams.putAll(input.params);
                }
                
                // 发送消息
                MessageResult result;
                if (input.template != null && !input.template.isEmpty()) {
                    result = messageService.sendTemplate(input.template, extraParams, channel).get();
                } else {
                    result = messageService.sendMessage(input.message, channel, extraParams).get();
                }
                
                // 构建输出
                Output output = new Output();
                output.success = result.isSuccess();
                output.channel = channelStr;
                output.message = result.getMessage();
                
                if (result.isSuccess()) {
                    output.detail = "消息已发送到 " + channelStr;
                    if (result.getMessageId() != null) {
                        output.messageId = result.getMessageId();
                    }
                } else {
                    output.error = result.getMessage();
                }
                
                return ToolResult.success(output);
                
            } catch (Exception e) {
                return ToolResult.error("发送消息失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 解析渠道字符串
     */
    private MessageService.Channel parseChannel(String channel) {
        return switch (channel.toLowerCase()) {
            case "slack" -> MessageService.Channel.SLACK;
            case "email" -> MessageService.Channel.EMAIL;
            case "webhook" -> MessageService.Channel.WEBHOOK;
            default -> MessageService.Channel.CONSOLE;
        };
    }
    
    @Override
    public TypeReference<Input> getInputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public TypeReference<Output> getOutputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public ToolValidationResult validate(Input input) {
        if (input == null) {
            return ToolValidationResult.invalid("输入不能为空");
        }
        if (input.message == null || input.message.trim().isEmpty()) {
            return ToolValidationResult.invalid("消息内容不能为空");
        }
        if (input.channel != null) {
            String[] validChannels = {"console", "slack", "email", "webhook"};
            boolean valid = false;
            for (String c : validChannels) {
                if (c.equalsIgnoreCase(input.channel)) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                return ToolValidationResult.invalid("无效的渠道: " + input.channel);
            }
        }
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(Input input) {
        return true;
    }
    
    @Override
    public boolean isDestructive(Input input) {
        return false;
    }
    
    // 输入类
    public static class Input {
        public String message;
        public String channel;
        public String template;
        public Map<String, String> params;
    }
    
    // 输出类
    public static class Output {
        public boolean success;
        public String channel;
        public String message;
        public String messageId;
        public String detail;
        public String error;
    }
}

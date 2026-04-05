package com.jwcode.core.tool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * BriefTool - 简短消息工具
 * 
 * 功能说明：
 * 向用户发送简短消息。这是 AI 与用户通信的主要方式。
 * 支持 Markdown 格式和附件。
 * 
 * 上下文关系：
 * - 被 QueryEngine 调用
 * - 用于向用户显示消息
 * - 支持主动式和正常式两种状态
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class BriefTool implements Tool<BriefTool.Input, BriefTool.Output, BriefTool.Progress> {
    
    public static final String NAME = "SendUserMessage";
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
    
    @Override
    public String getName() {
        return NAME;
    }
    
    @Override
    public String getDescription() {
        return "向用户发送消息。这是你主要的用户通信渠道。支持 Markdown 格式。";
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            String schema = """
               {
                 "type": "object",
                 "properties": {
                   "message": {
                     "type": "string",
                     "description": "发送给用户的消息。支持 Markdown 格式。"
                   },
                   "attachments": {
                     "type": "array",
                     "items": {"type": "string"},
                     "description": "可选的文件路径附件列表"
                   },
                   "status": {
                     "type": "string",
                     "enum": ["normal", "proactive"],
                     "description": "消息状态：normal=正常回复，proactive=主动通知"
                   }
                 },
                 "required": ["message"]
               }
               """;
            return MAPPER.readTree(schema);
        } catch (Exception e) {
            return null;
        }
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
            Input args,
            ToolExecutionContext context,
            java.util.function.Consumer<ToolProgress<Progress>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            String sentAt = Instant.now().toString();
            
            // 记录事件
            logBriefEvent(args.status != null && args.status.equals("proactive"));
            
            Output output = new Output();
            output.message = args.message;
            output.sentAt = sentAt;
            
            if (args.attachments != null && !args.attachments.isEmpty()) {
                // TODO: 处理附件
                output.attachmentCount = args.attachments.size();
            }
            
            return ToolResult.<Output>builder().data(output).build();
        });
    }
    
    private void logBriefEvent(boolean isProactive) {
        // TODO: 实现事件日志
        System.out.println("[BriefTool] Message sent, proactive=" + isProactive);
    }
    
    @Override
    public boolean isConcurrencySafe(Input input) {
        return true;
    }
    
    @Override
    public boolean isReadOnly(Input input) {
        return true;
    }
    
    /**
     * 输入类
     */
    static class Input {
        public String message;
        public List<String> attachments;
        public String status; // "normal" or "proactive"
    }
    
    /**
     * 输出类
     */
    public static class Output {
        public String message;
        public String sentAt;
        public Integer attachmentCount;
    }
    
    /**
     * 进度类
     */
    public static class Progress {
    }
}
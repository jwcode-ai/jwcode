package com.jwcode.core.tool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

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
    private static final Logger LOGGER = Logger.getLogger(BriefTool.class.getName());
    
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
            
            List<AttachmentInfo> attachmentInfos = new ArrayList<>();
            if (args.attachments != null && !args.attachments.isEmpty()) {
                for (String attachmentPath : args.attachments) {
                    try {
                        AttachmentInfo info = readAttachment(attachmentPath);
                        attachmentInfos.add(info);
                    } catch (Exception e) {
                        AttachmentInfo errorInfo = new AttachmentInfo();
                        errorInfo.path = attachmentPath;
                        errorInfo.error = e.getMessage();
                        attachmentInfos.add(errorInfo);
                    }
                }
                output.attachmentCount = attachmentInfos.size();
                output.attachments = attachmentInfos;
            }
            
            return ToolResult.<Output>builder().data(output).build();
        });
    }
    
    private void logBriefEvent(boolean isProactive) {
        String event = Instant.now() + " | BriefTool | " + (isProactive ? "PROACTIVE" : "NORMAL") + " | message sent";
        LOGGER.info(event);
        
        // 同时写入本地事件日志文件
        try {
            Path logDir = Paths.get(System.getProperty("user.dir"), ".jwcode", "logs");
            Files.createDirectories(logDir);
            Path logFile = logDir.resolve("events.log");
            Files.writeString(logFile, event + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            LOGGER.fine("写入事件日志失败: " + e.getMessage());
        }
    }
    
    private AttachmentInfo readAttachment(String pathStr) throws Exception {
        Path path = Paths.get(pathStr);
        if (!Files.exists(path)) {
            throw new Exception("文件不存在: " + pathStr);
        }
        
        String fileName = path.getFileName().toString().toLowerCase();
        boolean isText = fileName.endsWith(".txt") || fileName.endsWith(".md") || fileName.endsWith(".java")
            || fileName.endsWith(".py") || fileName.endsWith(".js") || fileName.endsWith(".ts")
            || fileName.endsWith(".html") || fileName.endsWith(".css") || fileName.endsWith(".json")
            || fileName.endsWith(".xml") || fileName.endsWith(".yaml") || fileName.endsWith(".yml");
        
        AttachmentInfo info = new AttachmentInfo();
        info.path = pathStr;
        info.fileName = path.getFileName().toString();
        info.size = Files.size(path);
        
        if (isText) {
            info.content = Files.readString(path);
            info.type = "text";
        } else {
            byte[] bytes = Files.readAllBytes(path);
            info.base64 = Base64.getEncoder().encodeToString(bytes);
            info.type = "base64";
        }
        
        return info;
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
        public List<AttachmentInfo> attachments;
    }
    
    public static class AttachmentInfo {
        public String path;
        public String fileName;
        public String type;
        public Long size;
        public String content;
        public String base64;
        public String error;
    }
    
    /**
     * 进度类
     */
    public static class Progress {
    }
}
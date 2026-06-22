package com.jwcode.core.message;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 消息服务
 * 支持多渠道消息发送和消息模板
 */
public class MessageService {

    private static final Logger LOGGER = Logger.getLogger(MessageService.class.getName());

    public enum Channel {
        CONSOLE,   // 控制台输出
        SLACK,     // Slack 消息
        EMAIL,     // 邮件
        WEBHOOK    // Webhook 调用
    }
    
    private static MessageService instance;
    
    private final Map<Channel, MessageSender> senders;
    private final Map<String, String> templates;
    private final ObjectMapper objectMapper;
    private final Path historyFile;
    
    // 历史记录配置
    private boolean historyEnabled = true;
    private int maxHistorySize = 1000;
    
    private MessageService() {
        this.senders = new ConcurrentHashMap<>();
        this.templates = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
        
        String userDir = System.getProperty("user.dir");
        this.historyFile = Paths.get(userDir, ".jwcode", "message_history.log");
        
        // 注册默认发送器
        registerDefaultSenders();
        
        // 加载默认模板
        loadDefaultTemplates();
    }
    
    public static synchronized MessageService getInstance() {
        if (instance == null) {
            instance = new MessageService();
        }
        return instance;
    }
    
    /**
     * 注册消息发送器
     */
    public void registerSender(Channel channel, MessageSender sender) {
        senders.put(channel, sender);
    }
    
    /**
     * 发送消息
     */
    public CompletableFuture<MessageResult> sendMessage(String message, Channel channel) {
        return sendMessage(message, channel, null);
    }
    
    /**
     * 发送消息（带额外参数）
     */
    public CompletableFuture<MessageResult> sendMessage(String message, Channel channel, Map<String, String> extraParams) {
        MessageSender sender = senders.get(channel);
        if (sender == null) {
            return CompletableFuture.completedFuture(
                    MessageResult.error("未找到发送器: " + channel));
        }
        
        // 记录历史
        if (historyEnabled) {
            logMessageHistory(message, channel, null);
        }
        
        return sender.send(message, extraParams)
                .thenApply(result -> {
                    if (historyEnabled) {
                        logMessageHistory(message, channel, result);
                    }
                    return result;
                });
    }
    
    /**
     * 发送模板消息
     */
    public CompletableFuture<MessageResult> sendTemplate(String templateName, Map<String, String> params, Channel channel) {
        String template = templates.get(templateName);
        if (template == null) {
            return CompletableFuture.completedFuture(
                    MessageResult.error("模板不存在: " + templateName));
        }
        
        String message = renderTemplate(template, params);
        return sendMessage(message, channel);
    }
    
    /**
     * 广播消息到多个渠道
     */
    public CompletableFuture<Map<Channel, MessageResult>> broadcast(String message, Channel... channels) {
        Map<Channel, CompletableFuture<MessageResult>> futures = new HashMap<>();
        
        for (Channel channel : channels) {
            futures.put(channel, sendMessage(message, channel));
        }
        
        return CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    Map<Channel, MessageResult> results = new HashMap<>();
                    futures.forEach((channel, future) -> {
                        try {
                            results.put(channel, future.get());
                        } catch (Exception e) {
                            results.put(channel, MessageResult.error(e.getMessage()));
                        }
                    });
                    return results;
                });
    }
    
    /**
     * 注册消息模板
     */
    public void registerTemplate(String name, String template) {
        templates.put(name, template);
    }
    
    /**
     * 获取模板
     */
    public String getTemplate(String name) {
        return templates.get(name);
    }
    
    /**
     * 渲染模板
     */
    public String renderTemplate(String template, Map<String, String> params) {
        if (params == null) {
            return template;
        }
        
        String result = template;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
    
    /**
     * 配置 SMTP（用于邮件发送）
     */
    public void configureSmtp(String host, int port, String username, String password, boolean useTls) {
        // EmailMessageSender was removed — javax.mail dependency not available
        // registerSender(Channel.EMAIL, new ConsoleMessageSender()); // fallback to console
    }
    
    /**
     * 配置 Slack Webhook
     */
    public void configureSlack(String webhookUrl) {
        SlackMessageSender slackSender = new SlackMessageSender(webhookUrl);
        registerSender(Channel.SLACK, slackSender);
    }
    
    /**
     * 配置 Webhook
     */
    public void configureWebhook(String defaultUrl, Map<String, String> defaultHeaders) {
        WebhookMessageSender webhookSender = new WebhookMessageSender(defaultUrl, defaultHeaders);
        registerSender(Channel.WEBHOOK, webhookSender);
    }
    
    /**
     * 设置历史记录启用状态
     */
    public void setHistoryEnabled(boolean enabled) {
        this.historyEnabled = enabled;
    }
    
    /**
     * 设置最大历史记录数
     */
    public void setMaxHistorySize(int size) {
        this.maxHistorySize = size;
    }
    
    private void registerDefaultSenders() {
        // 控制台发送器（默认）
        registerSender(Channel.CONSOLE, new ConsoleMessageSender());
    }
    
    private void loadDefaultTemplates() {
        // 默认模板
        registerTemplate("build_success", 
                "✅ 构建成功\n项目: {{project}}\n分支: {{branch}}\n提交: {{commit}}");
        
        registerTemplate("build_failure", 
                "❌ 构建失败\n项目: {{project}}\n分支: {{branch}}\n错误: {{error}}");
        
        registerTemplate("deployment", 
                "🚀 部署通知\n环境: {{environment}}\n版本: {{version}}\n时间: {{time}}");
        
        registerTemplate("code_review", 
                "👀 代码审查请求\n项目: {{project}}\n提交者: {{author}}\nPR: {{pr_url}}");
    }
    
    private void logMessageHistory(String message, Channel channel, MessageResult result) {
        try {
            Files.createDirectories(historyFile.getParent());
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            String status = result != null ? (result.isSuccess() ? "SUCCESS" : "FAILED") : "PENDING";
            String logEntry = String.format("[%s] [%s] [%s] %s%n", timestamp, channel, status, message);
            
            Files.writeString(historyFile, logEntry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            
        } catch (IOException e) {
            LOGGER.warning("Failed to record message history: " + e.getMessage());
        }
    }
    
    /**
     * 清空消息历史
     */
    public void clearHistory() throws IOException {
        if (Files.exists(historyFile)) {
            Files.delete(historyFile);
        }
    }
}

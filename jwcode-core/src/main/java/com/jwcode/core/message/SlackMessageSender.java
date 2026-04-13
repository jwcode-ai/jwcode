package com.jwcode.core.message;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Slack 消息发送器
 * 通过 Webhook 发送消息到 Slack
 */
public class SlackMessageSender implements MessageSender {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private final HttpClient httpClient;
    private String webhookUrl;
    private String defaultChannel;
    private String username;
    private String iconEmoji;
    
    public SlackMessageSender() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
    }
    
    public SlackMessageSender(String webhookUrl) {
        this();
        this.webhookUrl = webhookUrl;
    }
    
    public SlackMessageSender(String webhookUrl, String defaultChannel) {
        this(webhookUrl);
        this.defaultChannel = defaultChannel;
    }
    
    @Override
    public CompletableFuture<MessageResult> send(String message, Map<String, String> params) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return CompletableFuture.completedFuture(
                    MessageResult.error("Slack Webhook URL 未配置"));
        }
        
        try {
            Map<String, Object> payload = buildPayload(message, params);
            String jsonBody = OBJECT_MAPPER.writeValueAsString(payload);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            String body = response.body();
                            // Slack 返回 "ok" 表示成功
                            if ("ok".equalsIgnoreCase(body)) {
                                return MessageResult.success();
                            } else {
                                return MessageResult.error("Slack 返回错误: " + body);
                            }
                        } else {
                            return MessageResult.error("HTTP 错误: " + response.statusCode());
                        }
                    })
                    .exceptionally(throwable -> 
                            MessageResult.error("发送失败: " + throwable.getMessage()));
            
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    MessageResult.error("构建消息失败: " + e.getMessage()));
        }
    }
    
    /**
     * 构建 Slack 消息 payload
     */
    private Map<String, Object> buildPayload(String message, Map<String, String> params) {
        Map<String, Object> payload = new HashMap<>();
        
        // 支持富文本格式
        String formattedText = formatMessage(message, params);
        payload.put("text", formattedText);
        
        // 可选参数
        if (defaultChannel != null && !defaultChannel.isEmpty()) {
            payload.put("channel", defaultChannel);
        }
        
        if (username != null && !username.isEmpty()) {
            payload.put("username", username);
        }
        
        if (iconEmoji != null && !iconEmoji.isEmpty()) {
            payload.put("icon_emoji", iconEmoji);
        }
        
        // 支持 Block Kit 格式
        if (params != null && params.containsKey("use_blocks") && "true".equals(params.get("use_blocks"))) {
            payload.put("blocks", buildBlocks(message, params));
        }
        
        // 附件
        if (params != null && params.containsKey("attachment_color")) {
            payload.put("attachments", buildAttachments(message, params));
        }
        
        return payload;
    }
    
    /**
     * 格式化消息内容
     */
    private String formatMessage(String message, Map<String, String> params) {
        if (params == null) {
            return message;
        }
        
        String result = message;
        // 转换 Markdown 格式到 Slack 格式
        result = result.replaceAll("\\*\\*(.+?)\\*\\*", "*$1*");  // Bold
        result = result.replaceAll("__(.+?)__", "_$1_");          // Italic
        result = result.replaceAll("`(.+?)`", "`$1`");            // Code
        
        return result;
    }
    
    /**
     * 构建 Slack Block Kit blocks
     */
    private java.util.List<Map<String, Object>> buildBlocks(String message, Map<String, String> params) {
        java.util.List<Map<String, Object>> blocks = new java.util.ArrayList<>();
        
        // Header block
        Map<String, Object> headerBlock = new HashMap<>();
        headerBlock.put("type", "header");
        Map<String, Object> headerText = new HashMap<>();
        headerText.put("type", "plain_text");
        headerText.put("text", params != null ? params.getOrDefault("header", "通知") : "通知");
        headerBlock.put("text", headerText);
        blocks.add(headerBlock);
        
        // Section block
        Map<String, Object> sectionBlock = new HashMap<>();
        sectionBlock.put("type", "section");
        Map<String, Object> sectionText = new HashMap<>();
        sectionText.put("type", "mrkdwn");
        sectionText.put("text", message);
        sectionBlock.put("text", sectionText);
        blocks.add(sectionBlock);
        
        // Divider
        Map<String, Object> dividerBlock = new HashMap<>();
        dividerBlock.put("type", "divider");
        blocks.add(dividerBlock);
        
        return blocks;
    }
    
    /**
     * 构建附件
     */
    private java.util.List<Map<String, Object>> buildAttachments(String message, Map<String, String> params) {
        java.util.List<Map<String, Object>> attachments = new java.util.ArrayList<>();
        
        Map<String, Object> attachment = new HashMap<>();
        attachment.put("color", params.getOrDefault("attachment_color", "good"));
        attachment.put("text", message);
        
        if (params.containsKey("footer")) {
            attachment.put("footer", params.get("footer"));
        }
        
        if (params.containsKey("ts")) {
            attachment.put("ts", Long.parseLong(params.get("ts")));
        }
        
        attachments.add(attachment);
        return attachments;
    }
    
    @Override
    public boolean isConfigured() {
        return webhookUrl != null && !webhookUrl.isEmpty();
    }
    
    @Override
    public String getName() {
        return "Slack";
    }
    
    // Getters and Setters
    public String getWebhookUrl() {
        return webhookUrl;
    }
    
    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }
    
    public String getDefaultChannel() {
        return defaultChannel;
    }
    
    public void setDefaultChannel(String defaultChannel) {
        this.defaultChannel = defaultChannel;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getIconEmoji() {
        return iconEmoji;
    }
    
    public void setIconEmoji(String iconEmoji) {
        this.iconEmoji = iconEmoji;
    }
}

package com.jwcode.core.message;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * 邮件消息发送器
 * 使用 SMTP 协议发送邮件
 */
public class EmailMessageSender implements MessageSender {
    
    private String smtpHost;
    private int smtpPort;
    private String username;
    private String password;
    private boolean useTls;
    
    // 默认发件人和收件人
    private String fromAddress;
    private String toAddress;
    private String subjectPrefix;
    
    public EmailMessageSender() {
        this.smtpPort = 587;
        this.useTls = true;
        this.subjectPrefix = "[JwCode] ";
    }
    
    public EmailMessageSender(String smtpHost, int smtpPort, String username, String password, boolean useTls) {
        this();
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.username = username;
        this.password = password;
        this.useTls = useTls;
    }
    
    @Override
    public CompletableFuture<MessageResult> send(String message, Map<String, String> params) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(
                    MessageResult.error("SMTP 未配置"));
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String to = params != null && params.containsKey("to") 
                        ? params.get("to") 
                        : this.toAddress;
                String subject = params != null && params.containsKey("subject")
                        ? params.get("subject")
                        : subjectPrefix + "通知";
                
                if (to == null || to.isEmpty()) {
                    return MessageResult.error("收件人地址未指定");
                }
                
                // 发送邮件
                sendEmail(to, subject, message);
                
                return MessageResult.success();
                
            } catch (Exception e) {
                return MessageResult.error("邮件发送失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 发送邮件（使用 JavaMail API）
     * 注意：这里使用模拟实现，实际项目中需要添加 javax.mail 依赖
     */
    private void sendEmail(String to, String subject, String content) throws Exception {
        // 实际实现需要使用 javax.mail 库
        // 以下是伪代码，展示实现思路
        
        /*
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(useTls));
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));
        
        Session session = Session.getInstance(props, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
        
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromAddress != null ? fromAddress : username));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);
        message.setText(content);
        
        Transport.send(message);
        */
        
        // 模拟发送成功
        System.out.println("[Email] To: " + to);
        System.out.println("[Email] Subject: " + subject);
        System.out.println("[Email] Content: " + content);
    }
    
    /**
     * 发送 HTML 邮件
     */
    public CompletableFuture<MessageResult> sendHtml(String htmlContent, Map<String, String> params) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(
                    MessageResult.error("SMTP 未配置"));
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String to = params != null && params.containsKey("to") 
                        ? params.get("to") 
                        : this.toAddress;
                String subject = params != null && params.containsKey("subject")
                        ? params.get("subject")
                        : subjectPrefix + "通知";
                
                if (to == null || to.isEmpty()) {
                    return MessageResult.error("收件人地址未指定");
                }
                
                // 发送 HTML 邮件
                sendHtmlEmail(to, subject, htmlContent);
                
                return MessageResult.success();
                
            } catch (Exception e) {
                return MessageResult.error("HTML 邮件发送失败: " + e.getMessage());
            }
        });
    }
    
    private void sendHtmlEmail(String to, String subject, String htmlContent) throws Exception {
        // 实际实现需要使用 javax.mail 库
        /*
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(useTls));
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));
        
        Session session = Session.getInstance(props, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
        
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromAddress != null ? fromAddress : username));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);
        message.setContent(htmlContent, "text/html; charset=utf-8");
        
        Transport.send(message);
        */
        
        System.out.println("[Email HTML] To: " + to);
        System.out.println("[Email HTML] Subject: " + subject);
        System.out.println("[Email HTML] Content: " + htmlContent);
    }
    
    @Override
    public boolean isConfigured() {
        return smtpHost != null && !smtpHost.isEmpty()
                && username != null && !username.isEmpty();
    }
    
    @Override
    public String getName() {
        return "Email";
    }
    
    // Getters and Setters
    public String getSmtpHost() {
        return smtpHost;
    }
    
    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }
    
    public int getSmtpPort() {
        return smtpPort;
    }
    
    public void setSmtpPort(int smtpPort) {
        this.smtpPort = smtpPort;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public boolean isUseTls() {
        return useTls;
    }
    
    public void setUseTls(boolean useTls) {
        this.useTls = useTls;
    }
    
    public String getFromAddress() {
        return fromAddress;
    }
    
    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }
    
    public String getToAddress() {
        return toAddress;
    }
    
    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }
    
    public String getSubjectPrefix() {
        return subjectPrefix;
    }
    
    public void setSubjectPrefix(String subjectPrefix) {
        this.subjectPrefix = subjectPrefix;
    }
}

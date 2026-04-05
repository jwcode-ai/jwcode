package com.jwcode.core.buddy;

import java.time.Instant;

/**
 * CompanionPrompt - 伙伴提示系统
 * 
 * 功能说明：
 * 管理伙伴精灵的提示气泡，支持多种提示类型和样式。
 * 提示可以显示上下文相关信息、鼓励话语、错误提示等。
 * 
 * 核心特性：
 * - 多种提示类型（信息、警告、错误、成功）
 * - 提示优先级
 * - 自动过期
 * - 自定义样式
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class CompanionPrompt {
    
    /**
     * 提示类型枚举
     */
    public enum PromptType {
        /** 信息 */
        INFO,
        /** 警告 */
        WARNING,
        /** 错误 */
        ERROR,
        /** 成功 */
        SUCCESS,
        /** 提示 */
        TIP,
        /** 鼓励 */
        ENCOURAGEMENT,
        /** 问题 */
        QUESTION
    }
    
    /**
     * 提示优先级枚举
     */
    public enum PromptPriority {
        /** 低 */
        LOW,
        /** 中 */
        NORMAL,
        /** 高 */
        HIGH,
        /** 紧急 */
        URGENT
    }
    
    private final PromptType type;
    private final PromptPriority priority;
    private final String message;
    private final String title;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final String styleClass;
    private final boolean dismissible;
    
    /**
     * 构造函数
     */
    private CompanionPrompt(Builder builder) {
        this.type = builder.type;
        this.priority = builder.priority;
        this.message = builder.message;
        this.title = builder.title;
        this.createdAt = Instant.now();
        this.expiresAt = builder.expiresAt;
        this.styleClass = builder.styleClass;
        this.dismissible = builder.dismissible;
    }
    
    /**
     * 获取提示类型
     */
    public PromptType getType() {
        return type;
    }
    
    /**
     * 获取优先级
     */
    public PromptPriority getPriority() {
        return priority;
    }
    
    /**
     * 获取消息内容
     */
    public String getMessage() {
        return message;
    }
    
    /**
     * 获取标题
     */
    public String getTitle() {
        return title;
    }
    
    /**
     * 获取创建时间
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    /**
     * 获取过期时间
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }
    
    /**
     * 获取样式类
     */
    public String getStyleClass() {
        return styleClass;
    }
    
    /**
     * 检查是否可关闭
     */
    public boolean isDismissible() {
        return dismissible;
    }
    
    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return Instant.now().isAfter(expiresAt);
    }
    
    /**
     * 获取显示文本
     */
    public String getDisplayText() {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isEmpty()) {
            sb.append(title).append(": ");
        }
        sb.append(message);
        return sb.toString();
    }
    
    /**
     * 创建信息提示
     */
    public static CompanionPrompt info(String message) {
        return new Builder()
                .type(PromptType.INFO)
                .message(message)
                .build();
    }
    
    /**
     * 创建警告提示
     */
    public static CompanionPrompt warning(String message) {
        return new Builder()
                .type(PromptType.WARNING)
                .message(message)
                .priority(PromptPriority.HIGH)
                .build();
    }
    
    /**
     * 创建错误提示
     */
    public static CompanionPrompt error(String message) {
        return new Builder()
                .type(PromptType.ERROR)
                .message(message)
                .priority(PromptPriority.URGENT)
                .build();
    }
    
    /**
     * 创建成功提示
     */
    public static CompanionPrompt success(String message) {
        return new Builder()
                .type(PromptType.SUCCESS)
                .message(message)
                .build();
    }
    
    /**
     * 创建提示
     */
    public static CompanionPrompt tip(String message) {
        return new Builder()
                .type(PromptType.TIP)
                .message(message)
                .build();
    }
    
    /**
     * 创建鼓励提示
     */
    public static CompanionPrompt encouragement(String message) {
        return new Builder()
                .type(PromptType.ENCOURAGEMENT)
                .message(message)
                .build();
    }
    
    /**
     * 创建问题提示
     */
    public static CompanionPrompt question(String message) {
        return new Builder()
                .type(PromptType.QUESTION)
                .message(message)
                .priority(PromptPriority.HIGH)
                .dismissible(false)
                .build();
    }
    
    /**
     * 构建器类
     */
    public static class Builder {
        private PromptType type = PromptType.INFO;
        private PromptPriority priority = PromptPriority.NORMAL;
        private String message = "";
        private String title;
        private Instant expiresAt;
        private String styleClass;
        private boolean dismissible = true;
        
        public Builder type(PromptType type) {
            this.type = type;
            return this;
        }
        
        public Builder priority(PromptPriority priority) {
            this.priority = priority;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder title(String title) {
            this.title = title;
            return this;
        }
        
        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }
        
        public Builder expiresInSeconds(long seconds) {
            this.expiresAt = Instant.now().plusSeconds(seconds);
            return this;
        }
        
        public Builder styleClass(String styleClass) {
            this.styleClass = styleClass;
            return this;
        }
        
        public Builder dismissible(boolean dismissible) {
            this.dismissible = dismissible;
            return this;
        }
        
        public CompanionPrompt build() {
            return new CompanionPrompt(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("CompanionPrompt{type=%s, priority=%s, message='%s'}",
                type, priority, message);
    }
}
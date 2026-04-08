package com.jwcode.ui.components;

import com.googlecode.lanterna.TextColor;

/**
 * Message - 消息组件
 * 
 * 功能说明：
 * 显示信息、成功、警告、错误等消息，支持自动消失和可关闭功能。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class Message implements Component {
    
    private String content;
    private MessageType type;
    private boolean closable;
    private boolean closed;
    private int autoCloseDelay; // 毫秒，0 表示不自动关闭
    private long displayStartTime;
    private TextColor foregroundColor;
    private TextColor backgroundColor;
    private String icon;
    
    public Message() {
        this("");
    }
    
    public Message(String content) {
        this(content, MessageType.INFO);
    }
    
    public Message(String content, MessageType type) {
        this.content = content;
        this.type = type;
        this.closable = true;
        this.closed = false;
        this.autoCloseDelay = 0;
        this.displayStartTime = 0;
        initDefaults();
    }
    
    /**
     * 初始化默认值
     */
    private void initDefaults() {
        switch (type) {
            case INFO:
                foregroundColor = TextColor.ANSI.BLUE;
                backgroundColor = TextColor.ANSI.DEFAULT;
                icon = "ℹ";
                break;
            case SUCCESS:
                foregroundColor = TextColor.ANSI.GREEN;
                backgroundColor = TextColor.ANSI.DEFAULT;
                icon = "✓";
                break;
            case WARNING:
                foregroundColor = TextColor.ANSI.YELLOW;
                backgroundColor = TextColor.ANSI.DEFAULT;
                icon = "⚠";
                break;
            case ERROR:
                foregroundColor = TextColor.ANSI.RED;
                backgroundColor = TextColor.ANSI.DEFAULT;
                icon = "✗";
                break;
        }
    }
    
    @Override
    public String render() {
        if (closed) {
            return "";
        }
        
        // 检查自动关闭
        if (autoCloseDelay > 0 && displayStartTime > 0) {
            long elapsed = System.currentTimeMillis() - displayStartTime;
            if (elapsed >= autoCloseDelay) {
                closed = true;
                return "";
            }
        }
        
        StringBuilder sb = new StringBuilder();
        
        // 开始框线
        sb.append("╭");
        for (int i = 0; i < Math.max(20, content.length() + 4); i++) {
            sb.append("─");
        }
        sb.append("╮\n");
        
        // 内容行
        sb.append("│ ");
        sb.append(icon);
        sb.append(" ");
        sb.append(content);
        int padding = Math.max(0, content.length() + 4 - content.length() - 2);
        for (int i = 0; i < padding; i++) {
            sb.append(" ");
        }
        if (closable) {
            sb.append("[×]");
        } else {
            sb.append(" │");
        }
        sb.append("\n");
        
        // 结束框线
        sb.append("╰");
        for (int i = 0; i < Math.max(20, content.length() + 4); i++) {
            sb.append("─");
        }
        sb.append("╯");
        
        return sb.toString();
    }
    
    /**
     * 显示消息
     */
    public void show() {
        closed = false;
        displayStartTime = System.currentTimeMillis();
    }
    
    /**
     * 关闭消息
     */
    public void close() {
        closed = true;
    }
    
    /**
     * 是否已关闭
     */
    public boolean isClosed() {
        return closed;
    }
    
    /**
     * 设置内容
     */
    public void setContent(String content) {
        this.content = content;
    }
    
    /**
     * 获取内容
     */
    public String getContent() {
        return content;
    }
    
    /**
     * 设置类型
     */
    public void setType(MessageType type) {
        this.type = type;
        initDefaults();
    }
    
    /**
     * 获取类型
     */
    public MessageType getType() {
        return type;
    }
    
    /**
     * 设置是否可关闭
     */
    public void setClosable(boolean closable) {
        this.closable = closable;
    }
    
    /**
     * 设置自动关闭延迟（毫秒）
     */
    public void setAutoCloseDelay(int delay) {
        this.autoCloseDelay = delay;
    }
    
    /**
     * 设置前景色
     */
    public void setForegroundColor(TextColor color) {
        this.foregroundColor = color;
    }
    
    /**
     * 设置背景色
     */
    public void setBackgroundColor(TextColor color) {
        this.backgroundColor = color;
    }
    
    /**
     * 设置图标
     */
    public void setIcon(String icon) {
        this.icon = icon;
    }
    
    /**
     * 创建信息消息
     */
    public static Message info(String content) {
        return new Message(content, MessageType.INFO);
    }
    
    /**
     * 创建成功消息
     */
    public static Message success(String content) {
        return new Message(content, MessageType.SUCCESS);
    }
    
    /**
     * 创建警告消息
     */
    public static Message warning(String content) {
        return new Message(content, MessageType.WARNING);
    }
    
    /**
     * 创建错误消息
     */
    public static Message error(String content) {
        return new Message(content, MessageType.ERROR);
    }
    
    /**
     * 消息类型枚举
     */
    public enum MessageType {
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }
    
    /**
     * 消息队列管理类
     */
    public static class MessageQueue {
        private final java.util.List<Message> messages;
        private final int maxMessages;
        
        public MessageQueue() {
            this(10);
        }
        
        public MessageQueue(int maxMessages) {
            this.messages = new java.util.ArrayList<>();
            this.maxMessages = maxMessages;
        }
        
        /**
         * 添加消息
         */
        public void add(Message message) {
            messages.add(message);
            message.show();
            
            // 限制消息数量
            while (messages.size() > maxMessages) {
                messages.remove(0);
            }
        }
        
        /**
         * 添加信息消息
         */
        public void addInfo(String content) {
            add(Message.info(content));
        }
        
        /**
         * 添加成功消息
         */
        public void addSuccess(String content) {
            add(Message.success(content));
        }
        
        /**
         * 添加警告消息
         */
        public void addWarning(String content) {
            add(Message.warning(content));
        }
        
        /**
         * 添加错误消息
         */
        public void addError(String content) {
            add(Message.error(content));
        }
        
        /**
         * 获取所有消息
         */
        public java.util.List<Message> getAll() {
            return new java.util.ArrayList<>(messages);
        }
        
        /**
         * 清除已关闭的消息
         */
        public void clearClosed() {
            messages.removeIf(Message::isClosed);
        }
        
        /**
         * 清除所有消息
         */
        public void clear() {
            messages.clear();
        }
        
        /**
         * 渲染所有消息
         */
        public String render() {
            clearClosed();
            StringBuilder sb = new StringBuilder();
            for (Message message : messages) {
                sb.append(message.render()).append("\n");
            }
            return sb.toString();
        }
    }
}
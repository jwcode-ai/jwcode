package com.jwcode.ui.components;

import com.jwcode.core.model.Message;

import java.util.*;

/**
 * MessageList - 消息列表组件
 * 
 * 用于显示对话消息历史，支持不同角色（用户/助手/系统）的样式区分
 * 参照 Claude Code/Kimi Code 的消息渲染设计
 */
public class MessageList implements Component {
    
    private List<MessageItem> messages;
    private int visibleStart;
    private int visibleCount;
    private int maxWidth;
    private boolean showTimestamps;
    private boolean compact;
    private String sourceLabel; // 用于显示子代理来源

    /** 虚拟滚动：可见区域高度（行数），0 = 禁用虚拟滚动 */
    private int viewportHeight = 0;
    /** 虚拟滚动：是否自动滚动到底部 */
    private boolean autoScroll = true;
    
    // ANSI 颜色
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String MAGENTA = "\u001B[35m";
    
    public MessageList() {
        this.messages = new ArrayList<>();
        this.visibleStart = 0;
        this.visibleCount = 20;
        this.maxWidth = 80;
        this.showTimestamps = false;
        this.compact = false;
        this.sourceLabel = null;
    }
    
    public MessageList messages(List<Message> msgs) {
        this.messages = new ArrayList<>();
        for (Message msg : msgs) {
            addMessage(msg);
        }
        return this;
    }
    
    public MessageList addMessage(Message msg) {
        MessageItem item = new MessageItem();
        item.content = extractContent(msg);
        item.role = msg.getRole();
        item.timestamp = System.currentTimeMillis();
        item.isStreaming = false;
        this.messages.add(item);
        return this;
    }
    
    public MessageList addUserMessage(String content) {
        MessageItem item = new MessageItem();
        item.content = content;
        item.role = Message.Role.USER;
        item.timestamp = System.currentTimeMillis();
        this.messages.add(item);
        return this;
    }
    
    public MessageList addAssistantMessage(String content) {
        MessageItem item = new MessageItem();
        item.content = content;
        item.role = Message.Role.ASSISTANT;
        item.timestamp = System.currentTimeMillis();
        this.messages.add(item);
        return this;
    }
    
    public MessageList addSystemMessage(String content) {
        MessageItem item = new MessageItem();
        item.content = content;
        item.role = Message.Role.SYSTEM;
        item.timestamp = System.currentTimeMillis();
        this.messages.add(item);
        return this;
    }
    
    public MessageList sourceLabel(String label) {
        this.sourceLabel = label;
        return this;
    }
    
    public MessageList maxWidth(int width) {
        this.maxWidth = width;
        return this;
    }
    
    public MessageList compact(boolean compact) {
        this.compact = compact;
        return this;
    }
    
    public MessageList showTimestamps(boolean show) {
        this.showTimestamps = show;
        return this;
    }

    // ========== 虚拟滚动支持 ==========

    /**
     * 设置视口高度（行数）。启用后，render() 只渲染可见区域的消息。
     *
     * @param height 视口高度（行数），≤ 0 表示禁用虚拟滚动
     */
    public MessageList viewportHeight(int height) {
        this.viewportHeight = height;
        return this;
    }

    /**
     * 设置是否自动滚动到底部。
     */
    public MessageList autoScroll(boolean autoScroll) {
        this.autoScroll = autoScroll;
        return this;
    }

    /**
     * 向上滚动一行。
     */
    public void scrollUp() {
        if (visibleStart > 0) {
            visibleStart--;
        }
    }

    /**
     * 向下滚动一行。
     */
    public void scrollDown() {
        if (visibleStart < getMaxScrollStart()) {
            visibleStart++;
        }
    }

    /**
     * 滚动到顶部。
     */
    public void scrollToTop() {
        visibleStart = 0;
    }

    /**
     * 滚动到底部。
     */
    public void scrollToBottom() {
        visibleStart = getMaxScrollStart();
        autoScroll = true;
    }

    /**
     * 获取最大滚动起始位置。
     */
    private int getMaxScrollStart() {
        return Math.max(0, messages.size() - visibleCount);
    }

    /**
     * 更新最后一条消息的内容（用于流式输出）
     */
    public MessageList updateLastMessage(String content) {
        if (!messages.isEmpty()) {
            MessageItem last = messages.get(messages.size() - 1);
            last.content = content;
            last.isStreaming = true;
        }
        return this;
    }
    
    /**
     * 标记最后一条消息为完成
     */
    public MessageList finishLastMessage() {
        if (!messages.isEmpty()) {
            messages.get(messages.size() - 1).isStreaming = false;
        }
        return this;
    }
    
    private String extractContent(Message msg) {
        if (msg == null || msg.getContent() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Message.ContentBlock block : msg.getContent()) {
            if (block instanceof Message.TextContent) {
                sb.append(((Message.TextContent) block).getText());
            }
        }
        return sb.toString();
    }
    
    @Override
    public String render() {
        if (messages.isEmpty()) {
            return "";
        }

        // 虚拟滚动模式：根据视口高度计算可见消息范围
        if (viewportHeight > 0) {
            return renderVirtualScroll();
        }

        StringBuilder sb = new StringBuilder();
        int endIndex = Math.min(visibleStart + visibleCount, messages.size());

        for (int i = visibleStart; i < endIndex; i++) {
            MessageItem item = messages.get(i);
            sb.append(renderMessage(item, i));

            if (!compact && i < endIndex - 1) {
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 虚拟滚动渲染：只渲染视口高度内可见的消息行。
     *
     * <p>算法：从最后一条消息开始反向累加行数，直到填满视口或到达顶部。</p>
     */
    private String renderVirtualScroll() {
        StringBuilder sb = new StringBuilder();
        int availableLines = viewportHeight;
        int startIdx;

        if (autoScroll) {
            // 自动滚动：从底部开始
            startIdx = messages.size() - 1;
            int lines = 0;

            // 反向遍历消息，累加行数
            for (int i = messages.size() - 1; i >= 0; i--) {
                String rendered = renderMessage(messages.get(i), i);
                int msgLines = rendered.split("\n", -1).length;
                if (lines + msgLines > availableLines) {
                    // 如果当前消息超出可用行数，只取部分
                    startIdx = i;
                    break;
                }
                lines += msgLines;
                startIdx = i;
                if (lines >= availableLines) break;
            }

            visibleStart = startIdx;
        }

        // 从 startIdx 开始渲染消息
        int renderedLines = 0;
        for (int i = visibleStart; i < messages.size(); i++) {
            String rendered = renderMessage(messages.get(i), i);
            String[] lines = rendered.split("\n", -1);

            if (renderedLines + lines.length > availableLines) {
                // 只渲染部分行
                int remainingLines = availableLines - renderedLines;
                for (int j = 0; j < remainingLines && j < lines.length; j++) {
                    sb.append(lines[j]);
                    if (j < remainingLines - 1) sb.append("\n");
                }
                break;
            }

            if (renderedLines > 0) sb.append("\n");
            sb.append(rendered);
            renderedLines += lines.length;

            if (renderedLines >= availableLines) break;
        }

        return sb.toString();
    }
    
    private String renderMessage(MessageItem item, int index) {
        StringBuilder sb = new StringBuilder();
        String[] lines = wrapText(item.content, maxWidth - 4).split("\n");
        
        // 获取角色颜色和标签
        String color;
        String roleLabel;
        switch (item.role) {
            case USER:
                color = GREEN;
                roleLabel = "用户";
                break;
            case ASSISTANT:
                color = CYAN;
                roleLabel = "助手";
                break;
            case SYSTEM:
                color = YELLOW;
                roleLabel = "系统";
                break;
            default:
                color = DIM;
                roleLabel = "";
        }
        
        if (compact) {
            // 紧凑模式：单行显示
            String preview = lines.length > 0 ? lines[0] : "";
            if (preview.length() > maxWidth - 10) {
                preview = preview.substring(0, maxWidth - 13) + "...";
            }
            sb.append(color).append(roleLabel).append(": ").append(RESET);
            sb.append(preview);
            if (lines.length > 1) {
                sb.append(" ...").append(RESET);
            }
            if (item.isStreaming) {
                sb.append(DIM).append(" ◐").append(RESET);
            }
        } else {
            // 完整模式
            sb.append("\n");
            
            // 子代理来源标签
            if (sourceLabel != null && !sourceLabel.isEmpty()) {
                sb.append(DIM).append("│");
                sb.append(MAGENTA).append(" ← ").append(sourceLabel).append(RESET);
                sb.append("\n");
            }
            
            // 消息头
            sb.append(color);
            sb.append("┌─ ").append(BOLD).append(roleLabel).append(RESET).append(color);
            for (int i = 0; i < maxWidth - roleLabel.length() - 4; i++) {
                sb.append("─");
            }
            sb.append("┐").append(RESET).append("\n");
            
            // 消息内容
            for (String line : lines) {
                sb.append(color).append("│").append(RESET).append(" ");
                sb.append(line);
                // 填充
                for (int i = line.length(); i < maxWidth - 2; i++) {
                    sb.append(' ');
                }
                sb.append(color).append(" │").append(RESET).append("\n");
            }
            
            // 消息尾
            sb.append(color).append("└");
            for (int i = 0; i < maxWidth; i++) {
                sb.append("─");
            }
            sb.append("┘").append(RESET);
            
            // 流式指示器
            if (item.isStreaming) {
                sb.append(DIM).append(" ◐").append(RESET);
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 文本自动换行
     */
    private String wrapText(String text, int width) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        String[] paragraphs = text.split("\n");
        
        for (int p = 0; p < paragraphs.length; p++) {
            if (p > 0) {
                sb.append("\n");
            }
            
            String para = paragraphs[p];
            int pos = 0;
            
            while (pos < para.length()) {
                if (pos > 0) {
                    sb.append("\n");
                }
                
                if (pos > 0) {
                    sb.append("  "); // 续行缩进
                }
                
                int end = Math.min(pos + width, para.length());
                
                if (end < para.length()) {
                    // 尝试在空格处断开
                    int breakPos = para.lastIndexOf(' ', end);
                    if (breakPos > pos + width / 2) {
                        end = breakPos;
                    }
                }
                
                sb.append(para.substring(pos, end));
                pos = end;
                
                // 跳过连续空格
                while (pos < para.length() && para.charAt(pos) == ' ') {
                    pos++;
                }
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 消息项内部类
     */
    private static class MessageItem {
        String content;
        Message.Role role;
        long timestamp;
        boolean isStreaming;
    }
}

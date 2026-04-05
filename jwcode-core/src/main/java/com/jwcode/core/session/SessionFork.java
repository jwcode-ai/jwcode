package com.jwcode.core.session;

import com.jwcode.core.model.Message;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Session Fork - 会话分支机制
 * 
 * 功能说明：
 * 允许从现有会话创建分支（Fork），子会话继承父会话的上下文，但相互独立。
 * 这是实现子 Agent 上下文继承的基础。
 * 
 * 继承策略：
 * - 消息历史：深拷贝（子会话可以看到历史，但修改不影响父会话）
 * - 工作目录：引用（共享相同的工作目录）
 * - 元数据：深拷贝
 * - 模型配置：深拷贝
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class SessionFork {
    
    private final Session parentSession;
    private final String forkReason;
    private final Map<String, Object> inheritedContext;
    
    public SessionFork(Session parentSession, String forkReason) {
        this.parentSession = parentSession;
        this.forkReason = forkReason;
        this.inheritedContext = new HashMap<>();
    }
    
    /**
     * 添加要继承的上下文数据
     */
    public SessionFork withContext(String key, Object value) {
        this.inheritedContext.put(key, value);
        return this;
    }
    
    /**
     * 创建 Fork 后的新 Session
     */
    public Session execute() {
        // 生成新的 Session ID，保留父会话信息
        String forkId = parentSession.getId() + "-fork-" + UUID.randomUUID().toString().substring(0, 8);
        
        // 创建新会话（继承工作目录）
        Session forkedSession = new Session(forkId, parentSession.getWorkingDirectory());
        
        // 深拷贝消息历史
        List<Message> parentMessages = parentSession.getMessages();
        for (Message msg : parentMessages) {
            forkedSession.addMessage(cloneMessage(msg));
        }
        
        // 继承模型配置
        forkedSession.setModel(parentSession.getModel());
        
        // 深拷贝元数据
        forkedSession.setMetadata("parentSessionId", parentSession.getId());
        forkedSession.setMetadata("forkReason", forkReason);
        forkedSession.setMetadata("forkTime", System.currentTimeMillis());
        
        // 添加继承的上下文
        for (Map.Entry<String, Object> entry : inheritedContext.entrySet()) {
            forkedSession.setMetadata(entry.getKey(), entry.getValue());
        }
        
        // 设置标题
        String title = parentSession.getTitle();
        if (title != null) {
            forkedSession.setTitle(title + " [Fork: " + forkReason + "]");
        } else {
            forkedSession.setTitle("Fork: " + forkReason);
        }
        
        return forkedSession;
    }
    
    /**
     * 克隆消息（深拷贝）
     */
    private Message cloneMessage(Message original) {
        // 消息是不可变的，可以直接引用，但为了安全起见，我们创建新实例
        switch (original.getRole()) {
            case USER:
                return Message.createUserMessage(getTextContent(original));
            case ASSISTANT:
                return Message.createAssistantMessage(getTextContent(original));
            case SYSTEM:
                return Message.createSystemMessage(getTextContent(original));
            case TOOL:
                // 工具消息需要特殊处理
                return cloneToolMessage(original);
            default:
                return Message.createUserMessage(getTextContent(original));
        }
    }
    
    /**
     * 获取消息的文本内容
     */
    private String getTextContent(Message message) {
        if (message.getContent() == null || message.getContent().isEmpty()) {
            return "";
        }
        
        for (Message.ContentBlock block : message.getContent()) {
            if (block instanceof Message.TextContent) {
                return ((Message.TextContent) block).getText();
            }
        }
        return "";
    }
    
    /**
     * 克隆工具消息
     */
    private Message cloneToolMessage(Message original) {
        for (Message.ContentBlock block : original.getContent()) {
            if (block instanceof Message.ToolResultContent) {
                Message.ToolResultContent toolContent = (Message.ToolResultContent) block;
                return Message.createToolResultMessage(
                    toolContent.getToolUseId(),
                    toolContent.getToolName(),
                    toolContent.getResult()
                );
            }
        }
        return Message.createUserMessage("[Tool Result]");
    }
    
    /**
     * 创建 Fork 构建器的便捷方法
     */
    public static SessionFork from(Session parent, String reason) {
        return new SessionFork(parent, reason);
    }
}

package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TodoItem - 待办事项数据结构
 * 
 * 支持结构化的待办事项，包含 ID、内容、状态和优先级
 */
public record TodoItem(
    
    /** 唯一标识符 */
    @JsonProperty("id")
    String id,
    
    /** 待办事项内容 */
    @JsonProperty("content")
    String content,
    
    /** 状态: pending, in_progress, completed */
    @JsonProperty("status")
    String status,
    
    /** 优先级: high, medium, low */
    @JsonProperty("priority")
    String priority
) {
    public TodoItem {
        // 默认值
        if (status == null) {
            status = "pending";
        }
        if (priority == null) {
            priority = "medium";
        }
    }
    
    /**
     * 创建简单的待办事项
     */
    public static TodoItem of(String content) {
        return new TodoItem(null, content, "pending", "medium");
    }
    
    /**
     * 创建带状态的待办事项
     */
    public static TodoItem of(String content, String status) {
        return new TodoItem(null, content, status, "medium");
    }
    
    /**
     * 转换为 Markdown 格式
     */
    public String toMarkdown() {
        String checkbox = switch (status) {
            case "completed" -> "[x]";
            case "in_progress" -> "[~]";
            default -> "[ ]";
        };
        
        String priorityTag = switch (priority) {
            case "high" -> " 🔴";
            case "low" -> " 🟢";
            default -> "";
        };
        
        String idPrefix = id != null ? "**#" + id + "** " : "";
        return "- " + checkbox + " " + idPrefix + content + priorityTag;
    }
    
    /**
     * 从 Markdown 行解析
     */
    public static TodoItem fromMarkdown(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("- ")) {
            return null;
        }
        
        String content = trimmed.substring(2);
        String status = "pending";
        String id = null;
        String priority = "medium";
        
        // 解析 checkbox
        if (content.startsWith("[x] ")) {
            status = "completed";
            content = content.substring(4);
        } else if (content.startsWith("[~] ")) {
            status = "in_progress";
            content = content.substring(4);
        } else if (content.startsWith("[ ] ")) {
            content = content.substring(4);
        }
        
        // 解析 ID
        if (content.startsWith("**#")) {
            int endIdx = content.indexOf("** ");
            if (endIdx > 0) {
                id = content.substring(3, endIdx);
                content = content.substring(endIdx + 3);
            }
        }
        
        // 解析优先级
        if (content.endsWith(" 🔴")) {
            priority = "high";
            content = content.substring(0, content.length() - 2);
        } else if (content.endsWith(" 🟢")) {
            priority = "low";
            content = content.substring(0, content.length() - 2);
        }
        
        return new TodoItem(id, content.trim(), status, priority);
    }
}

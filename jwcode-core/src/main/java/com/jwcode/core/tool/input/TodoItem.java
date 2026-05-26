package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TodoItem - 待办事项数据结构
 * 
 * 支持结构化的待办事项，包含 ID、内容、activeForm（进行时态）、状态和优先级。
 * 
 * <p>双形式设计：</p>
 * <ul>
 *   <li><b>content</b> — 命令式，用于 pending/completed 状态的显示（如 "Run tests"）</li>
 *   <li><b>activeForm</b> — 现在进行式，用于 in_progress 状态的显示（如 "Running tests"）</li>
 * </ul>
 * 
 * <p>状态机：pending → in_progress → completed（任意时刻只有一个 in_progress）</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TodoItem(
    
    /** 唯一标识符 */
    @JsonProperty("id")
    String id,
    
    /** 待办事项内容（命令式，用于 pending/completed 显示） */
    @JsonProperty("content")
    String content,
    
    /** 进行时态（用于 in_progress 显示，如 "Running tests"） */
    @JsonProperty("activeForm")
    String activeForm,
    
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
        // activeForm 默认使用 content
        if (activeForm == null) {
            activeForm = content;
        }
    }
    
    /**
     * 创建简单的待办事项
     */
    public static TodoItem of(String content) {
        return new TodoItem(null, content, null, "pending", "medium");
    }
    
    /**
     * 创建带状态的待办事项
     */
    public static TodoItem of(String content, String status) {
        return new TodoItem(null, content, null, status, "medium");
    }
    
    /**
     * 创建带 activeForm 的待办事项
     */
    public static TodoItem of(String content, String activeForm, String status) {
        return new TodoItem(null, content, activeForm, status, "medium");
    }
    
    /**
     * 创建完整的待办事项
     */
    public static TodoItem of(String id, String content, String activeForm, String status, String priority) {
        return new TodoItem(id, content, activeForm, status, priority);
    }
    
    /**
     * 获取显示文本 — 根据状态自动选择 content 或 activeForm
     */
    public String getDisplayText() {
        if ("in_progress".equals(status) && activeForm != null && !activeForm.isEmpty()) {
            return activeForm;
        }
        return content != null ? content : "";
    }
    
    /**
     * 转换为 Markdown 格式
     * 格式: - [ ] content | activeForm
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
        String displayContent = getDisplayText();
        
        // 如果 activeForm 与 content 不同，附加 activeForm
        String activeFormSuffix = "";
        if (activeForm != null && !activeForm.equals(content) && !activeForm.isEmpty()) {
            activeFormSuffix = " |" + activeForm;
        }
        
        return "- " + checkbox + " " + idPrefix + displayContent + activeFormSuffix + priorityTag;
    }
    
    /**
     * 从 Markdown 行解析
     * 支持格式: - [ ] content | activeForm
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
        String activeForm = null;
        
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
        
        // 解析 activeForm（格式: content | activeForm）
        int pipeIdx = content.indexOf(" |");
        if (pipeIdx > 0) {
            activeForm = content.substring(pipeIdx + 2).trim();
            content = content.substring(0, pipeIdx).trim();
        }
        
        return new TodoItem(id, content.trim(), activeForm, status, priority);
    }
    
    /**
     * 创建副本并修改状态
     */
    public TodoItem withStatus(String newStatus) {
        return new TodoItem(id, content, activeForm, newStatus, priority);
    }
    
    /**
     * 创建副本并修改 activeForm
     */
    public TodoItem withActiveForm(String newActiveForm) {
        return new TodoItem(id, content, newActiveForm, status, priority);
    }
    
    /**
     * 创建副本并修改内容
     */
    public TodoItem withContent(String newContent) {
        return new TodoItem(id, newContent, activeForm, status, priority);
    }
}



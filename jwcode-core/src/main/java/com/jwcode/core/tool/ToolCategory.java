package com.jwcode.core.tool;

/**
 * 工具类别 — 按功能域组织工具，替代平铺列表。
 *
 * <p>AI 在决策时先选择类别，再选择具体工具，降低"选错工具"的概率。</p>
 */
public enum ToolCategory {
    FILE_OPERATION("File Operation", "文件读写、搜索、编辑"),
    EXECUTION("Execution", "Shell、PowerShell、REPL 执行"),
    SEARCH("Search", "代码搜索、网络搜索、信息获取"),
    COMMUNICATION("Communication", "消息发送、Web 请求"),
    METACOGNITION("Metacognition", "任务管理、计划、压缩、Agent"),
    CODE_ANALYSIS("Code Analysis", "LSP、语义分析、语法解析"),
    EXTERNAL_INTEGRATION("External Integration", "MCP、Git、工作区"),
    SYSTEM("System", "配置、睡眠、权限检查");

    private final String displayName;
    private final String description;

    ToolCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}

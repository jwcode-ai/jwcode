package com.jwcode.cli.log;

/**
 * AI 活动类型枚举
 * 定义所有可见的AI操作类型
 */
public enum ActivityType {
    
    // 文件操作
    FILE_READ("读取文件", "📄", CliLogger.BLUE),
    FILE_WRITE("写入文件", "📝", CliLogger.GREEN),
    FILE_EDIT("编辑文件", "✏️", CliLogger.YELLOW),
    FILE_DELETE("删除文件", "🗑️", CliLogger.RED),
    GLOB_SEARCH("搜索文件", "🔍", CliLogger.CYAN),
    
    // 代码操作
    CODE_SEARCH("搜索代码", "🔎", CliLogger.CYAN),
    CODE_REPLACE("替换代码", "🔄", CliLogger.YELLOW),
    CODE_REVIEW("审查代码", "👀", CliLogger.MAGENTA),
    CODE_REFACTOR("重构代码", "🏗️", CliLogger.BLUE),
    
    // 执行操作
    SHELL_EXEC("执行命令", "⚡", CliLogger.YELLOW),
    BASH_EXEC("执行 Bash", "⚡", CliLogger.YELLOW),
    PS_EXEC("执行 PowerShell", "⚡", CliLogger.YELLOW),
    PROCESS_RUN("运行进程", "▶️", CliLogger.GREEN),
    
    // 网络操作
    WEB_SEARCH("网络搜索", "🌐", CliLogger.BLUE),
    WEB_FETCH("获取网页", "📥", CliLogger.CYAN),
    API_CALL("API 调用", "🔌", CliLogger.MAGENTA),
    
    // AI 操作
    AI_THINKING("AI 思考", "🤔", CliLogger.GRAY),
    AI_PLANNING("规划任务", "📋", CliLogger.BLUE),
    AI_ANALYZING("分析中", "🔬", CliLogger.CYAN),
    AI_RESPONDING("生成回复", "💬", CliLogger.GREEN),
    
    // 任务管理
    TASK_CREATE("创建任务", "📌", CliLogger.GREEN),
    TASK_UPDATE("更新任务", "📝", CliLogger.YELLOW),
    TASK_COMPLETE("完成任务", "✅", CliLogger.GREEN),
    TASK_LIST("列出任务", "📋", CliLogger.BLUE),
    
    // 进度操作
    PROGRESS_START("开始", "▶️", CliLogger.GREEN),
    PROGRESS_UPDATE("进度更新", "⏳", CliLogger.YELLOW),
    PROGRESS_COMPLETE("完成", "✓", CliLogger.GREEN),
    PROGRESS_ERROR("错误", "✗", CliLogger.RED),
    
    // 系统操作
    SYSTEM_INIT("初始化", "⚙️", CliLogger.GRAY),
    SYSTEM_CONFIG("配置", "🔧", CliLogger.GRAY),
    SYSTEM_CHECK("系统检查", "✓", CliLogger.GREEN),
    
    // 版本控制
    GIT_COMMIT("Git 提交", "📦", CliLogger.GREEN),
    GIT_PUSH("Git 推送", "📤", CliLogger.BLUE),
    GIT_PULL("Git 拉取", "📥", CliLogger.BLUE),
    GIT_BRANCH("Git 分支", "🌿", CliLogger.GREEN),
    
    // 其他
    WAITING_USER("等待用户", "⏸️", CliLogger.YELLOW),
    PERMISSION_REQUEST("请求权限", "🔐", CliLogger.YELLOW),
    INFO_DISPLAY("信息", "ℹ️", CliLogger.BLUE),
    WARNING_DISPLAY("警告", "⚠️", CliLogger.YELLOW),
    ERROR_DISPLAY("错误", "❌", CliLogger.RED),
    SUCCESS_DISPLAY("成功", "✅", CliLogger.GREEN);
    
    private final String label;
    private final String icon;
    private final String color;
    
    ActivityType(String label, String icon, String color) {
        this.label = label;
        this.icon = icon;
        this.color = color;
    }
    
    public String getLabel() {
        return label;
    }
    
    public String getIcon() {
        return icon;
    }
    
    public String getColor() {
        return color;
    }
    
    /**
     * 获取带颜色的完整显示文本
     */
    public String getDisplayText(boolean useColor) {
        if (useColor) {
            return color + icon + " " + label + CliLogger.RESET;
        }
        return icon + " " + label;
    }
    
    /**
     * 根据工具名称获取对应的活动类型
     */
    public static ActivityType fromToolName(String toolName) {
        if (toolName == null) return INFO_DISPLAY;
        
        return switch (toolName) {
            case "FileReadTool" -> FILE_READ;
            case "FileWriteTool" -> FILE_WRITE;
            case "EditTool", "FileEditTool", "StrReplaceFileTool" -> FILE_EDIT;
            case "GlobTool" -> GLOB_SEARCH;
            case "GrepTool" -> CODE_SEARCH;
            case "BashTool", "ShellTool" -> BASH_EXEC;
            case "PowerShellTool" -> PS_EXEC;
            case "WebSearchTool" -> WEB_SEARCH;
            case "WebFetchTool" -> WEB_FETCH;
            case "TaskCreateTool" -> TASK_CREATE;
            case "TaskUpdateTool" -> TASK_UPDATE;
            case "TaskListTool" -> TASK_LIST;
            case "GitTool" -> GIT_COMMIT;
            case "AskUserQuestionTool" -> WAITING_USER;
            default -> INFO_DISPLAY;
        };
    }
}

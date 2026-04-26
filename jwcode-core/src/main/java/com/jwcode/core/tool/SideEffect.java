package com.jwcode.core.tool;

/**
 * 工具副作用类型 — 显式声明工具对系统产生的副作用。
 *
 * <p>AI 利用副作用信息做智能决策：</p>
 * <ul>
 *   <li>相同副作用类型的工具可能需要串行</li>
 *   <li>WRITE_FILE 操作前通常需要 READ_ONLY 确认</li>
 *   <li>EXECUTE_COMMAND 通常需要用户确认</li>
 * </ul>
 */
public enum SideEffect {
    /**
     * 只读 — 不修改任何系统状态
     */
    READ_ONLY,

    /**
     * 文件写操作 — 创建、修改、删除文件
     */
    WRITE_FILE,

    /**
     * 命令执行 — 执行 Shell/PowerShell/REPL 命令
     */
    EXECUTE_COMMAND,

    /**
     * 网络访问 — HTTP 请求、WebSocket、API 调用
     */
    NETWORK,

    /**
     * Git 操作 — commit, push, branch 等
     */
    GIT,

    /**
     * 会话修改 — 修改 Session、Memory、Config
     */
    SESSION_MUTATION,

    /**
     * 外部服务 — MCP、LSP、消息发送等
     */
    EXTERNAL_SERVICE
}

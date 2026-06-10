package com.jwcode.plugin.api;

/**
 * 插件能力类型 — 声明插件可以扩展的系统部分。
 */
public enum PluginCapability {
    /** 注册自定义工具 */
    TOOL,
    /** 注册生命周期钩子 */
    HOOK,
    /** 注册可重用技能 */
    SKILL,
    /** 提供 MCP 服务器 */
    MCP_SERVER,
    /** 注册自定义 Agent 类型 */
    AGENT,
    /** 注册斜杠命令 */
    COMMAND
}

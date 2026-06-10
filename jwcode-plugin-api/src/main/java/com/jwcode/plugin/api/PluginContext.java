package com.jwcode.plugin.api;

/**
 * 插件上下文 — 提供给插件的系统服务访问入口。
 *
 * <p>插件通过此上下文注册工具、钩子、技能等到 JWCode 运行时。
 */
public interface PluginContext {

    /** 注册一个工具 */
    void registerTool(Object tool);

    /** 反注册一个工具 */
    void unregisterTool(String toolName);

    /** 注册一个钩子 */
    void registerHook(String eventType, Object hook);

    /** 反注册一个钩子 */
    void unregisterHook(String eventType, String hookId);

    /** 注册一个技能 */
    void registerSkill(Object skill);

    /** 反注册一个技能 */
    void unregisterSkill(String skillId);

    /** 获取配置值 */
    String getConfig(String key);

    /** 获取配置值（带默认值） */
    String getConfig(String key, String defaultValue);

    /** 获取插件数据目录 */
    java.nio.file.Path getPluginDataDir();

    /** 记录日志 */
    void log(String level, String message);
}

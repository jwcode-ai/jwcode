package com.jwcode.plugin.api;

/**
 * 插件主接口 — 所有 JWCode 插件必须实现此接口。
 *
 * <p>生命周期：init → start → (运行) → stop → destroy
 */
public interface Plugin {

    /** 插件初始化 — 在加载后、启动前调用 */
    void init(PluginContext ctx);

    /** 启动插件 — 注册工具、钩子、技能等 */
    void start();

    /** 停止插件 — 暂停功能但保留注册 */
    void stop();

    /** 销毁插件 — 反注册所有资源，释放引用 */
    void destroy();

    /** 获取插件清单 */
    PluginManifest getManifest();
}

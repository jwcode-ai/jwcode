package com.jwcode.core.advanced.ai;

import com.jwcode.core.config.ConfigManager;
import com.jwcode.core.config.ConfigScope;

import java.util.logging.Logger;

/**
 * Auto AI Manager — 自动 AI 规划管理器。
 *
 * <p>当启用时，对非 CHAT 类型的任务自动进入 Plan+Act 两阶段模式，
 * 自动分解任务 → 执行 → 汇总，无需用户手动确认。</p>
 *
 * <p>配置键: {@code autoAI.enabled} (默认 false)</p>
 */
public class AutoAIManager {

    private static final Logger log = Logger.getLogger(AutoAIManager.class.getName());

    private static final String CONFIG_KEY = "autoAI.enabled";

    private volatile boolean enabled;

    public AutoAIManager() {
        reload();
    }

    /**
     * 从 ConfigManager 重新加载配置。
     */
    public void reload() {
        String raw = ConfigManager.getInstance().get(CONFIG_KEY);
        this.enabled = Boolean.parseBoolean(raw);
        log.fine("[AutoAI] reloaded: enabled=" + enabled + " (raw=" + raw + ")");
    }

    /**
     * 是否启用 Auto AI 模式。
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 动态设置（同时持久化到 USER 作用域）。
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        ConfigManager.getInstance().set(CONFIG_KEY, String.valueOf(enabled), ConfigScope.USER);
        log.info("[AutoAI] setEnabled=" + enabled);
    }
}


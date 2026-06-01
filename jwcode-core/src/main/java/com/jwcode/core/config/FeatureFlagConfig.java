package com.jwcode.core.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * FeatureFlagConfig — 特性开关配置加载器。
 *
 * <p>三级优先级（从高到低）：
 * <ol>
 *   <li>环境变量 {@code FEATURE_FLAG_<NAME>}（如 {@code FEATURE_FLAG_MICRO_COMPACT=true}）</li>
 *   <li>{@code .jwcode/features.json} 配置文件</li>
 *   <li>{@link FeatureFlag#getDefaultValue()} 代码默认值</li>
 * </ol>
 * </p>
 *
 * <p>配置文件格式（.jwcode/features.json）：
 * <pre>
 * {
 *   "memory.relevantInjection": true,
 *   "compact.microCompact": true,
 *   "compact.autoMicroCompact": false
 * }
 * </pre>
 * </p>
 *
 * <p>支持 60 秒间隔的自动重载，避免每次调用都读文件。
 * 对标 Claude Code 的 GrowthBook 平台（getFeatureValue_CACHED_MAY_BE_STALE）。</p>
 *
 * @author JWCode Team
 * @since 3.1.0
 */
public class FeatureFlagConfig {

    private static final Logger logger = Logger.getLogger(FeatureFlagConfig.class.getName());

    private static final long RELOAD_INTERVAL_MS = 60_000;
    private static final String ENV_PREFIX = "FEATURE_FLAG_";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path configFile;
    private final Map<String, Boolean> fileOverrides = new ConcurrentHashMap<>();
    private long lastLoadTime = 0;

    /**
     * @param workspaceRoot 工作目录根路径
     */
    public FeatureFlagConfig(Path workspaceRoot) {
        this.configFile = workspaceRoot.resolve(".jwcode").resolve("features.json");
        reload();
    }

    /**
     * 检查特性是否启用。
     * 优先级：env var > file > default
     */
    public boolean isEnabled(FeatureFlag flag) {
        // 1. 环境变量（最高优先级）
        String envName = ENV_PREFIX + flag.name();
        String envValue = System.getenv(envName);
        if (envValue != null) {
            return Boolean.parseBoolean(envValue.trim());
        }

        // 2. 配置文件
        reloadIfStale();
        Boolean fileValue = fileOverrides.get(flag.getConfigKey());
        if (fileValue != null) {
            return fileValue;
        }

        // 3. 默认值
        return flag.getDefaultValue();
    }

    /**
     * 运行时覆盖（不持久化，会话级）。
     */
    public void setEnabled(FeatureFlag flag, boolean enabled) {
        fileOverrides.put(flag.getConfigKey(), enabled);
        logger.info("[FeatureFlag] 运行时覆盖: " + flag.getConfigKey() + " = " + enabled);
    }

    /**
     * 从配置文件重新加载（遵循重载间隔）。
     */
    public synchronized void reload() {
        if (!Files.exists(configFile)) {
            logger.fine("[FeatureFlag] 配置文件不存在: " + configFile + "，使用默认值");
            return;
        }

        try {
            String content = Files.readString(configFile);
            Map<String, Boolean> loaded = MAPPER.readValue(content,
                new TypeReference<Map<String, Boolean>>() {});

            fileOverrides.clear();
            if (loaded != null) {
                fileOverrides.putAll(loaded);
            }
            lastLoadTime = System.currentTimeMillis();

            logger.fine("[FeatureFlag] 已加载 " + fileOverrides.size() + " 个配置项");
        } catch (IOException e) {
            logger.warning("[FeatureFlag] 加载配置失败: " + e.getMessage());
        }
    }

    /**
     * 如果距上次加载超过重载间隔，则重新加载。
     */
    private void reloadIfStale() {
        if (System.currentTimeMillis() - lastLoadTime > RELOAD_INTERVAL_MS) {
            reload();
        }
    }

    /**
     * 获取所有当前有效的标志值（用于 UI 展示）。
     */
    public Map<String, Boolean> getAllFlags() {
        reloadIfStale();
        Map<String, Boolean> all = new ConcurrentHashMap<>();
        for (FeatureFlag flag : FeatureFlag.values()) {
            all.put(flag.getConfigKey(), isEnabled(flag));
        }
        return all;
    }

    /**
     * 获取配置文件路径。
     */
    public Path getConfigFile() {
        return configFile;
    }

    /**
     * 强制立即重载。
     */
    public void forceReload() {
        lastLoadTime = 0;
        reload();
    }
}

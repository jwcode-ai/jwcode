package com.jwcode.core.config;

import java.util.*;
import java.util.logging.Logger;

/**
 * 配置迁移管理器 — 配置文件版本化 + 自动迁移。
 *
 * <p>解决配置格式变更导致的不兼容问题：
 * <ul>
 *   <li>配置文件包含 schema_version 字段</li>
 *   <li>启动时自动检测版本并执行迁移链</li>
 *   <li>迁移前后自动备份</li>
 *   <li>迁移失败时回滚到备份</li>
 * </ul>
 */
public class ConfigMigrationManager {
    private static final Logger logger = Logger.getLogger(ConfigMigrationManager.class.getName());

    /** 当前配置 schema 版本 */
    public static final int CURRENT_SCHEMA_VERSION = 2;

    /** 已注册的迁移步骤，key=源版本 */
    private final Map<Integer, MigrationStep> migrations = new TreeMap<>();

    public ConfigMigrationManager() {
        registerBuiltInMigrations();
    }

    // ==== 公共 API ====

    /**
     * 检查并执行迁移。
     *
     * @param configData 当前配置数据（可变的 Map）
     * @param configPath 配置文件路径（用于备份）
     * @return 迁移后的配置数据
     */
    public Map<String, Object> migrate(Map<String, Object> configData, String configPath) {
        int currentVersion = getSchemaVersion(configData);

        if (currentVersion >= CURRENT_SCHEMA_VERSION) {
            logger.fine("[ConfigMigration] schema 已是最新版本 " + currentVersion);
            return configData;
        }

        logger.info("[ConfigMigration] 检测到旧版配置 schema v" + currentVersion
            + "，将迁移至 v" + CURRENT_SCHEMA_VERSION);

        // 备份
        backupConfig(configData, configPath, currentVersion);

        // 执行迁移链
        Map<String, Object> migrated = new HashMap<>(configData);
        for (int v = currentVersion; v < CURRENT_SCHEMA_VERSION; v++) {
            MigrationStep step = migrations.get(v);
            if (step != null) {
                logger.info("[ConfigMigration] 执行迁移 v" + v + " -> v" + (v + 1) + ": " + step.description);
                try {
                    migrated = step.migrate().apply(migrated);
                    migrated.put("schema_version", v + 1);
                } catch (Exception e) {
                    logger.severe("[ConfigMigration] 迁移失败 v" + v + ": " + e.getMessage());
                    // 从备份恢复
                    restoreBackup(configData, configPath, currentVersion);
                    throw new ConfigMigrationException("配置迁移失败 v" + v, e);
                }
            }
        }

        logger.info("[ConfigMigration] 迁移完成，当前 schema v" + CURRENT_SCHEMA_VERSION);
        return migrated;
    }

    /** 获取配置的 schema 版本 */
    public static int getSchemaVersion(Map<String, Object> config) {
        Object version = config.get("schema_version");
        if (version instanceof Number n) return n.intValue();
        return 0; // 无版本信息 = v0
    }

    /** 注册自定义迁移步骤 */
    public void registerMigration(int fromVersion, MigrationStep step) {
        migrations.put(fromVersion, step);
    }

    // ==== 内置迁移 ====

    private void registerBuiltInMigrations() {
        // v0 -> v1: 添加 schema_version 字段
        registerMigration(0, new MigrationStep("v0→v1", "初始化 schema 版本并规范配置结构", config -> {
            Map<String, Object> result = new HashMap<>(config);
            result.put("schema_version", 1);

            // 规范化: providers 从扁平的 key-value 迁移到嵌套结构
            migrateProvidersV0toV1(result);
            return result;
        }));

        // v1 -> v2: 添加新字段默认值
        registerMigration(1, new MigrationStep("v1→v2", "添加 auto_mode、yolo_classifier、dream 等新配置项", config -> {
            Map<String, Object> result = new HashMap<>(config);
            result.put("schema_version", 2);

            // 添加引擎配置默认值
            ensureNestedMap(result, "engine");
            @SuppressWarnings("unchecked")
            Map<String, Object> engine = (Map<String, Object>) result.get("engine");
            engine.putIfAbsent("max_iterations", 50);
            engine.putIfAbsent("token_budget", 1_000_000);
            engine.putIfAbsent("timeout_minutes", 30);

            // 添加 auto_mode 配置
            ensureNestedMap(result, "auto_mode");
            @SuppressWarnings("unchecked")
            Map<String, Object> autoMode = (Map<String, Object>) result.get("auto_mode");
            autoMode.putIfAbsent("enabled", false);
            autoMode.putIfAbsent("confidence_threshold", 0.85);
            autoMode.putIfAbsent("max_frequency_per_minute", 30);

            // 添加 dream 服务配置
            ensureNestedMap(result, "dream");
            @SuppressWarnings("unchecked")
            Map<String, Object> dream = (Map<String, Object>) result.get("dream");
            dream.putIfAbsent("enabled", true);
            dream.putIfAbsent("idle_threshold_seconds", 30);
            dream.putIfAbsent("model", "haiku");

            // 添加压缩配置
            ensureNestedMap(result, "compression");
            @SuppressWarnings("unchecked")
            Map<String, Object> compression = (Map<String, Object>) result.get("compression");
            compression.putIfAbsent("auto_compact_threshold", 0.7);
            compression.putIfAbsent("strategy", "adaptive");

            return result;
        }));
    }

    // ==== v0→v1 辅助 ====

    private void migrateProvidersV0toV1(Map<String, Object> config) {
        // 如果提供者还是扁平的 key=value，转换为嵌套结构
        for (String key : new HashSet<>(config.keySet())) {
            if (key.startsWith("provider.")) {
                Object value = config.remove(key);
                String providerName = key.substring(9); // remove "provider."
                ensureNestedMap(config, "providers");
                @SuppressWarnings("unchecked")
                Map<String, Object> providers = (Map<String, Object>) config.get("providers");
                ensureNestedMap(providers, providerName);
                @SuppressWarnings("unchecked")
                Map<String, Object> provider = (Map<String, Object>) providers.get(providerName);

                // 解析 provider.base-url 格式
                int dot = providerName.lastIndexOf('.');
                if (dot > 0) {
                    String actualName = providerName.substring(0, dot);
                    String attr = providerName.substring(dot + 1);
                    ensureNestedMap(providers, actualName);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> actual = (Map<String, Object>) providers.get(actualName);
                    actual.put(attr, value);
                } else {
                    provider.put("base_url", value);
                }
            }
        }
    }

    // ==== 备份/恢复 ====

    @SuppressWarnings("unchecked")
    private void backupConfig(Map<String, Object> config, String path, int version) {
        try {
            java.nio.file.Path configPath = java.nio.file.Path.of(path);
            java.nio.file.Path backupPath = configPath.resolveSibling(
                configPath.getFileName() + ".v" + version + ".bak");
            java.nio.file.Files.writeString(backupPath,
                new com.fasterxml.jackson.databind.ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(config));
            logger.info("[ConfigMigration] 备份已保存: " + backupPath);
        } catch (Exception e) {
            logger.warning("[ConfigMigration] 备份失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreBackup(Map<String, Object> original, String path, int version) {
        try {
            java.nio.file.Path configPath = java.nio.file.Path.of(path);
            java.nio.file.Path backupPath = configPath.resolveSibling(
                configPath.getFileName() + ".v" + version + ".bak");
            if (java.nio.file.Files.exists(backupPath)) {
                java.nio.file.Files.copy(backupPath, configPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                logger.info("[ConfigMigration] 已从备份恢复: " + backupPath);
            }
        } catch (Exception e) {
            logger.severe("[ConfigMigration] 恢复失败: " + e.getMessage());
        }
    }

    // ==== 工具方法 ====

    @SuppressWarnings("unchecked")
    private void ensureNestedMap(Map<String, Object> parent, String key) {
        if (!(parent.get(key) instanceof Map)) {
            parent.put(key, new LinkedHashMap<String, Object>());
        }
    }

    // ==== 内部类型 ====

    public record MigrationStep(String name, String description,
                                 java.util.function.Function<Map<String, Object>, Map<String, Object>> migrate) {
    }

    public static class ConfigMigrationException extends RuntimeException {
        public ConfigMigrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

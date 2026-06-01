package com.jwcode.core.config;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

@DisplayName("FeatureFlagConfig — 特性开关测试")
class FeatureFlagConfigTest {

    private FeatureFlagConfig config;

    @BeforeEach
    void setUp() {
        config = new FeatureFlagConfig(Path.of(System.getProperty("user.home"), ".jwcode-test"));
    }

    @Test
    @DisplayName("默认值正确返回")
    void defaultValues() {
        assertTrue(config.isEnabled(FeatureFlag.RELEVANT_MEMORY_INJECTION));
        assertTrue(config.isEnabled(FeatureFlag.MICRO_COMPACT));
        assertFalse(config.isEnabled(FeatureFlag.AUTO_MICRO_COMPACT));
        assertTrue(config.isEnabled(FeatureFlag.PROMPT_SECTION_CACHE));
        assertFalse(config.isEnabled(FeatureFlag.CACHE_BREAK_DETECTION));
        assertFalse(config.isEnabled(FeatureFlag.GRADUATED_ESCALATION));
    }

    @Test
    @DisplayName("运行时覆盖生效")
    void runtimeOverride() {
        assertTrue(config.isEnabled(FeatureFlag.MICRO_COMPACT));
        config.setEnabled(FeatureFlag.MICRO_COMPACT, false);
        assertFalse(config.isEnabled(FeatureFlag.MICRO_COMPACT));
        // 恢复
        config.setEnabled(FeatureFlag.MICRO_COMPACT, true);
    }

    @Test
    @DisplayName("getAllFlags 返回所有标志")
    void getAllFlagsReturnsAll() {
        var flags = config.getAllFlags();
        assertEquals(FeatureFlag.values().length, flags.size());
        for (FeatureFlag f : FeatureFlag.values()) {
            assertTrue(flags.containsKey(f.getConfigKey()));
        }
    }

    @Test
    @DisplayName("configKey 格式正确")
    void configKeyFormat() {
        assertEquals("memory.relevantInjection", FeatureFlag.RELEVANT_MEMORY_INJECTION.getConfigKey());
        assertEquals("compact.microCompact", FeatureFlag.MICRO_COMPACT.getConfigKey());
        assertEquals("prompt.sectionCache", FeatureFlag.PROMPT_SECTION_CACHE.getConfigKey());
    }

    @Test
    @DisplayName("getConfigFile 返回正确路径")
    void configFilePath() {
        assertTrue(config.getConfigFile().toString().contains(".jwcode"));
        assertTrue(config.getConfigFile().toString().contains("features.json"));
    }

    @Test
    @DisplayName("forceReload 不抛异常")
    void forceReloadNoThrow() {
        assertDoesNotThrow(() -> config.forceReload());
    }

    @Test
    @DisplayName("JSON 配置文件格式的 key 与枚举一致")
    void configKeysMatchEnumNames() {
        // 验证枚举的 configKey 遵循 `category.featureName` 格式
        for (FeatureFlag flag : FeatureFlag.values()) {
            String key = flag.getConfigKey();
            assertTrue(key.contains("."), "configKey 应包含 . 分隔符: " + key);
            assertTrue(key.matches("[a-z]+\\.[a-zA-Z]+"), "configKey 格式: " + key);
        }
    }
}

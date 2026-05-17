package com.jwcode.core.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置管理集成测试
 *
 * <p>测试配置管理系统的核心功能：
 * ConfigManager 多级配置、ConfigScope 作用域管理、ConfigItem、ConfigChain 等。</p>
 */
@DisplayName("配置管理集成测试")
public class ConfigManagementIntegrationTest {

    private ConfigManager configManager;

    @BeforeEach
    void setUp() {
        // 使用独立实例避免影响其他测试
        configManager = ConfigManager.createNew();
    }

    @Test
    @DisplayName("设置和获取配置（默认作用域）")
    void testSetAndGetConfig() {
        configManager.set("test.key", "test-value");
        String value = configManager.get("test.key");
        assertEquals("test-value", value, "应能通过 get() 获取已设置的配置值");
    }

    @Test
    @DisplayName("获取不存在的配置返回 null")
    void testGetNonExistentConfigReturnsNull() {
        String value = configManager.get("non.existent.key");
        assertNull(value, "不存在的配置应返回 null");
    }

    @Test
    @DisplayName("删除配置")
    void testDeleteConfig() {
        configManager.set("test.key", "value");
        assertNotNull(configManager.get("test.key"));

        configManager.delete("test.key");
        assertNull(configManager.get("test.key"), "删除后配置应为 null");
    }

    @Test
    @DisplayName("获取全部配置")
    void testGetAllConfig() {
        configManager.set("key1", "value1");
        configManager.set("key2", "value2");

        Map<String, String> all = configManager.getAll();
        assertTrue(all.containsKey("key1"));
        assertTrue(all.containsKey("key2"));
    }

    @Test
    @DisplayName("指定作用域设置和获取")
    void testSetAndGetWithScope() {
        configManager.set("test.key", "scope-value", ConfigScope.USER);
        String value = configManager.get("test.key", ConfigScope.USER);
        assertEquals("scope-value", value);
    }

    @Test
    @DisplayName("不同作用域互不干扰")
    void testScopesAreIsolated() {
        configManager.set("test.key", "user-value", ConfigScope.USER);
        configManager.set("test.key", "project-value", ConfigScope.PROJECT);

        assertEquals("user-value", configManager.get("test.key", ConfigScope.USER));
        assertEquals("project-value", configManager.get("test.key", ConfigScope.PROJECT));
    }

    @Test
    @DisplayName("获取指定作用域的全部配置")
    void testGetAllWithScope() {
        configManager.set("k1", "v1", ConfigScope.USER);
        configManager.set("k2", "v2", ConfigScope.PROJECT);

        Map<String, String> userConfigs = configManager.getAll(ConfigScope.USER);
        assertTrue(userConfigs.containsKey("k1"));
    }

    @Test
    @DisplayName("删除指定作用域的配置")
    void testDeleteWithScope() {
        configManager.set("test.key", "value", ConfigScope.USER);
        configManager.delete("test.key", ConfigScope.USER);
        assertNull(configManager.get("test.key", ConfigScope.USER));
    }

    @Test
    @DisplayName("ConfigItem 类型安全")
    void testConfigItem() {
        configManager.set("int.key", "42");
        ConfigItem<?> item = configManager.getConfigItem("int.key");
        assertNotNull(item);
        assertEquals("int.key", item.getKey());
        assertEquals("42", item.getValue());
    }

    @Test
    @DisplayName("ConfigItem 指定作用域")
    void testConfigItemWithScope() {
        configManager.set("test.key", "value", ConfigScope.SYSTEM);
        ConfigItem<?> item = configManager.getConfigItem("test.key", ConfigScope.SYSTEM);
        assertNotNull(item);
    }

    @Test
    @DisplayName("获取全部 ConfigItem")
    void testGetAllItems() {
        configManager.set("k1", "v1");
        configManager.set("k2", "v2");

        Map<String, ConfigItem<?>> items = configManager.getAllItems();
        // ConfigManager 可能包含默认配置项，至少包含我们设置的 2 个
        assertTrue(items.containsKey("k1"), "应包含 k1");
        assertTrue(items.containsKey("k2"), "应包含 k2");
        assertTrue(items.size() >= 2, "至少包含 2 个配置项，当前: " + items.size());
    }

    @Test
    @DisplayName("ConfigChain 链式查找")
    void testConfigChain() {
        configManager.set("chain.key", "user-value", ConfigScope.USER);
        configManager.set("chain.key", "project-value", ConfigScope.PROJECT);

        ConfigChain chain = configManager.getConfigChain("chain.key");
        assertNotNull(chain);
    }

    @Test
    @DisplayName("清除运行时配置")
    void testClearRuntime() {
        configManager.set("k1", "v1", ConfigScope.RUNTIME);
        configManager.clearRuntime();

        assertNull(configManager.get("k1", ConfigScope.RUNTIME), "clearRuntime 后运行时配置应为空");
    }

    @Test
    @DisplayName("配置统计")
    void testConfigStats() {
        configManager.set("k1", "v1");
        configManager.set("k2", "v2");

        Map<String, Integer> stats = configManager.getStats();
        assertNotNull(stats);
    }

    @Test
    @DisplayName("ConfigScope 枚举值")
    void testConfigScopeValues() {
        assertNotNull(ConfigScope.SYSTEM);
        assertNotNull(ConfigScope.USER);
        assertNotNull(ConfigScope.PROJECT);
        assertNotNull(ConfigScope.RUNTIME);
    }

    @Test
    @DisplayName("配置重新加载")
    void testReload() {
        configManager.set("k1", "v1");
        assertDoesNotThrow(() -> configManager.reload());
    }

    @Test
    @DisplayName("配置导出")
    void testExportConfig(@TempDir Path tempDir) {
        configManager.set("k1", "v1");
        Path exportPath = tempDir.resolve("config.properties");
        assertDoesNotThrow(() -> configManager.exportConfig(ConfigScope.USER, ConfigExportFormat.PROPERTIES, exportPath));
        assertTrue(exportPath.toFile().exists());
    }

    @Test
    @DisplayName("获取配置路径")
    void testGetConfigPath() {
        Path path = configManager.getConfigPath(ConfigScope.USER);
        assertNotNull(path);
    }
}

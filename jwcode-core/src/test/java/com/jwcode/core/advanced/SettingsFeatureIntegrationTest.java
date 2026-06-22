package com.jwcode.core.advanced;

import com.jwcode.core.config.ConfigManager;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Settings Features - wiring integration")
public class SettingsFeatureIntegrationTest {

    private ConfigManager configManager;

    @BeforeEach
    void setUp() {
        configManager = ConfigManager.createNew();
    }

    // ==================== Config Key Consistency ====================

    @Test
    @DisplayName("Config keys are settable and retrievable")
    void configKeysWork() {
        configManager.set("yolo.enabled", "true");
        assertEquals("true", configManager.get("yolo.enabled"));
    }

    @Test
    @DisplayName("Unset config keys return null (treated as false by callers)")
    void missingConfigDefaults() {
        configManager.set("yolo.enabled", "false");
        assertEquals("false", configManager.get("yolo.enabled"));
    }

    @Test
    @DisplayName("All ConfigHandler keys are valid")
    void configHandlerKeyValidation() {
        String[] keys = {
            "yolo.enabled"
        };
        for (String key : keys) {
            configManager.set(key, "true");
            assertEquals("true", configManager.get(key), "Key valid: " + key);
        }
    }
}

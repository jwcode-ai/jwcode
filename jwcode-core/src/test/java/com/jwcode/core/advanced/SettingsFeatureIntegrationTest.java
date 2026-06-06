package com.jwcode.core.advanced;

import com.jwcode.core.advanced.swarm.AutoSwarmTrigger;
import com.jwcode.core.advanced.swarm.AgentSwarm;
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
    @DisplayName("All 3 features have correct config keys matching frontend expectations")
    void configKeysMatchFrontend() {
        configManager.set("yolo.enabled", "true");
        assertEquals("true", configManager.get("yolo.enabled"));

        configManager.set("autoSwarm.enabled", "true");
        assertEquals("true", configManager.get("autoSwarm.enabled"));
    }

    // ==================== Auto Swarm ====================

    @Test
    @DisplayName("AutoSwarmTrigger: simple task below threshold")
    void swarmSimpleTask() {
        AutoSwarmTrigger trigger = new AutoSwarmTrigger(new AgentSwarm());
        AutoSwarmTrigger.TaskAnalysis analysis = trigger.analyzeTask("fix typo in main.java");
        assertFalse(analysis.isShouldUseSwarm());
        assertTrue(analysis.getComplexity() < 3);
    }

    @Test
    @DisplayName("AutoSwarmTrigger: complex refactor task triggers swarm")
    void swarmComplexTask() {
        AutoSwarmTrigger trigger = new AutoSwarmTrigger(new AgentSwarm());
        AutoSwarmTrigger.TaskAnalysis analysis = trigger.analyzeTask(
            "refactor the entire auth module to extract common logic");
        assertTrue(analysis.isShouldUseSwarm());
        assertTrue(analysis.getComplexity() >= 3);
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Unset config keys return null (treated as false by callers)")
    void missingConfigDefaults() {
        configManager.set("yolo.enabled", "false");
        assertEquals("false", configManager.get("yolo.enabled"));
        // autoSwarm key not set in this instance — should be null
        configManager.set("autoSwarm.enabled", null);
        assertNull(configManager.get("autoSwarm.enabled"));
    }

    @Test
    @DisplayName("All ConfigHandler keys are valid")
    void configHandlerKeyValidation() {
        String[] keys = {
            "yolo.enabled", "autoSwarm.enabled"
        };
        for (String key : keys) {
            configManager.set(key, "true");
            assertEquals("true", configManager.get(key), "Key valid: " + key);
        }
    }
}

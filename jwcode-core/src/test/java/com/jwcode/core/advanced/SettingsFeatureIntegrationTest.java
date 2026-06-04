package com.jwcode.core.advanced;

import com.jwcode.core.advanced.ai.AutoAIManager;
import com.jwcode.core.advanced.compression.ContextCompressor;
import com.jwcode.core.advanced.swarm.AutoSwarmTrigger;
import com.jwcode.core.advanced.swarm.AgentSwarm;
import com.jwcode.core.advanced.thinking.ThinkingModeManager;
import com.jwcode.core.advanced.yolo.YoloModeManager;
import com.jwcode.core.config.ConfigManager;
import com.jwcode.core.config.ConfigScope;
import com.jwcode.core.model.Message;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

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
    @DisplayName("All 5 features have correct config keys matching frontend expectations")
    void configKeysMatchFrontend() {
        configManager.set("thinking.enabled", "true");
        assertEquals("true", configManager.get("thinking.enabled"));
        configManager.set("thinking.enabled", "false");

        configManager.set("yolo.enabled", "true");
        assertEquals("true", configManager.get("yolo.enabled"));

        configManager.set("autoSwarm.enabled", "true");
        assertEquals("true", configManager.get("autoSwarm.enabled"));

        configManager.set("autoAI.enabled", "true");
        assertEquals("true", configManager.get("autoAI.enabled"));

        configManager.set("compression.enabled", "true");
        assertEquals("true", configManager.get("compression.enabled"));
        configManager.set("compression.maxMessages", "50");
        assertEquals("50", configManager.get("compression.maxMessages"));
        configManager.set("compression.tokenThreshold", "4000");
        assertEquals("4000", configManager.get("compression.tokenThreshold"));
    }

    // ==================== Thinking Mode ====================

    @Test
    @DisplayName("ThinkingModeManager: toggle and config sync")
    void thinkingModeToggle() {
        ThinkingModeManager mgr = new ThinkingModeManager();
        assertFalse(mgr.isEnabled());

        mgr.setEnabled(true);
        assertTrue(mgr.isEnabled());

        mgr.setEnabled(false);
        assertFalse(mgr.isEnabled());

        assertTrue(mgr.toggle());
        assertTrue(mgr.isEnabled());
    }

    @Test
    @DisplayName("ThinkingModeManager: executeWithThinking produces correct result")
    void thinkingModeExecution() {
        ThinkingModeManager mgr = new ThinkingModeManager();
        mgr.setEnabled(true);

        String result = mgr.executeWithThinking(() -> "task done", "test task")
            .join().getResult();
        assertEquals("task done", result);
    }

    // ==================== YOLO Mode ====================

    @Test
    @DisplayName("YoloModeManager: disabled - blocks execution")
    void yoloDisabledBlock() {
        YoloModeManager mgr = new YoloModeManager();
        assertFalse(mgr.isEnabled());

        YoloModeManager.YoloAction action = new YoloModeManager.YoloAction(
            YoloModeManager.YoloActionType.FILE_MODIFY,
            "test write", "/test/file.txt", "content");
        assertFalse(mgr.canExecute(action));
    }

    @Test
    @DisplayName("YoloModeManager: enabled - allows safe actions")
    void yoloEnabledAllow() {
        YoloModeManager mgr = new YoloModeManager();
        mgr.setEnabled(true);
        assertTrue(mgr.isEnabled());

        YoloModeManager.YoloAction action = new YoloModeManager.YoloAction(
            YoloModeManager.YoloActionType.FILE_MODIFY,
            "safe write", "/workspace/test/file.txt", "content");
        assertTrue(mgr.canExecute(action));
    }

    @Test
    @DisplayName("YoloModeManager: blocks dangerous commands even when enabled")
    void yoloBlocksDangerous() {
        YoloModeManager mgr = new YoloModeManager();
        mgr.setEnabled(true);

        YoloModeManager.YoloAction dangerous = new YoloModeManager.YoloAction(
            YoloModeManager.YoloActionType.COMMAND,
            "rm -rf /", "/", "rm -rf /");
        assertFalse(mgr.canExecute(dangerous));
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

    // ==================== Auto AI ====================

    @Test
    @DisplayName("AutoAIManager: setEnabled works")
    void autoAIPersist() {
        AutoAIManager mgr = new AutoAIManager();
        mgr.setEnabled(true);
        assertTrue(mgr.isEnabled());
        mgr.setEnabled(false);
        assertFalse(mgr.isEnabled());
    }

    // ==================== Context Compression ====================

    @Test
    @DisplayName("ContextCompressor: needsCompression false for few messages")
    void compressionNotNeeded() {
        ContextCompressor compressor = new ContextCompressor();
        List<Message> few = new ArrayList<>();
        few.add(Message.createUserMessage("hello"));
        few.add(Message.createAssistantMessage("hi"));
        assertFalse(compressor.needsCompression(few));
    }

    @Test
    @DisplayName("ContextCompressor: compresses many messages")
    void compressionWithManyMessages() {
        ContextCompressor compressor = new ContextCompressor();
        List<Message> many = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            many.add(Message.createUserMessage("msg " + i));
        }
        assertTrue(compressor.needsCompression(many));
        ContextCompressor.CompressionResult result = compressor.compress(many);
        assertNotNull(result);
        assertEquals(60, result.getOriginalCount());
        assertTrue(result.getCompressedCount() < 60);
        assertTrue(result.getSavedTokens() > 0);
        assertNotNull(result.getSummary());
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Unset config keys return null (treated as false by callers)")
    void missingConfigDefaults() {
        assertNull(configManager.get("thinking.enabled"));
        assertNull(configManager.get("yolo.enabled"));
        assertNull(configManager.get("autoSwarm.enabled"));
        assertNull(configManager.get("autoAI.enabled"));
        assertNull(configManager.get("compression.enabled"));
    }

    @Test
    @DisplayName("All 7 ConfigHandler keys are valid")
    void configHandlerKeyValidation() {
        String[] keys = {
            "thinking.enabled", "yolo.enabled", "autoSwarm.enabled",
            "autoAI.enabled", "compression.enabled",
            "compression.maxMessages", "compression.tokenThreshold"
        };
        for (String key : keys) {
            configManager.set(key, "true");
            assertEquals("true", configManager.get(key), "Key valid: " + key);
        }
    }
}


package com.jwcode.core.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryCheckpointTriggerTest {
    @Test
    void triggersOnlyAtConfiguredThresholds() {
        MemoryCheckpointTrigger trigger = new MemoryCheckpointTrigger();

        assertFalse(trigger.shouldTrigger("s", 0.10));
        assertTrue(trigger.shouldTrigger("s", 0.20));
        assertFalse(trigger.shouldTrigger("s", 0.30));
        assertTrue(trigger.shouldTrigger("s", 0.45));
        assertFalse(trigger.shouldTrigger("s", 0.50));
        assertTrue(trigger.shouldTrigger("s", 0.70));
        assertFalse(trigger.shouldTrigger("s", 0.95));
    }
}

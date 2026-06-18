package com.jwcode.core.command;

import com.jwcode.core.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for the unified slash command system.
 *
 * <p>Verifies that the Java backend registry is the single source of truth,
 * exposing all expected commands with complete metadata.
 */
public class CommandSystemIntegrationTest {

    private static final List<String> EXPECTED = Arrays.asList(
        "help", "exit", "clear", "config", "status", "model", "eval", "doctor", "cost", "skills",
        "tokens", "export", "checkpoint", "memory", "project", "search", "test", "lint",
        "agents", "plugin", "rewind", "compact", "init", "effort", "branch", "mcp");

    @Test
    void registryContainsAllUnifiedCommands() {
        CommandRegistry registry = CommandRegistry.createFull(ToolRegistry.createDefault());
        CommandRegistry.setInstance(registry);
        Collection<Command> all = registry.getAllCommands();
        assertTrue(all.size() >= 25, "expected >= 25 commands, got " + all.size());

        Set<String> names = new HashSet<>();
        for (Command c : all) {
            names.add(c.getName());
        }
        for (String name : EXPECTED) {
            assertTrue(names.contains(name), "missing command: " + name);
        }
    }

    @Test
    void everyCommandHasCompleteMetadata() {
        CommandRegistry registry = CommandRegistry.createFull(ToolRegistry.createDefault());
        for (Command c : registry.getAllCommands()) {
            assertNotNull(c.getName(), "name is null");
            assertNotNull(c.getDescription(), "description is null for " + c.getName());
            assertNotNull(c.getUsage(), "usage is null for " + c.getName());
            assertNotNull(c.getCategory(), "category is null for " + c.getName());
            assertNotNull(c.getSource(), "source is null for " + c.getName());
            assertNotNull(c.getAliases(), "aliases is null for " + c.getName());
        }
    }
}

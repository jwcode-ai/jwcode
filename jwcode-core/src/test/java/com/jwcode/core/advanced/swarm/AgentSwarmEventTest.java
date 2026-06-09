package com.jwcode.core.advanced.swarm;

import com.jwcode.core.observability.ObservationEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test AgentSwarm event publishing to ObservationPipeline.
 * Verifies that SwarmTaskStarted / SwarmTaskCompleted events are
 * dispatched when executing complex tasks.
 */
public class AgentSwarmEventTest {

    @Test
    void testSwarmPublishesEvents() {
        // Collect events
        List<ObservationEvent> receivedEvents = new ArrayList<>();
        Consumer<ObservationEvent> collector = receivedEvents::add;

        AgentSwarm swarm = new AgentSwarm();

        // Execute a task that triggers rule-based decomposition (refactor)
        AgentSwarm.SwarmExecutionResult result = swarm.executeComplexTask(
            "refactor the codebase structure",
            null,
            collector
        );

        // Verify result
        assertNotNull(result);
        assertTrue(result.getSubTaskCount() >= 3,
            "Expected 3+ subtasks for refactor, got: " + result.getSubTaskCount());
        assertTrue(result.getAgentCount() >= 1,
            "Expected at least 1 agent");
        assertTrue(result.getDurationMs() > 0,
            "Duration should be positive");

        // Verify events were published
        assertFalse(receivedEvents.isEmpty(),
            "Expected at least one ObservationEvent to be published");

        // Check for SwarmTaskStarted events
        long startedCount = receivedEvents.stream()
            .filter(e -> e instanceof ObservationEvent.SwarmTaskStarted)
            .count();
        assertTrue(startedCount > 0,
            "Expected SwarmTaskStarted events, got: " + startedCount);

        // Check for SwarmTaskCompleted events
        long completedCount = receivedEvents.stream()
            .filter(e -> e instanceof ObservationEvent.SwarmTaskCompleted)
            .count();
        assertTrue(completedCount > 0,
            "Expected SwarmTaskCompleted events, got: " + completedCount);

        // Verify each started task has matching completed event
        assertEquals(startedCount, completedCount,
            "Task started/completed counts should match");

        System.out.println("Swarm test passed: " + startedCount + " tasks, "
            + result.getDurationMs() + "ms, "
            + String.format("%.1fx speedup", result.getSpeedup()));
    }

    @Test
    void testSwarmNoEventsWithoutConsumer() {
        AgentSwarm swarm = new AgentSwarm();

        // Without consumer - should not crash
        AgentSwarm.SwarmExecutionResult result = swarm.executeComplexTask(
            "implement a new feature",
            null
        );

        assertNotNull(result);
        assertTrue(result.getSubTaskCount() >= 4,
            "Feature tasks should have 4+ subtasks");
    }

    @Test
    void testSwarmTaskEventContent() {
        List<ObservationEvent> events = new ArrayList<>();
        AgentSwarm swarm = new AgentSwarm();

        swarm.executeComplexTask("fix all bugs in module", null, events::add);

        // Inspect first started event
        ObservationEvent.SwarmTaskStarted started = (ObservationEvent.SwarmTaskStarted) events.stream()
            .filter(e -> e instanceof ObservationEvent.SwarmTaskStarted)
            .findFirst()
            .orElse(null);

        assertNotNull(started, "Should have at least one SwarmTaskStarted");
        assertNotNull(started.agentId(), "Agent ID should not be null");
        assertNotNull(started.taskId(), "Task ID should not be null");
        assertNotNull(started.description(), "Description should not be null");
        assertNotNull(started.type(), "Type should not be null");
        assertTrue(started.priority() >= 0, "Priority should be non-negative");

        // Inspect first completed event
        ObservationEvent.SwarmTaskCompleted completed = (ObservationEvent.SwarmTaskCompleted) events.stream()
            .filter(e -> e instanceof ObservationEvent.SwarmTaskCompleted)
            .findFirst()
            .orElse(null);

        assertNotNull(completed, "Should have at least one SwarmTaskCompleted");
        assertTrue(completed.durationMs() >= 0, "Duration should be non-negative");
        assertTrue(completed.success(), "Task should succeed");
    }
}

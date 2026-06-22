package com.jwcode.core.agent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared runtime state for agent management.
 *
 * <p>The registry tracks two independent concepts:
 * <ul>
 *   <li>whether an agent is enabled for new background work</li>
 *   <li>how many live background instances are currently running</li>
 * </ul>
 */
public final class AgentRuntimeRegistry {

    private static final AgentRuntimeRegistry INSTANCE = new AgentRuntimeRegistry();

    private final ConcurrentMap<String, Boolean> enabledByAgent = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> instanceCounts = new ConcurrentHashMap<>();

    private AgentRuntimeRegistry() {
    }

    public static AgentRuntimeRegistry getInstance() {
        return INSTANCE;
    }

    public boolean isEnabled(String agentId) {
        String key = normalize(agentId);
        return enabledByAgent.getOrDefault(key, Boolean.TRUE);
    }

    public RuntimeSnapshot setEnabled(String agentId, boolean enabled) {
        String key = normalize(agentId);
        enabledByAgent.put(key, enabled);
        return getSnapshot(key);
    }

    public RuntimeSnapshot toggleEnabled(String agentId) {
        String key = normalize(agentId);
        boolean enabled = enabledByAgent.compute(key, (ignored, current) ->
            current == null ? Boolean.FALSE : !current);
        return getSnapshot(key, enabled);
    }

    public int beginInstance(String agentId) {
        String key = normalize(agentId);
        return instanceCounts.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
    }

    public int endInstance(String agentId) {
        String key = normalize(agentId);
        AtomicInteger counter = instanceCounts.computeIfAbsent(key, ignored -> new AtomicInteger());
        while (true) {
            int current = counter.get();
            if (current <= 0) {
                return 0;
            }
            if (counter.compareAndSet(current, current - 1)) {
                return current - 1;
            }
        }
    }

    public int getInstanceCount(String agentId) {
        AtomicInteger counter = instanceCounts.get(normalize(agentId));
        return counter != null ? counter.get() : 0;
    }

    public RuntimeSnapshot getSnapshot(String agentId) {
        String key = normalize(agentId);
        return getSnapshot(key, isEnabled(key));
    }

    private RuntimeSnapshot getSnapshot(String agentId, boolean enabled) {
        return new RuntimeSnapshot(agentId, enabled, getInstanceCount(agentId));
    }

    private static String normalize(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return "orchestrator";
        }
        return agentId.trim().toLowerCase();
    }

    public static final class RuntimeSnapshot {
        private final String agentId;
        private final boolean enabled;
        private final int instanceCount;

        private RuntimeSnapshot(String agentId, boolean enabled, int instanceCount) {
            this.agentId = agentId;
            this.enabled = enabled;
            this.instanceCount = instanceCount;
        }

        public String getAgentId() {
            return agentId;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getInstanceCount() {
            return instanceCount;
        }
    }
}

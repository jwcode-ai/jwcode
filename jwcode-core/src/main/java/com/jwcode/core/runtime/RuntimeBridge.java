package com.jwcode.core.runtime;

public class RuntimeBridge {
    private final AgentRuntime runtime;

    public RuntimeBridge(AgentRuntime runtime) {
        this.runtime = runtime;
    }

    public RuntimeResult chat(String sessionId, String message) {
        return runtime.handleChat(sessionId, message);
    }

    public RuntimeResult plan(String sessionId, String message) {
        return runtime.handlePlan(sessionId, message);
    }
}

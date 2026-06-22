package com.jwcode.core.hands;

public class RemoteAgentHand implements Hand<AgentRequest, AgentResult> {
    @Override
    public AgentResult execute(AgentRequest input, HandContext context) {
        return AgentResult.failure(input.role(), "RemoteAgentHand is not enabled for local workflow execution", 0);
    }
}

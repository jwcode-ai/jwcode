package com.jwcode.core.runtime;

import com.jwcode.core.workflow.WorkflowInput;
import com.jwcode.core.workflow.WorkflowRun;
import com.jwcode.core.workflow.WorkflowStatus;

public interface AgentRuntime {
    RuntimeResult handleChat(String sessionId, String message);
    RuntimeResult handlePlan(String sessionId, String message);
    WorkflowRun startWorkflow(String sessionId, WorkflowInput input);
    WorkflowRun resumeWorkflow(String runId);
    void cancelWorkflow(String runId);
    WorkflowStatus getWorkflowStatus(String runId);
}

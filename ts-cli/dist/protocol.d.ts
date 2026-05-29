/**
 * Protocol types — exact match with Python protocol.py and Java backend WSMessage format.
 */
export interface WSMessage {
    type: string;
    data?: string | null;
    sessionId?: string | null;
    message?: string | null;
}
export interface ToolCall {
    id: string;
    name: string;
    args?: string;
    result?: string;
    status: 'running' | 'complete' | 'error';
    complete: boolean;
    index?: number;
}
export interface Step {
    id: string;
    title: string;
    description?: string;
    thought?: string;
    action?: string;
    result?: string;
    status: 'running' | 'success' | 'error' | 'thinking' | 'action' | 'start';
    tools: ToolCall[];
}
export interface Message {
    id: string;
    type: 'user' | 'assistant' | 'system';
    content: string;
    thinking: string;
    steps: Step[];
    toolCalls: ToolCall[];
    timestamp: number;
}
export interface TokenUsage {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
    usageRatio: number;
}
export interface HookApproval {
    approvalId: string;
    toolName: string;
    askPayload: string;
    status: 'pending' | 'approved' | 'denied';
}
export declare const EVENT_TYPES: readonly ["connected", "session_created", "auth_required", "auth_success", "auth_failed", "start", "content", "thinking", "tool_call", "tool_result", "step_start", "step_thinking", "step_action", "step_complete", "progress", "complete", "error", "ping", "pong", "log", "commands_list", "notification", "plan_start", "plan_thinking", "plan_tasks", "plan_task_start", "plan_task_update", "plan_task_result", "plan_complete", "plan_error", "plan_mode_change", "workspace_changed", "generation_paused", "generation_resumed", "token_update", "hook_ask", "doctor_result"];
export type EventType = typeof EVENT_TYPES[number];
export declare function parseData(m: WSMessage): Record<string, unknown>;
export declare function createMessage(type: 'user' | 'assistant' | 'system', content?: string): Message;

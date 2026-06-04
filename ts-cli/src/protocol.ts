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
  timestamp?: number;
  duration?: number;
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
  timestamp?: number;
  duration?: number;
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

export interface PlanTask {
  id: string;
  title: string;
  description?: string;
  phase: number;
  phaseName: string;
  agentType?: string;
  status: 'pending' | 'running' | 'completed' | 'failed' | 'skipped' | 'blocked';
  dependencies?: string[];
  result?: string;
  error?: string;
  timestamp?: number;
  duration?: number;
}


export interface DegradationStatus {
  active: boolean;
  retryCount: number;
  maxRetries: number;
  mode: "normal" | "retrying" | "alternative" | "degraded" | "verifying" | "verified_fail";
  message: string;
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

// Event types from Java backend MessageType enum
export const EVENT_TYPES = [
  'connected', 'session_created',
  'auth_required', 'auth_success', 'auth_failed',
  'start', 'content', 'thinking',
  'tool_call', 'tool_result',
  'step_start', 'step_thinking', 'step_action', 'step_complete',
  'progress', 'complete', 'error',
  'ping', 'pong', 'log',
  'commands_list', 'notification',
  'plan_start', 'plan_thinking', 'plan_tasks',
  'plan_task_start', 'plan_task_update', 'plan_task_result',
  'plan_complete', 'plan_error', 'plan_mode_change',
  'workspace_changed',
  'generation_paused', 'generation_resumed',
  'token_update', 'context_compressed', 'compaction_progress',
  'hook_ask',
  'doctor_result',
  'degradation_update',
  "plan_mode_enter", "plan_mode_exit",
  "todo_update", "todo_item_done", "todo_progress",
  "hook_response_ack",
  "toggle_workspace_guard",
] as const;

export type EventType = typeof EVENT_TYPES[number];

export function parseData(m: WSMessage): Record<string, unknown> {
  if (typeof m.data === 'string') {
    try { return JSON.parse(m.data); } catch { return {}; }
  }
  return (m.data as unknown as Record<string, unknown>) || {};
}

export function createMessage(type: 'user' | 'assistant' | 'system', content = ''): Message {
  return {
    id: `msg-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    type,
    content,
    thinking: '',
    steps: [],
    toolCalls: [],
    timestamp: Date.now(),
  };
}

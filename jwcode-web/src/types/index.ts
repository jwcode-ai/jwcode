// Message types
export type MessageType = 'user' | 'assistant' | 'system';

export interface Message {
  id: string;
  type: MessageType;
  content: string;
  timestamp: number;
  thinking?: string;
  steps?: Step[];
  toolCalls?: ToolCall[];
}

// Step types
export type StepStatus = 'pending' | 'running' | 'success' | 'error' | 'warning';

export interface Step {
  id: string;
  title: string;
  description: string;
  status: StepStatus;
  thought?: string;
  action?: string;
  result?: string;
  toolCalls?: ToolCall[];
  timestamp: number;
  duration?: number;
}

// Tool call types
export interface ToolCall {
  id: string;
  index?: number;
  name: string;
  args: Record<string, unknown> | string;
  result?: unknown;
  status: 'running' | 'completed' | 'error';
  timestamp: number;
  duration?: number;
}

// Session types
export interface Session {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  messageCount: number;
}

// 会话 Tab 类型
export interface SessionTab {
  id: string;
  title: string;
  createdAt: number;
}

// 分屏布局模式（已简化为仅单屏）
export type SplitLayout = 'single';

// Model types
export interface Model {
  id: string;
  name: string;
  provider: string;
  status: 'online' | 'offline' | 'error';
  load: number;
  maxLoad: number;
  tokens: number;
  maxTokens: number;
  price: {
    input: number;
    output: number;
  };
  enabled?: boolean;
  temperature?: number;
  contextWindow?: number;
  isDefault?: boolean;
}

export interface ModelStatus {
  overallStatus: 'healthy' | 'degraded' | 'unhealthy' | 'unknown';
  healthRate: number;
  healthyInstances: number;
  totalInstances: number;
  loadBalanceStrategy: string;
  totalRequests: number;
}

// Tool types
export interface ToolParam {
  name: string;
  type: string;
  required: boolean;
  description: string;
}

export interface Tool {
  id: string;
  name: string;
  description: string;
  category: string;
  enabled: boolean;
  params?: ToolParam[];
}

// Skill types
export interface Skill {
  id: string;
  name: string;
  description: string;
  enabled: boolean;
  category: string;
  icon?: string;
  tags?: string[];
}

// Agent types
export interface Agent {
  id: string;
  name: string;
  description: string;
  color: string;
  active: boolean;
  state: 'idle' | 'busy' | 'error';
}

// Template types
export interface Template {
  id: string;
  name: string;
  description: string;
  content: string;
}

// Log types
export type LogLevel = 'info' | 'warn' | 'error' | 'success' | 'tool';

export interface LogEntry {
  id: string;
  level: LogLevel;
  source: string;
  message: string;
  timestamp: number;
}

// Settings types
export interface Settings {
  theme: 'dark' | 'light' | 'auto';
  language: string;
  fontSize: number;
  streamingEnabled: boolean;
}

export interface AdvancedSettings {
  thinking: { enabled: boolean };
  yolo: { enabled: boolean };
  autoSwarm: { enabled: boolean };
  autoAI: { enabled: boolean };
  compression: {
    enabled: boolean;
    maxMessages: number;
    tokenThreshold: number;
  };
}

// WebSocket message types
export type WSMessageType = 
  | 'connected'
  | 'start'
  | 'content'
  | 'thinking'
  | 'tool_call'
  | 'tool_result'
  | 'step_start'
  | 'step_thinking'
  | 'step_action'
  | 'step_complete'
  | 'complete'
  | 'error'
  | 'log'
  | 'subscribe_logs'
  | 'unsubscribe_logs'
  | 'chat'
  | 'plan'
  | 'create_session'
  | 'ping'
  | 'pong'
  | 'auth'
  | 'auth_required'
  | 'auth_success'
  | 'auth_failed'
  | 'ack'
  | 'get_commands'
  | 'commands_list'
  // Plan 模式消息
  | 'plan_start'
  | 'plan_thinking'
  | 'plan_tasks'
  | 'plan_task_start'
  | 'plan_task_update'
  | 'plan_task_result'
  | 'plan_complete'
  | 'plan_error'
  // TodoWrite 消息
  | 'todo_update'
  | 'todo_item_done'
  | 'todo_progress'
  // Plan Mode 管理消息
  | 'plan_mode_enter'
  | 'plan_mode_exit'
  // 工作目录切换消息
  | 'workspace'
  | 'workspace_changed';


export interface WSMessage {
  type: WSMessageType;
  data?: string;
  sessionId?: string;
  message?: string;
  token?: string;
}

// Task types
export type TaskStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface Task {
  id: string;
  title: string;
  description?: string;
  status: TaskStatus;
  priority: number;
  progress: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTaskInput {
  title: string;
  description?: string;
  priority?: number;
}

export interface UpdateTaskInput {
  title?: string;
  description?: string;
  status?: TaskStatus;
  priority?: number;
  progress?: number;
}

// Tab types
export type TabId = 'chat' | 'plan' | 'terminal' | 'files' | 'models' | 'tools' | 'skills' | 'agents' | 'settings' | 'logs' | 'tasks';

export interface Tab {
  id: TabId;
  title: string;
  icon?: string;
  closable?: boolean;
}

// File tree types
export interface FileNode {
  id: string;
  name: string;
  path: string;
  type: 'file' | 'directory';
  children?: FileNode[];
  expanded?: boolean;
}

// === Plan 模式相关类型 ===

export type PlanPhase = 'idle' | 'planning' | 'executing' | 'result' | 'error';

export interface PlanTask {
  id: string;
  title: string;
  description: string;
  status: 'pending' | 'running' | 'completed' | 'failed' | 'skipped';
  agentType: string;
  dependencies: string[];
  /** 子任务列表（树形结构支持） */
  children?: PlanTask[];
  result?: string;
  error?: string;
  startedAt?: number;
  completedAt?: number;
  progress?: number;
  logs?: string[];
}

export interface Plan {
  id: string;
  sessionId: string;
  phase: PlanPhase;
  goal: string;
  tasks: PlanTask[];
  createdAt: number;
  updatedAt: number;
}

export interface MessageQueueItem {
  id: string;
  content: string;
  timestamp: number;
}

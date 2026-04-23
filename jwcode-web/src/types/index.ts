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
  timestamp: number;
  duration?: number;
}

// Tool call types
export interface ToolCall {
  id: string;
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
  createdAt: number;
  updatedAt: number;
  messageCount: number;
}

// Model types
export type ModelHealthStatus = 'up' | 'down' | 'degraded' | 'starting' | 'unknown';

export interface Model {
  id: string;
  name: string;
  provider: string;
  providerDisplay: string;
  endpoint: string;
  healthStatus: ModelHealthStatus;
  score: number;
  successRate: number;
  avgLatencyMs: number;
  currentConnections: number;
  maxConcurrent: number;
  weight: number;
  totalRequests: number;
  uptimeSeconds: number;
  isActive: boolean;
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
export interface Tool {
  id: string;
  name: string;
  description: string;
  category: string;
  tags: string[];
  enabled: boolean;
}

// Skill types
export interface Skill {
  id: string;
  name: string;
  description: string;
  tags: string[];
  enabled: boolean;
}

// Agent types
export interface Agent {
  id: string;
  name: string;
  description: string;
  toolCount: number;
  isActive: boolean;
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
  | 'create_session';

export interface WSMessage {
  type: WSMessageType;
  data?: string;
  sessionId?: string;
  message?: string;
}

// Tab types
export type TabId = 'chat' | 'terminal' | 'files' | 'models' | 'tools' | 'skills' | 'agents' | 'settings' | 'logs';

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
  type: 'file' | 'folder';
  children?: FileNode[];
  expanded?: boolean;
}
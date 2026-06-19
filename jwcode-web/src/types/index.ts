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
  /** Hook 审批信息 �?当此消息为权限申请时存在 */
  hookApproval?: HookApprovalInfo;
  /** Tombstone: 标记此消息已被后端的模型切换或上下文压缩清理 */
  deleted?: boolean;
}

/**
 * HookApprovalInfo �?嵌入对话的权限申请信息�? *
 * 当后�?Hook 返回 ASK 决策时，前端在对话中插入一条包含此信息的消息，
 * 用户可以直接在对话中点击"允许"/"拒绝"或通过下拉菜单选择更细粒度的控制�? */
export interface HookApprovalInfo {
  /** 审批 ID */
  approvalId: string;
  /** 工具名称 */
  toolName: string;
  /** ASK 载荷（展示给用户的提示信息） */
  askPayload: string;
  /** 审批状态：pending=待审�? approved=已批�? denied=已拒�?*/
  status: 'pending' | 'approved' | 'denied';
  /** 时间�?*/
  timestamp: number;
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
  /** 是否为全局默认模型 */
  isGlobalDefault?: boolean;
  /** 是否为 Plan 模式默认模型 */
  isPlanDefault?: boolean;
  /** 是否为 Act 模式默认模型 */
  isActDefault?: boolean;
  apiType?: string;
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
  trigger?: string;
  injection?: string;
  systemPrompt?: string;
  requiredTools?: string[];
  source?: string;
  editable?: boolean;
  version?: string;
  author?: string;
  provenance?: string;
}

// Agent types
export interface Agent {
  id: string;
  name: string;
  description: string;
  color: string;
  active: boolean;
  enabled: boolean;
  instanceCount: number;
  state: 'idle' | 'busy' | 'error';
  /** Agent 模型绑定配置 */
  modelBinding?: {
    mode: 'mode-default' | 'specified';
    modelRef?: string;
  };
  /** Plan 模式生效模型 */
  effectivePlanModel?: EffectiveModel;
  /** Act 模式生效模型 */
  effectiveActModel?: EffectiveModel;
}

/** 生效模型信息 */
export interface EffectiveModel {
  modelRef: string | null;
  provider: string | null;
  modelId: string | null;
  usable: boolean;
  fallback: boolean;
  fallbackReason?: string;
  error?: string;
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
  /** Theme preset ID — selects a named color palette. Defaults to 'github'. */
  themePresetId?: string;
}

/** A named color palette with dark and light variants */
export interface ThemePreset {
  id: string;
  nameKey: string;
  descriptionKey: string;
  dark: CustomThemeColors;
  light: CustomThemeColors;
}

export interface AdvancedSettings {
  yolo: { enabled: boolean };
  autoSwarm: { enabled: boolean };
}

export interface CustomThemeColors {
  bg: string;
  surface: string;
  border: string;
  text: string;
  muted: string;
  accentBlue: string;
  accentGreen: string;
  accentRed: string;
  accentYellow: string;
  accentPurple: string;
}

export const DEFAULT_DARK_THEME: CustomThemeColors = {
  bg: '#0d1117',
  surface: '#161b22',
  border: '#30363d',
  text: '#c9d1d9',
  muted: '#8b949e',
  accentBlue: '#58a6ff',
  accentGreen: '#238636',
  accentRed: '#f85149',
  accentYellow: '#d29922',
  accentPurple: '#a855f7',
};

export const DEFAULT_LIGHT_THEME: CustomThemeColors = {
  bg: '#ffffff',
  surface: '#f6f8fa',
  border: '#d0d7de',
  text: '#24292f',
  muted: '#57606a',
  accentBlue: '#0969da',
  accentGreen: '#1a7f37',
  accentRed: '#cf222e',
  accentYellow: '#9a6700',
  accentPurple: '#8250df',
};

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
  | 'plan_complete'
  | 'plan_error'
  | 'plan_mode_change'
  | 'plan_tasks'
  | 'plan_task_start'
  | 'plan_task_update'
  | 'plan_task_result'
  // 步骤提示消息
  | 'step_prompt'
  // TodoWrite 消息
  | 'todo_update'
  | 'todo_item_done'
  | 'todo_progress'
  // Plan Mode 管理消息
  | 'plan_mode_enter'
  | 'plan_mode_exit'
  // 工作目录切换消息
  | 'workspace'
  | 'workspace_changed'
  // 生成控制消息
  | 'stop'
  | 'pause'
  | 'resume'
  | 'generation_paused'
  | 'generation_resumed'
  // Hook 审批消息
  | 'hook_ask'
  | 'hook_allow'
  | 'hook_deny'
  | 'hook_response_ack'
    | 'toggle_workspace_guard'
  | 'toggle_yolo'
  | 'toggle_auto_swarm'
  // Token 用量
  | 'token_update'
  // Infrastructure & diagnostics
  | 'degradation_update'
  | 'doctor_result'
  | 'context_compressed'
  | 'compaction_progress'
  | 'agent_flow_event'
  | 'message_ack'
  | 'exit'
  // Unified command_execute protocol
  | 'command_execute'
  | 'command_start'
  | 'command_output'
  | 'command_complete'
  | 'command_error'
  | 'tombstone';


export interface WSMessage {
  type: WSMessageType;
  data?: string;
  sessionId?: string;
  message?: string;
  token?: string;
  seq?: number; // 消息序号，用于 ACK 确认
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

// 会话内任务类型（每个会话维护独立的任务列表）
// 统一数据源：SessionTaskBoard 和 TaskListPanel 都从此读取
export interface SessionTask {
  id: string;
  title: string;
  completed: boolean;
  createdAt: number;
  /** 关联的后端任务 ID（通过 MCP TaskCreate 创建的任务） */
  backendId?: string;
  /** 后端任务状态 */
  backendStatus?: TaskStatus;
  /** 后端任务描述 */
  description?: string;

  // === Plan 任务扩展字段（AI 任务执行状态） ===
  /** Plan 任务状态：pending/running/completed/failed/skipped */
  planStatus?: 'pending' | 'running' | 'completed' | 'failed' | 'skipped';
  /** Agent 类型 */
  agentType?: string;
  /** 依赖的其他任务 ID */
  dependencies?: string[];
  /** 子任务列表 */
  children?: SessionTask[];
  /** 执行结果 */
  result?: string;
  /** 错误信息 */
  error?: string;
  /** 开始时间 */
  startedAt?: number;
  /** 完成时间 */
  completedAt?: number;
  /** 进度 0-100 */
  progress?: number;
  /** 执行日志 */
  logs?: string[];
  /** 步骤编号 */
  stepNumber?: number;
  /** 步骤动作 */
  action?: string;
  /** AI 提示词 */
  stepPrompt?: string;
  /** 任务上下文 */
  context?: Record<string, string>;
  /** 执行模式 */
  executionMode?: 'SEQUENTIAL' | 'CONCURRENT';
  /** 所属阶段 */
  phase?: string;
  /** 并发组 ID */
  parallelGroup?: string;
}

// Tab types
export type TabId = 'chat' | 'plan' | 'terminal' | 'files' | 'models' | 'tools' | 'skills' | 'agents' | 'hooks' | 'channels' | 'settings' | 'logs' | 'agentflow';

export type ChannelType = 'wechat' | 'feishu' | 'dingtalk';
export interface Channel {
  id: string; name: string; type: ChannelType;
  appId: string; appSecret: string; token: string; encodingAESKey: string;
  enabled: boolean; extra: Record<string, string>;
  connected?: boolean;
}
export interface ChannelFormData {
  id?: string; name: string; type: ChannelType;
  appId: string; appSecret: string; token: string; encodingAESKey: string; enabled: boolean;
}

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

// Terminal types
export interface TerminalStartResponse {
  port?: number;
  ttydPort: number;
  wsUrl: string;
  workspaceDir?: string;
  shell?: string;
}

export interface TerminalStatusResponse {
  running: boolean;
  terminalAvailable?: boolean;
  ttydAvailable?: boolean;
  port?: number;
  ttydPort?: number;
  uptime?: number;
  workspaceDir?: string;
  wsUrl?: string;
}

// Observability types
export interface ObservabilitySummary {
  llmCalls: number;
  toolCalls: number;
  errors: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  cacheCreationTokens: number;
  cacheReadTokens: number;
  cacheHitRate: number;
  elapsedSeconds: number;
  totalCostDollars: number;
}

export interface ModelCostSummary {
  modelId: string;
  totalCostDollars: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  requestCount: number;
}

export interface CostEntry {
  timestamp: string;
  modelId: string;
  inputTokens: number;
  outputTokens: number;
  costDollars: number;
}

export interface CostData {
  totalCostDollars: number;
  byModel: ModelCostSummary[];
  history: CostEntry[];
}

export interface TraceRunSummary {
  runId: string;
  scenarioId: string;
  description: string;
  status: string;
  startedAt: string;
  completedAt?: string;
  eventCount: number;
  summary?: string;
}

export type TraceRunDetail = TraceRunSummary;

export interface TraceEvent {
  runId: string;
  scenarioId: string;
  turnId: number;
  timestamp: string;
  eventType: string;
  event: Record<string, unknown>;
}

export interface PaginatedEvents {
  events: TraceEvent[];
  total: number;
  page: number;
  size: number;
}

// Hook configuration types
export type HookImplementationType = 'SHELL' | 'HTTP' | 'PROMPT' | 'AGENT';
export type HookPriority = 'SYSTEM' | 'SECURITY' | 'PROJECT' | 'USER' | 'PLUGIN';
export type HookScope = 'user' | 'project' | 'local';
export type HookDecision = 'ALLOW' | 'DENY' | 'ASK' | 'MODIFY' | 'VOID' | 'DEFER';

export interface HookRule {
  name: string;
  description: string;
  events: string[];
  implementationType: HookImplementationType;
  command?: string;
  url?: string;
  promptTemplate?: string;
  agentName?: string;
  priority: HookPriority;
  tools: string[];
  matchers: Record<string, string>;
  timeoutMs: number;
  enabled: boolean;
  failOpen: boolean;
  scope: HookScope;
}

export interface HookRuleFormData {
  name: string;
  description: string;
  events: string[];
  implementationType: HookImplementationType;
  command: string;
  url: string;
  promptTemplate: string;
  agentName: string;
  priority: HookPriority;
  tools: string[];
  matcherToolNamePattern: string;
  matcherFromState: string;
  matcherToState: string;
  timeoutMs: number;
  enabled: boolean;
  failOpen: boolean;
  scope: HookScope;
}

export interface HookDryRunRequest {
  hook: HookRule;
  eventType: string;
  toolName: string;
  toolInput: string;
}

export interface HookDryRunResult {
  decision: HookDecision;
  hookName: string;
  reason: string;
  durationMs: number;
  contextOutput: string;
}

export interface HookStats {
  totalHooks: number;
  enabledCount: number;
  disabledCount: number;
  configFile: string;
  lastLoadTime: string;
  hooksByEvent: Record<string, number>;
}

export interface HookEventCategory {
  category: string;
  events: HookEventMeta[];
}

export interface HookEventMeta {
  name: string;
  summary: string;
  hasMatcher: boolean;
  matcherField?: string;
}

export interface HookAgentInfo {
  id: string;
  name: string;
  description: string;
}

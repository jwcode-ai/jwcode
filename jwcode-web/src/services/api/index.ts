import { apiClient } from './client';
import type { ApiResponse } from './client';
import type {
  Model, Tool, Skill, Agent, FileNode, Session,
  Task, CreateTaskInput, UpdateTaskInput,
  ObservabilitySummary, CostData, TraceRunSummary, TraceRunDetail, PaginatedEvents,
  HookRule, HookRuleFormData, HookDryRunRequest, HookDryRunResult,
  HookExecutionLog, HookStats, HookEventCategory, HookAgentInfo,
  Channel, ChannelFormData
} from '../../types';

export { apiClient, type ApiResponse };
export type { Model, Tool, Skill, Agent, FileNode, Session, Task, TaskStatus, CreateTaskInput, UpdateTaskInput } from '../../types';

// Log file types
export interface LogFileInfo {
  name: string;
  size: number;
  modified: number;
  previewable: boolean;
}

export interface LogFileContent {
  name: string;
  size: number;
  modified: number;
  totalLines: number;
  startLine: number;
  content: string;
}

export const api = {
  // Model related
  models: {
    list: () => apiClient.get<{ models: Model[]; defaultProvider: unknown }>('/api/models'),
    get: (id: string) => apiClient.get<Model>(`/api/models/${id}`),
    create: (data: { provider: string; model: Record<string, unknown> }) => apiClient.post<{ model: Record<string, unknown>; provider: string; savedTo: string }>('/api/models', data),
    update: (provider: string, modelId: string, data: { model: Record<string, unknown> }) =>
        apiClient.post<{ provider: string; modelId: string; model: Record<string, unknown> }>('/api/models/update', { provider, modelId, ...data }),
    delete: (provider: string, modelId: string) =>
        apiClient.post<{ provider: string; modelId: string; removed: boolean }>('/api/models/delete', { provider, modelId }),
    test: (id: string) => apiClient.post<{ success: boolean; message: string }>(`/api/models/${id}/test`),
    toggle: (provider: string, modelId: string) => apiClient.post<{ provider: string; modelId: string; enabled: boolean }>('/api/models/toggle', { provider, modelId }),
  },

  // Tool related
  tools: {
    list: () => apiClient.get<Tool[]>('/api/tools'),
    get: (id: string) => apiClient.get<Tool>(`/api/tools/${id}`),
    update: (id: string, data: Partial<Tool>) => apiClient.put<Tool>(`/api/tools/${id}`, data),
    toggle: (id: string, enabled: boolean) => apiClient.post<void>(`/api/tools/${id}/toggle`, { enabled }),
  },

  // Skill related
  skills: {
    list: () => apiClient.get<Skill[]>('/api/skills'),
    get: (id: string) => apiClient.get<Skill>(`/api/skills/${id}`),
    update: (id: string, data: Partial<Skill>) => apiClient.put<Skill>(`/api/skills/${id}`, data),
    toggle: (id: string, enabled: boolean) => apiClient.post<void>(`/api/skills/${id}/toggle`, { enabled }),
  },

  // Agent related
  agents: {
    list: () => apiClient.get<Agent[]>('/api/agents'),
    get: (id: string) => apiClient.get<Agent>(`/api/agents/${id}`),
    create: (data: Omit<Agent, 'id'>) => apiClient.post<Agent>('/api/agents', data),
    update: (id: string, data: Partial<Agent>) => apiClient.put<Agent>(`/api/agents/${id}`, data),
    delete: (id: string) => apiClient.delete<void>(`/api/agents/${id}`),
    setActive: (id: string) => apiClient.post<void>(`/api/agents/${id}/activate`),
  },

  // File related
  files: {
    list: (path?: string) => apiClient.get<FileNode[]>(`/api/files${path ? `?path=${encodeURIComponent(path)}` : ''}`),
    read: (path: string) => apiClient.get<string>(`/api/files/read?path=${encodeURIComponent(path)}`),
    create: (path: string, content: string) => apiClient.post<void>('/api/files', { path, content }),
    update: (path: string, content: string) => apiClient.put<void>('/api/files/write', { path, content }),
    delete: (path: string) => apiClient.delete<void>(`/api/files?path=${encodeURIComponent(path)}`),
  },

  // Session related
  sessions: {
    list: () => apiClient.get<Session[]>('/api/sessions'),
    get: (id: string) => apiClient.get<Session>(`/api/sessions/${id}`),
    create: (title?: string) => apiClient.post<Session>('/api/sessions', { title }),
    delete: (id: string) => apiClient.delete<void>(`/api/sessions/${id}`),
  },

  // Task related
  tasks: {
    list: () => apiClient.get<Task[]>('/api/tasks'),
    get: (id: string) => apiClient.get<Task>(`/api/tasks/${id}`),
    create: (data: CreateTaskInput) => apiClient.post<Task>('/api/tasks', data),
    update: (id: string, data: UpdateTaskInput) => apiClient.put<Task>(`/api/tasks/${id}`, data),
    updateStatus: (id: string, status: Task['status']) => apiClient.patch<Task>(`/api/tasks/${id}/status`, { status }),
    delete: (id: string) => apiClient.delete<void>(`/api/tasks/${id}`),
    clearCompleted: () => apiClient.delete<{ deleted: number }>('/api/tasks'),
  },

  // Config related
  config: {
    provider: {
      get: () => apiClient.get<{
        configured: boolean;
        defaultProvider: string | null;
        providers: Record<string, { baseUrl?: string; hasApiKey: boolean; modelCount: number; apiType?: string }>;
      }>('/api/config/provider'),
      save: (data: Record<string, unknown>) => apiClient.post<{ message: string }>('/api/config/provider', data),
      delete: (providerName: string) => apiClient.post<{ message: string }>('/api/config/provider', { provider: providerName, apiKeys: [], models: [] }),
      setDefault: (providerName: string) => apiClient.post<{ message: string }>('/api/config/provider', { provider: providerName, setDefault: true }),
    },
    files: {
      list: () => apiClient.get<Array<{ name: string; size: number; modified: number; editable: boolean }>>('/api/config/files'),
      read: (file: string) => apiClient.get<{ name: string; content: string; editable: boolean }>(`/api/config/files/read?file=${encodeURIComponent(file)}`),
      write: (file: string, content: string) => apiClient.put<{ message: string }>('/api/config/files/write', { file, content }),
    },
  },

  // System status
  system: {
    status: () => apiClient.get<{
      status: string;
      uptime: number;
      memory: { used: number; total: number; max: number };
      models: { total: number; online: number; offline: number };
      timestamp: number;
    }>('/api/system/status'),
    restart: () => apiClient.post<{ message: string }>('/api/system/restart'),
    shutdown: () => apiClient.post<{ message: string }>('/api/system/shutdown'),
  },

  // Observability
  observability: {
    summary: () => apiClient.get<ObservabilitySummary>('/api/observability/summary'),
    costs: () => apiClient.get<CostData>('/api/observability/costs'),
    listRuns: () => apiClient.get<TraceRunSummary[]>('/api/observability/runs'),
    getRun: (runId: string) => apiClient.get<TraceRunDetail>(`/api/observability/runs/${encodeURIComponent(runId)}`),
    getRunEvents: (runId: string, page = 0, size = 50) =>
      apiClient.get<PaginatedEvents>(`/api/observability/runs/${encodeURIComponent(runId)}/events?page=${page}&size=${size}`),
  },

  // Log file management
  logs: {
    list: () => apiClient.get<LogFileInfo[]>('/api/logs'),
    read: (file: string, maxLines?: number) =>
      apiClient.get<LogFileContent>(`/api/logs/read?file=${encodeURIComponent(file)}${maxLines ? `&maxLines=${maxLines}` : ''}`),
    downloadUrl: (file: string) => `/api/logs/download?file=${encodeURIComponent(file)}`,
  },

  // Hook config management
  hooks: {
    list: () => apiClient.get<HookRule[]>('/api/hooks'),
    get: (name: string) => apiClient.get<HookRule>(`/api/hooks/${encodeURIComponent(name)}`),
    create: (data: HookRuleFormData) => apiClient.post<HookRule>('/api/hooks', data),
    update: (name: string, data: HookRuleFormData) => apiClient.put<HookRule>(`/api/hooks/${encodeURIComponent(name)}`, data),
    delete: (name: string) => apiClient.delete<void>(`/api/hooks/${encodeURIComponent(name)}`),
    batchDelete: (names: string[]) => apiClient.post<void>('/api/hooks/batch-delete', { names }),
    toggle: (name: string, enabled: boolean) => apiClient.patch<void>(`/api/hooks/${encodeURIComponent(name)}/toggle`, { enabled }),
    batchToggle: (names: string[], enabled: boolean) => apiClient.post<void>('/api/hooks/batch-toggle', { names, enabled }),
    dryRun: (data: HookDryRunRequest) => apiClient.post<HookDryRunResult>('/api/hooks/dry-run', data),
    stats: () => apiClient.get<HookStats>('/api/hooks/stats'),
    logs: () => apiClient.get<HookExecutionLog[]>('/api/hooks/logs'),
    events: () => apiClient.get<HookEventCategory[]>('/api/hooks/events'),
    agents: () => apiClient.get<HookAgentInfo[]>('/api/hooks/agents'),
    export: () => apiClient.get<{version: string; hooks: HookRule[]; lifecycleMappings: Record<string,string>}>('/api/hooks/export'),
    import: (data: {hooks: HookRule[]; lifecycleMappings?: Record<string,string>; mergeMode?: string}) =>
      apiClient.post<{status: string; reloaded: boolean; count: number}>('/api/hooks/import', data),
    lifecycleMappings: {
      get: () => apiClient.get<Record<string, string>>('/api/hooks/lifecycle-mappings'),
      save: (mappings: Record<string, string>) => apiClient.put<void>('/api/hooks/lifecycle-mappings', mappings),
    },
  },

  // Channel management (WeChat / Feishu / DingTalk ...)
  channels: {
    list: () => apiClient.get<Channel[]>('/api/channels'),
    create: (data: ChannelFormData) => apiClient.post<Channel>('/api/channels', data),
    update: (id: string, data: ChannelFormData) => apiClient.put<Channel>(`/api/channels/${encodeURIComponent(id)}`, data),
    delete: (id: string) => apiClient.delete<void>(`/api/channels/${encodeURIComponent(id)}`),
    toggle: (id: string, enabled: boolean) => apiClient.patch<void>(`/api/channels/${encodeURIComponent(id)}/toggle`, { enabled }),
    test: (id: string) => apiClient.post<{ connected: boolean }>(`/api/channels/${encodeURIComponent(id)}/test`),
    wechat: {
      qrcode: (id: string, token: string) => apiClient.get<unknown>(`/api/channels/${encodeURIComponent(id)}/wechat/qrcode?token=${encodeURIComponent(token)}`),
      qrcodeStatus: (id: string, token: string, qrcode: string) =>
        apiClient.get<unknown>(`/api/channels/${encodeURIComponent(id)}/wechat/qrcode/status?token=${encodeURIComponent(token)}&qrcode=${encodeURIComponent(qrcode)}`),
    },
  },
};

export default api;

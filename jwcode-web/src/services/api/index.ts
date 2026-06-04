import { apiClient } from './client';
import type { ApiResponse } from './client';
import type {
  Model, Tool, Skill, Agent, FileNode, Session,
  Task, CreateTaskInput, UpdateTaskInput
} from '../../types';

export { apiClient, type ApiResponse };
export type { Model, Tool, Skill, Agent, FileNode, Session, Task, TaskStatus, CreateTaskInput, UpdateTaskInput } from '../../types';

export const api = {
  // 模型相关
  models: {
    list: () => apiClient.get<{ models: Model[]; defaultProvider: unknown }>('/api/models'),
    get: (id: string) => apiClient.get<Model>(`/api/models/${id}`),
    create: (data: { provider: string; model: Record<string, unknown> }) => apiClient.post<{ model: Record<string, unknown>; provider: string; savedTo: string }>('/api/models', data),
    update: (id: string, data: Partial<Model>) => apiClient.put<Model>(`/api/models/${id}`, data),
    test: (id: string) => apiClient.post<{ success: boolean; message: string }>(`/api/models/${id}/test`),
  },

  // 工具相关
  tools: {
    list: () => apiClient.get<Tool[]>('/api/tools'),
    get: (id: string) => apiClient.get<Tool>(`/api/tools/${id}`),
    update: (id: string, data: Partial<Tool>) => apiClient.put<Tool>(`/api/tools/${id}`, data),
    toggle: (id: string, enabled: boolean) => apiClient.post<void>(`/api/tools/${id}/toggle`, { enabled }),
  },

  // 技能相关
  skills: {
    list: () => apiClient.get<Skill[]>('/api/skills'),
    get: (id: string) => apiClient.get<Skill>(`/api/skills/${id}`),
    update: (id: string, data: Partial<Skill>) => apiClient.put<Skill>(`/api/skills/${id}`, data),
    toggle: (id: string, enabled: boolean) => apiClient.post<void>(`/api/skills/${id}/toggle`, { enabled }),
  },

  // Agent 相关
  agents: {
    list: () => apiClient.get<Agent[]>('/api/agents'),
    get: (id: string) => apiClient.get<Agent>(`/api/agents/${id}`),
    create: (data: Omit<Agent, 'id'>) => apiClient.post<Agent>('/api/agents', data),
    update: (id: string, data: Partial<Agent>) => apiClient.put<Agent>(`/api/agents/${id}`, data),
    delete: (id: string) => apiClient.delete<void>(`/api/agents/${id}`),
    setActive: (id: string) => apiClient.post<void>(`/api/agents/${id}/activate`),
  },

  // 文件相关
  files: {
    list: (path?: string) => apiClient.get<FileNode[]>(`/api/files${path ? `?path=${encodeURIComponent(path)}` : ''}`),
    read: (path: string) => apiClient.get<string>(`/api/files/read?path=${encodeURIComponent(path)}`),
    create: (path: string, content: string) => apiClient.post<void>('/api/files', { path, content }),
    update: (path: string, content: string) => apiClient.put<void>('/api/files/write', { path, content }),
    delete: (path: string) => apiClient.delete<void>(`/api/files?path=${encodeURIComponent(path)}`),
  },

  // 会话相关
  sessions: {
    list: () => apiClient.get<Session[]>('/api/sessions'),
    get: (id: string) => apiClient.get<Session>(`/api/sessions/${id}`),
    create: (title?: string) => apiClient.post<Session>('/api/sessions', { title }),
    delete: (id: string) => apiClient.delete<void>(`/api/sessions/${id}`),
  },

  // 任务相关
  tasks: {
    list: () => apiClient.get<Task[]>('/api/tasks'),
    get: (id: string) => apiClient.get<Task>(`/api/tasks/${id}`),
    create: (data: CreateTaskInput) => apiClient.post<Task>('/api/tasks', data),
    update: (id: string, data: UpdateTaskInput) => apiClient.put<Task>(`/api/tasks/${id}`, data),
    updateStatus: (id: string, status: Task['status']) => apiClient.patch<Task>(`/api/tasks/${id}/status`, { status }),
    delete: (id: string) => apiClient.delete<void>(`/api/tasks/${id}`),
    clearCompleted: () => apiClient.delete<{ deleted: number }>('/api/tasks'),
  },

  // 配置相关
  config: {
    provider: {
      get: () => apiClient.get<{
        configured: boolean;
        defaultProvider: string | null;
        providers: Record<string, { baseUrl?: string; hasApiKey: boolean; modelCount: number; apiType?: string }>;
      }>('/api/config/provider'),
      save: (data: Record<string, unknown>) => apiClient.post<{ message: string }>('/api/config/provider', data),
      delete: (providerName: string) => apiClient.post<{ message: string }>('/api/config/provider', { provider: providerName, apiKeys: [], models: [] }),
    },
  },

  // 系统状态
  system: {
    status: () => apiClient.get<{
      status: string;
      uptime: number;
      memory: { used: number; total: number; max: number };
      models: { total: number; online: number; offline: number };
      timestamp: number;
    }>('/api/system/status'),
  },
};

export default api;

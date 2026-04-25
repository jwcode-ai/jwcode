/**
 * API Service - 前后端通信层
 * 
 * 负责与后端 REST API 交互
 */

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const WS_BASE_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8081';

// ============ Task 类型 ============

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

// ============ 类型定义 ============

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
}

export interface Tool {
  id: string;
  name: string;
  description: string;
  category: string;
  enabled: boolean;
  params: ToolParam[];
}

export interface ToolParam {
  name: string;
  type: string;
  required: boolean;
  description: string;
}

export interface Skill {
  id: string;
  name: string;
  description: string;
  enabled: boolean;
  category: string;
  icon?: string;
}

export interface Agent {
  id: string;
  name: string;
  description: string;
  color: string;
  active: boolean;
  state: 'idle' | 'busy' | 'error';
}

export interface FileNode {
  id: string;
  name: string;
  path: string;
  type: 'file' | 'directory';
  children?: FileNode[];
  expanded?: boolean;
}

export interface Session {
  id: string;
  title: string;
  createdAt: number;
  updatedAt: number;
  messageCount: number;
}

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
}

// ============ HTTP 客户端 ============

class ApiClient {
  private baseUrl: string;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<ApiResponse<T>> {
    try {
      const response = await fetch(`${this.baseUrl}${endpoint}`, {
        headers: {
          'Content-Type': 'application/json',
          ...options.headers,
        },
        ...options,
      });

      const data = await response.json();

      if (!response.ok) {
        return {
          success: false,
          error: data.error || `HTTP ${response.status}`,
        };
      }

      return {
        success: true,
        data,
      };
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Network error',
      };
    }
  }

  get<T>(endpoint: string): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, { method: 'GET' });
  }

  post<T>(endpoint: string, body?: unknown): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, {
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  put<T>(endpoint: string, body?: unknown): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, {
      method: 'PUT',
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  delete<T>(endpoint: string): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, { method: 'DELETE' });
  }

  patch<T>(endpoint: string, body?: unknown): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, {
      method: 'PATCH',
      body: body ? JSON.stringify(body) : undefined,
    });
  }
}

export const apiClient = new ApiClient(API_BASE_URL);

// ============ WebSocket 服务 ============

class WebSocketService {
  private ws: WebSocket | null = null;
  private url: string;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 3000;
  private messageHandlers: Set<(msg: unknown) => void> = new Set();
  private statusHandlers: Set<(status: 'connected' | 'disconnected' | 'error') => void> = new Set();

  constructor(url: string) {
    this.url = url;
  }

  connect(): void {
    if (this.ws?.readyState === WebSocket.OPEN) return;

    try {
      this.ws = new WebSocket(this.url);

      this.ws.onopen = () => {
        console.log('[WS] Connected');
        this.reconnectAttempts = 0;
        this.statusHandlers.forEach(h => h('connected'));
      };

      this.ws.onclose = () => {
        console.log('[WS] Disconnected');
        this.statusHandlers.forEach(h => h('disconnected'));
        this.scheduleReconnect();
      };

      this.ws.onerror = (error) => {
        console.error('[WS] Error:', error);
        this.statusHandlers.forEach(h => h('error'));
      };

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          this.messageHandlers.forEach(h => h(data));
        } catch (e) {
          console.error('[WS] Parse error:', e);
        }
      };
    } catch (error) {
      console.error('[WS] Connection failed:', error);
    }
  }

  private scheduleReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.log('[WS] Max reconnect attempts reached');
      return;
    }

    this.reconnectAttempts++;
    console.log(`[WS] Reconnecting (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);
    setTimeout(() => this.connect(), this.reconnectDelay);
  }

  disconnect(): void {
    this.ws?.close();
    this.ws = null;
  }

  send(data: unknown): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data));
    } else {
      console.warn('[WS] Not connected');
    }
  }

  onMessage(handler: (msg: unknown) => void): () => void {
    this.messageHandlers.add(handler);
    return () => this.messageHandlers.delete(handler);
  }

  onStatus(handler: (status: 'connected' | 'disconnected' | 'error') => void): () => void {
    this.statusHandlers.add(handler);
    return () => this.statusHandlers.delete(handler);
  }

  get isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }
}

export const wsService = new WebSocketService(WS_BASE_URL);

// ============ API 服务 ============

export const api = {
  // 模型相关
  models: {
    list: () => apiClient.get<Model[]>('/api/models'),
    get: (id: string) => apiClient.get<Model>(`/api/models/${id}`),
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
    update: (path: string, content: string) => apiClient.put<void>('/api/files', { path, content }),
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
    updateStatus: (id: string, status: TaskStatus) => apiClient.patch<Task>(`/api/tasks/${id}/status`, { status }),
    delete: (id: string) => apiClient.delete<void>(`/api/tasks/${id}`),
    clearCompleted: () => apiClient.delete<{ deleted: number }>('/api/tasks'),
  },

  // 系统状态
  system: {
    status: () => apiClient.get<{
      status: string;
      uptime: number;
      memory: { used: number; total: number };
      activeSessions: number;
    }>('/api/system/status'),
  },
};

export default api;

import { create } from 'zustand';

export interface SwarmTask {
  agentId: string;
  taskId: string;
  description: string;
  type: string;
  status: "running" | "success" | "error";
  durationMs?: number;
  priority?: number;
}

interface SwarmSession {
  sessionId: string;
  tasks: SwarmTask[];
  completedCount: number;
  totalCount: number;
}

interface SwarmState {
  swarmsBySession: Record<string, SwarmSession>;
  handleTaskStart: (sessionId: string, task: SwarmTask) => void;
  handleTaskComplete: (sessionId: string, taskId: string, success: boolean, durationMs: number) => void;
  handleProgress: (sessionId: string, completed: number, total: number) => void;
  clearSession: (sessionId: string) => void;
  hasActiveSwarm: (sessionId: string) => boolean;
  getSessionTasks: (sessionId: string) => SwarmTask[];
}

export const useSwarmStore = create<SwarmState>()((set, get) => ({
  swarmsBySession: {},

  handleTaskStart: (sessionId, task) => set((state) => {
    const existing = state.swarmsBySession[sessionId] || { sessionId, tasks: [], completedCount: 0, totalCount: 0 };
    if (existing.tasks.some(t => t.taskId === task.taskId)) return state;
    return {
      swarmsBySession: {
        ...state.swarmsBySession,
        [sessionId]: {
          ...existing,
          totalCount: existing.totalCount + 1,
          tasks: [...existing.tasks, { ...task, status: "running" as const }],
        },
      },
    };
  }),

  handleTaskComplete: (sessionId, taskId, success, durationMs) => set((state) => {
    const existing = state.swarmsBySession[sessionId];
    if (!existing) return state;
    const newTasks = existing.tasks.map(t =>
      t.taskId === taskId ? { ...t, status: success ? "success" as const : "error" as const, durationMs } : t
    );
    return {
      swarmsBySession: {
        ...state.swarmsBySession,
        [sessionId]: {
          ...existing,
          tasks: newTasks,
          completedCount: existing.completedCount + 1,
        },
      },
    };
  }),

  handleProgress: (sessionId, completed, total) => set((state) => {
    const existing = state.swarmsBySession[sessionId];
    if (!existing) return state;
    return {
      swarmsBySession: {
        ...state.swarmsBySession,
        [sessionId]: { ...existing, completedCount: completed, totalCount: total },
      },
    };
  }),

  clearSession: (sessionId) => set((state) => {
    const { [sessionId]: _, ...rest } = state.swarmsBySession;
    return { swarmsBySession: rest };
  }),

  hasActiveSwarm: (sessionId) => {
    const swarm = get().swarmsBySession[sessionId];
    if (!swarm) return false;
    return swarm.tasks.some(t => t.status === "running") || swarm.tasks.length === 0;
  },

  getSessionTasks: (sessionId) => {
    const swarm = get().swarmsBySession[sessionId];
    return swarm ? swarm.tasks : [];
  },
}));

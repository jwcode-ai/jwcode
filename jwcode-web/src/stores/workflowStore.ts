import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type WorkflowAgentStatus = 'scheduled' | 'running' | 'completed' | 'failed' | 'idle';
export type BlackboardTaskStatus = 'pending' | 'running' | 'completed' | 'failed' | 'skipped';
export type BlackboardSource = 'workflow' | 'agent' | 'plan' | 'todo' | 'task' | 'system';

export interface WorkflowProgress {
  runId: string;
  sessionId: string;
  status: string;
  completedPhases: number;
  totalPhases: number;
  completedEffects: number;
  totalEffects: number;
  tokensUsed: number;
  tokensRemaining: number;
  lastEventType?: string;
  error?: string;
  agentNodes?: Record<string, WorkflowAgentNode>;
  updatedAt: number;
}

export interface WorkflowAgentNode {
  nodeId: string;
  role: string;
  phase?: string;
  status: 'scheduled' | 'completed' | 'failed';
  tokens?: number;
  error?: string;
  updatedAt: number;
}

export interface TreeNode {
  agent: BlackboardAgent;
  children: TreeNode[];
  isRunning: boolean;
  tokensUsed: number;
}

export interface BlackboardAgent {
  id: string;
  sessionId: string;
  name: string;
  role: string;
  status: WorkflowAgentStatus;
  source: BlackboardSource;
  runId?: string;
  phase?: string;
  taskId?: string;
  currentTask?: string;
  tokens?: number;
  error?: string;
  parentId?: string;
  updatedAt: number;
}

export interface BlackboardTask {
  id: string;
  sessionId: string;
  title: string;
  status: BlackboardTaskStatus;
  source: BlackboardSource;
  description?: string;
  agentType?: string;
  progress?: number;
  result?: string;
  error?: string;
  runId?: string;
  dependencies?: string[];
  startedAt?: number;
  completedAt?: number;
  createdAt: number;
  updatedAt: number;
  raw?: unknown;
}

export interface BlackboardEvent {
  id: string;
  sessionId: string;
  type: string;
  source: BlackboardSource;
  timestamp: number;
  title?: string;
  message?: string;
  status?: string;
  runId?: string;
  agentId?: string;
  taskId?: string;
  data?: unknown;
}

interface WorkflowState {
  bySession: Record<string, WorkflowProgress>;
  byRun: Record<string, WorkflowProgress>;
  agentsBySession: Record<string, Record<string, BlackboardAgent>>;
  tasksBySession: Record<string, Record<string, BlackboardTask>>;
  eventsBySession: Record<string, BlackboardEvent[]>;
  upsert: (sessionId: string, progress: Partial<WorkflowProgress> & { runId: string; agentNode?: WorkflowAgentNode }) => void;
  fail: (sessionId: string, runId: string, error: string) => void;
  getForSession: (sessionId: string) => WorkflowProgress | undefined;
  upsertAgent: (sessionId: string, agent: Partial<BlackboardAgent> & { id: string }) => void;
  upsertTask: (sessionId: string, task: Partial<BlackboardTask> & { id: string }) => void;
  setTasks: (sessionId: string, tasks: Array<Partial<BlackboardTask> & { id: string }>, source?: BlackboardSource) => void;
  recordEvent: (sessionId: string, event: Omit<BlackboardEvent, 'id' | 'sessionId' | 'timestamp'> & Partial<Pick<BlackboardEvent, 'id' | 'timestamp'>>) => void;
  ingestAgentFlowEvent: (sessionId: string, event: any) => void;
  getAgentsForSession: (sessionId: string) => BlackboardAgent[];
  getAgentTree: (sessionId: string) => TreeNode[];
  getRunningAgentCount: (sessionId: string) => number;
  getTasksForSession: (sessionId: string) => BlackboardTask[];
  getEventsForSession: (sessionId: string) => BlackboardEvent[];
}

const MAX_EVENTS_PER_SESSION = 200;

const emptyProgress = (sessionId: string, runId: string): WorkflowProgress => ({
  runId,
  sessionId,
  status: 'RUNNING',
  completedPhases: 0,
  totalPhases: 0,
  completedEffects: 0,
  totalEffects: 0,
  tokensUsed: 0,
  tokensRemaining: 0,
  updatedAt: Date.now(),
});

const now = () => Date.now();

const normalizeStatus = (status?: string, completed?: boolean): BlackboardTaskStatus => {
  const value = String(status || '').toLowerCase();
  if (completed || value === 'done' || value === 'success' || value === 'completed' || value === 'complete') return 'completed';
  if (value === 'running' || value === 'in_progress' || value === 'started') return 'running';
  if (value === 'failed' || value === 'error') return 'failed';
  if (value === 'skipped' || value === 'cancelled' || value === 'canceled') return 'skipped';
  return 'pending';
};

const agentNameFromType = (type?: string) => {
  switch (String(type || '').toUpperCase()) {
    case 'ANALYSIS':
      return 'Explorer';
    case 'PLANNING':
      return 'Architect';
    case 'EXECUTION':
      return 'Coder';
    case 'VERIFICATION':
      return 'Tester';
    case 'REVIEW':
      return 'Reviewer';
    default:
      return type || 'Agent';
  }
};

const createEventId = (event: Partial<BlackboardEvent>) =>
  event.id || `${event.type || 'event'}-${event.timestamp || now()}-${Math.random().toString(36).slice(2, 8)}`;

export const useWorkflowStore = create<WorkflowState>()(
  persist(
    (set, get) => ({
      bySession: {},
      byRun: {},
      agentsBySession: {},
      tasksBySession: {},
      eventsBySession: {},

      upsert: (sessionId, progress) =>
        set((state) => {
          const runId = progress.runId;
          const previous = state.byRun[runId] || state.bySession[sessionId] || emptyProgress(sessionId, runId);
          const agentNodes = { ...(previous.agentNodes || {}) };
          if (progress.agentNode) {
            agentNodes[progress.agentNode.nodeId] = progress.agentNode;
          }
          const { agentNode, ...rest } = progress;
          const next: WorkflowProgress = {
            ...previous,
            ...rest,
            agentNodes,
            sessionId,
            runId,
            updatedAt: now(),
          };

          const agents = { ...(state.agentsBySession[sessionId] || {}) };
          if (agentNode) {
            agents[agentNode.nodeId] = {
              ...(agents[agentNode.nodeId] || {}),
              id: agentNode.nodeId,
              sessionId,
              name: agentNode.role,
              role: agentNode.role,
              source: 'workflow',
              runId,
              phase: agentNode.phase,
              status: agentNode.status,
              tokens: agentNode.tokens,
              error: agentNode.error,
              updatedAt: agentNode.updatedAt || now(),
            };
          }

          const eventType = progress.lastEventType || progress.status || 'workflow_update';
          const event: BlackboardEvent = {
            id: createEventId({ type: eventType }),
            sessionId,
            type: eventType,
            source: 'workflow',
            timestamp: now(),
            status: next.status,
            runId,
            agentId: agentNode?.nodeId,
            message: next.error,
            data: rest,
          };
          const events = [event, ...(state.eventsBySession[sessionId] || [])].slice(0, MAX_EVENTS_PER_SESSION);

          return {
            bySession: { ...state.bySession, [sessionId]: next },
            byRun: { ...state.byRun, [runId]: next },
            agentsBySession: { ...state.agentsBySession, [sessionId]: agents },
            eventsBySession: { ...state.eventsBySession, [sessionId]: events },
          };
        }),

      fail: (sessionId, runId, error) =>
        get().upsert(sessionId, { runId, status: 'ERROR', error, lastEventType: 'workflow_error' }),

      getForSession: (sessionId) => get().bySession[sessionId],

      upsertAgent: (sessionId, agent) =>
        set((state) => {
          const current = state.agentsBySession[sessionId]?.[agent.id];
          const next: BlackboardAgent = {
            id: agent.id,
            sessionId,
            name: agent.name || agent.role || current?.name || agent.id,
            role: agent.role || current?.role || agent.name || agent.id,
            source: agent.source || current?.source || 'agent',
            status: agent.status || current?.status || 'idle',
            runId: agent.runId ?? current?.runId,
            phase: agent.phase ?? current?.phase,
            taskId: agent.taskId ?? current?.taskId,
            currentTask: agent.currentTask ?? current?.currentTask,
            tokens: agent.tokens ?? current?.tokens,
            error: agent.error ?? current?.error,
            updatedAt: agent.updatedAt || now(),
          };
          return {
            agentsBySession: {
              ...state.agentsBySession,
              [sessionId]: { ...(state.agentsBySession[sessionId] || {}), [agent.id]: next },
            },
          };
        }),

      upsertTask: (sessionId, task) =>
        set((state) => {
          const current = state.tasksBySession[sessionId]?.[task.id];
          const timestamp = now();
          const status = task.status || current?.status || 'pending';
          const next: BlackboardTask = {
            id: task.id,
            sessionId,
            title: task.title || current?.title || task.description || task.id,
            status,
            source: task.source || current?.source || 'task',
            description: task.description ?? current?.description,
            agentType: task.agentType ?? current?.agentType,
            progress: task.progress ?? current?.progress,
            result: task.result ?? current?.result,
            error: task.error ?? current?.error,
            runId: task.runId ?? current?.runId,
            dependencies: task.dependencies ?? current?.dependencies,
            startedAt: task.startedAt ?? current?.startedAt ?? (status === 'running' ? timestamp : undefined),
            completedAt: task.completedAt ?? current?.completedAt ?? (status === 'completed' || status === 'failed' ? timestamp : undefined),
            createdAt: task.createdAt || current?.createdAt || timestamp,
            updatedAt: task.updatedAt || timestamp,
            raw: task.raw ?? current?.raw,
          };
          return {
            tasksBySession: {
              ...state.tasksBySession,
              [sessionId]: { ...(state.tasksBySession[sessionId] || {}), [task.id]: next },
            },
          };
        }),

      setTasks: (sessionId, tasks, source = 'plan') =>
        set((state) => {
          const previous = state.tasksBySession[sessionId] || {};
          const retained = Object.fromEntries(Object.entries(previous).filter(([, task]) => task.source !== source));
          const timestamp = now();
          const normalized = Object.fromEntries(tasks.map((task, index) => {
            const current = previous[task.id];
            const status = task.status || current?.status || 'pending';
            const next: BlackboardTask = {
              id: task.id,
              sessionId,
              title: task.title || current?.title || task.description || `Task ${index + 1}`,
              status,
              source: task.source || source,
              description: task.description ?? current?.description,
              agentType: task.agentType ?? current?.agentType,
              progress: task.progress ?? current?.progress,
              result: task.result ?? current?.result,
              error: task.error ?? current?.error,
              runId: task.runId ?? current?.runId,
              dependencies: task.dependencies ?? current?.dependencies,
              startedAt: task.startedAt ?? current?.startedAt,
              completedAt: task.completedAt ?? current?.completedAt,
              createdAt: task.createdAt || current?.createdAt || timestamp + index,
              updatedAt: task.updatedAt || timestamp,
              raw: task.raw ?? current?.raw,
            };
            return [next.id, next];
          }));
          return {
            tasksBySession: {
              ...state.tasksBySession,
              [sessionId]: { ...retained, ...normalized },
            },
          };
        }),

      recordEvent: (sessionId, event) =>
        set((state) => {
          const next: BlackboardEvent = {
            id: createEventId(event),
            sessionId,
            type: event.type,
            source: event.source || 'system',
            timestamp: event.timestamp || now(),
            title: event.title,
            message: event.message,
            status: event.status,
            runId: event.runId,
            agentId: event.agentId,
            taskId: event.taskId,
            data: event.data,
          };
          return {
            eventsBySession: {
              ...state.eventsBySession,
              [sessionId]: [next, ...(state.eventsBySession[sessionId] || [])].slice(0, MAX_EVENTS_PER_SESSION),
            },
          };
        }),

      ingestAgentFlowEvent: (sessionId, event) => {
        const payload = event?.data && typeof event.data === 'object' ? event.data : event;
        const eventType = event?.eventType || payload?.eventType || payload?.type || 'agent_flow_event';
        const taskId = String(payload?.taskId || event?.taskId || `${eventType}-${now()}`);
        const description = String(payload?.description || event?.description || '');
        const agentId = String(payload?.agentId || payload?.toAgent || payload?.fromAgent || agentNameFromType(payload?.type));
        const agentName = agentNameFromType(payload?.type || payload?.toAgent || payload?.agentId);
        const success = payload?.success !== false && payload?.status !== 'failed' && payload?.status !== 'error';
        const isComplete = eventType === 'task_complete' || eventType === 'complete';
        const isStart = eventType === 'task_start' || eventType === 'dispatch';
        const fromAgent = payload?.fromAgent || event?.fromAgent;

        if (isStart) {
          get().upsertAgent(sessionId, {
            id: agentId,
            parentId: fromAgent || undefined,
            name: agentName,
            role: agentName,
            status: 'running',
            source: 'agent',
            taskId,
            currentTask: description,
          });
          get().upsertTask(sessionId, {
            id: taskId,
            title: description || taskId,
            description,
            status: 'running',
            source: 'agent',
            agentType: agentName,
            raw: event,
          });
        } else if (isComplete) {
          get().upsertAgent(sessionId, {
            id: agentId,
            name: agentName,
            role: agentName,
            status: success ? 'completed' : 'failed',
            source: 'agent',
            taskId,
            error: success ? undefined : String(payload?.error || event?.error || 'Agent task failed'),
          });
          get().upsertTask(sessionId, {
            id: taskId,
            title: description || taskId,
            status: success ? 'completed' : 'failed',
            source: 'agent',
            agentType: agentName,
            result: payload?.result ? String(payload.result) : undefined,
            error: success ? undefined : String(payload?.error || event?.error || 'Agent task failed'),
            raw: event,
          });
        }

        get().recordEvent(sessionId, {
          type: eventType,
          source: 'agent',
          taskId,
          agentId,
          title: description || eventType,
          status: payload?.status || (isComplete ? (success ? 'completed' : 'failed') : isStart ? 'running' : undefined),
          data: event,
        });
      },

      getAgentsForSession: (sessionId) =>
        Object.values(get().agentsBySession[sessionId] || {}).sort((a, b) => b.updatedAt - a.updatedAt),

      getTasksForSession: (sessionId) =>
        Object.values(get().tasksBySession[sessionId] || {}).sort((a, b) => {
          const rank: Record<BlackboardTaskStatus, number> = { running: 0, pending: 1, failed: 2, completed: 3, skipped: 4 };
          return rank[a.status] - rank[b.status] || a.createdAt - b.createdAt;
        }),

      getEventsForSession: (sessionId) =>
        get().eventsBySession[sessionId] || [],

      getAgentTree: (sessionId) => {
        const agents = Object.values(get().agentsBySession[sessionId] || {});
        const agentMap = new Map(agents.map(a => [a.id, a]));
        const roots = agents.filter(a => !a.parentId || !agentMap.has(a.parentId));
        const childrenOf = (parentId: string) => agents.filter(a => a.parentId === parentId);
        const buildTree = (agent: BlackboardAgent): TreeNode => ({
          agent,
          children: childrenOf(agent.id).map(buildTree),
          isRunning: agent.status === 'running',
          tokensUsed: agent.tokens || 0,
        });
        return roots.map(buildTree);
      },

      getRunningAgentCount: (sessionId) => {
        const agents = Object.values(get().agentsBySession[sessionId] || {});
        return agents.filter(a => a.status === 'running').length;
      },
    }),
    {
      name: 'jwcode-workflow-store',
      partialize: (state) => ({
        bySession: state.bySession,
        byRun: state.byRun,
        agentsBySession: state.agentsBySession,
        tasksBySession: state.tasksBySession,
        eventsBySession: state.eventsBySession,
      }),
    }
  )
);

export { normalizeStatus as normalizeBlackboardTaskStatus };

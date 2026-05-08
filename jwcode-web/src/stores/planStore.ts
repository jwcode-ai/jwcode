import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Plan, PlanTask, PlanPhase, MessageQueueItem } from '../types';

type PlanMode = 'plan' | 'act';

interface PlanState {
  // 按 sessionId 隔离的 plans
  plansBySession: Record<string, Plan>;
  planPhasesBySession: Record<string, PlanPhase>;
  activePlanSessionId: string | null;

  // Plan/Act 模式（全局）
  mode: PlanMode;

  // 消息队列（全局，按 session 排队）
  messageQueue: MessageQueueItem[];

  // Actions
  startPlanning: (sessionId: string, goal: string) => void;
  setPlan: (sessionId: string, plan: Plan) => void;
  updateTask: (sessionId: string, taskId: string, updates: Partial<PlanTask>) => void;
  setPhase: (sessionId: string, phase: PlanPhase) => void;
  getPlan: (sessionId: string) => Plan | null;
  getPhase: (sessionId: string) => PlanPhase;

  setMode: (mode: PlanMode) => void;
  toggleMode: () => void;

  enqueueMessage: (content: string) => void;
  dequeueMessage: () => MessageQueueItem | null;

  clearPlan: (sessionId: string) => void;
  reset: () => void;
}

export const usePlanStore = create<PlanState>()(
  persist(
    (set, get) => ({
      plansBySession: {},
      planPhasesBySession: {},
      activePlanSessionId: null,
      mode: 'act',
      messageQueue: [],

      startPlanning: (sessionId, goal) =>
        set((state) => ({
          activePlanSessionId: sessionId,
          plansBySession: {
            ...state.plansBySession,
            [sessionId]: {
              id: `plan-${Date.now()}`,
              sessionId,
              phase: 'planning',
              goal,
              tasks: [],
              createdAt: Date.now(),
              updatedAt: Date.now(),
            },
          },
          planPhasesBySession: {
            ...state.planPhasesBySession,
            [sessionId]: 'planning',
          },
        })),

      setPlan: (sessionId, plan) =>
        set((state) => ({
          plansBySession: {
            ...state.plansBySession,
            [sessionId]: plan,
          },
          planPhasesBySession: {
            ...state.planPhasesBySession,
            [sessionId]: 'executing',
          },
        })),

      updateTask: (sessionId, taskId, updates) =>
        set((state) => {
          const plan = state.plansBySession[sessionId];
          if (!plan) return state;
          return {
            plansBySession: {
              ...state.plansBySession,
              [sessionId]: {
                ...plan,
                tasks: plan.tasks.map((t) =>
                  t.id === taskId ? { ...t, ...updates } : t
                ),
                updatedAt: Date.now(),
              },
            },
          };
        }),

      setPhase: (sessionId, phase) =>
        set((state) => ({
          planPhasesBySession: {
            ...state.planPhasesBySession,
            [sessionId]: phase,
          },
        })),

      getPlan: (sessionId) => {
        return get().plansBySession[sessionId] || null;
      },

      getPhase: (sessionId) => {
        return get().planPhasesBySession[sessionId] || 'idle';
      },

      setMode: (mode) => set({ mode }),

      toggleMode: () => set((state) => ({ mode: state.mode === 'plan' ? 'act' : 'plan' })),

      enqueueMessage: (content) =>
        set((state) => ({
          messageQueue: [
            ...state.messageQueue,
            {
              id: `queue-${Date.now()}`,
              content,
              timestamp: Date.now(),
            },
          ],
        })),

      dequeueMessage: () => {
        const state = get();
        if (state.messageQueue.length === 0) return null;
        const [first, ...rest] = state.messageQueue;
        set({ messageQueue: rest });
        return first || null;
      },

      clearPlan: (sessionId) =>
        set((state) => {
          const { [sessionId]: _, ...restPlans } = state.plansBySession;
          const { [sessionId]: __, ...restPhases } = state.planPhasesBySession;
          return {
            plansBySession: restPlans,
            planPhasesBySession: restPhases,
          };
        }),

      reset: () =>
        set({
          plansBySession: {},
          planPhasesBySession: {},
          activePlanSessionId: null,
          mode: 'act',
          messageQueue: [],
        }),
    }),
    {
      name: 'jwcode-plan-storage',
      partialize: (state) => ({
        plansBySession: state.plansBySession,
        planPhasesBySession: state.planPhasesBySession,
        mode: state.mode,
        messageQueue: state.messageQueue,
      }),
    }
  )
);

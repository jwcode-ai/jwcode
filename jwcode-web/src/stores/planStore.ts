import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Plan, PlanTask, StructuredTask, PlanPhase, MessageQueueItem } from '../types';

export type PlanMode = 'plan' | 'act' | 'normal';

interface ModeChangeEntry {
  previousMode: PlanMode;
  newMode: PlanMode;
  description: string;
  timestamp: number;
}

interface PlanState {
  // 按 sessionId 隔离的 plans
  plansBySession: Record<string, Plan>;
  planPhasesBySession: Record<string, PlanPhase>;
  activePlanSessionId: string | null;

  // 结构化任务（增强版，按 sessionId 隔离）
  structuredTasksBySession: Record<string, StructuredTask[]>;

  // Plan/Act 模式（全局）
  mode: PlanMode;

  // 当前 Plan 文件内容（从 ExitPlanMode 读取）
  currentPlanContent: string | null;

  // 模式切换历史
  modeHistory: ModeChangeEntry[];

  // 消息队列（全局，按 session 排队）
  messageQueue: MessageQueueItem[];

  // 当前步骤提示（全局）
  currentStepPrompt: {
    sessionId: string;
    taskId: string;
    stepIndex: number;
    stepNumber: number;
    description: string;
    action: string;
    stepPrompt: string;
    agentType: string;
  } | null;

  // 规划思考状态文本（plan_thinking 实时更新）
  thinkingStatusBySession: Record<string, string>;

  // Plan 确认状态（用户确认后才执行）
  showConfirmButton: boolean;         // 是否显示确认按钮
  planConfirmed: boolean;             // 是否已确认

  // Actions
  startPlanning: (sessionId: string, goal: string) => void;
  setPlan: (sessionId: string, plan: Plan) => void;
  updateTask: (sessionId: string, taskId: string, updates: Partial<PlanTask>) => void;
  updateTaskProgress: (sessionId: string, taskId: string, progress: number) => void;
  batchUpdateTasks: (sessionId: string, tasks: PlanTask[]) => void;
  setPhase: (sessionId: string, phase: PlanPhase) => void;
  getPlan: (sessionId: string) => Plan | null;
  getPhase: (sessionId: string) => PlanPhase;
  setThinkingStatus: (sessionId: string, status: string) => void;
  getThinkingStatus: (sessionId: string) => string;

  // Step prompt
  setCurrentStepPrompt: (prompt: PlanState['currentStepPrompt']) => void;
  clearCurrentStepPrompt: () => void;

  // Plan 确认相关
  setShowConfirmButton: (show: boolean) => void;
  confirmPlan: (sessionId: string) => void;
  clearPendingPlan: (sessionId: string) => void;

  // 结构化任务管理
  setStructuredTasks: (sessionId: string, tasks: StructuredTask[]) => void;
  updateStructuredTask: (sessionId: string, taskId: string, updates: Partial<StructuredTask>) => void;
  getStructuredTasks: (sessionId: string) => StructuredTask[];

  // Sprint Contract 状态跟踪
  activeContractId: string | null;
  contractIterationByTask: Record<string, number>;
  contractVerdictByTask: Record<string, string>;
  setContractIteration: (taskId: string, iteration: number) => void;
  setContractVerdict: (taskId: string, verdict: string) => void;

  // Plan Mode 管理
  setMode: (mode: PlanMode) => void;
  toggleMode: () => void;
  enterPlanMode: (sessionId: string, taskDescription: string) => void;
  exitPlanMode: (sessionId: string, summary: string) => void;
  setPlanContent: (content: string | null) => void;

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
      structuredTasksBySession: {},
      mode: 'act',
      currentPlanContent: null,
      modeHistory: [],
      messageQueue: [],

      // 当前步骤提示
      currentStepPrompt: null,

      // 规划思考状态
      thinkingStatusBySession: {},

      // Plan 确认状态
      showConfirmButton: false,
      planConfirmed: false,

      // Sprint Contract 状态
      activeContractId: null,
      contractIterationByTask: {},
      contractVerdictByTask: {},

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

      /**
       * 递归更新树形任务中的指定任务
       */
      updateTask: (sessionId, taskId, updates) =>
        set((state) => {
          const plan = state.plansBySession[sessionId];
          if (!plan) return state;

          const updateInTree = (tasks: PlanTask[]): PlanTask[] =>
            tasks.map((t) => {
              if (t.id === taskId) {
                return { ...t, ...updates };
              }
              if (t.children && t.children.length > 0) {
                return { ...t, children: updateInTree(t.children) };
              }
              return t;
            });

          return {
            plansBySession: {
              ...state.plansBySession,
              [sessionId]: {
                ...plan,
                tasks: updateInTree(plan.tasks),
                updatedAt: Date.now(),
              },
            },
          };
        }),

      /**
       * 更新任务进度（便捷方法）
       */
      updateTaskProgress: (sessionId, taskId, progress) =>
        get().updateTask(sessionId, taskId, { progress }),

      /**
       * 批量替换任务树（用于 plan_tasks 消息）
       */
      batchUpdateTasks: (sessionId, tasks) =>
        set((state) => {
          const plan = state.plansBySession[sessionId];
          if (!plan) return state;
          return {
            plansBySession: {
              ...state.plansBySession,
              [sessionId]: {
                ...plan,
                tasks,
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

      // 规划思考状态
      setThinkingStatus: (sessionId, status) =>
        set((state) => ({
          thinkingStatusBySession: {
            ...state.thinkingStatusBySession,
            [sessionId]: status,
          },
        })),

      getThinkingStatus: (sessionId) => {
        return get().thinkingStatusBySession[sessionId] || '';
      },

      // === Plan Mode 管理 ===

      setMode: (mode) => {
        const previousMode = get().mode;
        set((state) => ({
          mode,
          modeHistory: [
            ...state.modeHistory,
            {
              previousMode,
              newMode: mode,
              description: `模式切换: ${previousMode} → ${mode}`,
              timestamp: Date.now(),
            },
          ],
        }));
      },

      toggleMode: () => {
        const current = get().mode;
        const next = current === 'plan' ? 'act' : 'plan';
        get().setMode(next);
      },

      enterPlanMode: (sessionId, taskDescription) => {
        const previousMode = get().mode;
        set((state) => ({
          mode: 'plan',
          activePlanSessionId: sessionId,
          modeHistory: [
            ...state.modeHistory,
            {
              previousMode,
              newMode: 'plan',
              description: `进入 Plan Mode: ${taskDescription}`,
              timestamp: Date.now(),
            },
          ],
        }));
      },

      exitPlanMode: (_sessionId, summary) => {
        const previousMode = get().mode;
        set((state) => ({
          mode: 'act',
          modeHistory: [
            ...state.modeHistory,
            {
              previousMode,
              newMode: 'act',
              description: `退出 Plan Mode: ${summary}`,
              timestamp: Date.now(),
            },
          ],
        }));
      },

      setPlanContent: (content) => set({ currentPlanContent: content }),

      // === 消息队列 ===

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

      // === Plan 确认 ===

      setShowConfirmButton: (show) => set({ showConfirmButton: show }),

      confirmPlan: (sessionId) => {
        const state = get();
        // 通过 WebSocket 发送 plan_confirm 消息到后端
        try {
          // 使用动态导入的 wsService 避免循环依赖
          const wsService = (window as any).__wsService;
          if (wsService && wsService.send) {
            wsService.send({
              type: 'plan_confirm',
              sessionId,
              message: state.plansBySession[sessionId]?.goal || '',
            });
          } else {
            console.warn('[PlanStore] wsService not available');
          }
        } catch (e) {
          console.error('[PlanStore] Failed to send plan_confirm:', e);
        }
        set({ planConfirmed: true, showConfirmButton: false });
      },

      clearPendingPlan: (_sessionId) =>
        set({
          planConfirmed: false,
          showConfirmButton: false,
        }),

      // === 步骤提示 ===

      setCurrentStepPrompt: (prompt) => set({ currentStepPrompt: prompt }),

      clearCurrentStepPrompt: () => set({ currentStepPrompt: null }),

      // === 结构化任务管理 ===

      /**
       * 设置结构化任务列表（从后端 WebSocket plan_tasks 消息接收）
       */
      setStructuredTasks: (sessionId, tasks) =>
        set((state) => ({
          structuredTasksBySession: {
            ...state.structuredTasksBySession,
            [sessionId]: tasks,
          },
        })),

      /**
       * 递归更新树形结构化任务中的指定任务
       */
      updateStructuredTask: (sessionId, taskId, updates) =>
        set((state) => {
          const tasks = state.structuredTasksBySession[sessionId];
          if (!tasks) return state;

          const updateInTree = (taskList: StructuredTask[]): StructuredTask[] =>
            taskList.map((t) => {
              if (t.id === taskId) {
                return { ...t, ...updates };
              }
              if (t.children && t.children.length > 0) {
                return { ...t, children: updateInTree(t.children) };
              }
              return t;
            });

          return {
            structuredTasksBySession: {
              ...state.structuredTasksBySession,
              [sessionId]: updateInTree(tasks),
            },
          };
        }),

      /**
       * 获取结构化任务列表
       */
      getStructuredTasks: (sessionId) => {
        return get().structuredTasksBySession[sessionId] || [];
      },

      clearPlan: (sessionId) =>
        set((state) => {
          const { [sessionId]: _, ...restPlans } = state.plansBySession;
          const { [sessionId]: __, ...restPhases } = state.planPhasesBySession;
          const { [sessionId]: ___, ...restStructured } = state.structuredTasksBySession;
          return {
            plansBySession: restPlans,
            planPhasesBySession: restPhases,
            structuredTasksBySession: restStructured,
          };
        }),

      setContractIteration: (taskId, iteration) =>
        set((state) => ({
          contractIterationByTask: {
            ...state.contractIterationByTask,
            [taskId]: iteration,
          },
        })),

      setContractVerdict: (taskId, verdict) =>
        set((state) => ({
          contractVerdictByTask: {
            ...state.contractVerdictByTask,
            [taskId]: verdict,
          },
        })),

      reset: () =>
        set({
          plansBySession: {},
          planPhasesBySession: {},
          activePlanSessionId: null,
          structuredTasksBySession: {},
          mode: 'act',
          currentPlanContent: null,
          activeContractId: null,
          contractIterationByTask: {},
          contractVerdictByTask: {},
          modeHistory: [],
          messageQueue: [],
          currentStepPrompt: null,
          thinkingStatusBySession: {},
        }),
    }),
    {
      name: 'jwcode-plan-storage',
      partialize: (state) => ({
        plansBySession: state.plansBySession,
        planPhasesBySession: state.planPhasesBySession,
        mode: state.mode,
        currentPlanContent: state.currentPlanContent,
        modeHistory: state.modeHistory,
        messageQueue: state.messageQueue,
      }),
    }
  )
);

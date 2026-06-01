import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import wsService from '../services/websocket';

export type PlanMode = 'plan' | 'act' | 'normal';

interface ModeChangeEntry {
  previousMode: PlanMode;
  newMode: PlanMode;
  description: string;
  timestamp: number;
}

interface PlanState {
  mode: PlanMode;
  currentPlanContent: string | null;
  modeHistory: ModeChangeEntry[];
  thinkingStatusBySession: Record<string, string>;

  // Mode management
  setMode: (mode: PlanMode) => void;
  toggleMode: () => void;
  enterPlanMode: (sessionId: string, taskDescription: string) => void;
  exitPlanMode: (sessionId: string, summary: string) => void;
  setPlanContent: (content: string | null) => void;

  // Thinking status
  setThinkingStatus: (sessionId: string, status: string) => void;
  getThinkingStatus: (sessionId: string) => string;

  reset: () => void;
}

export const usePlanStore = create<PlanState>()(
  persist(
    (set, get) => ({
      mode: 'act',
      currentPlanContent: null,
      modeHistory: [],
      thinkingStatusBySession: {},

      setMode: (mode) => {
        const previousMode = get().mode;
        if (previousMode === mode) return;
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
        try {
          wsService.send({
            type: 'plan_mode_change',
            sessionId: '',
            data: JSON.stringify({ previousMode, newMode: mode, timestamp: Date.now() }),
          });
        } catch (e) {
          console.warn('[PlanStore] Failed to notify mode change via WS:', e);
        }
      },

      toggleMode: () => {
        const current = get().mode;
        const next = current === 'plan' ? 'act' : 'plan';
        get().setMode(next);
      },

      enterPlanMode: (_sessionId, taskDescription) => {
        const previousMode = get().mode;
        set((state) => ({
          mode: 'plan',
          modeHistory: [
            ...state.modeHistory,
            { previousMode, newMode: 'plan', description: `进入 Plan Mode: ${taskDescription}`, timestamp: Date.now() },
          ],
        }));
      },

      exitPlanMode: (_sessionId, summary) => {
        const previousMode = get().mode;
        set((state) => ({
          mode: 'act',
          modeHistory: [
            ...state.modeHistory,
            { previousMode, newMode: 'act', description: `退出 Plan Mode: ${summary}`, timestamp: Date.now() },
          ],
        }));
      },

      setPlanContent: (content) => set({ currentPlanContent: content }),

      setThinkingStatus: (sessionId, status) =>
        set((state) => ({
          thinkingStatusBySession: { ...state.thinkingStatusBySession, [sessionId]: status },
        })),

      getThinkingStatus: (sessionId) => {
        return get().thinkingStatusBySession[sessionId] || '';
      },

      reset: () =>
        set({
          mode: 'act',
          currentPlanContent: null,
          modeHistory: [],
          thinkingStatusBySession: {},
        }),
    }),
    {
      name: 'jwcode-plan-storage',
      partialize: (state) => ({
        mode: state.mode,
        currentPlanContent: state.currentPlanContent,
        modeHistory: state.modeHistory,
      }),
    }
  )
);

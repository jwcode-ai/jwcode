import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import wsService from '../services/websocket';
import { useSessionStore } from './sessionStore';

export type ExecutionMode = 'plan' | 'act' | 'goal' | 'normal';

interface ModeChangeEntry {
  previousMode: ExecutionMode;
  newMode: ExecutionMode;
  description: string;
  timestamp: number;
}

interface ExecutionModeState {
  mode: ExecutionMode;
  currentPlanContent: string | null;
  modeHistory: ModeChangeEntry[];
  thinkingStatusBySession: Record<string, string>;

  setMode: (mode: ExecutionMode) => void;
  applyBackendMode: (mode: ExecutionMode) => void;
  toggleMode: () => void;
  enterPlanMode: (sessionId: string, taskDescription: string) => void;
  exitPlanMode: (sessionId: string, summary: string) => void;
  enterGoalMode: (sessionId: string, goalDescription: string) => void;
  exitGoalMode: () => void;
  setPlanContent: (content: string | null) => void;

  setThinkingStatus: (sessionId: string, status: string) => void;
  getThinkingStatus: (sessionId: string) => string;

  reset: () => void;
}

const backendPlanMode = (mode: ExecutionMode): 'plan' | 'act' | 'goal' =>
  mode === 'plan' ? 'plan' : mode === 'goal' ? 'goal' : 'act';

export const useExecutionModeStore = create<ExecutionModeState>()(
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
              description: `Mode changed: ${previousMode} -> ${mode}`,
              timestamp: Date.now(),
            },
          ],
        }));

        const previousBackendMode = backendPlanMode(previousMode);
        const nextBackendMode = backendPlanMode(mode);
        if (previousBackendMode === nextBackendMode) return;

        try {
          const sessionId = useSessionStore.getState().activeSessionId || wsService.getSessionId() || undefined;
          wsService.send({
            type: 'plan_mode_change',
            sessionId,
            data: JSON.stringify({
              previousMode: previousBackendMode,
              newMode: nextBackendMode,
              frontendMode: mode,
              timestamp: Date.now(),
            }),
          });
        } catch (e) {
          console.warn('[ExecutionModeStore] Failed to notify mode change via WS:', e);
        }
      },

      applyBackendMode: (mode) => {
        const previousMode = get().mode;
        if (previousMode === mode) return;
        set((state) => ({
          mode,
          modeHistory: [
            ...state.modeHistory,
            {
              previousMode,
              newMode: mode,
              description: `Backend mode changed: ${previousMode} -> ${mode}`,
              timestamp: Date.now(),
            },
          ],
        }));
      },

      toggleMode: () => {
        const current = get().mode;
        // 循环: plan → goal → act → plan
        const next = current === 'plan' ? 'goal' : current === 'goal' ? 'act' : 'plan';
        get().setMode(next);
      },

      enterPlanMode: (_sessionId, taskDescription) => {
        const previousMode = get().mode;
        const newMode: ExecutionMode = previousMode === 'goal' ? 'goal' : 'plan';
        set((state) => ({
          mode: newMode,
          modeHistory: [
            ...state.modeHistory,
            {
              previousMode,
              newMode,
              description: `Enter ${newMode} mode: ${taskDescription}`,
              timestamp: Date.now(),
            },
          ],
        }));
      },

      exitPlanMode: (_sessionId, summary) => {
        const previousMode = get().mode;
        const newMode: ExecutionMode = previousMode === 'goal' ? 'goal' : 'act';
        set((state) => ({
          mode: newMode,
          modeHistory: [
            ...state.modeHistory,
            {
              previousMode,
              newMode,
              description: `Exit to ${newMode} mode: ${summary}`,
              timestamp: Date.now(),
            },
          ],
        }));
      },

      enterGoalMode: (_sessionId, goalDescription) => {
        const previousMode = get().mode;
        set((state) => ({
          mode: 'goal',
          modeHistory: [
            ...state.modeHistory,
            {
              previousMode,
              newMode: 'goal',
              description: `Enter Goal mode: ${goalDescription}`,
              timestamp: Date.now(),
            },
          ],
        }));

        try {
          const sessionId = useSessionStore.getState().activeSessionId || wsService.getSessionId() || undefined;
          wsService.send({
            type: 'plan_mode_change',
            sessionId,
            data: JSON.stringify({
              previousMode: backendPlanMode(previousMode),
              newMode: 'goal',
              frontendMode: 'goal',
              goalDescription,
              timestamp: Date.now(),
            }),
          });
        } catch (e) {
          console.warn('[ExecutionModeStore] Failed to notify goal mode change via WS:', e);
        }
      },

      exitGoalMode: () => {
        const previousMode = get().mode;
        set((state) => ({
          mode: 'act',
          modeHistory: [
            ...state.modeHistory,
            {
              previousMode,
              newMode: 'act',
              description: 'Exit Goal mode',
              timestamp: Date.now(),
            },
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

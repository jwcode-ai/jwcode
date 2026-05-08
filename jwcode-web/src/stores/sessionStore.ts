import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Session, SessionTab, SplitLayout } from '../types';

const MAX_SESSION_TABS = 6;

interface SessionState {
  sessions: Session[];
  activeSessionId: string | null;
  sessionTabs: SessionTab[];
  layout: SplitLayout;

  // Actions
  addSession: (session: Session) => void;
  removeSession: (sessionId: string) => void;
  updateSession: (sessionId: string, updates: Partial<Session>) => void;
  setActiveSession: (sessionId: string) => void;

  // 多会话 Tab 管理
  addSessionTab: (title?: string) => string;
  closeSessionTab: (sessionId: string) => void;
  renameSessionTab: (sessionId: string, title: string) => void;
  getActiveSessionTab: () => SessionTab | null;
  getVisibleSessionTabs: () => SessionTab[];

}

export const useSessionStore = create<SessionState>()(
  persist(
    (set, get) => ({
      sessions: [],
      activeSessionId: null,
      sessionTabs: [],
      layout: 'single',

      addSession: (session) =>
        set((state) => ({
          sessions: [session, ...state.sessions],
          activeSessionId: session.id,
        })),

      removeSession: (sessionId) =>
        set((state) => ({
          sessions: state.sessions.filter((s) => s.id !== sessionId),
          activeSessionId: state.activeSessionId === sessionId
            ? state.sessions[0]?.id || null
            : state.activeSessionId,
        })),

      updateSession: (sessionId, updates) =>
        set((state) => ({
          sessions: state.sessions.map((s) =>
            s.id === sessionId ? { ...s, ...updates } : s
          ),
        })),

      setActiveSession: (sessionId) =>
        set({ activeSessionId: sessionId }),

      // 多会话 Tab 管理
      addSessionTab: (title) => {
        const state = get();
        if (state.sessionTabs.length >= MAX_SESSION_TABS) {
          // 超过上限，自动关闭最旧的
          const oldest = state.sessionTabs.reduce((a, b) =>
            a.createdAt < b.createdAt ? a : b
          );
          get().closeSessionTab(oldest.id);
        }

        const sessionId = `session-${Date.now()}`;
        const newTab: SessionTab = {
          id: sessionId,
          title: title || `对话 ${state.sessionTabs.length + 1}`,
          createdAt: Date.now(),
        };

        // 同时创建 Session 记录
        const newSession: Session = {
          id: sessionId,
          title: title || `对话 ${state.sessionTabs.length + 1}`,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          messageCount: 0,
        };

        set({
          sessionTabs: [...state.sessionTabs, newTab],
          sessions: [...state.sessions, newSession],
          activeSessionId: sessionId,
        });

        return sessionId;
      },

      closeSessionTab: (sessionId) => {
        const state = get();
        const remaining = state.sessionTabs.filter((t) => t.id !== sessionId);
        if (remaining.length === 0) return; // 至少保留一个

        const lastTab = remaining[remaining.length - 1];
        set({
          sessionTabs: remaining,
          activeSessionId: state.activeSessionId === sessionId
            ? (lastTab ? lastTab.id : remaining[0]?.id || null)
            : state.activeSessionId,
        });
      },

      renameSessionTab: (sessionId, title) =>
        set((state) => ({
          sessionTabs: state.sessionTabs.map((t) =>
            t.id === sessionId ? { ...t, title } : t
          ),
          sessions: state.sessions.map((s) =>
            s.id === sessionId ? { ...s, title, updatedAt: new Date().toISOString() } : s
          ),
        })),

      getActiveSessionTab: () => {
        const state = get();
        return state.sessionTabs.find((t) => t.id === state.activeSessionId) || null;
      },

      getVisibleSessionTabs: () => {
        const state = get();
        const active = state.sessionTabs.find((t) => t.id === state.activeSessionId);
        if (!active) return state.sessionTabs.slice(0, 1);
        return [active];
      },
    }),
    {
      name: 'jwcode-session-storage',
      partialize: (state) => ({
        sessions: state.sessions,
        sessionTabs: state.sessionTabs,
        activeSessionId: state.activeSessionId,
        layout: state.layout,
      }),
    }
  )
);

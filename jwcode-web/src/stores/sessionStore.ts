import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Session } from '../types';

interface SessionState {
  sessions: Session[];
  activeSessionId: string | null;
  
  // Actions
  addSession: (session: Session) => void;
  removeSession: (sessionId: string) => void;
  updateSession: (sessionId: string, updates: Partial<Session>) => void;
  setActiveSession: (sessionId: string) => void;
}

export const useSessionStore = create<SessionState>()(
  persist(
    (set) => ({
      sessions: [],
      activeSessionId: null,

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
    }),
    {
      name: 'jwcode-session-storage',
    }
  )
);
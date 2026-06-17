import { create } from 'zustand';

export type ErrorLevel = 'info' | 'warning' | 'error' | 'critical';

export interface ErrorEntry {
  id: string;
  level: ErrorLevel;
  title: string;
  detail?: string;
  action?: { label: string; onClick: () => void };
  timestamp: number;
  dismissed: boolean;
}

interface ErrorState {
  errors: ErrorEntry[];
  push: (level: ErrorLevel, title: string, detail?: string, action?: ErrorEntry['action']) => string;
  dismiss: (id: string) => void;
  clearAll: () => void;
}

const MAX_ERRORS = 50;

function nextId(): string {
  return `err-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

export const useErrorStore = create<ErrorState>((set) => ({
  errors: [],

  push: (level, title, detail, action) => {
    const id = nextId();
    const entry: ErrorEntry = { id, level, title, detail, action, timestamp: Date.now(), dismissed: false };
    set((s) => ({ errors: [...s.errors.slice(-(MAX_ERRORS - 1)), entry] }));
    return id;
  },

  dismiss: (id) => set((s) => ({
    errors: s.errors.map((e) => (e.id === id ? { ...e, dismissed: true } : e)),
  })),

  clearAll: () => set({ errors: [] }),
}));

// Convenience helpers — call from non-React code
export const errLog = {
  info: (title: string, detail?: string) => useErrorStore.getState().push('info', title, detail),
  warn: (title: string, detail?: string) => useErrorStore.getState().push('warning', title, detail),
  error: (title: string, detail?: string) => useErrorStore.getState().push('error', title, detail),
  critical: (title: string, detail?: string) => useErrorStore.getState().push('critical', title, detail),
};

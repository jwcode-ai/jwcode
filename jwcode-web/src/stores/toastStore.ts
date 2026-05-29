import { create } from 'zustand';

export type ToastType = 'info' | 'success' | 'warning' | 'error';

export interface Toast {
  id: string;
  type: ToastType;
  message: string;
  duration?: number; // ms, 0 = persist
  createdAt: number;
}

interface ToastState {
  toasts: Toast[];
  addToast: (type: ToastType, message: string, duration?: number) => void;
  dismissToast: (id: string) => void;
  clearAll: () => void;
}

const DEFAULT_DURATION = 4000;

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],

  addToast: (type, message, duration) => {
    const id = `toast-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
    const toast: Toast = { id, type, message, duration: duration ?? DEFAULT_DURATION, createdAt: Date.now() };
    set((s) => ({ toasts: [...s.toasts.slice(-9), toast] })); // max 10 toasts

    if (toast.duration! > 0) {
      setTimeout(() => {
        set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
      }, toast.duration);
    }
  },

  dismissToast: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),

  clearAll: () => set({ toasts: [] }),
}));

// Convenience exports
export const toast = {
  info: (msg: string, dur?: number) => useToastStore.getState().addToast('info', msg, dur),
  success: (msg: string, dur?: number) => useToastStore.getState().addToast('success', msg, dur),
  warning: (msg: string, dur?: number) => useToastStore.getState().addToast('warning', msg, dur),
  error: (msg: string, dur?: number) => useToastStore.getState().addToast('error', msg, dur ?? 6000),
};

import { create } from 'zustand';

export type TerminalStatus = 'idle' | 'starting' | 'running' | 'stopping' | 'error';

interface TerminalState {
  isOpen: boolean;
  status: TerminalStatus;
  ttydPort: number | null;
  ttydWsUrl: string | null;
  errorMessage: string | null;

  openTerminal: () => void;
  closeTerminal: () => void;
  toggleTerminal: () => void;
  setStarting: () => void;
  setRunning: (port: number, wsUrl: string) => void;
  setStopping: () => void;
  setIdle: () => void;
  setError: (message: string) => void;
}

export const useTerminalStore = create<TerminalState>((set) => ({
  isOpen: false,
  status: 'idle',
  ttydPort: null,
  ttydWsUrl: null,
  errorMessage: null,

  openTerminal: () => set({ isOpen: true }),
  closeTerminal: () => set({ isOpen: false }),
  toggleTerminal: () => set((s) => ({ isOpen: !s.isOpen })),
  setStarting: () => set({ status: 'starting', errorMessage: null }),
  setRunning: (port, wsUrl) => set({ status: 'running', ttydPort: port, ttydWsUrl: wsUrl }),
  setStopping: () => set({ status: 'stopping' }),
  setIdle: () => set({ status: 'idle', ttydPort: null, ttydWsUrl: null, errorMessage: null }),
  setError: (message) => set({ status: 'error', errorMessage: message }),
}));

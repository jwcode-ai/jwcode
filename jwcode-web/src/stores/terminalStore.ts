import { create } from 'zustand';

interface TerminalState {
  isOpen: boolean;
  output: string[];
  isExecuting: boolean;
  
  // Actions
  openTerminal: () => void;
  closeTerminal: () => void;
  toggleTerminal: () => void;
  addOutput: (line: string) => void;
  clearOutput: () => void;
  setExecuting: (executing: boolean) => void;
}

export const useTerminalStore = create<TerminalState>((set) => ({
  isOpen: false,
  output: [],
  isExecuting: false,

  openTerminal: () => set({ isOpen: true }),
  closeTerminal: () => set({ isOpen: false }),
  toggleTerminal: () => set((state) => ({ isOpen: !state.isOpen })),
  
  addOutput: (line) =>
    set((state) => ({
      output: [...state.output, line],
    })),
    
  clearOutput: () => set({ output: [] }),
  setExecuting: (executing) => set({ isExecuting: executing }),
}));
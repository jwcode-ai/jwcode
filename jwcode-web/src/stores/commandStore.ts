import { create } from 'zustand';

/**
 * 后端命令描述
 */
export interface BackendCommand {
  name: string;
  description: string;
  usage: string;
}

interface CommandState {
  /** 从后端获取的命令列表 */
  backendCommands: BackendCommand[];
  /** 是否已从后端获取 */
  loaded: boolean;
  /** 设置后端命令列表 */
  setBackendCommands: (commands: BackendCommand[]) => void;
  /** 获取所有命令（合并别名） */
  getAllCommands: () => BackendCommand[];
}

export const useCommandStore = create<CommandState>()((set, get) => ({
  backendCommands: [],
  loaded: false,

  setBackendCommands: (commands: BackendCommand[]) => {
    set({ backendCommands: commands, loaded: true });
  },

  getAllCommands: () => {
    return get().backendCommands;
  },
}));

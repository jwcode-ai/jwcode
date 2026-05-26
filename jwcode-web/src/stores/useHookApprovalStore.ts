import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/**
 * Hook 审批条目
 */
export interface HookApprovalItem {
  approvalId: string;
  toolName: string;
  askPayload: string;
  timestamp: number;
}

/**
 * Hook 审批状态
 */
interface HookApprovalState {
  /** 待审批列表 */
  pendingApprovals: HookApprovalItem[];

  /** 自动模式：所有 ASK 自动批准 */
  autoMode: boolean;

  /** 当前会话已批准的工具列表 */
  sessionAllowList: string[];

  /** 是否显示 hooks.json 配置说明 */
  showConfigHint: boolean;

  // Actions
  addApproval: (item: HookApprovalItem) => void;
  removeApproval: (approvalId: string) => void;
  clearApprovals: () => void;

  setAutoMode: (enabled: boolean) => void;
  addToSessionAllowList: (toolName: string) => void;
  isSessionAllowed: (toolName: string) => boolean;

  setShowConfigHint: (show: boolean) => void;
}

export const useHookApprovalStore = create<HookApprovalState>()(
  persist(
    (set, get) => ({
      pendingApprovals: [],
      autoMode: false,
      sessionAllowList: [],
      showConfigHint: true,

      addApproval: (item) =>
        set((state) => ({
          pendingApprovals: [...state.pendingApprovals, item],
        })),

      removeApproval: (approvalId) =>
        set((state) => ({
          pendingApprovals: state.pendingApprovals.filter(
            (a) => a.approvalId !== approvalId
          ),
        })),

      clearApprovals: () => set({ pendingApprovals: [] }),

      setAutoMode: (enabled) => set({ autoMode: enabled }),

      addToSessionAllowList: (toolName) =>
        set((state) => {
          if (state.sessionAllowList.includes(toolName)) return state;
          return {
            sessionAllowList: [...state.sessionAllowList, toolName],
          };
        }),

      isSessionAllowed: (toolName) => {
        return get().sessionAllowList.includes(toolName);
      },

      setShowConfigHint: (show) => set({ showConfigHint: show }),
    }),
    {
      name: 'jwcode-hook-approval',
      partialize: (state) => ({
        autoMode: state.autoMode,
        showConfigHint: state.showConfigHint,
        // sessionAllowList 不持久化（每次会话重置）
      }),
    }
  )
);

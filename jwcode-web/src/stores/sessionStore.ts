import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Session, SessionTab, SessionTask, SplitLayout } from '../types';


const MAX_SESSION_TABS = 6;
const MAX_HISTORY_SESSIONS = 50;

interface SessionState {
  sessions: Session[];
  activeSessionId: string | null;
  sessionTabs: SessionTab[];
  layout: SplitLayout;

  // 每个会话独立的工作目录
  sessionWorkspaceDirs: Record<string, string>;

  // 每个会话独立的任务列表
  tasksBySession: Record<string, SessionTask[]>;

  // 历史会话列表（关闭的会话移入此处，支持查看和唤起）
  historySessions: SessionTab[];

  // Actions
  addSession: (session: Session) => void;
  removeSession: (sessionId: string) => void;
  updateSession: (sessionId: string, updates: Partial<Session>) => void;
  setActiveSession: (sessionId: string) => void;

  // 多会话 Tab 管理
  addSessionTab: (title?: string, workspaceDir?: string) => string;
  closeSessionTab: (sessionId: string) => void;
  renameSessionTab: (sessionId: string, title: string) => void;
  getActiveSessionTab: () => SessionTab | null;
  getVisibleSessionTabs: () => SessionTab[];

  // 会话工作目录管理
  setSessionWorkspaceDir: (sessionId: string, dir: string) => void;
  getSessionWorkspaceDir: (sessionId: string) => string;

  // 会话任务管理
  addSessionTask: (sessionId: string, title: string, backendId?: string, description?: string) => void;
  toggleSessionTask: (sessionId: string, taskId: string) => void;
  removeSessionTask: (sessionId: string, taskId: string) => void;
  updateSessionTask: (sessionId: string, taskId: string, title: string) => void;
  getSessionTasks: (sessionId: string) => SessionTask[];
  setSessionTasks: (sessionId: string, tasks: SessionTask[]) => void;
  /** 更新任务的 Plan 状态字段（统一数据源入口） */
  updateTaskPlanStatus: (sessionId: string, taskId: string, updates: Partial<SessionTask>) => void;
  // 会话自动命名（用首条用户消息）
  autoNameSession: (sessionId: string, firstUserMessage: string) => void;

  // 历史会话管理
  getHistorySessions: () => SessionTab[];
  restoreHistorySession: (sessionId: string) => void;
  clearHistorySessions: () => void;
  removeHistorySession: (sessionId: string) => void;
}

export const useSessionStore = create<SessionState>()(
  persist(
    (set, get) => ({
      sessions: [],
      activeSessionId: null,
      sessionTabs: [],
      layout: 'single',
      sessionWorkspaceDirs: {},
      tasksBySession: {},
      historySessions: [],

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

      // 会话工作目录管理
      setSessionWorkspaceDir: (sessionId, dir) =>
        set((state) => ({
          sessionWorkspaceDirs: {
            ...state.sessionWorkspaceDirs,
            [sessionId]: dir,
          },
        })),

      getSessionWorkspaceDir: (sessionId) => {
        const state = get();
        return state.sessionWorkspaceDirs[sessionId] || '';
      },

      // 多会话 Tab 管理
      addSessionTab: (title, workspaceDir) => {
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

        // 记录工作目录（如果传入了 workspaceDir）
        const workspaceDirs = { ...state.sessionWorkspaceDirs };
        if (workspaceDir) {
          workspaceDirs[sessionId] = workspaceDir;
        }

        // 初始化该会话的空任务列表
        const tasksBySession = { ...state.tasksBySession };
        if (!tasksBySession[sessionId]) {
          tasksBySession[sessionId] = [];
        }

        set({
          sessionTabs: [...state.sessionTabs, newTab],
          sessions: [...state.sessions, newSession],
          sessionWorkspaceDirs: workspaceDirs,
          tasksBySession,
          activeSessionId: sessionId,
        });

        return sessionId;
      },

      closeSessionTab: (sessionId) => {
        const state = get();
        const tabToClose = state.sessionTabs.find((t) => t.id === sessionId);
        const remaining = state.sessionTabs.filter((t) => t.id !== sessionId);
        if (remaining.length === 0) return; // 至少保留一个


        // 关闭的会话移入历史列表（保留最近 MAX_HISTORY_SESSIONS 条）
        let historySessions = state.historySessions;
        if (tabToClose) {
          historySessions = [
            tabToClose,
            ...historySessions.filter((h) => h.id !== sessionId),
          ].slice(0, MAX_HISTORY_SESSIONS);
        }

        const lastTab = remaining[remaining.length - 1];
        set({
          sessionTabs: remaining,
          historySessions,
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
          historySessions: state.historySessions.map((h) =>
            h.id === sessionId ? { ...h, title } : h
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

      // === 会话任务管理 ===

      addSessionTask: (sessionId, title, backendId, description) =>
        set((state) => {
          const tasks = state.tasksBySession[sessionId] || [];
          const newTask: SessionTask = {
            id: `session-task-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
            title: title.trim(),
            completed: false,
            createdAt: Date.now(),
            backendId,
            description,
          };
          return {
            tasksBySession: {
              ...state.tasksBySession,
              [sessionId]: [...tasks, newTask],
            },
          };
        }),

      toggleSessionTask: (sessionId, taskId) =>
        set((state) => {
          const tasks = state.tasksBySession[sessionId] || [];
          return {
            tasksBySession: {
              ...state.tasksBySession,
              [sessionId]: tasks.map((t) =>
                t.id === taskId ? { ...t, completed: !t.completed } : t
              ),
            },
          };
        }),

      removeSessionTask: (sessionId, taskId) =>
        set((state) => ({
          tasksBySession: {
            ...state.tasksBySession,
            [sessionId]: (state.tasksBySession[sessionId] || []).filter((t) => t.id !== taskId),
          },
        })),

      updateSessionTask: (sessionId, taskId, title) =>
        set((state) => {
          const tasks = state.tasksBySession[sessionId] || [];
          return {
            tasksBySession: {
              ...state.tasksBySession,
              [sessionId]: tasks.map((t) =>
                t.id === taskId ? { ...t, title: title.trim() } : t
              ),
            },
          };
        }),

      getSessionTasks: (sessionId) => {
        return get().tasksBySession[sessionId] || [];
      },

      setSessionTasks: (sessionId, tasks) =>
        set((state) => ({
          tasksBySession: {
            ...state.tasksBySession,
            [sessionId]: tasks,
          },
        })),

      /**
       * 更新某个任务的 Plan 状态字段（由 WebSocket plan_task_* 消息触发）
       * 统一数据源：所有任务状态变更都通过此方法写入 sessionStore
       */
      updateTaskPlanStatus: (sessionId, taskId, updates) =>
        set((state) => {
          const tasks = state.tasksBySession[sessionId] || [];
          return {
            tasksBySession: {
              ...state.tasksBySession,
              [sessionId]: tasks.map((t) =>
                t.id === taskId ? { ...t, ...updates } : t
              ),
            },
          };
        }),

      // === 会话自动命名（用首条用户消息截取前20字） ===

      autoNameSession: (sessionId, firstUserMessage) => {
        const state = get();
        // 只有标题还是默认的"对话 N"时才自动命名
        const tab = state.sessionTabs.find((t) => t.id === sessionId);
        if (!tab) return;
        if (!/^对话 \d+$/.test(tab.title)) return; // 已经被手动改过名就不覆盖

        const name = firstUserMessage.trim().slice(0, 20) + (firstUserMessage.trim().length > 20 ? '...' : '');
        if (!name) return;

        get().renameSessionTab(sessionId, name);
      },

      // === 历史会话管理 ===

      getHistorySessions: () => {
        return get().historySessions;
      },

      restoreHistorySession: (sessionId) => {
        const state = get();
        const historyTab = state.historySessions.find((h) => h.id === sessionId);
        if (!historyTab) return;

        // 从历史中移除
        const newHistory = state.historySessions.filter((h) => h.id !== sessionId);

        // 检查是否已经在 tabs 中
        if (state.sessionTabs.some((t) => t.id === sessionId)) {
          set({
            historySessions: newHistory,
            activeSessionId: sessionId,
          });
          return;
        }

        // 如果 tabs 已满，先关掉最旧的
        let tabs = state.sessionTabs;
        if (tabs.length >= MAX_SESSION_TABS) {
          const oldest = tabs.reduce((a, b) =>
            a.createdAt < b.createdAt ? a : b
          );
          tabs = tabs.filter((t) => t.id !== oldest.id);
          // 关掉的也放入历史
          newHistory.unshift(oldest);
        }

        set({
          sessionTabs: [...tabs, historyTab],
          historySessions: newHistory.slice(0, MAX_HISTORY_SESSIONS),
          activeSessionId: sessionId,
        });
      },

      clearHistorySessions: () =>
        set({ historySessions: [] }),

      removeHistorySession: (sessionId) =>
        set((state) => ({
          historySessions: state.historySessions.filter((h) => h.id !== sessionId),
        })),
    }),
    {
      name: 'jwcode-session-storage',
      partialize: (state) => ({
        sessions: state.sessions,
        sessionTabs: state.sessionTabs,
        activeSessionId: state.activeSessionId,
        layout: state.layout,
        sessionWorkspaceDirs: state.sessionWorkspaceDirs,
        tasksBySession: state.tasksBySession,
        historySessions: state.historySessions,
      }),
    }
  )
);

import { useState, useEffect, useCallback, useRef, lazy, Suspense, startTransition } from 'react';
import { MessageSquare, Terminal, FolderTree, Settings, Brain, Wrench, Target, Users, FileText, ScrollText, LucideIcon, Menu, X, ChevronDown, ChevronUp, ListChecks } from 'lucide-react';
import { useChatStore } from './stores/chatStore';
import { useSessionStore } from './stores/sessionStore';
import { useTerminalStore } from './stores/terminalStore';
import { useSettingsStore } from './stores/settingsStore';
import { usePlanStore } from './stores/planStore';
import { useHookApprovalStore } from './stores/useHookApprovalStore';
import wsService from './services/websocket';
import { useWebSocket } from './hooks/useWebSocket';
import { Tab, TabId, LogEntry, SessionTask } from './types';
import { ErrorBoundary } from './components/ErrorBoundary';
import { api } from './services/api';

// 懒加载非首屏组件
import { StatusLine } from './components/Chat/StatusLine';
const ModelsView = lazy(() => import('./components/Models/ModelsView').then(m => ({ default: m.ModelsView })));
const ToolsView = lazy(() => import('./components/Tools/ToolsView').then(m => ({ default: m.ToolsView })));
const SkillsView = lazy(() => import('./components/Skills/SkillsView').then(m => ({ default: m.SkillsView })));
const AgentsView = lazy(() => import('./components/Agents/AgentsView').then(m => ({ default: m.AgentsView })));
const FileTreeView = lazy(() => import('./components/FileTree/FileTreeView').then(m => ({ default: m.FileTreeView })));
const TerminalView = lazy(() => import('./components/Terminal/TerminalView').then(m => ({ default: m.TerminalView })));
const SessionTabs = lazy(() => import('./components/Chat/SessionTabs').then(m => ({ default: m.SessionTabs })));
const SessionGrid = lazy(() => import('./components/Chat/SessionGrid').then(m => ({ default: m.SessionGrid })));
const SettingsPanel = lazy(() => import('./components/Settings/SettingsPanel').then(m => ({ default: m.SettingsPanel })));
const LogsPanel = lazy(() => import('./components/Logs/LogsPanel').then(m => ({ default: m.LogsPanel })));
const PlanPanel = lazy(() => import('./components/Plan/PlanPanel').then(m => ({ default: m.PlanPanel })));
const HookApprovalModal = lazy(() => import('./components/Hook/HookApprovalModal').then(m => ({ default: m.HookApprovalModal })));

// 懒加载组件的加载占位
const PanelFallback = () => (
  <div className="flex-1 flex items-center justify-center bg-dark-bg">
    <div className="w-5 h-5 border-2 border-accent-blue border-t-transparent rounded-full animate-spin" />
  </div>
);

// Tab configuration
const TABS: Tab[] = [
  { id: 'chat', title: '对话', icon: 'MessageSquare' },
  { id: 'terminal', title: '终端', icon: 'Terminal' },
  { id: 'files', title: '文件', icon: 'FolderTree' },
  { id: 'models', title: '模型', icon: 'Brain' },
  { id: 'tools', title: '工具', icon: 'Wrench' },
  { id: 'skills', title: '技能', icon: 'Target' },
  { id: 'agents', title: 'Agents', icon: 'Users' },
  { id: 'settings', title: '设置', icon: 'Settings' },
  { id: 'logs', title: '日志', icon: 'ScrollText', },
];

const ICON_MAP: Record<string, LucideIcon> = {
  MessageSquare, Terminal, FolderTree, Settings, Brain,
  Wrench, Target, Users, FileText, ScrollText, ListChecks,
};

function App() {
  const [activeTab, setActiveTab] = useState<TabId>('chat');
  const [isConnected, setIsConnected] = useState(false);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [unreadLogs, setUnreadLogs] = useState(0);
  const [isLogDrawerOpen, setIsLogDrawerOpen] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [hookModalOpen, setHookModalOpen] = useState(false);
  const [isTaskListOpen, setIsTaskListOpen] = useState(true);

  const { toggleTerminal } = useTerminalStore();
  const { theme, setTheme, workspaceDir, setWorkspaceDir } = useSettingsStore();
  const approvalStore = useHookApprovalStore();

  // 使用 startTransition 包裹 Tab 切换，避免懒加载组件在同步事件中 suspend
  const handleTabChange = useCallback((tabId: TabId) => {
    startTransition(() => {
      setActiveTab(tabId);
    });
  }, []);

  // 全局错误捕获：将未捕获的错误显示到日志面板
  useEffect(() => {
    const handleGlobalError = (event: ErrorEvent) => {
      const errorMsg = event.message || '未知错误';
      const source = event.filename || 'global';
      // 过滤掉 Chrome 扩展相关的错误，避免刷屏
      if (source.includes('chrome-extension://') || source.includes('moz-extension://')) {
        return;
      }
      setLogs(prev => [...prev, {
        id: `error-${Date.now()}-${crypto.randomUUID()}`,
        level: 'error' as LogEntry['level'],
        source: '浏览器',
        message: `未捕获错误: ${errorMsg}`,
        timestamp: Date.now(),
      }].slice(-500));
    };

    const handleRejection = (event: PromiseRejectionEvent) => {
      const reason = event.reason?.message || event.reason || '未知原因';
      setLogs(prev => [...prev, {
        id: `rejection-${Date.now()}-${crypto.randomUUID()}`,
        level: 'error' as LogEntry['level'],
        source: 'Promise',
        message: `未捕获的 Promise 拒绝: ${reason}`,
        timestamp: Date.now(),
      }].slice(-500));
    };

    window.addEventListener('error', handleGlobalError);
    window.addEventListener('unhandledrejection', handleRejection);

    return () => {
      window.removeEventListener('error', handleGlobalError);
      window.removeEventListener('unhandledrejection', handleRejection);
    };
  }, []);

  // 多会话状态
  const {
    sessionTabs,
    activeSessionId,
    setActiveSession,
    addSessionTab,
    closeSessionTab,
    renameSessionTab,
    getVisibleSessionTabs,
    historySessions,
    restoreHistorySession,
    clearHistorySessions,
    removeHistorySession,
    autoNameSession,
    tasksBySession,
    addSessionTask,
    toggleSessionTask,
    removeSessionTask,
    updateSessionTask,
    setSessionTasks,
  } = useSessionStore();

  const { clearMessages } = useChatStore();

  // 修复主题切换：同步到 DOM
  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'dark') {
      root.classList.add('dark');
      root.classList.remove('light');
    } else if (theme === 'light') {
      root.classList.add('light');
      root.classList.remove('dark');
    } else {
      const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
      root.classList.toggle('dark', prefersDark);
      root.classList.toggle('light', !prefersDark);
    }
  }, [theme]);

  // WebSocket connection
  useWebSocket({ activeTab, setLogs, setUnreadLogs });

  // 监听 switch-tab 自定义事件（用于 Plan 模式自动切换到 Plan 面板）
  useEffect(() => {
    const handler = (e: CustomEvent) => {
      const tabId = e.detail as TabId;
      if (tabId && tabId !== activeTab) {
        handleTabChange(tabId);
      }
    };
    window.addEventListener('switch-tab', handler as EventListener);
    return () => window.removeEventListener('switch-tab', handler as EventListener);
  }, [activeTab, handleTabChange]);

  // WebSocket connection status
  useEffect(() => {
    const unsubOpen = wsService.onOpen(() => setIsConnected(true));
    const unsubClose = wsService.onClose(() => setIsConnected(false));
    return () => { unsubOpen(); unsubClose(); };
  }, []);

  // 初始化默认会话 Tab（如果没有）
  useEffect(() => {
    if (sessionTabs.length === 0) {
      addSessionTab('对话 1');
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Hook 审批弹窗事件监听
  useEffect(() => {
    const handler = () => setHookModalOpen(true);
    window.addEventListener('hook-approval-required', handler);
    return () => window.removeEventListener('hook-approval-required', handler);
  }, []);

  // 自动模式变更时处理待审批项
  useEffect(() => {
    if (approvalStore.autoMode && approvalStore.pendingApprovals.length > 0) {
      // autoMode 打开 → 关闭弹窗
      setHookModalOpen(false);
    }
    if (approvalStore.pendingApprovals.length > 0 && !approvalStore.autoMode) {
      setHookModalOpen(true);
    }
  }, [approvalStore.autoMode, approvalStore.pendingApprovals.length]);

  const handleNewSession = useCallback(() => {
    startTransition(() => {
      // 新建会话继承当前工作目录
      const newId = addSessionTab(undefined, workspaceDir);
      wsService.setSessionId(newId);

      // 重置 planStore 全局状态，避免模式/确认状态残留
      usePlanStore.getState().setMode('act');
      usePlanStore.getState().setShowConfirmButton(false);
    });
  }, [addSessionTab, workspaceDir]);

  const handleSwitchSession = useCallback((sessionId: string) => {
    setActiveSession(sessionId);
    wsService.setSessionId(sessionId);
  }, [setActiveSession]);

  const handleCloseSession = useCallback((sessionId: string) => {
    closeSessionTab(sessionId);
    // 不清除消息（保留在 localStorage 以便恢复）
  }, [closeSessionTab]);

  const handleRenameSession = useCallback((sessionId: string, title: string) => {
    renameSessionTab(sessionId, title);
  }, [renameSessionTab]);

  const handleClearMessages = useCallback(() => {
    if (activeSessionId) clearMessages(activeSessionId);
  }, [activeSessionId, clearMessages]);

  const sendMessage = useCallback((sessionId: string, content: string) => {
    if (!content.trim()) return;

    const isGen = useChatStore.getState().isGenerating(sessionId);

    if (isGen) return;

    const planStore = usePlanStore.getState();

    // Plan 模式校验：如果有未确认的计划，阻止发送新消息
    if (planStore.mode === 'plan' && planStore.showConfirmButton && !planStore.planConfirmed) {
      console.warn('[App] Plan 模式下有待确认的计划，不能发送新消息');
      // 添加系统提示消息告知用户
      useChatStore.getState().addMessage(sessionId, {
        id: `msg-${Date.now()}`,
        type: 'system',
        content: '⚠️ 当前有未确认的计划。请先在上方确认或取消当前计划，再发送新消息。',
        timestamp: Date.now(),
      });
      return;
    }

    // 发送新消息时清除暂停状态
    useChatStore.getState().resumeGeneration(sessionId);

    wsService.setSessionId(sessionId);

    // 首条用户消息自动命名会话
    const msgs = useChatStore.getState().getMessages(sessionId);
    const hasUserMsg = msgs.some((m) => m.type === 'user');
    if (!hasUserMsg) {
      autoNameSession(sessionId, content.trim());
    }

    useChatStore.getState().addMessage(sessionId, {
      id: `msg-${Date.now()}`,
      type: 'user',
      content: content.trim(),
      timestamp: Date.now(),
    });

    // 根据 Plan/Act 模式决定消息类型
    const currentMode = planStore.mode;
    if (currentMode === 'plan') {
      // Plan 模式：发送 plan 消息，自动创建 Task（PENDING）
      // 后端 handlePlanMessage 会处理规划流程
      if (planStore.activePlanSessionId !== sessionId) {
        planStore.startPlanning(sessionId, content.trim());
      }
      wsService.send({
        type: 'plan',
        sessionId,
        message: content.trim(),
      });
    } else {
      // Act/Chat 模式：直接发送 chat 消息
      wsService.send({
        type: 'chat',
        sessionId,
        message: content.trim(),
      });
    }
  }, [autoNameSession]);

  // 停止生成
  const stopGeneration = useCallback((sessionId: string) => {
    wsService.send({ type: 'stop', sessionId });
    useChatStore.getState().endGeneration(sessionId);
  }, []);

  // 暂停生成
  const pauseGeneration = useCallback((sessionId: string) => {
    wsService.send({ type: 'pause', sessionId });
    useChatStore.getState().pauseGeneration(sessionId);
  }, []);

  // 恢复生成
  const resumeGeneration = useCallback((sessionId: string) => {
    wsService.send({ type: 'resume', sessionId });
    useChatStore.getState().resumeGeneration(sessionId);
  }, []);

  // 切换工作目录：用户输入新路径后重置所有会话
  const handleWorkspaceChange = useCallback(() => {
    startTransition(() => {
      const newDir = window.prompt('请输入新的工作目录路径：', workspaceDir);
      if (newDir && newDir.trim() && newDir.trim() !== workspaceDir) {
        const trimmed = newDir.trim();
        // 1. 保存新目录到前端 store
        setWorkspaceDir(trimmed);

        // 2. 通过 WebSocket 通知后端切换工作目录
        wsService.send({
          type: 'workspace',
          message: trimmed,
        });

        // 3. 重置所有会话 — 直接操作 store 内部状态
        const store = useSessionStore.getState();
        // 清空所有 session 的消息和计划
        store.sessionTabs.forEach(t => {
          useChatStore.getState().clearMessages(t.id);
          usePlanStore.getState().clearPlan(t.id);
        });
        // 直接重置 sessionTabs 和 sessions 为空
        useSessionStore.setState({
          sessionTabs: [],
          sessions: [],
          sessionWorkspaceDirs: {},
          activeSessionId: null,
        });

        // 4. 创建新会话（携带新工作目录）
        setTimeout(() => {
          useSessionStore.getState().addSessionTab('对话 1', trimmed);
        }, 0);
      }
    });
  }, [workspaceDir, setWorkspaceDir]);


  const handleClearLogs = () => {
    setLogs([]);
    setUnreadLogs(0);
  };


  // 任务列表组件（右侧面板）- 每个会话维护独立任务列表，支持增删改
  // 挂载时自动从后端 /api/tasks 拉取任务，实现与 MCP TaskCreate 的双向同步
  function TaskListPanel() {
    const sid = useSessionStore((s) => s.activeSessionId);
    const tasks = sid ? (useSessionStore((s) => s.tasksBySession[sid]) || []) : [];
    const [newTaskTitle, setNewTaskTitle] = useState('');
    const [editingTaskId, setEditingTaskId] = useState<string | null>(null);
    const [editValue, setEditValue] = useState('');
    const inputRef = useRef<HTMLInputElement>(null);
    const loadedRef = useRef(false);

    // 挂载时从后端加载任务列表（只执行一次）
    useEffect(() => {
      if (!sid || loadedRef.current) return;
      loadedRef.current = true;

      // 新 session 且本地任务为空时，不从后端拉取旧任务
      const existing = useSessionStore.getState().tasksBySession[sid] || [];
      if (existing.length === 0) return;

      api.tasks.list().then(res => {
        if (!res.success || !res.data) return;
        const backendTasks: any[] = res.data;
        if (backendTasks.length === 0) return;

        const existingTitles = new Set(existing.map(t => t.title));

        // 将后端任务中尚未在本地存在的任务添加进来
        const newTasks = backendTasks
          .filter((bt: any) => !existingTitles.has(bt.title))
          .map((bt: any) => ({
            id: `session-task-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
            title: bt.title,
            completed: bt.status === 'COMPLETED',
            createdAt: Date.parse(bt.createdAt) || Date.now(),
            backendId: bt.id,
            backendStatus: bt.status as any,
            description: bt.description,
          }));

        if (newTasks.length > 0) {
          setSessionTasks(sid, [...existing, ...newTasks]);
        }
      }).catch(e => {
        console.warn('[TaskListPanel] 从后端加载任务失败:', e);
      });
    }, [sid, setSessionTasks]);

    const handleAdd = useCallback(async () => {
      if (!newTaskTitle.trim() || !sid) return;

      // 先在后端创建，获取 backendId
      try {
        const result = await api.tasks.create({ title: newTaskTitle.trim(), description: '' });
        if (result.success && result.data) {
          const backendTask = result.data as any;
          addSessionTask(sid, newTaskTitle.trim(), backendTask.id, '');
        } else {
          // 后端创建失败，仅本地添加
          addSessionTask(sid, newTaskTitle.trim());
        }
      } catch (e) {
        console.warn('[TaskListPanel] 同步任务到后端失败:', e);
        addSessionTask(sid, newTaskTitle.trim());
      }

      setNewTaskTitle('');
      setTimeout(() => inputRef.current?.focus(), 0);
    }, [newTaskTitle, sid, addSessionTask]);

    const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
      if (e.key === 'Enter') handleAdd();
    }, [handleAdd]);

    const handleEditStart = useCallback((task: { id: string; title: string }) => {
      setEditingTaskId(task.id);
      setEditValue(task.title);
    }, []);

    const handleEditConfirm = useCallback(() => {
      if (editingTaskId && editValue.trim() && sid) {
        updateSessionTask(sid, editingTaskId, editValue.trim());
      }
      setEditingTaskId(null);
    }, [editingTaskId, editValue, sid, updateSessionTask]);

    const handleEditKeyDown = useCallback((e: React.KeyboardEvent) => {
      if (e.key === 'Enter') handleEditConfirm();
      else if (e.key === 'Escape') setEditingTaskId(null);
    }, [handleEditConfirm]);

    // 根据 backendId 或 title 查找后端任务并执行操作
    const syncBackendAction = useCallback(async (task: SessionTask, action: 'toggle' | 'delete') => {
      const backendId = (task as any).backendId;
      if (backendId) {
        // 有 backendId，直接操作
        if (action === 'toggle') {
          const newStatus = task.completed ? 'PENDING' : 'COMPLETED';
          await api.tasks.updateStatus(backendId, newStatus as any).catch(() => {});
        } else {
          await api.tasks.delete(backendId).catch(() => {});
        }
        return;
      }
      // 降级：按 title 匹配
      try {
        const res = await api.tasks.list();
        if (res.success && res.data) {
          const backendTask = (res.data as any[]).find((t: any) => t.title === task.title);
          if (backendTask) {
            if (action === 'toggle') {
              const newStatus = task.completed ? 'PENDING' : 'COMPLETED';
              await api.tasks.updateStatus(backendTask.id, newStatus as any).catch(() => {});
            } else {
              await api.tasks.delete(backendTask.id).catch(() => {});
            }
          }
        }
      } catch (e) {
        console.warn('[TaskListPanel] 同步操作到后端失败:', e);
      }
    }, []);

    return (
      <div className="px-3 pb-2">
        {/* 添加新任务 */}
        <div className="flex items-center gap-1 mb-2">
          <input
            ref={inputRef}
            value={newTaskTitle}
            onChange={(e) => setNewTaskTitle(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="添加任务..."
            className="flex-1 bg-dark-bg border border-dark-border rounded px-2 py-1 text-[11px] text-dark-text placeholder-dark-muted outline-none focus:border-accent-blue/50 transition-colors"
          />
          <button
            onClick={handleAdd}
            disabled={!newTaskTitle.trim()}
            className="px-2 py-1 text-[10px] bg-accent-blue text-white rounded hover:opacity-90 transition-opacity disabled:opacity-40 disabled:cursor-not-allowed"
          >
            添加
          </button>
        </div>

        {/* 任务列表 */}
        {tasks.length === 0 ? (
          <div className="text-[11px] text-dark-muted text-center py-2">
            暂无任务，在上方输入添加
          </div>
        ) : (
          <div className="max-h-[180px] overflow-y-auto custom-scrollbar space-y-0.5">
            {tasks.map((task) => {
              const isEditing = editingTaskId === task.id;
              return (
                <div
                  key={task.id}
                  className="flex items-center gap-1.5 px-2 py-1 rounded text-[11px] transition-colors hover:bg-dark-hover/50 group"
                >
                  {/* 勾选框 */}
                  <button
                    onClick={() => {
                      if (!sid) return;
                      toggleSessionTask(sid, task.id);
                      syncBackendAction(task, 'toggle');
                    }}
                    className={`shrink-0 w-3.5 h-3.5 rounded border flex items-center justify-center transition-colors
                      ${task.completed
                        ? 'bg-accent-green border-accent-green'
                        : 'border-dark-border hover:border-accent-blue'
                      }`}
                  >
                    {task.completed && (
                      <svg width="8" height="8" viewBox="0 0 8 8" fill="none">
                        <path d="M1 4L3 6L7 2" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                      </svg>
                    )}
                  </button>

                  {/* 标题（编辑模式或显示模式） */}
                  {isEditing ? (
                    <input
                      value={editValue}
                      onChange={(e) => setEditValue(e.target.value)}
                      onBlur={handleEditConfirm}
                      onKeyDown={handleEditKeyDown}
                      className="flex-1 bg-dark-bg border border-accent-blue rounded px-1 py-0.5 text-[11px] text-dark-text outline-none"
                      autoFocus
                      onClick={(e) => e.stopPropagation()}
                    />
                  ) : (
                    <span
                      className={`flex-1 truncate cursor-pointer ${task.completed ? 'text-dark-muted line-through' : 'text-dark-text'}`}
                      onDoubleClick={() => handleEditStart(task)}
                      title="双击编辑"
                    >
                      {task.title}
                    </span>
                  )}

                  {/* 删除按钮 */}
                  <button
                    onClick={() => {
                      if (!sid) return;
                      removeSessionTask(sid, task.id);
                      syncBackendAction(task, 'delete');
                    }}
                    className="opacity-0 group-hover:opacity-100 hover:bg-dark-hover rounded p-0.5 transition-all shrink-0"
                    title="删除任务"
                  >
                    <X size={10} />
                  </button>
                </div>
              );
            })}
          </div>
        )}
      </div>
    );
  }

  // Render tab content
  const renderTabContent = (tabId: TabId) => {
    switch (tabId) {
      case 'chat': {
        const visibleTabs = getVisibleSessionTabs();
        return (
          <div className="flex flex-col flex-1 min-h-0">
            <SessionTabs
              tabs={sessionTabs}
              activeSessionId={activeSessionId}
              onSwitch={handleSwitchSession}
              onClose={handleCloseSession}
              onNew={handleNewSession}
              onRename={handleRenameSession}
            />
            <SessionGrid
              tabs={visibleTabs}
              activeSessionId={activeSessionId}
              onSend={sendMessage}
              onStop={stopGeneration}
              onPause={pauseGeneration}
              onResume={resumeGeneration}
              onSwitch={handleSwitchSession}
              activeTab={activeTab}
              setActiveTab={handleTabChange}
              createNewSession={handleNewSession}
              clearMessages={handleClearMessages}
              setTheme={setTheme}
              toggleTerminal={toggleTerminal}
              setLogs={setLogs}
              setUnreadLogs={setUnreadLogs}
            />
          </div>
        );
      }
      case 'plan':
        return <PlanPanel />;
      case 'terminal':
        return <TerminalView />;
      case 'files':
        return <FileTreeView />;
      case 'models':
        return <ModelsView />;
      case 'tools':
        return <ToolsView />;
      case 'skills':
        return <SkillsView />;
      case 'agents':
        return <AgentsView />;
      case 'settings':
        return <SettingsPanel />;
      case 'logs':
        return <LogsPanel logs={logs} onClear={handleClearLogs} />;
      default:
        return null;
    }
  };

  return (
    <ErrorBoundary>
      <div className="h-screen w-screen flex flex-col bg-dark-bg text-dark-text overflow-hidden">
        {/* Top Navigation Bar */}
        <header className="h-12 bg-dark-surface border-b border-dark-border flex items-center px-4 shrink-0 z-50">
          {/* Mobile menu toggle */}
          <button
            className="md:hidden p-2 mr-2 rounded-lg hover:bg-dark-hover transition-colors"
            onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
          >
            {isMobileMenuOpen ? <X size={20} /> : <Menu size={20} />}
          </button>

          {/* Logo */}
          <div className="flex items-center gap-2 mr-6">
            <div className="w-7 h-7 rounded-lg bg-accent-blue flex items-center justify-center">
              <span className="text-white font-bold text-sm">JW</span>
            </div>
            <span className="font-semibold hidden sm:inline">JwCode</span>
          </div>

          {/* Tab Navigation */}
          <nav className="flex-1 flex items-center gap-1 overflow-x-auto">
            {TABS.map(tab => {
              const Icon = ICON_MAP[tab.icon || 'MessageSquare'] as LucideIcon;
              const isActive = activeTab === tab.id;
              const isLogTab = tab.id === 'logs';
              return (
                <button
                  key={tab.id}
                  onClick={() => {
                    handleTabChange(tab.id);
                    if (isLogTab) setUnreadLogs(0);
                    setIsMobileMenuOpen(false);
                  }}
                  className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm transition-all whitespace-nowrap ${
                    isActive
                      ? 'bg-accent-blue/10 text-accent-blue'
                      : 'text-dark-muted hover:text-dark-text hover:bg-dark-hover'
                  }`}
                >
                  <Icon size={16} />
                  <span className="hidden lg:inline">{tab.title}</span>
                  {isLogTab && unreadLogs > 0 && (
                    <span className="bg-accent-red text-white text-[10px] px-1.5 py-0.5 rounded-full min-w-[18px] text-center">
                      {unreadLogs > 99 ? '99+' : unreadLogs}
                    </span>
                  )}
                </button>
              );
            })}
          </nav>

          {/* Right side: Connection status + New session */}
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1.5 text-xs text-dark-muted">
              <span className={`w-2 h-2 rounded-full ${isConnected ? 'bg-accent-green' : 'bg-accent-red'}`} />
              <span className="hidden sm:inline">{isConnected ? '已连接' : '未连接'}</span>
            </div>
            {activeTab === 'chat' && (
              <button
                onClick={handleNewSession}
                className="px-3 py-1.5 text-sm bg-accent-blue text-white rounded-lg hover:opacity-90 transition-opacity"
              >
                新建会话
              </button>
            )}
          </div>
        </header>

        {/* Mobile Menu Overlay */}
        {isMobileMenuOpen && (
          <div className="md:hidden fixed inset-0 bg-dark-bg/95 z-40 pt-12">
            <div className="p-4 space-y-2">
              {TABS.map(tab => {
                const Icon = ICON_MAP[tab.icon || 'MessageSquare'] as LucideIcon;
                const isActive = activeTab === tab.id;
                return (
                  <button
                    key={tab.id}
                    onClick={() => {
                      handleTabChange(tab.id);
                      setIsMobileMenuOpen(false);
                    }}
                    className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg text-left transition-all ${
                      isActive ? 'bg-accent-blue/10 text-accent-blue' : 'text-dark-text hover:bg-dark-hover'
                    }`}
                  >
                    <Icon size={20} />
                    <span>{tab.title}</span>
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {/* Status line: 实时显示模型/Token/预算/生成状态 */}
        <StatusLine />

        {/* Main Content Area */}
        <div className="flex-1 flex overflow-hidden min-h-0">
          <div className="flex-1 flex flex-col min-w-0 min-h-0">
            <Suspense fallback={<PanelFallback />}>
              {renderTabContent(activeTab)}
            </Suspense>
          </div>

          {/* 右侧面板 - 响应式宽度，小屏隐藏 */}
          <div className="hidden lg:flex flex-col bg-dark-surface border-l border-dark-border overflow-hidden shrink-0 min-h-0 w-80 xl:w-80 2xl:w-96">

            {/* Working Directory Section - 点击可切换 */}
            <div
              className="shrink-0 border-b border-dark-border px-4 py-2.5 cursor-pointer hover:bg-dark-hover transition-colors"
              onClick={handleWorkspaceChange}
              title="点击切换工作目录"
            >
              <div className="flex items-center gap-1.5 mb-1">
                <FolderTree size={14} className="text-accent-blue shrink-0" />
                <span className="text-xs font-semibold text-dark-text">工作目录</span>
                <span className="text-[10px] text-accent-blue ml-auto">切换</span>
              </div>
              <div className="text-[11px] text-dark-muted truncate font-mono pl-5">
                {workspaceDir}
              </div>
            </div>


            {/* Session History Section (collapsible) */}
            <div className="shrink-0 border-b border-dark-border">
              <div
                className="flex items-center justify-between px-4 py-2 cursor-pointer hover:bg-dark-hover transition-colors"
                onClick={() => startTransition(() => {
                  const panel = document.getElementById('session-history-panel');
                  if (panel) {
                    const isOpen = !panel.classList.contains('hidden');
                    panel.classList.toggle('hidden', isOpen);
                  }
                })}
              >
                <h3 className="text-xs font-semibold flex items-center gap-1.5 text-dark-text">
                  <MessageSquare size={14} className="text-accent-blue" />
                  会话历史
                  <span className="text-[10px] font-normal text-dark-muted">({historySessions.length})</span>
                </h3>
                <div className="flex items-center gap-2">
                  {historySessions.length > 0 && (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        clearHistorySessions();
                      }}
                      className="px-2 py-0.5 text-[10px] bg-dark-hover rounded hover:bg-dark-border transition-colors"
                      title="清空历史"
                    >
                      清空
                    </button>
                  )}
                </div>
              </div>
              <div id="session-history-panel" className={historySessions.length > 0 ? '' : 'hidden'}>
                <div className="max-h-[160px] overflow-y-auto custom-scrollbar">
                  <div className="px-3 pb-2 space-y-0.5">
                    {historySessions.map((h) => (
                      <div
                        key={h.id}
                        className="flex items-center gap-1.5 px-2 py-1.5 rounded text-[11px] transition-colors hover:bg-dark-hover/50 group cursor-pointer"
                        onClick={() => {
                          restoreHistorySession(h.id);
                          wsService.setSessionId(h.id);
                        }}
                        title={`点击恢复「${h.title}」`}
                      >
                        <MessageSquare size={12} className="text-dark-muted shrink-0" />
                        <span className="truncate flex-1 text-dark-text">{h.title}</span>
                        <span className="text-[9px] text-dark-muted shrink-0">
                          {new Date(h.createdAt).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}
                        </span>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            removeHistorySession(h.id);
                          }}
                          className="opacity-0 group-hover:opacity-100 hover:bg-dark-hover rounded p-0.5 transition-all shrink-0"
                          title="从历史移除"
                        >
                          <X size={10} />
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
              {historySessions.length === 0 && (
                <div className="px-4 pb-3 text-[11px] text-dark-muted">
                  暂无历史会话
                </div>
              )}
            </div>

            {/* Task List Section (collapsible) - 每个会话维护独立任务列表 */}
            <div className="shrink-0 border-b border-dark-border">
              <div
                className="flex items-center justify-between px-4 py-2 cursor-pointer hover:bg-dark-hover transition-colors"
                onClick={() => startTransition(() => setIsTaskListOpen(!isTaskListOpen))}
              >
                <h3 className="text-xs font-semibold flex items-center gap-1.5 text-dark-text">
                  <ListChecks size={14} className="text-accent-blue" />
                  任务列表
                  {activeSessionId && tasksBySession[activeSessionId] && (
                    <span className="text-[10px] font-normal text-dark-muted">
                      ({tasksBySession[activeSessionId].filter(t => !t.completed).length}/{tasksBySession[activeSessionId].length})
                    </span>
                  )}
                </h3>
                <div className="flex items-center gap-2">
                  {isTaskListOpen ? <ChevronUp size={14} className="text-dark-muted" /> : <ChevronDown size={14} className="text-dark-muted" />}
                </div>
              </div>
              {isTaskListOpen && <TaskListPanel />}
            </div>

            {/* Log Section Header (collapsible) */}
            <div
              className="flex items-center justify-between px-4 py-2 border-b border-dark-border shrink-0 cursor-pointer hover:bg-dark-hover transition-colors"
              onClick={() => startTransition(() => setIsLogDrawerOpen(!isLogDrawerOpen))}
            >
              <h2 className="text-xs font-semibold flex items-center gap-1.5 text-dark-text">
                <ScrollText size={14} className="text-accent-blue" />
                后台日志
                <span className="text-[10px] font-normal text-dark-muted">({logs.length})</span>
              </h2>
              <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
                <button
                  onClick={handleClearLogs}
                  className="px-2 py-0.5 text-[10px] bg-dark-hover rounded hover:bg-dark-border transition-colors"
                >
                  清空
                </button>
                {isLogDrawerOpen ? <ChevronDown size={14} className="text-dark-muted" /> : <ChevronUp size={14} className="text-dark-muted" />}
              </div>
            </div>

            {/* Log Content (collapsible) - 展开时占满剩余空间，折叠时完全隐藏 */}
            {isLogDrawerOpen && (
              <div className="flex-1 flex flex-col overflow-hidden min-h-0">
                <Suspense fallback={<PanelFallback />}>
                  <LogsPanel logs={logs} onClear={handleClearLogs} compact />
                </Suspense>
              </div>
            )}

          </div>
        </div>
      </div>

      {/* Hook 审批弹窗 */}
      <Suspense fallback={null}>
        <HookApprovalModal
          isOpen={hookModalOpen}
          onClose={() => setHookModalOpen(false)}
        />
      </Suspense>
    </ErrorBoundary>
  );
}

export default App;

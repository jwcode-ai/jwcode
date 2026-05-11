import { useState, useEffect, useCallback, lazy, Suspense, startTransition } from 'react';
import { MessageSquare, Terminal, FolderTree, Settings, Brain, Wrench, Target, Users, FileText, ScrollText, LucideIcon, Menu, X, ChevronDown, ChevronUp, ListChecks } from 'lucide-react';
import { useChatStore } from './stores/chatStore';
import { useSessionStore } from './stores/sessionStore';
import { useTerminalStore } from './stores/terminalStore';
import { useSettingsStore } from './stores/settingsStore';
import { usePlanStore } from './stores/planStore';
import wsService from './services/websocket';
import { useWebSocket } from './hooks/useWebSocket';
import { Tab, TabId, LogEntry } from './types';
import { ErrorBoundary } from './components/ErrorBoundary';

// 懒加载非首屏组件
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
  { id: 'logs', title: '日志', icon: 'ScrollText' },
];

const ICON_MAP: Record<string, LucideIcon> = {
  MessageSquare, Terminal, FolderTree, Settings, Brain,
  Wrench, Target, Users, FileText, ScrollText,
};

function App() {
  const [activeTab, setActiveTab] = useState<TabId>('chat');
  const [isConnected, setIsConnected] = useState(false);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [unreadLogs, setUnreadLogs] = useState(0);
  const [isLogDrawerOpen, setIsLogDrawerOpen] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  const { toggleTerminal } = useTerminalStore();
  const { theme, setTheme, workspaceDir, setWorkspaceDir } = useSettingsStore();

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
        id: `error-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        level: 'error' as LogEntry['level'],
        source: '浏览器',
        message: `未捕获错误: ${errorMsg}`,
        timestamp: Date.now(),
      }].slice(-500));
    };

    const handleRejection = (event: PromiseRejectionEvent) => {
      const reason = event.reason?.message || event.reason || '未知原因';
      setLogs(prev => [...prev, {
        id: `rejection-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
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

  const handleNewSession = useCallback(() => {
    startTransition(() => {
      const newId = addSessionTab();
      wsService.setSessionId(newId);
    });
  }, [addSessionTab]);

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

    // 发送新消息时清除暂停状态
    useChatStore.getState().resumeGeneration(sessionId);

    wsService.setSessionId(sessionId);

    useChatStore.getState().addMessage(sessionId, {
      id: `msg-${Date.now()}`,
      type: 'user',
      content: content.trim(),
      timestamp: Date.now(),
    });

    // 根据 Plan/Act 模式决定消息类型
    const currentMode = usePlanStore.getState().mode;
    if (currentMode === 'plan') {
      wsService.send({
        type: 'plan',
        sessionId,
        message: content.trim(),
      });
    } else {
      wsService.send({
        type: 'chat',
        sessionId,
        message: content.trim(),
      });
    }
  }, []);

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
        activeSessionId: null,
      });

      // 4. 创建新会话
      setTimeout(() => {
        useSessionStore.getState().addSessionTab('对话 1');
      }, 0);
    }
  }, [workspaceDir, setWorkspaceDir]);


  const handleClearLogs = () => {
    setLogs([]);
    setUnreadLogs(0);
  };


  // 任务概览组件（右侧面板顶部）- 按当前 session 显示
  function TaskOverview() {
    const plansBySession = usePlanStore((s) => s.plansBySession);
    const planPhasesBySession = usePlanStore((s) => s.planPhasesBySession);
    const sessionId = activeSessionId;

    if (!sessionId) {
      return (
        <div className="px-4 pb-3 text-[11px] text-dark-muted">
          暂无执行中的任务
        </div>
      );
    }

    const currentPlan = plansBySession[sessionId];
    const planPhase = planPhasesBySession[sessionId] || 'idle';

    if (!currentPlan || planPhase === 'idle') {
      return (
        <div className="px-4 pb-3 text-[11px] text-dark-muted">
          暂无执行中的任务
        </div>
      );
    }

    const total = currentPlan.tasks.length;
    const completed = currentPlan.tasks.filter((t) => t.status === 'completed').length;
    const running = currentPlan.tasks.filter((t) => t.status === 'running').length;
    const progress = total > 0 ? Math.round((completed / total) * 100) : 0;

    return (
      <div className="px-4 pb-3">
        <div className="flex items-center justify-between mb-1.5">
          <span className="text-[11px] text-dark-muted truncate max-w-[180px]">
            {currentPlan.goal}
          </span>
          <span className="text-[10px] text-dark-muted shrink-0 ml-2">
            {completed}/{total}
          </span>
        </div>
        <div className="w-full h-1.5 bg-dark-bg rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full transition-all duration-500 ${
              running > 0 ? 'bg-accent-blue' : 'bg-accent-green'
            }`}
            style={{ width: `${progress}%` }}
          />
        </div>
        <div className="flex items-center gap-2 mt-1">
          {running > 0 && (
            <span className="flex items-center gap-1 text-[10px] text-accent-blue">
              <span className="w-1.5 h-1.5 bg-accent-blue rounded-full animate-pulse" />
              执行中 ({running})
            </span>
          )}
          {planPhase === 'result' && (
            <span className="text-[10px] text-accent-green">✅ 已完成</span>
          )}
          {planPhase === 'error' && (
            <span className="text-[10px] text-accent-red">❌ 规划失败</span>
          )}
        </div>
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


            {/* Task Overview Section */}
            <div className="shrink-0 border-b border-dark-border">
              <div className="px-4 py-2.5 flex items-center justify-between">
                <h3 className="text-xs font-semibold flex items-center gap-1.5 text-dark-text">
                  <ListChecks size={14} className="text-accent-blue" />
                  任务概览
                </h3>
                <button
                  onClick={() => handleTabChange('plan')}
                  className="text-[10px] text-accent-blue hover:underline"
                >
                  查看全部 →
                </button>
              </div>
              <TaskOverview />
            </div>


            {/* Log Section Header (collapsible) */}
            <div
              className="flex items-center justify-between px-4 py-2 border-b border-dark-border shrink-0 cursor-pointer hover:bg-dark-hover transition-colors"
              onClick={() => setIsLogDrawerOpen(!isLogDrawerOpen)}
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
                <LogsPanel logs={logs} onClear={handleClearLogs} compact />
              </div>
            )}

          </div>
        </div>
      </div>
    </ErrorBoundary>
  );
}

export default App;

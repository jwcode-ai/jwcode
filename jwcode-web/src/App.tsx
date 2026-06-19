import { useState, useEffect, useCallback, useMemo, useRef, lazy, Suspense, startTransition } from 'react';
import { useTranslation } from 'react-i18next';
import { Activity, MessageSquare, MessageCircle, Terminal, FolderTree, Settings, Brain, Wrench, Target, Users, FileText, ScrollText, LucideIcon, Menu, X, ChevronDown, ChevronUp, ListChecks, Shield } from 'lucide-react';
import { useChatStore } from './stores/chatStore';
import { useSessionStore } from './stores/sessionStore';
import { useTerminalStore } from './stores/terminalStore';
import { useSettingsStore } from './stores/settingsStore';
import { usePlanStore } from './stores/planStore';
import { useHookApprovalStore } from './stores/useHookApprovalStore';
import wsService from './services/websocket';
import { useWebSocket } from './hooks/useWebSocket';
import { apiClient } from './services/api/client';
import { Tab, TabId, LogEntry, SessionTask } from './types';
import { getThemeColors, DEFAULT_PRESET_ID } from './themes/presets';
import { ErrorBoundary } from './components/ErrorBoundary';
import { api } from './services/api';

// Lazy-loaded non-primary components
import { StatusLine } from './components/Chat/StatusLine';
const ModelsView = lazy(() => import('./components/Models/ModelsView').then(m => ({ default: m.ModelsView })));
const ToolsView = lazy(() => import('./components/Tools/ToolsView').then(m => ({ default: m.ToolsView })));
const SkillsView = lazy(() => import('./components/Skills/SkillsView').then(m => ({ default: m.SkillsView })));
const AgentFlowView = lazy(() => import('./components/Agents/AgentFlowView').then(m => ({ default: m.AgentFlowView })));
const AgentsView = lazy(() => import('./components/Agents/AgentsView').then(m => ({ default: m.AgentsView })));
const FileTreeView = lazy(() => import('./components/FileTree/FileTreeView').then(m => ({ default: m.FileTreeView })));
const TerminalView = lazy(() => import('./components/Terminal/TerminalView').then(m => ({ default: m.TerminalView })));
const SessionTabs = lazy(() => import('./components/Chat/SessionTabs').then(m => ({ default: m.SessionTabs })));
const SessionGrid = lazy(() => import('./components/Chat/SessionGrid').then(m => ({ default: m.SessionGrid })));
const SettingsPanel = lazy(() => import('./components/Settings/SettingsPanel').then(m => ({ default: m.SettingsPanel })));
const LogsPanel = lazy(() => import('./components/Logs/LogsPanel').then(m => ({ default: m.LogsPanel })));

const HookApprovalModal = lazy(() => import('./components/Hook/HookApprovalModal').then(m => ({ default: m.HookApprovalModal })));
const HookConfigView = lazy(() => import('./components/Hook/HookConfigView').then(m => ({ default: m.HookConfigView })));
const ChannelConfigView = lazy(() => import('./components/Channels/ChannelConfigView').then(m => ({ default: m.ChannelConfigView })));
import { ToastContainer } from './components/Toast/ToastContainer';
import { ErrorToast } from './components/Toast/ErrorToast';
import { toast } from './stores/toastStore';
import { CommandPalette } from './components/CommandPalette';
import { ShortcutsHelp } from './components/ShortcutsHelp';
import type { UseSlashCommandsOptions } from './hooks/useSlashCommands';

const PanelFallback = () => (
  <div className="flex-1 flex items-center justify-center bg-dark-bg">
    <div className="w-5 h-5 border-2 border-accent-blue border-t-transparent rounded-full animate-spin" />
  </div>
);

// Tab configuration — titles resolved via i18n keys in component
const TAB_KEYS: Record<string, string> = {
  chat: 'nav.chat',
  terminal: 'nav.terminal',
  files: 'nav.files',
  models: 'nav.models',
  tools: 'nav.tools',
  skills: 'nav.skills',
  agents: 'nav.agents',
  hooks: 'nav.hooks',
  settings: 'nav.settings',
  logs: 'nav.logs',
};

const TABS: Tab[] = [
  { id: 'chat', title: '对话', icon: 'MessageSquare' },
  { id: 'terminal', title: '终端', icon: 'Terminal' },
  { id: 'files', title: '文件', icon: 'FolderTree' },
  { id: 'models', title: '模型', icon: 'Brain' },
  { id: 'tools', title: '工具', icon: 'Wrench' },
  { id: 'skills', title: '技能', icon: 'Target' },
  { id: 'agents', title: 'Agents', icon: 'Users' },
  { id: 'hooks', title: 'Hooks', icon: 'Shield' },
  { id: 'channels', title: '频道', icon: 'MessageCircle' },
  { id: 'settings', title: '设置', icon: 'Settings' },
  { id: 'logs', title: '日志', icon: 'ScrollText' },
];

const ICON_MAP: Record<string, LucideIcon> = {
  MessageSquare, MessageCircle, Terminal, FolderTree, Settings, Brain,
  Wrench, Target, Users, FileText, ScrollText, ListChecks, Activity, Shield,
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
  const [isHistoryOpen, setIsHistoryOpen] = useState(true);
  const [isAgentFlowOpen, setIsAgentFlowOpen] = useState(false);
  const [ttydAvailable, setTtydAvailable] = useState<boolean | null>(null);
  const [isPaletteOpen, setIsPaletteOpen] = useState(false);
  const [isShortcutsOpen, setIsShortcutsOpen] = useState(false);

  const { t, i18n } = useTranslation();
  const { toggleTerminal } = useTerminalStore();
  const { theme, setTheme, setThemePreset, language, workspaceDir, setWorkspaceDir } = useSettingsStore();
  const approvalStore = useHookApprovalStore();

  // Sync language from store to i18n
  useEffect(() => {
    if (language && i18n.language !== language) {
      i18n.changeLanguage(language);
    }
  }, [language, i18n]);

  // Filter visible tabs: hide terminal when ttyd unavailable
  const filteredTabs = useMemo(() =>
    TABS.filter(tab => !(tab.id === 'terminal' && ttydAvailable === false)),
  [ttydAvailable]);

  const handleTabChange = useCallback((tabId: TabId) => {
    startTransition(() => {
      setActiveTab(tabId);
    });
  }, []);

  // Global error capture
  useEffect(() => {
    const handleGlobalError = (event: ErrorEvent) => {
      const errorMsg = event.message || t('common.unknownError');
      const source = event.filename || 'global';
      if (source.includes('chrome-extension://') || source.includes('moz-extension://')) {
        return;
      }
      setLogs(prev => [...prev, {
        id: `error-${Date.now()}-${crypto.randomUUID()}`,
        level: 'error' as LogEntry['level'],
        source: 'Browser',
        message: `${t('toast.uncaughtError')}${errorMsg}`,
        timestamp: Date.now(),
      }].slice(-500));
    };

    const handleRejection = (event: PromiseRejectionEvent) => {
      const reason = event.reason?.message || event.reason || t('toast.unknownReason');
      setLogs(prev => [...prev, {
        id: `rejection-${Date.now()}-${crypto.randomUUID()}`,
        level: 'error' as LogEntry['level'],
        source: 'Promise',
        message: `${t('toast.uncaughtRejection')}${reason}`,
        timestamp: Date.now(),
      }].slice(-500));
    };

    window.addEventListener('error', handleGlobalError);
    window.addEventListener('unhandledrejection', handleRejection);

    return () => {
      window.removeEventListener('error', handleGlobalError);
      window.removeEventListener('unhandledrejection', handleRejection);
    };
  }, [t]);

  // Multi-session state
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

  // Theme + preset colors: sync to DOM + inject CSS variables
  const { customTheme, customThemeEnabled, themePresetId } = useSettingsStore();
  useEffect(() => {
    const root = document.documentElement;
    const metaThemeColor = document.querySelector('meta[name="theme-color"]');
    const metaColorScheme = document.querySelector('meta[name="color-scheme"]');

    const hexToRgb = (hex: string) => {
      const r = parseInt(hex.slice(1, 3), 16);
      const g = parseInt(hex.slice(3, 5), 16);
      const b = parseInt(hex.slice(5, 7), 16);
      return `${r} ${g} ${b}`;
    };

    // Compute effective mode (respecting auto → media query)
    let effectiveMode: 'dark' | 'light';
    if (theme === 'dark') {
      effectiveMode = 'dark';
    } else if (theme === 'light') {
      effectiveMode = 'light';
    } else {
      effectiveMode = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }

    // Apply dark/light class
    root.classList.toggle('dark', effectiveMode === 'dark');
    root.classList.toggle('light', effectiveMode === 'light');

    // Get preset colors, merge custom overrides if enabled
    const overrides = customThemeEnabled ? customTheme : undefined;
    const colors = getThemeColors(themePresetId || DEFAULT_PRESET_ID, effectiveMode, overrides);

    // Inject all CSS vars as RGB triplets
    root.style.setProperty('--color-bg', hexToRgb(colors.bg));
    root.style.setProperty('--color-surface', hexToRgb(colors.surface));
    root.style.setProperty('--color-border', hexToRgb(colors.border));
    root.style.setProperty('--color-text', hexToRgb(colors.text));
    root.style.setProperty('--color-muted', hexToRgb(colors.muted));
    root.style.setProperty('--color-accent-blue', hexToRgb(colors.accentBlue));
    root.style.setProperty('--color-accent-green', hexToRgb(colors.accentGreen));
    root.style.setProperty('--color-accent-red', hexToRgb(colors.accentRed));
    root.style.setProperty('--color-accent-yellow', hexToRgb(colors.accentYellow));
    root.style.setProperty('--color-accent-purple', hexToRgb(colors.accentPurple));
    // Compute --color-hover from surface (slightly lighter/darker)
    const hoverRgb = hexToRgb(colors.surface);
    root.style.setProperty('--color-hover', hoverRgb);

    // Update meta tags
    if (metaThemeColor) metaThemeColor.setAttribute('content', effectiveMode === 'dark' ? colors.bg : colors.bg);
    if (metaColorScheme) metaColorScheme.setAttribute('content', effectiveMode);

    // Listen for auto mode media query changes
    if (theme === 'auto') {
      const mq = window.matchMedia('(prefers-color-scheme: dark)');
      const mqListener = () => {
        const mode = mq.matches ? 'dark' : 'light';
        const c = getThemeColors(themePresetId || DEFAULT_PRESET_ID, mode, overrides);
        root.classList.toggle('dark', mode === 'dark');
        root.classList.toggle('light', mode === 'light');
        root.style.setProperty('--color-bg', hexToRgb(c.bg));
        root.style.setProperty('--color-surface', hexToRgb(c.surface));
        root.style.setProperty('--color-border', hexToRgb(c.border));
        root.style.setProperty('--color-text', hexToRgb(c.text));
        root.style.setProperty('--color-muted', hexToRgb(c.muted));
        root.style.setProperty('--color-accent-blue', hexToRgb(c.accentBlue));
        root.style.setProperty('--color-accent-green', hexToRgb(c.accentGreen));
        root.style.setProperty('--color-accent-red', hexToRgb(c.accentRed));
        root.style.setProperty('--color-accent-yellow', hexToRgb(c.accentYellow));
        root.style.setProperty('--color-accent-purple', hexToRgb(c.accentPurple));
        root.style.setProperty('--color-hover', hexToRgb(c.surface));
        if (metaThemeColor) metaThemeColor.setAttribute('content', c.bg);
        if (metaColorScheme) metaColorScheme.setAttribute('content', mode);
      };
      mq.addEventListener('change', mqListener);
      return () => mq.removeEventListener('change', mqListener);
    }
  }, [theme, customTheme, customThemeEnabled, themePresetId]);

  // WebSocket connection
  useWebSocket({ activeTab, setLogs, setUnreadLogs });

  // Check ttyd availability
  useEffect(() => {
    apiClient.get<{ ttydAvailable?: boolean }>('/api/terminal/status')
      .then(res => setTtydAvailable(res.success && res.data ? res.data.ttydAvailable !== false : false))
      .catch(() => setTtydAvailable(false));
  }, []);

  // Listen for switch-tab custom event
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
    const unsubOpen = wsService.onOpen(() => {
      setIsConnected(true);
      toast.success(t('toast.backendConnected'));
    });
    const unsubClose = wsService.onClose(() => {
      if (isConnected) toast.warning(t('toast.backendDisconnected'));
      setIsConnected(false);
    });
    return () => { unsubOpen(); unsubClose(); };
  }, [isConnected, t]);

  // Initialize default session tab
  useEffect(() => {
    if (sessionTabs.length === 0) {
      addSessionTab(t('chat.defaultSessionTitle', { n: 1 }));
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Hook approval modal event listener
  useEffect(() => {
    const handler = () => setHookModalOpen(true);
    window.addEventListener('hook-approval-required', handler);
    return () => window.removeEventListener('hook-approval-required', handler);
  }, []);

  // Handle pending approvals on auto mode change
  useEffect(() => {
    if (approvalStore.autoMode && approvalStore.pendingApprovals.length > 0) {
      setHookModalOpen(false);
    }
    if (approvalStore.pendingApprovals.length > 0 && !approvalStore.autoMode) {
      setHookModalOpen(true);
    }
  }, [approvalStore.autoMode, approvalStore.pendingApprovals.length]);

  // ESC pause/stop: single ESC pauses, double ESC within 500ms stops
  const lastEscRef = useRef(0);
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      if (e.defaultPrevented) return;
      const tag = (e.target as HTMLElement)?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || (e.target as HTMLElement)?.isContentEditable) return;

      const chatStore = useChatStore.getState();
      const sessionStore = useSessionStore.getState();
      const activeSid = sessionStore.activeSessionId;
      if (!activeSid || !chatStore.isGenerating(activeSid)) return;

      e.preventDefault();
      const now = Date.now();
      const prev = lastEscRef.current;
      lastEscRef.current = now;
      if (prev > 0 && (now - prev) < 500) {
        wsService.send({ type: 'stop', sessionId: activeSid });
        toast.info(t('toast.terminated'));
      } else {
        wsService.send({ type: 'pause', sessionId: activeSid });
        toast.info(t('toast.paused'));
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [t]);

  const handleNewSession = useCallback(() => {
    startTransition(() => {
      const newId = addSessionTab(undefined, workspaceDir);
      wsService.setSessionId(newId);
      usePlanStore.getState().setMode('act');
    });
  }, [addSessionTab, workspaceDir]);

  const handleSwitchSession = useCallback((sessionId: string) => {
    setActiveSession(sessionId);
    wsService.setSessionId(sessionId);
  }, [setActiveSession]);

  const handleCloseSession = useCallback((sessionId: string) => {
    closeSessionTab(sessionId);
  }, [closeSessionTab]);

  const handleRenameSession = useCallback((sessionId: string, title: string) => {
    renameSessionTab(sessionId, title);
  }, [renameSessionTab]);

  const handleClearMessages = useCallback(() => {
    if (activeSessionId) clearMessages(activeSessionId);
  }, [activeSessionId, clearMessages]);

  const sendMessage = useCallback(async (sessionId: string, content: string, referencedFiles?: string[]) => {
    if (!content.trim()) return;

    const isGen = useChatStore.getState().isGenerating(sessionId);
    if (isGen) return;

    const planStore = usePlanStore.getState();
    useChatStore.getState().resumeGeneration(sessionId);
    wsService.setSessionId(sessionId);

    const msgs = useChatStore.getState().getMessages(sessionId);
    const hasUserMsg = msgs.some((m) => m.type === 'user');
    if (!hasUserMsg) {
      autoNameSession(sessionId, content.trim());
    }

    // Resolve @-referenced file contents
    let finalContent = content.trim();
    if (referencedFiles && referencedFiles.length > 0) {
      const fileContexts: string[] = [];
      for (const filePath of referencedFiles) {
        try {
          const fileResp = await api.files.read(filePath);
          const fileContent = fileResp.success && fileResp.data ? fileResp.data : null;
          if (fileContent) {
            finalContent = finalContent.replace(filePath, '');
            const ext = filePath.split('.').pop() || '';
            fileContexts.push(
              `<context ref="${filePath}">\n\`\`\`${ext}\n${fileContent}\n\`\`\`\n</context>`
            );
          }
        } catch (e) {
          console.warn(`Failed to read @-referenced file: ${filePath}`, e);
        }
      }
      if (fileContexts.length > 0) {
        finalContent = fileContexts.join('\n\n') + '\n\n' + finalContent.trim();
        finalContent = finalContent.trim();
      }
    }

    useChatStore.getState().addMessage(sessionId, {
      id: `msg-${Date.now()}`,
      type: 'user',
      content: finalContent,
      timestamp: Date.now(),
    });

    const currentMode = planStore.mode;
    if (currentMode === 'plan') {
      wsService.send({ type: 'plan', sessionId, message: finalContent });
    } else {
      wsService.send({ type: 'chat', sessionId, message: finalContent });
    }
  }, [autoNameSession]);

  const stopGeneration = useCallback((sessionId: string) => {
    wsService.send({ type: 'stop', sessionId });
    useChatStore.getState().endGeneration(sessionId);
  }, []);

  const pauseGeneration = useCallback((sessionId: string) => {
    wsService.send({ type: 'pause', sessionId });
    useChatStore.getState().pauseGeneration(sessionId);
  }, []);

  const resumeGeneration = useCallback((sessionId: string) => {
    wsService.send({ type: 'resume', sessionId });
    useChatStore.getState().resumeGeneration(sessionId);
  }, []);

  // Switch workspace directory
  const handleWorkspaceChange = useCallback(() => {
    startTransition(() => {
      const newDir = window.prompt(t('chat.switchDirPrompt'), workspaceDir);
      if (newDir && newDir.trim() && newDir.trim() !== workspaceDir) {
        const trimmed = newDir.trim();
        setWorkspaceDir(trimmed);
        wsService.send({ type: 'workspace', message: trimmed });

        const store = useSessionStore.getState();
        store.sessionTabs.forEach(t => {
          useChatStore.getState().clearMessages(t.id);
        });
        useSessionStore.setState({
          sessionTabs: [],
          sessions: [],
          sessionWorkspaceDirs: {},
          activeSessionId: null,
        });

        setTimeout(() => {
          useSessionStore.getState().addSessionTab(t('chat.defaultSessionTitle', { n: 1 }), trimmed);
        }, 0);
      }
    });
  }, [workspaceDir, setWorkspaceDir, t]);

  const handleClearLogs = () => {
    setLogs([]);
    setUnreadLogs(0);
  };

  // Command palette options (reuses slash-command set at app level)
  const paletteOptions = useMemo<UseSlashCommandsOptions>(() => ({
    setActiveTab: handleTabChange,
    createNewSession: handleNewSession,
    clearMessages: handleClearMessages,
    setTheme,
    setThemePreset,
    toggleTerminal,
    setLogs,
    setUnreadLogs,
  }), [handleTabChange, handleNewSession, handleClearMessages, setTheme, setThemePreset, toggleTerminal]);

  // Global keyboard shortcuts: Ctrl/Cmd + K / L / / ; Esc closes overlays
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const mod = e.ctrlKey || e.metaKey;
      const tag = (e.target as HTMLElement)?.tagName;
      const inEditable = tag === 'INPUT' || tag === 'TEXTAREA' || (e.target as HTMLElement)?.isContentEditable;

      // Esc closes app-level overlays first (palette/help/mobile menu)
      if (e.key === 'Escape') {
        if (isPaletteOpen) { e.preventDefault(); setIsPaletteOpen(false); return; }
        if (isShortcutsOpen) { e.preventDefault(); setIsShortcutsOpen(false); return; }
        if (isMobileMenuOpen) { e.preventDefault(); setIsMobileMenuOpen(false); return; }
        return; // fall through to ChatPanel/ESC pause handler
      }

      if (!mod) return;

      if (e.key === 'k' || e.key === 'K') {
        e.preventDefault();
        setIsShortcutsOpen(false);
        setIsPaletteOpen((v) => !v);
      } else if (e.key === 'l' || e.key === 'L') {
        // Don't hijack terminal/input clear when editing text
        if (inEditable) return;
        e.preventDefault();
        handleClearLogs();
        toast.success(t('slashCommands.logs_clear_result'));
      } else if (e.key === '/') {
        e.preventDefault();
        setIsPaletteOpen(false);
        setIsShortcutsOpen((v) => !v);
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [isPaletteOpen, isShortcutsOpen, isMobileMenuOpen, t]);

  // TaskListPanel - per-session task list with backend sync
  function TaskListPanel() {
    const sid = useSessionStore((s) => s.activeSessionId);
    const tasks = sid ? (useSessionStore((s) => s.tasksBySession[sid]) || []) : [];
    const [newTaskTitle, setNewTaskTitle] = useState('');
    const [editingTaskId, setEditingTaskId] = useState<string | null>(null);
    const [editValue, setEditValue] = useState('');
    const inputRef = useRef<HTMLInputElement>(null);
    const loadedRef = useRef<string | null>(null);

    useEffect(() => {
      if (!sid || loadedRef.current === sid) return;
      loadedRef.current = sid;

      const existing = useSessionStore.getState().tasksBySession[sid] || [];
      if (existing.length === 0) return;

      api.tasks.list().then(res => {
        if (!res.success || !res.data) return;
        const backendTasks: any[] = res.data;
        if (backendTasks.length === 0) return;

        const existingTitles = new Set(existing.map(t => t.title));
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
        console.warn('[TaskListPanel] Failed to load tasks from backend', e);
      });
    }, [sid, setSessionTasks]);

    const handleAdd = useCallback(async () => {
      if (!newTaskTitle.trim() || !sid) return;
      try {
        const result = await api.tasks.create({ title: newTaskTitle.trim(), description: '' });
        if (result.success && result.data) {
          const backendTask = result.data as any;
          addSessionTask(sid, newTaskTitle.trim(), backendTask.id, '');
        } else {
          addSessionTask(sid, newTaskTitle.trim());
        }
      } catch (e) {
        console.warn('[TaskListPanel] Failed to sync task to backend', e);
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

    const syncBackendAction = useCallback(async (task: SessionTask, action: 'toggle' | 'delete') => {
      const backendId = (task as any).backendId;
      if (backendId) {
        if (action === 'toggle') {
          const newStatus = task.completed ? 'PENDING' : 'COMPLETED';
          await api.tasks.updateStatus(backendId, newStatus as any).catch(() => {});
        } else {
          await api.tasks.delete(backendId).catch(() => {});
        }
        return;
      }
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
        console.warn('[TaskListPanel] Failed to sync action to backend', e);
      }
    }, []);

    return (
      <div className="px-3 pb-2">
        <div className="flex items-center gap-1 mb-2">
          <input
            ref={inputRef}
            value={newTaskTitle}
            onChange={(e) => setNewTaskTitle(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={t('chat.addTaskPlaceholder')}
            className="flex-1 bg-dark-bg border border-dark-border rounded px-2 py-1 text-[11px] text-dark-text placeholder-dark-muted outline-none focus:border-accent-blue/50 transition-colors"
          />
          <button
            onClick={handleAdd}
            disabled={!newTaskTitle.trim()}
            className="px-2 py-1 text-[10px] bg-accent-blue text-white rounded hover:opacity-90 transition-opacity disabled:opacity-40 disabled:cursor-not-allowed"
          >
            {t('chat.addTask')}
          </button>
        </div>

        {tasks.length === 0 ? (
          <div className="text-[11px] text-dark-muted text-center py-2">
            {t('chat.noTasks')}
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
                      title={t('chat.doubleClickEdit')}
                    >
                      {task.title}
                    </span>
                  )}

                  <button
                    onClick={() => {
                      if (!sid) return;
                      removeSessionTask(sid, task.id);
                      syncBackendAction(task, 'delete');
                    }}
                    className="opacity-0 group-hover:opacity-100 hover:bg-dark-hover rounded p-0.5 transition-all shrink-0"
                    title={t('chat.deleteTask')}
                    aria-label={t('chat.deleteTask')}
                  >
                    <X size={10} aria-hidden="true" />
                  </button>
                </div>
              );
            })}
          </div>
        )}
      </div>
    );
  }

  // Resolve tab title for display
  const getTabTitle = (tabId: string, fallback: string) =>
    TAB_KEYS[tabId] ? t(TAB_KEYS[tabId]) : fallback;

  // Date formatter using current language
  const formatDate = (ts: number) => {
    const locale = i18n.language === 'en' ? 'en-US' : 'zh-CN';
    return new Date(ts).toLocaleDateString(locale, { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
  };

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
      case 'hooks':
        return <HookConfigView />;
      case 'channels':
        return <ChannelConfigView />;
      case 'settings':
        return <SettingsPanel onNavigate={handleTabChange} />;
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
          <button
            className="md:hidden p-2 mr-2 rounded-lg hover:bg-dark-hover transition-colors"
            onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
            aria-label={isMobileMenuOpen ? t('a11y.closeMenu') : t('a11y.openMenu')}
            aria-expanded={isMobileMenuOpen}
          >
            {isMobileMenuOpen ? <X size={20} aria-hidden="true" /> : <Menu size={20} aria-hidden="true" />}
          </button>

          <div className="flex items-center gap-2 mr-6">
            <img src="/logo.svg" alt="JWCode" className="w-7 h-7 rounded-lg" />
            <span className="font-semibold hidden sm:inline">JWCode</span>
          </div>

          <nav className="flex-1 flex items-center gap-1 overflow-x-auto">
            {filteredTabs.map(tab => {
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
                  <span className="hidden lg:inline">{getTabTitle(tab.id, tab.title)}</span>
                  {isLogTab && unreadLogs > 0 && (
                    <span className="bg-accent-red text-white text-[10px] px-1.5 py-0.5 rounded-full min-w-[18px] text-center">
                      {unreadLogs > 99 ? '99+' : unreadLogs}
                    </span>
                  )}
                </button>
              );
            })}
          </nav>

          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1.5 text-xs text-dark-muted" role="status" aria-label={t('a11y.connectionStatus')}>
              <span className={`w-2 h-2 rounded-full ${isConnected ? 'bg-accent-green' : 'bg-accent-red'}`} aria-hidden="true" />
              <span className="hidden sm:inline">{isConnected ? t('chat.connected') : t('chat.disconnected')}</span>
            </div>
            {activeTab === 'chat' && (
              <button
                onClick={handleNewSession}
                className="px-3 py-1.5 text-sm bg-accent-blue text-white rounded-lg hover:opacity-90 transition-opacity"
              >
                {t('chat.newSession')}
              </button>
            )}
          </div>
        </header>

        {/* Mobile Menu Overlay */}
        {isMobileMenuOpen && (
          <div className="md:hidden fixed inset-0 bg-dark-bg/95 z-40 pt-12">
            <div className="p-4 space-y-2">
              {filteredTabs.map(tab => {
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
                    <span>{getTabTitle(tab.id, tab.title)}</span>
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {/* Main Content Area */}
        <div className="flex-1 flex overflow-hidden min-h-0">
          {/* AgentFlow Left Drawer — only visible on chat tab */}
          {activeTab === 'chat' && (
            <div
              className={`shrink-0 border-r border-dark-border bg-dark-surface transition-all duration-300 ease-in-out overflow-hidden ${
                isAgentFlowOpen ? 'w-80 xl:w-[420px]' : 'w-0 border-r-0'
              }`}
            >
              <div className="h-full" style={{ width: isAgentFlowOpen ? undefined : 0, minWidth: isAgentFlowOpen ? 320 : 0 }}>
                {isAgentFlowOpen && (
                  <Suspense fallback={<PanelFallback />}>
                    <AgentFlowView />
                  </Suspense>
                )}
              </div>
            </div>
          )}

          {/* Toggle button for AgentFlow drawer */}
          {activeTab === 'chat' && (
            <button
              onClick={() => setIsAgentFlowOpen(!isAgentFlowOpen)}
              className={`shrink-0 flex items-center justify-center transition-all duration-300 bg-dark-surface hover:bg-dark-hover border-r border-dark-border ${
                isAgentFlowOpen ? 'w-5' : 'w-7'
              }`}
              title={isAgentFlowOpen ? '折叠 Agent 信息流' : '展开 Agent 信息流'}
              aria-label={t('a11y.toggleAgentFlow')}
              aria-expanded={isAgentFlowOpen}
            >
              <Activity size={14} aria-hidden="true" className={`text-accent-purple transition-transform duration-300 ${isAgentFlowOpen ? 'rotate-180' : ''}`} />
              {!isAgentFlowOpen && (
                <span className="text-[9px] text-dark-muted ml-1 hidden xl:inline">Agent 信息流</span>
              )}
            </button>
          )}

          <div className="flex-1 flex flex-col min-w-0 min-h-0">
            <Suspense fallback={<PanelFallback />}>
              <ErrorBoundary>
                {renderTabContent(activeTab)}
              </ErrorBoundary>
            </Suspense>
          </div>

          {/* Right sidebar panel */}
          <div className="hidden lg:flex flex-col bg-dark-surface border-l border-dark-border overflow-hidden shrink-0 min-h-0 w-80 xl:w-80 2xl:w-96">

            {/* Working Directory Section */}
            <div
              className="shrink-0 border-b border-dark-border px-4 py-2.5 cursor-pointer hover:bg-dark-hover transition-colors"
              onClick={handleWorkspaceChange}
              title={t('chat.clickToSwitchDir')}
            >
              <div className="flex items-center gap-1.5 mb-1">
                <FolderTree size={14} className="text-accent-blue shrink-0" />
                <span className="text-xs font-semibold text-dark-text">{t('chat.workingDir')}</span>
                <span className="text-[10px] text-accent-blue ml-auto">{t('chat.switchDir')}</span>
              </div>
              <div className="text-[11px] text-dark-muted truncate font-mono pl-5">
                {workspaceDir}
              </div>
            </div>

            {/* Session History Section (collapsible) */}
            <div className="shrink-0 border-b border-dark-border">
              <div
                className="flex items-center justify-between px-4 py-2 cursor-pointer hover:bg-dark-hover transition-colors"
                onClick={() => setIsHistoryOpen(!isHistoryOpen)}
              >
                <h3 className="text-xs font-semibold flex items-center gap-1.5 text-dark-text">
                  <MessageSquare size={14} className="text-accent-blue" />
                  {t('chat.sessionHistory')}
                  <span className="text-[10px] font-normal text-dark-muted">({historySessions.length})</span>
                </h3>
                <div className="flex items-center gap-2">
                  {isHistoryOpen ? <ChevronUp size={14} className="text-dark-muted" /> : <ChevronDown size={14} className="text-dark-muted" />}
                  {historySessions.length > 0 && (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        clearHistorySessions();
                      }}
                      className="px-2 py-0.5 text-[10px] bg-dark-hover rounded hover:bg-dark-border transition-colors"
                      title={t('chat.clearHistoryTitle')}
                    >
                      {t('common.clear')}
                    </button>
                  )}
                </div>
              </div>
              {isHistoryOpen && historySessions.length > 0 && (
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
                        title={t('chat.restoreSession', { title: h.title })}
                      >
                        <MessageSquare size={12} className="text-dark-muted shrink-0" />
                        <span className="truncate flex-1 text-dark-text">{h.title}</span>
                        <span className="text-[9px] text-dark-muted shrink-0">
                          {formatDate(h.createdAt)}
                        </span>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            removeHistorySession(h.id);
                          }}
                          className="opacity-0 group-hover:opacity-100 hover:bg-dark-hover rounded p-0.5 transition-all shrink-0"
                          title={t('chat.removeFromHistory')}
                          aria-label={t('chat.removeFromHistory')}
                        >
                          <X size={10} aria-hidden="true" />
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              )}
              {isHistoryOpen && historySessions.length === 0 && (
                <div className="px-4 pb-3 text-[11px] text-dark-muted">
                  {t('chat.noHistory')}
                </div>
              )}
            </div>

            {/* Task List Section (collapsible) */}
            <div className="shrink-0 border-b border-dark-border">
              <div
                className="flex items-center justify-between px-4 py-2 cursor-pointer hover:bg-dark-hover transition-colors"
                onClick={() => startTransition(() => setIsTaskListOpen(!isTaskListOpen))}
              >
                <h3 className="text-xs font-semibold flex items-center gap-1.5 text-dark-text">
                  <ListChecks size={14} className="text-accent-blue" />
                  {t('chat.taskList')}
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
                {t('chat.backgroundLogs')}
                <span className="text-[10px] font-normal text-dark-muted">({logs.length})</span>
              </h2>
              <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
                <button
                  onClick={handleClearLogs}
                  className="px-2 py-0.5 text-[10px] bg-dark-hover rounded hover:bg-dark-border transition-colors"
                >
                  {t('common.clear')}
                </button>
                {isLogDrawerOpen ? <ChevronDown size={14} className="text-dark-muted" /> : <ChevronUp size={14} className="text-dark-muted" />}
              </div>
            </div>

            {/* Log Content (collapsible) */}
            {isLogDrawerOpen && (
              <div className="flex-1 flex flex-col overflow-hidden min-h-0">
                <Suspense fallback={<PanelFallback />}>
                  <LogsPanel logs={logs} onClear={handleClearLogs} compact />
                </Suspense>
              </div>
            )}

          </div>
        </div>

        <StatusLine activeTab={activeTab} />
      </div>

      <Suspense fallback={null}>
        <HookApprovalModal
          isOpen={hookModalOpen}
          onCloseModal={() => setHookModalOpen(false)}
        />
      </Suspense>

      <CommandPalette
        open={isPaletteOpen}
        onClose={() => setIsPaletteOpen(false)}
        options={paletteOptions}
      />
      <ShortcutsHelp open={isShortcutsOpen} onClose={() => setIsShortcutsOpen(false)} />

      <ToastContainer />
      <ErrorToast />
    </ErrorBoundary>
  );
}

export default App;

import { useState, useEffect, useCallback, useRef } from 'react';
import { Plus, Minus } from 'lucide-react';
import { MessageSquare, Terminal, FolderTree, Settings, Brain, Wrench, Target, Users, FileText, ScrollText, LucideIcon, Menu, X, Send } from 'lucide-react';
import { Panel, PanelGroup, PanelResizeHandle } from 'react-resizable-panels';
import { useChatStore } from './stores/chatStore';
import { useSessionStore } from './stores/sessionStore';
import { useTerminalStore } from './stores/terminalStore';
import { useSettingsStore } from './stores/settingsStore';
import wsService from './services/websocket';
import { Message, TabId, Tab, Step, ToolCall, LogEntry } from './types';
import { ModelsView } from './components/Models/ModelsView';
import { ToolsView } from './components/Tools/ToolsView';
import { SkillsView } from './components/Skills/SkillsView';
import { AgentsView } from './components/Agents/AgentsView';
import { TasksView } from './components/Tasks/TasksView';
import { FileTreeView } from './components/FileTree/FileTreeView';
import { TerminalView } from './components/Terminal/TerminalView';
import { MarkdownRenderer } from './components/common/MarkdownRenderer';
import { SlashCommandMenu } from './components/SlashCommandMenu';
import { useSlashCommands, parseSlashCommand, SlashCommand } from './hooks/useSlashCommands';

// Tab configuration
const TABS: Tab[] = [
  { id: 'chat', title: '对话', icon: 'MessageSquare' },
  { id: 'terminal', title: '终端', icon: 'Terminal' },
  { id: 'files', title: '文件', icon: 'FolderTree' },
  { id: 'models', title: '模型', icon: 'Brain' },
  { id: 'tools', title: '工具', icon: 'Wrench' },
  { id: 'skills', title: '技能', icon: 'Target' },
  { id: 'agents', title: 'Agents', icon: 'Users' },
  { id: 'tasks', title: '任务', icon: 'FileText' },
  { id: 'settings', title: '设置', icon: 'Settings' },
  { id: 'logs', title: '日志', icon: 'ScrollText' },
];

const ICON_MAP: Record<string, LucideIcon> = {
  MessageSquare,
  Terminal,
  FolderTree,
  Settings,
  Brain,
  Wrench,
  Target,
  Users,
  FileText,
  ScrollText,
};

function App() {
  const [activeTab, setActiveTab] = useState<TabId>('chat');
  const [isConnected, setIsConnected] = useState(false);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [unreadLogs, setUnreadLogs] = useState(0);
  const [isLogDrawerOpen, setIsLogDrawerOpen] = useState(false);
  const [input, setInput] = useState('');
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const createNewSessionRef = useRef<(() => void) | null>(null);
  
  const messagesEndRef = useRef<HTMLDivElement>(null);
  
  const {
    messages, 
    isGenerating, 
    addMessage, 
    appendToLastMessage, 
    setThinking, 
    appendToLastMessageThinking,
    addStep, 
    updateStep,
    addToolCall,
    updateToolCall,
    appendToLastToolCallArgs,
    startGeneration,
    endGeneration,
    clearMessages
  } = useChatStore();
  
  const { addSession, sessions, setActiveSession, activeSessionId } = useSessionStore();
  const { isOpen: isTerminalOpen, toggleTerminal } = useTerminalStore();
  const { theme, setTheme } = useSettingsStore();

  // Log level configurations (used by drawer)
  const levelColors = {
    info: 'bg-accent-blue',
    warn: 'bg-accent-yellow',
    error: 'bg-accent-red',
    success: 'bg-accent-green',
    tool: 'bg-accent-purple',
  };
  
  const levelIcons = {
    info: 'ℹ️',
    warn: '⚠️',
    error: '❌',
    success: '✅',
    tool: '🔧',
  };

  // Auto-scroll to bottom when new messages arrive
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isGenerating]);

  // WebSocket connection
  useEffect(() => {
    wsService.connect();
    
    const unsubOpen = wsService.onOpen(() => setIsConnected(true));
    const unsubClose = wsService.onClose(() => setIsConnected(false));
    const unsubMessage = wsService.onMessage(handleWSMessage);
    
    return () => {
      unsubOpen();
      unsubClose();
      unsubMessage();
    };
  }, []);

  // 辅助函数：从 store 获取最新的 step
  const getLatestStep = useCallback(() => {
    const state = useChatStore.getState();
    const messages = state.messages;
    const lastMessage = messages[messages.length - 1];
    return lastMessage?.steps?.[lastMessage.steps.length - 1];
  }, []);

  // 辅助函数：确保有可写的 step 存在。如果最后一步已完成且当前不是 complete 事件，则创建新 step
  const ensureStep = useCallback((type: string, stepData: any) => {
    const lastStep = getLatestStep();
    const isCompleteEvent = type === 'step_complete';
    const stepFinished = lastStep && (lastStep.status === 'success' || lastStep.status === 'error');
    
    if (!lastStep || (stepFinished && !isCompleteEvent)) {
      if (stepFinished) {
        console.log(`[${type}] Last step already finished (${lastStep!.status}), creating new step`);
      } else {
        console.warn(`[${type}] No steps found, creating new step`);
      }
      const stepTitle = stepData?.step || type.replace('step_', '').replace('_', ' ');
      addStep({
        id: `step-${Date.now()}`,
        title: stepTitle,
        description: stepData?.description || '执行中...',
        status: 'running',
        timestamp: Date.now(),
      });
      // 返回新创建的 step
      return getLatestStep();
    }
    return lastStep;
  }, [getLatestStep, addStep]);

  // Handle WebSocket messages
  const handleWSMessage = useCallback((msg: { type: string; data?: string }) => {
    console.log('[WS] Received message:', msg.type, msg.data);
    
    // 支持原始消息格式："step_thinking {...}" 而不是 { type: "step_thinking", data: "..." }
    const rawType = msg.type;
    const rawData = msg.data;
    
    // 处理 step_* 类型的消息（支持原始格式）
    if (rawType.startsWith('step_')) {
      try {
        // 尝试解析原始 JSON 数据
        const stepData = JSON.parse(rawData || '{}');
        
        // 使用辅助函数获取最新 step（从 store 直接获取，避免闭包陷阱）
        const lastStep = ensureStep(rawType, stepData);
        
        if (lastStep) {
          switch (rawType) {
            case 'step_start':
              updateStep(lastStep.id, {
                title: stepData.step || lastStep.title,
                description: stepData.description || lastStep.description,
                status: stepData.status === 'start' ? 'running' : (stepData.status || 'running')
              });
              break;
            case 'step_thinking':
              updateStep(lastStep.id, { thought: stepData.thought });
              break;
            case 'step_action':
              updateStep(lastStep.id, { action: stepData.action });
              break;
            case 'step_complete':
              updateStep(lastStep.id, { 
                status: 'success', 
                result: stepData.result 
              });
              break;
          }
        }
      } catch (e) {
        console.error(`Failed to parse ${rawType}:`, e);
      }
      return;
    }
    
    // 标准消息类型处理
    switch (msg.type) {
      case 'start':
        startGeneration();
        addMessage({
          id: `msg-${Date.now()}`,
          type: 'assistant',
          content: '',
          timestamp: Date.now(),
        });
        break;
        
      case 'content':
        appendToLastMessage(msg.data || '');
        break;
        
      case 'thinking':
        appendToLastMessageThinking(msg.data || '');
        break;
        
      case 'tool_call':
        try {
          const toolData = JSON.parse(msg.data || '{}');
          const toolId = toolData.id || `tool-${Date.now()}`;
          const toolIndex = typeof toolData.index === 'number' ? toolData.index : undefined;

          // 检查是否已存在相同 index 或 id 的 toolCall（在 steps 和 message.toolCalls 中都查找）
          const state = useChatStore.getState();
          const lastMsg = state.messages[state.messages.length - 1];
          const allToolCalls = [
            ...(lastMsg?.steps?.flatMap(s => s.toolCalls || []) || []),
            ...(lastMsg?.toolCalls || [])
          ];
          const existing = allToolCalls.find((tc) =>
            (toolIndex !== undefined && tc.index === toolIndex) || tc.id === toolId
          );

          if (existing) {
            // 后端发送的 args 已经是累积值，直接替换而非追加
            updateToolCall(toolId, { args: toolData.args || '' });
          } else {
            addToolCall({
              id: toolId,
              index: toolIndex,
              name: toolData.name || 'Unknown',
              args: toolData.args || '',
              status: 'running',
              timestamp: Date.now(),
            });
          }
        } catch (e) {
          console.error('Failed to parse tool call:', e);
        }
        break;
        
      case 'tool_result':
        try {
          const toolData = JSON.parse(msg.data || '{}');
          // 使用 ensureStep 确保 step 存在
          const lastStep = ensureStep('tool_result', { step: '工具结果', description: toolData.toolName || '工具执行' });
          if (lastStep) {
            updateStep(lastStep.id, {
              status: 'success',
              result: toolData.result || '执行完成'
            });
          }
          // 更新 toolCall 状态：从 store 获取最新状态，按 toolName 匹配
          const tcState = useChatStore.getState();
          const tcLastMsg = tcState.messages[tcState.messages.length - 1];
          const allToolCalls = [
            ...(tcLastMsg?.steps?.flatMap(s => s.toolCalls || []) || []),
            ...(tcLastMsg?.toolCalls || [])
          ];
          const matchedToolCall = allToolCalls.find(tc => tc.name === toolData.toolName);
          if (matchedToolCall) {
            updateToolCall(matchedToolCall.id, {
              status: 'completed',
              result: toolData.result
            });
          }
        } catch (e) {
          console.error('Failed to parse tool result:', e);
        }
        break;
        
      case 'complete':
        endGeneration();
        break;
        
      case 'error':
        endGeneration(msg.data || 'Unknown error');
        addMessage({
          id: `msg-${Date.now()}`,
          type: 'assistant',
          content: `❌ 错误: ${msg.data}`,
          timestamp: Date.now(),
        });
        break;
        
      case 'auth_required':
        // Token required - always send auth when requested (even if previously authenticated)
        // This handles reconnection cases where auth state needs to be refreshed
        let savedToken = localStorage.getItem('auth_token');
        if (!savedToken) {
          // Auto-set default token for development convenience
          savedToken = 'default-token';
          localStorage.setItem('auth_token', savedToken);
          console.log('[WS] Auto-set default auth_token');
        }
        // Always send authentication when server requests it
        console.log('[WS] Sending authentication...');
        wsService.setToken(savedToken);
        wsService.setAuthenticated(false); // Reset auth state before sending
        wsService.send({ type: 'auth', token: savedToken });
        break;

      case 'auth_success':
        console.log('[WS] Authentication successful');
        wsService.setAuthenticated(true);
        break;

      case 'auth_failed':
        console.error('[WS] Authentication failed:', msg.data);
        break;

      case 'log':
        try {
          const logData = typeof msg.data === 'string' ? JSON.parse(msg.data) : msg.data;
          const newLog: LogEntry = {
            id: `log-${Date.now()}`,
            level: logData.level || 'info',
            source: logData.source || 'System',
            message: logData.message || '',
            timestamp: logData.timestamp || Date.now(),
          };
          setLogs(prev => [...prev, newLog].slice(-500));
          if (activeTab !== 'logs') {
            setUnreadLogs(prev => prev + 1);
          }
        } catch (e) {
          console.error('Failed to parse log:', e);
        }
        break;
        
      case 'ping':
        // 收到心跳 ping 时自动回复 pong
        console.log('[WS] Received ping, sending pong...');
        wsService.send({ type: 'pong', data: Date.now().toString() });
        break;
    }
  }, [activeTab, startGeneration, addMessage, appendToLastMessage, appendToLastMessageThinking, setThinking, addStep, updateStep, addToolCall, appendToLastToolCallArgs, updateToolCall, endGeneration]);

  // Create new session
  const createNewSession = useCallback(() => {
    const now = new Date().toISOString();
    const newSession = {
      id: `session-${Date.now()}`,
      title: '新会话',
      createdAt: now,
      updatedAt: now,
      messageCount: 0,
    };
    addSession(newSession);
    clearMessages();
  }, [addSession, clearMessages]);
  
  createNewSessionRef.current = createNewSession;
  
  // Slash commands setup
  const slashCommands = useSlashCommands({
    activeTab,
    setActiveTab,
    createNewSession: () => createNewSessionRef.current?.(),
    clearMessages,
    setTheme,
    toggleTerminal,
    setLogs,
    setUnreadLogs,
  });

  // Execute slash command and show feedback
  const executeCommand = useCallback((commandName: string, args: string) => {
    const command = slashCommands.commands.find((cmd) => cmd.name === commandName);
    if (!command) {
      addMessage({
        id: `msg-${Date.now()}`,
        type: 'assistant',
        content: `❓ 未知命令: /${commandName}\n\n输入 **/help** 查看所有可用命令。`,
        timestamp: Date.now(),
      });
      return;
    }

    if (commandName === 'help') {
      const helpText = slashCommands.commands
        .map((cmd) => `**/${cmd.name}** ${cmd.args || ''} — ${cmd.description}`)
        .join('\n');
      addMessage({
        id: `msg-${Date.now()}`,
        type: 'assistant',
        content: `📋 **快捷命令列表**\n\n${helpText}\n\n在输入框中输入 "/" 即可唤起命令菜单。`,
        timestamp: Date.now(),
      });
      return;
    }

    const result = command.action(args);
    if (result.message) {
      addMessage({
        id: `msg-${Date.now()}`,
        type: 'assistant',
        content: result.success ? `✅ ${result.message}` : `⚠️ ${result.message}`,
        timestamp: Date.now(),
      });
    }
  }, [slashCommands.commands, addMessage]);

  // Send message
  const sendMessage = useCallback((content: string) => {
    if (!content.trim() || isGenerating) return;
    
    // Handle slash commands
    const parsed = parseSlashCommand(content);
    if (parsed) {
      executeCommand(parsed.command, parsed.args);
      return;
    }
    
    addMessage({
      id: `msg-${Date.now()}`,
      type: 'user',
      content: content.trim(),
      timestamp: Date.now(),
    });
    
    // 设置 sessionId 到 wsService，用于重连时恢复连接映射
    const sessionId = activeSessionId || `session-${Date.now()}`;
    wsService.setSessionId(sessionId);
    
    wsService.send({
      type: 'chat',
      sessionId: sessionId,
      message: content,
    });
  }, [isGenerating, addMessage, activeSessionId, executeCommand]);

  // Icon component renderer
  const renderIcon = (iconName: string) => {
    const Icon = ICON_MAP[iconName];
    return Icon ? <Icon size={18} /> : null;
  };

  // Handle keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Ctrl/Cmd + Enter to send message
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter' && input.trim()) {
        e.preventDefault();
        sendMessage(input);
        setInput('');
      }
    };
    
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [input, sendMessage]);

  // Main render content based on active tab
  const renderMainContent = () => {
    switch (activeTab) {
      case 'chat':
        return <ChatPanel messages={messages} isGenerating={isGenerating} onSend={sendMessage} input={input} setInput={setInput} messagesEndRef={messagesEndRef} slashCommands={slashCommands} />;
      case 'logs':
        return <LogsPanel logs={logs} onClear={() => setLogs([])} />;
      case 'settings':
        return <SettingsPanel />;
      case 'terminal':
        return (
          <div className="h-full flex flex-col">
            <div className="p-3 border-b border-dark-border bg-dark-surface flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Terminal size={16} className="text-accent-blue" />
                <span className="font-medium text-sm">终端</span>
              </div>
              <button
                onClick={toggleTerminal}
                className="text-xs px-2 py-1 rounded hover:bg-dark-hover transition-colors"
              >
                关闭
              </button>
            </div>
            <div className="flex-1 min-h-0">
              <TerminalView />
            </div>
          </div>
        );
      default:
        return renderTabContent(activeTab);
    }
  };

  return (
    <div className="h-full flex flex-col bg-dark-bg">
      {/* Header */}
      <header className="h-12 bg-dark-surface border-b border-dark-border flex items-center justify-between px-4 shrink-0">
        <div className="flex items-center gap-3">
          <button
            onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
            className="md:hidden p-1.5 rounded hover:bg-dark-hover transition-colors"
          >
            {isMobileMenuOpen ? <X size={18} /> : <Menu size={18} />}
          </button>
          <div className="flex items-center gap-2">
            <span className="text-accent-blue font-semibold text-lg">JwCode</span>
            <span className="text-dark-muted text-xs hidden sm:inline">v1.0.0</span>
          </div>
        </div>
        
        <div className="flex items-center gap-2">
          <div className={`hidden sm:flex items-center gap-1.5 px-2 py-1 rounded-full text-xs ${
            isConnected ? 'bg-accent-green/10 text-accent-green' : 'bg-accent-red/10 text-accent-red'
          }`}>
            <span className={`w-2 h-2 rounded-full ${isConnected ? 'bg-accent-green' : 'bg-accent-red'}`} />
            <span>{isConnected ? '已连接' : '已断开'}</span>
          </div>
          
          <button
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
            className="p-1.5 rounded hover:bg-dark-hover transition-colors"
            title={theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'}
          >
            {theme === 'dark' ? '🌙' : '☀️'}
          </button>
          
          <button
            onClick={toggleTerminal}
            className={`p-1.5 rounded hover:bg-dark-hover transition-colors ${isTerminalOpen ? 'bg-dark-hover text-accent-blue' : ''}`}
            title="终端"
          >
            <Terminal size={18} />
          </button>
          
          <button
            onClick={() => { setIsLogDrawerOpen(true); setUnreadLogs(0); }}
            className="relative p-1.5 rounded hover:bg-dark-hover transition-colors"
            title="日志"
          >
            <ScrollText size={18} />
            {unreadLogs > 0 && (
              <span className="absolute -top-1 -right-1 w-4 h-4 bg-accent-red text-white text-[10px] rounded-full flex items-center justify-center font-medium">
                {unreadLogs > 9 ? '9+' : unreadLogs}
              </span>
            )}
          </button>
        </div>
      </header>

      {/* Main Content */}
      <div className="flex-1 flex overflow-hidden">
        {/* Sidebar - Desktop */}
        <aside className="hidden md:flex w-56 bg-dark-surface border-r border-dark-border flex-col shrink-0">
            {/* New Chat Button */}
            <div className="p-3 border-b border-dark-border">
              <button
                onClick={createNewSession}
                className="w-full py-2 px-3 bg-accent-green text-white rounded-md flex items-center justify-center gap-2 hover:opacity-90 transition-opacity"
              >
                <span>+</span>
                <span>新会话</span>
              </button>
            </div>
            
            {/* Session List */}
            <div className="flex-1 overflow-y-auto p-2 max-h-48">
              {sessions.length === 0 ? (
                <div className="text-center text-dark-muted text-xs py-3">
                  暂无会话
                </div>
              ) : (
                <div className="space-y-1">
                  {sessions.slice(0, 5).map(session => (
                    <button
                      key={session.id}
                      onClick={() => setActiveSession(session.id)}
                      className={`w-full text-left px-3 py-2 rounded-md text-sm transition-colors truncate ${
                        activeSessionId === session.id
                          ? 'bg-accent-blue text-white'
                          : 'hover:bg-dark-hover text-dark-text'
                      }`}
                    >
                      {session.title}
                    </button>
                  ))}
                </div>
              )}
            </div>
            
            {/* Tab Navigation */}
            <div className="p-2 border-t border-dark-border">
              <div className="grid grid-cols-3 gap-1">
                {TABS.slice(0, 6).map(tab => (
                  <button
                    key={tab.id}
                    onClick={() => setActiveTab(tab.id)}
                    className={`p-2 rounded flex flex-col items-center gap-1 transition-colors ${
                      activeTab === tab.id
                        ? 'bg-accent-blue text-white'
                        : 'text-dark-muted hover:bg-dark-hover hover:text-dark-text'
                    }`}
                    title={tab.title}
                  >
                    {renderIcon(tab.icon || '')}
                    <span className="text-[10px]">{tab.title}</span>
                  </button>
                ))}
              </div>
            </div>
          </aside>
        )

        {/* Mobile Menu Overlay */}
        {isMobileMenuOpen && (
          <div className="fixed inset-0 z-50 md:hidden">
            <div className="absolute inset-0 bg-black/50" onClick={() => setIsMobileMenuOpen(false)} />
            <aside className="absolute left-0 top-12 bottom-0 w-64 bg-dark-surface border-r border-dark-border flex flex-col">
              <div className="p-3 border-b border-dark-border">
                <button
                  onClick={() => { createNewSession(); setIsMobileMenuOpen(false); }}
                  className="w-full py-2 px-3 bg-accent-green text-white rounded-md flex items-center justify-center gap-2"
                >
                  <span>+</span>
                  <span>新会话</span>
                </button>
              </div>
              <div className="flex-1 overflow-y-auto p-2">
                <div className="space-y-1">
                  {TABS.map(tab => (
                    <button
                      key={tab.id}
                      onClick={() => { setActiveTab(tab.id); setIsMobileMenuOpen(false); }}
                      className={`w-full flex items-center gap-3 px-3 py-2 rounded-md text-sm transition-colors ${
                        activeTab === tab.id
                          ? 'bg-accent-blue text-white'
                          : 'hover:bg-dark-hover text-dark-text'
                      }`}
                    >
                      {renderIcon(tab.icon || '')}
                      <span>{tab.title}</span>
                    </button>
                  ))}
                </div>
              </div>
            </aside>
          </div>
        )}

        {/* Main Panel */}
        <main className="flex-1 flex flex-col overflow-hidden">
          {/* Show terminal at bottom if open */}
          {isTerminalOpen && activeTab === 'chat' ? (
            <PanelGroup direction="vertical">
              <Panel className="min-h-0">
                {renderMainContent()}
              </Panel>
              <PanelResizeHandle className="h-1 bg-dark-border hover:bg-accent-blue transition-colors cursor-row-resize" />
              <Panel defaultSize={30} minSize={15} maxSize={50} className="min-h-[150px]">
                <TerminalView />
              </Panel>
            </PanelGroup>
          ) : (
            renderMainContent()
          )}
        </main>

        {/* Log Drawer - Slides from right */}
        {isLogDrawerOpen && (
          <>
            {/* Backdrop */}
            <div 
              className="fixed inset-0 bg-black/50 z-40 transition-opacity"
              onClick={() => setIsLogDrawerOpen(false)}
            />
            {/* Drawer Panel */}
            <div className="fixed right-0 top-12 bottom-0 w-full max-w-md bg-dark-surface border-l border-dark-border z-50 flex flex-col animate-slide-in-right">
              {/* Drawer Header */}
              <div className="flex items-center justify-between p-4 border-b border-dark-border shrink-0">
                <h2 className="text-lg font-semibold flex items-center gap-2">
                  <ScrollText size={18} className="text-accent-blue" />
                  后台日志
                  <span className="text-sm font-normal text-dark-muted">({logs.length})</span>
                </h2>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setLogs([])}
                    className="px-3 py-1.5 text-sm bg-dark-bg border border-dark-border rounded hover:bg-dark-hover transition-colors"
                  >
                    清空
                  </button>
                  <button
                    onClick={() => setIsLogDrawerOpen(false)}
                    className="p-1.5 rounded hover:bg-dark-hover transition-colors"
                  >
                    <X size={18} />
                  </button>
                </div>
              </div>
              
              {/* Drawer Content */}
              <div className="flex-1 overflow-y-auto p-4 font-mono text-sm space-y-1">
                {logs.length === 0 ? (
                  <div className="text-center text-dark-muted py-12">
                    <ScrollText size={48} className="mx-auto mb-2 opacity-50" />
                    <p>暂无日志</p>
                  </div>
                ) : (
                  logs.map(log => (
                    <div
                      key={log.id}
                      className={`flex gap-2 p-2 rounded hover:bg-dark-bg transition-colors ${
                        log.level === 'error' ? 'bg-accent-red/10' :
                        log.level === 'warn' ? 'bg-accent-yellow/10' :
                        log.level === 'success' ? 'bg-accent-green/10' : ''
                      }`}
                    >
                      <span className="text-dark-muted shrink-0 text-xs">
                        {new Date(log.timestamp).toLocaleTimeString('zh-CN', {
                          hour: '2-digit',
                          minute: '2-digit',
                          second: '2-digit',
                        })}
                      </span>
                      <span className="text-lg shrink-0">{levelIcons[log.level]}</span>
                      <span className={`px-1.5 py-0.5 rounded text-white text-xs shrink-0 ${levelColors[log.level]}`}>
                        {log.level.toUpperCase()}
                      </span>
                      <span className="text-dark-muted shrink-0 text-xs">[{log.source}]</span>
                      <span className="text-dark-text break-all">{log.message}</span>
                    </div>
                  ))
                )}
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

// Chat Panel Component
interface ChatPanelProps {
  messages: Message[];
  isGenerating: boolean;
  onSend: (content: string) => void;
  input: string;
  setInput: (input: string) => void;
  messagesEndRef: React.MutableRefObject<HTMLDivElement | null>;
  slashCommands: ReturnType<typeof useSlashCommands>;
}

function ChatPanel({ messages, isGenerating, onSend, input, setInput, messagesEndRef, slashCommands }: ChatPanelProps) {
  const {
    isOpen,
    setFilter,
    selectedIndex,
    setSelectedIndex,
    filteredCommands,
    closeMenu,
    selectNext,
    selectPrev,
    executeCommand,
    containerRef,
  } = slashCommands;

  const handleSend = () => {
    if (input.trim()) {
      onSend(input);
      setInput('');
    }
  };
  
  const handleKeyDown = (e: React.KeyboardEvent) => {
    // Handle slash command menu navigation
    if (isOpen) {
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        selectNext();
        return;
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault();
        selectPrev();
        return;
      }
      if (e.key === 'Enter') {
        e.preventDefault();
        const cmd = filteredCommands[selectedIndex];
        if (cmd) {
          const args = input.slice(input.indexOf(cmd.name) + cmd.name.length + 1).trim();
          const result = executeCommand(cmd, args);
          if (result.success) {
            setInput('');
          } else {
            // Keep input for error cases so user can fix it
            setInput('');
          }
        }
        return;
      }
      if (e.key === 'Escape') {
        e.preventDefault();
        closeMenu();
        return;
      }
      return;
    }

    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const value = e.target.value;
    setInput(value);

    // Detect slash command trigger
    if (value === '/') {
      slashCommands.openMenu();
      return;
    }

    if (value.startsWith('/')) {
      const query = value.slice(1);
      const spaceIndex = query.indexOf(' ');
      const commandPart = spaceIndex >= 0 ? query.slice(0, spaceIndex) : query;
      setFilter(commandPart);
      if (!isOpen) {
        slashCommands.openMenu();
      }
    } else if (isOpen) {
      closeMenu();
    }
  };

  const handleSelectCommand = (cmd: SlashCommand) => {
    const args = input.slice(input.indexOf(cmd.name) + cmd.name.length + 1).trim();
    const result = executeCommand(cmd, args);
    if (result.success) {
      setInput('');
    } else {
      setInput('');
    }
  };
  
  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-4 space-y-2">
        {messages.length === 0 ? (
          <div className="flex items-center justify-center h-full">
            <div className="text-center max-w-md px-4">
              <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-accent-blue/10 flex items-center justify-center">
                <MessageSquare size={32} className="text-accent-blue" />
              </div>
              <h2 className="text-xl font-semibold mb-2">欢迎使用 JwCode</h2>
              <p className="text-dark-muted text-sm">我可以帮你编写代码、分析项目、自动化任务。试试发送一条消息吧！</p>
              <div className="mt-6 flex flex-wrap justify-center gap-2">
                <span className="text-xs px-3 py-1.5 bg-dark-surface rounded-full text-dark-muted">💡 代码编写</span>
                <span className="text-xs px-3 py-1.5 bg-dark-surface rounded-full text-dark-muted">🔍 代码分析</span>
                <span className="text-xs px-3 py-1.5 bg-dark-surface rounded-full text-dark-muted">⚡ 自动化任务</span>
              </div>
              <div className="mt-4 text-xs text-dark-muted">
                输入 <kbd className="px-1 py-0.5 bg-dark-bg rounded border border-dark-border">/</kbd> 查看快捷命令
              </div>
            </div>
          </div>
        ) : (
          messages.map(message => (
            <MessageBubble key={message.id} message={message} />
          ))
        )}
        
        {isGenerating && (
          <div className="flex items-center gap-3 text-dark-muted">
            <div className="flex gap-1">
              <span className="w-2 h-2 bg-accent-blue rounded-full animate-bounce" />
              <span className="w-2 h-2 bg-accent-blue rounded-full animate-bounce" style={{ animationDelay: '0.1s' }} />
              <span className="w-2 h-2 bg-accent-blue rounded-full animate-bounce" style={{ animationDelay: '0.2s' }} />
            </div>
            <span className="text-sm">AI 正在思考...</span>
          </div>
        )}
        
        <div ref={messagesEndRef} />
      </div>
      
      {/* Input Area */}
      <div className="p-4 border-t border-dark-border bg-dark-surface relative">
        <SlashCommandMenu
          isOpen={isOpen}
          commands={filteredCommands}
          selectedIndex={selectedIndex}
          filter={slashCommands.filter}
          onSelect={handleSelectCommand}
          onHover={setSelectedIndex}
          containerRef={containerRef}
        />
        <div className="flex gap-3 items-end">
          <textarea
            value={input}
            onChange={handleChange}
            onKeyDown={handleKeyDown}
            placeholder="输入消息... (Enter 发送, Shift+Enter 换行, / 快捷命令)"
            className="flex-1 bg-dark-bg border border-dark-border rounded-lg px-4 py-3 text-dark-text placeholder-dark-muted resize-none focus:border-accent-blue focus:outline-none text-sm"
            rows={2}
          />
          <button
            onClick={handleSend}
            disabled={!input.trim() || isGenerating}
            className="px-4 py-3 bg-accent-green text-white rounded-lg hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed transition-all flex items-center gap-2"
          >
            <Send size={16} />
            <span className="hidden sm:inline">发送</span>
          </button>
        </div>
        <div className="mt-2 text-xs text-dark-muted text-center">
          <kbd className="px-1.5 py-0.5 bg-dark-bg rounded border border-dark-border">Ctrl</kbd>
          {' + '}
          <kbd className="px-1.5 py-0.5 bg-dark-bg rounded border border-dark-border">Enter</kbd>
          {' 快速发送 · '}
          <kbd className="px-1.5 py-0.5 bg-dark-bg rounded border border-dark-border">/</kbd>
          {' 快捷命令'}
        </div>
      </div>
    </div>
  );
}

// Message Bubble Component
function MessageBubble({ message }: { message: Message }) {
  const isUser = message.type === 'user';
  
  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'} animate-fade-in`}>
      <div
        className={`max-w-[85%] md:max-w-[75%] px-4 py-3 rounded-2xl ${
          isUser
            ? 'bg-accent-blue text-white rounded-br-md'
            : 'bg-dark-surface border border-dark-border text-dark-text rounded-bl-md'
        }`}
      >
        {/* Steps - Collapsible with ToolCalls */}
        {message.steps && message.steps.length > 0 && (
          <div className="mb-1 space-y-0.5">
            {message.steps.map((step) => (
              <StepItem key={step.id} step={step} defaultCollapsed={false} />
            ))}
          </div>
        )}
        
        {/* Fallback: 没有 steps 时才独立展示 thinking / toolCalls */}
        {!message.steps?.length && message.thinking && (
          <div className="mb-1 p-1.5 bg-dark-bg border border-dark-border rounded-lg">
            <div className="text-xs text-accent-blue mb-0.5 flex items-center gap-1">
              <span>💭</span>
              <span>思考过程</span>
            </div>
            <div className="text-sm text-dark-muted italic">{message.thinking.replace(/\n\s*\n+/g, '\n')}</div>
          </div>
        )}
        
        {!message.steps?.length && message.toolCalls && message.toolCalls.length > 0 && (
          <div className="mb-1 space-y-0.5">
            {message.toolCalls.map(toolCall => (
              <ToolCallItem key={toolCall.id} toolCall={toolCall} defaultCollapsed={true} />
            ))}
          </div>
        )}
        
        {/* Content with Markdown - Fixed line breaks */}
        {message.content ? (
            <div className="whitespace-pre-wrap break-words leading-relaxed">
            {isUser ? (
              <span>{message.content.replace(/\n\s*\n+/g, '\n')}</span>
            ) : (
              <MarkdownRenderer content={message.content.replace(/\n\s*\n+/g, '\n')} className="whitespace-pre-wrap" />
            )}
          </div>
        ) : !isUser && message.thinking ? (
          <div className="whitespace-pre-wrap break-words leading-relaxed text-dark-muted italic">
            <span className="inline-flex items-center gap-2">
              <span className="w-2 h-2 bg-accent-blue rounded-full animate-bounce" />
              <span className="w-2 h-2 bg-accent-blue rounded-full animate-bounce" style={{ animationDelay: '0.1s' }} />
              <span className="w-2 h-2 bg-accent-blue rounded-full animate-bounce" style={{ animationDelay: '0.2s' }} />
              💭 正在思考...
            </span>
          </div>
        ) : null}
        
        {/* Timestamp */}
        <div className={`text-[10px] mt-2 ${isUser ? 'text-white/70' : 'text-dark-muted'}`}>
          {new Date(message.timestamp).toLocaleTimeString('zh-CN', { 
            hour: '2-digit', 
            minute: '2-digit' 
          })}
        </div>
      </div>
    </div>
  );
}

// Expandable Result Component
function ExpandableResult({ text, maxLength = 300, preformatted = false }: { text: string; maxLength?: number; preformatted?: boolean }) {
  const [expanded, setExpanded] = useState(false);
  const needsExpand = text.length > maxLength;
  
  if (!needsExpand) {
    return preformatted
      ? <pre className="text-xs font-mono bg-dark-bg p-1.5 rounded overflow-x-auto mt-1 text-accent-green">{text}</pre>
      : <div className="text-dark-text leading-snug">{text}</div>;
  }
  
  return (
    <div>
      {preformatted ? (
        <pre className="text-xs font-mono bg-dark-bg p-1.5 rounded overflow-x-auto mt-1 text-accent-green">
          {expanded ? text : text.slice(0, maxLength) + '...'}
        </pre>
      ) : (
        <div className="text-dark-text leading-snug">
          {expanded ? text : text.slice(0, maxLength) + '...'}
        </div>
      )}
      <button
        onClick={(e) => { e.stopPropagation(); setExpanded(!expanded); }}
        className="mt-1 text-[10px] px-2 py-0.5 rounded bg-dark-hover text-accent-blue hover:bg-dark-border transition-colors"
      >
        {expanded ? '收起' : '展开'}
      </button>
    </div>
  );
}

// Step Item Component - 树形结构
function StepItem({ step, defaultCollapsed = false }: { step: Step; defaultCollapsed?: boolean }) {
  const [isCollapsed, setIsCollapsed] = useState(defaultCollapsed);
  
  const statusColors = {
    pending: 'bg-dark-hover text-dark-muted',
    running: 'bg-accent-blue text-white animate-pulse-soft',
    success: 'bg-accent-green text-white',
    error: 'bg-accent-red text-white',
    warning: 'bg-accent-yellow text-white',
  };
  
  const statusIcons = {
    pending: '○',
    running: '⟳',
    success: '✓',
    error: '✗',
    warning: '⚠',
  };
  
  const hasDetails = step.thought || step.result || step.action || (step.toolCalls && step.toolCalls.length > 0);
  
  return (
    <div className={`bg-dark-bg border-l-4 rounded-r-lg text-sm overflow-hidden ${
      step.status === 'running' ? 'border-accent-blue' :
      step.status === 'success' ? 'border-accent-green' :
      step.status === 'error' ? 'border-accent-red' :
      step.status === 'warning' ? 'border-accent-yellow' :
      'border-dark-border'
    }`}>
      {/* Header - Always visible */}
      <div 
        className="flex items-center gap-2 p-3 cursor-pointer hover:bg-dark-surface/50"
        onClick={() => hasDetails && setIsCollapsed(!isCollapsed)}
      >
        {hasDetails ? (
          isCollapsed ? <Plus size={14} className="text-accent-blue" /> : <Minus size={14} className="text-accent-blue" />
        ) : <span className="w-3.5" />}
        <span className={`w-5 h-5 rounded-full flex items-center justify-center text-xs ${statusColors[step.status]}`}>
          {statusIcons[step.status]}
        </span>
        <span className="font-medium">{step.title}</span>
        {step.status === 'running' && <span className="text-xs text-accent-blue animate-pulse">运行中...</span>}
      </div>
      
      {/* Collapsible content - 树形结构 */}
      {!isCollapsed && (
        <div className="ml-4 space-y-1">
          {/* Step Description */}
          {step.description && (
            <div className="text-dark-muted py-1 px-3 text-xs border-l-2 border-dark-border">
              {step.description}
            </div>
          )}
          
          {/* 1. Thought - 💭 思考过程 */}
          {step.thought && (
            <div className="py-1 px-3 text-xs border-l-2 border-accent-blue pl-2 bg-accent-blue/5 rounded-r">
              <div className="flex items-center gap-1 text-accent-blue mb-0.5">
                <span>💭</span>
                <span>思考</span>
              </div>
              <div className="text-dark-muted italic whitespace-pre-wrap leading-snug">
                {step.thought.replace(/\n\s*\n+/g, '\n')}
              </div>
            </div>
          )}
          
          {/* 2. Action - ⚡ 执行动作 */}
          {step.action && (
            <div className="py-1 px-3 text-xs border-l-2 border-accent-yellow pl-2 bg-accent-yellow/5 rounded-r">
              <div className="flex items-center gap-1 text-accent-yellow mb-0.5">
                <span>⚡</span>
                <span>执行</span>
              </div>
              <div className="text-dark-text leading-snug">
                {step.action}
              </div>
            </div>
          )}
          
          {/* 3. Tool Calls - 🔧 请求+结果（树形子节点） */}
          {step.toolCalls && step.toolCalls.length > 0 && step.toolCalls.map((toolCall, index) => (
            <div key={toolCall.id} className="py-1 px-3 text-xs border-l-2 border-accent-purple pl-2 bg-accent-purple/5 rounded-r">
              <div className="flex items-center gap-1 text-accent-purple mb-0.5">
                <span>🔧</span>
                <span>工具调用 {index + 1}</span>
                <span className={`text-[10px] px-1.5 py-0.5 rounded ${
                  toolCall.status === 'running' ? 'bg-accent-blue text-white' : 'bg-accent-green text-white'
                }`}>
                  {toolCall.status === 'running' ? '运行中' : '完成'}
                </span>
              </div>
              {/* 请求参数 */}
              <div className="ml-2 text-dark-muted mb-0.5">
                <span className="text-accent-purple/70">请求:</span>
                <pre className="text-xs font-mono bg-dark-bg p-1.5 rounded overflow-x-auto mt-1">
                  {typeof toolCall.args === 'string'
                    ? toolCall.args
                    : JSON.stringify(toolCall.args, null, 2)}
                </pre>
              </div>
              {/* 返回结果 */}
              {toolCall.result !== undefined && toolCall.result !== null && (
                <div className="ml-2 text-dark-muted">
                  <span className="text-accent-green/70">结果:</span>
                  <ExpandableResult
                    text={typeof toolCall.result === 'object' ? JSON.stringify(toolCall.result, null, 2) : String(toolCall.result)}
                    maxLength={300}
                    preformatted
                  />
                </div>
              )}
            </div>
          ))}
          
          {/* 4. Result - ✓ 完成结果 */}
          {step.result && (
            <div className="py-1 px-3 text-xs border-l-2 border-accent-green pl-2 bg-accent-green/5 rounded-r">
              <div className="flex items-center gap-1 text-accent-green mb-0.5">
                <span>✓</span>
                <span>结果</span>
              </div>
              <ExpandableResult text={step.result} maxLength={300} />
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// Tool Call Item Component - Collapsible
function ToolCallItem({ toolCall, defaultCollapsed = false }: { toolCall: ToolCall; defaultCollapsed?: boolean }) {
  const [isCollapsed, setIsCollapsed] = useState(defaultCollapsed);
  const isRunning = toolCall.status === 'running';
  const hasResult = toolCall.result !== undefined && toolCall.result !== null;
  
  return (
    <div className="bg-dark-bg border border-dark-border rounded-lg overflow-hidden">
      {/* Header - Always visible, clickable to collapse/expand */}
      <div 
        className="flex items-center justify-between px-3 py-2 bg-dark-surface border-b border-dark-border cursor-pointer hover:bg-dark-hover"
        onClick={() => setIsCollapsed(!isCollapsed)}
      >
        <div className="flex items-center gap-2">
          {isCollapsed ? <Plus size={14} className="text-accent-blue" /> : <Minus size={14} className="text-accent-blue" />}
          <span className="text-accent-blue">🔧</span>
          <span className="font-medium text-sm">{toolCall.name}</span>
        </div>
        <span className={`text-xs px-2 py-0.5 rounded ${
          isRunning ? 'bg-accent-blue text-white' : 'bg-accent-green text-white'
        }`}>
          {isRunning ? '运行中' : '完成'}
        </span>
      </div>
      
      {/* Collapsible content */}
      {!isCollapsed && (
        <div className="p-3">
          <div className="text-xs text-dark-muted mb-1">参数</div>
          <pre className="text-xs font-mono bg-dark-bg p-2 rounded overflow-x-auto">
            {typeof toolCall.args === 'string'
              ? toolCall.args
              : JSON.stringify(toolCall.args, null, 2)}
          </pre>
          {hasResult && (
            <>
              <div className="text-xs text-dark-muted mb-1 mt-2">结果</div>
              <pre className="text-xs font-mono bg-dark-bg p-2 rounded overflow-x-auto text-accent-green">
                {typeof toolCall.result === 'object' ? JSON.stringify(toolCall.result, null, 2) : String(toolCall.result)}
              </pre>
            </>
          )}
        </div>
      )}
      
      {/* Show hint if collapsed */}
      {isCollapsed && hasResult && (
        <div className="px-3 py-2 text-xs text-dark-muted">
          ✓ 有返回结果
        </div>
      )}
    </div>
  );
}

// Logs Panel Component
function LogsPanel({ logs, onClear }: { logs: LogEntry[]; onClear: () => void }) {
  const levelColors = {
    info: 'bg-accent-blue',
    warn: 'bg-accent-yellow',
    error: 'bg-accent-red',
    success: 'bg-accent-green',
    tool: 'bg-accent-purple',
  };
  
  const levelIcons = {
    info: 'ℹ️',
    warn: '⚠️',
    error: '❌',
    success: '✅',
    tool: '🔧',
  };
  
  return (
    <div className="flex-1 flex flex-col overflow-hidden p-4">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold flex items-center gap-2">
          <ScrollText size={18} className="text-accent-blue" />
          后台日志
          <span className="text-sm font-normal text-dark-muted">({logs.length})</span>
        </h2>
        <button
          onClick={onClear}
          className="px-3 py-1.5 text-sm bg-dark-surface border border-dark-border rounded hover:bg-dark-hover transition-colors"
        >
          清空
        </button>
      </div>
      
      <div className="flex-1 overflow-y-auto font-mono text-sm space-y-1">
        {logs.length === 0 ? (
          <div className="text-center text-dark-muted py-12">
            <ScrollText size={48} className="mx-auto mb-2 opacity-50" />
            <p>暂无日志</p>
          </div>
        ) : (
          logs.map(log => (
            <div
              key={log.id}
              className={`flex gap-2 p-2 rounded hover:bg-dark-surface transition-colors ${
                log.level === 'error' ? 'bg-accent-red/10' :
                log.level === 'warn' ? 'bg-accent-yellow/10' :
                log.level === 'success' ? 'bg-accent-green/10' : ''
              }`}
            >
              <span className="text-dark-muted shrink-0 text-xs">
                {new Date(log.timestamp).toLocaleTimeString('zh-CN', {
                  hour: '2-digit',
                  minute: '2-digit',
                  second: '2-digit',
                })}
              </span>
              <span className="text-lg shrink-0">{levelIcons[log.level]}</span>
              <span className={`px-1.5 py-0.5 rounded text-white text-xs shrink-0 ${levelColors[log.level]}`}>
                {log.level.toUpperCase()}
              </span>
              <span className="text-dark-muted shrink-0 text-xs">[{log.source}]</span>
              <span className="text-dark-text break-all">{log.message}</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

// Settings Panel Component
function SettingsPanel() {
  const { 
    theme, setTheme,
    thinking, setThinkingEnabled,
    yolo, setYoloEnabled,
    autoSwarm, setAutoSwarmEnabled,
    autoAI, setAutoAIEnabled,
    compression, setCompressionEnabled
  } = useSettingsStore();
  
  return (
    <div className="flex-1 overflow-y-auto p-4">
      <h2 className="text-lg font-semibold mb-4 flex items-center gap-2">
        <Settings size={18} className="text-accent-blue" />
        设置
      </h2>
      
      <div className="space-y-6 max-w-2xl">
        {/* Theme */}
        <div className="bg-dark-surface border border-dark-border rounded-lg p-4">
          <h3 className="font-medium mb-3 flex items-center gap-2">
            <span>🎨</span> 主题
          </h3>
          <div className="flex gap-2">
            {(['dark', 'light', 'auto'] as const).map(t => (
              <button
                key={t}
                onClick={() => setTheme(t)}
                className={`px-4 py-2 rounded-lg transition-all ${
                  theme === t 
                    ? 'bg-accent-blue text-white' 
                    : 'bg-dark-hover text-dark-text hover:bg-dark-border'
                }`}
              >
                {t === 'dark' ? '🌙 深色' : t === 'light' ? '☀️ 浅色' : '🔄 自动'}
              </button>
            ))}
          </div>
        </div>
        
        {/* Advanced Features */}
        <div className="bg-dark-surface border border-dark-border rounded-lg p-4">
          <h3 className="font-medium mb-3 flex items-center gap-2">
            <span>🚀</span> 高级功能
          </h3>
          <div className="space-y-3">
            <FeatureToggle
              title="🧠 Thinking Mode"
              subtitle="深度推理模式 - 让 AI 进行更详细的思考"
              enabled={thinking.enabled}
              onChange={setThinkingEnabled}
            />
            <FeatureToggle
              title="⚡ YOLO Mode"
              subtitle="全自动模式 - 无需确认直接执行"
              enabled={yolo.enabled}
              onChange={setYoloEnabled}
            />
            <FeatureToggle
              title="🐝 Auto Swarm"
              subtitle="自动智能体集群 - 多 Agent 协同工作"
              enabled={autoSwarm.enabled}
              onChange={setAutoSwarmEnabled}
            />
            <FeatureToggle
              title="🤖 Auto AI"
              subtitle="自动 AI 规划 - 智能任务分解与执行"
              enabled={autoAI.enabled}
              onChange={setAutoAIEnabled}
            />
            <FeatureToggle
              title="📦 Context Compression"
              subtitle="上下文压缩 - 自动管理对话长度"
              enabled={compression.enabled}
              onChange={setCompressionEnabled}
            />
          </div>
        </div>
        
        {/* About */}
        <div className="bg-dark-surface border border-dark-border rounded-lg p-4">
          <h3 className="font-medium mb-3 flex items-center gap-2">
            <span>ℹ️</span> 关于
          </h3>
          <div className="text-sm text-dark-muted space-y-2">
            <p><span className="text-dark-text">JwCode Web</span> v1.0.0</p>
            <p>基于 Web 的 AI 编码助手界面</p>
            <p className="text-xs">Powered by React + TypeScript + TailwindCSS</p>
          </div>
        </div>
      </div>
    </div>
  );
}

// Feature Toggle Component
interface FeatureToggleProps {
  title: string;
  subtitle: string;
  enabled: boolean;
  onChange: (enabled: boolean) => void;
}

function FeatureToggle({ title, subtitle, enabled, onChange }: FeatureToggleProps) {
  return (
    <div className="flex items-center justify-between p-3 bg-dark-bg rounded-lg">
      <div>
        <div className="font-medium text-sm">{title}</div>
        <div className="text-xs text-dark-muted">{subtitle}</div>
      </div>
      <button
        onClick={() => onChange(!enabled)}
        className={`relative w-12 h-6 rounded-full transition-colors ${
          enabled ? 'bg-accent-green' : 'bg-dark-border'
        }`}
      >
        <span
          className={`absolute top-1 w-4 h-4 bg-white rounded-full transition-transform ${
            enabled ? 'left-7' : 'left-1'
          }`}
        />
      </button>
    </div>
  );
}

// Render tab content
function renderTabContent(tabId: TabId) {
  switch (tabId) {
    case 'models':
      return <ModelsView />;
    case 'tools':
      return <ToolsView />;
    case 'skills':
      return <SkillsView />;
    case 'agents':
      return <AgentsView />;
    case 'tasks':
      return <TasksView />;
    case 'files':
      return <FileTreeView />;
    default:
      return null;
  }
}

export default App;

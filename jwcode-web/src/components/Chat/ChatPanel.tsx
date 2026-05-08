import { memo } from 'react';
import { MessageSquare, Send, ListChecks, Zap } from 'lucide-react';
import { Message, TabId, LogEntry } from '../../types';
import { MessageBubble } from './MessageBubble';
import { SlashCommandMenu } from '../SlashCommandMenu';
import { useSlashCommands, SlashCommand } from '../../hooks/useSlashCommands';
import { usePlanStore } from '../../stores/planStore';
import { SessionTaskBoard } from './SessionTaskBoard';


interface ChatPanelProps {
  messages: Message[];
  isGenerating: boolean;
  onSend: (content: string) => void;
  input: string;
  setInput: (input: string) => void;
  messagesEndRef: React.MutableRefObject<HTMLDivElement | null>;
  compact?: boolean;
  sessionId: string; // 用于 SessionTaskBoard
  // 独立 slash commands 所需的回调
  activeTab: TabId;
  setActiveTab: (tab: TabId) => void;
  createNewSession: () => void;
  clearMessages: () => void;
  setTheme: (theme: 'dark' | 'light' | 'auto') => void;
  toggleTerminal: () => void;
  setLogs: React.Dispatch<React.SetStateAction<LogEntry[]>>;
  setUnreadLogs: React.Dispatch<React.SetStateAction<number>>;
}

export const ChatPanel = memo(function ChatPanel({
  messages, isGenerating, onSend, input, setInput, messagesEndRef, sessionId,
  setActiveTab, createNewSession, clearMessages, setTheme, toggleTerminal, setLogs, setUnreadLogs,
}: ChatPanelProps) {

  const mode = usePlanStore((s) => s.mode);
  const setMode = usePlanStore((s) => s.setMode);

  // 每个 ChatPanel 独立创建 slashCommands 实例，互不干扰
  const slashCommands = useSlashCommands({
    setActiveTab,
    createNewSession,
    clearMessages,
    setTheme,
    toggleTerminal,
    setLogs,
    setUnreadLogs,
  });

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
    <div className="flex-1 flex flex-col overflow-hidden min-h-0">
      {/* 消息区域：占满剩余空间，内容少时也能撑开 */}
      <div className="flex-1 overflow-auto min-h-0 flex flex-col">
        {messages.length === 0 ? (
          /* 欢迎页：flex-1 确保撑满 flex 容器 */
          <div className="flex-1 flex items-center justify-center p-4">
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
          /* 消息列表：有消息时用 p-4 space-y-2 布局 */
          <div className="p-4 space-y-2">
            <div id="scroll-anchor" />
            {messages.map(message => (
              <MessageBubble key={message.id} message={message} />
            ))}
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
        )}
      </div>

      {/* 每个会话独立的任务看板 - 放在消息区域和输入区域之间 */}
      <div className="shrink-0">
        <SessionTaskBoard sessionId={sessionId} />
      </div>

      {/* Input Area - shrink-0 防止被压缩，固定在底部 */}
      <div className="p-3 sm:p-4 border-t border-dark-border bg-dark-surface relative shrink-0">

        <SlashCommandMenu
          isOpen={isOpen}
          commands={filteredCommands}
          selectedIndex={selectedIndex}
          filter={slashCommands.filter}
          onSelect={handleSelectCommand}
          onHover={setSelectedIndex}
          containerRef={containerRef}
        />
        <div className="flex gap-2 items-end">
          {/* Plan/Act 模式切换按钮组 */}
          <div className="flex flex-col gap-1 shrink-0">
            <div className="flex bg-dark-bg rounded-lg p-0.5 border border-dark-border">
              <button
                onClick={() => setMode('plan')}
                className={`flex items-center gap-1 px-2.5 py-1.5 text-xs rounded-md transition-all ${
                  mode === 'plan'
                    ? 'bg-accent-blue text-white shadow-sm'
                    : 'text-dark-muted hover:text-dark-text'
                }`}
                title="Plan 模式：AI 先分析并制定任务计划"
              >
                <ListChecks size={14} />
                <span className="hidden sm:inline">Plan</span>
              </button>
              <button
                onClick={() => setMode('act')}
                className={`flex items-center gap-1 px-2.5 py-1.5 text-xs rounded-md transition-all ${
                  mode === 'act'
                    ? 'bg-accent-green text-white shadow-sm'
                    : 'text-dark-muted hover:text-dark-text'
                }`}
                title="Act 模式：AI 直接执行任务"
              >
                <Zap size={14} />
                <span className="hidden sm:inline">Act</span>
              </button>
            </div>
            {mode === 'plan' && (
              <span className="text-[10px] text-accent-blue text-center">计划模式</span>
            )}
          </div>

          <textarea
            value={input}
            onChange={handleChange}
            onKeyDown={handleKeyDown}
            placeholder="输入消息... (Enter 发送, Shift+Enter 换行, / 快捷命令)"
            className="flex-1 bg-dark-bg border border-dark-border rounded-lg px-3 sm:px-4 py-2.5 sm:py-3 text-dark-text placeholder-dark-muted resize-none focus:border-accent-blue focus:outline-none text-sm min-h-[40px] max-h-[40vh]"
            rows={1}
            onInput={(e) => {
              const target = e.currentTarget;
              target.style.height = 'auto';
              target.style.height = Math.min(target.scrollHeight, window.innerHeight * 0.4) + 'px';
            }}
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
        <div className="mt-2 text-xs text-dark-muted text-center flex items-center justify-center gap-3">
          <span>
            <kbd className="px-1.5 py-0.5 bg-dark-bg rounded border border-dark-border">Ctrl</kbd>
            {' + '}
            <kbd className="px-1.5 py-0.5 bg-dark-bg rounded border border-dark-border">Enter</kbd>
            {' 快速发送'}
          </span>
          <span>
            <kbd className="px-1.5 py-0.5 bg-dark-bg rounded border border-dark-border">/</kbd>
            {' 快捷命令'}
          </span>
          {mode === 'plan' && (
            <span className="text-accent-blue">📋 Plan 模式：AI 将先制定计划再执行</span>
          )}
        </div>
      </div>
    </div>
  );
});

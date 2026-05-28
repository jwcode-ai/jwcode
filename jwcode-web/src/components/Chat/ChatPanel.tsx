import { memo, useRef } from 'react';
import { useAutoScroll } from '../../hooks/useAutoScroll';
import { MessageSquare, ListChecks, Zap, Square, Pause, Play } from 'lucide-react';
import { Message, TabId, LogEntry } from '../../types';
import { MessageBubble } from './MessageBubble';
import { SlashCommandMenu } from '../SlashCommandMenu';
import { useSlashCommands, SlashCommand } from '../../hooks/useSlashCommands';
import { usePlanStore } from '../../stores/planStore';
import { ContextManager } from './ContextManager';
import { SessionTaskBoard } from './SessionTaskBoard';


interface ChatPanelProps {
  messages: Message[];
  isGenerating: boolean;
  isPaused: boolean;
  onSend: (content: string) => void;
  onStop: () => void;
  onPause: () => void;
  onResume: () => void;
  input: string;
  setInput: (input: string) => void;
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
  messages, isGenerating, isPaused, onSend, onStop, onPause, onResume, input, setInput, sessionId,
  setActiveTab, createNewSession, clearMessages, setTheme, toggleTerminal, setLogs, setUnreadLogs,
}: ChatPanelProps) {

  const mode = usePlanStore((s) => s.mode);
  const setMode = usePlanStore((s) => s.setMode);
  const showConfirmButton = usePlanStore((s) => s.showConfirmButton);
  const planConfirmed = usePlanStore((s) => s.planConfirmed);

  // Plan 模式下有待确认的计划时，禁用输入
  const isPlanWaitingConfirm = mode === 'plan' && showConfirmButton && !planConfirmed;

  // textarea ref，用于发送后重置高度
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const isComposingRef = useRef(false);

  // 智能自动滚动：新消息时自动滚到底部，用户手动滚动时暂停
  const { containerRef: scrollContainerRef } = useAutoScroll([messages, isGenerating]);

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
      // 发送后重置 textarea 高度
      if (textareaRef.current) {
        textareaRef.current.style.height = 'auto';
      }
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
          executeCommand(cmd, args);
          setInput('');
          // /help 命令：重新打开菜单显示全部命令
          if (cmd.id === 'help') {
            setTimeout(() => slashCommands.openMenu(), 0);
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

    if (e.key === 'Enter' && !e.shiftKey && !isComposingRef.current) {
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
    executeCommand(cmd, args);
    setInput('');
    // /help 命令：重新打开菜单显示全部命令
    if (cmd.id === 'help') {
      setTimeout(() => slashCommands.openMenu(), 0);
    }
  };

  return (
    <div className="flex-1 flex flex-col overflow-hidden min-h-0">
      {/* 消息区域：占满剩余空间，内容少时也能撑开 */}
      <div ref={scrollContainerRef} className="flex-1 overflow-auto min-h-0 flex flex-col">
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
          <div className="p-4 space-y-4">
            {messages.map(message => (
              <MessageBubble key={message.id} message={message} sessionId={sessionId} />
            ))}
            {isGenerating && (
              <div className="flex items-center gap-2 text-xs text-dark-muted py-1">
                <span className="text-accent-blue animate-spin-frame text-sm">◐</span>
                <span>Thinking...</span>
              </div>
            )}
          </div>
        )}
      </div>

      {/* 紧凑工具栏: Context + Plan/Act + 任务看板切换 */}
      <div className="shrink-0 border-t border-dark-border bg-dark-surface h-9 flex items-center px-3 gap-2">
        <ContextManager />
        <div className="w-px h-4 bg-dark-border mx-1" />
        <button
          onClick={() => setMode('plan')}
          className={`flex items-center gap-1 px-2 py-0.5 text-[11px] rounded transition-all ${
            mode === 'plan' ? 'bg-accent-blue/10 text-accent-blue' : 'text-dark-muted hover:text-dark-text'
          }`}
          title="Plan 模式"
        ><ListChecks size={12} /> Plan</button>
        <button
          onClick={() => setMode('act')}
          className={`flex items-center gap-1 px-2 py-0.5 text-[11px] rounded transition-all ${
            mode === 'act' ? 'bg-accent-green/10 text-accent-green' : 'text-dark-muted hover:text-dark-text'
          }`}
          title="Act 模式"
        ><Zap size={12} /> Act</button>
        <div className="flex-1" />
        <SessionTaskBoard sessionId={sessionId} />
      </div>

      {/* Input Area */}
      <div className="px-3 pb-3 pt-2 border-t border-dark-border bg-dark-surface relative shrink-0">

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

          <textarea
            ref={textareaRef}
            value={input}
            onChange={handleChange}
            onKeyDown={handleKeyDown}
            placeholder=""
            className={`flex-1 bg-dark-bg border rounded-lg px-3 sm:px-4 py-2.5 sm:py-3 text-dark-text placeholder-dark-muted resize-none focus:outline-none text-sm min-h-[40px] max-h-[40vh] transition-colors ${
              mode === 'plan'
                ? 'border-accent-blue/50 focus:border-accent-blue'
                : 'border-dark-border focus:border-accent-green'
            }`}
            rows={1}
            disabled={isGenerating || isPlanWaitingConfirm}
            onCompositionStart={() => { isComposingRef.current = true; }}
            onCompositionEnd={(e) => {
              isComposingRef.current = false;
              // 组合输入结束后再调整高度
              const target = e.currentTarget;
              target.style.height = 'auto';
              target.style.height = Math.min(target.scrollHeight, window.innerHeight * 0.4) + 'px';
            }}
            onInput={(e) => {
              if (isComposingRef.current) return;
              const target = e.currentTarget;
              target.style.height = 'auto';
              target.style.height = Math.min(target.scrollHeight, window.innerHeight * 0.4) + 'px';
            }}
          />
          {/* 按钮区域：根据状态显示不同按钮 */}
          {isGenerating ? (
            <div className="flex gap-1.5 shrink-0">
              {isPaused ? (
                <>
                  {/* 暂停中：恢复 + 终止 */}
                  <button
                    onClick={onResume}
                    className="px-3 py-3 bg-accent-green text-white rounded-lg hover:opacity-90 transition-all flex items-center gap-1.5"
                    title="恢复生成"
                  >
                    <Play size={16} />
                    <span className="hidden sm:inline">恢复</span>
                  </button>
                  <button
                    onClick={onStop}
                    className="px-3 py-3 bg-accent-red text-white rounded-lg hover:opacity-90 transition-all flex items-center gap-1.5"
                    title="终止生成"
                  >
                    <Square size={16} />
                    <span className="hidden sm:inline">终止</span>
                  </button>
                </>
              ) : (
                <>
                  {/* 生成中：暂停 + 终止 */}
                  <button
                    onClick={onPause}
                    className="px-3 py-3 bg-accent-yellow text-white rounded-lg hover:opacity-90 transition-all flex items-center gap-1.5"
                    title="暂停生成"
                  >
                    <Pause size={16} />
                    <span className="hidden sm:inline">暂停</span>
                  </button>
                  <button
                    onClick={onStop}
                    className="px-3 py-3 bg-accent-red text-white rounded-lg hover:opacity-90 transition-all flex items-center gap-1.5"
                    title="终止生成"
                  >
                    <Square size={16} />
                    <span className="hidden sm:inline">终止</span>
                  </button>
                </>
              )}
            </div>
          ) : (
            <button
              onClick={handleSend}
              disabled={!input.trim() || isPlanWaitingConfirm}
              className={`px-4 py-3 text-white rounded-lg hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed transition-all flex items-center gap-2 ${
                mode === 'plan'
                  ? 'bg-accent-blue'
                  : 'bg-accent-green'
              }`}
            >
              {mode === 'plan' ? <ListChecks size={16} /> : <Zap size={16} />}
              <span className="hidden sm:inline">{mode === 'plan' ? '生成计划' : '执行'}</span>
            </button>
          )}
        </div>
      </div>
    </div>
  );
});

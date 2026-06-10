import { memo, useRef, useCallback, useState, useMemo, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Virtuoso, VirtuosoHandle } from 'react-virtuoso';
import { MessageSquare, Zap, Square, Pause, Play } from 'lucide-react';
import { Message, TabId, LogEntry, FileNode } from '../../types';
import { MessageBubble } from './MessageBubble';
import { SlashCommandMenu } from '../SlashCommandMenu';
import { FileMentionMenu } from './FileMentionMenu';
import { useSlashCommands, SlashCommand } from '../../hooks/useSlashCommands';
import { useInputHistory, addToHistory } from '../../hooks/useInputHistory';
import { usePlanStore } from '../../stores/planStore';
import { useTokenStore } from '../../stores/tokenStore';
import { useHookApprovalStore } from '../../stores/useHookApprovalStore';
import { api } from '../../services/api';
import { ContextManager } from './ContextManager';
import { SessionTaskBoard } from './SessionTaskBoard';

interface ChatPanelProps {
  messages: Message[];
  isGenerating: boolean;
  isPaused: boolean;
  onSend: (content: string, referencedFiles?: string[]) => void;
  onStop: () => void;
  onPause: () => void;
  onResume: () => void;
  input: string;
  setInput: (input: string) => void;
  compact?: boolean;
  sessionId: string;
  activeTab: TabId;
  setActiveTab: (tab: TabId) => void;
  createNewSession: () => void;
  clearMessages: () => void;
  setTheme: (theme: 'dark' | 'light' | 'auto') => void;
  toggleTerminal: () => void;
  setLogs: React.Dispatch<React.SetStateAction<LogEntry[]>>;
  setUnreadLogs: React.Dispatch<React.SetStateAction<number>>;
}

async function searchFiles(query: string): Promise<FileNode[]> {
  try {
    const result = await api.files.list('.');
    if (!result.success || !result.data) return [];
    // Flatten and fuzzy filter
    const flat: FileNode[] = [];
    const walk = (nodes: FileNode[]) => {
      for (const n of nodes) {
        if (n.type === 'file') flat.push(n);
        if (n.children) walk(n.children);
      }
    };
    walk(result.data);
    if (!query) return flat.slice(0, 15);
    const q = query.toLowerCase();
    return flat.filter(f => f.name.toLowerCase().includes(q)).slice(0, 15);
  } catch { return []; }
}

export const ChatPanel = memo(function ChatPanel({
  messages, isGenerating, isPaused, onSend, onStop, onPause, onResume, input, setInput, sessionId,
  setActiveTab, createNewSession, clearMessages, setTheme, toggleTerminal, setLogs, setUnreadLogs,
}: ChatPanelProps) {
  const { t } = useTranslation();
  const planMode = usePlanStore((s) => s.mode);
  const toggleMode = usePlanStore((s) => s.toggleMode);
  const hasPendingApproval = useHookApprovalStore((s) => s.pendingApprovals.length > 0);

  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const isComposingRef = useRef(false);
  const virtuosoRef = useRef<VirtuosoHandle>(null);
  const lastEscRef = useRef(0);

  // Input history
  const { navigate, reset: resetHistory } = useInputHistory(sessionId);

  // @ file mention state
  const [showFileMenu, setShowFileMenu] = useState(false);
  const [fileMenuFiles, setFileMenuFiles] = useState<FileNode[]>([]);
  const [isFileSearching, setIsFileSearching] = useState(false);
  const [fileMenuIndex, setFileMenuIndex] = useState(0);
  const [fileSearchQuery, setFileSearchQuery] = useState('');
  const fileSearchRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Track @-referenced file paths for content attachment on send
  const referencedFilesRef = useRef<Set<string>>(new Set());
  // Preloaded flat file list for instant @ popup
  const allFilesRef = useRef<FileNode[]>([]);
  const filesLoadedRef = useRef(false);

  // Preload file tree on mount for instant @ popup
  useEffect(() => {
    let cancelled = false;
    api.files.list('.').then(result => {
      if (cancelled || !result.success || !result.data) return;
      const flat: FileNode[] = [];
      const walk = (nodes: FileNode[]) => {
        for (const n of nodes) {
          if (n.type === 'file') flat.push(n);
          if (n.children) walk(n.children);
        }
      };
      walk(result.data);
      allFilesRef.current = flat;
      filesLoadedRef.current = true;
    }).catch(() => {});
    return () => { cancelled = true; };
  }, []);

  // Slash commands
  const slashCommands = useSlashCommands({ setActiveTab, createNewSession, clearMessages, setTheme, toggleTerminal, setLogs, setUnreadLogs });
  const { isOpen, setFilter, selectedIndex, setSelectedIndex, filteredCommands, closeMenu, selectNext, selectPrev, executeCommand, containerRef } = slashCommands;

  // Token estimate
  const estimateTokens = useTokenStore((s) => s.estimateTokens);
  const tokenEstimate = useMemo(() => estimateTokens(input), [input, estimateTokens]);

  const resetTextarea = () => {
    if (textareaRef.current) textareaRef.current.style.height = 'auto';
  };

  const handleSend = () => {
    if (input.trim()) {
      addToHistory(input);
      resetHistory();
      const files = Array.from(referencedFilesRef.current);
      referencedFilesRef.current.clear();
      onSend(input, files.length > 0 ? files : undefined);
      setInput('');
      resetTextarea();
      closeFileMention();
    }
  };

  const closeFileMention = () => {
    setShowFileMenu(false);
    setFileSearchQuery('');
    setIsFileSearching(false);
    setFileMenuFiles([]);
    setFileMenuIndex(0);
    if (fileSearchRef.current) clearTimeout(fileSearchRef.current);
  };

  const handleAtSearch = useCallback((query: string) => {
    setFileSearchQuery(query);
    setShowFileMenu(true);

    if (!filesLoadedRef.current) {
      // Preload not ready — fall back to on-demand fetch
      setIsFileSearching(true);
      searchFiles(query).then(files => {
        setFileMenuFiles(files);
        setIsFileSearching(false);
        setFileMenuIndex(0);
      });
      return;
    }

    // Sync filter from preloaded cache (no API call, no delay)
    setIsFileSearching(false);
    const allFiles = allFilesRef.current;
    if (!query) {
      setFileMenuFiles(allFiles.slice(0, 15));
    } else {
      const q = query.toLowerCase();
      setFileMenuFiles(allFiles.filter(f => f.name.toLowerCase().includes(q)).slice(0, 15));
    }
    setFileMenuIndex(0);
  }, []);

  // Detect @ trigger position in textarea
  const checkAtTrigger = useCallback((value: string, cursorPos: number) => {
    // Find the last @ before cursor that isn't preceded by a word char (not in an email etc)
    const beforeCursor = value.slice(0, cursorPos);
    const atIdx = beforeCursor.lastIndexOf('@');
    if (atIdx === -1) { closeFileMention(); return; }
    // Ensure @ isn't part of a word (e.g. email)
    if (atIdx > 0 && /\w/.test(beforeCursor[atIdx - 1]!)) { closeFileMention(); return; }
    // Get text after @ up to cursor
    const query = beforeCursor.slice(atIdx + 1);
    // Only trigger if query is alphanumeric (or empty)
    if (query && !/^[\w.\-/]*$/.test(query)) { closeFileMention(); return; }

    if (fileSearchRef.current) clearTimeout(fileSearchRef.current);
    handleAtSearch(query);
  }, [handleAtSearch]);

  const handleFileSelect = useCallback((file: FileNode) => {
    if (!textareaRef.current) return;
    const ta = textareaRef.current;
    const cursorPos = ta.selectionStart;
    const value = input;
    const beforeCursor = value.slice(0, cursorPos);
    const atIdx = beforeCursor.lastIndexOf('@');
    if (atIdx === -1) return;

    const afterCursor = value.slice(cursorPos);
    const newValue = value.slice(0, atIdx) + file.path + afterCursor;
    setInput(newValue);
    // Track file for content attachment on send
    referencedFilesRef.current.add(file.path);
    closeFileMention();

    // Restore cursor after inserted path
    const newPos = atIdx + file.path.length;
    setTimeout(() => {
      ta.focus();
      ta.setSelectionRange(newPos, newPos);
    }, 0);
  }, [input, setInput]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    // File mention menu
    if (showFileMenu) {
      if (e.key === 'ArrowDown') { e.preventDefault(); setFileMenuIndex(i => Math.min(i + 1, fileMenuFiles.length - 1)); return; }
      if (e.key === 'ArrowUp') { e.preventDefault(); setFileMenuIndex(i => Math.max(i - 1, 0)); return; }
      if (e.key === 'Enter') { e.preventDefault(); if (fileMenuFiles[fileMenuIndex]) handleFileSelect(fileMenuFiles[fileMenuIndex]!); return; }
      if (e.key === 'Escape') { e.preventDefault(); closeFileMention(); return; }
      return;
    }

    // Slash command menu
    if (isOpen) {
      if (e.key === 'ArrowDown') { e.preventDefault(); selectNext(); return; }
      if (e.key === 'ArrowUp') { e.preventDefault(); selectPrev(); return; }
      if (e.key === 'Enter') {
        e.preventDefault();
        const cmd = filteredCommands[selectedIndex];
        if (cmd) { executeCommand(cmd, input.slice(input.indexOf(cmd.name) + cmd.name.length + 1).trim()); setInput(''); if (cmd.id === 'help') setTimeout(() => slashCommands.openMenu(), 0); }
        return;
      }
      if (e.key === 'Escape') { e.preventDefault(); closeMenu(); return; }
      return;
    }

    // ESC pause/stop generation — single pauses, double within 500ms stops
    if (e.key === 'Escape' && isGenerating) {
      e.preventDefault();
      const now = Date.now();
      const prev = lastEscRef.current;
      lastEscRef.current = now;
      if (prev > 0 && (now - prev) < 500) {
        onStop();
      } else {
        onPause();
      }
      return;
    }

    // Input history: ArrowUp when at start or empty
    if (e.key === 'ArrowUp' && !isComposingRef.current) {
      const ta = textareaRef.current;
      if (ta && ta.selectionStart === 0) {
        e.preventDefault();
        const result = navigate('up', input);
        if (result !== null) { setInput(result); resetTextarea(); }
        return;
      }
    }
    if (e.key === 'ArrowDown' && !isComposingRef.current) {
      const ta = textareaRef.current;
      if (ta && ta.selectionStart === input.length) {
        e.preventDefault();
        const result = navigate('down', input);
        if (result !== null) { setInput(result); resetTextarea(); }
        return;
      }
    }

    if (e.key === 'Enter' && !e.shiftKey && !isComposingRef.current) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const value = e.target.value;
    setInput(value);

    // Reset history navigation on manual input
    resetHistory();

    // @ file mention detection
    if (!isComposingRef.current) {
      checkAtTrigger(value, e.target.selectionStart);
    }

    // Slash command detection
    if (value === '/') { slashCommands.openMenu(); return; }
    if (value.startsWith('/') && !isOpen) {
      const query = value.slice(1);
      const spaceIdx = query.indexOf(' ');
      setFilter(spaceIdx >= 0 ? query.slice(0, spaceIdx) : query);
      slashCommands.openMenu();
    } else if (!value.startsWith('/') && isOpen) {
      closeMenu();
    }
  };

  const handleSelectCommand = (cmd: SlashCommand) => {
    const args = input.slice(input.indexOf(cmd.name) + cmd.name.length + 1).trim();
    executeCommand(cmd, args);
    setInput('');
    if (cmd.id === 'help') setTimeout(() => slashCommands.openMenu(), 0);
  };

  const renderMessage = useCallback((_index: number, message: Message) => (
    <div className="px-4 py-2"><MessageBubble message={message} sessionId={sessionId} /></div>
  ), [sessionId]);

  const GeneratingFooter = useCallback(() => {
    if (!isGenerating) return null;
    return <div className="flex items-center gap-2 text-xs text-dark-muted py-2 px-4"><span className="text-accent-blue animate-spin-frame text-sm">◐</span><span>Thinking...</span></div>;
  }, [isGenerating]);

  const EmptyPlaceholder = useCallback(() => (
    <div className="flex-1 flex items-center justify-center p-4">
      <div className="text-center max-w-md px-4">
        <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-accent-blue/10 flex items-center justify-center">
          <MessageSquare size={32} className="text-accent-blue" />
        </div>
        <h2 className="text-xl font-semibold mb-2">{t('chat.welcome')}</h2>
        <p className="text-dark-muted text-sm">{t('chat.welcomeDesc')}</p>
        <div className="mt-6 flex flex-wrap justify-center gap-2">
          <span className="text-xs px-3 py-1.5 bg-dark-surface rounded-full text-dark-muted">💡 {t('chat.suggestionCode')}</span>
          <span className="text-xs px-3 py-1.5 bg-dark-surface rounded-full text-dark-muted">🔍 {t('chat.suggestionAnalyze')}</span>
          <span className="text-xs px-3 py-1.5 bg-dark-surface rounded-full text-dark-muted">⚡ {t('chat.suggestionAutomate')}</span>
        </div>
        <div className="mt-4 text-xs text-dark-muted space-y-1">
          <div>{t('chat.quickCommands')} <kbd className="px-1 py-0.5 bg-dark-bg rounded border border-dark-border">/</kbd></div>
          <div>{t('chat.refFiles')} <kbd className="px-1 py-0.5 bg-dark-bg rounded border border-dark-border">@</kbd></div>
        </div>
      </div>
    </div>
  ), []);

  const tokenWarn = tokenEstimate > 8000;
  const charCount = input.length;

  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* Messages */}
      <div className="flex-1 min-h-0 overflow-hidden">
        <Virtuoso ref={virtuosoRef} data={messages} itemContent={renderMessage} followOutput="smooth"
          components={{ Footer: GeneratingFooter, EmptyPlaceholder }} style={{ height: '100%' }} />
      </div>

      {/* Toolbar */}
      <div className="shrink-0 border-t border-dark-border bg-dark-surface h-9 flex items-center px-3 gap-2">
        <ContextManager />
        <div className="flex-1" />
        <SessionTaskBoard sessionId={sessionId} />
      </div>

      {/* Plan/Act Mode Banner */}
      {planMode === "plan" && (
        <div className="px-3 pt-1 pb-0.5 bg-accent-purple/10 border-b border-accent-purple/20 flex items-center gap-2 text-xs">
          <span className="text-accent-purple font-bold">{t('plan.readOnlyMode')}</span>
          <span className="text-dark-muted">{t('plan.planModeHint')}</span>
          <button onClick={toggleMode} className="ml-auto px-2 py-0.5 text-[10px] bg-accent-green text-white rounded">{t('plan.switchToAct')}</button>
        </div>
      )}
      {/* Input Area */}
      <div className="px-3 pb-3 pt-2 border-t border-dark-border bg-dark-surface relative shrink-0">
        {/* Slash command menu */}
        <SlashCommandMenu isOpen={isOpen} commands={filteredCommands} selectedIndex={selectedIndex} filter={slashCommands.filter}
          onSelect={handleSelectCommand} onHover={setSelectedIndex} containerRef={containerRef} />

        {/* @ file mention menu */}
        <FileMentionMenu isOpen={showFileMenu} files={fileMenuFiles} selectedIndex={fileMenuIndex} query={fileSearchQuery}
          onSelect={handleFileSelect} onHover={setFileMenuIndex} isSearching={isFileSearching} />

        <div className="flex gap-2 items-end">
          <div className="flex-1 flex flex-col gap-1">
            <textarea
              ref={textareaRef} value={input} onChange={handleChange} onKeyDown={handleKeyDown}
              placeholder={
                hasPendingApproval ? '请先在弹窗中处理审批请求...' :
                planMode === "plan" ? t('plan.planTitle') :
                planMode === "act" ? t('plan.actTitle') :
                t('chat.inputPlaceholder')
              }
              rows={1} disabled={isGenerating || hasPendingApproval} className={`flex-1 bg-dark-bg border rounded-lg px-3 sm:px-4 py-2.5 sm:py-3 text-dark-text placeholder-dark-muted resize-none focus:outline-none focus:border-accent-green text-sm min-h-[40px] max-h-[40vh] transition-colors ${
                hasPendingApproval ? "border-accent-yellow/50 opacity-60" :
                planMode === "plan" ? "border-accent-purple/50" :
                planMode === "act" ? "border-accent-green/50" :
                "border-dark-border"
              }`}
              onCompositionStart={() => { isComposingRef.current = true; }}
              onCompositionEnd={(e) => {
                isComposingRef.current = false;
                const t = e.currentTarget;
                t.style.height = 'auto';
                t.style.height = Math.min(t.scrollHeight, window.innerHeight * 0.4) + 'px';
              }}
              onInput={(e) => {
                if (isComposingRef.current) return;
                const t = e.currentTarget;
                t.style.height = 'auto';
                t.style.height = Math.min(t.scrollHeight, window.innerHeight * 0.4) + 'px';
              }}
            />
            {/* Input info row */}
            <div className="flex items-center gap-3 px-1">
              <span className={`text-[10px] ${tokenWarn ? 'text-accent-red' : 'text-dark-muted'}`}>
                {charCount > 0 && <>{t('chat.charTokens', { chars: charCount, tokens: tokenEstimate })}</>}
              </span>
              {tokenWarn && <span className="text-[10px] text-accent-red font-medium">{t('chat.nearLimit')}</span>}
              <span className="text-[10px] text-dark-muted opacity-60">{t('chat.shiftEnter')}</span>
            </div>
          </div>

          {/* Send/Pause/Resume buttons */}
          {isGenerating ? (
            <div className="flex gap-1.5 shrink-0">
              {isPaused ? (
                <>
                  <button onClick={onResume} className="px-3 py-3 bg-accent-green text-white rounded-lg hover:opacity-90 transition-all flex items-center gap-1.5" title={t('chat.resumeGen')}><Play size={16} /><span className="hidden sm:inline">{t('chat.resume')}</span></button>
                  <button onClick={onStop} className="px-3 py-3 bg-accent-red text-white rounded-lg hover:opacity-90 transition-all flex items-center gap-1.5" title={t('chat.stopGen')}><Square size={16} /><span className="hidden sm:inline">{t('chat.stop')}</span></button>
                </>
              ) : (
                <>
                  <button onClick={onPause} className="px-3 py-3 bg-accent-yellow text-white rounded-lg hover:opacity-90 transition-all flex items-center gap-1.5" title={t('chat.pauseGen')}><Pause size={16} /><span className="hidden sm:inline">{t('chat.pause')}</span></button>
                  <button onClick={onStop} className="px-3 py-3 bg-accent-red text-white rounded-lg hover:opacity-90 transition-all flex items-center gap-1.5" title={t('chat.stopGen')}><Square size={16} /><span className="hidden sm:inline">{t('chat.stop')}</span></button>
                </>
              )}
            </div>
          ) : (
            <button onClick={handleSend} disabled={!input.trim()}
              className="px-4 py-3 text-white rounded-lg hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed transition-all flex items-center gap-2 bg-accent-green">
              <Zap size={16} />
              <span className="hidden sm:inline">{t('chat.sendBtn')}</span>
            </button>
          )}
        </div>
      </div>
    </div>
  );
});

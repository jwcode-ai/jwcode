import { memo, useRef, useCallback, useEffect } from 'react';
import { ScrollText } from 'lucide-react';
import { LogEntry } from '../../types';

interface LogsPanelProps {
  logs: LogEntry[];
  onClear: () => void;
  compact?: boolean;
}

const levelColors: Record<string, string> = {
  info: 'bg-accent-blue',
  warn: 'bg-accent-yellow',
  error: 'bg-accent-red',
  success: 'bg-accent-green',
  tool: 'bg-accent-purple',
};

const levelIcons: Record<string, string> = {
  info: 'ℹ️',
  warn: '⚠️',
  error: '❌',
  success: '✅',
  tool: '🔧',
};

export const LogsPanel = memo(function LogsPanel({ logs, onClear, compact }: LogsPanelProps) {
  // 自动滚动 ref 和状态
  const scrollRef = useRef<HTMLDivElement>(null);
  const isAtBottomRef = useRef(true);

  const checkIsAtBottom = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return true;
    const threshold = 50;
    return el.scrollHeight - el.scrollTop - el.clientHeight < threshold;
  }, []);

  const scrollToBottom = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    el.scrollTop = el.scrollHeight;
  }, []);

  // 监听滚动事件
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const handleScroll = () => {
      isAtBottomRef.current = checkIsAtBottom();
    };
    el.addEventListener('scroll', handleScroll, { passive: true });
    return () => el.removeEventListener('scroll', handleScroll);
  }, [checkIsAtBottom]);

  // logs 变化时自动滚动到底部
  useEffect(() => {
    if (isAtBottomRef.current) {
      scrollToBottom();
    }
  }, [logs, scrollToBottom]);

  return (
    <div className={`flex flex-col min-h-0 ${compact ? 'flex-1 p-2' : 'flex-1 overflow-hidden p-4'}`}>
      {!compact && (
        <div className="flex items-center justify-between mb-4 shrink-0">
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
      )}

      <div
        ref={scrollRef}
        className="flex-1 overflow-y-auto min-h-0 font-mono text-xs space-y-0.5"
        style={{
          scrollbarWidth: 'thin',
          scrollbarColor: '#30363d transparent',
        }}
      >

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
});



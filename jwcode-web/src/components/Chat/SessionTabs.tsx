import { memo, useState, useRef, useCallback, useEffect } from 'react';
import { SessionTab } from '../../types';
import { X, Clock, MessageSquare } from 'lucide-react';
import { useSessionStore } from '../../stores/sessionStore';
import wsService from '../../services/websocket';

interface SessionTabsProps {
  tabs: SessionTab[];
  activeSessionId: string | null;
  onSwitch: (sessionId: string) => void;
  onClose: (sessionId: string) => void;
  onNew: () => void;
  onRename: (sessionId: string, title: string) => void;
}

export const SessionTabs = memo(function SessionTabs({
  tabs,
  activeSessionId,
  onSwitch,
  onClose,
  onNew: _onNew,
  onRename,
}: SessionTabsProps) {

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState('');
  const editInputRef = useRef<HTMLInputElement>(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const historyRef = useRef<HTMLDivElement>(null);

  const historySessions = useSessionStore((s) => s.historySessions);
  const restoreHistorySession = useSessionStore((s) => s.restoreHistorySession);
  const removeHistorySession = useSessionStore((s) => s.removeHistorySession);

  // 点击外部关闭历史下拉
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (historyRef.current && !historyRef.current.contains(e.target as Node)) {
        setHistoryOpen(false);
      }
    };
    if (historyOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => document.removeEventListener('mousedown', handleClickOutside);
    }
  }, [historyOpen]);

  const handleDoubleClick = useCallback((tab: SessionTab) => {
    setEditingId(tab.id);
    setEditValue(tab.title);
    setTimeout(() => editInputRef.current?.select(), 50);
  }, []);

  const handleRenameConfirm = useCallback(() => {
    if (editingId && editValue.trim()) {
      onRename(editingId, editValue.trim());
    }
    setEditingId(null);
  }, [editingId, editValue, onRename]);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleRenameConfirm();
    } else if (e.key === 'Escape') {
      setEditingId(null);
    }
  }, [handleRenameConfirm]);

  const handleRestoreHistory = useCallback((sessionId: string) => {
    restoreHistorySession(sessionId);
    wsService.setSessionId(sessionId);
    setHistoryOpen(false);
  }, [restoreHistorySession]);

  return (
    <div className="flex items-center gap-1 px-3 py-1.5 bg-dark-surface border-b border-dark-border overflow-x-auto shrink-0">
      {/* Session Tabs */}
      <div className="flex items-center gap-0.5 flex-1 overflow-x-auto">
        {tabs.map((tab) => {
          const isActive = tab.id === activeSessionId;
          const isEditing = editingId === tab.id;

          return (
            <div
              key={tab.id}
              onClick={() => !isEditing && onSwitch(tab.id)}
              onDoubleClick={() => handleDoubleClick(tab)}
              className={`
                group flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs cursor-pointer
                transition-all shrink-0 max-w-[160px]
                ${isActive
                  ? 'bg-accent-blue/10 text-accent-blue border border-accent-blue/30'
                  : 'text-dark-muted hover:text-dark-text hover:bg-dark-hover border border-transparent'
                }
              `}
            >
              {/* Edit mode */}
              {isEditing ? (
                <input
                  ref={editInputRef}
                  value={editValue}
                  onChange={(e) => setEditValue(e.target.value)}
                  onBlur={handleRenameConfirm}
                  onKeyDown={handleKeyDown}
                  className="w-full bg-dark-bg border border-accent-blue rounded px-1 py-0.5 text-xs text-dark-text outline-none"
                  onClick={(e) => e.stopPropagation()}
                  autoFocus
                />
              ) : (
                <>
                  <span className="truncate">{tab.title}</span>
                  <span className="text-[10px] opacity-0 group-hover:opacity-100 transition-opacity">
                    {tab.createdAt ? '📝' : ''}
                  </span>
                  {/* Close button */}
                  {tabs.length > 1 && (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onClose(tab.id);
                      }}
                      className="opacity-0 group-hover:opacity-100 hover:bg-dark-hover rounded p-0.5 transition-all"
                    >
                      <X size={12} />
                    </button>
                  )}
                </>
              )}
            </div>
          );
        })}
      </div>

      {/* 历史会话按钮 */}
      {historySessions.length > 0 && (
        <div className="relative shrink-0" ref={historyRef}>
          <button
            onClick={() => setHistoryOpen(!historyOpen)}
            className={`flex items-center gap-1 px-2 py-1 rounded-md text-xs transition-all
              ${historyOpen
                ? 'bg-accent-blue/10 text-accent-blue border border-accent-blue/30'
                : 'text-dark-muted hover:text-dark-text hover:bg-dark-hover border border-transparent'
              }`}
            title="历史会话"
          >
            <Clock size={14} />
            <span className="text-[10px]">{historySessions.length}</span>
          </button>

          {/* 历史会话下拉菜单 */}
          {historyOpen && (
            <div className="absolute top-full right-0 mt-1 w-64 bg-dark-surface border border-dark-border rounded-lg shadow-xl z-50 max-h-[300px] overflow-y-auto custom-scrollbar">
              <div className="px-3 py-2 text-[11px] font-semibold text-dark-muted border-b border-dark-border">
                历史会话 ({historySessions.length})
              </div>
              {historySessions.map((h) => (
                <div
                  key={h.id}
                  className="flex items-center gap-2 px-3 py-2 text-xs cursor-pointer hover:bg-dark-hover transition-colors group"
                  onClick={() => handleRestoreHistory(h.id)}
                >
                  <MessageSquare size={12} className="text-dark-muted shrink-0" />
                  <span className="truncate flex-1 text-dark-text">{h.title}</span>
                  <span className="text-[9px] text-dark-muted shrink-0">
                    {new Date(h.createdAt).toLocaleDateString('zh-CN', {
                      month: '2-digit', day: '2-digit',
                      hour: '2-digit', minute: '2-digit',
                    })}
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
          )}
        </div>
      )}
    </div>
  );

});

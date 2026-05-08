import { memo, useState, useRef, useCallback } from 'react';
import { SessionTab } from '../../types';
import { Plus, X } from 'lucide-react';

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
  onNew,
  onRename,
}: SessionTabsProps) {

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState('');
  const editInputRef = useRef<HTMLInputElement>(null);

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

      {/* Actions */}
      <div className="flex items-center gap-1 shrink-0">
        {/* New session */}
        <button
          onClick={onNew}
          className="p-1 rounded-md text-dark-muted hover:text-dark-text hover:bg-dark-hover transition-all"
          title="新建会话"
        >
          <Plus size={14} />
        </button>
      </div>
    </div>
  );

});

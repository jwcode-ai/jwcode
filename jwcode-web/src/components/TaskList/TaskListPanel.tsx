import { useState, useEffect, useCallback, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { X } from 'lucide-react';
import { useSessionStore } from '../../stores/sessionStore';
import { api } from '../../services/api';
import type { SessionTask } from '../../types';

/**
 * TaskListPanel — per-session task list with backend sync.
 * Extracted from App.tsx for better maintainability.
 */
export function TaskListPanel() {
  const { t } = useTranslation();
  const sid = useSessionStore((s) => s.activeSessionId);
  const tasks = sid ? (useSessionStore((s) => s.tasksBySession[sid]) || []) : [];
  const {
    addSessionTask,
    toggleSessionTask,
    removeSessionTask,
    updateSessionTask,
    setSessionTasks,
  } = useSessionStore();

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

import { memo, useState, useMemo } from 'react';
import { ChevronDown, ChevronUp, ListChecks } from 'lucide-react';
import { SessionTask } from '../../types';
import { useSessionStore } from '../../stores/sessionStore';

const AGENT_ICONS: Record<string, string> = {
  coder: '💻',
  debug: '🐛',
  explore: '🔍',
  architect: '🏗️',
  test: '🧪',
  reviewer: '👁️',
  doc: '📝',
  default: '⚙️',
};

type StatusKey = 'pending' | 'running' | 'completed' | 'failed' | 'skipped';

const STATUS_CONFIG: Record<StatusKey, { icon: string; color: string; bg: string }> = {
  pending: { icon: '⏳', color: 'text-gray-400', bg: 'bg-gray-500/10' },
  running: { icon: '🔄', color: 'text-blue-400', bg: 'bg-blue-500/10' },
  completed: { icon: '✅', color: 'text-green-400', bg: 'bg-green-500/10' },
  failed: { icon: '❌', color: 'text-red-400', bg: 'bg-red-500/10' },
  skipped: { icon: '⏭️', color: 'text-gray-500', bg: 'bg-gray-500/5' },
};

interface SessionTaskBoardProps {
  sessionId: string;
}

export const SessionTaskBoard = memo(function SessionTaskBoard({ sessionId }: SessionTaskBoardProps) {
  const [isExpanded, setIsExpanded] = useState(false);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);

  const tasks = useSessionStore((s) => s.tasksBySession[sessionId] || []);

  if (tasks.length === 0) return null;

  const stats = useMemo(() => {
    const total = tasks.length;
    const completed = tasks.filter((t) => t.planStatus === 'completed' || t.completed).length;
    const running = tasks.filter((t) => t.planStatus === 'running').length;
    const failed = tasks.filter((t) => t.planStatus === 'failed').length;
    const progress = total > 0 ? Math.round((completed / total) * 100) : 0;
    return { total, completed, running, failed, progress };
  }, [tasks]);

  const grouped = useMemo(() => {
    const groups: Record<string, SessionTask[]> = {
      pending: [], running: [], completed: [], failed: [],
    };
    tasks.forEach((t) => {
      const status = t.planStatus || 'pending';
      const key = status === 'skipped' ? 'pending' : status;
      if (groups[key]) groups[key].push(t);
    });
    return groups as { pending: SessionTask[]; running: SessionTask[]; completed: SessionTask[]; failed: SessionTask[] };
  }, [tasks]);

  const formatDuration = (start?: number, end?: number) => {
    if (!start) return '';
    const ms = (end || Date.now()) - start;
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(1)}s`;
  };

  return (
    <div className="border-t border-dark-border bg-dark-surface/50">
      <button
        onClick={() => setIsExpanded(!isExpanded)}
        className="w-full flex items-center justify-between px-4 py-2 hover:bg-dark-hover transition-colors"
      >
        <div className="flex items-center gap-2">
          <ListChecks size={14} className="text-accent-blue" />
          <span className="text-xs font-medium text-dark-text">任务看板</span>
          <span className="text-[10px] text-dark-muted">
            {stats.completed}/{stats.total}
          </span>
          <div className="w-20 h-1.5 bg-dark-bg rounded-full overflow-hidden">
            <div
              className={`h-full rounded-full transition-all duration-500 ${
                stats.running > 0 ? 'bg-accent-blue' : stats.failed > 0 ? 'bg-accent-red' : 'bg-accent-green'
              }`}
              style={{ width: `${stats.progress}%` }}
            />
          </div>
          {stats.running > 0 && (
            <span className="w-1.5 h-1.5 bg-accent-blue rounded-full animate-pulse" />
          )}
        </div>
        <div className="flex items-center gap-2">
          {isExpanded ? <ChevronUp size={14} className="text-dark-muted" /> : <ChevronDown size={14} className="text-dark-muted" />}
        </div>
      </button>

      {isExpanded && (
        <div className="px-4 pb-3 max-h-[240px] overflow-y-auto space-y-1.5">
          {grouped.running.map((task) => (
            <TaskItem key={task.id} task={task} isSelected={task.id === selectedTaskId}
              onClick={() => setSelectedTaskId(selectedTaskId === task.id ? null : task.id)}
              formatDuration={formatDuration} />
          ))}
          {grouped.pending.slice(0, 5).map((task) => (
            <TaskItem key={task.id} task={task} isSelected={task.id === selectedTaskId}
              onClick={() => setSelectedTaskId(selectedTaskId === task.id ? null : task.id)}
              formatDuration={formatDuration} />
          ))}
          {grouped.completed.slice(-3).map((task) => (
            <TaskItem key={task.id} task={task} isSelected={task.id === selectedTaskId}
              onClick={() => setSelectedTaskId(selectedTaskId === task.id ? null : task.id)}
              formatDuration={formatDuration} />
          ))}
          {grouped.failed.map((task) => (
            <TaskItem key={task.id} task={task} isSelected={task.id === selectedTaskId}
              onClick={() => setSelectedTaskId(selectedTaskId === task.id ? null : task.id)}
              formatDuration={formatDuration} />
          ))}
          {grouped.pending.length > 5 && (
            <div className="text-[10px] text-dark-muted text-center">还有 {grouped.pending.length - 5} 个待处理任务...</div>
          )}
          {grouped.completed.length > 3 && (
            <div className="text-[10px] text-dark-muted text-center">还有 {grouped.completed.length - 3} 个已完成任务...</div>
          )}
        </div>
      )}
    </div>
  );
});

interface TaskItemProps {
  task: SessionTask;
  isSelected: boolean;
  onClick: () => void;
  formatDuration: (start?: number, end?: number) => string;
}

const TaskItem = memo(function TaskItem({ task, isSelected, onClick, formatDuration }: TaskItemProps) {
  const status: StatusKey = (task.planStatus as StatusKey) || 'pending';
  const statusCfg = STATUS_CONFIG[status];
  const agentIcon = AGENT_ICONS[task.agentType || 'default'] || AGENT_ICONS.default;

  return (
    <div onClick={onClick}
      className={`rounded-lg border p-2 cursor-pointer transition-all ${statusCfg.bg} border-dark-border
        ${isSelected ? 'ring-1 ring-accent-blue' : 'hover:border-dark-muted/30'}
        ${status === 'running' ? 'border-l-2 border-l-accent-blue' : 'border-l-2 border-l-transparent'}`}
    >
      <div className="flex items-center gap-2">
        <span className="text-sm">{agentIcon}</span>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5">
            <span className="text-xs text-dark-text truncate">{task.title}</span>
            <span className={`text-[10px] ${statusCfg.color} shrink-0`}>{statusCfg.icon}</span>
          </div>
          <div className="flex items-center gap-2 text-[10px] text-dark-muted">
            <span>{task.agentType || 'task'}</span>
            {task.startedAt && <span>⏱ {formatDuration(task.startedAt, task.completedAt)}</span>}
          </div>
        </div>
      </div>
      {status === 'running' && task.progress !== undefined && (
        <div className="mt-1.5">
          <div className="w-full h-1 bg-dark-bg rounded-full overflow-hidden">
            <div className="h-full bg-accent-blue rounded-full transition-all duration-500"
              style={{ width: `${task.progress}%` }} />
          </div>
        </div>
      )}
      {isSelected && (
        <div className="mt-2 pt-2 border-t border-dark-border space-y-1">
          {task.description && <p className="text-[10px] text-dark-muted">{task.description}</p>}
          {task.planStatus === 'failed' && task.error && <p className="text-[10px] text-red-400">❌ {task.error}</p>}
          {task.result && <p className="text-[10px] text-green-400">✅ {task.result}</p>}
          {task.dependencies && task.dependencies.length > 0 && (
            <p className="text-[10px] text-dark-muted">🔗 依赖 {task.dependencies.length} 个任务</p>
          )}
        </div>
      )}
    </div>
  );
});

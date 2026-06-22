import { memo, useMemo, useState } from 'react';
import { AlertCircle, CheckCircle2, ChevronDown, ChevronUp, Circle, Clock3, ListChecks, Loader2 } from 'lucide-react';
import { useWorkflowStore, type BlackboardTask, type BlackboardTaskStatus } from '../../stores/workflowStore';

type GroupedTasks = Record<'running' | 'pending' | 'failed' | 'completed', BlackboardTask[]>;

interface SessionTaskBoardProps {
  sessionId: string;
}

const STATUS_CONFIG: Record<BlackboardTaskStatus, { color: string; bg: string; label: string }> = {
  pending: { color: 'text-gray-400', bg: 'bg-gray-500/10', label: 'Pending' },
  running: { color: 'text-blue-400', bg: 'bg-blue-500/10', label: 'Running' },
  completed: { color: 'text-green-400', bg: 'bg-green-500/10', label: 'Done' },
  failed: { color: 'text-red-400', bg: 'bg-red-500/10', label: 'Failed' },
  skipped: { color: 'text-gray-500', bg: 'bg-gray-500/5', label: 'Skipped' },
};

const StatusIcon = ({ status }: { status: BlackboardTaskStatus }) => {
  if (status === 'running') return <Loader2 size={12} className="animate-spin text-accent-blue" />;
  if (status === 'completed') return <CheckCircle2 size={12} className="text-accent-green" />;
  if (status === 'failed') return <AlertCircle size={12} className="text-accent-red" />;
  if (status === 'skipped') return <Clock3 size={12} className="text-dark-muted" />;
  return <Circle size={12} className="text-dark-muted" />;
};

export const SessionTaskBoard = memo(function SessionTaskBoard({ sessionId }: SessionTaskBoardProps) {
  const [isExpanded, setIsExpanded] = useState(false);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const tasks = useWorkflowStore((state) => state.getTasksForSession(sessionId));

  const stats = useMemo(() => {
    const total = tasks.length;
    const completed = tasks.filter((task) => task.status === 'completed').length;
    const running = tasks.filter((task) => task.status === 'running').length;
    const failed = tasks.filter((task) => task.status === 'failed').length;
    const progress = total > 0 ? Math.round((completed / total) * 100) : 0;
    return { total, completed, running, failed, progress };
  }, [tasks]);

  const grouped = useMemo<GroupedTasks>(() => {
    const groups: GroupedTasks = { pending: [], running: [], completed: [], failed: [] };
    tasks.forEach((task) => {
      const key = task.status === 'skipped' ? 'pending' : task.status;
      groups[key].push(task);
    });
    return groups;
  }, [tasks]);

  if (tasks.length === 0) return null;

  const orderedTasks = [
    ...grouped.running,
    ...grouped.failed,
    ...grouped.pending.slice(0, 5),
    ...grouped.completed.slice(-3),
  ];

  return (
    <div className="min-w-[220px] border border-dark-border rounded bg-dark-bg/70">
      <button
        onClick={() => setIsExpanded(!isExpanded)}
        className="w-full flex items-center justify-between px-2 py-1.5 hover:bg-dark-hover transition-colors"
      >
        <div className="flex items-center gap-2 min-w-0">
          <ListChecks size={14} className="text-accent-blue shrink-0" />
          <span className="text-xs font-medium text-dark-text">Task Board</span>
          <span className="text-[10px] text-dark-muted shrink-0">
            {stats.completed}/{stats.total}
          </span>
          <div className="w-16 h-1.5 bg-dark-surface rounded-full overflow-hidden">
            <div
              className={`h-full rounded-full transition-all duration-500 ${
                stats.running > 0 ? 'bg-accent-blue' : stats.failed > 0 ? 'bg-accent-red' : 'bg-accent-green'
              }`}
              style={{ width: `${stats.progress}%` }}
            />
          </div>
          {stats.running > 0 && <span className="w-1.5 h-1.5 bg-accent-blue rounded-full animate-pulse shrink-0" />}
        </div>
        {isExpanded ? <ChevronUp size={14} className="text-dark-muted" /> : <ChevronDown size={14} className="text-dark-muted" />}
      </button>

      {isExpanded && (
        <div className="px-2 pb-2 max-h-[260px] overflow-y-auto space-y-1.5">
          {orderedTasks.map((task) => (
            <TaskItem
              key={task.id}
              task={task}
              isSelected={task.id === selectedTaskId}
              onClick={() => setSelectedTaskId(selectedTaskId === task.id ? null : task.id)}
            />
          ))}
          {grouped.pending.length > 5 && (
            <div className="text-[10px] text-dark-muted text-center">
              {grouped.pending.length - 5} more pending tasks
            </div>
          )}
          {grouped.completed.length > 3 && (
            <div className="text-[10px] text-dark-muted text-center">
              {grouped.completed.length - 3} more completed tasks
            </div>
          )}
        </div>
      )}
    </div>
  );
});

interface TaskItemProps {
  task: BlackboardTask;
  isSelected: boolean;
  onClick: () => void;
}

const formatDuration = (start?: number, end?: number) => {
  if (!start) return '';
  const ms = (end || Date.now()) - start;
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
};

const TaskItem = memo(function TaskItem({ task, isSelected, onClick }: TaskItemProps) {
  const statusCfg = STATUS_CONFIG[task.status];

  return (
    <div
      onClick={onClick}
      className={`rounded border p-2 cursor-pointer transition-all ${statusCfg.bg} border-dark-border
        ${isSelected ? 'ring-1 ring-accent-blue' : 'hover:border-dark-muted/40'}
        ${task.status === 'running' ? 'border-l-2 border-l-accent-blue' : 'border-l-2 border-l-transparent'}`}
    >
      <div className="flex items-center gap-2">
        <StatusIcon status={task.status} />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5">
            <span className="text-xs text-dark-text truncate">{task.title}</span>
            <span className={`text-[10px] ${statusCfg.color} shrink-0`}>{statusCfg.label}</span>
          </div>
          <div className="flex items-center gap-2 text-[10px] text-dark-muted">
            <span>{task.agentType || task.source}</span>
            {task.startedAt && <span>{formatDuration(task.startedAt, task.completedAt)}</span>}
          </div>
        </div>
      </div>
      {task.status === 'running' && task.progress !== undefined && (
        <div className="mt-1.5">
          <div className="w-full h-1 bg-dark-bg rounded-full overflow-hidden">
            <div className="h-full bg-accent-blue rounded-full transition-all duration-500" style={{ width: `${task.progress}%` }} />
          </div>
        </div>
      )}
      {isSelected && (
        <div className="mt-2 pt-2 border-t border-dark-border space-y-1">
          {task.description && <p className="text-[10px] text-dark-muted">{task.description}</p>}
          {task.error && <p className="text-[10px] text-red-400">{task.error}</p>}
          {task.result && <p className="text-[10px] text-green-400">{task.result}</p>}
          {task.dependencies && task.dependencies.length > 0 && (
            <p className="text-[10px] text-dark-muted">{task.dependencies.length} dependencies</p>
          )}
        </div>
      )}
    </div>
  );
});

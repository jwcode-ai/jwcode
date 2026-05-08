import { memo, useState, useMemo } from 'react';
import { ChevronDown, ChevronUp, ListChecks } from 'lucide-react';
import { PlanTask } from '../../types';
import { usePlanStore } from '../../stores/planStore';

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

const STATUS_CONFIG = {
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

  const plan = usePlanStore((s) => s.plansBySession[sessionId]);
  const phase = usePlanStore((s) => s.planPhasesBySession[sessionId] || 'idle');

  const tasks = plan?.tasks || [];
  const hasActivePlan = phase !== 'idle' && tasks.length > 0;

  // 统计
  const stats = useMemo(() => {
    const total = tasks.length;
    const completed = tasks.filter((t) => t.status === 'completed').length;
    const running = tasks.filter((t) => t.status === 'running').length;
    const failed = tasks.filter((t) => t.status === 'failed').length;
    const progress = total > 0 ? Math.round((completed / total) * 100) : 0;
    return { total, completed, running, failed, progress };
  }, [tasks]);

  // 按状态分组
  const grouped = useMemo(() => {
    const groups: Record<string, PlanTask[]> = {
      pending: [],
      running: [],
      completed: [],
      failed: [],
    };
    tasks.forEach((t) => {
      const key = t.status === 'skipped' ? 'pending' : t.status;
      if (groups[key]) groups[key].push(t);
    });
    return groups as { pending: PlanTask[]; running: PlanTask[]; completed: PlanTask[]; failed: PlanTask[] };
  }, [tasks]);

  if (!hasActivePlan) return null;

  const formatDuration = (start?: number, end?: number) => {
    if (!start) return '';
    const ms = (end || Date.now()) - start;
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(1)}s`;
  };

  return (
    <div className="border-t border-dark-border bg-dark-surface/50">
      {/* Header - 点击展开/折叠 */}
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
          {/* 进度条 */}
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
          {phase === 'planning' && (
            <span className="text-[10px] text-accent-blue animate-pulse">分析中...</span>
          )}
          {isExpanded ? <ChevronUp size={14} className="text-dark-muted" /> : <ChevronDown size={14} className="text-dark-muted" />}
        </div>
      </button>

      {/* Expanded content */}
      {isExpanded && (
        <div className="px-4 pb-3 space-y-1.5 max-h-[240px] overflow-y-auto">
          {/* 按状态分组显示 */}
          {phase === 'planning' && tasks.length === 0 && (
            <div className="text-[11px] text-dark-muted text-center py-2">
              AI 正在分析任务...
            </div>
          )}

          {/* Running tasks first */}
          {grouped.running.map((task) => (
            <TaskItem
              key={task.id}
              task={task}
              isSelected={task.id === selectedTaskId}
              onClick={() => setSelectedTaskId(selectedTaskId === task.id ? null : task.id)}
              formatDuration={formatDuration}
            />
          ))}

          {/* Pending tasks */}
          {grouped.pending.slice(0, 5).map((task) => (
            <TaskItem
              key={task.id}
              task={task}
              isSelected={task.id === selectedTaskId}
              onClick={() => setSelectedTaskId(selectedTaskId === task.id ? null : task.id)}
              formatDuration={formatDuration}
            />
          ))}

          {/* Completed tasks (show only last 3) */}
          {grouped.completed.slice(-3).map((task) => (
            <TaskItem
              key={task.id}
              task={task}
              isSelected={task.id === selectedTaskId}
              onClick={() => setSelectedTaskId(selectedTaskId === task.id ? null : task.id)}
              formatDuration={formatDuration}
            />
          ))}

          {/* Failed tasks */}
          {grouped.failed.map((task) => (
            <TaskItem
              key={task.id}
              task={task}
              isSelected={task.id === selectedTaskId}
              onClick={() => setSelectedTaskId(selectedTaskId === task.id ? null : task.id)}
              formatDuration={formatDuration}
            />
          ))}

          {/* 如果任务太多，显示省略 */}
          {grouped.pending.length > 5 && (
            <div className="text-[10px] text-dark-muted text-center">
              还有 {grouped.pending.length - 5} 个待处理任务...
            </div>
          )}
          {grouped.completed.length > 3 && (
            <div className="text-[10px] text-dark-muted text-center">
              还有 {grouped.completed.length - 3} 个已完成任务...
            </div>
          )}
        </div>
      )}
    </div>
  );
});

// 单个任务项
interface TaskItemProps {
  task: PlanTask;
  isSelected: boolean;
  onClick: () => void;
  formatDuration: (start?: number, end?: number) => string;
}

const TaskItem = memo(function TaskItem({ task, isSelected, onClick, formatDuration }: TaskItemProps) {
  const statusCfg = STATUS_CONFIG[task.status] || STATUS_CONFIG.pending;
  const agentIcon = AGENT_ICONS[task.agentType] || AGENT_ICONS.default;

  return (
    <div
      onClick={onClick}
      className={`
        rounded-lg border p-2 cursor-pointer transition-all
        ${statusCfg.bg} border-dark-border
        ${isSelected ? 'ring-1 ring-accent-blue' : 'hover:border-dark-muted/30'}
        ${task.status === 'running' ? 'border-l-2 border-l-accent-blue' : 'border-l-2 border-l-transparent'}
      `}
    >
      <div className="flex items-center gap-2">
        <span className="text-sm">{agentIcon}</span>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5">
            <span className="text-xs text-dark-text truncate">{task.title}</span>
            <span className={`text-[10px] ${statusCfg.color} shrink-0`}>{statusCfg.icon}</span>
          </div>
          <div className="flex items-center gap-2 text-[10px] text-dark-muted">
            <span>{task.agentType}</span>
            {task.startedAt && <span>⏱ {formatDuration(task.startedAt, task.completedAt)}</span>}
          </div>
        </div>
      </div>

      {/* Progress bar for running tasks */}
      {task.status === 'running' && task.progress !== undefined && (
        <div className="mt-1.5">
          <div className="w-full h-1 bg-dark-bg rounded-full overflow-hidden">
            <div
              className="h-full bg-accent-blue rounded-full transition-all duration-500"
              style={{ width: `${task.progress}%` }}
            />
          </div>
        </div>
      )}

      {/* Expanded detail */}
      {isSelected && (
        <div className="mt-2 pt-2 border-t border-dark-border space-y-1">
          {task.description && (
            <p className="text-[10px] text-dark-muted">{task.description}</p>
          )}
          {task.status === 'failed' && task.error && (
            <p className="text-[10px] text-red-400">❌ {task.error}</p>
          )}
          {task.result && (
            <p className="text-[10px] text-green-400">✅ {task.result}</p>
          )}
          {task.dependencies.length > 0 && (
            <p className="text-[10px] text-dark-muted">🔗 依赖 {task.dependencies.length} 个任务</p>
          )}
        </div>
      )}
    </div>
  );
});

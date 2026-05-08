import { memo } from 'react';
import { PlanTask } from '../../types';

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

const AGENT_COLORS: Record<string, string> = {
  coder: 'border-l-blue-500',
  debug: 'border-l-orange-500',
  explore: 'border-l-purple-500',
  architect: 'border-l-yellow-500',
  test: 'border-l-green-500',
  reviewer: 'border-l-cyan-500',
  doc: 'border-l-pink-500',
  default: 'border-l-gray-500',
};

const STATUS_CONFIG = {
  pending: {
    icon: '⏳',
    bg: 'bg-gray-500/10',
    border: 'border-gray-500/30',
    text: 'text-gray-400',
    label: '等待中',
  },
  running: {
    icon: '🔄',
    bg: 'bg-blue-500/10',
    border: 'border-blue-500/50',
    text: 'text-blue-400',
    label: '执行中',
  },
  completed: {
    icon: '✅',
    bg: 'bg-green-500/10',
    border: 'border-green-500/30',
    text: 'text-green-400',
    label: '已完成',
  },
  failed: {
    icon: '❌',
    bg: 'bg-red-500/10',
    border: 'border-red-500/30',
    text: 'text-red-400',
    label: '失败',
  },
  skipped: {
    icon: '⏭️',
    bg: 'bg-gray-500/5',
    border: 'border-gray-500/20',
    text: 'text-gray-500',
    label: '已跳过',
  },
};

interface TaskCardProps {
  task: PlanTask;
  isActive: boolean;
  onClick: () => void;
}

export const TaskCard = memo(function TaskCard({ task, isActive, onClick }: TaskCardProps) {
  const statusCfg = STATUS_CONFIG[task.status];
  const agentIcon = AGENT_ICONS[task.agentType] || AGENT_ICONS.default;
  const agentColor = AGENT_COLORS[task.agentType] || AGENT_COLORS.default;

  const formatDuration = (start?: number, end?: number) => {
    if (!start) return '';
    const ms = (end || Date.now()) - start;
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(1)}s`;
  };

  return (
    <div
      onClick={onClick}
      className={`
        group relative p-3 rounded-lg border cursor-pointer transition-all
        ${statusCfg.bg} ${statusCfg.border}
        ${isActive ? 'ring-2 ring-blue-500 shadow-lg shadow-blue-500/10 scale-[1.02]' : 'hover:scale-[1.01] hover:shadow-md'}
        border-l-4 ${agentColor}
      `}
    >
      {/* Header */}
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          <span className="text-lg shrink-0">{agentIcon}</span>
          <div className="min-w-0">
            <div className="flex items-center gap-1.5">
              <span className="text-sm font-medium text-dark-text truncate">
                {task.title}
              </span>
              <span className={`text-xs px-1.5 py-0.5 rounded-full ${statusCfg.bg} ${statusCfg.text} shrink-0`}>
                {statusCfg.label}
              </span>
            </div>
            <span className="text-xs text-dark-muted block truncate">
              {task.agentType.charAt(0).toUpperCase() + task.agentType.slice(1)}Agent
            </span>
          </div>
        </div>
        <span className={`text-xs ${statusCfg.text} shrink-0`}>
          {statusCfg.icon}
        </span>
      </div>

      {/* Description */}
      {task.description && (
        <p className="text-xs text-dark-muted mt-1.5 line-clamp-2">
          {task.description}
        </p>
      )}

      {/* Progress bar for running tasks */}
      {task.status === 'running' && task.progress !== undefined && (
        <div className="mt-2">
          <div className="flex items-center justify-between text-xs text-dark-muted mb-1">
            <span>进度</span>
            <span>{task.progress}%</span>
          </div>
          <div className="h-1.5 bg-dark-bg rounded-full overflow-hidden">
            <div
              className="h-full bg-blue-500 rounded-full transition-all duration-500"
              style={{ width: `${task.progress}%` }}
            />
          </div>
        </div>
      )}

      {/* Duration */}
      <div className="flex items-center gap-3 mt-2 text-xs text-dark-muted">
        {task.startedAt && (
          <span>⏱ {formatDuration(task.startedAt, task.completedAt)}</span>
        )}
        {task.dependencies.length > 0 && (
          <span>🔗 依赖 {task.dependencies.length} 个任务</span>
        )}
      </div>

      {/* Error message */}
      {task.status === 'failed' && task.error && (
        <div className="mt-2 p-2 bg-red-500/10 rounded text-xs text-red-400">
          {task.error}
        </div>
      )}
    </div>
  );
});

import { memo, useMemo } from 'react';
import { PlanTask } from '../../types';

interface PlanTimelineProps {
  tasks: PlanTask[];
  activeTaskId: string | null;
  onTaskClick: (task: PlanTask) => void;
}

const STATUS_DOT = {
  pending: 'bg-gray-500',
  running: 'bg-blue-500 animate-pulse',
  completed: 'bg-green-500',
  failed: 'bg-red-500',
  skipped: 'bg-gray-400',
};

const STATUS_LABEL = {
  pending: '⏳ 等待中',
  running: '🔄 进行中...',
  completed: '✅ 完成',
  failed: '❌ 失败',
  skipped: '⏭️ 已跳过',
};

export const PlanTimeline = memo(function PlanTimeline({
  tasks,
  activeTaskId,
  onTaskClick,
}: PlanTimelineProps) {
  const sorted = useMemo(() => {
    return [...tasks].sort((a, b) => {
      // Running tasks first, then by startedAt
      if (a.status === 'running' && b.status !== 'running') return -1;
      if (a.status !== 'running' && b.status === 'running') return 1;
      if (a.startedAt && b.startedAt) return a.startedAt - b.startedAt;
      return 0;
    });
  }, [tasks]);

  const formatTime = (ts?: number) => {
    if (!ts) return '--:--:--';
    const d = new Date(ts);
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`;
  };

  const formatDuration = (start?: number, end?: number) => {
    if (!start) return '';
    const ms = (end || Date.now()) - start;
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(1)}s`;
  };

  return (
    <div className="bg-dark-surface rounded-lg border border-dark-border">
      <div className="px-4 py-3 border-b border-dark-border">
        <h3 className="text-sm font-medium text-dark-text">📊 执行时间线</h3>
      </div>
      <div className="p-4 max-h-[300px] overflow-y-auto">
        {sorted.length === 0 ? (
          <div className="text-center py-8 text-dark-muted text-sm">
            暂无任务记录
          </div>
        ) : (
          <div className="relative">
            {/* Timeline vertical line */}
            <div className="absolute left-[19px] top-2 bottom-2 w-0.5 bg-dark-border" />

            <div className="space-y-3">
              {sorted.map((task) => {
                const isActive = task.id === activeTaskId;

                return (
                  <div
                    key={task.id}
                    onClick={() => onTaskClick(task)}
                    className={`
                      relative flex items-start gap-3 cursor-pointer group
                      ${isActive ? 'scale-[1.02]' : ''}
                      transition-all
                    `}
                  >
                    {/* Timeline dot */}
                    <div className="relative z-10 shrink-0">
                      <div className={`w-[10px] h-[10px] rounded-full mt-1.5 ${STATUS_DOT[task.status]} ${isActive ? 'ring-2 ring-blue-500/50 ring-offset-2 ring-offset-dark-surface' : ''}`} />
                    </div>

                    {/* Content */}
                    <div className={`
                      flex-1 p-3 rounded-lg border transition-all
                      ${isActive
                        ? 'bg-blue-500/5 border-blue-500/30 shadow-sm'
                        : 'bg-dark-bg/50 border-dark-border group-hover:bg-dark-bg group-hover:border-dark-muted/30'
                      }
                    `}>
                      <div className="flex items-start justify-between gap-2">
                        <div className="min-w-0">
                          <div className="flex items-center gap-2">
                            <span className={`text-sm font-medium ${isActive ? 'text-blue-400' : 'text-dark-text'}`}>
                              {task.title}
                            </span>
                            <span className="text-xs text-dark-muted">
                              {STATUS_LABEL[task.status]}
                            </span>
                          </div>
                          <div className="flex items-center gap-3 mt-1">
                            <span className="text-xs text-dark-muted">
                              {task.agentType.charAt(0).toUpperCase() + task.agentType.slice(1)}Agent
                            </span>
                            {task.startedAt && (
                              <span className="text-xs text-dark-muted">
                                ⏱ {formatDuration(task.startedAt, task.completedAt)}
                              </span>
                            )}
                          </div>
                        </div>
                        <span className="text-xs text-dark-muted shrink-0 font-mono">
                          {formatTime(task.startedAt || task.completedAt)}
                        </span>
                      </div>

                      {/* Progress bar for running */}
                      {task.status === 'running' && task.progress !== undefined && (
                        <div className="mt-2">
                          <div className="h-1 bg-dark-border rounded-full overflow-hidden">
                            <div
                              className="h-full bg-blue-500 rounded-full transition-all duration-500"
                              style={{ width: `${task.progress}%` }}
                            />
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>
    </div>
  );
});

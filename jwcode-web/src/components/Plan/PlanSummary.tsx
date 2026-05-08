import { memo, useMemo } from 'react';
import { Plan } from '../../types';

interface PlanSummaryProps {
  plan: Plan;
  onNewPlan: () => void;
}

export const PlanSummary = memo(function PlanSummary({ plan, onNewPlan }: PlanSummaryProps) {
  const stats = useMemo(() => {
    const total = plan.tasks.length;
    const completed = plan.tasks.filter((t) => t.status === 'completed').length;
    const failed = plan.tasks.filter((t) => t.status === 'failed').length;
    const skipped = plan.tasks.filter((t) => t.status === 'skipped').length;
    const totalDuration = plan.tasks.reduce((acc, t) => {
      if (t.startedAt && t.completedAt) return acc + (t.completedAt - t.startedAt);
      return acc;
    }, 0);
    return { total, completed, failed, skipped, totalDuration };
  }, [plan.tasks]);

  const formatDuration = (ms: number) => {
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    const m = Math.floor(ms / 60000);
    const s = Math.round((ms % 60000) / 1000);
    return `${m}m ${s}s`;
  };

  const successRate = stats.total > 0
    ? Math.round((stats.completed / stats.total) * 100)
    : 0;

  return (
    <div className="bg-dark-surface rounded-lg border border-dark-border">
      {/* Header */}
      <div className="px-4 py-3 border-b border-dark-border">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-lg">
              {stats.failed === 0 ? '✅' : '⚠️'}
            </span>
            <h3 className="text-sm font-medium text-dark-text">
              Plan 执行{stats.failed === 0 ? '完成' : '完成（有失败）'}
            </h3>
          </div>
          <button
            onClick={onNewPlan}
            className="px-3 py-1.5 text-xs bg-accent-blue text-white rounded-lg hover:opacity-90 transition-opacity"
          >
            新建 Plan
          </button>
        </div>
      </div>

      {/* Stats */}
      <div className="px-4 py-4 border-b border-dark-border">
        <div className="grid grid-cols-4 gap-4">
          <div className="text-center">
            <div className="text-2xl font-bold text-dark-text">{stats.total}</div>
            <div className="text-xs text-dark-muted mt-1">总任务</div>
          </div>
          <div className="text-center">
            <div className="text-2xl font-bold text-green-400">{stats.completed}</div>
            <div className="text-xs text-dark-muted mt-1">成功</div>
          </div>
          <div className="text-center">
            <div className="text-2xl font-bold text-red-400">{stats.failed}</div>
            <div className="text-xs text-dark-muted mt-1">失败</div>
          </div>
          <div className="text-center">
            <div className="text-2xl font-bold text-dark-text">{successRate}%</div>
            <div className="text-xs text-dark-muted mt-1">成功率</div>
          </div>
        </div>

        {/* Success rate bar */}
        <div className="mt-3">
          <div className="h-2 bg-dark-bg rounded-full overflow-hidden">
            <div
              className={`h-full rounded-full transition-all duration-1000 ${
                successRate >= 80 ? 'bg-green-500' :
                successRate >= 50 ? 'bg-yellow-500' : 'bg-red-500'
              }`}
              style={{ width: `${successRate}%` }}
            />
          </div>
        </div>

        {stats.totalDuration > 0 && (
          <div className="mt-3 text-xs text-dark-muted text-center">
            总耗时: {formatDuration(stats.totalDuration)}
            {' · '}
            平均每任务: {formatDuration(stats.totalDuration / stats.total)}
          </div>
        )}
      </div>

      {/* Goal */}
      <div className="px-4 py-3 border-b border-dark-border">
        <h4 className="text-xs font-medium text-dark-muted mb-1.5">🎯 目标</h4>
        <p className="text-sm text-dark-text">{plan.goal}</p>
      </div>

      {/* Task Results */}
      <div className="px-4 py-3">
        <h4 className="text-xs font-medium text-dark-muted mb-2">📋 详细结果</h4>
        <div className="space-y-2 max-h-[300px] overflow-y-auto">
          {plan.tasks.map((task, i) => (
            <div
              key={task.id}
              className={`
                p-3 rounded-lg border text-sm
                ${task.status === 'completed' ? 'bg-green-500/5 border-green-500/20' :
                  task.status === 'failed' ? 'bg-red-500/5 border-red-500/20' :
                  task.status === 'skipped' ? 'bg-gray-500/5 border-gray-500/20' :
                  'bg-dark-bg/50 border-dark-border'}
              `}
            >
              <div className="flex items-start gap-2">
                <span className="shrink-0 mt-0.5">
                  {task.status === 'completed' ? '✅' :
                   task.status === 'failed' ? '❌' :
                   task.status === 'skipped' ? '⏭️' : '⏳'}
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-dark-text">
                      {i + 1}. {task.title}
                    </span>
                    <span className="text-xs text-dark-muted">
                      ({task.agentType})
                    </span>
                  </div>
                  {task.result && (
                    <p className="text-xs text-green-400 mt-1">{task.result}</p>
                  )}
                  {task.error && (
                    <p className="text-xs text-red-400 mt-1">{task.error}</p>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
});

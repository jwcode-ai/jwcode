import { memo, useMemo } from 'react';
import { PlanTask } from '../../types';
import { TaskCard } from './TaskCard';

interface KanbanBoardProps {
  tasks: PlanTask[];
  activeTaskId: string | null;
  onTaskClick: (task: PlanTask) => void;
}

const COLUMNS = [
  { id: 'pending' as const, title: '📋 待处理', color: 'border-t-gray-500' },
  { id: 'running' as const, title: '🔄 执行中', color: 'border-t-blue-500' },
  { id: 'completed' as const, title: '✅ 已完成', color: 'border-t-green-500' },
  { id: 'failed' as const, title: '❌ 失败', color: 'border-t-red-500' },
];

export const KanbanBoard = memo(function KanbanBoard({
  tasks,
  activeTaskId,
  onTaskClick,
}: KanbanBoardProps) {
  const grouped = useMemo(() => {
    const groups: { pending: PlanTask[]; running: PlanTask[]; completed: PlanTask[]; failed: PlanTask[] } = {
      pending: [],
      running: [],
      completed: [],
      failed: [],
    };
    // skipped 归入 pending
    tasks.forEach((t) => {
      const key = t.status === 'skipped' ? 'pending' : t.status;
      if (key === 'pending' || key === 'running' || key === 'completed' || key === 'failed') {
        groups[key].push(t);
      } else {
        groups.pending.push(t);
      }
    });
    return groups;
  }, [tasks]);

  return (
    <div className="grid grid-cols-4 gap-3">
      {COLUMNS.map((col) => {
        const colTasks = grouped[col.id];
        return (
          <div
            key={col.id}
            className={`bg-dark-surface rounded-lg border border-dark-border border-t-2 ${col.color}`}
          >
            {/* Column Header */}
            <div className="px-3 py-2 border-b border-dark-border">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-medium text-dark-text">{col.title}</h3>
                <span className="text-xs text-dark-muted bg-dark-bg px-2 py-0.5 rounded-full">
                  {colTasks.length}
                </span>
              </div>
            </div>

            {/* Column Content */}
            <div className="p-2 space-y-2 min-h-[120px] max-h-[400px] overflow-y-auto">
              {colTasks.length === 0 ? (
                <div className="flex items-center justify-center h-20 text-xs text-dark-muted">
                  暂无任务
                </div>
              ) : (
                colTasks.map((task) => (
                  <TaskCard
                    key={task.id}
                    task={task}
                    isActive={task.id === activeTaskId}
                    onClick={() => onTaskClick(task)}
                  />
                ))
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
});

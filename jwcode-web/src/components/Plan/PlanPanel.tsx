import { useState, useCallback } from 'react';
import { usePlanStore } from '../../stores/planStore';
import { useSessionStore } from '../../stores/sessionStore';
import { PlanTask } from '../../types';
import { KanbanBoard } from './KanbanBoard';
import { CurrentTaskDetail } from './CurrentTaskDetail';
import { PlanTimeline } from './PlanTimeline';
import { PlanSummary } from './PlanSummary';
import { PlanSkeleton } from './PlanSkeleton';

type ViewMode = 'kanban' | 'timeline';

export function PlanPanel() {
  const plansBySession = usePlanStore((s) => s.plansBySession);
  const planPhasesBySession = usePlanStore((s) => s.planPhasesBySession);
  const messageQueue = usePlanStore((s) => s.messageQueue);
  const clearPlan = usePlanStore((s) => s.clearPlan);
  const activeSessionId = useSessionStore((s) => s.activeSessionId);

  const [activeTaskId, setActiveTaskId] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<ViewMode>('kanban');

  // 获取当前 session 的 plan
  const currentPlan = activeSessionId ? plansBySession[activeSessionId] : null;
  const planPhase = activeSessionId ? (planPhasesBySession[activeSessionId] || 'idle') : 'idle';

  const handleTaskClick = useCallback((task: PlanTask) => {
    setActiveTaskId((prev) => (prev === task.id ? null : task.id));
  }, []);

  const handleNewPlan = useCallback(() => {
    if (activeSessionId) clearPlan(activeSessionId);
  }, [clearPlan, activeSessionId]);

  // Find active task
  const activeTask = currentPlan?.tasks.find((t) => t.id === activeTaskId) || null;
  const activeTaskIndex = currentPlan
    ? currentPlan.tasks.findIndex((t) => t.id === activeTaskId) + 1
    : 0;

  // Planning phase - show skeleton
  if (planPhase === 'planning' || !currentPlan) {
    return (
      <div className="flex-1 flex flex-col overflow-hidden p-4">
        <PlanSkeleton />
      </div>
    );
  }

  // Result phase - show summary
  if (planPhase === 'result') {
    return (
      <div className="flex-1 flex flex-col overflow-hidden p-4">
        <PlanSummary plan={currentPlan} onNewPlan={handleNewPlan} />
      </div>
    );
  }

  // Executing phase - show full UI
  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      {/* Header */}
      <div className="px-4 py-3 border-b border-dark-border bg-dark-surface shrink-0">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="text-lg">🎯</span>
            <div>
              <h2 className="text-sm font-semibold text-dark-text">Plan 模式</h2>
              <p className="text-xs text-dark-muted mt-0.5 line-clamp-1 max-w-xl">
                {currentPlan.goal}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            {/* Queue indicator */}
            {messageQueue.length > 0 && (
              <div className="flex items-center gap-1.5 px-2 py-1 bg-yellow-500/10 border border-yellow-500/30 rounded-lg">
                <span className="text-xs text-yellow-400">📥</span>
                <span className="text-xs text-yellow-400">
                  {messageQueue.length} 条消息等待中
                </span>
              </div>
            )}

            {/* View mode toggle */}
            <div className="flex bg-dark-bg rounded-lg p-0.5">
              <button
                onClick={() => setViewMode('kanban')}
                className={`px-3 py-1.5 text-xs rounded-md transition-all ${
                  viewMode === 'kanban'
                    ? 'bg-accent-blue text-white'
                    : 'text-dark-muted hover:text-dark-text'
                }`}
              >
                看板
              </button>
              <button
                onClick={() => setViewMode('timeline')}
                className={`px-3 py-1.5 text-xs rounded-md transition-all ${
                  viewMode === 'timeline'
                    ? 'bg-accent-blue text-white'
                    : 'text-dark-muted hover:text-dark-text'
                }`}
              >
                时间线
              </button>
            </div>

            {/* Progress summary */}
            <div className="text-xs text-dark-muted">
              {currentPlan.tasks.filter((t) => t.status === 'completed').length}/
              {currentPlan.tasks.length} 完成
            </div>
          </div>
        </div>
      </div>

      {/* Main content */}
      <div className="flex-1 flex overflow-hidden">
        {/* Left: Kanban or Timeline */}
        <div className="flex-1 overflow-y-auto p-4">
          {viewMode === 'kanban' ? (
            <KanbanBoard
              tasks={currentPlan.tasks}
              activeTaskId={activeTaskId}
              onTaskClick={handleTaskClick}
            />
          ) : (
            <PlanTimeline
              tasks={currentPlan.tasks}
              activeTaskId={activeTaskId}
              onTaskClick={handleTaskClick}
            />
          )}
        </div>

        {/* Right: Task detail - 响应式宽度 */}
        <div className="hidden xl:block w-[400px] 2xl:w-[480px] shrink-0 border-l border-dark-border overflow-y-auto p-4">

          <CurrentTaskDetail
            task={activeTask}
            taskIndex={activeTaskIndex}
            totalTasks={currentPlan.tasks.length}
          />
        </div>
      </div>
    </div>
  );
}



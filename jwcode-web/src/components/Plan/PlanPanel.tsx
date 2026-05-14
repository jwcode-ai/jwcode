import { useState, useCallback } from 'react';
import { usePlanStore } from '../../stores/planStore';
import { useSessionStore } from '../../stores/sessionStore';

import { PlanTask, StructuredTask } from '../../types';
import { KanbanBoard } from './KanbanBoard';
import { CurrentTaskDetail } from './CurrentTaskDetail';
import { PlanTimeline } from './PlanTimeline';
import { PlanSummary } from './PlanSummary';
import { PlanSkeleton } from './PlanSkeleton';
import { TaskTree } from './TaskTree';
import { StepProgressBar } from './StepProgressBar';
import { StructuredTaskView, StructuredTaskSummary } from './StructuredTaskView';

type ViewMode = 'kanban' | 'timeline' | 'tree' | 'structured';

/**
 * PlanPanel — Plan/Act 模式面板
 * 
 * 功能：
 * - Plan/Act 模式切换 UI（顶部状态栏）
 * - Plan 文件内容预览（Plan Mode 下显示）
 * - 任务看板/时间线/树形视图
 * - 当前任务详情
 * - 消息队列指示器
 */
export function PlanPanel() {
  const plansBySession = usePlanStore((s) => s.plansBySession);
  const planPhasesBySession = usePlanStore((s) => s.planPhasesBySession);
  const structuredTasksBySession = usePlanStore((s) => s.structuredTasksBySession);
  const messageQueue = usePlanStore((s) => s.messageQueue);
  const mode = usePlanStore((s) => s.mode);
  const currentPlanContent = usePlanStore((s) => s.currentPlanContent);
  const modeHistory = usePlanStore((s) => s.modeHistory);
  const currentStepPrompt = usePlanStore((s) => s.currentStepPrompt);
  const setMode = usePlanStore((s) => s.setMode);
  const clearPlan = usePlanStore((s) => s.clearPlan);
  const activeSessionId = useSessionStore((s) => s.activeSessionId);

  const [activeTaskId, setActiveTaskId] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<ViewMode>('kanban');
  const [showPlanContent, setShowPlanContent] = useState(false);

  // 获取当前 session 的 plan
  const currentPlan = activeSessionId ? plansBySession[activeSessionId] : null;
  const planPhase = activeSessionId ? (planPhasesBySession[activeSessionId] || 'idle') : 'idle';

  // 获取当前 session 的结构化任务
  const currentStructuredTasks = activeSessionId
    ? (structuredTasksBySession[activeSessionId] || [])
    : [];

  // 如果有结构化任务数据，自动切换到结构化视图
  const hasStructuredTasks = currentStructuredTasks.length > 0;

  const handleTaskClick = useCallback((task: PlanTask | StructuredTask) => {
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

  // === Plan/Act 模式切换按钮 ===
  const renderModeToggle = () => (
    <div className="flex items-center gap-2">
      <div className="flex bg-dark-bg rounded-lg p-0.5">
        <button
          onClick={() => setMode('plan')}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-md transition-all ${
            mode === 'plan'
              ? 'bg-purple-500 text-white shadow-sm'
              : 'text-dark-muted hover:text-dark-text'
          }`}
          title="Plan Mode：只读探索，不能修改代码"
        >
          <span>📋</span>
          <span>Plan</span>
        </button>
        <button
          onClick={() => setMode('act')}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-md transition-all ${
            mode === 'act'
              ? 'bg-green-500 text-white shadow-sm'
              : 'text-dark-muted hover:text-dark-text'
          }`}
          title="Act Mode：可以读写和执行"
        >
          <span>⚡</span>
          <span>Act</span>
        </button>
      </div>
      {mode === 'plan' && (
        <span className="text-xs text-purple-400 animate-pulse">
          只读模式
        </span>
      )}
    </div>
  );

  // === Plan 文件内容预览 ===
  const renderPlanContentPreview = () => {
    if (!currentPlanContent) return null;
    return (
      <div className="border-b border-dark-border">
        <button
          onClick={() => setShowPlanContent(!showPlanContent)}
          className="w-full flex items-center justify-between px-4 py-2 text-xs text-dark-muted hover:text-dark-text hover:bg-dark-surface/50 transition-colors"
        >
          <span className="flex items-center gap-1.5">
            <span>📄</span>
            <span>Plan 文件内容</span>
          </span>
          <span>{showPlanContent ? '收起 ▲' : '展开 ▼'}</span>
        </button>
        {showPlanContent && (
          <div className="px-4 pb-3">
            <pre className="text-xs text-dark-text bg-dark-bg rounded-lg p-3 overflow-x-auto whitespace-pre-wrap max-h-60 overflow-y-auto border border-dark-border">
              {currentPlanContent}
            </pre>
          </div>
        )}
      </div>
    );
  };

  // === 模式历史 ===
  const lastModeChange = modeHistory.length > 0 ? modeHistory[modeHistory.length - 1] : null;

  // Planning phase - show skeleton or confirm button
  if (planPhase === 'planning' || !currentPlan) {
    const showConfirm = usePlanStore((s) => s.showConfirmButton);
    const confirmPlan = usePlanStore((s) => s.confirmPlan);
    const clearPendingPlan = usePlanStore((s) => s.clearPendingPlan);

    return (
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Mode toggle bar */}
        <div className="px-4 py-2 border-b border-dark-border bg-dark-surface/50 shrink-0">
          <div className="flex items-center justify-between">
            {renderModeToggle()}
            {lastModeChange && (
              <span className="text-xs text-dark-muted">
                {lastModeChange.description}
              </span>
            )}
          </div>
        </div>
        <div className="flex-1 flex flex-col overflow-hidden p-4">
          {showConfirm ? (
            <div className="flex flex-col items-center justify-center h-full">
              <div className="max-w-lg text-center">
                <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-purple-500/10 flex items-center justify-center">
                  <span className="text-2xl">📋</span>
                </div>
                <h2 className="text-lg font-semibold text-dark-text mb-2">
                  AI 分析完成
                </h2>
                <p className="text-sm text-dark-muted mb-6">
                  AI 已完成需求分析并生成了任务计划。请查看上方 AI 的回复内容，
                  确认无误后点击下方按钮开始执行。
                </p>
                <div className="flex items-center justify-center gap-3">
                  <button
                    onClick={() => confirmPlan(activeSessionId || '')}
                    className="px-6 py-2.5 text-sm bg-green-500 text-white rounded-lg hover:bg-green-600 transition-colors flex items-center gap-2"
                  >
                    <span>⚡</span>
                    <span>确认执行</span>
                  </button>
                  <button
                    onClick={() => {
                      clearPendingPlan(activeSessionId || '');
                      handleNewPlan();
                    }}
                    className="px-4 py-2.5 text-sm bg-dark-surface border border-dark-border rounded-lg hover:bg-dark-hover transition-colors"
                  >
                    重新规划
                  </button>
                </div>
              </div>
            </div>
          ) : (
            <PlanSkeleton />
          )}
        </div>
      </div>
    );
  }

  // Result phase - show summary
  if (planPhase === 'result') {
    return (
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Mode toggle bar */}
        <div className="px-4 py-2 border-b border-dark-border bg-dark-surface/50 shrink-0">
          <div className="flex items-center justify-between">
            {renderModeToggle()}
            {lastModeChange && (
              <span className="text-xs text-dark-muted">
                {lastModeChange.description}
              </span>
            )}
          </div>
        </div>
        <div className="flex-1 flex flex-col overflow-hidden p-4">
          <PlanSummary plan={currentPlan} onNewPlan={handleNewPlan} />
        </div>
      </div>
    );
  }

  // Error phase - show error with retry option
  if (planPhase === 'error') {
    return (
      <div className="flex-1 flex flex-col overflow-hidden">
        <div className="px-4 py-2 border-b border-dark-border bg-dark-surface/50 shrink-0">
          <div className="flex items-center justify-between">
            {renderModeToggle()}
            {lastModeChange && (
              <span className="text-xs text-dark-muted">
                {lastModeChange.description}
              </span>
            )}
          </div>
        </div>
        <div className="flex-1 flex flex-col items-center justify-center p-4">
          <div className="text-center max-w-md">
            <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-accent-red/10 flex items-center justify-center">
              <span className="text-2xl">❌</span>
            </div>
            <h2 className="text-lg font-semibold text-dark-text mb-2">任务规划失败</h2>
            <p className="text-sm text-dark-muted mb-4">
              {currentPlan?.goal || 'AI 无法生成有效的任务计划，请重试或使用 Act 模式直接执行。'}
            </p>
            <div className="flex items-center justify-center gap-3">
              <button
                onClick={handleNewPlan}
                className="px-4 py-2 text-sm bg-accent-blue text-white rounded-lg hover:opacity-90 transition-opacity"
              >
                重新规划
              </button>
              <button
                onClick={() => setMode('act')}
                className="px-4 py-2 text-sm bg-dark-surface border border-dark-border rounded-lg hover:bg-dark-hover transition-colors"
              >
                切换到 Act 模式
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // Executing phase - show full UI
  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      {/* Mode toggle bar */}
      <div className="px-4 py-2 border-b border-dark-border bg-dark-surface/50 shrink-0">
        <div className="flex items-center justify-between">
          {renderModeToggle()}
          {lastModeChange && (
            <span className="text-xs text-dark-muted">
              {lastModeChange.description}
            </span>
          )}
        </div>
      </div>

      {/* Plan content preview (collapsible) */}
      {renderPlanContentPreview()}

      {/* Step Progress Bar */}
      {currentPlan.tasks.length > 0 && (
        <div className="px-4 py-3 border-b border-dark-border bg-dark-surface/30 shrink-0">
          <StepProgressBar
            tasks={currentPlan.tasks}
            currentStepPrompt={currentStepPrompt}
          />
        </div>
      )}

      {/* Header */}
      <div className="px-4 py-3 border-b border-dark-border bg-dark-surface shrink-0">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="text-lg">🎯</span>
            <div>
              <h2 className="text-sm font-semibold text-dark-text">
                {mode === 'plan' ? 'Plan 模式（只读）' : 'Act 模式'}
              </h2>
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
              <button
                onClick={() => setViewMode('tree')}
                className={`px-3 py-1.5 text-xs rounded-md transition-all ${
                  viewMode === 'tree'
                    ? 'bg-accent-blue text-white'
                    : 'text-dark-muted hover:text-dark-text'
                }`}
              >
                树形
              </button>
              <button
                onClick={() => setViewMode('structured')}
                className={`px-3 py-1.5 text-xs rounded-md transition-all ${
                  viewMode === 'structured'
                    ? 'bg-accent-blue text-white'
                    : hasStructuredTasks
                      ? 'text-amber-400 hover:text-amber-300'
                      : 'text-dark-muted hover:text-dark-text'
                }`}
              >
                {hasStructuredTasks && <span className="mr-1">⚡</span>}
                结构化
                {hasStructuredTasks && (
                  <span className="ml-1 text-[10px] opacity-60">
                    ({currentStructuredTasks.length})
                  </span>
                )}
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
        {/* Left: Kanban, Timeline, or Tree */}
        <div className="flex-1 overflow-y-auto p-4">
          {viewMode === 'kanban' && (
            <KanbanBoard
              tasks={currentPlan.tasks}
              activeTaskId={activeTaskId}
              onTaskClick={handleTaskClick}
            />
          )}
          {viewMode === 'timeline' && (
            <PlanTimeline
              tasks={currentPlan.tasks}
              activeTaskId={activeTaskId}
              onTaskClick={handleTaskClick}
            />
          )}
          {viewMode === 'tree' && (
            <div className="bg-dark-surface rounded-lg border border-dark-border p-4">
              <div className="mb-3 flex items-center justify-between">
                <h3 className="text-sm font-medium text-dark-text">🌳 任务树形结构</h3>
                <span className="text-xs text-dark-muted">
                  {currentPlan.tasks.length} 个顶层任务
                </span>
              </div>
              <TaskTree
                tasks={currentPlan.tasks}
                activeTaskId={activeTaskId}
                onTaskClick={handleTaskClick}
              />
            </div>
          )}
          {viewMode === 'structured' && (
            <div>
              {hasStructuredTasks ? (
                <div className="space-y-4">
                  {/* 结构化任务统计 */}
                  <StructuredTaskSummary tasks={currentStructuredTasks} />

                  {/* 结构化任务视图 */}
                  <div className="bg-dark-surface rounded-lg border border-dark-border p-4">
                    <div className="mb-3 flex items-center justify-between">
                      <h3 className="text-sm font-medium text-dark-text">
                        ⚡ 结构化任务执行视图
                      </h3>
                      <span className="text-xs text-dark-muted">
                        {currentStructuredTasks.length} 个阶段
                      </span>
                    </div>
                    <StructuredTaskView
                      tasks={currentStructuredTasks}
                      activeTaskId={activeTaskId}
                      onTaskClick={handleTaskClick}
                    />
                  </div>
                </div>
              ) : (
                <div className="flex items-center justify-center py-16">
                  <div className="text-center">
                    <div className="text-4xl mb-3">📋</div>
                    <h3 className="text-sm font-medium text-dark-text mb-2">
                      暂无结构化任务
                    </h3>
                    <p className="text-xs text-dark-muted max-w-sm">
                      在 Plan 模式下与 AI 确认执行计划后，AI 会自动生成结构化任务列表。
                      <br />
                      结构化任务会标注执行模式（串行/并发）、阶段分组和依赖关系。
                    </p>
                  </div>
                </div>
              )}
            </div>
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




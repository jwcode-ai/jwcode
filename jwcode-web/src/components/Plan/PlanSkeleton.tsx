import { memo } from 'react';

export const PlanSkeleton = memo(function PlanSkeleton() {
  return (
    <div className="bg-dark-surface rounded-lg border border-dark-border p-6">
      {/* Header skeleton */}
      <div className="flex items-center gap-3 mb-6">
        <div className="w-8 h-8 bg-dark-bg rounded-lg animate-pulse" />
        <div className="space-y-2">
          <div className="w-48 h-4 bg-dark-bg rounded animate-pulse" />
          <div className="w-64 h-3 bg-dark-bg rounded animate-pulse" />
        </div>
      </div>

      {/* Progress skeleton */}
      <div className="space-y-3 mb-6">
        <div className="flex items-center justify-between">
          <div className="w-20 h-3 bg-dark-bg rounded animate-pulse" />
          <div className="w-12 h-3 bg-dark-bg rounded animate-pulse" />
        </div>
        <div className="h-2 bg-dark-bg rounded-full animate-pulse overflow-hidden">
          <div className="h-full w-1/3 bg-dark-border rounded-full animate-pulse" />
        </div>
      </div>

      {/* Task list skeleton */}
      <div className="space-y-3">
        {[1, 2, 3, 4, 5].map((i) => (
          <div
            key={i}
            className="flex items-center gap-3 p-3 bg-dark-bg/50 rounded-lg border border-dark-border"
          >
            <div className="w-4 h-4 bg-dark-bg rounded-full animate-pulse" />
            <div className="flex-1 space-y-2">
              <div className="w-3/4 h-3 bg-dark-bg rounded animate-pulse" />
              <div className="w-1/2 h-2 bg-dark-bg rounded animate-pulse" />
            </div>
            <div className="w-16 h-3 bg-dark-bg rounded animate-pulse" />
          </div>
        ))}
      </div>

      {/* Loading indicator */}
      <div className="flex items-center justify-center gap-2 mt-6 text-dark-muted">
        <div className="flex gap-1">
          <span className="w-2 h-2 bg-accent-blue rounded-full animate-bounce" />
          <span className="w-2 h-2 bg-accent-blue rounded-full animate-bounce" style={{ animationDelay: '0.1s' }} />
          <span className="w-2 h-2 bg-accent-blue rounded-full animate-bounce" style={{ animationDelay: '0.2s' }} />
        </div>
        <span className="text-sm">AI 正在分析需求，生成任务列表...</span>
      </div>
    </div>
  );
});

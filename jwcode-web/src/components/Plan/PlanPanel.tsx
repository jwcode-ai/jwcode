import { useState } from 'react';
import { useExecutionModeStore } from '../../stores/executionModeStore';

/**
 * PlanPanel — Plan/Act 模式面板（简化版）
 *
 * 功能：
 * - Plan/Act 模式切换按钮
 * - Plan 文件内容预览
 * - 分析完成后显示"切换到 Act 模式执行"提示
 */
export function PlanPanel() {
  const mode = useExecutionModeStore((s) => s.mode);
  const currentPlanContent = useExecutionModeStore((s) => s.currentPlanContent);
  const modeHistory = useExecutionModeStore((s) => s.modeHistory);
  const setMode = useExecutionModeStore((s) => s.setMode);

  const [showPlanContent, setShowPlanContent] = useState(false);

  const lastModeChange = modeHistory.length > 0 ? modeHistory[modeHistory.length - 1] : null;

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
        <span className="text-xs text-purple-400 animate-pulse">只读模式</span>
      )}
    </div>
  );

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

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      {/* Mode toggle bar */}
      <div className="px-4 py-2 border-b border-dark-border bg-dark-surface/50 shrink-0">
        <div className="flex items-center justify-between">
          {renderModeToggle()}
          {lastModeChange && (
            <span className="text-xs text-dark-muted">{lastModeChange.description}</span>
          )}
        </div>
      </div>

      {/* Plan content preview */}
      {renderPlanContentPreview()}

      {/* Main content area */}
      <div className="flex-1 flex flex-col items-center justify-center p-4 overflow-y-auto">
        {mode === 'plan' ? (
          <div className="text-center max-w-md">
            <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-purple-500/10 flex items-center justify-center">
              <span className="text-2xl">📋</span>
            </div>
            <h2 className="text-lg font-semibold text-dark-text mb-2">Plan 模式 — 只读分析</h2>
            <p className="text-sm text-dark-muted mb-6">
              在 Plan 模式下，AI 将使用只读工具分析需求和代码，不会修改任何文件。
              分析完成后，请切换到 Act 模式执行具体实现。
            </p>
            <button
              onClick={() => setMode('act')}
              className="px-6 py-2.5 text-sm bg-green-500 text-white rounded-lg hover:bg-green-600 transition-colors flex items-center gap-2 mx-auto"
            >
              <span>⚡</span>
              <span>切换到 Act 模式执行</span>
            </button>
          </div>
        ) : (
          <div className="text-center max-w-md">
            <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-green-500/10 flex items-center justify-center">
              <span className="text-2xl">⚡</span>
            </div>
            <h2 className="text-lg font-semibold text-dark-text mb-2">Act 模式</h2>
            <p className="text-sm text-dark-muted mb-6">
              在 Act 模式下，AI 可以读取、修改文件并执行命令。
              需要先分析需求？切换到 Plan 模式进行只读探索。
            </p>
            <button
              onClick={() => setMode('plan')}
              className="px-6 py-2.5 text-sm bg-purple-500 text-white rounded-lg hover:bg-purple-600 transition-colors flex items-center gap-2 mx-auto"
            >
              <span>📋</span>
              <span>切换到 Plan 模式分析</span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

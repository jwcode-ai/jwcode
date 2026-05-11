import { memo } from 'react';
import { PlanTask } from '../../types';
import { CheckCircle2, PlayCircle, XCircle, SkipForward, AlertCircle } from 'lucide-react';

interface StepProgressBarProps {
  tasks: PlanTask[];
  currentStepPrompt?: {
    stepNumber: number;
    action: string;
    stepPrompt: string;
    agentType: string;
  } | null;
}

/**
 * StepProgressBar — 步骤进度条组件
 * 横向展示所有步骤的编号、状态和连接线，支持点击高亮当前步骤提示
 */
export const StepProgressBar = memo(function StepProgressBar({
  tasks,
  currentStepPrompt,
}: StepProgressBarProps) {
  if (!tasks || tasks.length === 0) return null;

  const completedCount = tasks.filter((t) => t.status === 'completed').length;
  const totalCount = tasks.length;

  return (
    <div className="flex flex-col gap-2">
      {/* 总体进度文字 */}
      <div className="flex items-center justify-between px-1">
        <span className="text-xs text-dark-muted">
          步骤进度
        </span>
        <span className="text-xs font-medium text-dark-text">
          {completedCount}/{totalCount} 完成
        </span>
      </div>

      {/* 步骤进度条 */}
      <div className="flex items-center gap-0">
        {tasks.map((task, idx) => {
          const isLast = idx === tasks.length - 1;
          const isActive = currentStepPrompt?.stepNumber === task.stepNumber;

          return (
            <div key={task.id} className="flex items-center gap-0 flex-1 min-w-0">
              {/* 步骤圆点 */}
              <div
                className={`
                  relative flex flex-col items-center shrink-0
                  ${isActive ? 'scale-110' : ''}
                  transition-transform duration-300
                `}
                title={`步骤 ${task.stepNumber || idx + 1}: ${task.title}${task.action ? ` - ${task.action}` : ''}`}
              >
                <div className={`
                  w-8 h-8 rounded-full flex items-center justify-center
                  ${task.status === 'completed' ? 'bg-green-500/20 border border-green-500/50' : ''}
                  ${task.status === 'running' ? 'bg-blue-500/20 border border-blue-500/50' : ''}
                  ${task.status === 'failed' ? 'bg-red-500/20 border border-red-500/50' : ''}
                  ${task.status === 'pending' ? 'bg-dark-bg border border-dark-border' : ''}
                  ${task.status === 'skipped' ? 'bg-gray-500/10 border border-gray-500/30' : ''}
                  ${isActive ? 'ring-2 ring-purple-400 shadow-lg shadow-purple-400/20' : ''}
                `}>
                  {task.status === 'completed' ? (
                    <CheckCircle2 size={14} className="text-green-400" />
                  ) : task.status === 'running' ? (
                    <PlayCircle size={14} className="text-blue-400 animate-pulse" />
                  ) : task.status === 'failed' ? (
                    <XCircle size={14} className="text-red-400" />
                  ) : task.status === 'skipped' ? (
                    <SkipForward size={14} className="text-gray-500" />
                  ) : (
                    <span className="text-[10px] font-bold text-dark-muted">
                      {task.stepNumber || idx + 1}
                    </span>
                  )}
                </div>
                {/* 步骤标题 */}
                <span className={`
                  text-[9px] mt-1 text-center leading-tight max-w-[60px] truncate
                  ${isActive ? 'text-purple-300 font-medium' : 'text-dark-muted'}
                `}>
                  {task.action || task.title}
                </span>
              </div>

              {/* 连接线 */}
              {!isLast && (
                <div className="flex-1 h-[2px] mx-0.5 min-w-[4px]">
                  <div
                    className={`h-full rounded-full ${
                      task.status === 'completed'
                        ? 'bg-green-500/50'
                        : task.status === 'running'
                        ? 'bg-gradient-to-r from-green-500/50 to-dark-border'
                        : 'bg-dark-border'
                    }`}
                  />
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* 当前步骤AI提示 */}
      {currentStepPrompt && (
        <div className="mt-3 p-3 rounded-lg bg-purple-500/5 border border-purple-500/20">
          <div className="flex items-center gap-2 mb-1.5">
            <AlertCircle size={14} className="text-purple-400" />
            <span className="text-xs font-medium text-purple-300">
              步骤 {currentStepPrompt.stepNumber} · AI 指引
            </span>
            <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-purple-500/20 text-purple-300">
              {currentStepPrompt.agentType || 'agent'}
            </span>
          </div>
          <p className="text-xs text-purple-200/80 leading-relaxed">
            {currentStepPrompt.stepPrompt || currentStepPrompt.action}
          </p>
        </div>
      )}
    </div>
  );
});

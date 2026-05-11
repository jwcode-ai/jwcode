import { memo, useEffect, useRef } from 'react';
import { PlanTask } from '../../types';
import { usePlanStore } from '../../stores/planStore';
import { AlertCircle, ArrowRight } from 'lucide-react';

interface CurrentTaskDetailProps {
  task: PlanTask | null;
  taskIndex: number;
  totalTasks: number;
}

export const CurrentTaskDetail = memo(function CurrentTaskDetail({
  task,
  taskIndex,
  totalTasks,
}: CurrentTaskDetailProps) {
  const logEndRef = useRef<HTMLDivElement>(null);
  const currentStepPrompt = usePlanStore((s) => s.currentStepPrompt);

  // Auto-scroll logs
  useEffect(() => {
    logEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [task?.logs]);

  if (!task) {
    return (
      <div className="bg-dark-surface rounded-lg border border-dark-border p-6">
        <div className="flex items-center justify-center h-32 text-dark-muted text-sm">
          选择一个任务查看详情
        </div>
      </div>
    );
  }

  const formatDuration = (start?: number, end?: number) => {
    if (!start) return '';
    const ms = (end || Date.now()) - start;
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(1)}s`;
  };

  const getAgentIcon = (type: string) => {
    const icons: Record<string, string> = {
      coder: '💻', debug: '🐛', explore: '🔍',
      architect: '🏗️', test: '🧪', reviewer: '👁️', doc: '📝',
    };
    return icons[type] || '⚙️';
  };

  return (
    <div className="bg-dark-surface rounded-lg border border-dark-border">
      {/* Header */}
      <div className="px-4 py-3 border-b border-dark-border">
        <div className="flex items-center gap-2">
          <span className="text-lg">{getAgentIcon(task.agentType)}</span>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-dark-text">
                任务 {taskIndex}/{totalTasks}
              </span>
              <span className="text-xs text-dark-muted">·</span>
              <span className="text-sm text-dark-text">{task.title}</span>
            </div>
            <span className="text-xs text-dark-muted">
              {task.agentType.charAt(0).toUpperCase() + task.agentType.slice(1)}Agent
              {task.startedAt && ` · ⏱ ${formatDuration(task.startedAt, task.completedAt)}`}
            </span>
          </div>
        </div>
      </div>

      {/* Description */}
      {task.description && (
        <div className="px-4 py-2 border-b border-dark-border bg-dark-bg/50">
          <p className="text-xs text-dark-muted">{task.description}</p>
        </div>
      )}

      {/* Dependencies */}
      {task.dependencies.length > 0 && (
        <div className="px-4 py-2 border-b border-dark-border">
          <div className="flex items-center gap-2 text-xs text-dark-muted">
            <span>🔗 依赖任务:</span>
            {task.dependencies.map((depId) => (
              <span key={depId} className="px-1.5 py-0.5 bg-dark-bg rounded text-xs">
                {depId.slice(0, 8)}...
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Progress */}
      {task.status === 'running' && task.progress !== undefined && (
        <div className="px-4 py-3 border-b border-dark-border">
          <div className="flex items-center justify-between text-xs text-dark-muted mb-1.5">
            <span>执行进度</span>
            <span>{task.progress}%</span>
          </div>
          <div className="h-2 bg-dark-bg rounded-full overflow-hidden">
            <div
              className="h-full bg-gradient-to-r from-blue-500 to-blue-400 rounded-full transition-all duration-500"
              style={{ width: `${task.progress}%` }}
            />
          </div>
        </div>
      )}

      {/* Step Prompt - AI指引 */}
      {currentStepPrompt && currentStepPrompt.taskId === task.id && (
        <div className="px-4 py-3 border-b border-purple-500/20 bg-purple-500/5">
          <div className="flex items-center gap-2 mb-2">
            <AlertCircle size={14} className="text-purple-400 shrink-0" />
            <span className="text-xs font-medium text-purple-300">
              🤖 AI 步骤指引
            </span>
          </div>
          <p className="text-xs text-purple-200/80 leading-relaxed">
            {currentStepPrompt.stepPrompt}
          </p>
          {currentStepPrompt.action && (
            <div className="flex items-center gap-1.5 mt-2 text-[10px] text-purple-400/60">
              <ArrowRight size={10} />
              <span>动作: {currentStepPrompt.action}</span>
            </div>
          )}
        </div>
      )}

      {/* Execution Logs */}
      <div className="px-4 py-3">
        <h4 className="text-xs font-medium text-dark-muted mb-2">执行日志</h4>
        <div className="bg-dark-bg rounded-lg p-3 max-h-[200px] overflow-y-auto font-mono text-xs space-y-1">
          {task.logs && task.logs.length > 0 ? (
            task.logs.map((log, i) => {
              const icon = log.startsWith('💭') ? '💭' :
                          log.startsWith('🔧') ? '🔧' :
                          log.startsWith('✅') ? '✅' :
                          log.startsWith('❌') ? '❌' :
                          log.startsWith('📝') ? '📝' : '  ';
              return (
                <div key={i} className="flex items-start gap-2 text-dark-muted">
                  <span className="shrink-0">{icon}</span>
                  <span>{log.replace(/^[^\s]+\s/, '')}</span>
                </div>
              );
            })
          ) : (
            <div className="text-dark-muted/50 text-center py-4">
              {task.status === 'pending' ? '等待执行...' :
               task.status === 'running' ? '正在初始化...' :
               '无执行日志'}
            </div>
          )}
          <div ref={logEndRef} />
        </div>
      </div>

      {/* Result */}
      {task.result && (
        <div className="px-4 py-3 border-t border-dark-border">
          <h4 className="text-xs font-medium text-dark-muted mb-2">执行结果</h4>
          <div className="bg-green-500/5 border border-green-500/20 rounded-lg p-3 text-xs text-green-400">
            {task.result}
          </div>
        </div>
      )}

      {/* Error */}
      {task.error && (
        <div className="px-4 py-3 border-t border-dark-border">
          <h4 className="text-xs font-medium text-dark-muted mb-2">错误信息</h4>
          <div className="bg-red-500/5 border border-red-500/20 rounded-lg p-3 text-xs text-red-400">
            {task.error}
          </div>
        </div>
      )}
    </div>
  );
});

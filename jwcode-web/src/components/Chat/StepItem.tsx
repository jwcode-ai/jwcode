import { memo, useState } from 'react';
import { Plus, Minus } from 'lucide-react';
import { Step } from '../../types';
import { ExpandableResult } from './ExpandableResult';

interface StepItemProps {
  step: Step;
  defaultCollapsed?: boolean;
}

export const StepItem = memo(function StepItem({ step, defaultCollapsed = false }: StepItemProps) {
  const [isCollapsed, setIsCollapsed] = useState(defaultCollapsed);
  const [isThoughtCollapsed, setIsThoughtCollapsed] = useState(true);

  const statusColors = {
    pending: 'bg-dark-hover text-dark-muted',
    running: 'bg-accent-blue text-white animate-pulse-soft',
    success: 'bg-accent-green text-white',
    error: 'bg-accent-red text-white',
    warning: 'bg-accent-yellow text-white',
  };

  const statusIcons = {
    pending: '○',
    running: '⟳',
    success: '✓',
    error: '✗',
    warning: '⚠',
  };

  const hasDetails = step.thought || step.result || step.action || (step.toolCalls && step.toolCalls.length > 0);

  return (
    <div className={`bg-dark-bg border-l-4 rounded-r-lg text-sm overflow-hidden ${
      step.status === 'running' ? 'border-accent-blue' :
      step.status === 'success' ? 'border-accent-green' :
      step.status === 'error' ? 'border-accent-red' :
      step.status === 'warning' ? 'border-accent-yellow' :
      'border-dark-border'
    }`}>
      {/* Header - Always visible */}
      <div
        className="flex items-center gap-2 p-3 cursor-pointer hover:bg-dark-surface/50"
        onClick={() => hasDetails && setIsCollapsed(!isCollapsed)}
      >
        {hasDetails ? (
          isCollapsed ? <Plus size={14} className="text-accent-blue" /> : <Minus size={14} className="text-accent-blue" />
        ) : <span className="w-3.5" />}
        <span className={`w-5 h-5 rounded-full flex items-center justify-center text-xs ${statusColors[step.status]}`}>
          {statusIcons[step.status]}
        </span>
        <span className="font-medium">{step.title}</span>
        {step.status === 'running' && <span className="text-xs text-accent-blue animate-pulse">运行中...</span>}
      </div>

      {/* Collapsible content - 树形结构 */}
      {!isCollapsed && (
        <div className="ml-4 space-y-1">
          {/* Step Description */}
          {step.description && (
            <div className="text-dark-muted py-1 px-3 text-xs border-l-2 border-dark-border">
              {step.description}
            </div>
          )}

          {/* 1. Thought - 💭 思考过程（可折叠） */}
          {step.thought && (
            <div className="py-1 px-3 text-xs border-l-2 border-accent-blue pl-2 bg-accent-blue/5 rounded-r">
              <div
                className="flex items-center gap-1 text-accent-blue cursor-pointer hover:opacity-80 select-none"
                onClick={() => setIsThoughtCollapsed(!isThoughtCollapsed)}
              >
                <span>{isThoughtCollapsed ? <Plus size={12} /> : <Minus size={12} />}</span>
                <span>💭</span>
                <span>思考</span>
                {isThoughtCollapsed && (
                  <span className="text-[10px] text-dark-muted ml-1 truncate max-w-[160px]">
                    {step.thought.slice(0, 50).replace(/\n/g, ' ')}...
                  </span>
                )}
              </div>
              {!isThoughtCollapsed && (
                <div className="text-dark-muted italic whitespace-pre-wrap leading-snug mt-1">
                  {step.thought.replace(/\n\s*\n+/g, '\n')}
                </div>
              )}
            </div>
          )}

          {/* 2. Action - ⚡ 执行动作 */}
          {step.action && (
            <div className="py-1 px-3 text-xs border-l-2 border-accent-yellow pl-2 bg-accent-yellow/5 rounded-r">
              <div className="flex items-center gap-1 text-accent-yellow mb-0.5">
                <span>⚡</span>
                <span>执行</span>
              </div>
              <div className="text-dark-text leading-snug">
                {step.action}
              </div>
            </div>
          )}

          {/* 3. Tool Calls - 🔧 请求+结果（树形子节点） */}
          {step.toolCalls && step.toolCalls.length > 0 && step.toolCalls.map((toolCall, index) => (
            <div key={toolCall.id} className="py-1 px-3 text-xs border-l-2 border-accent-purple pl-2 bg-accent-purple/5 rounded-r">
              <div className="flex items-center gap-1 text-accent-purple mb-0.5">
                <span>🔧</span>
                <span>工具调用 {index + 1}</span>
                <span className={`text-[10px] px-1.5 py-0.5 rounded ${
                  toolCall.status === 'running' ? 'bg-accent-blue text-white' : 'bg-accent-green text-white'
                }`}>
                  {toolCall.status === 'running' ? '运行中' : '完成'}
                </span>
              </div>
              {/* 请求参数 */}
              <div className="ml-2 text-dark-muted mb-0.5">
                <span className="text-accent-purple/70">请求:</span>
                <pre className="text-xs font-mono bg-dark-bg p-1.5 rounded overflow-x-auto mt-1">
                  {typeof toolCall.args === 'string'
                    ? toolCall.args
                    : JSON.stringify(toolCall.args, null, 2)}
                </pre>
              </div>
              {/* 返回结果 */}
              {toolCall.result !== undefined && toolCall.result !== null && (
                <div className="ml-2 text-dark-muted">
                  <span className="text-accent-green/70">结果:</span>
                  <ExpandableResult
                    text={typeof toolCall.result === 'object' ? JSON.stringify(toolCall.result, null, 2) : String(toolCall.result)}
                    maxLength={300}
                    preformatted
                  />
                </div>
              )}
            </div>
          ))}

          {/* 4. Result - ✓ 完成结果 */}
          {step.result && (
            <div className="py-1 px-3 text-xs border-l-2 border-accent-green pl-2 bg-accent-green/5 rounded-r">
              <div className="flex items-center gap-1 text-accent-green mb-0.5">
                <span>✓</span>
                <span>结果</span>
              </div>
              <ExpandableResult text={step.result} maxLength={300} />
            </div>
          )}
        </div>
      )}
    </div>
  );
});

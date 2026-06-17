import { memo, useState, useRef } from 'react';
import { Plus, Minus, Clock } from 'lucide-react';
import { Step } from '../../types';
import { ExpandableResult } from './ExpandableResult';
import { ToolCallItem } from './ToolCallItem';
import { useGlobalTick } from '../../hooks/useGlobalTick';

interface StepItemProps {
  step: Step;
  defaultCollapsed?: boolean;
}

export const StepItem = memo(function StepItem({ step, defaultCollapsed = false }: StepItemProps) {
  const [isCollapsed, setIsCollapsed] = useState(defaultCollapsed);
  const [isThoughtCollapsed, setIsThoughtCollapsed] = useState(true);
  const tick = useGlobalTick(); // 1s global tick — replaces per-component setInterval
  const startTickRef = useRef(0);

  // Capture starting tick when the step begins running
  if (step.status === 'running' && startTickRef.current === 0) {
    startTickRef.current = tick;
  }
  if (step.status !== 'running') {
    startTickRef.current = 0;
  }

  const elapsed = step.status === 'running' ? tick - startTickRef.current : 0;

  const statusConfig = {
    pending:  { bg: 'bg-dark-hover', text: 'text-dark-muted', border: 'border-dark-border', icon: '○', label: '等待' },
    running:  { bg: 'bg-accent-blue/20', text: 'text-accent-blue', border: 'border-accent-blue', icon: '⟳', label: '' },
    success:  { bg: 'bg-accent-green/20', text: 'text-accent-green', border: 'border-accent-green', icon: '✓', label: '' },
    error:    { bg: 'bg-accent-red/20', text: 'text-accent-red', border: 'border-accent-red', icon: '✗', label: '' },
    warning:  { bg: 'bg-accent-yellow/20', text: 'text-accent-yellow', border: 'border-accent-yellow', icon: '⚠', label: '' },
  };

  const sc = statusConfig[step.status] || statusConfig.pending;
  const hasDetails = step.thought || step.result || step.action || (step.toolCalls && step.toolCalls.length > 0);
  const duration = step.duration ?? (step.status === 'running' ? elapsed : 0);
  const durationStr = duration > 0
    ? duration >= 60 ? `${Math.floor(duration / 60)}m ${duration % 60}s` : `${duration}s`
    : '';

  return (
    <div className={`bg-dark-bg border-l-4 rounded-r-lg text-sm overflow-hidden transition-colors ${sc.border}`}>
      {/* Header */}
      <div
        className="flex items-center gap-2 p-3 cursor-pointer hover:bg-dark-surface/50"
        onClick={() => hasDetails && setIsCollapsed(!isCollapsed)}
      >
        {hasDetails ? (
          isCollapsed ? <Plus size={14} className="text-accent-blue shrink-0" /> : <Minus size={14} className="text-accent-blue shrink-0" />
        ) : <span className="w-3.5 shrink-0" />}

        {/* Status circle with animation */}
        <span className={`w-5 h-5 rounded-full flex items-center justify-center text-xs shrink-0
          ${step.status === 'running' ? 'bg-accent-blue/30 text-accent-blue animate-pulse' : sc.bg + ' ' + sc.text}`}>
          {step.status === 'running' ? (
            <span className="animate-spin-frame text-[10px]">◌</span>
          ) : sc.icon}
        </span>

        <span className="font-medium text-dark-text truncate">{step.title}</span>

        {/* Live timer */}
        {step.status === 'running' && (
          <span className="text-xs text-accent-blue animate-pulse shrink-0 flex items-center gap-1">
            <Clock size={11} />
            <span>{durationStr}</span>
          </span>
        )}

        {/* Duration label for completed steps */}
        {step.status !== 'running' && step.status !== 'pending' && durationStr && (
          <span className="text-[10px] text-dark-muted shrink-0 flex items-center gap-1">
            <Clock size={10} />
            <span>{durationStr}</span>
          </span>
        )}

        <span className={`text-[10px] px-1.5 py-0.5 rounded-full shrink-0 ml-auto ${sc.bg} ${sc.text}`}>
          {sc.label || sc.icon}
        </span>
      </div>

      {/* Collapsible details */}
      {!isCollapsed && hasDetails && (
        <div className="ml-6 space-y-1 border-l-2 border-dark-border/30">
          {step.description && (
            <div className="text-dark-muted py-1 px-3 text-xs">{step.description}</div>
          )}

          {/* Thought */}
          {step.thought && (
            <div className="py-1 px-3 text-xs border-l-2 border-accent-blue/40 bg-accent-blue/5 rounded-r">
              <div
                className="flex items-center gap-1 text-accent-blue cursor-pointer hover:opacity-80 select-none"
                onClick={() => setIsThoughtCollapsed(!isThoughtCollapsed)}
              >
                <span>{isThoughtCollapsed ? <Plus size={12} /> : <Minus size={12} />}</span>
                <span>💭 思考</span>
                {isThoughtCollapsed && (
                  <span className="text-[10px] text-dark-muted truncate max-w-[200px]">
                    {step.thought.slice(0, 80).replace(/\n/g, ' ')}...
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

          {/* Action */}
          {step.action && (
            <div className="py-1 px-3 text-xs border-l-2 border-accent-yellow/40 bg-accent-yellow/5 rounded-r">
              <div className="flex items-center gap-1 text-accent-yellow mb-0.5">
                <span>⚡ 执行</span>
              </div>
              <div className="text-dark-text leading-snug font-mono text-[11px]">{step.action}</div>
            </div>
          )}

          {/* Tool Calls — unified ToolCallItem rendering */}
          {step.toolCalls && step.toolCalls.length > 0 && step.toolCalls.map((toolCall) => (
            <div key={toolCall.id} className="px-2 py-1">
              <ToolCallItem toolCall={toolCall} defaultCollapsed={toolCall.status !== 'running'} />
            </div>
          ))}

          {/* Result */}
          {step.result && (
            <div className="py-1 px-3 text-xs border-l-2 border-accent-green/40 bg-accent-green/5 rounded-r">
              <div className="flex items-center gap-1 text-accent-green mb-0.5">
                <span>✓ 结果</span>
              </div>
              <ExpandableResult text={step.result} maxLength={500} />
            </div>
          )}
        </div>
      )}
    </div>
  );
});

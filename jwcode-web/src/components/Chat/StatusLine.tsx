import { memo, useEffect, useRef, useState } from 'react';
import { useTokenStore } from '../../stores/tokenStore';
import { useChatStore } from '../../stores/chatStore';
import { useSessionStore } from '../../stores/sessionStore';
import { usePlanStore } from '../../stores/planStore';
import { useHookApprovalStore } from '../../stores/useHookApprovalStore';

export const StatusLine = memo(function StatusLine() {
  const { currentUsage, maxContextTokens, model } = useTokenStore();
  const activeSessionId = useSessionStore((s) => s.activeSessionId);
  const isGenerating = useChatStore((s) => activeSessionId ? s.isGenerating(activeSessionId) : false);
  const [elapsed, setElapsed] = useState(0);
  const startRef = useRef<number>(0);

  // Plan/Act mode
  const planMode = usePlanStore((s) => s.mode);
  const { toggleMode, enterPlanMode } = usePlanStore();
  const autoMode = useHookApprovalStore((s) => s.autoMode);
  const setAutoMode = useHookApprovalStore((s) => s.setAutoMode);

  useEffect(() => {
    if (isGenerating && !startRef.current) startRef.current = Date.now();
    if (!isGenerating) { startRef.current = 0; setElapsed(0); }
    if (!isGenerating) return;
    const timer = setInterval(() => setElapsed(Math.floor((Date.now() - startRef.current) / 1000)), 1000);
    return () => clearInterval(timer);
  }, [isGenerating]);

  const usagePercent = maxContextTokens > 0
    ? Math.min(100, Math.round((currentUsage.totalTokens / maxContextTokens) * 100))
    : 0;

  const formatTokens = (n: number): string => {
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M';
    if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K';
    return String(n);
  };

  const barGradient = usagePercent > 85
    ? 'bg-gradient-to-r from-accent-yellow via-accent-red to-accent-red'
    : usagePercent > 70
    ? 'bg-gradient-to-r from-accent-green via-accent-yellow to-accent-yellow'
    : 'bg-gradient-to-r from-accent-cyan via-accent-blue to-accent-purple';

  const handlePlanToggle = () => {
    if (planMode !== 'plan' && activeSessionId) {
      enterPlanMode(activeSessionId, '');
    } else {
      toggleMode();
    }
  };

  return (
    <div className="h-7 bg-dark-surface border-t border-dark-border flex items-center px-3 gap-3 shrink-0 select-none text-xs">
      <span className="text-accent-orange font-bold">jwcode</span>

      {/* Plan/Act toggle */}
      <div className="flex items-center gap-0.5 bg-dark-bg rounded-md p-0.5 border border-dark-border">
        <button
          onClick={handlePlanToggle}
          className={`px-2 py-0.5 rounded text-xs font-medium transition-all ${
            planMode === 'plan'
              ? 'bg-accent-cyan text-dark-bg shadow-sm'
              : 'text-dark-muted hover:text-dark-text'
          }`}
          title="规划模式：先分析规划再执行 (Tab)"
        >
          ◉ Plan
        </button>
        <button
          onClick={handlePlanToggle}
          className={`px-2 py-0.5 rounded text-xs font-medium transition-all ${
            planMode !== 'plan'
              ? 'bg-accent-green text-dark-bg shadow-sm'
              : 'text-dark-muted hover:text-dark-text'
          }`}
          title="执行模式：直接执行 (Tab)"
        >
          ○ Act
        </button>
      </div>

      {/* Auto mode toggle */}
      <button
        onClick={() => setAutoMode(!autoMode)}
        className={`px-2 py-0.5 rounded text-xs font-medium transition-all border ${
          autoMode
            ? 'bg-accent-purple/20 text-accent-purple border-accent-purple/30'
            : 'text-dark-muted border-dark-border hover:text-dark-text'
        }`}
        title="自动模式：自动批准工具调用"
      >
        {autoMode ? '⚡ Auto' : '○ Auto'}
      </button>

      <span className="text-dark-muted">
        <span className="text-dark-text">model:</span>
        <span className="text-accent-blue ml-1">{model || '...'}</span>
      </span>

      <span className="text-dark-muted">
        <span className="text-dark-text">{formatTokens(currentUsage.totalTokens)}</span>
        <span className="mx-0.5">/</span>
        <span>{formatTokens(maxContextTokens)}</span>
        <span className="ml-0.5">tokens</span>
        <div className="inline-block w-16 h-1.5 bg-dark-border rounded-full overflow-hidden ml-1.5 align-middle">
          <div className={`h-full rounded-full transition-all duration-500 ${barGradient}`}
            style={{ width: `${usagePercent}%` }} />
        </div>
        <span className="ml-1 text-dark-text">{usagePercent}%</span>
      </span>

      {currentUsage.estimatedCost && currentUsage.estimatedCost > 0 && (
        <span className="text-dark-muted">
          <span className="text-accent-green">${currentUsage.estimatedCost.toFixed(4)}</span>
        </span>
      )}

      {isGenerating && (
        <span className="text-accent-cyan">
          <span className="inline-block animate-spin-frame align-middle mr-0.5">◐</span>
          <span className="text-accent-blue">{elapsed}s</span>
        </span>
      )}

      <div className="flex-1" />

      {usagePercent > 85 && (
        <span className="text-accent-red font-bold">⚠ Budget</span>
      )}
    </div>
  );
});

import { memo, useEffect, useRef, useState } from 'react';
import { useTokenStore } from '../../stores/tokenStore';
import { useChatStore } from '../../stores/chatStore';
import { useSessionStore } from '../../stores/sessionStore';
import { usePlanStore } from '../../stores/planStore';
import { useHookApprovalStore } from '../../stores/useHookApprovalStore';
import type { TabId } from '../../types';

interface StatusLineProps {
  activeTab: TabId;
}

export const StatusLine = memo(function StatusLine(_props: StatusLineProps) {
  const { currentUsage, maxContextTokens, model, tokenRate, compactingUntil, lastCompactInfo, degradation, compactionProgress } = useTokenStore();
  const activeSessionId = useSessionStore((s) => s.activeSessionId);
  const isGenerating = useChatStore((s) => activeSessionId ? s.isGenerating(activeSessionId) : false);
  const [elapsed, setElapsed] = useState(0);
  const startRef = useRef<number>(0);

  // Plan/Act mode
  const planMode = usePlanStore((s) => s.mode);
  const setPlanMode = usePlanStore((s) => s.setMode);
  const autoMode = useHookApprovalStore((s) => s.autoMode);
  const setAutoMode = useHookApprovalStore((s) => s.setAutoMode);

  useEffect(() => {
    if (isGenerating && !startRef.current) startRef.current = Date.now();
    if (!isGenerating) { startRef.current = 0; setElapsed(0); }
    if (!isGenerating) return;
    const timer = setInterval(() => setElapsed(Math.floor((Date.now() - startRef.current) / 1000)), 1000);
    return () => clearInterval(timer);
  }, [isGenerating]);

  const total = currentUsage.totalTokens;
  const prompt = currentUsage.promptTokens;
  const completion = currentUsage.completionTokens;
  const usagePercent = maxContextTokens > 0
    ? Math.min(100, Math.round((total / maxContextTokens) * 100))
    : 0;

  // Split bar: prompt vs completion proportion
  // const promptRatio = total > 0 ? prompt / total : 0;
  // const completionRatio = total > 0 ? completion / total : 0;

  const formatTokens = (n: number): string => {
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M';
    if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K';
    return String(n);
  };

  // smooth rate: one decimal
  const rateDisplay = tokenRate > 0
    ? tokenRate >= 100 ? Math.round(tokenRate) + 't/s'
    : tokenRate >= 10 ? tokenRate.toFixed(1) + 't/s'
    : tokenRate.toFixed(1) + 't/s'
    : '';

  return (
    <div className="h-7 bg-dark-surface border-b border-dark-border flex items-center px-3 gap-3 shrink-0 select-none text-xs">
      <span className="text-accent-orange font-bold">jwcode</span>

      {/* Plan | Act mode selector — two always-visible buttons */}
      <div className="flex items-center gap-0.5">
        <button
          onClick={() => setPlanMode('plan')}
          className={`px-2 py-0.5 rounded-l text-xs font-medium transition-all border ${
            planMode === 'plan'
              ? 'bg-accent-cyan/20 text-accent-cyan border-accent-cyan/30'
              : 'text-dark-muted border-dark-border hover:text-dark-text hover:border-dark-muted'
          }`}
          title="切换到规划模式"
        >
          {planMode === 'plan' ? '●' : '○'} Plan
        </button>
        <button
          onClick={() => setPlanMode('act')}
          className={`px-2 py-0.5 rounded-r text-xs font-medium transition-all border ${
            planMode !== 'plan'
              ? 'bg-accent-green/20 text-accent-green border-accent-green/30'
              : 'text-dark-muted border-dark-border hover:text-dark-text hover:border-dark-muted'
          }`}
          title="切换到执行模式"
        >
          {planMode !== 'plan' ? '●' : '○'} Act
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

      {/* Token breakdown: prompt + completion = total / max */}
      <span className="text-dark-muted flex items-center gap-0.5">
        <span className="text-accent-blue">{formatTokens(prompt)}</span>
        <span className="text-dark-muted/60">+</span>
        <span className="text-accent-green">{formatTokens(completion)}</span>
        <span className="text-dark-muted/60">=</span>
        <span className="text-dark-text">{formatTokens(total)}</span>
        <span className="text-dark-muted/60">/</span>
        <span>{formatTokens(maxContextTokens)}</span>

        {/* Watermark bar with 70%/90% thresholds */}
        <div className="relative inline-flex w-20 h-2 bg-dark-border rounded-full overflow-hidden ml-1 align-middle gap-0 group/token">
          {/* Background threshold markers */}
          <div className="absolute inset-0 flex pointer-events-none">
            <div className="h-full border-r border-dark-text/20" style={{ width: "70%" }} />
            <div className="h-full border-r border-dark-text/20" style={{ width: `${(90 / 70 - 1) * 100}%`, marginLeft: "70%" }} />
          </div>
          {/* Actual fill */}
          <div
            className={`h-full transition-all duration-700 ${
              usagePercent >= 90 ? "bg-accent-red/80" :
              usagePercent >= 70 ? "bg-accent-yellow/80" :
              usagePercent >= 50 ? "bg-accent-blue/60" :
              "bg-accent-blue/40"
            }`}
            style={{ width: `${Math.min(100, usagePercent)}%` }}
          />
        </div>
        <span className={`ml-1 font-mono text-[11px] ${
          usagePercent >= 90 ? "text-accent-red font-bold" :
          usagePercent >= 70 ? "text-accent-yellow" :
          "text-dark-text"
        }`}>{usagePercent}%</span>
      </span>

      {/* Cost */}
      {currentUsage.estimatedCost && currentUsage.estimatedCost > 0 && (
        <span className="text-dark-muted">
          <span className="text-accent-green">${currentUsage.estimatedCost.toFixed(4)}</span>
        </span>
      )}

      {/* Live generation indicators */}
      {isGenerating && (
        <>
          <span className="text-accent-cyan flex items-center gap-0.5">
            <span className="inline-block animate-spin-frame align-middle">◐</span>
            <span className="text-accent-blue">{elapsed}s</span>
          </span>
          {rateDisplay && (
            <span className="text-accent-purple text-[11px]">{rateDisplay}</span>
          )}
        </>
      )}

      <div className="flex-1" />

      {/* Compaction progress bar — shown during active compaction */}
      {compactionProgress && (
        <span className="flex items-center gap-2">
          <span className="text-accent-cyan text-[11px]">
            {compactionProgress.percent < 100 ? '🗜️' : '✅'}
          </span>
          <span className={`text-[11px] ${
            compactionProgress.percent >= 100 ? 'text-accent-green' : 'text-accent-cyan'
          }`}>
            {compactionProgress.message}
          </span>
          <div className="relative inline-flex w-24 h-1.5 bg-dark-border rounded-full overflow-hidden">
            <div
              className={`h-full transition-all duration-500 rounded-full ${
                compactionProgress.percent >= 100 ? 'bg-accent-green' : 'bg-accent-cyan'
              }`}
              style={{ width: `${Math.min(100, compactionProgress.percent)}%` }}
            />
          </div>
          <span className="text-dark-muted text-[10px] font-mono">{compactionProgress.percent}%</span>
        </span>
      )}

      {/* Compaction animation */}
      {!compactionProgress && Date.now() < compactingUntil && lastCompactInfo && (
        <span className="text-accent-cyan animate-pulse flex items-center gap-1">
          <span className="inline-block animate-spin-frame">◎</span>
          <span>{lastCompactInfo.originalCount}→{lastCompactInfo.compressedCount}</span>
          <span className="text-dark-muted">-{formatTokens(lastCompactInfo.tokensSaved)}</span>
        </span>
      )}

      {/* Watermark threshold warnings */}
      {usagePercent >= 90 && (
        <span className="text-accent-red font-bold animate-pulse flex items-center gap-0.5" title="Hard threshold - compaction triggered">
          <span className="text-[10px]">⚠</span>
          <span className="text-[11px]">90%+ Compacting</span>
        </span>
      )}
      {usagePercent >= 70 && usagePercent < 90 && (
        <span className="text-accent-yellow flex items-center gap-0.5" title="Soft threshold - compaction advised">
          <span className="text-[10px]">⚠</span>
          <span className="text-[11px]">70%+ Watch</span>
        </span>
      )}

      {/* Degradation indicator */}
      {degradation && degradation.active && (
        <span className="text-accent-orange flex items-center gap-0.5" title={`${degradation.label}${degradation.retryCount > 0 ? ` (${degradation.retryCount}/${degradation.maxRetries})` : ""}: ${degradation.message}`}>
          <span className="text-[10px]">⚠</span>
          <span className="text-[11px]">{degradation.label}{degradation.retryCount > 0 ? ` ${degradation.retryCount}/${degradation.maxRetries}` : ""}</span>
        </span>
      )}
    </div>
  );
});

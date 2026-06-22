import { memo, useEffect, useState, useRef } from 'react';
import { Sparkles } from 'lucide-react';

interface ThinkingStatusProps {
  /** Number of currently running agents */
  runningCount: number;
  /** Total tokens consumed by all running/completed agents in this session */
  totalTokens: number;
  /** Unix timestamp when the first agent started (for elapsed time) */
  startTime?: number;
}

export const ThinkingStatus = memo(function ThinkingStatus({
  runningCount,
  totalTokens,
  startTime,
}: ThinkingStatusProps) {
  const [elapsed, setElapsed] = useState(0);
  const startRef = useRef(startTime || Date.now());

  useEffect(() => {
    if (runningCount === 0) { setElapsed(0); return; }
    if (startTime) startRef.current = startTime;
    const timer = setInterval(() => {
      setElapsed(Math.floor((Date.now() - startRef.current) / 1000));
    }, 1000);
    return () => clearInterval(timer);
  }, [runningCount, startTime]);

  if (runningCount === 0) return null;

  const formatElapsed = (s: number) => {
    if (s >= 60) {
      const m = Math.floor(s / 60);
      const sec = s % 60;
      return `${m}m ${sec}s`;
    }
    return `${s}s`;
  };

  const formatTokens = (n: number): string => {
    if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
    if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
    return String(n);
  };

  // Compute tokens-per-second rate
  const rate = elapsed > 0 && totalTokens > 0
    ? `${formatTokens(Math.round(totalTokens / elapsed))}/s`
    : '';

  return (
    <div className="flex items-center gap-2 px-3 py-1.5 border-t border-dark-border bg-dark-surface/30">
      <Sparkles size={12} className="text-accent-purple animate-pulse shrink-0" />
      <span className="text-[11px] text-dark-text italic">
        {runningCount === 1 ? 'Agent' : 'Agents'} working
      </span>
      <span className="text-[11px] text-dark-muted font-mono">
        ({formatElapsed(elapsed)}
        {totalTokens > 0 && <span> · ↓{formatTokens(totalTokens)} tok</span>}
        {rate && <span> · {rate}</span>}
        )
      </span>
    </div>
  );
});

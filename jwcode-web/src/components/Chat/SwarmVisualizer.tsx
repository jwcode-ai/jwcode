import { memo, useEffect, useState, useRef } from "react";
import { useSwarmStore, SwarmTask } from "../../stores/swarmStore";
import { Clock, CheckCircle2, Loader2, XCircle, Cpu, Search, FileCode, TestTube, Eye } from "lucide-react";

const TYPE_CONFIG: Record<string, { label: string; color: string; icon: any }> = {
  ANALYSIS: { label: "Analysis", color: "border-l-accent-blue", icon: Search },
  PLANNING: { label: "Planning", color: "border-l-accent-purple", icon: Cpu },
  EXECUTION: { label: "Execution", color: "border-l-accent-green", icon: FileCode },
  VERIFICATION: { label: "Verification", color: "border-l-accent-yellow", icon: TestTube },
};

function getTypeConfig(type: string) {
  return TYPE_CONFIG[type] || { label: type, color: "border-l-dark-border", icon: Eye };
}

function SwarmTaskItem({ task }: { task: SwarmTask }) {
  const tc = getTypeConfig(task.type);
  const isRunning = task.status === "running";
  const isSuccess = task.status === "success";
  const [elapsed, setElapsed] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (isRunning) {
      const startTime = Date.now();
      setElapsed(0);
      timerRef.current = setInterval(() => {
        setElapsed(Math.floor((Date.now() - startTime) / 1000));
      }, 1000);
    }
    return () => { if (timerRef.current) clearInterval(timerRef.current); };
  }, [isRunning]);

  const duration = task.durationMs ? Math.floor(task.durationMs / 1000) : (isRunning ? elapsed : 0);
  const durationStr = duration > 0
    ? duration >= 60 ? `${Math.floor(duration / 60)}m ${duration % 60}s` : `${duration}s`
    : "";

  return (
    <div className={`flex items-center gap-2 py-1.5 px-2 rounded bg-dark-bg border-l-2 ${tc.color} ${isRunning ? "animate-pulse" : ""}`}>
      {/* Agent type icon */}
      <span className={`w-6 h-6 rounded-full flex items-center justify-center shrink-0 ${isRunning ? "bg-accent-blue/20 text-accent-blue" : isSuccess ? "bg-accent-green/20 text-accent-green" : "bg-accent-red/20 text-accent-red"}`}>
        {isRunning ? (
          <Loader2 size={12} className="animate-spin" />
        ) : isSuccess ? (
          <CheckCircle2 size={12} />
        ) : (
          <XCircle size={12} />
        )}
      </span>

      {/* Agent ID badge */}
      <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-dark-hover text-dark-muted shrink-0">
        {task.agentId.replace("swarm-", "").replace(/-/g, " ")}
      </span>

      {/* Description */}
      <span className="text-xs text-dark-text truncate flex-1 min-w-0">
        {task.description}
      </span>

      {/* Duration */}
      {durationStr && (
        <span className="text-[10px] text-dark-muted shrink-0 flex items-center gap-1">
          <Clock size={10} />
          {durationStr}
        </span>
      )}

      {/* Status badge */}
      <span className={`text-[10px] px-1.5 py-0.5 rounded-full shrink-0 ${
        isRunning ? "bg-accent-blue/20 text-accent-blue" :
        isSuccess ? "bg-accent-green/20 text-accent-green" :
        "bg-accent-red/20 text-accent-red"
      }`}>
        {isRunning ? "running" : isSuccess ? "done" : "failed"}
      </span>
    </div>
  );
}

export const SwarmVisualizer = memo(function SwarmVisualizer({ sessionId }: { sessionId: string }) {
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  const tasks = useSwarmStore((s) => s.swarmsBySession[sessionId]?.tasks || []);
  const completedCount = useSwarmStore((s) => s.swarmsBySession[sessionId]?.completedCount || 0);
  const totalCount = useSwarmStore((s) => s.swarmsBySession[sessionId]?.totalCount || 0);

  if (!mounted || tasks.length === 0) return null;

  const progress = totalCount > 0 ? Math.round((completedCount / totalCount) * 100) : 0;
  const allDone = completedCount >= totalCount && totalCount > 0;
  const hasRunning = tasks.some(t => t.status === "running");

  return (
    <div className="mb-3 border border-dark-border rounded-lg overflow-hidden bg-dark-surface/50">
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-2 bg-dark-hover border-b border-dark-border">
        <div className="flex items-center gap-2">
          <Cpu size={14} className="text-accent-purple" />
          <span className="text-xs font-medium text-dark-text">Agent Swarm</span>
          {hasRunning && (
            <span className="flex items-center gap-1 text-[10px] text-accent-blue">
              <Loader2 size={10} className="animate-spin" />
              Executing...
            </span>
          )}
          {allDone && (
            <span className="text-[10px] text-accent-green">Complete</span>
          )}
        </div>
        <div className="flex items-center gap-2 text-[10px] text-dark-muted">
          <span>{completedCount}/{totalCount} tasks</span>
          {progress > 0 && (
            <span className="w-16 h-1.5 bg-dark-border rounded-full overflow-hidden">
              <span
                className={`h-full block rounded-full transition-all ${
                  allDone ? "bg-accent-green" : "bg-accent-blue"
                }`}
                style={{ width: `${progress}%` }}
              />
            </span>
          )}
        </div>
      </div>

      {/* Task list */}
      <div className="p-2 space-y-1 max-h-48 overflow-y-auto">
        {tasks.map((task) => (
          <SwarmTaskItem key={task.taskId} task={task} />
        ))}
      </div>

      {/* Stats footer */}
      {allDone && (
        <div className="px-3 py-1.5 border-t border-dark-border bg-accent-green/5 text-[10px] text-accent-green flex items-center gap-2">
          <CheckCircle2 size={11} />
          Swarm completed: {totalCount} tasks in parallel
        </div>
      )}
    </div>
  );
});

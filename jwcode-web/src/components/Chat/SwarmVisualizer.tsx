import { memo } from "react";
import { useWorkflowStore } from "../../stores/workflowStore";
import { useSwarmStore } from "../../stores/swarmStore";
import { AgentTreeView } from "./AgentTreeView";
import { ThinkingStatus } from "./ThinkingStatus";

/**
 * SwarmVisualizer — displays multi-agent execution as a tree view.
 *
 * Data comes from workflowStore (primary) for the tree structure,
 * and from swarmStore (legacy fallback) for backward compatibility
 * when no agent_flow_event data is available.
 */
export const SwarmVisualizer = memo(function SwarmVisualizer({ sessionId }: { sessionId: string }) {
  /* ── Primary: workflowStore (tree data from agent_flow_event) ── */
  const trees = useWorkflowStore((s) => s.getAgentTree(sessionId));
  const runningCount = useWorkflowStore((s) => s.getRunningAgentCount(sessionId));
  const totalTokens = useWorkflowStore((s) => {
    const agents = s.agentsBySession[sessionId];
    if (!agents) return 0;
    return Object.values(agents).reduce((sum, a) => sum + (a.tokens || 0), 0);
  });

  /* ── Fallback: legacy swarmStore (flat task list, no tree) ── */
  const legacyTasks = useSwarmStore((s) => s.swarmsBySession[sessionId]?.tasks || []);

  // Derive earliest start time from agent data
  const agents = useWorkflowStore((s) => s.agentsBySession[sessionId]);
  const startTime = agents ? Math.min(
    ...Object.values(agents).map(a => a.updatedAt),
    Date.now()
  ) : undefined;

  // Nothing to show
  if (trees.length === 0 && legacyTasks.length === 0) return null;

  /* ── Legacy flat-list fallback (no tree data) ── */
  if (trees.length === 0 && legacyTasks.length > 0) {
    const completedCount = legacyTasks.filter(t => t.status === "success").length;
    const totalCount = legacyTasks.length;
    const progress = totalCount > 0 ? Math.round((completedCount / totalCount) * 100) : 0;
    const allDone = completedCount >= totalCount && totalCount > 0;

    return (
      <div className="mb-3 border border-dark-border rounded-lg overflow-hidden bg-dark-surface/50">
        <div className="flex items-center justify-between px-3 py-2 bg-dark-hover border-b border-dark-border">
          <div className="flex items-center gap-2">
            <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-accent-purple/10 text-accent-purple">legacy</span>
            <span className="text-xs text-dark-text">Swarm Tasks</span>
          </div>
          <div className="flex items-center gap-2 text-[10px] text-dark-muted">
            <span>{completedCount}/{totalCount}</span>
            <div className="w-16 h-1.5 bg-dark-border rounded-full overflow-hidden">
              <span
                className={`h-full block rounded-full transition-all ${
                  allDone ? "bg-accent-green" : "bg-accent-blue"
                }`}
                style={{ width: `${progress}%` }}
              />
            </div>
          </div>
        </div>
        <div className="p-2 text-[10px] text-dark-muted text-center">
          {legacyTasks.length} task{legacyTasks.length > 1 ? 's' : ''} — flat list (no tree data available)
        </div>
      </div>
    );
  }

  /* ── Primary: tree view ── */
  return (
    <div>
      <AgentTreeView trees={trees} runningCount={runningCount} />
      <ThinkingStatus
        runningCount={runningCount}
        totalTokens={totalTokens}
        startTime={startTime}
      />
    </div>
  );
});

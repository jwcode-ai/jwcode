import { memo, useState, useEffect, useRef } from 'react';
import { Loader2, CheckCircle2, XCircle, Circle, Clock, ChevronRight, ChevronDown, Cpu } from 'lucide-react';
import type { TreeNode } from '../../stores/workflowStore';

interface AgentTreeViewProps {
  trees: TreeNode[];
  /** Total running agents across all trees — used by ThinkingStatus */
  runningCount: number;
}

/* ───── Status Icon ───── */
const StatusIcon = memo(function StatusIcon({ status, isRunning }: { status: string; isRunning: boolean }) {
  if (isRunning || status === 'running') return <Loader2 size={12} className="animate-spin text-accent-blue shrink-0" />;
  if (status === 'completed') return <CheckCircle2 size={12} className="text-accent-green shrink-0" />;
  if (status === 'failed') return <XCircle size={12} className="text-accent-red shrink-0" />;
  return <Circle size={12} className="text-dark-muted shrink-0" />;
});

/* ───── Status Badge ───── */
const StatusBadge = memo(function StatusBadge({ status }: { status: string }) {
  const isRunning = status === 'running' || status === 'scheduled';
  const isSuccess = status === 'completed';
  const isFailed = status === 'failed';

  let cls = 'text-dark-muted bg-dark-surface';
  if (isRunning) cls = 'text-accent-blue bg-accent-blue/10';
  else if (isSuccess) cls = 'text-accent-green bg-accent-green/10';
  else if (isFailed) cls = 'text-accent-red bg-accent-red/10';

  let label = status;
  if (status === 'running' || status === 'scheduled') label = 'running';
  else if (status === 'completed') label = 'done';
  else if (status === 'failed') label = 'failed';

  return (
    <span className={`text-[10px] px-1.5 py-0.5 rounded-full shrink-0 font-medium ${cls}`}>
      {label}
    </span>
  );
});

/* ───── Token Count ───── */
const TokenCount = memo(function TokenCount({ tokens }: { tokens: number }) {
  const formatted = tokens >= 1_000 ? `${(tokens / 1_000).toFixed(1)}K` : String(tokens);
  return (
    <span className="text-[10px] text-dark-muted shrink-0 font-mono" title={`${tokens} tokens`}>
      {formatted} tok
    </span>
  );
});

/* ───── Elapsed Timer ───── */
function useElapsed(startedAt: number | undefined, isRunning: boolean) {
  const [elapsed, setElapsed] = useState(0);
  const startRef = useRef(startedAt || Date.now());

  useEffect(() => {
    if (!isRunning || !startedAt) { setElapsed(0); return; }
    startRef.current = startedAt;
    const timer = setInterval(() => setElapsed(Math.floor((Date.now() - startRef.current) / 1000)), 1000);
    return () => clearInterval(timer);
  }, [isRunning, startedAt]);

  return elapsed;
}

const formatDuration = (s: number) => {
  if (s >= 60) {
    const m = Math.floor(s / 60);
    const sec = s % 60;
    return `${m}m ${sec}s`;
  }
  return `${s}s`;
};

/* ───── Single Tree Node (Recursive) ───── */
interface TreeNodeItemProps {
  node: TreeNode;
  depth: number;
  isLast: boolean;
}

const TreeNodeItem = memo(function TreeNodeItem({ node, depth, isLast }: TreeNodeItemProps) {
  const [collapsed, setCollapsed] = useState(false);
  const hasChildren = node.children.length > 0;
  const isRunning = node.agent.status === 'running' || node.agent.status === 'scheduled';
  const elapsed = useElapsed(node.agent.updatedAt, isRunning);
  const showDuration = isRunning || node.agent.status === 'completed';

  // Build the tree-line prefix
  const prefix = depth === 0 ? '' : isLast ? '└─ ' : '├─ ';

  return (
    <div className="select-none">
      {/* Node row */}
      <div
        className={`flex items-center gap-1.5 py-1 px-1 rounded transition-colors ${
          isRunning ? 'bg-accent-blue/[0.04]' : 'hover:bg-dark-hover/30'
        }`}
        style={{ marginLeft: depth * 16 }}
      >
        {/* Collapse toggle for parent nodes */}
        {hasChildren ? (
          <button
            onClick={() => setCollapsed(!collapsed)}
            className="w-3.5 h-3.5 flex items-center justify-center shrink-0 text-dark-muted hover:text-dark-text transition-colors"
          >
            {collapsed ? <ChevronRight size={11} /> : <ChevronDown size={11} />}
          </button>
        ) : (
          <span className="w-3.5 shrink-0" />
        )}

        {/* Tree connector */}
        {depth > 0 && (
          <span className="text-[11px] text-dark-muted/50 font-mono shrink-0 leading-none">
            {prefix}
          </span>
        )}

        {/* Status icon */}
        <StatusIcon status={node.agent.status} isRunning={isRunning} />

        {/* Agent name */}
        <span className={`text-xs truncate min-w-0 ${
          isRunning ? 'text-accent-blue font-medium' : 'text-dark-text'
        }`}>
          {node.agent.name || node.agent.role || 'Agent'}
        </span>

        {/* Status badge */}
        <StatusBadge status={node.agent.status} />

        {/* Token count */}
        {node.tokensUsed > 0 && <TokenCount tokens={node.tokensUsed} />}

        {/* Duration */}
        {showDuration && elapsed > 0 && (
          <span className="text-[10px] text-dark-muted/70 shrink-0 flex items-center gap-0.5 font-mono">
            <Clock size={9} />
            {formatDuration(elapsed)}
          </span>
        )}

        {/* Current task description — only shown for running agents */}
        {isRunning && node.agent.currentTask && (
          <span className="text-[10px] text-dark-muted italic truncate ml-1 hidden sm:inline">
            {node.agent.currentTask}
          </span>
        )}
      </div>

      {/* Current task as sub-line (for collapsed view) */}
      {!isRunning && node.agent.currentTask && (
        <div
          className="text-[10px] text-dark-muted/60 italic pl-1 truncate"
          style={{ marginLeft: depth * 16 + 56 }}
        >
          {node.agent.currentTask}
        </div>
      )}

      {/* Children (recursive) */}
      {!collapsed && hasChildren && (
        <div>
          {node.children.map((child, idx) => (
            <TreeNodeItem
              key={child.agent.id}
              node={child}
              depth={depth + 1}
              isLast={idx === node.children.length - 1}
            />
          ))}
        </div>
      )}

      {/* Collapsed hint */}
      {collapsed && hasChildren && (
        <div
          className="text-[9px] text-dark-muted/40 italic"
          style={{ marginLeft: depth * 16 + 32 }}
        >
          {node.children.length} sub-agent{node.children.length > 1 ? 's' : ''} (collapsed)
        </div>
      )}
    </div>
  );
});

/* ───── Root Component ───── */
export const AgentTreeView = memo(function AgentTreeView({ trees, runningCount }: AgentTreeViewProps) {
  const totalCount = trees.reduce((sum, t) => sum + 1 + countNodes(t.children), 0);
  const completedCount = trees.reduce((sum, t) => sum + countCompleted(t), 0);
  const progress = totalCount > 0 ? Math.round((completedCount / totalCount) * 100) : 0;
  const allDone = completedCount >= totalCount && totalCount > 0;

  if (trees.length === 0) return null;

  return (
    <div className="mb-3 border border-dark-border rounded-lg overflow-hidden bg-dark-surface/50">
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-2 bg-dark-hover border-b border-dark-border">
        <div className="flex items-center gap-2 min-w-0">
          <Cpu size={14} className="text-accent-purple shrink-0" />
          <span className="text-xs font-medium text-dark-text">Agent Swarm</span>
          {runningCount > 0 && (
            <span className="flex items-center gap-1 text-[10px] text-accent-blue">
              <Loader2 size={10} className="animate-spin" />
              {runningCount} running
            </span>
          )}
          {allDone && (
            <span className="text-[10px] text-accent-green font-medium">Complete</span>
          )}
        </div>
        <div className="flex items-center gap-2 text-[10px] text-dark-muted shrink-0">
          <span>{completedCount}/{totalCount} agents</span>
          <div className="w-16 h-1.5 bg-dark-border rounded-full overflow-hidden">
            <span
              className={`h-full block rounded-full transition-all duration-500 ${
                allDone ? 'bg-accent-green' : runningCount > 0 ? 'bg-accent-blue' : 'bg-accent-green'
              }`}
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>
      </div>

      {/* Tree list */}
      <div className="p-2 max-h-60 overflow-y-auto custom-scrollbar">
        {trees.map((tree, idx) => (
          <TreeNodeItem
            key={tree.agent.id}
            node={tree}
            depth={0}
            isLast={idx === trees.length - 1}
          />
        ))}
      </div>

      {/* Footer */}
      {allDone && (
        <div className="px-3 py-1.5 border-t border-dark-border bg-accent-green/5 text-[10px] text-accent-green flex items-center gap-2">
          <CheckCircle2 size={11} />
          All {totalCount} agent{totalCount > 1 ? 's' : ''} completed
        </div>
      )}
    </div>
  );
});

/* ───── Helpers ───── */
function countNodes(children: TreeNode[]): number {
  return children.reduce((sum, c) => sum + 1 + countNodes(c.children), 0);
}

function countCompleted(node: TreeNode): number {
  const self = node.agent.status === 'completed' ? 1 : 0;
  return self + node.children.reduce((sum, c) => sum + countCompleted(c), 0);
}

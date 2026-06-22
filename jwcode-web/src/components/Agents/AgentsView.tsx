import { AlertTriangle, CheckCircle2, Clock3, Loader2, Users } from 'lucide-react';
import { useSessionStore } from '../../stores/sessionStore';
import { useWorkflowStore, type BlackboardAgent } from '../../stores/workflowStore';

const statusStyle: Record<BlackboardAgent['status'], string> = {
  idle: 'bg-dark-hover text-dark-muted',
  scheduled: 'bg-accent-blue/15 text-accent-blue',
  running: 'bg-accent-blue/15 text-accent-blue',
  completed: 'bg-accent-green/15 text-accent-green',
  failed: 'bg-accent-red/15 text-accent-red',
};

const StatusIcon = ({ status }: { status: BlackboardAgent['status'] }) => {
  if (status === 'running') return <Loader2 size={14} className="text-accent-blue animate-spin" />;
  if (status === 'completed') return <CheckCircle2 size={14} className="text-accent-green" />;
  if (status === 'failed') return <AlertTriangle size={14} className="text-accent-red" />;
  return <Clock3 size={14} className="text-dark-muted" />;
};

const displaySource = (source: BlackboardAgent['source']) => {
  if (source === 'workflow') return 'Goal';
  if (source === 'agent') return 'Plan/Act';
  return source;
};

export function AgentsView() {
  const activeSessionId = useSessionStore((state) => state.activeSessionId);
  const agents = useWorkflowStore((state) =>
    activeSessionId ? state.getAgentsForSession(activeSessionId) : []
  );
  const events = useWorkflowStore((state) =>
    activeSessionId ? state.getEventsForSession(activeSessionId).slice(0, 8) : []
  );

  return (
    <div className="flex-1 flex flex-col overflow-hidden p-4">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold flex items-center gap-2">
          <Users size={20} />
          Agent Workspace
          <span className="text-sm font-normal text-dark-muted">({agents.length})</span>
        </h2>
        <div className="text-xs text-dark-muted">
          {activeSessionId ? `Session ${activeSessionId}` : 'No active session'}
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_320px] gap-4 min-h-0 flex-1">
        <div className="overflow-y-auto border border-dark-border rounded bg-dark-surface">
          {agents.length > 0 ? (
            <table className="w-full text-sm">
              <thead className="sticky top-0 bg-dark-bg text-dark-muted border-b border-dark-border">
                <tr>
                  <th className="text-left font-medium px-3 py-2">Agent</th>
                  <th className="text-left font-medium px-3 py-2">Status</th>
                  <th className="text-left font-medium px-3 py-2">Task</th>
                  <th className="text-left font-medium px-3 py-2">Source</th>
                  <th className="text-right font-medium px-3 py-2">Tokens</th>
                  <th className="text-left font-medium px-3 py-2">Error</th>
                </tr>
              </thead>
              <tbody>
                {agents.map((agent) => (
                  <tr key={agent.id} className="border-b border-dark-border/70 last:border-b-0">
                    <td className="px-3 py-2">
                      <div className="font-medium">{agent.name}</div>
                      <div className="text-xs text-dark-muted font-mono">{agent.id}</div>
                    </td>
                    <td className="px-3 py-2">
                      <span className={`inline-flex items-center gap-1 px-2 py-1 rounded text-xs ${statusStyle[agent.status]}`}>
                        <StatusIcon status={agent.status} />
                        {agent.status}
                      </span>
                    </td>
                    <td className="px-3 py-2 max-w-[320px]">
                      <div className="truncate">{agent.currentTask || '-'}</div>
                      {agent.phase && <div className="text-xs text-dark-muted">{agent.phase}</div>}
                    </td>
                    <td className="px-3 py-2 text-dark-muted">{displaySource(agent.source)}</td>
                    <td className="px-3 py-2 text-right tabular-nums">{agent.tokens ?? 0}</td>
                    <td className="px-3 py-2 text-accent-red text-xs max-w-[220px] truncate">{agent.error || '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <div className="h-full min-h-[240px] flex items-center justify-center text-center text-dark-muted">
              <div>
                <Users size={40} className="mx-auto mb-3 opacity-50" />
                <p>No agent activity yet</p>
                <p className="text-xs mt-1">Plan, Act, and Goal agents will appear here for the active session.</p>
              </div>
            </div>
          )}
        </div>

        <div className="border border-dark-border rounded bg-dark-surface overflow-hidden flex flex-col min-h-[240px]">
          <div className="px-3 py-2 border-b border-dark-border text-xs font-semibold">Recent Events</div>
          <div className="flex-1 overflow-y-auto">
            {events.length === 0 ? (
              <div className="text-xs text-dark-muted p-3">No events recorded.</div>
            ) : (
              <div className="divide-y divide-dark-border/70">
                {events.map((event) => (
                  <div key={event.id} className="px-3 py-2 text-xs">
                    <div className="flex items-center gap-2">
                      <span className="font-medium truncate">{event.title || event.type}</span>
                      <span className="ml-auto text-dark-muted">{new Date(event.timestamp).toLocaleTimeString()}</span>
                    </div>
                    {event.message && <div className="mt-1 text-dark-muted line-clamp-2">{event.message}</div>}
                    <div className="mt-1 text-[10px] text-dark-muted">{event.source}</div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default AgentsView;

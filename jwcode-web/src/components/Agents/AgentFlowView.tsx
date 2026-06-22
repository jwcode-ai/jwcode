import { useState } from 'react';
import { Activity, CheckCircle2, ChevronDown, Circle, Loader2, XCircle } from 'lucide-react';
import { useSessionStore } from '../../stores/sessionStore';
import { useWorkflowStore, type BlackboardAgent } from '../../stores/workflowStore';

const AGENT_ORDER = ['Orchestrator', 'Explorer', 'Architect', 'Coder', 'Tester', 'Reviewer', 'Debug', 'Documenter', 'Evaluator'];

const statusIcon = (status: BlackboardAgent['status']) => {
  if (status === 'running' || status === 'scheduled') return <Loader2 size={10} className="animate-spin text-accent-blue" />;
  if (status === 'completed') return <CheckCircle2 size={10} className="text-accent-green" />;
  if (status === 'failed') return <XCircle size={10} className="text-accent-red" />;
  return <Circle size={10} className="text-dark-muted" />;
};

const statusText = (status: BlackboardAgent['status']) => {
  if (status === 'running' || status === 'scheduled') return 'Running';
  if (status === 'completed') return 'Complete';
  if (status === 'failed') return 'Failed';
  return 'Idle';
};

const normalizeKey = (value: string) => value.toLowerCase();

function AgentRow({ agent }: { agent: BlackboardAgent }) {
  const active = agent.status === 'running' || agent.status === 'scheduled';
  return (
    <div className={`flex items-center gap-2 px-3 py-2 rounded border transition-colors ${
      active ? 'bg-accent-blue/10 border-accent-blue/40' :
      agent.status === 'completed' ? 'bg-accent-green/10 border-accent-green/30' :
      agent.status === 'failed' ? 'bg-accent-red/10 border-accent-red/30' :
      'border-dark-border'
    }`}>
      {statusIcon(agent.status)}
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="text-[11px] font-semibold truncate">{agent.name}</span>
          <span className="text-[9px] text-dark-muted shrink-0">{statusText(agent.status)}</span>
        </div>
        {agent.currentTask && <div className="text-[9px] text-dark-muted truncate mt-0.5">{agent.currentTask}</div>}
      </div>
    </div>
  );
}

export function AgentFlowView() {
  const [logExpanded, setLogExpanded] = useState(false);
  const activeSessionId = useSessionStore((state) => state.activeSessionId);
  const agents = useWorkflowStore((state) =>
    activeSessionId ? state.getAgentsForSession(activeSessionId) : []
  );
  const events = useWorkflowStore((state) =>
    activeSessionId ? state.getEventsForSession(activeSessionId).filter((event) => event.source === 'agent' || event.source === 'plan').slice(0, 50) : []
  );

  const byName = new Map(agents.map((agent) => [normalizeKey(agent.name), agent]));
  const orderedAgents = AGENT_ORDER
    .map((name) => byName.get(normalizeKey(name)) || byName.get(normalizeKey(name.replace('Orchestrator', 'orchestrator'))))
    .filter(Boolean) as BlackboardAgent[];
  const remainingAgents = agents.filter((agent) => !orderedAgents.some((item) => item.id === agent.id));
  const visibleAgents = [...orderedAgents, ...remainingAgents];
  const activeCount = visibleAgents.filter((agent) => agent.status === 'running' || agent.status === 'scheduled').length;

  return (
    <div className="h-full flex flex-col bg-dark-bg">
      <div className="shrink-0 px-3 py-2.5 border-b border-dark-border flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          <Activity size={14} className="text-accent-purple" />
          <span className="text-xs font-semibold text-dark-text">Agent Flow</span>
        </div>
        <div className="flex items-center gap-2 text-[9px] text-dark-muted">
          <span className="flex items-center gap-0.5"><Circle size={8} /> Idle</span>
          <span className="flex items-center gap-0.5"><Loader2 size={8} className="animate-spin text-accent-blue" /> Running</span>
          <span className="flex items-center gap-0.5"><CheckCircle2 size={8} className="text-accent-green" /> Done</span>
          <span className="flex items-center gap-0.5"><XCircle size={8} className="text-accent-red" /> Failed</span>
        </div>
      </div>

      <div className="flex-1 flex flex-col overflow-hidden">
        <div className="flex-1 overflow-y-auto overflow-x-hidden custom-scrollbar">
          <div className="px-3 py-3 space-y-2">
            {visibleAgents.length === 0 ? (
              <div className="min-h-[180px] flex items-center justify-center text-center text-dark-muted text-xs">
                <div>
                  <Activity size={28} className="mx-auto mb-2 opacity-50" />
                  <div>Waiting for agent activity</div>
                </div>
              </div>
            ) : (
              visibleAgents.map((agent) => <AgentRow key={agent.id} agent={agent} />)
            )}
          </div>
        </div>

        <div
          className="shrink-0 border-t border-dark-border transition-all duration-300"
          style={{ height: logExpanded ? 180 : 32 }}
        >
          <button
            onClick={() => setLogExpanded(!logExpanded)}
            className="w-full flex items-center justify-between px-3 py-1.5 hover:bg-dark-hover/30 transition-colors"
          >
            <span className="text-[10px] font-semibold text-dark-text flex items-center gap-1.5">
              Event Log
              <span className="px-1 py-0.5 rounded text-[8px] bg-dark-surface text-dark-muted">{events.length}</span>
              {activeCount > 0 && <span className="w-1.5 h-1.5 rounded-full bg-accent-blue animate-pulse" />}
            </span>
            <ChevronDown
              size={12}
              className="text-dark-muted transition-transform duration-300"
              style={{ transform: logExpanded ? 'rotate(180deg)' : 'rotate(0deg)' }}
            />
          </button>

          {logExpanded && (
            <div className="overflow-y-auto custom-scrollbar px-2" style={{ height: 146 }}>
              {events.length === 0 ? (
                <div className="py-4 text-[10px] text-dark-muted text-center">No flow events yet.</div>
              ) : (
                <div className="space-y-1 pb-2">
                  {events.map((event) => (
                    <div key={event.id} className="px-2 py-1.5 rounded border border-dark-border bg-dark-surface">
                      <div className="flex items-center gap-1 text-[9px]">
                        <span className="font-medium text-dark-text truncate">{event.title || event.type}</span>
                        <span className="text-dark-muted/70 ml-auto">
                          {new Date(event.timestamp).toLocaleTimeString()}
                        </span>
                      </div>
                      {event.message && <p className="text-[8px] text-dark-muted/80 mt-0.5 truncate">{event.message}</p>}
                      <div className="text-[8px] text-dark-muted/60 mt-0.5">{event.source}</div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default AgentFlowView;

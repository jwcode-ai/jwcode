import { useEffect, useState, useRef, useCallback } from 'react';
import { Activity, Circle, ArrowRight, CheckCircle2, XCircle, Loader2, RefreshCw } from 'lucide-react';
import wsService from '../../services/websocket';

// ---- Types ----

interface AgentFlowEvent {
  eventType: 'dispatch' | 'complete' | 'agent_status';
  fromAgent: string;
  toAgent: string;
  taskId: string;
  description: string;
  status: string;
  sessionId: string;
  timestamp: number;
}

interface AgentNode {
  name: string;
  type: string;
  color: string;
  status: 'idle' | 'busy' | 'completed' | 'failed';
  currentTask: string;
  currentTaskId: string;
  lastEvent: number;
}

interface FlowEdge {
  from: string;
  to: string;
  taskId: string;
  description: string;
  status: 'running' | 'completed' | 'failed';
  timestamp: number;
  active: boolean;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
// eslint-disable-next-line @typescript-eslint/no-explicit-any
// ---- Constants ----

const DEFAULT_DEF = { color: "#8b949e", label: "Agent" };

const AGENT_DEFS: Record<string, { color: string; label: string }> = {
  Orchestrator: { color: '#a855f7', label: 'Orchestrator' },
  explorer: { color: '#58a6ff', label: 'Explorer' },
  architect: { color: '#d29922', label: 'Architect' },
  coder: { color: '#238636', label: 'Coder' },
  tester: { color: '#f85149', label: 'Tester' },
  reviewer: { color: '#a371f7', label: 'Reviewer' },
  debug: { color: '#db6d28', label: 'Debug' },
  documenter: { color: '#1f6feb', label: 'Documenter' },
  evaluator: { color: '#56d364', label: 'Evaluator' },
  memory: { color: '#58a6ff', label: 'Memory' },
  compiler: { color: '#3b82f6', label: 'Compiler' },
  default: { color: '#8b949e', label: 'Agent' },
};

function getAgentDef(name: string): { color: string; label: string } {
  const key = name.toLowerCase();
  return AGENT_DEFS[key] || DEFAULT_DEF;
}

const SUB_AGENTS = ['explorer', 'architect', 'coder', 'tester', 'reviewer', 'debug', 'documenter', 'evaluator'];
const MAX_EDGE_HISTORY = 50;
const FLOW_TIMEOUT = 8000;

// ---- Helper: status icon ----

function StatusIcon({ status }: { status: string }) {
  switch (status) {
    case 'busy':
    case 'running':
      return <Loader2 size={14} className="animate-spin" />;
    case 'completed':
      return <CheckCircle2 size={14} />;
    case 'failed':
      return <XCircle size={14} />;
    default:
      return <Circle size={14} />;
  }
}

// ---- Main Component ----

export function AgentFlowView() {
  const [agents, setAgents] = useState<Map<string, AgentNode>>(() => {
    const m = new Map<string, AgentNode>();
    m.set('Orchestrator', {
      name: 'Orchestrator', type: 'orchestrator', color: '#a855f7',
      status: 'idle', currentTask: '', currentTaskId: '', lastEvent: Date.now(),
    });
    for (const name of SUB_AGENTS) {
      const def = getAgentDef(name);
      m.set(name, {
        name: def.label, type: name, color: def.color,
        status: 'idle', currentTask: '', currentTaskId: '', lastEvent: Date.now(),
      });
    }
    return m;
  });

  const [edges, setEdges] = useState<FlowEdge[]>([]);
  const [activeFlow, setActiveFlow] = useState<{ from: string; to: string; taskId: string } | null>(null);
  const [log, setLog] = useState<AgentFlowEvent[]>([]);
  const eventsRef = useRef<AgentFlowEvent[]>([]);
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  
  // ---- WebSocket subscription ----
  useEffect(() => {
    const unsub = wsService.onMessage((msg: any) => {
      if (msg.type === 'agent_flow_event' && msg.data) {
        handleEvent(msg.data);
      }
    });
    return () => unsub();
  }, []);

  const handleEvent = useCallback((evt: AgentFlowEvent) => {
    const now = Date.now();

    if (evt.eventType === 'dispatch') {
      setAgents(prev => {
        const next = new Map(prev);
        const from = next.get('Orchestrator');
        if (from) {
          from.status = 'busy';
          from.currentTask = evt.description;
          from.currentTaskId = evt.taskId;
          from.lastEvent = now;
        }
        const toKey = SUB_AGENTS.find(a => a.toLowerCase() === evt.toAgent.toLowerCase()) || evt.toAgent.toLowerCase();
        const to = next.get(toKey);
        if (to) {
          to.status = 'busy';
          to.currentTask = evt.description;
          to.currentTaskId = evt.taskId;
          to.lastEvent = now;
        }
        return next;
      });
      setActiveFlow({ from: 'Orchestrator', to: getAgentDef(evt.toAgent).label, taskId: evt.taskId });
      setEdges(prevEdges => {
        const newEdge: FlowEdge = {
          from: 'Orchestrator', to: getAgentDef(evt.toAgent).label, taskId: evt.taskId,
          description: evt.description, status: 'running', timestamp: now, active: true,
        };
        return [...prevEdges, newEdge].slice(-MAX_EDGE_HISTORY);
      });
    } else if (evt.eventType === 'complete') {
      const toKey = SUB_AGENTS.find(a => a.toLowerCase() === evt.toAgent.toLowerCase()) || evt.toAgent.toLowerCase();
      setAgents(prev => {
        const next = new Map(prev);
        const agent = next.get(toKey);
        if (agent) {
          agent.status = evt.status === 'completed' ? 'completed' : 'failed';
          agent.lastEvent = now;
          setTimeout(() => {
            setAgents(p => {
              const n = new Map(p);
              const a = n.get(toKey);
              if (a) a.status = 'idle';
              const o = n.get('Orchestrator');
              if (o) o.status = 'idle';
              return n;
            });
          }, FLOW_TIMEOUT);
        }
        return next;
      });
      setEdges(prevEdges =>
        prevEdges.map(e =>
          e.taskId === evt.taskId && e.to === getAgentDef(evt.toAgent).label
            ? { ...e, status: evt.status === 'completed' ? 'completed' : 'failed', active: false }
            : e
        )
      );
      setActiveFlow(null);
    }

    // Add to log
    eventsRef.current = [evt, ...eventsRef.current].slice(0, 50);
    setLog(eventsRef.current);
  }, []);



  // ---- Layout helpers ----
  const ORCHESTRATOR_Y = 60;
  const AGENT_START_Y = 170;
  const AGENT_GAP_Y = 100;

  const getAgentPosition = (index: number) => ({
    x: 160 + (index % 4) * 200,
    y: AGENT_START_Y + Math.floor(index / 4) * AGENT_GAP_Y,
  });

  // ---- Render ----
  return (
    <div className="h-full flex flex-col bg-dark-bg">
      {/* Header */}
      <div className="shrink-0 px-4 py-3 border-b border-dark-border flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Activity size={16} className="text-accent-purple" />
          <span className="text-sm font-semibold text-dark-text">Agent 信息流</span>
        </div>
        <div className="flex items-center gap-3 text-[11px] text-dark-muted">
          <span className="flex items-center gap-1">
            <Circle size={10} className="text-dark-muted" /> 空闲
          </span>
          <span className="flex items-center gap-1">
            <Loader2 size={10} className="text-accent-blue animate-spin" /> 处理中
          </span>
          <span className="flex items-center gap-1">
            <CheckCircle2 size={10} className="text-accent-green" /> 完成
          </span>
          <span className="flex items-center gap-1">
            <XCircle size={10} className="text-accent-red" /> 失败
          </span>
        </div>
      </div>

      {/* Flow canvas */}
      <div className="flex-1 flex overflow-hidden">
        <div className="flex-1 relative overflow-auto">
          <svg
            width="100%"
            height="100%"
            className="absolute inset-0"
            style={{ minWidth: 900, minHeight: 500 }}
          >
            <defs>
              <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="10" refY="3.5" orient="auto">
                <polygon points="0 0, 10 3.5, 0 7" fill="#58a6ff" />
              </marker>
            </defs>

            {/* Connection curves from Orchestrator to each agent */}
            {SUB_AGENTS.map((agentName, i) => {
              const pos = getAgentPosition(i);
              const agent = agents.get(agentName);
              const isActive = activeFlow && activeFlow.to === (agent?.name || agentName);
              const edge = edges.find(e => e.to === (agent?.name || agentName) && e.active);
              const strokeColor = isActive
                ? '#a855f7'
                : edge?.status === 'completed'
                  ? '#238636'
                  : edge?.status === 'failed'
                    ? '#f85149'
                    : '#30363d';
              const strokeWidth = isActive ? 3 : 1.5;
              const opacity = isActive ? 1 : 0.4;

              return (
                <g key={agentName}>
                  <path
                    d={`M 300,${ORCHESTRATOR_Y + 30} C 300,${(ORCHESTRATOR_Y + 30 + pos.y) / 2}, ${300 + (pos.x - 300) * 0.3},${(ORCHESTRATOR_Y + 30 + pos.y) / 2}, ${pos.x + 60},${pos.y}`}
                    fill="none"
                    stroke={strokeColor}
                    strokeWidth={strokeWidth}
                    opacity={opacity}
                    markerEnd={isActive ? 'url(#arrowhead)' : undefined}
                    className={isActive ? 'animate-pulse' : ''}
                  />
                  {/* Animated dot along path */}
                  {isActive && (
                    <circle r="4" fill="#a855f7" filter="url(#glow)">
                      <animateMotion
                        dur="2s"
                        repeatCount="indefinite"
                        path={`M 300,${ORCHESTRATOR_Y + 30} C 300,${(ORCHESTRATOR_Y + 30 + pos.y) / 2}, ${300 + (pos.x - 300) * 0.3},${(ORCHESTRATOR_Y + 30 + pos.y) / 2}, ${pos.x + 60},${pos.y}`}
                      />
                    </circle>
                  )}
                </g>
              );
            })}
          </svg>

          {/* Orchestrator node */}
          <div
            className="absolute z-10"
            style={{ left: 240, top: ORCHESTRATOR_Y, width: 120 }}
          >
            <div
              className="flex flex-col items-center p-3 rounded-lg border"
              style={{
                backgroundColor: 'rgba(168, 85, 247, 0.1)',
                borderColor: agents.get('Orchestrator')?.status === 'busy' ? '#a855f7' : '#30363d',
                boxShadow: agents.get('Orchestrator')?.status === 'busy'
                  ? '0 0 20px rgba(168, 85, 247, 0.3)' : 'none',
              }}
            >
              <div className="flex items-center gap-1.5 mb-1">
                <div
                  className="w-2 h-2 rounded-full"
                  style={{
                    backgroundColor: agents.get('Orchestrator')?.status === 'busy' ? '#a855f7' : '#8b949e',
                    boxShadow: agents.get('Orchestrator')?.status === 'busy'
                      ? '0 0 8px #a855f7' : 'none',
                  }}
                />
                <span className="text-xs font-bold text-purple-400">Orchestrator</span>
              </div>
              {agents.get('Orchestrator')?.currentTask && (
                <span className="text-[10px] text-dark-muted text-center truncate max-w-[100px]">
                  {agents.get('Orchestrator')?.currentTask}
                </span>
              )}
            </div>
          </div>

          {/* Agent nodes */}
          {SUB_AGENTS.map((agentName, i) => {
            const pos = getAgentPosition(i);
            const agent = agents.get(agentName);
            if (!agent) return null;
            const isActive = activeFlow?.to === agent.name;

            return (
              <div
                key={agentName}
                className="absolute z-10"
                style={{ left: pos.x, top: pos.y, width: 130 }}
              >
                <div
                  className="flex flex-col items-center p-2.5 rounded-lg border transition-all duration-300"
                  style={{
                    backgroundColor: isActive
                      ? `rgba(${parseInt(agent.color.slice(1,3), 16)}, ${parseInt(agent.color.slice(3,5), 16)}, ${parseInt(agent.color.slice(5,7), 16)}, 0.15)`
                      : 'rgba(22, 27, 34, 0.8)',
                    borderColor: isActive ? agent.color : '#30363d',
                    boxShadow: isActive ? `0 0 15px ${agent.color}40` : 'none',
                  }}
                >
                  <div className="flex items-center gap-1.5 mb-0.5">
                    <div
                      className="w-2 h-2 rounded-full transition-all duration-300"
                      style={{
                        backgroundColor: agent.status === 'idle' ? '#8b949e'
                          : agent.status === 'busy' ? agent.color
                            : agent.status === 'completed' ? '#238636'
                              : '#f85149',
                        boxShadow: isActive ? `0 0 8px ${agent.color}` : 'none',
                      }}
                    />
                    <span className="text-[11px] font-semibold text-dark-text">{agent.name}</span>
                  </div>
                  {agent.currentTask && (
                    <div className="flex items-center gap-1 mt-0.5 max-w-[110px]">
                      <span className="text-[9px] text-dark-muted truncate">{agent.currentTask}</span>
                      {agent.status === 'busy' && (
                        <Loader2 size={10} className="animate-spin shrink-0" style={{ color: agent.color }} />
                      )}
                    </div>
                  )}
                  {/* Task ID subtag */}
                  {agent.currentTaskId && agent.status === 'busy' && (
                    <span className="text-[8px] text-dark-muted/50 mt-0.5 font-mono">
                      {agent.currentTaskId.substring(0, 8)}
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>

        {/* Event log sidebar */}
        <div className="w-72 shrink-0 border-l border-dark-border flex flex-col">
          <div className="shrink-0 px-3 py-2 border-b border-dark-border">
            <span className="text-xs font-semibold text-dark-text">事件日志</span>
          </div>
          <div className="flex-1 overflow-y-auto custom-scrollbar">
            {log.length === 0 ? (
              <div className="px-3 py-4 text-[11px] text-dark-muted text-center">
                等待 Agent 活动...
              </div>
            ) : (
              log.map((evt, i) => (
                <div
                  key={`${evt.taskId}-${i}`}
                  className="px-3 py-1.5 border-b border-dark-border/50 hover:bg-dark-hover/30 transition-colors"
                >
                  <div className="flex items-center gap-1.5">
                    <StatusIcon status={evt.eventType === 'dispatch' ? 'running' : evt.status} />
                    <span className="text-[10px] font-medium" style={{
                      color: evt.eventType === 'dispatch' ? '#58a6ff'
                        : evt.status === 'completed' ? '#238636'
                          : evt.status === 'failed' ? '#f85149' : '#8b949e',
                    }}>
                      {evt.eventType === 'dispatch' ? '分发' : evt.eventType === 'complete' ? '完成' : '状态'}
                    </span>
                    <span className="text-[10px] text-dark-muted ml-auto">
                      {new Date(evt.timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                    </span>
                  </div>
                  <div className="flex items-center gap-1 mt-0.5">
                    <span className="text-[10px] text-dark-muted">{evt.fromAgent}</span>
                    <ArrowRight size={10} className="text-dark-muted" />
                    <span className="text-[10px] text-dark-muted">{evt.toAgent || evt.fromAgent}</span>
                  </div>
                  {evt.description && (
                    <p className="text-[9px] text-dark-muted/70 mt-0.5 truncate">{evt.description}</p>
                  )}
                </div>
              ))
            )}
          </div>
          {log.length > 0 && (
            <div className="shrink-0 px-3 py-1.5 border-t border-dark-border">
              <button
                onClick={() => { eventsRef.current = []; setLog([]); }}
                className="text-[10px] text-dark-muted hover:text-dark-text transition-colors flex items-center gap-1"
              >
                <RefreshCw size={10} /> 清空日志
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default AgentFlowView;


import { useEffect, useState, useRef, useCallback } from 'react';
import { Activity, Circle, ArrowRight, CheckCircle2, XCircle, Loader2, RefreshCw, ChevronDown } from 'lucide-react';
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

// ---- Constants ----

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
};

function getAgentDef(name: string): { color: string; label: string } {
  const key = name.toLowerCase();
  return AGENT_DEFS[key] || { color: '#8b949e', label: name };
}

const SUB_AGENTS = ['explorer', 'architect', 'coder', 'tester', 'reviewer', 'debug', 'documenter', 'evaluator'];
const FLOW_TIMEOUT = 8000;

// ---- Status icon ----

function StatusIcon({ status, size }: { status: string; size?: number }) {
  const s = size || 10;
  switch (status) {
    case 'busy':
    case 'running':
      return <Loader2 size={s} className="animate-spin" />;
    case 'completed':
      return <CheckCircle2 size={s} />;
    case 'failed':
      return <XCircle size={s} />;
    default:
      return <Circle size={s} />;
  }
}

// ---- Compact agent row ----

function AgentRow({
  agent,
  isActive,
}: {
  agent: AgentNode;
  isActive: boolean;
}) {
  const statusColor =
    agent.status === 'busy' ? agent.color
    : agent.status === 'completed' ? '#238636'
    : agent.status === 'failed' ? '#f85149'
    : '#484f58';

  return (
    <div
      className="flex items-center gap-2 px-3 py-2 rounded-lg border transition-all duration-500"
      style={{
        backgroundColor: isActive
          ? `${agent.color}12`
          : agent.status === 'completed' ? '#23863608'
          : agent.status === 'failed' ? '#f8514908'
          : 'transparent',
        borderColor: isActive ? `${agent.color}60`
          : agent.status === 'completed' ? '#23863630'
          : agent.status === 'failed' ? '#f8514930'
          : '#30363d',
        boxShadow: isActive ? `0 0 12px ${agent.color}20` : 'none',
      }}
    >
      {/* Status dot */}
      <div className="relative shrink-0">
        <div
          className="w-2.5 h-2.5 rounded-full transition-all duration-500"
          style={{
            backgroundColor: statusColor,
            boxShadow: agent.status === 'busy' ? `0 0 8px ${agent.color}` : 'none',
          }}
        />
        {agent.status === 'busy' && (
          <div
            className="absolute inset-0 rounded-full animate-ping"
            style={{ backgroundColor: agent.color, opacity: 0.3 }}
          />
        )}
      </div>

      {/* Info */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-1.5">
          <span className="text-[11px] font-semibold truncate" style={{ color: agent.color }}>
            {agent.name}
          </span>
          <span className="text-[9px] shrink-0" style={{ color: statusColor }}>
            {agent.status === 'idle' ? '空闲'
              : agent.status === 'busy' ? '处理中'
              : agent.status === 'completed' ? '完成'
              : '失败'}
          </span>
        </div>
        {agent.currentTask && (
          <p className="text-[9px] text-dark-muted truncate mt-0.5 leading-tight">
            {agent.currentTask}
          </p>
        )}
      </div>

      {agent.status === 'busy' && <Loader2 size={10} className="animate-spin shrink-0" style={{ color: agent.color }} />}
    </div>
  );
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

  const [activeFlows, setActiveFlows] = useState<Set<string>>(new Set());
  const [log, setLog] = useState<AgentFlowEvent[]>([]);
  const eventsRef = useRef<AgentFlowEvent[]>([]);
  const [logExpanded, setLogExpanded] = useState(false);

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
      const toKey = SUB_AGENTS.find(a => a.toLowerCase() === evt.toAgent.toLowerCase()) || evt.toAgent.toLowerCase();

      setAgents(prev => {
        const next = new Map(prev);
        const orch = next.get('Orchestrator');
        if (orch) {
          orch.status = 'busy';
          orch.currentTask = evt.description;
          orch.currentTaskId = evt.taskId;
          orch.lastEvent = now;
        }
        const to = next.get(toKey);
        if (to) {
          to.status = 'busy';
          to.currentTask = evt.description;
          to.currentTaskId = evt.taskId;
          to.lastEvent = now;
        }
        return next;
      });

      setActiveFlows(prev => new Set(prev).add(toKey));
    } else if (evt.eventType === 'complete') {
      const toKey = SUB_AGENTS.find(a => a.toLowerCase() === evt.toAgent.toLowerCase()) || evt.toAgent.toLowerCase();

      setAgents(prev => {
        const next = new Map(prev);
        const agent = next.get(toKey);
        if (agent) {
          agent.status = evt.status === 'completed' ? 'completed' : 'failed';
          agent.lastEvent = now;
        }
        return next;
      });

      setTimeout(() => {
        setAgents(p => {
          const n = new Map(p);
          const a = n.get(toKey);
          if (a) a.status = 'idle';
          const o = n.get('Orchestrator');
          if (o) o.status = 'idle';
          return n;
        });
        setActiveFlows(prev => {
          const next = new Set(prev);
          next.delete(toKey);
          return next;
        });
      }, FLOW_TIMEOUT);
    }

    eventsRef.current = [evt, ...eventsRef.current].slice(0, 50);
    setLog(eventsRef.current);
  }, []);

  const orch = agents.get('Orchestrator');

  // ---- Test simulation ----
  const runTestFlow = useCallback(() => {
    const tasks = [
      { agent: 'explorer', desc: '分析代码库结构' },
      { agent: 'architect', desc: '设计系统架构' },
      { agent: 'coder', desc: '实现核心功能' },
      { agent: 'tester', desc: '编写单元测试' },
      { agent: 'reviewer', desc: '代码审查' },
      { agent: 'debug', desc: '调试问题' },
      { agent: 'documenter', desc: '编写 API 文档' },
      { agent: 'evaluator', desc: '评估代码质量' },
    ];

    tasks.forEach((task, i) => {
      const taskId = `test-${Date.now()}-${i}`;
      // Dispatch
      setTimeout(() => {
        handleEvent({
          eventType: 'dispatch',
          fromAgent: 'Orchestrator',
          toAgent: task.agent,
          taskId,
          description: task.desc,
          status: 'running',
          sessionId: 'test-session',
          timestamp: Date.now(),
        });
      }, i * 600);

      // Complete
      setTimeout(() => {
        const success = i !== 5; // debug fails for variety
        handleEvent({
          eventType: 'complete',
          fromAgent: task.agent,
          toAgent: 'Orchestrator',
          taskId,
          description: '',
          status: success ? 'completed' : 'failed',
          sessionId: 'test-session',
          timestamp: Date.now(),
        });
      }, i * 600 + 2500);
    });
  }, [handleEvent]);

  // ---- Render ----
  return (
    <div className="h-full flex flex-col bg-dark-bg">
      {/* Header */}
      <div className="shrink-0 px-3 py-2.5 border-b border-dark-border flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          <Activity size={14} className="text-accent-purple" />
          <span className="text-xs font-semibold text-dark-text">Agent 信息流</span>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={runTestFlow}
            className="text-[9px] px-2 py-0.5 rounded bg-accent-purple/20 text-accent-purple hover:bg-accent-purple/30 transition-colors"
          >
            模拟测试
          </button>
          <span className="flex items-center gap-0.5 text-[9px] text-dark-muted"><Circle size={8} className="text-dark-muted" /> 空闲</span>
          <span className="flex items-center gap-0.5 text-[9px] text-dark-muted"><Loader2 size={8} className="text-accent-blue animate-spin" /> 处理中</span>
          <span className="flex items-center gap-0.5 text-[9px] text-dark-muted"><CheckCircle2 size={8} className="text-accent-green" /> 完成</span>
          <span className="flex items-center gap-0.5 text-[9px] text-dark-muted"><XCircle size={8} className="text-accent-red" /> 失败</span>
        </div>
      </div>

      {/* Vertical river flow */}
      <div className="flex-1 flex flex-col overflow-hidden">
        <div className="flex-1 overflow-y-auto overflow-x-hidden custom-scrollbar">
          <div className="px-3 py-3 space-y-0">
            {/* Orchestrator — river source */}
            <div className="relative">
              <div
                className="flex items-center gap-2 px-3 py-2.5 rounded-lg border transition-all duration-500"
                style={{
                  backgroundColor: activeFlows.size > 0 ? '#a855f712' : 'transparent',
                  borderColor: activeFlows.size > 0 ? '#a855f760' : '#30363d',
                  boxShadow: activeFlows.size > 0 ? '0 0 16px #a855f720' : 'none',
                }}
              >
                <div className="relative shrink-0">
                  <div
                    className="w-3 h-3 rounded-full transition-all duration-500"
                    style={{
                      backgroundColor: orch?.status === 'busy' ? '#a855f7' : '#484f58',
                      boxShadow: orch?.status === 'busy' ? '0 0 10px #a855f7' : 'none',
                    }}
                  />
                  {orch?.status === 'busy' && (
                    <div className="absolute inset-0 rounded-full animate-ping" style={{ backgroundColor: '#a855f7', opacity: 0.3 }} />
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-1.5">
                    <span className="text-[12px] font-bold text-purple-400">Orchestrator</span>
                    <span className="text-[9px] text-dark-muted">
                      {orch?.status === 'busy' ? '调度中' : '就绪'}
                    </span>
                  </div>
                  {orch?.currentTask && (
                    <p className="text-[9px] text-dark-muted truncate mt-0.5">{orch.currentTask}</p>
                  )}
                </div>
                {orch?.status === 'busy' && <Loader2 size={12} className="animate-spin shrink-0 text-purple-400" />}
              </div>

              {/* Vertical river connector — from Orchestrator down */}
              <div
                className="flex justify-center py-0.5"
                style={{ height: 16 }}
              >
                <div className="relative flex flex-col items-center" style={{ width: 2 }}>
                  <div
                    className="flex-1 w-full rounded-full transition-all duration-700"
                    style={{
                      backgroundColor: activeFlows.size > 0 ? '#a855f760' : '#30363d',
                      boxShadow: activeFlows.size > 0 ? '0 0 6px #a855f740' : 'none',
                    }}
                  />
                  {activeFlows.size > 0 && (
                    <div
                      className="absolute w-1.5 h-1.5 rounded-full"
                      style={{
                        backgroundColor: '#a855f7',
                        boxShadow: '0 0 8px #a855f7',
                        animation: 'verticalDrift 1.5s ease-in-out infinite',
                      }}
                    />
                  )}
                </div>
              </div>
            </div>

            {/* Sub-agents in 2-column grid */}
            <div className="grid grid-cols-2 gap-x-2 gap-y-0">
              {SUB_AGENTS.map((agentName, i) => {
                const agent = agents.get(agentName);
                if (!agent) return null;
                const isActive = activeFlows.has(agentName);

                // Branch connector
                const colIndex = i % 2;
                const isLeftCol = colIndex === 0;

                return (
                  <div key={agentName}>
                    {/* Horizontal branch connector from center river to agent */}
                    <div className="flex items-center" style={{ height: 14 }}>
                      <div
                        className="flex-1"
                        style={{
                          height: 2,
                          backgroundColor: isActive ? `${getAgentDef(agentName).color}40` : '#30363d',
                          boxShadow: isActive ? `0 0 4px ${getAgentDef(agentName).color}40` : 'none',
                          marginLeft: isLeftCol ? '50%' : undefined,
                          marginRight: isLeftCol ? undefined : '50%',
                        }}
                      />
                      <div className="flex flex-col items-center shrink-0" style={{ width: 2 }}>
                        <div
                          className="flex-1 w-full"
                          style={{
                            backgroundColor: isActive ? `${getAgentDef(agentName).color}60` : '#30363d',
                          }}
                        />
                      </div>
                    </div>

                    {/* Agent row */}
                    <AgentRow agent={agent} isActive={isActive} />

                    {/* Vertical river segment between rows */}
                    {i < SUB_AGENTS.length - 2 && colIndex === 1 && (() => {
                      const next1 = SUB_AGENTS[i + 1];
                      const next2 = SUB_AGENTS[i + 2];
                      const hasFlow = (next1 && activeFlows.has(next1)) || (next2 && activeFlows.has(next2));
                      return (
                        <div className="flex justify-center" style={{ height: 8 }}>
                          <div
                            className="w-0.5 h-full rounded-full"
                            style={{ backgroundColor: hasFlow ? '#30363d80' : '#30363d' }}
                          />
                        </div>
                      );
                    })()}
                  </div>
                );
              })}
            </div>

            {/* River endpoint */}
            <div className="flex justify-center py-1">
              <div className="flex flex-col items-center gap-1">
                <div style={{ width: 2, height: 12, backgroundColor: '#30363d', borderRadius: 2 }} />
                <div className="w-6 h-6 rounded-full border border-dashed border-dark-border flex items-center justify-center">
                  <CheckCircle2 size={10} className="text-dark-muted" />
                </div>
                <span className="text-[8px] text-dark-muted">输出</span>
              </div>
            </div>
          </div>
        </div>

        {/* Event log — bottom panel */}
        <div
          className="shrink-0 border-t border-dark-border transition-all duration-300"
          style={{ height: logExpanded ? 180 : 32 }}
        >
          <button
            onClick={() => setLogExpanded(!logExpanded)}
            className="w-full flex items-center justify-between px-3 py-1.5 hover:bg-dark-hover/30 transition-colors"
          >
            <span className="text-[10px] font-semibold text-dark-text flex items-center gap-1.5">
              事件日志
              {log.length > 0 && (
                <span className="px-1 py-0.5 rounded text-[8px] bg-dark-surface text-dark-muted">
                  {log.length}
                </span>
              )}
            </span>
            <ChevronDown
              size={12}
              className="text-dark-muted transition-transform duration-300"
              style={{ transform: logExpanded ? 'rotate(180deg)' : 'rotate(0deg)' }}
            />
          </button>

          {logExpanded && (
            <div className="overflow-y-auto custom-scrollbar px-2" style={{ height: 146 }}>
              {log.length === 0 ? (
                <div className="py-4 text-[10px] text-dark-muted text-center">
                  等待 Agent 活动...
                </div>
              ) : (
                <div className="space-y-1 pb-2">
                  {log.map((evt, i) => {
                    const agentDef = getAgentDef(evt.toAgent || evt.fromAgent);
                    return (
                      <div
                        key={`${evt.taskId}-${i}`}
                        className="px-2 py-1.5 rounded border transition-all"
                        style={{
                          backgroundColor: evt.eventType === 'dispatch'
                            ? `${agentDef.color}08`
                            : evt.status === 'completed' ? '#23863608'
                            : evt.status === 'failed' ? '#f8514908'
                            : '#161b22',
                          borderColor: evt.eventType === 'dispatch'
                            ? `${agentDef.color}30`
                            : evt.status === 'completed' ? '#23863630'
                            : evt.status === 'failed' ? '#f8514930'
                            : '#30363d',
                        }}
                      >
                        <div className="flex items-center gap-1">
                          <StatusIcon status={evt.eventType === 'dispatch' ? 'running' : evt.status} size={8} />
                          <span
                            className="text-[9px] font-medium"
                            style={{
                              color: evt.eventType === 'dispatch' ? agentDef.color
                                : evt.status === 'completed' ? '#238636'
                                : evt.status === 'failed' ? '#f85149' : '#8b949e',
                            }}
                          >
                            {evt.eventType === 'dispatch' ? '分发'
                              : evt.eventType === 'complete' ? '完成' : '状态'}
                          </span>
                          <span className="text-[8px] text-dark-muted/50 ml-auto">
                            {new Date(evt.timestamp).toLocaleTimeString('zh-CN', {
                              hour: '2-digit', minute: '2-digit', second: '2-digit',
                            })}
                          </span>
                        </div>
                        <div className="flex items-center gap-1 mt-0.5">
                          <span className="text-[9px] text-dark-muted">{evt.fromAgent}</span>
                          <ArrowRight size={8} className="text-dark-muted" />
                          <span className="text-[9px]" style={{ color: agentDef.color }}>
                            {evt.toAgent || evt.fromAgent}
                          </span>
                        </div>
                        {evt.description && (
                          <p className="text-[8px] text-dark-muted/60 mt-0.5 truncate">{evt.description}</p>
                        )}
                      </div>
                    );
                  })}
                  <button
                    onClick={() => { eventsRef.current = []; setLog([]); }}
                    className="w-full py-1 rounded border border-dark-border hover:border-dark-muted transition-colors text-[9px] text-dark-muted hover:text-dark-text flex items-center justify-center gap-1"
                  >
                    <RefreshCw size={8} />
                    清空日志
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      <style>{`
        @keyframes verticalDrift {
          0%   { top: 0%; opacity: 1; }
          50%  { top: 50%; opacity: 0.5; }
          100% { top: 100%; opacity: 1; }
        }
      `}</style>
    </div>
  );
}

export default AgentFlowView;

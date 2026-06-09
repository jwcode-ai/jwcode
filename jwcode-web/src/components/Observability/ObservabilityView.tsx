import { useEffect, useState, useCallback } from 'react';
import { Activity, RefreshCw, AlertTriangle, BarChart3, DollarSign, History, Zap, Wrench, AlertCircle, Cpu, Clock, ChevronLeft } from 'lucide-react';
import { api } from '../../services/api';
import type { ObservabilitySummary, CostData, TraceRunSummary, TraceRunDetail, TraceEvent } from '../../types';

type SubTab = 'overview' | 'costs' | 'history';

function formatTokens(n: number): string {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M';
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K';
  return String(n);
}

function formatCost(d: number): string {
  return '$' + d.toFixed(4);
}

function formatDuration(sec: number): string {
  if (sec < 60) return Math.round(sec) + 's';
  if (sec < 3600) return Math.round(sec / 60) + 'm ' + Math.round(sec % 60) + 's';
  const h = Math.floor(sec / 3600);
  const m = Math.round((sec % 3600) / 60);
  return h + 'h ' + m + 'm';
}

function formatTime(iso: string): string {
  if (!iso) return '-';
  const d = new Date(iso);
  return d.toLocaleString();
}

const EVENT_COLORS: Record<string, string> = {
  StepStart: 'bg-blue-500/20 text-blue-300',
  Thinking: 'bg-purple-500/20 text-purple-300',
  ToolCall: 'bg-cyan-500/20 text-cyan-300',
  ToolResult: 'bg-teal-500/20 text-teal-300',
  TokenUsage: 'bg-green-500/20 text-green-300',
  Error: 'bg-red-500/20 text-red-300',
  Checkpoint: 'bg-yellow-500/20 text-yellow-300',
  StepComplete: 'bg-blue-500/20 text-blue-300',
  ContentChunk: 'bg-gray-500/20 text-gray-300',
  ThinkingChunk: 'bg-purple-500/20 text-purple-300',
  TaskStateChanged: 'bg-orange-500/20 text-orange-300',
  TaskPlanUpdated: 'bg-orange-500/20 text-orange-300',
  ContextCompressed: 'bg-pink-500/20 text-pink-300',
  StepPrompt: 'bg-indigo-500/20 text-indigo-300',
  WaitingForInput: 'bg-yellow-500/20 text-yellow-300',
};

export function ObservabilityView() {
  const [activeSubTab, setActiveSubTab] = useState<SubTab>('overview');
  const [summary, setSummary] = useState<ObservabilitySummary | null>(null);
  const [costData, setCostData] = useState<CostData | null>(null);
  const [runs, setRuns] = useState<TraceRunSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Run detail state
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
  const [runDetail, setRunDetail] = useState<TraceRunDetail | null>(null);
  const [runEvents, setRunEvents] = useState<TraceEvent[]>([]);
  const [eventsTotal, setEventsTotal] = useState(0);
  const [eventsPage, setEventsPage] = useState(0);
  const [loadingEvents, setLoadingEvents] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [summaryRes, costsRes, runsRes] = await Promise.all([
        api.observability.summary(),
        api.observability.costs(),
        api.observability.listRuns(),
      ]);
      if (summaryRes.success && summaryRes.data) setSummary(summaryRes.data);
      if (costsRes.success && costsRes.data) setCostData(costsRes.data);
      if (runsRes.success && runsRes.data) setRuns(runsRes.data);
    } catch {
      setError('Failed to load observability data');
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const loadRunDetail = useCallback(async (runId: string) => {
    setSelectedRunId(runId);
    setRunDetail(null);
    setRunEvents([]);
    setEventsPage(0);
    const [detailRes, eventsRes] = await Promise.all([
      api.observability.getRun(runId),
      api.observability.getRunEvents(runId, 0, 50),
    ]);
    if (detailRes.success && detailRes.data) setRunDetail(detailRes.data);
    if (eventsRes.success && eventsRes.data) {
      setRunEvents(eventsRes.data.events);
      setEventsTotal(eventsRes.data.total);
    }
  }, []);

  const loadMoreEvents = async () => {
    if (!selectedRunId) return;
    setLoadingEvents(true);
    const nextPage = eventsPage + 1;
    const res = await api.observability.getRunEvents(selectedRunId, nextPage, 50);
    if (res.success && res.data) {
      setRunEvents(prev => [...prev, ...res.data!.events]);
      setEventsTotal(res.data.total);
      setEventsPage(nextPage);
    }
    setLoadingEvents(false);
  };

  const handleBackToRuns = () => {
    setSelectedRunId(null);
    setRunDetail(null);
    setRunEvents([]);
  };

  // ---- Loading state ----
  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <div className="text-center">
          <RefreshCw size={32} className="animate-spin mx-auto mb-2 text-accent-blue" />
          <p className="text-dark-muted">Loading...</p>
        </div>
      </div>
    );
  }

  // ---- Error state ----
  if (error) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <div className="text-center">
          <AlertTriangle size={32} className="mx-auto mb-2 text-accent-red" />
          <p className="text-accent-red mb-4">{error}</p>
          <button onClick={loadData} className="px-4 py-2 bg-accent-blue text-white rounded-lg hover:bg-accent-blue/80">
            Retry
          </button>
        </div>
      </div>
    );
  }

  // ---- Run detail view ----
  if (activeSubTab === 'history' && selectedRunId) {
    return (
      <div className="flex-1 flex flex-col overflow-hidden p-4">
        <div className="flex items-center gap-3 mb-4">
          <button onClick={handleBackToRuns} className="p-1.5 rounded hover:bg-dark-hover text-dark-muted hover:text-dark-text transition-colors">
            <ChevronLeft size={20} />
          </button>
          <h2 className="text-lg font-semibold text-dark-text truncate">{selectedRunId}</h2>
          {runDetail && (
            <span className={`px-2 py-0.5 rounded text-xs ${runDetail.status === 'passed' ? 'bg-accent-green/20 text-accent-green' : runDetail.status === 'failed' ? 'bg-accent-red/20 text-accent-red' : 'bg-dark-hover text-dark-muted'}`}>
              {runDetail.status}
            </span>
          )}
        </div>

        {runDetail && (
          <div className="flex flex-wrap gap-4 mb-4 text-sm text-dark-muted">
            <span>Scenario: <span className="text-dark-text">{runDetail.scenarioId || '-'}</span></span>
            <span>Events: <span className="text-dark-text">{runDetail.eventCount}</span></span>
            <span>Started: <span className="text-dark-text">{formatTime(runDetail.startedAt)}</span></span>
            {runDetail.completedAt && <span>Completed: <span className="text-dark-text">{formatTime(runDetail.completedAt)}</span></span>}
          </div>
        )}

        {runDetail?.summary && (
          <div className="mb-4 p-3 rounded-lg bg-dark-surface border border-dark-border text-sm text-dark-text">
            {runDetail.summary}
          </div>
        )}

        <div className="text-sm text-dark-muted mb-2">Events ({eventsTotal} total)</div>
        <div className="flex-1 overflow-y-auto space-y-1.5">
          {runEvents.map((evt, idx) => (
            <div key={idx} className="flex items-start gap-3 p-2 rounded bg-dark-surface border border-dark-border text-sm">
              <span className={`px-1.5 py-0.5 rounded text-xs font-medium shrink-0 ${EVENT_COLORS[evt.eventType] || 'bg-dark-hover text-dark-muted'}`}>
                {evt.eventType}
              </span>
              <span className="text-dark-muted text-xs shrink-0 mt-0.5">{formatTime(evt.timestamp)}</span>
              <span className="text-dark-text truncate flex-1">
                {evt.eventType === 'TokenUsage' && evt.event && `prompt=${evt.event.promptTokens} completion=${evt.event.completionTokens} model=${evt.event.model}`}
                {evt.eventType === 'ToolCall' && evt.event && `${evt.event.toolName || ''}`}
                {evt.eventType === 'ToolResult' && evt.event && `${evt.event.toolName || ''} ${evt.event.success ? 'OK' : 'FAIL'}`}
                {evt.eventType === 'Error' && evt.event && `${evt.event.source || ''}: ${evt.event.message || ''}`}
                {evt.eventType === 'StepStart' && evt.event && `${evt.event.stepName || ''}`}
                {evt.eventType === 'StepComplete' && evt.event && `${evt.event.stepName || ''}: ${String(evt.event.result || '').substring(0, 100)}`}
                {evt.eventType === 'Thinking' && evt.event && `${String(evt.event.content || '').substring(0, 120)}`}
                {evt.eventType === 'Checkpoint' && evt.event && `${evt.event.summary || ''}`}
                {evt.eventType === 'ContextCompressed' && evt.event && `${evt.event.originalCount} → ${evt.event.compressedCount} msgs`}
                {evt.eventType === 'TaskStateChanged' && evt.event && `${evt.event.oldStatus} → ${evt.event.newStatus}`}
                {evt.eventType === 'TaskPlanUpdated' && evt.event && `${evt.event.completedSteps}/${evt.event.totalSteps}`}
                {!['TokenUsage','ToolCall','ToolResult','Error','StepStart','StepComplete','Thinking','Checkpoint','ContextCompressed','TaskStateChanged','TaskPlanUpdated'].includes(evt.eventType) && '—'}
              </span>
            </div>
          ))}
        </div>
        {runEvents.length < eventsTotal && (
          <div className="flex justify-center mt-3">
            <button onClick={loadMoreEvents} disabled={loadingEvents}
              className="px-4 py-2 bg-dark-surface border border-dark-border rounded-lg text-dark-text hover:bg-dark-hover disabled:opacity-50 text-sm">
              {loadingEvents ? 'Loading...' : 'Load More'}
            </button>
          </div>
        )}
        {runEvents.length === 0 && !loadingEvents && (
          <div className="flex-1 flex items-center justify-center text-dark-muted">No events recorded</div>
        )}
      </div>
    );
  }

  // ---- Main view ----
  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-dark-border shrink-0">
        <div className="flex items-center gap-2">
          <Activity size={20} className="text-accent-blue" />
          <h2 className="text-lg font-semibold text-dark-text">Observability</h2>
        </div>
        <button onClick={loadData} className="p-1.5 rounded hover:bg-dark-hover text-dark-muted hover:text-dark-text transition-colors" title="Refresh">
          <RefreshCw size={16} />
        </button>
      </div>

      {/* Sub-tab bar */}
      <div className="flex gap-1 px-4 py-2 border-b border-dark-border shrink-0">
        {([
          ['overview', 'Overview', BarChart3],
          ['costs', 'Costs', DollarSign],
          ['history', 'History', History],
        ] as const).map(([id, label, Icon]) => (
          <button key={id}
            onClick={() => setActiveSubTab(id)}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded text-sm transition-colors ${
              activeSubTab === id ? 'bg-accent-blue text-white' : 'text-dark-muted hover:text-dark-text hover:bg-dark-hover'
            }`}
          >
            <Icon size={14} />
            {label}
          </button>
        ))}
      </div>

      {/* Content area */}
      <div className="flex-1 overflow-y-auto p-4">
        {activeSubTab === 'overview' && renderOverview(summary)}
        {activeSubTab === 'costs' && renderCosts(costData)}
        {activeSubTab === 'history' && renderHistory(runs, loadRunDetail)}
      </div>
    </div>
  );
}

function renderOverview(s: ObservabilitySummary | null) {
  if (!s) {
    return <div className="text-center text-dark-muted py-8">No data available</div>;
  }

  const statCards = [
    { label: 'LLM Calls', value: s.llmCalls, icon: Zap, color: 'text-accent-blue' },
    { label: 'Tool Calls', value: s.toolCalls, icon: Wrench, color: 'text-accent-green' },
    { label: 'Errors', value: s.errors, icon: AlertCircle, color: s.errors > 0 ? 'text-accent-red' : 'text-dark-muted' },
    { label: 'Cost', value: formatCost(s.totalCostDollars), icon: DollarSign, color: 'text-accent-yellow', isString: true },
  ];

  return (
    <div className="space-y-6">
      {/* Stat cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {statCards.map(card => (
          <div key={card.label} className="p-4 rounded-lg bg-dark-surface border border-dark-border">
            <div className="flex items-center gap-2 mb-1">
              <card.icon size={16} className={card.color} />
              <span className="text-dark-muted text-xs">{card.label}</span>
            </div>
            <div className={`text-2xl font-bold ${card.color}`}>
              {card.isString ? card.value : (card.value as number).toLocaleString()}
            </div>
          </div>
        ))}
      </div>

      {/* Token breakdown */}
      <div className="p-4 rounded-lg bg-dark-surface border border-dark-border">
        <div className="flex items-center gap-2 mb-3">
          <Cpu size={16} className="text-accent-blue" />
          <span className="text-dark-text font-medium">Token Usage</span>
        </div>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 text-sm">
          <div>
            <div className="text-dark-muted">Total Tokens</div>
            <div className="text-dark-text font-semibold">{formatTokens(s.totalTokens)}</div>
          </div>
          <div>
            <div className="text-dark-muted">Prompt</div>
            <div className="text-dark-text font-semibold">{formatTokens(s.promptTokens)}</div>
          </div>
          <div>
            <div className="text-dark-muted">Completion</div>
            <div className="text-dark-text font-semibold">{formatTokens(s.completionTokens)}</div>
          </div>
          <div>
            <div className="text-dark-muted">Cache Hit Rate</div>
            <div className="text-dark-text font-semibold">{(s.cacheHitRate * 100).toFixed(1)}%</div>
          </div>
        </div>
        {/* Prompt vs Completion bar */}
        <div className="mt-3 flex rounded-full overflow-hidden h-2 bg-dark-hover">
          {s.totalTokens > 0 && (
            <>
              <div className="bg-accent-blue h-full" style={{ width: `${(s.promptTokens / s.totalTokens) * 100}%` }} />
              <div className="bg-accent-green h-full" style={{ width: `${(s.completionTokens / s.totalTokens) * 100}%` }} />
            </>
          )}
        </div>
        <div className="flex gap-4 mt-1.5 text-xs text-dark-muted">
          <div className="flex items-center gap-1"><div className="w-2 h-2 rounded-full bg-accent-blue" /> Prompt</div>
          <div className="flex items-center gap-1"><div className="w-2 h-2 rounded-full bg-accent-green" /> Completion</div>
        </div>
      </div>

      {/* Session info */}
      <div className="p-4 rounded-lg bg-dark-surface border border-dark-border">
        <div className="flex items-center gap-2 mb-3">
          <Clock size={16} className="text-accent-blue" />
          <span className="text-dark-text font-medium">Session</span>
        </div>
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <div className="text-dark-muted">Duration</div>
            <div className="text-dark-text font-semibold">{formatDuration(s.elapsedSeconds)}</div>
          </div>
          <div>
            <div className="text-dark-muted">Total Cost</div>
            <div className="text-dark-text font-semibold">{formatCost(s.totalCostDollars)}</div>
          </div>
        </div>
      </div>
    </div>
  );
}

function renderCosts(cd: CostData | null) {
  if (!cd || cd.byModel.length === 0) {
    return <div className="text-center text-dark-muted py-8">No cost data available</div>;
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 text-sm text-dark-text">
        <DollarSign size={16} className="text-accent-yellow" />
        <span>Total Cost: <strong>{formatCost(cd.totalCostDollars)}</strong></span>
      </div>

      {/* By model table */}
      <div className="rounded-lg border border-dark-border overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-dark-surface">
            <tr className="text-dark-muted text-left">
              <th className="p-3 font-medium">Model</th>
              <th className="p-3 font-medium text-right">Requests</th>
              <th className="p-3 font-medium text-right">Input Tokens</th>
              <th className="p-3 font-medium text-right">Output Tokens</th>
              <th className="p-3 font-medium text-right">Cost</th>
            </tr>
          </thead>
          <tbody>
            {cd.byModel.map(m => (
              <tr key={m.modelId} className="border-t border-dark-border hover:bg-dark-hover/50 transition-colors">
                <td className="p-3 text-dark-text font-medium">{m.modelId}</td>
                <td className="p-3 text-dark-text text-right">{m.requestCount}</td>
                <td className="p-3 text-dark-text text-right">{formatTokens(m.totalInputTokens)}</td>
                <td className="p-3 text-dark-text text-right">{formatTokens(m.totalOutputTokens)}</td>
                <td className="p-3 text-dark-text text-right font-mono">{formatCost(m.totalCostDollars)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Cost history */}
      {cd.history.length > 0 && (
        <div>
          <div className="text-sm text-dark-muted mb-2">Recent Cost History ({cd.history.length} entries)</div>
          <div className="rounded-lg border border-dark-border overflow-hidden max-h-64 overflow-y-auto">
            <table className="w-full text-sm">
              <thead className="bg-dark-surface sticky top-0">
                <tr className="text-dark-muted text-left">
                  <th className="p-2 font-medium">Time</th>
                  <th className="p-2 font-medium">Model</th>
                  <th className="p-2 font-medium text-right">Input</th>
                  <th className="p-2 font-medium text-right">Output</th>
                  <th className="p-2 font-medium text-right">Cost</th>
                </tr>
              </thead>
              <tbody>
                {cd.history.slice().reverse().map((entry, idx) => (
                  <tr key={idx} className="border-t border-dark-border">
                    <td className="p-2 text-dark-muted text-xs">{formatTime(entry.timestamp)}</td>
                    <td className="p-2 text-dark-text text-xs">{entry.modelId}</td>
                    <td className="p-2 text-dark-text text-xs text-right">{entry.inputTokens}</td>
                    <td className="p-2 text-dark-text text-xs text-right">{entry.outputTokens}</td>
                    <td className="p-2 text-dark-text text-xs text-right font-mono">{formatCost(entry.costDollars)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

function renderHistory(runs: TraceRunSummary[], onSelectRun: (id: string) => void) {
  if (runs.length === 0) {
    return <div className="text-center text-dark-muted py-8">No historical runs found</div>;
  }

  return (
    <div className="space-y-2">
      {runs.map(run => (
        <button key={run.runId}
          onClick={() => onSelectRun(run.runId)}
          className="w-full text-left p-4 rounded-lg bg-dark-surface border border-dark-border hover:border-dark-hover transition-colors"
        >
          <div className="flex items-center justify-between mb-1">
            <span className="text-dark-text font-medium text-sm truncate max-w-[60%]">{run.runId}</span>
            <span className={`px-2 py-0.5 rounded text-xs ${
              run.status === 'passed' ? 'bg-accent-green/20 text-accent-green' :
              run.status === 'failed' ? 'bg-accent-red/20 text-accent-red' :
              'bg-dark-hover text-dark-muted'
            }`}>
              {run.status}
            </span>
          </div>
          <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-dark-muted">
            <span>Scenario: {run.scenarioId || '-'}</span>
            <span>Events: {run.eventCount}</span>
            <span>Started: {formatTime(run.startedAt)}</span>
            {run.summary && <span className="w-full mt-0.5 text-dark-text/70">{run.summary}</span>}
          </div>
        </button>
      ))}
    </div>
  );
}

export default ObservabilityView;

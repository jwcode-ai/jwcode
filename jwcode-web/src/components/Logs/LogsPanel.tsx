import { memo, useRef, useCallback, useEffect, useState } from 'react';
import { ScrollText, FileText, Download, Eye, ChevronLeft, Loader2, History, FlaskConical } from 'lucide-react';
import { LogEntry } from '../../types';
import { api, LogFileInfo, LogFileContent } from '../../services/api';
import { useSessionStore } from '../../stores/sessionStore';
import wsService from '../../services/websocket';
import type { TraceRunSummary, TraceRunDetail, TraceEvent } from '../../types';

type LogsSubTab = 'files' | 'eval' | 'history';

type EvalMode = 'quick' | 'full' | 'list';

const EVAL_OPTIONS: { mode: EvalMode; label: string; desc: string }[] = [
  { mode: 'quick', label: 'Quick Eval', desc: '验收检查 (无需 LLM)' },
  { mode: 'full', label: 'Full Eval', desc: '真实 LLM 调用 (耗时)' },
  { mode: 'list', label: 'List Tasks', desc: '列出所有评测任务' },
];

interface LogsPanelProps {
  logs: LogEntry[];
  onClear: () => void;
  compact?: boolean;
}

const levelColors: Record<string, string> = {
  info: 'bg-accent-blue',
  warn: 'bg-accent-yellow',
  error: 'bg-accent-red',
  success: 'bg-accent-green',
  tool: 'bg-accent-purple',
};

const levelIcons: Record<string, string> = {
  info: 'ℹ',
  warn: '⚠',
  error: '✗',
  success: '✓',
  tool: '🔧',
};

function formatTime(iso: string): string {
  if (!iso) return '-';
  const d = new Date(iso);
  return d.toLocaleString();
}

function LogFileBrowser() {
  const [files, setFiles] = useState<LogFileInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedFile, setSelectedFile] = useState<LogFileInfo | null>(null);
  const [fileContent, setFileContent] = useState<LogFileContent | null>(null);
  const [loadingContent, setLoadingContent] = useState(false);

  const loadFiles = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.logs.list();
      if (res.success && res.data) {
        setFiles(res.data);
      }
    } catch (e) {
      console.error("Failed to load log files:", e);
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    loadFiles();
  }, [loadFiles]);

  const handlePreview = useCallback(async (file: LogFileInfo) => {
    if (!file.previewable) return;
    setSelectedFile(file);
    setLoadingContent(true);
    const res = await api.logs.read(file.name, 500);
    if (res.success && res.data) {
      setFileContent(res.data);
    }
    setLoadingContent(false);
  }, []);

  const handleBack = useCallback(() => {
    setSelectedFile(null);
    setFileContent(null);
  }, []);

  const formatSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  const formatTime = (ms: number) => {
    return new Date(ms).toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  if (selectedFile && fileContent) {
    return (
      <div className="flex flex-col min-h-0 h-full">
        <div className="flex items-center gap-2 mb-2 shrink-0">
          <button
            onClick={handleBack}
            className="p-1 rounded hover:bg-dark-hover transition-colors"
            title="返回文件列表"
          >
            <ChevronLeft size={16} />
          </button>
          <span className="text-sm font-medium text-dark-text truncate flex-1">{selectedFile.name}</span>
          <a
            href={api.logs.downloadUrl(selectedFile.name)}
            download={selectedFile.name}
            className="flex items-center gap-1 px-2 py-1 text-xs bg-accent-blue text-white rounded hover:opacity-90 transition-opacity"
          >
            <Download size={12} />
            下载
          </a>
        </div>
        {loadingContent ? (
          <div className="flex-1 flex items-center justify-center">
            <Loader2 size={20} className="animate-spin text-accent-blue" />
          </div>
        ) : (
          <>
            <div className="text-[10px] text-dark-muted mb-1 shrink-0">
              {formatSize(fileContent.size)} · {fileContent.totalLines} 行
              {fileContent.startLine > 1 && ` (显示最新 ${fileContent.totalLines - fileContent.startLine + 1} 行)`}
            </div>
            <div
              className="flex-1 overflow-y-auto min-h-0 font-mono text-[11px] leading-relaxed whitespace-pre-wrap break-all
                bg-dark-bg border border-dark-border rounded p-3 custom-scrollbar"
              style={{
                scrollbarWidth: 'thin',
                scrollbarColor: '#30363d transparent',
              }}
            >
              {fileContent.content || (
                <span className="text-dark-muted italic">(空文件)</span>
              )}
            </div>
          </>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col min-h-0 h-full">
      <div className="flex items-center justify-between mb-2 shrink-0">
        <span className="text-xs font-medium text-dark-text">日志文件</span>
        <button
          onClick={loadFiles}
          className="px-2 py-1 text-[10px] bg-dark-hover rounded hover:bg-dark-border transition-colors"
          disabled={loading}
        >
          {loading ? '刷新中...' : '刷新'}
        </button>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-8">
          <Loader2 size={16} className="animate-spin text-accent-blue" />
        </div>
      ) : files.length === 0 ? (
        <div className="text-center py-8 text-dark-muted">
          <FileText size={32} className="mx-auto mb-1 opacity-50" />
          <p className="text-xs">暂无日志文件</p>
        </div>
      ) : (
        <div className="flex-1 overflow-y-auto min-h-0 space-y-1 custom-scrollbar">
          {files.map(file => (
            <div
              key={file.name}
              className="flex items-center gap-2 p-2 rounded hover:bg-dark-hover transition-colors group"
            >
              <FileText size={14} className="text-accent-blue shrink-0" />
              <div className="flex-1 min-w-0">
                <div className="text-xs text-dark-text truncate">{file.name}</div>
                <div className="text-[10px] text-dark-muted">
                  {formatSize(file.size)} · {formatTime(file.modified)}
                </div>
              </div>
              <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                {file.previewable && (
                  <button
                    onClick={() => handlePreview(file)}
                    className="p-1.5 rounded hover:bg-dark-border transition-colors"
                    title="预览"
                  >
                    <Eye size={14} />
                  </button>
                )}
                <a
                  href={api.logs.downloadUrl(file.name)}
                  download={file.name}
                  className="p-1.5 rounded hover:bg-dark-border transition-colors block"
                  title="下载"
                >
                  <Download size={14} />
                </a>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function EvalContent() {
  const [running, setRunning] = useState<EvalMode | null>(null);
  const [lastResult, setLastResult] = useState<string | null>(null);

  const runEval = (mode: EvalMode) => {
    const sessionId = useSessionStore.getState().activeSessionId;
    if (!sessionId) return;
    setRunning(mode);
    setLastResult(null);

    const cmd = mode === 'list' ? '/test list' : `/test ${mode === 'full' ? 'full' : 'all'}`;
    wsService.setSessionId(sessionId);
    wsService.send({ type: 'chat', sessionId, message: cmd });

    const labels: Record<EvalMode, string> = { quick: 'Quick Eval', full: 'Full Eval', list: 'List Tasks' };
    setLastResult(`${labels[mode]} 已触发，请在对话中查看结果`);
    setTimeout(() => setRunning(null), 2000);
  };

  return (
    <div className="flex flex-col h-full">
      <div className="text-sm text-dark-muted mb-4">
        运行能力评估测试，验证系统功能是否正常。
      </div>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        {EVAL_OPTIONS.map((opt) => {
          const isRunning = running === opt.mode;
          return (
            <button
              key={opt.mode}
              onClick={() => runEval(opt.mode)}
              disabled={running !== null}
              className={`p-4 rounded-lg border text-left transition-all ${
                isRunning
                  ? 'bg-accent-green/10 border-accent-green/50 cursor-wait'
                  : running !== null
                    ? 'bg-dark-surface border-dark-border opacity-50 cursor-not-allowed'
                    : 'bg-dark-surface border-dark-border hover:border-accent-blue/50 hover:bg-dark-hover'
              }`}
            >
              <div className="flex items-center gap-2 mb-1">
                <FlaskConical size={16} className={isRunning ? 'text-accent-green animate-pulse' : 'text-accent-blue'} />
                <span className="text-sm font-medium text-dark-text">
                  {isRunning ? '运行中...' : opt.label}
                </span>
              </div>
              <div className="text-xs text-dark-muted pl-6">{opt.desc}</div>
            </button>
          );
        })}
      </div>
      {lastResult && (
        <div className="mt-4 p-3 rounded-lg bg-accent-blue/10 border border-accent-blue/30 text-sm text-dark-text">
          {lastResult}
        </div>
      )}
    </div>
  );
}

function HistoryContent() {
  const [runs, setRuns] = useState<TraceRunSummary[]>([]);
  const [loading, setLoading] = useState(true);

  // Run detail state
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
  const [runDetail, setRunDetail] = useState<TraceRunDetail | null>(null);
  const [runEvents, setRunEvents] = useState<TraceEvent[]>([]);
  const [eventsTotal, setEventsTotal] = useState(0);
  const [eventsPage, setEventsPage] = useState(0);
  const [loadingEvents, setLoadingEvents] = useState(false);

  const loadRuns = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.observability.listRuns();
      if (res.success && res.data) setRuns(res.data);
    } catch (e) {
      console.error("Failed to load runs:", e);
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    loadRuns();
  }, [loadRuns]);

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

  // Run detail view
  if (selectedRunId) {
    return (
      <div className="flex flex-col h-full">
        <div className="flex items-center gap-3 mb-4 shrink-0">
          <button onClick={handleBackToRuns} className="p-1.5 rounded hover:bg-dark-hover text-dark-muted hover:text-dark-text transition-colors">
            <ChevronLeft size={20} />
          </button>
          <h2 className="text-base font-semibold text-dark-text truncate">{selectedRunId}</h2>
          {runDetail && (
            <span className={`px-2 py-0.5 rounded text-xs ${
              runDetail.status === 'passed' ? 'bg-accent-green/20 text-accent-green' :
              runDetail.status === 'failed' ? 'bg-accent-red/20 text-accent-red' :
              'bg-dark-hover text-dark-muted'
            }`}>
              {runDetail.status}
            </span>
          )}
        </div>

        {runDetail && (
          <div className="flex flex-wrap gap-4 mb-4 text-sm text-dark-muted shrink-0">
            <span>Scenario: <span className="text-dark-text">{runDetail.scenarioId || '-'}</span></span>
            <span>Events: <span className="text-dark-text">{runDetail.eventCount}</span></span>
            <span>Started: <span className="text-dark-text">{formatTime(runDetail.startedAt)}</span></span>
            {runDetail.completedAt && <span>Completed: <span className="text-dark-text">{formatTime(runDetail.completedAt)}</span></span>}
          </div>
        )}

        {runDetail?.summary && (
          <div className="mb-4 p-3 rounded-lg bg-dark-surface border border-dark-border text-sm text-dark-text shrink-0">
            {runDetail.summary}
          </div>
        )}

        <div className="text-sm text-dark-muted mb-2 shrink-0">Events ({eventsTotal} total)</div>
        <div className="flex-1 overflow-y-auto space-y-1.5 min-h-0">
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
          <div className="flex justify-center mt-3 shrink-0">
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

  // Run list
  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 size={20} className="animate-spin text-accent-blue" />
      </div>
    );
  }

  if (runs.length === 0) {
    return <div className="text-center text-dark-muted py-8">No historical runs found</div>;
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between mb-3 shrink-0">
        <span className="text-xs font-medium text-dark-text">运行历史</span>
        <button
          onClick={loadRuns}
          className="px-2 py-1 text-[10px] bg-dark-hover rounded hover:bg-dark-border transition-colors"
        >
          刷新
        </button>
      </div>
      <div className="flex-1 overflow-y-auto min-h-0 space-y-2">
        {runs.map(run => (
          <button key={run.runId}
            onClick={() => loadRunDetail(run.runId)}
            className="w-full text-left p-3 rounded-lg bg-dark-surface border border-dark-border hover:border-dark-hover transition-colors"
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
    </div>
  );
}

export const LogsPanel = memo(function LogsPanel({ logs, onClear, compact }: LogsPanelProps) {
  const [activeSubTab, setActiveSubTab] = useState<LogsSubTab>('files');
  const scrollRef = useRef<HTMLDivElement>(null);
  const isAtBottomRef = useRef(true);

  const checkIsAtBottom = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return true;
    const threshold = 50;
    return el.scrollHeight - el.scrollTop - el.clientHeight < threshold;
  }, []);

  const scrollToBottom = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    el.scrollTop = el.scrollHeight;
  }, []);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const handleScroll = () => {
      isAtBottomRef.current = checkIsAtBottom();
    };
    el.addEventListener('scroll', handleScroll, { passive: true });
    return () => el.removeEventListener('scroll', handleScroll);
  }, [checkIsAtBottom]);

  useEffect(() => {
    if (isAtBottomRef.current) {
      scrollToBottom();
    }
  }, [logs, scrollToBottom]);

  return (
    <div className={`flex flex-col min-h-0 ${compact ? 'flex-1 p-2' : 'flex-1 overflow-hidden p-4'}`}>
      {!compact && (
        <>
          <div className="flex items-center justify-between mb-3 shrink-0 flex-wrap gap-2">
            <h2 className="text-lg font-semibold flex items-center gap-2">
              <ScrollText size={18} className="text-accent-blue" />
              后台日志
              <span className="text-sm font-normal text-dark-muted">({logs.length})</span>
            </h2>
            <div className="flex items-center gap-2">
              <button
                onClick={onClear}
                className="px-3 py-1.5 text-sm bg-dark-surface border border-dark-border rounded hover:bg-dark-hover transition-colors"
              >
                清空
              </button>
            </div>
          </div>

          {/* Sub-tab bar */}
          <div className="flex gap-1 mb-3 shrink-0">
            {([
              ['files', '日志文件', FileText],
              ['eval', 'Eval', FlaskConical],
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

          {/* Tab content */}
          <div className="shrink-0 max-h-[320px] overflow-y-auto mb-3 custom-scrollbar">
            {activeSubTab === 'files' && (
              <div className="border border-dark-border rounded-lg p-3 bg-dark-surface">
                <LogFileBrowser />
              </div>
            )}
            {activeSubTab === 'eval' && (
              <div className="border border-dark-border rounded-lg p-3 bg-dark-surface">
                <EvalContent />
              </div>
            )}
            {activeSubTab === 'history' && (
              <div className="border border-dark-border rounded-lg p-3 bg-dark-surface min-h-[200px]">
                <HistoryContent />
              </div>
            )}
          </div>

          <div className="flex items-center gap-2 mb-2 shrink-0">
            <span className="text-xs font-medium text-dark-muted">实时日志流</span>
          </div>
        </>
      )}

      <div
        ref={scrollRef}
        className="flex-1 overflow-y-auto min-h-0 font-mono text-xs space-y-0.5"
        style={{
          scrollbarWidth: 'thin',
          scrollbarColor: '#30363d transparent',
        }}
      >
        {logs.length === 0 ? (
          <div className="text-center text-dark-muted py-12">
            <ScrollText size={48} className="mx-auto mb-2 opacity-50" />
            <p>暂无实时日志</p>
          </div>
        ) : (
          logs.map(log => (
            <div
              key={log.id}
              className={`flex gap-2 p-2 rounded hover:bg-dark-surface transition-colors ${
                log.level === 'error' ? 'bg-accent-red/10' :
                log.level === 'warn' ? 'bg-accent-yellow/10' :
                log.level === 'success' ? 'bg-accent-green/10' : ''
              }`}
            >
              <span className="text-dark-muted shrink-0 text-xs">
                {new Date(log.timestamp).toLocaleTimeString('zh-CN', {
                  hour: '2-digit',
                  minute: '2-digit',
                  second: '2-digit',
                })}
              </span>
              <span className="text-lg shrink-0 leading-none">{levelIcons[log.level]}</span>
              <span className={`px-1.5 py-0.5 rounded text-white text-xs shrink-0 ${levelColors[log.level]}`}>
                {log.level.toUpperCase()}
              </span>
              <span className="text-dark-muted shrink-0 text-xs">[{log.source}]</span>
              <span className="text-dark-text break-all">{log.message}</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
});

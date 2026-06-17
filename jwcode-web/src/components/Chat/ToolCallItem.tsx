import { memo, useState, useRef } from 'react';
import { Plus, Minus, Clock } from 'lucide-react';
import { ToolCall } from '../../types';
import { DiffPreview } from './DiffPreview';
import { useGlobalTick } from '../../hooks/useGlobalTick';

interface ToolCallItemProps {
  toolCall: ToolCall;
  defaultCollapsed?: boolean;
}

const FILE_MODIFY_TOOLS = [
  'FileEditTool', 'FileWriteTool', 'EditTool', 'NotebookEditTool',
  'Write', 'Edit', 'WriteTool', 'FileCreateTool',
];

const FILE_WRITE_PATTERNS = /\b(write|edit|save|create|update|modify|patch)\b/i;
const FILE_READ_PATTERNS = /\b(read|open|cat|view|list|ls|dir|find|grep|glob|search)\b/i;

function detectFileModify(name: string): boolean {
  if (FILE_MODIFY_TOOLS.includes(name)) return true;
  return FILE_WRITE_PATTERNS.test(name) && !FILE_READ_PATTERNS.test(name);
}

function extractResultText(result: unknown): string {
  if (typeof result === 'string') return result;
  if (result && typeof result === 'object') return JSON.stringify(result, null, 2);
  return String(result ?? '');
}

function extractFilePath(args: Record<string, unknown> | string): string | undefined {
  if (typeof args === 'string') return undefined;
  if (!args) return undefined;
  return (args.filePath || args.path || args.file || args.file_path) as string | undefined;
}


export const ToolCallItem = memo(function ToolCallItem({ toolCall, defaultCollapsed = false }: ToolCallItemProps) {
  const [isCollapsed, setIsCollapsed] = useState(defaultCollapsed);
  const tick = useGlobalTick(); // single 1s global tick
  const startTickRef = useRef(0);

  const isRunning = toolCall.status === 'running';
  const hasResult = toolCall.result !== undefined && toolCall.result !== null;
  const isError = toolCall.status === 'error';
  const isFileModify = detectFileModify(toolCall.name);
  const resultText = hasResult ? extractResultText(toolCall.result) : '';
  const filePath = typeof toolCall.args === 'object' && toolCall.args
    ? extractFilePath(toolCall.args as Record<string, unknown>)
    : undefined;

  // Capture starting tick when this tool call begins running
  if (isRunning && startTickRef.current === 0) {
    startTickRef.current = tick;
  }
  if (!isRunning) {
    startTickRef.current = 0;
  }

  const elapsed = isRunning ? tick - startTickRef.current : 0;
  const duration = toolCall.duration ?? (isRunning ? elapsed : 0);
  const durationStr = duration > 0
    ? duration >= 60 ? `${Math.floor(duration / 60)}m ${duration % 60}s` : `${duration}s`
    : '';

  const statusConfig = isRunning
    ? { dot: 'bg-accent-blue', border: 'border-accent-blue/40', text: 'text-accent-blue', label: '运行中' }
    : isError
    ? { dot: 'bg-accent-red', border: 'border-accent-red/40', text: 'text-accent-red', label: '错误' }
    : { dot: 'bg-accent-green', border: 'border-accent-green/40', text: 'text-accent-green', label: '完成' };

  return (
    <div className={`bg-dark-bg border rounded-lg overflow-hidden ${statusConfig.border}`}>
      {/* Header — always visible, clickable */}
      <div
        className="flex items-center gap-2 px-3 py-2 cursor-pointer hover:bg-dark-surface/50"
        onClick={() => setIsCollapsed(!isCollapsed)}
      >
        {isCollapsed ? <Plus size={14} className="text-accent-blue shrink-0" /> : <Minus size={14} className="text-accent-blue shrink-0" />}

        {/* Status dot */}
        <span className={`w-2 h-2 rounded-full shrink-0 ${isRunning ? 'animate-pulse ' : ''}${statusConfig.dot}`} />

        <span className="text-xs text-dark-muted shrink-0">🔧</span>
        <span className="font-medium text-sm text-dark-text truncate">{toolCall.name}</span>

        {/* Elapsed timer for running */}
        {isRunning && (
          <span className="text-[11px] text-accent-blue animate-pulse shrink-0 flex items-center gap-1">
            <Clock size={11} />
            <span>{elapsed}s</span>
          </span>
        )}

        {/* Duration for completed */}
        {!isRunning && durationStr && (
          <span className="text-[10px] text-dark-muted shrink-0 flex items-center gap-1">
            <Clock size={10} />
            <span>{durationStr}</span>
          </span>
        )}

        {/* Status badge */}
        <span className={`text-[10px] px-1.5 py-0.5 rounded-full shrink-0 ml-auto ${statusConfig.text} bg-dark-surface`}>
          {statusConfig.label}
        </span>
      </div>

      {/* Args — collapsible */}
      {!isCollapsed && (
        <div className="px-3 pb-2">
          <div>
            <div className="text-[10px] text-dark-muted mb-1">参数</div>
            <pre className="text-[11px] font-mono bg-dark-bg border border-dark-border p-2 rounded overflow-x-auto max-h-24 overflow-y-auto">
              {typeof toolCall.args === 'string'
                ? toolCall.args
                : JSON.stringify(toolCall.args, null, 2)}
            </pre>
          </div>
        </div>
      )}

      {/* Result — always visible, never collapsed */}
      {hasResult && (
        <div className="px-3 pb-3">
          <div className="text-[10px] text-dark-muted mb-1">
            {isError ? '错误' : '结果'}
          </div>
          {isFileModify && resultText ? (
            <DiffPreview
              filePath={filePath}
              content={resultText}
            />
          ) : (
            <pre className={`text-[11px] font-mono bg-dark-bg border border-dark-border p-2 rounded overflow-x-auto max-h-48 overflow-y-auto whitespace-pre-wrap break-all ${
              isError ? 'text-accent-red' : 'text-accent-green'
            }`}>
              {resultText}
            </pre>
          )}
        </div>
      )}
    </div>
  );
});

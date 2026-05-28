import { memo, useState } from 'react';
import { Plus, Minus } from 'lucide-react';
import { ToolCall } from '../../types';
import { DiffPreview } from './DiffPreview';

interface ToolCallItemProps {
  toolCall: ToolCall;
  defaultCollapsed?: boolean;
}

const FILE_MODIFY_TOOLS = ['FileEditTool', 'FileWriteTool', 'EditTool', 'NotebookEditTool'];

function extractResultText(result: unknown): string {
  if (typeof result === 'string') return result;
  if (result && typeof result === 'object') return JSON.stringify(result, null, 2);
  return String(result ?? '');
}

export const ToolCallItem = memo(function ToolCallItem({ toolCall, defaultCollapsed = false }: ToolCallItemProps) {
  const [isCollapsed, setIsCollapsed] = useState(defaultCollapsed);
  const isRunning = toolCall.status === 'running';
  const hasResult = toolCall.result !== undefined && toolCall.result !== null;
  const isFileModify = FILE_MODIFY_TOOLS.includes(toolCall.name);
  const resultText = hasResult ? extractResultText(toolCall.result) : '';

  return (
    <div className="bg-dark-bg border border-dark-border rounded-lg overflow-hidden">
      {/* Header - Always visible, clickable to collapse/expand */}
      <div
        className="flex items-center justify-between px-3 py-2 bg-dark-surface border-b border-dark-border cursor-pointer hover:bg-dark-hover"
        onClick={() => setIsCollapsed(!isCollapsed)}
      >
        <div className="flex items-center gap-2">
          {isCollapsed ? <Plus size={14} className="text-accent-blue" /> : <Minus size={14} className="text-accent-blue" />}
          <span className="text-accent-blue">🔧</span>
          <span className="font-medium text-sm">{toolCall.name}</span>
        </div>
        <span className={`text-xs px-2 py-0.5 rounded ${
          isRunning ? 'bg-accent-blue text-white' : 'bg-accent-green text-white'
        }`}>
          {isRunning ? '运行中' : '完成'}
        </span>
      </div>

      {/* Collapsible content */}
      {!isCollapsed && (
        <div className="p-3">
          <div className="text-xs text-dark-muted mb-1">参数</div>
          <pre className="text-xs font-mono bg-dark-bg p-2 rounded overflow-x-auto">
            {typeof toolCall.args === 'string'
              ? toolCall.args
              : JSON.stringify(toolCall.args, null, 2)}
          </pre>
          {hasResult && (
            <>
              <div className="text-xs text-dark-muted mb-1 mt-2">结果</div>
              {isFileModify ? (
                <DiffPreview
                  filePath={typeof toolCall.args === 'object' && toolCall.args ? (toolCall.args as any).filePath || (toolCall.args as any).path : undefined}
                  content={resultText}
                />
              ) : (
                <pre className="text-xs font-mono bg-dark-bg p-2 rounded overflow-x-auto text-accent-green">
                  {resultText}
                </pre>
              )}
            </>
          )}
        </div>
      )}

      {/* Show hint if collapsed */}
      {isCollapsed && hasResult && (
        <div className="px-3 py-2 text-xs text-dark-muted">
          ✓ 有返回结果
        </div>
      )}
    </div>
  );
});

/**
 * ContextAnalysisBanner — 上下文分析提示条（对标 Claude Code /context 命令输出）。
 *
 * 在 ChatPanel 顶部展示上下文使用情况警告和建议：
 * - 接近容量警告 (≥80%)
 * - 大型工具输出警告
 * - 重复文件读取警告
 * - 记忆文件膨胀警告
 * - 自动压缩禁用提醒
 *
 * 对标 Claude Code 的 contextSuggestions.ts 输出到 UI 的渲染。
 */

import React, { useEffect, useState } from 'react';

// ==================== 类型 ====================

export interface ContextSuggestion {
  severity: 'warning' | 'info';
  title: string;
  detail: string;
  savingsTokens?: number;
}

export interface TokenStats {
  total: number;
  maxTokens: number;
  percent: number;
  toolRequests: Record<string, number>;
  toolResults: Record<string, number>;
  humanMessages: number;
  assistantMessages: number;
  duplicateFileReads: Record<string, { count: number; tokens: number }>;
  memoryTokens: number;
  memoryFileCount: number;
  isAutoCompactEnabled: boolean;
}

interface Props {
  stats: TokenStats | null;
  suggestions: ContextSuggestion[];
  onDismiss?: () => void;
}

// ==================== 组件 ====================

export const ContextAnalysisBanner: React.FC<Props> = ({
  stats,
  suggestions,
  onDismiss,
}) => {
  const [dismissed, setDismissed] = useState<Set<number>>(new Set());
  const [collapsed, setCollapsed] = useState(false);

  // 自动重置（stats 变化时）
  useEffect(() => {
    setDismissed(new Set());
  }, [stats?.total]);

  if (!stats || suggestions.length === 0) return null;

  const visibleSuggestions = suggestions.filter((_, i) => !dismissed.has(i));
  if (visibleSuggestions.length === 0 && !collapsed) return null;

  return (
    <div className="border-b border-yellow-500/30 bg-yellow-500/5 px-3 py-2">
      {/* 进度条 */}
      <div className="mb-2 flex items-center gap-2">
        <div className="flex-1 h-2 bg-gray-700 rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full transition-all duration-500 ${
              stats.percent >= 90
                ? 'bg-red-500'
                : stats.percent >= 80
                ? 'bg-yellow-500'
                : stats.percent >= 60
                ? 'bg-blue-500'
                : 'bg-green-500'
            }`}
            style={{ width: `${Math.min(stats.percent, 100)}%` }}
          />
        </div>
        <span className="text-xs text-gray-400 whitespace-nowrap font-mono">
          {formatTokens(stats.total)} / {formatTokens(stats.maxTokens)} ({stats.percent}%)
        </span>
      </div>

      {/* 建议列表 */}
      {!collapsed && (
        <div className="space-y-1">
          {visibleSuggestions.map((suggestion, i) => (
            <SuggestionRow
              key={i}
              suggestion={suggestion}
              onDismiss={() => setDismissed(prev => new Set(prev).add(i))}
            />
          ))}
        </div>
      )}

      {/* 操作按钮 */}
      <div className="mt-2 flex items-center gap-2 text-xs">
        <button
          className="text-gray-500 hover:text-gray-300 transition-colors"
          onClick={() => setCollapsed(!collapsed)}
        >
          {collapsed ? `展开 (${visibleSuggestions.length}条建议)` : '收起'}
        </button>
        <div className="flex-1" />
        <span className="text-gray-500">
          工具请求: {formatTokens(
            Object.values(stats.toolRequests).reduce((a, b) => a + b, 0)
          )}{' '}
          | 工具结果:{' '}
          {formatTokens(
            Object.values(stats.toolResults).reduce((a, b) => a + b, 0)
          )}{' '}
          | 记忆:{' '}
          {formatTokens(stats.memoryTokens)}
          {stats.memoryFileCount > 0 && ` (${stats.memoryFileCount}文件)`}
        </span>
        {onDismiss && (
          <button
            className="text-gray-500 hover:text-gray-300 transition-colors"
            onClick={onDismiss}
          >
            关闭
          </button>
        )}
      </div>
    </div>
  );
};

// ==================== 建议行 ====================

const SuggestionRow: React.FC<{
  suggestion: ContextSuggestion;
  onDismiss: () => void;
}> = ({ suggestion, onDismiss }) => {
  const [expanded, setExpanded] = useState(false);

  const colorClass =
    suggestion.severity === 'warning'
      ? 'text-yellow-400 border-yellow-500/30 bg-yellow-500/10'
      : 'text-blue-400 border-blue-500/30 bg-blue-500/5';

  return (
    <div className={`rounded border px-2 py-1 text-xs ${colorClass}`}>
      <div className="flex items-center justify-between">
        <button
          className="flex-1 text-left font-medium truncate hover:underline"
          onClick={() => setExpanded(!expanded)}
          title={suggestion.title}
        >
          <span className="mr-1">
            {suggestion.severity === 'warning' ? '⚠️' : 'ℹ️'}
          </span>
          {suggestion.title}
          {suggestion.savingsTokens != null && suggestion.savingsTokens > 0 && (
            <span className="ml-1 text-gray-500">
              (可节省 ~{formatTokens(suggestion.savingsTokens)})
            </span>
          )}
        </button>
        <button
          className="ml-2 text-gray-600 hover:text-gray-300 leading-none"
          onClick={onDismiss}
          title="忽略此建议"
        >
          ×
        </button>
      </div>
      {expanded && (
        <p className="mt-1 text-gray-400 leading-relaxed">{suggestion.detail}</p>
      )}
    </div>
  );
};

// ==================== 工具函数 ====================

function formatTokens(tokens: number): string {
  if (tokens >= 1_000_000) return `${(tokens / 1_000_000).toFixed(1)}M`;
  if (tokens >= 1_000) return `${(tokens / 1_000).toFixed(1)}K`;
  return String(Math.round(tokens));
}

export default ContextAnalysisBanner;

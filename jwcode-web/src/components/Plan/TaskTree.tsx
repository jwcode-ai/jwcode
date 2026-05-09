import { memo, useState, useCallback } from 'react';
import { PlanTask } from '../../types';
import { ChevronRight, ChevronDown, ChevronUp } from 'lucide-react';

const AGENT_ICONS: Record<string, string> = {
  coder: '💻',
  debug: '🐛',
  explore: '🔍',
  architect: '🏗️',
  test: '🧪',
  reviewer: '👁️',
  doc: '📝',
  default: '⚙️',
};

const AGENT_COLORS: Record<string, string> = {
  coder: 'border-l-blue-500',
  debug: 'border-l-orange-500',
  explore: 'border-l-purple-500',
  architect: 'border-l-yellow-500',
  test: 'border-l-green-500',
  reviewer: 'border-l-cyan-500',
  doc: 'border-l-pink-500',
  default: 'border-l-gray-500',
};

const STATUS_CONFIG = {
  pending: {
    icon: '⏳',
    bg: 'bg-gray-500/10',
    border: 'border-gray-500/30',
    text: 'text-gray-400',
    label: '等待中',
  },
  running: {
    icon: '🔄',
    bg: 'bg-blue-500/10',
    border: 'border-blue-500/50',
    text: 'text-blue-400',
    label: '执行中',
  },
  completed: {
    icon: '✅',
    bg: 'bg-green-500/10',
    border: 'border-green-500/30',
    text: 'text-green-400',
    label: '已完成',
  },
  failed: {
    icon: '❌',
    bg: 'bg-red-500/10',
    border: 'border-red-500/30',
    text: 'text-red-400',
    label: '失败',
  },
  skipped: {
    icon: '⏭️',
    bg: 'bg-gray-500/5',
    border: 'border-gray-500/20',
    text: 'text-gray-500',
    label: '已跳过',
  },
};

interface TaskTreeProps {
  tasks: PlanTask[];
  activeTaskId: string | null;
  onTaskClick: (task: PlanTask) => void;
  /** 缩进层级（内部递归使用） */
  depth?: number;
}

/**
 * 树形任务展示组件
 * 递归渲染 PlanTask 的 children 层级结构
 */
export const TaskTree = memo(function TaskTree({
  tasks,
  activeTaskId,
  onTaskClick,
  depth = 0,
}: TaskTreeProps) {
  if (!tasks || tasks.length === 0) {
    return depth === 0 ? (
      <div className="flex items-center justify-center h-20 text-xs text-dark-muted">
        暂无任务
      </div>
    ) : null;
  }

  return (
    <div className={depth === 0 ? 'space-y-1' : 'ml-5 space-y-1'}>
      {tasks.map((task) => (
        <TaskTreeNode
          key={task.id}
          task={task}
          activeTaskId={activeTaskId}
          onTaskClick={onTaskClick}
          depth={depth}
        />
      ))}
    </div>
  );
});

/**
 * 单个树形节点（递归）
 */
const TaskTreeNode = memo(function TaskTreeNode({
  task,
  activeTaskId,
  onTaskClick,
  depth,
}: {
  task: PlanTask;
  activeTaskId: string | null;
  onTaskClick: (task: PlanTask) => void;
  depth: number;
}) {
  const [isExpanded, setIsExpanded] = useState(true);
  const hasChildren = task.children && task.children.length > 0;
  const isActive = task.id === activeTaskId;
  const statusCfg = STATUS_CONFIG[task.status] || STATUS_CONFIG.pending;
  const agentIcon = AGENT_ICONS[task.agentType] || AGENT_ICONS.default;
  const agentColor = AGENT_COLORS[task.agentType] || AGENT_COLORS.default;

  const handleToggle = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    setIsExpanded((prev) => !prev);
  }, []);

  const handleClick = useCallback(() => {
    onTaskClick(task);
  }, [onTaskClick, task]);

  const formatDuration = (start?: number, end?: number) => {
    if (!start) return '';
    const ms = (end || Date.now()) - start;
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(1)}s`;
  };

  return (
    <div>
      {/* 节点行 */}
      <div
        onClick={handleClick}
        className={`
          group flex items-center gap-1.5 p-2 rounded-lg border cursor-pointer transition-all
          ${statusCfg.bg} ${statusCfg.border}
          ${isActive ? 'ring-2 ring-blue-500 shadow-lg shadow-blue-500/10 scale-[1.01]' : 'hover:scale-[1.005] hover:shadow-sm'}
          border-l-4 ${agentColor}
        `}
        style={{ paddingLeft: `${depth === 0 ? 12 : 8}px` }}
      >
        {/* 展开/折叠按钮 */}
        {hasChildren ? (
          <button
            onClick={handleToggle}
            className="shrink-0 p-0.5 rounded hover:bg-dark-hover transition-colors"
          >
            {isExpanded ? (
              <ChevronDown size={14} className="text-dark-muted" />
            ) : (
              <ChevronRight size={14} className="text-dark-muted" />
            )}
          </button>
        ) : (
          <span className="w-[18px] shrink-0" />
        )}

        {/* Agent 图标 */}
        <span className="text-base shrink-0">{agentIcon}</span>

        {/* 任务信息 */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5">
            <span className="text-xs font-medium text-dark-text truncate">
              {task.title}
            </span>
            <span className={`text-[10px] px-1 py-0.5 rounded-full ${statusCfg.bg} ${statusCfg.text} shrink-0`}>
              {statusCfg.label}
            </span>
          </div>
          <div className="flex items-center gap-2 text-[10px] text-dark-muted">
            <span>{task.agentType.charAt(0).toUpperCase() + task.agentType.slice(1)}Agent</span>
            {task.startedAt && (
              <span>⏱ {formatDuration(task.startedAt, task.completedAt)}</span>
            )}
            {hasChildren && (
              <span>📂 {task.children!.length} 个子任务</span>
            )}
          </div>
        </div>

        {/* 状态图标 */}
        <span className={`text-xs ${statusCfg.text} shrink-0`}>
          {statusCfg.icon}
        </span>
      </div>

      {/* 子任务递归渲染 */}
      {hasChildren && isExpanded && (
        <div className="relative ml-2">
          {/* 竖线连接 */}
          <div className="absolute left-[11px] top-0 bottom-0 w-0.5 bg-dark-border/50" />
          <TaskTree
            tasks={task.children!}
            activeTaskId={activeTaskId}
            onTaskClick={onTaskClick}
            depth={depth + 1}
          />
        </div>
      )}

      {/* 进度条（运行中的任务） */}
      {task.status === 'running' && task.progress !== undefined && (
        <div className="ml-7 mr-2 mb-1">
          <div className="flex items-center justify-between text-[10px] text-dark-muted mb-0.5">
            <span>进度</span>
            <span>{task.progress}%</span>
          </div>
          <div className="h-1 bg-dark-bg rounded-full overflow-hidden">
            <div
              className="h-full bg-blue-500 rounded-full transition-all duration-500"
              style={{ width: `${task.progress}%` }}
            />
          </div>
        </div>
      )}

      {/* 错误信息（可展开） */}
      {task.status === 'failed' && task.error && (
        <div className="ml-7 mr-2 mb-1">
          <div className="p-1.5 bg-red-500/10 rounded text-[10px] text-red-400 break-all">
            {task.error.length > 80 ? (
              <ExpandableText text={task.error} type="error" />
            ) : (
              task.error
            )}
          </div>
        </div>
      )}

      {/* 完成结果 */}
      {task.status === 'completed' && task.result && (
        <div className="ml-7 mr-2 mb-1 p-1.5 bg-green-500/5 rounded text-[10px] text-green-400/80 break-all">
          {task.result.length > 80 ? (
            <ExpandableText text={task.result} type="success" />
          ) : (
            task.result
          )}
        </div>
      )}
    </div>
  );
});

/**
 * 可展开/折叠的文本组件
 * 用于显示较长的错误信息或结果详情
 */
const ExpandableText = memo(function ExpandableText({
  text,
  type,
}: {
  text: string;
  type: 'error' | 'success';
}) {
  const [isExpanded, setIsExpanded] = useState(false);
  const truncated = text.substring(0, 80);

  return (
    <div>
      <div className="flex items-start gap-1">
        <span className="flex-1 break-all">
          {isExpanded ? text : truncated + '...'}
        </span>
        <button
          onClick={(e) => {
            e.stopPropagation();
            setIsExpanded(!isExpanded);
          }}
          className={`shrink-0 p-0.5 rounded hover:bg-dark-hover transition-colors ${
            type === 'error' ? 'text-red-400' : 'text-green-400'
          }`}
        >
          {isExpanded ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
        </button>
      </div>
    </div>
  );
});

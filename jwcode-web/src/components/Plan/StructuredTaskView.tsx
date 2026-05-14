import { memo } from 'react';
import { StructuredTask, ExecutionMode, TaskPhase } from '../../types';
import { Zap, ArrowRight } from 'lucide-react';

// ==================== 常量配置 ====================

const PHASE_CONFIG: Record<TaskPhase, { icon: string; label: string; color: string; bgColor: string }> = {
  EXPLORATION: { icon: '🔍', label: '调研探索', color: 'text-purple-400', bgColor: 'bg-purple-500/10' },
  DESIGN: { icon: '🏗️', label: '架构设计', color: 'text-yellow-400', bgColor: 'bg-yellow-500/10' },
  IMPLEMENTATION: { icon: '💻', label: '代码实现', color: 'text-blue-400', bgColor: 'bg-blue-500/10' },
  TESTING: { icon: '🧪', label: '测试验证', color: 'text-green-400', bgColor: 'bg-green-500/10' },
  REVIEW: { icon: '👁️', label: '代码审查', color: 'text-cyan-400', bgColor: 'bg-cyan-500/10' },
  DOCUMENTATION: { icon: '📝', label: '文档编写', color: 'text-pink-400', bgColor: 'bg-pink-500/10' },
  GENERAL: { icon: '📌', label: '通用任务', color: 'text-gray-400', bgColor: 'bg-gray-500/10' },
};

const STATUS_CONFIG = {
  pending: { icon: '⏳', bg: 'bg-gray-500/10', border: 'border-gray-500/30', text: 'text-gray-400', label: '等待中' },
  running: { icon: '🔄', bg: 'bg-blue-500/10', border: 'border-blue-500/50', text: 'text-blue-400', label: '执行中', pulse: true },
  completed: { icon: '✅', bg: 'bg-green-500/10', border: 'border-green-500/30', text: 'text-green-400', label: '已完成' },
  failed: { icon: '❌', bg: 'bg-red-500/10', border: 'border-red-500/30', text: 'text-red-400', label: '失败' },
  skipped: { icon: '⏭️', bg: 'bg-gray-500/5', border: 'border-gray-500/20', text: 'text-gray-500', label: '已跳过' },
};

const AGENT_ICONS: Record<string, string> = {
  coder: '💻', debug: '🐛', explore: '🔍', architect: '🏗️',
  test: '🧪', reviewer: '👁️', doc: '📝', default: '⚙️',
};

// ==================== 子组件 ====================

/**
 * 执行模式徽章
 */
const ModeBadge = memo(function ModeBadge({ mode }: { mode: ExecutionMode }) {
  if (mode === 'CONCURRENT') {
    return (
      <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-medium bg-amber-500/15 text-amber-400 border border-amber-500/30">
        <Zap size={10} /> 并发
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-medium bg-blue-500/15 text-blue-400 border border-blue-500/30">
      <ArrowRight size={10} /> 串行
    </span>
  );
});

/**
 * 阶段标签
 */
const PhaseTag = memo(function PhaseTag({ phase }: { phase: TaskPhase }) {
  const config = PHASE_CONFIG[phase] || PHASE_CONFIG.GENERAL;
  return (
    <span className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-medium ${config.bgColor} ${config.color}`}>
      {config.icon} {config.label}
    </span>
  );
});

/**
 * 进度条
 */
const ProgressBar = memo(function ProgressBar({ progress }: { progress: number }) {
  return (
    <div className="w-full h-1 bg-dark-bg rounded-full overflow-hidden">
      <div
        className="h-full bg-gradient-to-r from-blue-500 to-green-500 rounded-full transition-all duration-500"
        style={{ width: `${Math.min(100, Math.max(0, progress))}%` }}
      />
    </div>
  );
});

/**
 * 并发组连接线
 */
const ParallelGroupIndicator = memo(function ParallelGroupIndicator({
  groupName,
  taskCount,
}: {
  groupName: string;
  taskCount: number;
}) {
  return (
    <div className="flex items-center gap-2 ml-2 mb-1">
      <div className="flex-1 h-px bg-amber-500/30" />
      <span className="text-[10px] text-amber-400/70 flex items-center gap-1">
        <Zap size={10} />
        并发组 {groupName} ({taskCount}个任务并行)
      </span>
      <div className="flex-1 h-px bg-amber-500/30" />
    </div>
  );
});

// ==================== 主组件 ====================

interface StructuredTaskViewProps {
  tasks: StructuredTask[];
  activeTaskId: string | null;
  onTaskClick: (task: StructuredTask) => void;
  depth?: number;
}

/**
 * StructuredTaskView — 结构化任务视图组件。
 * 
 * 递归渲染 StructuredTask 的树形层级结构，展示：
 * - 执行模式（串行/并发）
 * - 阶段分组
 * - 任务进度
 * - 依赖关系
 * - 并发组连接
 */
export const StructuredTaskView = memo(function StructuredTaskView({
  tasks,
  activeTaskId,
  onTaskClick,
  depth = 0,
}: StructuredTaskViewProps) {
  if (!tasks || tasks.length === 0) {
    return (
      <div className="flex items-center justify-center py-12 text-dark-muted text-sm">
        暂无结构化任务
      </div>
    );
  }

  // 过滤出并发组
  const parallelGroups = tasks
    .filter((t) => t.executionMode === 'CONCURRENT' && t.parallelGroup)
    .map((t) => t.parallelGroup!)
    .filter((v, i, a) => a.indexOf(v) === i);

  return (
    <div className="space-y-1" style={{ paddingLeft: depth > 0 ? 0 : 0 }}>
      {tasks.map((task, index) => {
        const isActive = task.id === activeTaskId;
        const isPhaseWrapper = task.children && task.children.length > 0;
        const hasChildren = isPhaseWrapper;
        const statusConfig = STATUS_CONFIG[task.status] || STATUS_CONFIG.pending;
        const agentIcon = AGENT_ICONS[task.agentType] || AGENT_ICONS.default;
        const phaseConfig = PHASE_CONFIG[task.phase] || PHASE_CONFIG.GENERAL;

        // 并发组分隔线
        const showParallelIndicator =
          task.executionMode === 'CONCURRENT' &&
          task.parallelGroup &&
          parallelGroups.includes(task.parallelGroup) &&
          tasks.findIndex((t) => t.parallelGroup === task.parallelGroup) === index;

        return (
          <div key={task.id}>
            {/* 并发组指示器 */}
            {showParallelIndicator && (
              <ParallelGroupIndicator
                groupName={task.parallelGroup!}
                taskCount={
                  tasks.filter((t) => t.parallelGroup === task.parallelGroup).length
                }
              />
            )}

            {/* 任务项 */}
            <div
              onClick={() => onTaskClick(task)}
              className={`
                group relative rounded-lg border transition-all duration-200 cursor-pointer
                ${isActive
                  ? 'border-blue-500/50 bg-blue-500/10 shadow-sm shadow-blue-500/10'
                  : 'border-dark-border bg-dark-surface hover:border-dark-muted/50 hover:bg-dark-bg/50'
                }
                ${task.status === 'running' ? 'animate-pulse-border' : ''}
                ${depth > 0 ? 'ml-6' : ''}
                mb-1
              `}
            >
              <div className="p-2.5">
                {/* 顶部：阶段标签 + 执行模式 + 步骤编号 */}
                <div className="flex items-center gap-2 mb-1.5">
                  {isPhaseWrapper ? (
                    <PhaseTag phase={task.phase} />
                  ) : (
                    <>
                      <PhaseTag phase={task.phase} />
                      <ModeBadge mode={task.executionMode} />
                    </>
                  )}
                  {task.stepNumber != null && task.stepNumber > 0 && (
                    <span className="text-[10px] text-dark-muted">
                      Step {task.stepNumber}
                    </span>
                  )}
                  <span className="flex-1" />
                  <span className={`text-[10px] px-1.5 py-0.5 rounded ${statusConfig.bg} ${statusConfig.text}`}>
                    {statusConfig.icon} {statusConfig.label}
                  </span>
                </div>

                {/* 标题行 */}
                <div className="flex items-start gap-2">
                  <span className="text-sm mt-0.5">
                    {hasChildren ? phaseConfig.icon : agentIcon}
                  </span>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-1.5">
                      <span
                        className={`text-sm font-medium truncate ${
                          isActive ? 'text-blue-400' : 'text-dark-text'
                        }`}
                      >
                        {task.title}
                      </span>
                      {hasChildren && (
                        <span className="text-[10px] text-dark-muted flex-shrink-0">
                          ({task.children!.length}个子任务)
                        </span>
                      )}
                    </div>
                    {task.description && task.description !== task.title && (
                      <p className="text-xs text-dark-muted mt-0.5 line-clamp-2">
                        {task.description}
                      </p>
                    )}

                    {/* 任务上下文（文件路径、模块等） */}
                    {task.context && Object.keys(task.context).length > 0 && (
                      <div className="flex items-center gap-1 mt-1 flex-wrap">
                        {task.context.relatedFiles && (
                          <span className="text-[9px] px-1.5 py-0.5 bg-purple-500/10 text-purple-400 rounded border border-purple-500/20 truncate max-w-[180px]" title={task.context.relatedFiles}>
                            📄 {task.context.relatedFiles.split(',')[0]}
                            {task.context.relatedFiles.includes(',') && ' ...'}
                          </span>
                        )}
                        {task.context.targetModule && (
                          <span className="text-[9px] px-1.5 py-0.5 bg-yellow-500/10 text-yellow-400 rounded border border-yellow-500/20">
                            📦 {task.context.targetModule}
                          </span>
                        )}
                        {task.context.constraints && (
                          <span className="text-[9px] px-1.5 py-0.5 bg-red-500/10 text-red-400 rounded border border-red-500/20 truncate max-w-[150px]" title={task.context.constraints}>
                            ⚠️ 约束
                          </span>
                        )}
                        {task.context.phase && (
                          <span className="text-[9px] px-1.5 py-0.5 bg-blue-500/10 text-blue-400 rounded border border-blue-500/20">
                            🔹 {task.context.phaseLabel || task.context.phase}
                          </span>
                        )}
                      </div>
                    )}
                  </div>
                </div>

                {/* 依赖关系 */}
                {task.dependencies && task.dependencies.length > 0 && (
                  <div className="mt-1.5 flex items-center gap-1 flex-wrap">
                    <span className="text-[10px] text-dark-muted">依赖:</span>
                    {task.dependencies.map((dep) => (
                      <span
                        key={dep}
                        className="text-[10px] px-1 py-0.5 rounded bg-dark-bg text-dark-muted border border-dark-border"
                      >
                        {dep}
                      </span>
                    ))}
                  </div>
                )}

                {/* 进度条 */}
                {task.status === 'running' && (
                  <div className="mt-2">
                    <ProgressBar progress={task.progress || 0} />
                  </div>
                )}

                {/* 结果/错误信息 */}
                {task.result && task.status === 'completed' && (
                  <div className="mt-1.5 text-xs text-green-400/80 bg-green-500/5 rounded p-1.5 border border-green-500/10">
                    {task.result.length > 150 ? task.result.substring(0, 150) + '...' : task.result}
                  </div>
                )}
                {task.error && task.status === 'failed' && (
                  <div className="mt-1.5 text-xs text-red-400/80 bg-red-500/5 rounded p-1.5 border border-red-500/10">
                    {task.error.length > 150 ? task.error.substring(0, 150) + '...' : task.error}
                  </div>
                )}
              </div>

              {/* 选中指示条 */}
              {isActive && (
                <div className="absolute left-0 top-0 bottom-0 w-0.5 bg-blue-500 rounded-l-lg" />
              )}
            </div>

            {/* 递归渲染子任务 */}
            {hasChildren && (
              <div className="ml-2 pl-4 border-l-2 border-dark-border/50">
                <StructuredTaskView
                  tasks={task.children!}
                  activeTaskId={activeTaskId}
                  onTaskClick={onTaskClick}
                  depth={depth + 1}
                />
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
});

// ==================== 汇总面板 ====================

interface StructuredTaskSummaryProps {
  tasks: StructuredTask[];
}

/**
 * 结构化任务汇总面板 — 显示任务统计信息
 */
export const StructuredTaskSummary = memo(function StructuredTaskSummary({
  tasks,
}: StructuredTaskSummaryProps) {
  const stats = useTaskStats(tasks);

  return (
    <div className="grid grid-cols-4 gap-2 mb-3">
      <StatCard label="总任务" value={stats.total} icon="📋" color="text-gray-400" />
      <StatCard label="并发组" value={stats.concurrentGroups} icon="⚡" color="text-amber-400" />
      <StatCard label="串行任务" value={stats.sequentialCount} icon="➡️" color="text-blue-400" />
      <StatCard label="阶段数" value={stats.phases} icon="🏗️" color="text-purple-400" />
    </div>
  );
});

function StatCard({
  label,
  value,
  icon,
  color,
}: {
  label: string;
  value: number;
  icon: string;
  color: string;
}) {
  return (
    <div className="bg-dark-surface rounded-lg border border-dark-border p-2.5 text-center">
      <div className={`text-lg ${color}`}>{icon}</div>
      <div className="text-lg font-bold text-dark-text">{value}</div>
      <div className="text-[10px] text-dark-muted">{label}</div>
    </div>
  );
}

/**
 * 统计结构化任务信息
 */
function useTaskStats(tasks: StructuredTask[]) {
  const stats = {
    total: 0,
    concurrentGroups: 0,
    sequentialCount: 0,
    phases: new Set<TaskPhase>(),
  };

  const traverse = (taskList: StructuredTask[]) => {
    for (const task of taskList) {
      stats.total++;
      stats.phases.add(task.phase);
      if (task.executionMode === 'CONCURRENT') {
        if (task.parallelGroup) {
          stats.concurrentGroups++;
        }
      } else {
        stats.sequentialCount++;
      }
      if (task.children) {
        traverse(task.children);
      }
    }
  };

  traverse(tasks);

  return {
    total: stats.total,
    concurrentGroups: stats.concurrentGroups,
    sequentialCount: stats.sequentialCount,
    phases: stats.phases.size,
  };
}

export default StructuredTaskView;

/**
 * PlanTaskBoard -- structured plan task visualization with theme-aware colors.
 */
import { Box, Text } from 'ink';
import { memo, useMemo } from 'react';
import type { PlanTask } from '../protocol.js';
import { t } from '../theme.js';

interface Props {
  tasks: PlanTask[];
  terminalCols: number;
}

const STATUS_ICON: Record<string, string> = {
  pending: '○',
  running: '●',
  completed: '✓',
  failed: '✗',
  skipped: '→',
  blocked: '⌇',
};

const STATUS_COLOR: Record<string, string> = {
  pending: t.muted,
  running: t.primary,
  completed: t.success,
  failed: t.error,
  skipped: t.warning,
  blocked: t.warning,
};

const PHASE_LABELS: Record<number, string> = {
  1: 'Research',
  2: 'Design',
  3: 'Implement',
  4: 'Review',
  5: 'Iterate',
};

function formatDur(sec?: number): string {
  if (!sec || sec <= 0) return '';
  if (sec >= 60) {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m}m${s}s`;
  }
  return `${sec}s`;
}

export const PlanTaskBoard = memo(function PlanTaskBoard({ tasks, terminalCols }: Props) {
  const grouped = useMemo(() => {
    const map = new Map<number, PlanTask[]>();
    for (const t of tasks) {
      const phase = t.phase || 1;
      if (!map.has(phase)) map.set(phase, []);
      map.get(phase)!.push(t);
    }
    return [...map.entries()].sort((a, b) => a[0] - b[0]);
  }, [tasks]);

  const total = tasks.length;
  const completed = tasks.filter(t => t.status === 'completed').length;
  const failed = tasks.filter(t => t.status === 'failed').length;
  const running = tasks.filter(t => t.status === 'running').length;

  if (total === 0) return null;

  const maxWidth = Math.min(terminalCols - 4, 80);

  return (
    <Box flexDirection="column" borderStyle="single" borderColor={t.primary} paddingX={1} marginBottom={1}>
      <Box>
        <Text bold color={t.primary}>Task Board</Text>
        <Text dimColor>  </Text>
        <Text color={t.success}>{completed}</Text>
        <Text dimColor>/</Text>
        <Text color={t.text}>{total}</Text>
        {running > 0 && (
          <>
            <Text dimColor>  running: </Text>
            <Text color={t.primary}>{running}</Text>
          </>
        )}
        {failed > 0 && (
          <>
            <Text dimColor>  failed: </Text>
            <Text color={t.error}>{failed}</Text>
          </>
        )}
      </Box>

      <Box marginY={1}>
        <Text dimColor>[</Text>
        <Text color={t.success}>{'█'.repeat(Math.round(completed / Math.max(total, 1) * 20))}</Text>
        <Text color={t.primary}>{'█'.repeat(Math.round(running / Math.max(total, 1) * 20))}</Text>
        <Text dimColor>{'─'.repeat(Math.max(0, 20 - Math.round((completed + running) / Math.max(total, 1) * 20)))}</Text>
        <Text dimColor>] </Text>
        <Text>{Math.round((completed / Math.max(total, 1)) * 100)}%</Text>
      </Box>

      {grouped.map(([phase, phaseTasks]) => (
        <Box key={phase} flexDirection="column" marginBottom={1}>
          <Box>
            <Text backgroundColor={t.plan} color="black"> Phase {phase} </Text>
            <Text color={t.primary} bold> {PHASE_LABELS[phase] || `Phase ${phase}`}</Text>
            <Text dimColor> ({phaseTasks.length} tasks)</Text>
          </Box>
          {phaseTasks.map(task => {
            const icon = STATUS_ICON[task.status] || '?';
            const color = STATUS_COLOR[task.status] || t.text;
            const durStr = formatDur(task.duration);
            const dim = task.status === 'pending';
            return (
              <Box key={task.id} paddingLeft={3} flexDirection="column">
                <Box>
                  <Text color={color} dimColor={dim}>{icon} </Text>
                  <Text bold={task.status === 'running'} color={color} dimColor={dim}>
                    {task.title.slice(0, maxWidth - 30)}
                  </Text>
                  {durStr && (
                    <>
                      <Text dimColor>  </Text>
                      <Text color={t.muted} dimColor>{durStr}</Text>
                    </>
                  )}
                  {task.agentType && (
                    <>
                      <Text dimColor>  </Text>
                      <Text color={t.tool} dimColor={dim}>{task.agentType}</Text>
                    </>
                  )}
                </Box>
                {task.error && (
                  <Box paddingLeft={3}>
                    <Text color={t.error} dimColor>{task.error.slice(0, maxWidth - 8)}</Text>
                  </Box>
                )}
                {task.result && task.status === 'completed' && (
                  <Box paddingLeft={3}>
                    <Text color={t.success} dimColor>{task.result.slice(0, maxWidth - 8)}</Text>
                  </Box>
                )}
              </Box>
            );
          })}
        </Box>
      ))}
    </Box>
  );
});

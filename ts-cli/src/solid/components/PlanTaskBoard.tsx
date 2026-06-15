/**
 * PlanTaskBoard — structured plan task visualization with theme-aware colors.
 * Ported from Ink/React to Solid/OpenTUI.
 */
import { createMemo } from "solid-js"
import { useTheme } from "../context/theme"
import type { PlanTask } from "../../protocol"

interface Props {
  tasks: PlanTask[]
  terminalCols: number
}

function formatDur(sec?: number): string {
  if (!sec || sec <= 0) return ""
  if (sec >= 60) {
    const m = Math.floor(sec / 60)
    const s = sec % 60
    return `${m}m${s}s`
  }
  return `${sec}s`
}

export function PlanTaskBoard(props: Props) {
  const { theme } = useTheme()

  const grouped = createMemo(() => {
    const map = new Map<number, PlanTask[]>()
    for (const t of props.tasks) {
      const phase = t.phase || 1
      if (!map.has(phase)) map.set(phase, [])
      map.get(phase)!.push(t)
    }
    return [...map.entries()].sort((a, b) => a[0] - b[0])
  })

  const total = () => props.tasks.length
  const completed = () => props.tasks.filter((t) => t.status === "completed").length
  const failed = () => props.tasks.filter((t) => t.status === "failed").length
  const running = () => props.tasks.filter((t) => t.status === "running").length

  if (total() === 0) return null

  const maxWidth = () => Math.min(props.terminalCols - 4, 80)

  const STATUS_ICON: Record<string, string> = {
    pending: "○",
    running: "●",
    completed: "✓",
    failed: "✗",
    skipped: "→",
    blocked: "⌇",
  }

  const PHASE_LABELS: Record<number, string> = {
    1: "Research",
    2: "Design",
    3: "Implement",
    4: "Review",
    5: "Iterate",
  }

  return (
    <box flexDirection="column" paddingX={1} marginBottom={1}>
      <box>
        <text fg={theme.primary} attributes={1}>Task Board</text>
        <text fg={theme.textMuted}>  </text>
        <text fg={theme.success}>{completed()}</text>
        <text fg={theme.textMuted}>/</text>
        <text fg={theme.text}>{total()}</text>
        {running() > 0 && (
          <>
            <text fg={theme.textMuted}>  running: </text>
            <text fg={theme.primary}>{running()}</text>
          </>
        )}
        {failed() > 0 && (
          <>
            <text fg={theme.textMuted}>  failed: </text>
            <text fg={theme.error}>{failed()}</text>
          </>
        )}
      </box>

      <box>
        <text fg={theme.textMuted}>[</text>
        <text fg={theme.success}>{"█".repeat(Math.round(completed() / Math.max(total(), 1) * 20))}</text>
        <text fg={theme.primary}>{"█".repeat(Math.round(running() / Math.max(total(), 1) * 20))}</text>
        <text fg={theme.textMuted}>{"─".repeat(Math.max(0, 20 - Math.round((completed() + running()) / Math.max(total(), 1) * 20)))}</text>
        <text fg={theme.textMuted}>] </text>
        <text>{Math.round((completed() / Math.max(total(), 1)) * 100)}%</text>
      </box>

      {grouped().map(([phase, phaseTasks]) => (
        <box key={phase} flexDirection="column" marginBottom={1}>
          <box>
            <text bg={theme.primary} fg={theme.background}> Phase {phase} </text>
            <text fg={theme.primary} attributes={1}> {PHASE_LABELS[phase] || `Phase ${phase}`}</text>
            <text fg={theme.textMuted}> ({phaseTasks.length} tasks)</text>
          </box>
          {phaseTasks.map((task) => {
            const icon = STATUS_ICON[task.status] || "?"
            const durStr = formatDur(task.duration)
            const dim = task.status === "pending"
            return (
              <box key={task.id} paddingLeft={3} flexDirection="column">
                <box>
                  <text fg={theme.textMuted}>{icon} </text>
                  <text
                    attributes={task.status === "running" ? 1 : 0}
                    fg={theme.text}
                  >
                    {task.title.slice(0, maxWidth() - 30)}
                  </text>
                  {durStr && (
                    <>
                      <text fg={theme.textMuted}>  </text>
                      <text fg={theme.textMuted}>{durStr}</text>
                    </>
                  )}
                  {task.agentType && (
                    <>
                      <text fg={theme.textMuted}>  </text>
                      <text fg={theme.textMuted}>{task.agentType}</text>
                    </>
                  )}
                </box>
                {task.error && (
                  <box paddingLeft={3}>
                    <text fg={theme.error}>{task.error.slice(0, maxWidth() - 8)}</text>
                  </box>
                )}
                {task.result && task.status === "completed" && (
                  <box paddingLeft={3}>
                    <text fg={theme.success}>{task.result.slice(0, maxWidth() - 8)}</text>
                  </box>
                )}
              </box>
            )
          })}
        </box>
      ))}
    </box>
  )
}

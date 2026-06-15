/**
 * AssistantToolUseMessage - renders tool call progress tree.
 */
import type { ToolCall } from "../../../protocol"
import { useTheme } from "../../context/theme"
import { AgentProgressTree, toolCallsToTreeNodes } from "../AgentProgressTree"

interface Props {
  toolCalls: ToolCall[]
  terminalCols: number
}

export function AssistantToolUseMessage(props: Props) {
  const { theme } = useTheme()
  const t = theme as any

  if (!props.toolCalls || props.toolCalls.length === 0) return null

  const nodes = () => toolCallsToTreeNodes(props.toolCalls)

  // Check if AgentProgressTree produces visible output
  if (nodes().length === 0) {
    // Fallback: simple text list
    return (
      <box paddingX={1} flexDirection="column">
        {props.toolCalls.map((tc) => (
          <box>
            <text fg={t.warning}>  Tool: {tc.name || "unknown"}</text>
            {tc.args && <text fg={t.textMuted}> {tc.args}</text>}
            <text fg={tc.status === "complete" ? t.success : t.warning}>
              {tc.status === "complete" ? " [done]" : " [running]"}
            </text>
          </box>
        ))}
      </box>
    )
  }

  return (
    <box marginY={1}>
      <AgentProgressTree
        nodes={nodes()}
        terminalCols={props.terminalCols}
      />
    </box>
  )
}

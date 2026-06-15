/**
 * AssistantMessage - composite component that combines thinking, tool calls, and text.
 */
import { Show } from "solid-js"
import type { Message } from "../../../protocol"
import { useTheme } from "../../context/theme"
import { AssistantTextMessage } from "./AssistantTextMessage"
import { AssistantThinkingMessage } from "./AssistantThinkingMessage"
import { AssistantToolUseMessage } from "./AssistantToolUseMessage"
import { formatTimestamp } from "./format"

interface Props {
  msg: Message
  terminalCols: number
}

export function AssistantMessage(props: Props) {
  const { theme } = useTheme()
  const t = theme as any
  const msg = () => props.msg

  return (
    <box flexDirection="column"
      backgroundColor={t.assistantMessageBackground}
      paddingTop={1}
      paddingBottom={1}
    >
      {/* Header */}
      <box paddingLeft={1}>
        <text fg={t.primary} attributes={1}>Assistant</text>
        <text fg={t.textMuted}> {formatTimestamp(msg().timestamp)}</text>
      </box>

      {/* Thinking */}
      <Show when={msg().thinking}>
        <AssistantThinkingMessage thinking={msg().thinking} />
      </Show>

      {/* Tool calls */}
      <Show when={msg().toolCalls?.length > 0}>
        <AssistantToolUseMessage
          toolCalls={msg().toolCalls}
          terminalCols={props.terminalCols}
        />
      </Show>

      {/* Steps (legacy support) */}
      <Show when={msg().steps?.length > 0}>
        <StepDisplay steps={msg().steps} theme={t} />
      </Show>

      {/* Content */}
      <Show when={msg().content}>
        <AssistantTextMessage msg={msg()} terminalCols={props.terminalCols} />
      </Show>
    </box>
  )
}

function StepDisplay(props: { steps: any[]; theme: any }) {
  const { steps, theme: t } = props
  return (
    <box flexDirection="column" marginY={1}>
      {steps.map((step) => {
        const icon = step.status === "success" ? "[ok]"
          : step.status === "error" ? "[!!]"
          : step.status === "running" ? "[..]"
          : "[--]"
        const color = step.status === "success" ? t.success
          : step.status === "error" ? t.error
          : t.primary
        const durStr = step.duration ? formatDuration(step.duration) : ""
        return (
          <box flexDirection="column">
            <box>
              <text fg={color}>{`  ${icon} `}</text>
              <text fg={color} attributes={1}>{step.title}</text>
              {durStr ? <text fg={t.textMuted}>{`  ${durStr}`}</text> : null}
            </box>
            {step.thought ? <text fg={t.textMuted}>{`    ${truncate(step.thought, 200)}`}</text> : null}
            {step.action ? <text fg={t.warning}>{`    ${truncate(step.action, 200)}`}</text> : null}
            {step.result ? <text fg={t.success}>{`    ${truncate(step.result, 300)}`}</text> : null}
          </box>
        )
      })}
    </box>
  )
}

function formatDuration(sec: number): string {
  if (sec <= 0) return ""
  if (sec >= 60) {
    const m = Math.floor(sec / 60)
    const s = sec % 60
    return `${m}m${s}s`
  }
  return `${sec}s`
}

function truncate(s: unknown, max: number): string {
  const str = typeof s === "string" ? s : String(s ?? "")
  if (str.length <= max) return str
  return str.slice(0, max) + "..."
}

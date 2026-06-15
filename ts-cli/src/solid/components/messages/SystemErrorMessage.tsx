/**
 * SystemErrorMessage - renders system/error messages with collapsible stack.
 *
 * Click the "▼/▶ expand" hint to toggle the rest of the stack. Keep state
 * local — errors are short-lived and don't need cross-instance sync.
 */
import { createSignal, Show } from "solid-js"
import type { Message } from "../../../protocol"
import { useTheme } from "../../context/theme"

interface Props {
  msg: Message
}

export function SystemErrorMessage(props: Props) {
  const { theme } = useTheme()
  const t = theme as any
  const msg = () => props.msg
  const [expanded, setExpanded] = createSignal(false)

  const firstLine = () => {
    const i = msg().content.indexOf("\n")
    return i >= 0 ? msg().content.slice(0, i) : msg().content
  }
  const rest = () => {
    const i = msg().content.indexOf("\n")
    return i >= 0 ? msg().content.slice(i + 1) : ""
  }
  const hasMore = () => rest().length > 0

  return (
    <box flexDirection="column" backgroundColor={t.backgroundPanel} paddingX={1}>
      <box flexDirection="row">
        <text fg={t.error} attributes={1}>Error: </text>
        <text fg={t.error}>{firstLine()}</text>
        <Show when={hasMore()}>
          <text fg={t.info} onClick={() => setExpanded((v) => !v)}>
            {" "}[{expanded() ? "▼ collapse" : "▶ expand stack"}]
          </text>
        </Show>
      </box>
      <Show when={expanded() && hasMore()}>
        <text fg={t.textMuted}>{rest()}</text>
      </Show>
    </box>
  )
}

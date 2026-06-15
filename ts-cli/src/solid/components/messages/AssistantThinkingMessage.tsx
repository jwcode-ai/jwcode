/**
 * AssistantThinkingMessage - renders thinking/dimmed text with optional collapse.
 */
import { createSignal } from "solid-js"
import { useTheme } from "../../context/theme"

const MAX_VISIBLE_THINKING = 200

interface Props {
  thinking: string
}

export function AssistantThinkingMessage(props: Props) {
  const { theme } = useTheme()
  const t = theme as any
  const [expanded, setExpanded] = createSignal(false)

  if (!props.thinking) return null

  const text = () => props.thinking
  const isLong = () => text().length > MAX_VISIBLE_THINKING
  const display = () => isLong() && !expanded()
    ? text().slice(0, MAX_VISIBLE_THINKING) + "..."
    : text()

  return (
    <box paddingX={1} flexDirection="column">
      <box>
        <text fg={t.textMuted}>{display()}</text>
      </box>
      {isLong() && (
        <box paddingLeft={1}>
          <text
            fg={t.info}
            attributes={1}
            onClick={() => setExpanded((v) => !v)}
          >
            {expanded() ? " [hide thought]" : " [show full thought]"}
          </text>
        </box>
      )}
    </box>
  )
}

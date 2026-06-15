/**
 * UserMessage - renders a user message with header bar and content.
 */
import type { Message } from "../../../protocol"
import { useTheme } from "../../context/theme"

interface Props {
  msg: Message
}

export function UserMessage(props: Props) {
  const { theme } = useTheme()
  const t = theme as any
  const msg = () => props.msg
  const preview = () => msg().content.length > 30
    ? msg().content.slice(0, 30) + "..."
    : msg().content

  return (
    <box flexDirection="column" backgroundColor={t.userMessageBackground}>
      <box paddingLeft={1}>
        <box backgroundColor={t.accent} width={2} height={1} />
        <text fg={t.accent} attributes={1}> You</text>
        {msg().content.length > 30 && (
          <text fg={t.textMuted}> {preview()}</text>
        )}
      </box>
      <box paddingLeft={2}>
        <text fg={t.text}>{msg().content}</text>
      </box>
    </box>
  )
}

/**
 * AssistantTextMessage - renders assistant markdown content.
 */
import type { Message } from "../../../protocol"
import { useTheme } from "../../context/theme"
import { MarkdownRenderer } from "../MarkdownRenderer"
import { formatTimestamp } from "./format"

interface Props {
  msg: Message
  terminalCols: number
}

export function AssistantTextMessage(props: Props) {
  const { theme } = useTheme()
  const t = theme as any
  const msg = () => props.msg

  if (!msg().content) return null

  return (
    <box paddingX={1} marginTop={1}>
      <MarkdownRenderer content={msg().content} terminalCols={props.terminalCols} />
    </box>
  )
}

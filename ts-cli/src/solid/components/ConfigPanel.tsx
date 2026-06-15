/**
 * ConfigPanel — displays connection, model, theme, provider, token usage, and workspace info.
 * Referenced from the Claude Code CLI welcome screen aesthetic.
 */
import { useTheme } from "../context/theme"
import { useAppStateContext } from "../hooks/AppStateProvider"
import { loadConfig } from "../../config"
import { useKeyboard } from "@opentui/solid"

interface Props {
  onClose: () => void
}

export function ConfigPanel(props: Props) {
  const { theme } = useTheme()
  const { state } = useAppStateContext()
  const cfg = loadConfig()

  useKeyboard((key: any) => {
    if (key.name === "escape") {
      props.onClose()
      return
    }
  })

  const usage = () => {
    const u = state.usage
    const total = u.totalTokens || 1
    const pct = (n: number) => Math.min(Math.round((n / total) * 100), 100)
    return { ...u, pPrompt: pct(u.promptTokens), pCompletion: pct(u.completionTokens) }
  }

  const connectedIcon = () => state.connected ? "●" : "○"
  const connectedColor = () => state.connected ? theme.success : theme.error

  return (
    <box borderStyle="single" border={true} borderColor={theme.primary} flexDirection="column" paddingX={1} paddingY={1}>
      {/* Title */}
      <box paddingX={1} marginBottom={1}>
        <text fg={theme.primary} attributes={1}>{" >_" } Configuration</text>
      </box>

      {/* Connection */}
      <box paddingX={1}>
        <text fg={theme.textMuted}>connection: </text>
        <text fg={connectedColor()}>{connectedIcon()}</text>
        <text fg={theme.textMuted}>  {state.connected ? "Connected" : "Disconnected"}</text>
      </box>
      <box paddingX={1}>
        <text fg={theme.textMuted}>  backend:   </text>
        <text fg={theme.text}>{cfg.backend_url}</text>
      </box>
      <box paddingX={1}>
        <text fg={theme.textMuted}>  ws:        </text>
        <text fg={theme.text}>{cfg.ws_url}</text>
      </box>

      {/* Model */}
      <box paddingX={1} marginTop={1}>
        <text fg={theme.textMuted}>model:      </text>
        <text fg={theme.success}>{state.modelName || "connecting..."}</text>
      </box>

      {/* Theme */}
      <box paddingX={1} marginTop={1}>
        <text fg={theme.textMuted}>theme:      </text>
        <text fg={theme.warning}>{state.modelName ? "jwcode" : "jwcode"}</text>
      </box>

      {/* Provider */}
      <box paddingX={1} marginTop={1}>
        <text fg={theme.textMuted}>provider:   </text>
        <text fg={theme.text}>{/* Only available via WS */ state.statusText && state.statusText.includes("Provider") ? state.statusText : "Not configured"}</text>
      </box>

      {/* Token Usage */}
      <box paddingX={1} marginTop={1}>
        <text fg={theme.textMuted}>token usage:</text>
      </box>
      <box paddingX={2} flexDirection="column">
        <box>
          <text fg={theme.textMuted}>  prompt:     </text>
          <text fg={theme.info}>{String(state.usage.promptTokens)}</text>
        </box>
        <box>
          <text fg={theme.textMuted}>  completion: </text>
          <text fg={theme.info}>{String(state.usage.completionTokens)}</text>
        </box>
        <box>
          <text fg={theme.textMuted}>  total:      </text>
          <text fg={theme.info}>{String(state.usage.totalTokens)}</text>
        </box>
        {state.usage.totalTokens > 0 && (
          <box>
            <text fg={theme.textMuted}>  bar:        </text>
            <text fg={theme.primary}>{"█".repeat(usage().pPrompt)}</text>
            <text fg={theme.success}>{"█".repeat(usage().pCompletion)}</text>
            <text fg={theme.textMuted}>{"░".repeat(Math.max(0, 20 - usage().pPrompt - usage().pCompletion))}</text>
            <text fg={theme.textMuted}> {String(state.usage.totalTokens)}</text>
          </box>
        )}
      </box>

      {/* Workspace */}
      <box paddingX={1} marginTop={1}>
        <text fg={theme.textMuted}>workspace:  </text>
        <text fg={theme.warning}>{cfg.workspace_dir || process.cwd()}</text>
      </box>

      {/* Close hint */}
      <box paddingX={1} marginTop={1}>
        <text fg={theme.textMuted}>Press Esc to close</text>
      </box>
    </box>
  )
}

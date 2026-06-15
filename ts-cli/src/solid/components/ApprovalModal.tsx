/**
 * ApprovalModal — risk-classified permission request UI.
 * Ported from Ink/React to Solid/OpenTUI.
 */
import { createSignal, createEffect, onCleanup } from "solid-js"
import { useTheme } from "../context/theme"
import { useKeyboard } from "@opentui/solid"

type RiskLevel = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW"

interface Props {
  toolName: string
  payload: string
  onAllow: () => void
  onDeny: () => void
  onAllowSession: () => void
  onAutoMode: () => void
  queuePosition?: { current: number; total: number }
}

const COUNTDOWN_S = 15

function classifyRisk(toolName: string, payload: string): { level: RiskLevel; reason: string } {
  const name = toolName.toLowerCase()

  if (/\b(rm|del|delete|drop|truncate|format|mkfs)\b/.test(name)) {
    return { level: "CRITICAL", reason: "Destructive — may delete data" }
  }

  if (/\b(bash|shell|exec|cmd|powershell|terminal)\b/.test(name)) {
    if (/\b(rm\s+-rf|sudo|chmod\s+777|curl.*\|\s*(ba)?sh|wget.*-O|>\/dev\/|mkfs)\b/i.test(payload)) {
      return { level: "CRITICAL", reason: "High-risk command — system-level operation" }
    }
    return { level: "HIGH", reason: "Shell command execution" }
  }

  if (/\b(write|edit|save|upload|deploy|publish)\b/i.test(name)) {
    return { level: "HIGH", reason: "File write operation" }
  }

  if (/\b(install|uninstall|npm|pip|cargo|gem|apt|brew)\b/i.test(name)) {
    return { level: "HIGH", reason: "Package manager operation" }
  }

  if (/\b(git)\b/.test(name) && /\b(push|force|hard\s*reset|rebase)\b/i.test(payload)) {
    return { level: "HIGH", reason: "Git destructive operation" }
  }

  if (/\b(http|fetch|curl|wget|api|request|download)\b/i.test(name)) {
    return { level: "MEDIUM", reason: "Network request" }
  }

  if (/\b(read|open|list|ls|dir|cat|view|search|find|grep|glob)\b/i.test(name)) {
    return { level: "LOW", reason: "Read-only operation" }
  }

  return { level: "MEDIUM", reason: "Tool invocation" }
}

function extractPreview(toolName: string, payload: string): string {
  if (/\b(bash|shell|exec|cmd|powershell|terminal)\b/i.test(toolName)) {
    return payload.slice(0, 200)
  }
  if (/\b(write|edit|save|create)\b/i.test(toolName)) {
    const match = payload.match(/(?:file_path|path|file)["\s:=]+([^\s",}]+)/i)
    if (match) return "File: " + match[1] + "\n" + payload.slice(0, 160)
  }
  return payload.length > 200 ? payload.slice(0, 200) + "..." : payload
}

export function ApprovalModal(props: Props) {
  const { theme } = useTheme()
  const [selected, setSelected] = createSignal(0)
  const [countdown, setCountdown] = createSignal(COUNTDOWN_S)
  const [confirmBuffer, setConfirmBuffer] = createSignal("")

  const { level, reason } = classifyRisk(props.toolName, props.payload)
  const preview = extractPreview(props.toolName, props.payload)
  const isCritical = level === "CRITICAL"

  // Countdown timer
  const interval = setInterval(() => {
    setCountdown((prev) => {
      if (prev <= 1) { clearInterval(interval); return 0 }
      return prev - 1
    })
  }, 1000)
  onCleanup(() => clearInterval(interval))

  // Auto-approve on countdown expiry — BUT never for CRITICAL operations.
  // For CRITICAL the user must explicitly press Enter / 1 (or "y") to allow.
  createEffect(() => {
    if (countdown() === 0 && !isCritical) props.onAllow()
  })

  const countdownUrgent = countdown() <= 5

  const riskColor =
    level === "CRITICAL" ? theme.error
    : level === "HIGH" ? theme.warning
    : level === "MEDIUM" ? theme.warning
    : theme.info

  const options = [
    { label: "Allow — execute this command", hint: "Press Enter or y to confirm", action: props.onAllow },
    { label: "Deny — cancel this operation", hint: "Press Esc or n to cancel", action: props.onDeny },
    { label: "Allow always this session", hint: "Don't ask again for " + props.toolName, action: props.onAllowSession },
    { label: "Auto mode — allow all hooks", hint: "All future hooks will be auto-approved", action: props.onAutoMode },
  ]

  const execSelected = () => options[selected()].action()

  useKeyboard((evt: any) => {
    const key = evt || {}
    const keyName = key.name ?? ""
    const input = key.sequence ?? ""

    if (keyName === "escape") { props.onDeny(); return }
    if (keyName === "up") { setSelected((p) => (p === 0 ? options.length - 1 : p - 1)); return }
    if (keyName === "down") { setSelected((p) => (p === options.length - 1 ? 0 : p + 1)); return }
    // CRITICAL: require typed "yes" confirmation before allowing
    if (isCritical && (keyName === "enter" || keyName === "return" || input === "y" || input === "Y" || input === "1")) {
      if (confirmBuffer().toLowerCase() === "yes") props.onAllow()
      else props.onDeny()
      return
    }
    if (keyName === "enter" || keyName === "return") { execSelected(); return }
    if (input === "1") { props.onAllow(); return }
    if (input === "2") { props.onDeny(); return }
    if (input === "3") { props.onAllowSession(); return }
    if (input === "4") { props.onAutoMode(); return }
    if (input === "y" || input === "Y") { props.onAllow(); return }
    if (input === "n" || input === "N") { props.onDeny(); return }
    // Buffer printable chars for CRITICAL typed confirm
    if (isCritical && input && input.length === 1) {
      setConfirmBuffer((p) => (p + input).slice(-8))
    } else if (isCritical && keyName === "backspace") {
      setConfirmBuffer((p) => p.slice(0, -1))
    }
  })

  return (
    <box flexDirection="column" marginTop={1}>
      {/* Title row */}
      <box>
        <text fg={isCritical ? theme.error : theme.warning} attributes={1}>
          Permission Required
        </text>
        <text fg={theme.textMuted}> · {props.toolName}</text>
        {props.queuePosition && props.queuePosition.total > 1 && (
          <text fg={theme.textMuted}>({props.queuePosition.current}/{props.queuePosition.total})</text>
        )}
        <text fg={countdownUrgent ? theme.error : theme.textMuted}>
          {" "}Auto {countdown()}s
        </text>
      </box>

      {/* Risk badge + reason */}
      <box>
        <text bg={riskColor} fg={theme.background} attributes={1}> {level} </text>
        <text fg={theme.textMuted}> — {reason}</text>
      </box>

      {/* Preview */}
      {preview && (
        <box flexDirection="column" marginTop={1}>
          <box marginLeft={2}>
            <text>{preview}</text>
          </box>
        </box>
      )}

      {/* Critical warning + typed-confirm prompt */}
      {isCritical && (
        <box flexDirection="column" marginTop={1}>
          <text fg={theme.error}>This may cause irreversible changes — verify carefully</text>
          <text fg={theme.textMuted}>
            Type "yes" then press Enter to allow. Anything else denies.
          </text>
          <box marginTop={1}>
            <text fg={theme.textMuted}>Confirm: </text>
            <text fg={theme.warning}>{confirmBuffer() || " "}</text>
          </box>
        </box>
      )}

      {/* Options */}
      <box flexDirection="column" marginTop={1}>
        {options.map((opt, i) => (
          <box key={i} flexDirection="column" paddingX={1}>
            <box>
              <text fg={selected() === i ? theme.primary : theme.textMuted}>
                {selected() === i ? ">" : " "} {i + 1}. {opt.label}
              </text>
            </box>
            <box marginLeft={4}>
              <text fg={theme.textMuted}>{opt.hint}</text>
            </box>
          </box>
        ))}
      </box>

      {/* Footer hints */}
      <box marginTop={1}>
        <text fg={theme.textMuted}>
          1/2/3/4 to select · ↑↓ to navigate · Enter to confirm · Esc to deny
        </text>
      </box>
    </box>
  )
}

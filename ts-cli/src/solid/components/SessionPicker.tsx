/**
 * SessionPicker — session history list with search, resume, delete.
 * Ported from Ink/React to Solid/OpenTUI.
 */
import { createMemo, createSignal } from "solid-js"
import { useTheme } from "../context/theme"
import { useKeyboard } from "@opentui/solid"

export interface SessionInfo {
  id: string
  title: string
  createdAt: string
  updatedAt?: string
  messageCount: number
}

interface Props {
  sessions: SessionInfo[]
  onSelect: (session: SessionInfo | null) => void
  onDelete?: (sessionId: string) => void
}

const MAX_SHOW = 10

export function SessionPicker(props: Props) {
  const { theme } = useTheme()
  const [selected, setSelected] = createSignal(0)
  const [scrollOffset, setScrollOffset] = createSignal(0)
  const [filter, setFilter] = createSignal("")

  const visible = createMemo(() => {
    const f = filter().toLowerCase()
    if (!f) return props.sessions
    return props.sessions.filter((s) =>
      s.title.toLowerCase().includes(f) || s.id.toLowerCase().includes(f),
    )
  })

  const sliced = createMemo(() => {
    const so = scrollOffset()
    return visible().slice(so, so + MAX_SHOW)
  })

  useKeyboard((evt: any) => {
    const key = evt || {}
    const keyName = key.name ?? ""
    const input = key.sequence ?? ""

    if (keyName === "escape") { props.onSelect(null); return }
    if (keyName === "down") { setSelected((p) => Math.min(p + 1, visible().length - 1)); return }
    if (keyName === "up") { setSelected((p) => Math.max(p - 1, 0)); return }
    if (keyName === "pagedown") { setSelected((p) => Math.min(p + MAX_SHOW, visible().length - 1)); return }
    if (keyName === "pageup") { setSelected((p) => Math.max(p - MAX_SHOW, 0)); return }
    if (keyName === "home") { setSelected(0); return }
    if (keyName === "end") { setSelected(visible().length - 1); return }
    if (keyName === "enter" || keyName === "return") {
      const v = visible()
      const s = selected()
      if (v.length > 0 && s >= 0 && s < v.length) {
        props.onSelect(v[s])
      }
      return
    }
    if ((keyName === "delete" || keyName === "backspace") && !key.ctrl) {
      setFilter((p) => p.slice(0, -1))
      return
    }
    if (keyName === "delete" && props.onDelete && visible().length > 0) {
      const s = selected()
      if (s >= 0 && s < visible().length) {
        props.onDelete(visible()[s].id)
      }
      return
    }
    // Filter by typing
    if (!key.ctrl && !key.meta && input && input.length === 1 && keyName !== "enter") {
      setFilter((p) => p + input)
      return
    }
  })

  const formatDate = (iso: string) => {
    try {
      const d = new Date(iso)
      return d.toLocaleDateString() + " " + d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
    } catch { return iso }
  }

  return (
    <box flexDirection="column" paddingX={1} width={66}>
      <box>
        <text fg={theme.primary} attributes={1}>Session History</text>
        <text fg={theme.textMuted}>  type to filter / Enter resume / Del delete / Esc close</text>
      </box>
      {filter() && (
        <box paddingLeft={1}>
          <text fg={theme.textMuted}>Filter: </text>
          <text fg={theme.warning}>{filter()}</text>
        </box>
      )}
      {sliced().length === 0 && (
        <box paddingLeft={2}><text fg={theme.textMuted}>No sessions found.</text></box>
      )}
      {sliced().map((s, i) => {
        const idx = scrollOffset() + i
        return (
          <box key={s.id} paddingLeft={1}>
            <text fg={idx === selected() ? theme.primary : undefined} attributes={idx === selected() ? 1 : 0}>
              {idx === selected() ? "> " : "  "}
            </text>
            <text fg={theme.success}>{s.id}</text>
            <text fg={theme.textMuted}>  {s.title.slice(0, 30)}</text>
            <text fg={theme.textMuted}>  [{s.messageCount} msgs]</text>
            <text fg={theme.textMuted}>  {formatDate(s.updatedAt || s.createdAt)}</text>
          </box>
        )
      })}
      {visible().length > MAX_SHOW && (
        <box>
          <text fg={theme.textMuted}>
            {"  "}{scrollOffset() + 1}-{Math.min(scrollOffset() + MAX_SHOW, visible().length)} / {visible().length}
          </text>
        </box>
      )}
    </box>
  )
}

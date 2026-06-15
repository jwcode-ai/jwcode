/**
 * ToolPalette — ! system tool selection popup with fuzzy matching.
 * Similar to FilePalette and CommandPalette.
 */
import { createMemo, createSignal } from "solid-js"
import { useTheme } from "../context/theme"
import { useKeyboard } from "@opentui/solid"
import { fuzzyMatch } from "../../fuzzyMatch"

interface Props {
  tools: string[]
  filter: string
  onSelect: (tool: string | null) => void
}

const MAX_RESULTS = 10

export function ToolPalette(props: Props) {
  const { theme } = useTheme()
  const [selected, setSelected] = createSignal(0)
  const [scrollOffset, setScrollOffset] = createSignal(0)

  const visible = createMemo(() => {
    const f = props.filter.trim().toLowerCase()
    if (!f) return props.tools.slice(0, MAX_RESULTS)

    const results: Array<{ name: string; score: number }> = []
    for (const tool of props.tools) {
      const match = fuzzyMatch(f, tool)
      if (match) results.push({ name: tool, score: match.score })
    }
    results.sort((a, b) => a.score - b.score)
    return results.slice(0, MAX_RESULTS)
  })

  const sliced = createMemo(() => {
    const so = scrollOffset()
    return visible().slice(so, so + MAX_RESULTS)
  })

  useKeyboard((evt: any) => {
    const key = evt || {}
    const keyName = key.name ?? ""

    if (keyName === "escape") { props.onSelect(null); return }
    if (keyName === "down") { setSelected((p) => Math.min(p + 1, visible().length - 1)); return }
    if (keyName === "up") { setSelected((p) => Math.max(p - 1, 0)); return }
    if (keyName === "pagedown") { setSelected((p) => Math.min(p + MAX_RESULTS, visible().length - 1)); return }
    if (keyName === "pageup") { setSelected((p) => Math.max(p - MAX_RESULTS, 0)); return }
    if (keyName === "home") { setSelected(0); return }
    if (keyName === "end") { setSelected(visible().length - 1); return }
    if (keyName === "enter" || keyName === "return") {
      const v = visible()
      const s = selected()
      if (v.length > 0 && s >= 0 && s < v.length) {
        props.onSelect(v[s].name)
      }
      return
    }
  })

  return (
    <box flexDirection="column" paddingX={1}>
      <box>
        <text fg={theme.warning} attributes={1}>! 系统工具</text>
        <text fg={theme.textMuted}>  ↑↓选择 · PgUp/PgDn翻页 · Enter插入 · Esc取消</text>
      </box>
      {visible().length === 0 ? (
        <box paddingLeft={1}><text fg={theme.textMuted}>  未找到匹配工具</text></box>
      ) : (
        sliced().map((tool, i) => {
          const idx = scrollOffset() + i
          const isSelected = idx === selected()
          return (
            <box key={tool.name} paddingLeft={1}>
              <text fg={isSelected ? theme.warning : undefined} attributes={isSelected ? 1 : 0}>
                {isSelected ? "> " : "  "}{tool.name}
              </text>
            </box>
          )
        })
      )}
      {props.tools.length > MAX_RESULTS && (
        <box>
          <text fg={theme.textMuted}>
            {"  "}{scrollOffset() + 1}-{Math.min(scrollOffset() + MAX_RESULTS, visible().length)} / {props.tools.length}
          </text>
        </box>
      )}
    </box>
  )
}

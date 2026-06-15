/**
 * CommandPalette — / filterable popup with three-bucket filtering, category tags.
 * Ported from Ink/React to Solid/OpenTUI.
 */
import { createMemo, createSignal } from "solid-js"
import { useTheme } from "../context/theme"
import { useKeyboard } from "@opentui/solid"
import { ALL_COMMANDS, type CmdEntry } from "../../commands"

interface Props {
  filter: string
  onSelect: (cmd: string | null) => void
}

const MAX_SHOW = 10

export function CommandPalette(props: Props) {
  const { theme } = useTheme()
  const [selected, setSelected] = createSignal(0)
  const [scrollOffset, setScrollOffset] = createSignal(0)

  const visible = createMemo(() => {
    const f = props.filter.replace(/^\//, "").toLowerCase()
    const source = f ? ALL_COMMANDS : ALL_COMMANDS.filter((c) => !c.isAlias)
    if (!f) return source

    const exact: CmdEntry[] = []
    const prefix: CmdEntry[] = []
    const includes: CmdEntry[] = []

    for (const c of source) {
      const cmdLower = c.cmd.toLowerCase()
      if (cmdLower === "/" + f) exact.push(c)
      else if (cmdLower.startsWith("/" + f)) prefix.push(c)
      else if (cmdLower.includes(f) || c.desc.toLowerCase().includes(f)) includes.push(c)
    }

    return [...exact, ...prefix, ...includes]
  })

  const sliced = createMemo(() => {
    const so = scrollOffset()
    return visible().slice(so, so + MAX_SHOW)
  })

  useKeyboard((evt: any) => {
    const key = evt || {}
    const keyName = key.name ?? ""

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
        props.onSelect(v[s].cmd)
      }
      return
    }
  })

  return (
    <box flexDirection="column" paddingX={1}>
      <box>
        <text fg={theme.primary} attributes={1}>命令列表</text>
        <text fg={theme.textMuted}>  ↑↓选择 / PgUp/PgDn翻页 / 回车确认 / Esc取消</text>
      </box>
      {sliced().length === 0 ? (
        <box paddingLeft={1}><text fg={theme.textMuted}>  无匹配命令</text></box>
      ) : (
        sliced().map((cmd, i) => {
          const idx = scrollOffset() + i
          const isSelected = idx === selected()
          const catTag = cmd.category ? `[${cmd.category}]` : ""
          const aliasSuffix = cmd.isAlias ? " (alias)" : ""
          const selectMarker = isSelected ? "> " : "  "
          const line = selectMarker + cmd.cmd + "  " + cmd.desc + aliasSuffix + (catTag ? "  " + catTag : "")
          return (
            <box key={cmd.cmd} paddingLeft={1}>
              <text fg={isSelected ? theme.primary : undefined} attributes={isSelected ? 1 : 0}>
                {line}
              </text>
            </box>
          )
        })
      )}
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

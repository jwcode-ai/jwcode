/**
 * FilePalette — @ file reference popup with fuzzy matching.
 * Ported from Ink/React to Solid/OpenTUI.
 */
import { createMemo, createSignal } from "solid-js"
import { useTheme } from "../context/theme"
import { useKeyboard } from "@opentui/solid"
import { fuzzyMatch, highlightSpans } from "../../fuzzyMatch"

interface Props {
  query: string
  files: string[]
  onSelect: (path: string | null) => void
}

const MAX_RESULTS = 8

function basename(path: string): string {
  const parts = path.replace(/\\/g, "/").split("/")
  return parts[parts.length - 1] || path
}

function dirname(path: string): string {
  const parts = path.replace(/\\/g, "/").split("/")
  parts.pop()
  return parts.join("/") || "."
}

function dirDepth(path: string): number {
  const dir = dirname(path)
  return dir === "." ? 0 : dir.split("/").length
}

function isLikelyDirectory(path: string): boolean {
  // Heuristic: paths without a file extension in the last component are directories
  const name = basename(path)
  return !name.includes(".") || name.endsWith("/")
}

export function FilePalette(props: Props) {
  const { theme } = useTheme()
  const [selected, setSelected] = createSignal(0)
  const [scrollOffset, setScrollOffset] = createSignal(0)

  const visible = createMemo(() => {
    const q = props.query
    if (!q.trim()) {
      return props.files.slice(0, MAX_RESULTS).map((f) => ({ path: f, score: 0, indices: [] as number[] }))
    }

    const results: Array<{ path: string; score: number; indices: number[] }> = []
    for (const filePath of props.files) {
      const match = fuzzyMatch(q, basename(filePath))
      const fullMatch = match ? null : fuzzyMatch(q, filePath)
      const result = match || fullMatch
      if (result) {
        results.push({ path: filePath, score: result.score, indices: result.indices })
      }
    }
    results.sort((a, b) => {
      // Files before directories
      const aIsDir = isLikelyDirectory(a.path)
      const bIsDir = isLikelyDirectory(b.path)
      if (aIsDir !== bIsDir) return aIsDir ? 1 : -1
      // Then by directory depth (root files first)
      const da = dirDepth(a.path)
      const db = dirDepth(b.path)
      if (da !== db) return da - db
      // Then by fuzzy match score
      return a.score - b.score
    })
    return results.slice(0, MAX_RESULTS)
  })

  const maxShow = 8

  const sliced = createMemo(() => {
    const so = scrollOffset()
    return visible().slice(so, so + maxShow)
  })

  useKeyboard((evt: any) => {
    const key = evt || {}
    const keyName = key.name ?? ""

    if (keyName === "escape") { props.onSelect(null); return }
    if (keyName === "down") { setSelected((p) => Math.min(p + 1, visible().length - 1)); return }
    if (keyName === "up") { setSelected((p) => Math.max(p - 1, 0)); return }
    if (keyName === "pagedown") { setSelected((p) => Math.min(p + maxShow, visible().length - 1)); return }
    if (keyName === "pageup") { setSelected((p) => Math.max(p - maxShow, 0)); return }
    if (keyName === "home") { setSelected(0); return }
    if (keyName === "end") { setSelected(visible().length - 1); return }
    if (keyName === "enter" || keyName === "return") {
      const v = visible()
      const s = selected()
      if (v.length > 0 && s >= 0 && s < v.length) {
        props.onSelect(v[s].path)
      }
      return
    }
  })

  return (
    <box flexDirection="column" paddingX={1} width={64}>
      <box>
        <text fg={theme.warning} attributes={1}>@ 搜索: </text>
        <text fg={theme.success}>{props.query || "(输入文件名)"}</text>
        <text fg={theme.textMuted}>  ↑↓ 选择 · Enter 插入 · Esc 关闭</text>
      </box>
      {visible().length === 0 ? (
        <box paddingLeft={1}><text fg={theme.textMuted}>  未找到匹配文件</text></box>
      ) : (
        sliced().map((entry, i) => {
          const idx = scrollOffset() + i
          const path = entry.path
          const name = basename(path)
          const dir = dirname(path)
          const nameSpans = highlightSpans(name, entry.indices)
          // One-line format: "> filename /directory/path"
          const maxLen = 56
          const dirStr = dir === "." ? "" : `/${dir}`
          const fullStr = name + dirStr
          const showDir = fullStr.length > maxLen
            ? dirStr.slice(0, maxLen - name.length - 3) + ".."
            : dirStr
          return (
            <box key={path} paddingLeft={1}>
              <text fg={idx === selected() ? theme.warning : undefined} attributes={idx === selected() ? 1 : 0}>
                {idx === selected() ? "> " : "  "}
              </text>
              <text fg={theme.success}>
                {nameSpans.map((span, si) =>
                  span.highlighted ? "*" + span.text + "*" : span.text
                )}
              </text>
              <text fg={theme.textMuted}>{showDir}</text>
            </box>
          )
        })
      )}
    </box>
  )
}

/**
 * DiffDisplay — unified diff with line numbers, hunk tracking, theme palettes.
 * Ported from Ink/React to Solid/OpenTUI.
 *
 * Key changes:
 *  - Solid signals/memos replace React useState/useMemo
 *  - OpenTUI <box>/<text> replaces Ink <Box>/<Text>
 *  - Theme colors from Solid context instead of hex strings
 *  - RGBA color system from @opentui/core
 */
import { createMemo, createSignal } from "solid-js"
import { useTheme } from "../context/theme"

// ---- Types ----

type DiffLineType = "fileHeader" | "hunkHeader" | "addition" | "deletion" | "placeholder" | "context"

interface DiffLine {
  type: DiffLineType
  text: string
  raw: string
  oldLn: number | null
  newLn: number | null
  hunkIndex: number
}

interface HunkInfo {
  hunkIndex: number
  oldStart: number
  newStart: number
  lines: DiffLine[]
}

// ---- Constants ----

const RE_HUNK_HEADER = /^@@ -(\d+),?(\d*) \+(\d+),?(\d*) @@/
const COLLAPSE_THRESHOLD = 30
const HIGHLIGHT_MAX_LINES = 1000
const HIGHLIGHT_MAX_BYTES = 50000

// ---- Parser ----

function parseDiff(text: string): DiffLine[] {
  const lines = text.split("\n")
  const result: DiffLine[] = []
  let hunkIndex = -1
  let oldLn: number | null = null
  let newLn: number | null = null

  for (const raw of lines) {
    let type: DiffLineType = "context"

    const hunkMatch = raw.match(RE_HUNK_HEADER)
    if (hunkMatch) {
      type = "hunkHeader"
      hunkIndex++
      oldLn = parseInt(hunkMatch[1], 10)
      newLn = parseInt(hunkMatch[3], 10)
    } else if (/^(---|\+\+\+) /.test(raw)) {
      type = "fileHeader"
      hunkIndex = -1
      oldLn = null
      newLn = null
    } else if (raw.startsWith("+")) {
      type = "addition"
    } else if (raw.startsWith("-")) {
      type = "deletion"
    } else if (/^\\(?!.*\\$)/.test(raw)) {
      type = "placeholder"
    }

    let text = raw
    if (type === "addition" || type === "deletion") {
      text = raw.slice(1)
    }

    let lineOldLn: number | null = null
    let lineNewLn: number | null = null

    if (hunkIndex >= 0) {
      switch (type) {
        case "context":
          lineOldLn = oldLn
          lineNewLn = newLn
          if (oldLn !== null) oldLn++
          if (newLn !== null) newLn++
          break
        case "addition":
          lineNewLn = newLn
          if (newLn !== null) newLn++
          break
        case "deletion":
          lineOldLn = oldLn
          if (oldLn !== null) oldLn++
          break
      }
    }

    result.push({ type, text, raw, oldLn: lineOldLn, newLn: lineNewLn, hunkIndex: hunkIndex >= 0 ? hunkIndex : -1 })
  }

  return result
}

function extractHunks(lines: DiffLine[]): HunkInfo[] {
  const hunks: HunkInfo[] = []
  let current: HunkInfo | null = null

  for (const line of lines) {
    if (line.type === "hunkHeader") {
      const match = line.raw.match(RE_HUNK_HEADER)
      if (current) hunks.push(current)
      current = {
        hunkIndex: line.hunkIndex,
        oldStart: match ? parseInt(match[1], 10) : 0,
        newStart: match ? parseInt(match[3], 10) : 0,
        lines: [line],
      }
    } else if (current && line.hunkIndex >= 0) {
      current.lines.push(line)
    }
  }
  if (current) hunks.push(current)
  return hunks
}

// ---- Utilities ----

function countByType(lines: DiffLine[], type: DiffLineType): number {
  return lines.filter((l) => l.type === type).length
}

function computeGutterWidth(lines: DiffLine[]): number {
  let maxOld = 0
  let maxNew = 0
  for (const line of lines) {
    if (line.oldLn !== null && line.oldLn > maxOld) maxOld = line.oldLn
    if (line.newLn !== null && line.newLn > maxNew) maxNew = line.newLn
  }
  return Math.max(String(maxOld).length, String(maxNew).length, 3)
}

function shouldSkipHighlight(lines: DiffLine[]): boolean {
  const totalBytes = lines.reduce((s, l) => s + Buffer.byteLength(l.raw, "utf-8"), 0)
  return totalBytes > HIGHLIGHT_MAX_BYTES || lines.length > HIGHLIGHT_MAX_LINES
}

function formatLn(n: number | null, width: number): string {
  if (n === null) return "".padStart(width)
  return String(n).padStart(width)
}

function truncateLine(text: string, max: number): string {
  if (text.length <= max) return text
  return text.slice(0, max - 3) + "..."
}

// ---- Props ----

interface Props {
  content: string
  terminalCols?: number
  startCollapsed?: boolean
}

export function DiffDisplay(props: Props) {
  const { theme } = useTheme()
  const terminalCols = () => props.terminalCols ?? 120

  const lines = createMemo(() => parseDiff(props.content))
  const totalLines = () => lines().length
  const addCount = createMemo(() => countByType(lines(), "addition"))
  const delCount = createMemo(() => countByType(lines(), "deletion"))
  const shouldStartCollapsed = () => props.startCollapsed ?? (totalLines() > COLLAPSE_THRESHOLD)

  const [collapsed, setCollapsed] = createSignal(shouldStartCollapsed())
  const toggle = () => setCollapsed((c) => !c)

  const maxLineWidth = () => terminalCols() - 6
  const gutterWidth = createMemo(() => computeGutterWidth(lines()))
  const hunks = createMemo(() => extractHunks(lines()))

  const fileNames = createMemo(() => {
    const names: string[] = []
    for (const line of lines()) {
      if (line.type === "fileHeader" && line.raw.startsWith("+++")) {
        const name = line.raw.replace(/^\+\+\+ (a|b)\//, "").trim()
        if (name && name !== "/dev/null") names.push(name)
      }
    }
    return [...new Set(names)]
  })

  if (totalLines() === 0) return null

  const maxVisible = COLLAPSE_THRESHOLD
  const visibleLines = () => (collapsed() ? lines().slice(0, maxVisible) : lines())
  const hiddenCount = totalLines() - maxVisible

  const summaryText = fileNames().length > 0
    ? fileNames().join(", ") + `  →  +${addCount()}/-${delCount()}`
    : `Diff  →  +${addCount()}/-${delCount()}`

  const gutterW = gutterWidth()

  return (
    <box flexDirection="column" paddingLeft={1}>
      {/* DiffFrame — top border */}
      <box>
        <text fg={theme.diffHunkHeader}>{"┌─ "}</text>
        <text fg={theme.primary} attributes={1}>{summaryText}</text>
      </box>

      {/* Visible diff lines */}
      <box flexDirection="column">
        {visibleLines().map((line, i) => (
          <RenderDiffLine
            line={line}
            maxWidth={maxLineWidth()}
            gutterWidth={gutterW}
            theme={theme}
          />
        ))}
      </box>

      {/* Collapse indicator */}
      {collapsed() && hiddenCount > 0 && (
        <box>
          <text fg={theme.info}>{`  … ${hiddenCount} more lines `}</text>
          <text fg={theme.diffHunkHeader}>[click to expand]</text>
        </box>
      )}

      {/* DiffFrame — bottom border */}
      <box>
        <text fg={theme.diffHunkHeader}>{"└─ "}</text>
        <text fg={theme.textMuted}>{addCount() > 0 ? `+${addCount()} ` : ""}{delCount() > 0 ? `-${delCount()} ` : ""}{fileNames().length > 0 ? fileNames().join(", ") : ""}</text>
      </box>
    </box>
  )
}

// ---- Internal line renderer ----

function RenderDiffLine(props: {
  line: DiffLine
  maxWidth: number
  gutterWidth: number
  theme: any
}) {
  const { line, maxWidth, gutterWidth, theme } = props
  const display = truncateLine(line.text, maxWidth)
  const gutter = `${formatLn(line.oldLn, gutterWidth)} ${formatLn(line.newLn, gutterWidth)}`
  const gutterWidthWithSpace = gutterWidth * 2 + 1

  switch (line.type) {
    case "fileHeader":
      return (
        <box>
          <text fg={theme.diffHunkHeader}>
            {" ".repeat(gutterWidthWithSpace + 1)}{line.raw.slice(0, maxWidth)}
          </text>
        </box>
      )

    case "hunkHeader": {
      const displayHunk = line.raw.length > maxWidth
        ? line.raw.slice(0, maxWidth - 3) + "..."
        : line.raw
      return (
        <box>
          <text fg={theme.diffHunkHeader} attributes={1}>
            {gutter} |
          </text>
          <text fg={theme.textMuted}>{displayHunk}</text>
        </box>
      )
    }

    case "addition":
      return (
        <box>
          <text fg={theme.diffAdded} bg={theme.diffAddedBg}>
            {gutter} +{display}
          </text>
        </box>
      )

    case "deletion":
      return (
        <box>
          <text fg={theme.diffRemoved} bg={theme.diffRemovedBg}>
            {gutter} -{display}
          </text>
        </box>
      )

    case "placeholder":
      return (
        <box>
          <text fg={theme.textMuted}>
            {" ".repeat(gutterWidthWithSpace + 1)}{line.raw.slice(0, maxWidth)}
          </text>
        </box>
      )

    case "context":
    default:
      return (
        <box>
          <text fg={theme.textMuted}>
            {gutter} |{display}
          </text>
        </box>
      )
  }
}

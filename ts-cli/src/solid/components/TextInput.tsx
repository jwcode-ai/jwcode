/**
 * TextInput — grapheme-aware input with word movement, kill buffer, paste burst.
 * Ported from Ink/React to Solid/OpenTUI.
 *
 * Key changes:
 *  - Solid signals replace React useState/useRef
 *  - OpenTUI useKeyboard replaces Ink useInput
 *  - No useEffect/useRef — module-level kill buffer, signal-based cursor
 *  - OpenTUI <box>/<text> replaces Ink <Box>/<Text>
 */
import { createSignal, createMemo, createEffect, onCleanup, onMount, batch } from "solid-js"
import { useTheme } from "../context/theme"
import { useBlinkChar } from "../util/animation"
import { useKeyboard, useRenderer, useTerminalDimensions } from "@opentui/solid"
import { pasteSummary } from "../../pasteBuffer"
import { ALL_COMMANDS } from "../../commands"
import {
  graphemeOffsets,
  graphemeCount,
  graphemeToCursor,
  wordStartLeft,
  wordEndRight,
  killWordBackward,
} from "../../textInputGrapheme"

// ---- Kill buffer (module-level, survives submit/clear) ----

let _killBuffer: string | null = null
const enum KillKind { Characterwise, Linewise }

function killSet(text: string, _kind: KillKind): void {
  _killBuffer = text
}

function killGet(): string | null {
  return _killBuffer
}

// ---- Paste burst detection ----

interface PasteBurstState {
  accumulating: boolean
  buf: string
  lastCharTime: number
  consecutivePlainChars: number
  burstWindowUntil: number
}

function createPasteBurstState(): PasteBurstState {
  return { accumulating: false, buf: "", lastCharTime: 0, consecutivePlainChars: 0, burstWindowUntil: 0 }
}

const PASTE_BURST_MIN_CHARS = 3
const PASTE_BURST_CHAR_INTERVAL = 8
const PASTE_BURST_IDLE_TIMEOUT = 8

interface CharDecision {
  action: "accept" | "buffer_start" | "buffer_continue" | "buffer_flush"
}

function decideChar(state: PasteBurstState, now: number, isAscii: boolean): CharDecision {
  const dt = now - state.lastCharTime

  if (!state.accumulating) {
    if (isAscii && dt < PASTE_BURST_CHAR_INTERVAL && state.consecutivePlainChars >= 1) {
      return { action: "buffer_start" }
    }
    return { action: "accept" }
  }

  if (dt < PASTE_BURST_IDLE_TIMEOUT) {
    return { action: "buffer_continue" }
  }

  return { action: "buffer_flush" }
}

// ---- Token estimation (CJK heuristic) ----

/** CJK ≈ 1.5 chars/token, English ≈ 4 chars/token. Exported for unit tests. */
export function estimateTokens(text: string): number {
  let cjk = 0
  let other = 0
  for (const ch of text) {
    if (/[一-鿿㐀-䶿豈-﫿]/.test(ch)) cjk++
    else other++
  }
  return Math.ceil(cjk / 1.5 + other / 4)
}

/** Visual column width of text (CJK = 2, ASCII/others = 1). */
export function visualWidth(text: string): number {
  let w = 0
  for (const ch of text) {
    const code = ch.charCodeAt(0)
    if (
      (code >= 0x2E80 && code <= 0x9FFF) ||   // CJK Radicals, Kangxi, Ideographs
      (code >= 0xAC00 && code <= 0xD7AF) ||   // Hangul Syllables
      (code >= 0xF900 && code <= 0xFAFF) ||   // CJK Compatibility Ideographs
      (code >= 0xFF01 && code <= 0xFF60) ||   // Fullwidth Forms
      (code >= 0xFFE0 && code <= 0xFFE6) ||   // Fullwidth Signs
      (code >= 0x20000 && code <= 0x2FA1F)    // CJK Ext B/C/D/E/F
    ) {
      w += 2
    } else {
      w += 1
    }
  }
  return w
}

// Prompt prefix visual width (border + padding + "> ")
const PROMPT_WIDTH = 4

// ---- Cursor utilities ----

interface AtomicRange {
  start: number
  end: number
}

function clampToAtomic(gIdx: number, atoms: AtomicRange[]): number {
  for (const a of atoms) {
    if (gIdx > a.start && gIdx < a.end) return a.start
  }
  return gIdx
}

// ---- Props ----

interface Props {
  value: string
  onChange: (value: string) => void
  onSubmit: (value: string) => void
  placeholder?: string
  disabled?: boolean
  /** Number of terminal rows reserved below text input (border + status lines) */
  bottomReserved?: number
}

export function TextInput(props: Props) {
  const { theme } = useTheme()

  // Cursor stored as grapheme cluster index
  const [cursorGrapheme, setCursorGrapheme] = createSignal(graphemeCount(props.value))
  const [tick, setTick] = createSignal(0)

  // Mutable refs for latest props (avoid stale closures in keyboard handler)
  let valueRef = props.value
  let onChangeRef = props.onChange
  let onSubmitRef = props.onSubmit
  let disabledRef = props.disabled ?? false

  // Keep refs in sync
  createEffect(() => { valueRef = props.value; onChangeRef = props.onChange; onSubmitRef = props.onSubmit; disabledRef = props.disabled ?? false })

  // Track whether edit came from internal keyboard handling vs external (history, paste, etc.)
  let _internalChange = false
  let prevValue = props.value
  createEffect(() => {
    const v = props.value
    if (v !== prevValue) {
      if (!_internalChange) {
        // External change (history navigation, etc.) - reset cursor to end
        setCursorGrapheme(graphemeCount(v))
      }
      _internalChange = false
      prevValue = v
    }
  })

  const pasteBurst = createPasteBurstState()
  const atoms: AtomicRange[] = []

  // Clamp cursor to valid range
  const totalGraphemes = createMemo(() => graphemeCount(props.value))
  createMemo(() => {
    const total = totalGraphemes()
    const cur = cursorGrapheme()
    if (cur > total) setCursorGrapheme(total)
  })

  // ---- Editing primitives ----

  function insertAtCursor(text: string) {
    const pos = graphemeToCursor(valueRef, cursorGrapheme())
    const cur = valueRef
    const next = cur.slice(0, pos) + text + cur.slice(pos)
    batch(() => {
      onChangeRef(next)
      _internalChange = true
      setCursorGrapheme(graphemeCount(next))
    })
    setTick((t) => t + 1)
  }

  function deleteBackward() {
    if (cursorGrapheme() <= 0) return
    const offsets = graphemeOffsets(valueRef)
    const gPos = cursorGrapheme()
    const startCU = offsets[gPos - 1]
    const endCU = offsets[gPos]
    const cur = valueRef
    batch(() => {
      onChangeRef(cur.slice(0, startCU) + cur.slice(endCU))
      _internalChange = true
      setCursorGrapheme(gPos - 1)
    })
    setTick((t) => t + 1)
  }

  function deleteForward() {
    const total = graphemeCount(valueRef)
    if (cursorGrapheme() >= total) return
    const offsets = graphemeOffsets(valueRef)
    const gPos = cursorGrapheme()
    const startCU = offsets[gPos]
    const endCU = offsets[gPos + 1]
    const cur = valueRef
    batch(() => {
      onChangeRef(cur.slice(0, startCU) + cur.slice(endCU))
      _internalChange = true
    })
    setTick((t) => t + 1)
  }

  // ---- Keyboard handling ----

  useKeyboard((evt: any) => {
    if (disabledRef) return

    const key = evt || {}
    const input = key.name ?? key.sequence ?? ""
    const ctrl = !!key.ctrl
    const meta = !!key.meta
    const now = Date.now()
    const isAscii = input.length === 1 && input.charCodeAt(0) < 128

    // Bracketed paste handling
    const PASTE_START = "[200~"
    const PASTE_END = "[201~"
    if (typeof input === "string" && input.includes(PASTE_START)) {
      const rest = input.split(PASTE_START).slice(1).join(PASTE_START)
      if (rest.includes(PASTE_END)) {
        const parts = rest.split(PASTE_END)
        const content = parts[0] || ""
        const { label } = pasteSummary(content)
        const pos = graphemeToCursor(valueRef, cursorGrapheme())
        batch(() => {
          onChangeRef(valueRef.slice(0, pos) + label + valueRef.slice(pos))
          _internalChange = true
          setCursorGrapheme(graphemeCount(valueRef.slice(0, pos) + label))
        })
        setTick((t) => t + 1)
      }
      return
    }
    if (typeof input === "string" && input.includes(PASTE_END)) return

    // Paste burst detection (timing-based)
    const decision = decideChar(pasteBurst, now, isAscii)
    const pb = pasteBurst

    if (decision.action === "buffer_start") {
      pb.accumulating = true
      pb.buf = input
      pb.lastCharTime = now
      return
    }

    if (decision.action === "buffer_continue") {
      pb.buf += input
      pb.lastCharTime = now
      return
    }

    if (decision.action === "buffer_flush") {
      pb.accumulating = false
      const flushed = pb.buf
      pb.buf = ""
      if (flushed.length >= PASTE_BURST_MIN_CHARS) {
        const { label } = pasteSummary(flushed)
        const pos = graphemeToCursor(valueRef, cursorGrapheme())
        batch(() => {
          onChangeRef(valueRef.slice(0, pos) + label + valueRef.slice(pos))
          _internalChange = true
          setCursorGrapheme(graphemeCount(valueRef.slice(0, pos) + label))
        })
        setTick((t) => t + 1)
        return
      }
      insertAtCursor(flushed)
      return
    }

    pb.lastCharTime = now
    pb.consecutivePlainChars = isAscii ? (pb.consecutivePlainChars || 0) + 1 : 0

    const keyName = key.name ?? ""

    // ---- Navigation ----

    // Left arrow
    if (keyName === "left" && !ctrl) {
      if (cursorGrapheme() > 0) {
        setCursorGrapheme((c) => clampToAtomic(c - 1, atoms))
        setTick((t) => t + 1)
      }
      return
    }

    // Right arrow
    if (keyName === "right" && !ctrl) {
      const total = graphemeCount(valueRef)
      if (cursorGrapheme() < total) {
        setCursorGrapheme((c) => clampToAtomic(c + 1, atoms))
        setTick((t) => t + 1)
      }
      return
    }

    // Ctrl+Left / Alt+B — word left
    if ((keyName === "left" && ctrl) || (meta && input === "b")) {
      setCursorGrapheme(wordStartLeft(valueRef, cursorGrapheme()))
      setTick((t) => t + 1)
      return
    }

    // Ctrl+Right / Alt+F — word right
    if ((keyName === "right" && ctrl) || (meta && input === "f")) {
      setCursorGrapheme(wordEndRight(valueRef, cursorGrapheme()))
      setTick((t) => t + 1)
      return
    }

    // Home / Ctrl+A
    if (keyName === "home" || (ctrl && input === "a")) {
      setCursorGrapheme(0)
      setTick((t) => t + 1)
      return
    }

    // End / Ctrl+E
    if (keyName === "end" || (ctrl && input === "e")) {
      setCursorGrapheme(graphemeCount(valueRef))
      setTick((t) => t + 1)
      return
    }

    // ---- Editing ----

    // Enter — submit
    if (keyName === "enter" || keyName === "return") {
      onSubmitRef(valueRef)
      return
    }

    // Backspace
    if (keyName === "backspace" && !ctrl) {
      deleteBackward()
      return
    }

    // Ctrl+Backspace / Ctrl+W — kill word backward
    if ((keyName === "backspace" && ctrl) || (ctrl && input === "w")) {
      const result = killWordBackward(valueRef, cursorGrapheme())
      batch(() => {
        onChangeRef(result.kept)
        _internalChange = true
        setCursorGrapheme(result.newGraphemePos)
      })
      killSet(result.killed, KillKind.Characterwise)
      setTick((t) => t + 1)
      return
    }

    // Delete
    if (keyName === "delete" || keyName === "del") {
      deleteForward()
      return
    }

    // Ctrl+K — kill to end of line
    if (ctrl && input === "k") {
      const offsets = graphemeOffsets(valueRef)
      const gPos = cursorGrapheme()
      const cutStart = offsets[gPos]
      const killed = valueRef.slice(cutStart)
      batch(() => {
        onChangeRef(valueRef.slice(0, cutStart))
        _internalChange = true
      })
      killSet(killed, KillKind.Characterwise)
      setTick((t) => t + 1)
      return
    }

    // Ctrl+U — kill to start of line
    if (ctrl && input === "u") {
      const offsets = graphemeOffsets(valueRef)
      const gPos = cursorGrapheme()
      const cutEnd = offsets[gPos]
      const killed = valueRef.slice(0, cutEnd)
      batch(() => {
        onChangeRef(valueRef.slice(cutEnd))
        setCursorGrapheme(0)
        _internalChange = true
      })
      killSet(killed, KillKind.Characterwise)
      setTick((t) => t + 1)
      return
    }

    // Ctrl+Y — yank
    if (ctrl && input === "y") {
      const buf = killGet()
      if (buf !== null) insertAtCursor(buf)
      return
    }

    // Tab / Escape — ignore (handled by App keyboard handler)
    if (keyName === "tab" || keyName === "escape") return

    // Up/Down — handled by parent (input history or palette navigation)
    if (keyName === "up" || keyName === "down") return

    // Fallback printable character insertion
    if (input && !ctrl && !meta) {
      insertAtCursor(input)
    }
  })

  // ---- Render ----

  const display = () => props.value || ""
  const showPlaceholder = () => !display() && props.placeholder
  const tokenEstimate = () => (display() ? estimateTokens(display()) : 0)
  const charCount = () => display().length
  const codeUnitCursor = () => graphemeToCursor(props.value, cursorGrapheme())
  const clampedCursor = () => Math.min(codeUnitCursor(), display().length)

  // ── IME cursor tracking (hardware cursor follows editing cursor) ──

  const renderer = useRenderer()
  const dims = useTerminalDimensions()
  const cursorRef = { col: 1, row: 1, visible: false }

  // 硬件光标隐藏，软件光标（橙色字符底色）作为主要光标指示器
  createEffect(() => {
    const text = display()
    const cuPos = codeUnitCursor()
    const before = text.slice(0, cuPos)
    const vw = PROMPT_WIDTH + visualWidth(before)
    const cols = dims().width
    if (cols <= PROMPT_WIDTH) return

    // TextInput layout (top to bottom):
    //   border top (1) + content row (1) + [hints row?] + [char count row?] + border bottom (1)
    // The content row sits at screen row = dims().height - bottomReserved + 1
    // (border bottom is the last of the reserved rows). When text wraps past
    // the prompt width, wrap rows push the cursor down.
    const hasCharInfo = hints().length > 0 || charCount() > 0

    const inputW = Math.max(1, cols - PROMPT_WIDTH * 2)
    const cursorScreenCol = (vw % inputW) + PROMPT_WIDTH + 1
    const textWrapRows = Math.floor(vw / inputW)
    const contentFirstRow = dims().height - (props.bottomReserved ?? 5) + 1
    const cursorScreenRow = contentFirstRow + textWrapRows
    void hasCharInfo // reserved for future per-row offset

    cursorRef.col = cursorScreenCol
    cursorRef.row = cursorScreenRow
    cursorRef.visible = false
  })

  onMount(() => {
    const ppFn = () => {
      renderer.setCursorPosition(cursorRef.col, cursorRef.row, cursorRef.visible)
    }
    renderer.addPostProcessFn(ppFn)
    onCleanup(() => renderer.removePostProcessFn(ppFn))
  })

  // Command highlighting
  const cmdHighlight = () => {
    const v = props.value
    const slashIdx = v.indexOf("/")
    if (slashIdx < 0) return null
    // Find end of command (space or end)
    const spaceIdx = v.indexOf(" ", slashIdx)
    const cmdEnd = spaceIdx < 0 ? v.length : spaceIdx
    const cmdText = v.slice(slashIdx, cmdEnd)
    return { slashIdx, cmdEnd, cmdText }
  }

  // Matching commands for hint bar
  const cmdHints = () => {
    const v = props.value
    if (!v.startsWith("/")) return []
    const spaceIdx = v.indexOf(" ")
    const partial = spaceIdx < 0 ? v.slice(1).toLowerCase() : v.slice(1, spaceIdx).toLowerCase()
    if (!partial) return []
    return ALL_COMMANDS
      .filter((c) => c.cmd.slice(1).toLowerCase().startsWith(partial))
      .slice(0, 5)
  }

  const hints = () => cmdHints()

  return (
    <box flexDirection="column">
      <box>
        {display() ? (
          <box>
            <text>{props.value.slice(0, clampedCursor())}</text>
            <text bg={theme.primary} fg={theme.background}>
              {props.value[clampedCursor()] || (useBlinkChar() ? "▌" : " ")}
            </text>
            <text>{props.value.slice(clampedCursor() + 1)}</text>
          </box>
        ) : (
          <text fg={theme.textMuted}>{props.placeholder} ▶</text>
        )}
      </box>
      <box>
        {/* Command hints bar */}
        {hints().length > 0 ? (
          <box>
            {hints().map((cmd, i) => {
              const items: any[] = []
              if (i > 0) items.push(<text fg={theme.textMuted}> · </text>)
              items.push(<text fg={theme.warning}>{cmd.cmd}</text>)
              items.push(<text fg={theme.textMuted}> {cmd.desc}</text>)
              return items
            })}
          </box>
        ) : null}
        {/* Char and token count */}
        {charCount() > 0 && hints().length === 0 ? (
          <box>
            <text fg={theme.textMuted}>  {charCount()} chars ~ {tokenEstimate()} tokens</text>
            <text fg={theme.textMuted}>  [{cursorGrapheme() + 1}/{graphemeCount(props.value)}]</text>
            {tokenEstimate() > 100000 && (
              <text fg={theme.error}>  WARN: Approaching context limit</text>
            )}
          </box>
        ) : null}
      </box>
    </box>
  )
}

/**
 * ChatArea - scrollable message list with decomposed message components.
 *
 * Implements manual scroll management using AppState's scrollOffset field.
 */
import { createSignal, createMemo, createEffect, For, Show } from "solid-js"
import { useAppStateContext } from "../hooks/AppStateProvider"
import { useTheme } from "../context/theme"
import { UserMessage } from "./messages/UserMessage"
import { AssistantMessage } from "./messages/AssistantMessage"
import { SystemErrorMessage } from "./messages/SystemErrorMessage"
import { AgentProgressTree, toolCallsToTreeNodes } from "./AgentProgressTree"
import { measureMessageRows } from "./messages/measure"
import { useBlinkChar } from "../util/animation"
import type { Message } from "../../protocol"

// ?? Constants ??
const RESERVED_ROWS = 5
const MAX_VISIBLE_MESSAGES = 200

// ?? Row estimation for scrollbar ??

function estimateMsgRows(msg: Message, terminalCols: number): number {
  return measureMessageRows(msg, terminalCols)
}

// ── Scroll bar (thin vertical indicator on the right) ──

function ScrollBar(props: { totalRows: number; viewRows: number; rowOffset: number; theme: any; barHeight: number }) {
  const { totalRows, viewRows, rowOffset, theme, barHeight } = props
  if (totalRows <= viewRows || barHeight <= 0) return null

  const viewHeight = Math.min(barHeight, totalRows)
  const maxRowOffset = Math.max(1, totalRows - viewRows)
  const thumbSize = Math.max(1, Math.floor(viewRows / totalRows * viewHeight))
  const trackSize = Math.max(0, viewHeight - thumbSize)

  // rowOffset=0 (at bottom) → thumb at bottom of track
  // rowOffset=maxRowOffset (at top) → thumb at top of track
  const thumbTrackPos = Math.round(trackSize * (1 - rowOffset / maxRowOffset))

  const rows: any[] = []
  for (let i = 0; i < viewHeight; i++) {
    const isThumb = i >= thumbTrackPos && i < thumbTrackPos + thumbSize
    rows.push(
      <box width={2} height={1} flexShrink={0}>
        <text fg={isThumb ? theme.textMuted : theme.borderSubtle}>
          {isThumb ? "▮" : " "}
        </text>
      </box>,
    )
  }

  return (
    <box flexDirection="column" width={2} height={viewHeight} flexShrink={0}>
      {rows}
    </box>
  )
}

// ?? Main ChatArea ??

interface Props {
  terminalCols: number
  terminalRows: number
}

export function ChatArea(props: Props) {
  const { theme } = useTheme()
  const { messages, currentMessage, isGenerating, setState, scrollOffset: appScrollOffset }
    = useAppStateContext()

  const [expandedTools] = createSignal<Set<string>>(new Set())

  const viewportHeight = () => Math.max(3, props.terminalRows - RESERVED_ROWS)

  const [showAll, setShowAll] = createSignal(false)

  const allMessages = createMemo(() => {
    const msgs = messages()
    const cur = currentMessage()
    const filtered = cur ? msgs.filter((m) => m.id !== cur.id) : msgs
    if (filtered.length > MAX_VISIBLE_MESSAGES && !showAll()) {
      return filtered.slice(filtered.length - MAX_VISIBLE_MESSAGES)
    }
    return filtered
  })

  const hiddenCount = createMemo(() => {
    const msgs = messages()
    const cur = currentMessage()
    const filtered = cur ? msgs.filter((m) => m.id !== cur.id) : msgs
    return Math.max(0, filtered.length - MAX_VISIBLE_MESSAGES)
  })

  const total = () => messages().length + (currentMessage() ? 1 : 0)

  // ?? Scroll management (message-based slicing) ??

  const visibleCount = createMemo(() => {
    return Math.max(1, Math.floor(viewportHeight() / 3))
  })

  const maxOffset = createMemo(() => {
    return Math.max(0, allMessages().length - visibleCount())
  })

  const effectiveOffset = createMemo(() => {
    return Math.min(appScrollOffset(), maxOffset())
  })

  createEffect(() => {
    const clamped = effectiveOffset()
    if (appScrollOffset() !== clamped) {
      setState("scrollOffset", clamped)
    }
  })

  const visibleMessages = createMemo(() => {
    const all = allMessages()
    const allLen = all.length
    const vc = visibleCount()
    const offset = effectiveOffset()
    const startIdx = Math.max(0, allLen - vc - offset)
    const endIdx = Math.min(startIdx + vc, allLen)
    return all.slice(startIdx, endIdx)
  })

  createEffect(() => {
    const len = allMessages().length
    const maxO = maxOffset()
    const cur = effectiveOffset()
    if (cur > maxO) {
      setState("scrollOffset", maxO)
    }
  })

  // ?? Scrollbar row estimation ??

  const totalRows = createMemo(() => {
    let r = 0
    for (const msg of allMessages()) r += estimateMsgRows(msg, props.terminalCols)
    if (currentMessage()) r += estimateMsgRows(currentMessage()!, props.terminalCols)
    return r
  })

  // Convert message-offset to estimated row-offset for scrollbar visualization
  const rowOffset = createMemo(() => {
    const offset = effectiveOffset()
    if (offset === 0) return 0
    const all = allMessages()
    const cols = Math.max(20, props.terminalCols)
    let r = 0
    const len = all.length
    const start = Math.max(0, len - offset)
    for (let i = start; i < len; i++) {
      r += estimateMsgRows(all[i], props.terminalCols)
    }
    return r
  })

  // ?? Mouse scroll handler ??
  function handleMouseScroll(evt: any) {
    const delta = evt?.deltaY ?? evt?.wheelDelta ?? 0
    if (delta > 0) {
      setState("scrollOffset", (prev: number) => Math.max(0, prev - Math.ceil(delta / 50)))
    } else if (delta < 0) {
      setState("scrollOffset", (prev: number) => {
        const maxO = maxOffset()
        return Math.min(maxO, prev + Math.ceil(Math.abs(delta) / 50))
      })
    }
  }

  // ?? Render ??
  const t = theme as any

  return (
    <box flexDirection="row" width="100%">
      {/* Main message area */}
      <box flexGrow={1} flexDirection="column" onMouseScroll={handleMouseScroll}>
        {/* Header */}
        <box paddingLeft={1}>
          <text fg={t.textMuted}>{`[${total()}]`}</text>
        </box>

        {/* Hidden messages warning */}
        <Show when={hiddenCount() > 0 && !showAll()}>
          <box paddingLeft={1}>
            <text fg={t.textMuted}>{`- ${hiddenCount()} older hidden `}</text>
            <text fg={t.info} attributes={1}>[scroll up to show all]</text>
          </box>
        </Show>

        {/* Scroll-up indicator */}
        <Show when={effectiveOffset() > 1}>
          <box paddingLeft={1}>
            <text fg={t.warning}>{`^ ${effectiveOffset()} msgs `}</text>
            <text fg={t.textMuted}>PgUp scroll / mouse wheel</text>
          </box>
        </Show>

        {/* Visible messages */}
        <Show when={visibleMessages().length > 0}>
          <For each={visibleMessages()}>
            {(msg) => (
              <box flexDirection="column" marginBottom={1}>
                {msg.type === "user" && <UserMessage msg={msg} />}
                {msg.type === "assistant" && <AssistantMessage msg={msg} terminalCols={props.terminalCols} />}
                {msg.type === "system" && <SystemErrorMessage msg={msg} />}
              </box>
            )}
          </For>
        </Show>

        {/* Current/streaming message */}
        <Show when={currentMessage()}>
          {(cm) => (
            <box flexDirection="column" marginTop={1}>
              <box paddingLeft={1}>
                <text fg={t.primary} attributes={1}>Assistant (streaming)</text>
              </box>
              <Show when={cm().thinking}>
                <box paddingX={1}>
                  <text fg={t.textMuted}>{cm().thinking.slice(0, 200)}</text>
                </box>
              </Show>
              <Show when={cm().toolCalls?.length > 0}>
                <box paddingX={1}>
                  <AgentProgressTree
                    nodes={toolCallsToTreeNodes(cm().toolCalls)}
                    terminalCols={props.terminalCols}
                  />
                </box>
              </Show>
              <Show when={cm().content}>
                <box paddingX={1} marginTop={1} flexDirection="row">
                  <text>{cm().content}</text>
                  <text fg={t.primary}>{useBlinkChar() ? "▌" : " "}</text>
                </box>
              </Show>
            </box>
          )}
        </Show>

        {/* Empty state */}
        <Show when={allMessages().length === 0 && !currentMessage()}>
          <box paddingLeft={1}>
            <text fg={t.textMuted}>No messages yet. Type a message to begin.</text>
          </box>
        </Show>
      </box>

      {/* Scroll bar — show as soon as content exceeds viewport by even 1 row */}
      <Show when={totalRows() >= viewportHeight() && total() > 0}>
        <ScrollBar
          totalRows={totalRows()}
          viewRows={viewportHeight()}
          rowOffset={rowOffset()}
          theme={t}
          barHeight={viewportHeight()}
        />
      </Show>
    </box>
  )
}

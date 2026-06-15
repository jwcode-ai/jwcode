/**
 * useSelection — wraps OpenTUI's built-in selection with auto-copy, double/triple-click.
 *
 * OpenTUI already provides startSelection/updateSelection/getSelection on the
 * renderer. This hook adds convenience: auto-copy via OSC 52, click pattern
 * detection (word/line selection), and state tracking for the status line.
 */
import { createSignal, createMemo, onCleanup } from "solid-js"

export interface SelectionInfo {
  active: boolean
  text: string
}

interface UseSelectionOptions {
  /** Callback fired when text is selected and copied */
  onCopy?: (text: string) => void
}

export function useSelection(renderer: any, options?: UseSelectionOptions) {
  const [active, setActive] = createSignal(false)
  const [selectedText, setSelectedText] = createSignal("")

  let clickTimer: ReturnType<typeof setTimeout> | null = null
  let clickCount = 0
  let lastClickTime = 0
  let lastClickX = -1
  let lastClickY = -1
  let mouseDownTarget: any = null
  let mouseDownTime = 0
  let longPressTimer: ReturnType<typeof setTimeout> | null = null

  function getTextRange(x1: number, y1: number, x2: number, y2: number): string {
    const sel = renderer.getSelection?.()
    if (sel) return sel.getSelectedText()
    return ""
  }

  function resolveTarget(evt: any): any {
    // Use the root renderable container so selection works even when
    // mouse events are captured by an overlay element.
    return renderer.getSelectionContainer?.() || evt.target
  }

  function handleMouseDown(evt: any) {
    if (evt.button !== 0) return // left button only

    const now = Date.now()
    const samePos = evt.x === lastClickX && evt.y === lastClickY

    if (samePos && now - lastClickTime < 500) {
      clickCount++
    } else {
      clickCount = 1
    }
    lastClickTime = now
    lastClickX = evt.x
    lastClickY = evt.y

    mouseDownTarget = resolveTarget(evt)

    if (clickCount === 2) {
      // Double-click: OpenTUI handles word selection natively
      renderer.startSelection?.(mouseDownTarget, evt.x, evt.y)
      // Update selection text for StatusLine display
      setTimeout(() => {
        const sel = renderer.getSelection?.()
        if (sel) setSelectedText(sel.getSelectedText())
      }, 10)
      evt.preventDefault?.()
      return
    }

    if (clickCount >= 3) {
      // Triple-click: select line — approximate by extending horizontally
      renderer.startSelection?.(mouseDownTarget, 0, evt.y)
      renderer.updateSelection?.(mouseDownTarget, 9999, evt.y, { finishDragging: true })
      setTimeout(() => {
        const sel = renderer.getSelection?.()
        if (sel) setSelectedText(sel.getSelectedText())
      }, 10)
      evt.preventDefault?.()
      return
    }

    // Start drag selection
    setActive(true)
    renderer.startSelection?.(mouseDownTarget, evt.x, evt.y)
  }

  function handleMouseMove(evt: any) {
    // Cancel long-press if user starts dragging
    if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null }
    if (clickCount > 1) return // word/line selection already done
    if (!active()) return
    renderer.updateSelection?.(evt.target || mouseDownTarget, evt.x, evt.y)
  }

  function handleMouseUp(evt: any) {
    if (!active() && clickCount <= 1) return
    const target = evt.target || mouseDownTarget
    if (target) {
      renderer.updateSelection?.(target, evt.x, evt.y, { finishDragging: true })
    }
    setActive(false)
    // Copy is handled by App.tsx's right-click handler, not here
    // Only track selection text for StatusLine display
    const sel = renderer.getSelection?.()
    if (sel) setSelectedText(sel.getSelectedText())

    if (clickTimer) clearTimeout(clickTimer)
    clickTimer = setTimeout(() => { clickCount = 0 }, 500)
  }

  onCleanup(() => {
    if (clickTimer) clearTimeout(clickTimer)
    if (longPressTimer) clearTimeout(longPressTimer)
  })

  function clearSelection() {
    renderer.clearSelection?.()
    setActive(false)
    setSelectedText("")
    clickCount = 0
  }

  const selectionInfo = createMemo((): SelectionInfo => ({
    active: active(),
    text: selectedText(),
  }))

  return {
    selection: selectionInfo,
    handleMouseDown,
    handleMouseMove,
    handleMouseUp,
    clearSelection,
  }
}

/**
 * Shared animation utilities for OpenTUI/Solid components.
 *
 * Modeled after Claude Code's `useBlink` / `useAnimationFrame` hooks
 * (claude-code-source-main/src/hooks/useBlink.ts), but adapted for Solid
 * since @opentui/solid 0.4.0 does not provide animation hooks.
 *
 * All blink/animation consumers share a single setInterval that runs only
 * when at least one subscriber is mounted — no per-component drift, no
 * wasted work when the TUI is idle.
 */

import { createSignal, onCleanup } from "solid-js"

const subscribers = new Set<(t: number) => void>()
let _tick = 0
let _interval: ReturnType<typeof setInterval> | null = null

function ensureRunning(): void {
  if (_interval) return
  _interval = setInterval(() => {
    _tick = Date.now()
    for (const fn of subscribers) fn(_tick)
  }, 530)
}

function stop(): void {
  if (_interval) {
    clearInterval(_interval)
    _interval = null
  }
}

/**
 * Subscribe to a shared millisecond clock. Returns the current tick value
 * (initially 0). All subscribers see the same time so blinks stay in sync.
 */
export function sharedClock(): number {
  const [t, setT] = createSignal(_tick)
  subscribers.add(setT)
  ensureRunning()
  onCleanup(() => {
    subscribers.delete(setT)
    if (subscribers.size === 0) stop()
  })
  return t()
}

/**
 * Returns true on the "on" half of a blink cycle, false on the "off" half.
 * Both states last `periodMs / 2`. Always returns true when `enabled` is false
 * (steady-on, no flicker).
 */
export function useBlinkChar(enabled = true, periodMs = 530): boolean {
  if (!enabled) return true
  const t = sharedClock()
  return Math.floor(t / periodMs) % 2 === 0
}

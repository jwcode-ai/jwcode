/**
 * useKeybinding — context-based keybinding registry.
 * Inspired by Claude Code's keybinding system but simplified.
 *
 * Keybinding contexts:
 *  - Global: always active (stop, new-session, clear, help)
 *  - Chat: when input is focused and no modal is open
 *  - Scroll: when scroll offset > 0 (page up/down, home/end)
 *  - Confirm: when approval modal is active (y/n/1/2/3/4)
 */
import { createSignal, createMemo } from "solid-js"

export type KeybindingContext = "global" | "chat" | "scroll" | "confirm"
export type KeyAction =
  | "stop" | "pause" | "resume"
  | "new-session" | "clear" | "help"
  | "toggle-plan" | "toggle-auto"
  | "config-panel"
  | "scroll-up" | "scroll-down" | "scroll-top" | "scroll-bottom"
  | "page-up" | "page-down"
  | "confirm-yes" | "confirm-no" | "confirm-session" | "confirm-auto"
  | "copy-selection"

interface Binding {
  context: KeybindingContext
  keyName: string
  ctrl?: boolean
  meta?: boolean
  action: KeyAction
  handler: () => void
}

const _bindings: Binding[] = []
const _contexts = createSignal<Set<KeybindingContext>>(new Set(["global"]))

/** Register a keybinding */
export function registerBinding(b: Binding): () => void {
  _bindings.push(b)
  return () => {
    const idx = _bindings.indexOf(b)
    if (idx >= 0) _bindings.splice(idx, 1)
  }
}

/** Set active contexts */
export function setActiveContexts(ctx: KeybindingContext | KeybindingContext[]) {
  const next = new Set<KeybindingContext>()
  next.add("global")
  if (Array.isArray(ctx)) ctx.forEach((c) => next.add(c))
  else next.add(ctx)
  _contexts[1](next)
}

/** Look up a binding by key event. Returns matched action + handler or null. */
export function matchBinding(keyName: string, ctrl: boolean, meta: boolean): { action: KeyAction; handler: () => void } | null {
  const active = _contexts[0]()
  for (const b of _bindings) {
    if (!active.has(b.context)) continue
    if (b.keyName !== keyName) continue
    if (b.ctrl !== undefined && b.ctrl !== ctrl) continue
    if (b.meta !== undefined && b.meta !== meta) continue
    return { action: b.action, handler: b.handler }
  }
  return null
}

/** Register default keybindings */
export function registerDefaults(): () => void {
  const unsubs: (() => void)[] = []
  const reg = (b: Binding) => unsubs.push(registerBinding(b))
  reg({ context: "global", keyName: "escape", ctrl: false, action: "stop", handler: () => {} })
  reg({ context: "global", keyName: "c", ctrl: true, action: "stop", handler: () => {} })
  reg({ context: "global", keyName: "l", ctrl: true, action: "clear", handler: () => {} })
  reg({ context: "global", keyName: "n", ctrl: true, action: "new-session", handler: () => {} })
  reg({ context: "global", keyName: "r", ctrl: true, action: "scroll-bottom", handler: () => {} })
  reg({ context: "global", keyName: "f1", ctrl: false, action: "help", handler: () => {} })
  reg({ context: "global", keyName: "h", ctrl: true, action: "help", handler: () => {} })
  reg({ context: "global", keyName: "i", ctrl: true, action: "config-panel", handler: () => {} })
  reg({ context: "global", keyName: "tab", ctrl: false, action: "toggle-plan", handler: () => {} })
  reg({ context: "global", keyName: "p", ctrl: true, action: "toggle-auto", handler: () => {} })
  reg({ context: "global", keyName: "s", ctrl: true, action: "pause", handler: () => {} })

  // Scroll keys (PgUp/PgDn only — Up/Down/Home/End consumed by TextInput for input navigation)
  reg({ context: "global", keyName: "pageup", ctrl: false, action: "page-up", handler: () => {} })
  reg({ context: "global", keyName: "pagedown", ctrl: false, action: "page-down", handler: () => {} })

  reg({ context: "confirm", keyName: "y", ctrl: false, action: "confirm-yes", handler: () => {} })
  reg({ context: "confirm", keyName: "n", ctrl: false, action: "confirm-no", handler: () => {} })
  reg({ context: "confirm", keyName: "1", ctrl: false, action: "confirm-yes", handler: () => {} })
  reg({ context: "confirm", keyName: "2", ctrl: false, action: "confirm-session", handler: () => {} })
  reg({ context: "confirm", keyName: "3", ctrl: false, action: "confirm-auto", handler: () => {} })
  reg({ context: "confirm", keyName: "4", ctrl: false, action: "confirm-no", handler: () => {} })

  return () => unsubs.forEach((u) => u())
}

/**
 * Persistent input history for the TUI text input.
 *
 * Stored at ~/.jwcode/history.json. Loaded once on startup, dedup-pushed
 * on each submit, written through. Capped at MAX entries.
 *
 * Up/Down navigation uses `prevMatch`/`nextMatch` to step through entries
 * matching the current input — mirrors claude-code-source-main's
 * useArrowKeyHistory behaviour.
 */

import { existsSync, readFileSync, writeFileSync, mkdirSync } from "node:fs"
import { join } from "node:path"
import { homedir } from "node:os"

const HISTORY_PATH = join(homedir(), ".jwcode", "history.json")
const MAX = 200

export function loadHistory(): string[] {
  try {
    if (!existsSync(HISTORY_PATH)) return []
    const raw = readFileSync(HISTORY_PATH, "utf8")
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter((v): v is string => typeof v === "string").slice(-MAX)
  } catch {
    return []
  }
}

export function saveHistory(items: string[]): void {
  try {
    const dir = join(homedir(), ".jwcode")
    if (!existsSync(dir)) mkdirSync(dir, { recursive: true })
    writeFileSync(HISTORY_PATH, JSON.stringify(items.slice(-MAX)))
  } catch {
    // best-effort: ignore write failures (read-only fs, permissions, etc.)
  }
}

/** Push v onto items, removing any earlier duplicate, and cap at MAX. */
export function dedupPush(items: string[], v: string): string[] {
  if (!v.trim()) return items
  const next = items.slice()
  const i = next.lastIndexOf(v)
  if (i >= 0) next.splice(i, 1)
  next.push(v)
  return next.slice(-MAX)
}

/** Find previous (older) history item matching `current` (prefix or substring). */
export function prevMatch(items: string[], current: string, idx: number): number {
  if (items.length === 0) return -1
  for (let i = idx - 1; i >= 0; i--) {
    const it = items[i]
    if (it.startsWith(current) || (current && it.includes(current))) return i
  }
  return -1
}

/** Find next (newer) history item matching `current` (prefix or substring). */
export function nextMatch(items: string[], current: string, idx: number): number {
  for (let i = idx + 1; i < items.length; i++) {
    const it = items[i]
    if (it.startsWith(current) || (current && it.includes(current))) return i
  }
  return -1
}

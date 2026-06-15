/**
 * format - shared formatting utilities for message components.
 */

export function formatTimestamp(ts: number): string {
  const d = new Date(ts)
  const hh = d.getHours().toString().padStart(2, "0")
  const mm = d.getMinutes().toString().padStart(2, "0")
  return `${hh}:${mm}`
}

export function truncate(s: unknown, max: number): string {
  const str = typeof s === "string" ? s : String(s ?? "")
  if (str.length <= max) return str
  return str.slice(0, max) + "..."
}

export function formatDuration(sec: number): string {
  if (sec <= 0) return ""
  if (sec >= 60) {
    const m = Math.floor(sec / 60)
    const s = sec % 60
    return `${m}m${s}s`
  }
  return `${sec}s`
}


/**
 * Module-level conversation store — survives TUI lifecycle.
 * Messages are written here by App.tsx and printed to the main
 * screen buffer before the alternate screen is torn down on exit.
 */
interface StoredMessage {
  role: "user" | "assistant" | "system"
  content: string
  timestamp: number
}

const _messages: StoredMessage[] = []
const MAX_STORED = 100

export function recordMessage(role: StoredMessage["role"], content: string): void {
  if (!content) return
  _messages.push({ role, content, timestamp: Date.now() })
  if (_messages.length > MAX_STORED) {
    _messages.splice(0, _messages.length - MAX_STORED)
  }
}

export function getStoredMessages(): readonly StoredMessage[] {
  return _messages
}

/** Print all stored messages as plain text to stdout. */
export function printStoredMessages(): void {
  if (_messages.length === 0) return
  process.stdout.write("\n")
  process.stdout.write("─".repeat(60) + "\n")
  process.stdout.write("Session history:\n")
  process.stdout.write("─".repeat(60) + "\n")
  for (const msg of _messages) {
    const label = msg.role === "user" ? "You" : msg.role === "assistant" ? "JWCode" : "System"
    process.stdout.write(`\n[${label}]\n`)
    // Print content line by line so it wraps naturally in the terminal
    const lines = msg.content.split("\n")
    for (const line of lines) {
      process.stdout.write("  " + line + "\n")
    }
  }
  process.stdout.write("\n")
}

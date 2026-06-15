/**
 * Row-count estimation for messages, used by ChatArea for scrollbar
 * positioning and viewport sizing.
 *
 * Accuracy matters more for the scrollbar thumb than for performance —
 * small errors (±2 rows) are acceptable. We approximate Markdown line
 * counts by splitting on \n + ceil(text length / cols) for each line.
 */

import type { Message } from "../../../protocol"

const HEADER_ROWS = 2
const USER_HEADER_ROWS = 2
const ERR_HEADER_ROWS = 1

function lineCount(text: string, cols: number): number {
  if (!text) return 0
  const lines = text.split("\n")
  let total = 0
  for (const line of lines) {
    total += Math.max(1, Math.ceil((line.length || 1) / Math.max(1, cols)))
  }
  return total
}

function measureUser(msg: Message, cols: number): number {
  return USER_HEADER_ROWS + lineCount(msg.content, cols)
}

function measureAssistant(msg: any, cols: number): number {
  let rows = HEADER_ROWS
  if (msg.thinking) rows += Math.min(8, lineCount(msg.thinking, cols)) + 1
  if (msg.toolCalls?.length) rows += msg.toolCalls.length * 3
  if (msg.steps?.length) rows += msg.steps.length * 2
  if (msg.content) rows += lineCount(msg.content, cols) + 1
  return rows
}

function measureError(msg: Message, cols: number): number {
  return ERR_HEADER_ROWS + lineCount(msg.content, cols)
}

export function measureMessageRows(msg: Message, cols: number): number {
  const w = Math.max(20, cols)
  switch (msg.type) {
    case "user": return measureUser(msg, w)
    case "assistant": return measureAssistant(msg, w)
    case "system": return measureError(msg, w)
    default: return 2
  }
}

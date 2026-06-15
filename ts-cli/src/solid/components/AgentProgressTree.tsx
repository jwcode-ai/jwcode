/**
 * AgentProgressTree — tree-structured agent/tool progress display.
 * Inspired by Claude Code's agent progress lines.
 *
 * Renders tool calls as a tree: ├── / └── connectors with indentation,
 * color-coded agent type badges, status indicators, and compact mode.
 */
import { useTheme } from "../context/theme"
import type { ToolCall } from "../../protocol"

interface TreeNode {
  id: string
  name: string
  status: "running" | "complete" | "error"
  args?: string
  result?: string
  duration?: number
  timestamp?: number
  children?: TreeNode[]
  tokenCount?: number
  toolUseCount?: number
}

interface Props {
  nodes: TreeNode[]
  level?: number
  collapsed?: boolean
  terminalCols: number
}

/** Agent type → color mapping */
function agentColor(name: string, theme: any): string {
  const lower = name.toLowerCase()
  if (lower.includes("orchestrator") || lower.includes("architect") || lower.includes("planner")) return theme.primary
  if (lower.includes("coder") || lower.includes("developer") || lower.includes("implement")) return theme.success
  if (lower.includes("explorer") || lower.includes("reader") || lower.includes("search") || lower.includes("grep") || lower.includes("list") || lower.includes("find") || lower.includes("glob")) return theme.info
  if (lower.includes("reviewer") || lower.includes("debugger") || lower.includes("auditor") || lower.includes("security")) return theme.warning
  if (lower.includes("writer") || lower.includes("editor") || lower.includes("modifier")) return theme.accent
  return theme.textMuted
}

/** Short status indicator */
function statusIcon(status: string): string {
  if (status === "running") return "●"
  if (status === "complete") return "✓"
  if (status === "error") return "✗"
  return "?"
}

function statusColor(status: string, theme: any): string {
  if (status === "running") return theme.warning
  if (status === "complete") return theme.success
  if (status === "error") return theme.error
  return theme.textMuted
}

function formatDuration(sec?: number): string {
  if (sec == null || sec <= 0) return ""
  if (sec >= 60) {
    const m = Math.floor(sec / 60)
    const s = sec % 60
    return `(${m}m${s}s)`
  }
  return `(${sec}s)`
}

/** Format token count */
function formatTokens(n?: number): string {
  if (n == null || n <= 0) return ""
  if (n >= 1000) return `${(n / 1000).toFixed(1)}K`
  return String(n)
}

function truncateArgs(s?: string, max = 60): string {
  if (!s) return ""
  const clean = s.replace(/\s+/g, " ").trim()
  if (clean.length <= max) return clean
  return clean.slice(0, max) + "…"
}

export function AgentProgressTree(props: Props) {
  const { theme } = useTheme()
  const { nodes, level = 0, collapsed = false, terminalCols } = props

  if (!nodes || nodes.length === 0) return null
  if (collapsed) {
    // Show a single summary line
    const running = nodes.filter((n) => n.status === "running").length
    const total = nodes.length
    const allDone = running === 0
    const icon = allDone ? "✓" : `●${running}`
    const color = allDone ? theme.success : theme.warning
    const tc = nodes.reduce((s, n) => s + (n.tokenCount || 0), 0)
    const tu = nodes.reduce((s, n) => s + (n.toolUseCount || 0), 0)
    const extras: string[] = []
    if (tu > 0) extras.push(`${tu} tool uses`)
    if (tc > 0) extras.push(`${formatTokens(tc)} tokens`)
    const suffix = extras.length > 0 ? ` · ${extras.join(" · ")}` : ""
    return (
      <box paddingLeft={level * 2}>
        <text fg={color}>{`${icon} ${total} agents${suffix}`}</text>
      </box>
    )
  }

  const elements: any[] = []

  for (let i = 0; i < nodes.length; i++) {
    const node = nodes[i]
    const isLast = i === nodes.length - 1
    const connector = isLast ? "└── " : "├── "
    const indent = "  ".repeat(level)
    const prefix = level > 0 ? indent + connector : ""
    const color = agentColor(node.name, theme)
    const sColor = statusColor(node.status, theme)
    const icon = statusIcon(node.status)
    const dur = formatDuration(node.duration)
    const hasChildren = node.children && node.children.length > 0

    elements.push(
      <box flexDirection="column">
        <box>
          <text fg={sColor}>{`${prefix}${icon} `}</text>
          <text fg={color} attributes={1}>{node.name}</text>
          {dur ? <text fg={theme.textMuted}>{` ${dur}`}</text> : null}
          {node.tokenCount ? <text fg={theme.textMuted}>{` ${formatTokens(node.tokenCount)} tokens`}</text> : null}
          {node.toolUseCount ? <text fg={theme.textMuted}>{` ${node.toolUseCount} tools`}</text> : null}
        </box>
        {node.args && node.status === "running" && (
          <box paddingLeft={level * 2 + 4}>
            <text fg={theme.textMuted}>{truncateArgs(node.args, terminalCols - level * 2 - 8)}</text>
          </box>
        )}
        {hasChildren ? (
          <AgentProgressTree
            nodes={node.children!}
            level={level + 1}
            terminalCols={terminalCols}
          />
        ) : null}
      </box>,
    )
  }

  return <box flexDirection="column">{elements}</box>
}

/** Convert ToolCall[] to TreeNode[] with grouping */
export function toolCallsToTreeNodes(toolCalls: ToolCall[]): TreeNode[] {
  const EXPLORING = new Set(["read", "list", "search", "grep", "ls", "glob", "find", "view"])

  const result: TreeNode[] = []
  let i = 0

  while (i < toolCalls.length) {
    const tc = toolCalls[i]

    if (EXPLORING.has(tc.name.toLowerCase())) {
      // Group consecutive exploring tools
      const group: ToolCall[] = [tc]
      i++
      while (i < toolCalls.length && EXPLORING.has(toolCalls[i].name.toLowerCase())) {
        group.push(toolCalls[i])
        i++
      }
      result.push({
        id: group[0].id || `explore-${Date.now()}`,
        name: `Explore (${group.length} files)`,
        status: group.every((t) => t.status === "complete") ? "complete" : "running",
        toolUseCount: group.length,
        duration: group.reduce((s, t) => s + (t.duration || 0), 0),
        children: group.map((t) => ({
          id: t.id || "",
          name: t.name,
          status: t.status || "running",
          args: t.args,
          result: t.result,
          duration: t.duration,
        })),
      })
    } else {
      result.push({
        id: tc.id || tc.name || `tool-${i}`,
        name: tc.name || "Tool",
        status: tc.status || "running",
        args: tc.args,
        result: tc.result,
        duration: tc.duration,
        timestamp: tc.timestamp,
      })
      i++
    }
  }

  return result
}

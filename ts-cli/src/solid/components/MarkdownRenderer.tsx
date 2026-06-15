/**
 * Markdown renderer with token caching and fast-path plain text detection.
 * Ported from Ink/React to Solid/OpenTUI.
 *
 * Key changes:
 *  - Solid reactivity replaces React useMemo/memo
 *  - OpenTUI <box>/<text> replaces Ink <Box>/<Text>
 *  - Theme colors from Solid context instead of Ink useTheme
 *  - uses `marked` lib via lazy ESM import
 */
import { createMemo } from "solid-js"
import { useTheme } from "../context/theme"
import { RGBA } from "@opentui/core"

// Lazy-load marked
let markedModule: typeof import("marked") | null = null
let markedLoading: Promise<void> | null = null

async function loadMarked(): Promise<void> {
  if (markedModule) return
  if (markedLoading) return markedLoading
  markedLoading = (async () => {
    try {
      const mod = await import("marked")
      markedModule = mod
    } catch {
      // marked not available, fallback to plain text
    }
  })()
  return markedLoading
}

// Kick off eager load
loadMarked()

function getMarked(): typeof import("marked") | null {
  return markedModule
}

const TOKEN_CACHE_MAX = 500
const tokenCache = new Map<string, any[]>()
const MD_SYNTAX_RE = /[#*`|[>\-_~]|\n\n|^\d+\. |\n\d+\. /

function hasMarkdownSyntax(s: string): boolean {
  return MD_SYNTAX_RE.test(s.length > 500 ? s.slice(0, 500) : s)
}

// Simple hash function for cache key
function cacheKey(content: string): string {
  return content.length + ":" + content.slice(0, 200)
}

function cachedLexer(content: string): any[] {
  if (!hasMarkdownSyntax(content)) {
    return [
      {
        type: "paragraph",
        raw: content,
        text: content,
        tokens: [{ type: "text", raw: content, text: content }],
      },
    ]
  }

  const key = cacheKey(content)
  const cached = tokenCache.get(key)
  if (cached) return cached

  const md = markedModule ?? null
  if (!md) {
    return [
      {
        type: "paragraph",
        raw: content,
        text: content,
        tokens: [{ type: "text", raw: content, text: content }],
      },
    ]
  }

  try {
    const tokens = new md.Lexer().lex(content) as any[]
    if (tokenCache.size >= TOKEN_CACHE_MAX) {
      const firstKey = tokenCache.keys().next().value
      if (firstKey) tokenCache.delete(firstKey)
    }
    tokenCache.set(key, tokens)
    return tokens
  } catch {
    return [
      {
        type: "paragraph",
        raw: content,
        text: content,
        tokens: [{ type: "text", raw: content, text: content }],
      },
    ]
  }
}

function plainText(tokens: any[]): string {
  return tokens.map((tk: any) => tk.text || tk.raw || "").join("")
}

function inlineTokens(tokens: any[], keyPrefix: string, theme: any): JSX.Element[] {
  const nodes: any[] = []
  let i = 0
  for (const tok of tokens) {
    const k = keyPrefix + "-" + i++
    switch (tok.type) {
      case "strong": {
        const t = plainText(tok.tokens || [])
        if (t) nodes.push(<text attributes={1 /* BOLD */}>{t}</text>)
        break
      }
      case "em": {
        const t = plainText(tok.tokens || [])
        if (t) nodes.push(<text attributes={2 /* ITALIC */}>{t}</text>)
        break
      }
      case "codespan": {
        const t = tok.text || ""
        if (t) nodes.push(<text bg={theme.backgroundElement} fg={theme.warning} attributes={1}>{t}</text>)
        break
      }
      case "link": {
        const label = plainText(tok.tokens || [])
        if (label || tok.href) nodes.push(<text fg={theme.textMuted}>{label}{label ? " " : ""}({tok.href})</text>)
        break
      }
      case "text":
      default: {
        const t = tok.text || tok.raw || ""
        if (t) nodes.push(<text>{t}</text>)
        break
      }
    }
  }
  return nodes
}

// Typed hack for JSX
type JSX = any

interface Props {
  content: string
  terminalCols: number
}

export function MarkdownRenderer(props: Props) {
  const { theme } = useTheme()
  const tokens = createMemo(() => cachedLexer(props.content))

  const lines: any[] = []
  let idx = 0

  // We build the tree inside a createMemo so it only recomputes when content changes
  const rendered = createMemo(() => {
    const result: any[] = []
    let id = 0
    for (const tok of tokens()) {
      const k = "md-" + id++
      switch (tok.type) {
        case "heading": {
          const depth = tok.depth || 1
          const prefix = "#".repeat(depth) + " "
          result.push(
            <box marginTop={depth === 1 ? 1 : 0}>
              <text attributes={1} fg={depth <= 2 ? theme.primary : theme.textMuted}>
                {prefix}{plainText(tok.tokens || [])}
              </text>
            </box>,
          )
          break
        }

        case "paragraph":
          result.push(
            <box width="100%">
              {inlineTokens(tok.tokens || [], k, theme)}
            </box>,
          )
          break

        case "code": {
          const lang = tok.lang || ""
          const code = tok.text || ""
          const maxWidth = Math.min(props.terminalCols - 4, 100)
          const codeLines = code.split("\n")
          result.push(
            <box flexDirection="column" backgroundColor={theme.backgroundElement} paddingY={1}>
              {lang ? (
                <box paddingLeft={1}>
                  <text fg={theme.info} attributes={1}>{"┌─ "}{lang}</text>
                </box>
              ) : null}
              {codeLines.map((line: string, li: number) => (
                <box paddingLeft={2}>
                  <text fg={theme.warning}>{line.slice(0, maxWidth)}</text>
                </box>
              ))}
            </box>,
          )
          break
        }

        case "blockquote":
          result.push(
            <box flexDirection="column" paddingLeft={2} backgroundColor={theme.backgroundPanel}>
              {(tok.tokens || []).map((bt: any, bi: number) => (
                <box>
                  <text fg={theme.info}>▎ </text>
                  <text fg={theme.textMuted}>{bt.text || plainText(bt.tokens || [])}</text>
                </box>
              ))}
            </box>,
          )
          break

        case "list": {
          const items = tok.items || []
          result.push(
            <box flexDirection="column">
              {items.map((item: any, ii: number) => {
                const bullet = tok.ordered ? (tok.start || 1) + ii + ". " : "  • "
                return (
                  <box paddingLeft={1}>
                    <text fg={theme.textMuted}>{bullet}</text>
                    <box flexDirection="column">
                      {(item.tokens || []).map((it: any, i2: number) =>
                        it.type === "text" ? (
                          <box>{inlineTokens(it.tokens || [], `${k}-${ii}-${i2}`, theme)}</box>
                        ) : (
                          <text>{it.text || ""}</text>
                        ),
                      )}
                    </box>
                  </box>
                )
              })}
            </box>,
          )
          break
        }

        case "hr":
          result.push(
            <box>
              <text fg={theme.textMuted}>{"─".repeat(Math.min(props.terminalCols - 2, 60))}</text>
            </box>,
          )
          break

        case "space":
          break

        // Table rendering
        case "table": {
          const rawHeaders: string[] = (tok.header || []).map((h: any) => plainText(h.tokens || []))
          const rawAlign: (string | null)[] = tok.align || []
          const rawRows: string[][] = (tok.rows || []).map((row: any[]) =>
            row.map((cell: any) => plainText(cell.tokens || [])),
          )
          if (rawHeaders.length === 0) break

          const colCount = rawHeaders.length
          const gapWidth = 3
          const paddingWidth = 4
          const availableWidth = props.terminalCols - paddingWidth - (colCount - 1) * gapWidth - 2

          const naturalWidths = rawHeaders.map((h, ci) => {
            const cellWidths = rawRows.map((r) => (r[ci] || "").length)
            return Math.max(h.length, ...cellWidths, 1)
          })

          const MIN_COL_WIDTH = 3
          type ColKind = "narrative" | "tokenHeavy" | "compact"
          const colKind: ColKind[] = rawHeaders.map((_h, ci) => {
            const values = rawRows.map((r) => r[ci] || "")
            const totalWords = values.reduce((s, v) => s + v.split(/\s+/).length, 0)
            const avgWords = values.length > 0 ? totalWords / values.length : 0
            const avgWidth = values.length > 0 ? values.reduce((s, v) => s + v.length, 0) / values.length : 0
            const hasLongTokens = values.some((v) => v.split(/\s+/).some((t) => t.length >= 20))
            if (hasLongTokens) return "tokenHeavy"
            if (avgWords >= 4 || avgWidth >= 28) return "narrative"
            return "compact"
          })

          const shrinkPriority: Record<ColKind, number> = { tokenHeavy: 0, narrative: 1, compact: 2 }

          let colWidths = [...naturalWidths]
          const totalNatural = colWidths.reduce((s, w) => s + w, 0)
          if (totalNatural > availableWidth) {
            for (let iter = 0; iter < 1000; iter++) {
              const currentTotal = colWidths.reduce((s, w) => s + w, 0)
              if (currentTotal <= availableWidth) break
              let shrinkIdx = -1
              let bestPrio = -1
              for (let ci = 0; ci < colCount; ci++) {
                if (colWidths[ci] > MIN_COL_WIDTH) {
                  const prio = shrinkPriority[colKind[ci]]
                  if (prio > bestPrio) {
                    bestPrio = prio
                    shrinkIdx = ci
                  }
                }
              }
              if (shrinkIdx < 0) break
              colWidths[shrinkIdx]--
            }
          }

          const fits = colWidths.reduce((s, w) => s + w, 0) <= availableWidth || colWidths.every((w) => w === MIN_COL_WIDTH)

          if (!fits) {
            result.push(
              <box flexDirection="column">
                {rawRows.map((row, ri) => (
                  <box flexDirection="column">
                    {rawHeaders.map((h, ci) => (
                      <box>
                        <text attributes={1}>{h}: </text>
                        <text>{row[ci] || ""}</text>
                      </box>
                    ))}
                  </box>
                ))}
              </box>,
            )
          } else {
            const trunc = (s: string, w: number) => (s.length > w ? s.slice(0, Math.max(w - 1, 0)) + "…" : s)
            const align = (s: string, w: number, a: string | null) => {
              const tr = trunc(s, w)
              const pad = w - tr.length
              if (a === "right") return " ".repeat(pad) + tr
              if (a === "center") {
                const left = Math.floor(pad / 2)
                return " ".repeat(left) + tr + " ".repeat(pad - left)
              }
              return tr + " ".repeat(pad)
            }

            result.push(
              <box flexDirection="column">
                <box>
                  {rawHeaders.map((h, ci) => (
                    <box width={colWidths[ci] + gapWidth}>
                      <text attributes={1}>{align(h, colWidths[ci], rawAlign[ci])}</text>
                      {ci < colCount - 1 && <text fg={theme.textMuted}>| </text>}
                    </box>
                  ))}
                </box>
                <box>
                  {rawHeaders.map((_h, ci) => (
                    <text fg={theme.textMuted}>
                      {"─".repeat(colWidths[ci])}{ci < colCount - 1 ? "│ " : ""}
                    </text>
                  ))}
                </box>
                {rawRows.slice(0, 20).map((row, ri) => (
                  <box>
                    {row.map((cell, ci) => (
                      <box width={colWidths[ci] + gapWidth}>
                        <text>{align(cell, colWidths[ci], rawAlign[ci])}</text>
                        {ci < colCount - 1 && <text fg={theme.textMuted}>| </text>}
                      </box>
                    ))}
                  </box>
                ))}
                {rawRows.length > 20 && (
                  <text fg={theme.textMuted}>  … {rawRows.length - 20} more rows</text>
                )}
              </box>,
            )
          }
          break
        }

        default:
          if (tok.raw) {
            result.push(<text>{tok.raw.trimEnd()}</text>)
          }
          break
      }
    }
    return result
  })

  return <box flexDirection="column" width="100%">{rendered()}</box>
}

/**
 * StatusLine — single-line compact status bar with optional secondary row.
 *
 * Row 1: jwcode [Plan] ● model  Nmsgs  tokens+tok=total  ███░ 80%  12.5t/s  30s  status text
 * Row 2: [DEGRADED] ...  or  ⚙ compaction ...  (only when active)
 */
import { createSignal, createMemo, onCleanup, onMount } from "solid-js"
import { useTheme } from "../context/theme"
import { useAppStateContext } from "../hooks/AppStateProvider"
import { RGBA } from "@opentui/core"

function formatTokens(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000) return `${Math.round(n / 1_000)}K`
  return String(n)
}

function formatRate(rate: number): string {
  if (rate <= 0) return ""
  if (rate >= 100) return `${Math.round(rate)}t/s`
  return `${rate.toFixed(1)}t/s`
}

function formatElapsed(sec: number): string {
  if (sec <= 0) return ""
  if (sec >= 60) {
    const m = Math.floor(sec / 60)
    const s = sec % 60
    return `${m}m${s}s`
  }
  return `${sec}s`
}

function soften(c: RGBA, factor = 0.85): RGBA {
  const gray = 0.299 * c.r + 0.587 * c.g + 0.114 * c.b
  return RGBA.fromValues(
    gray + (c.r - gray) * factor,
    gray + (c.g - gray) * factor,
    gray + (c.b - gray) * factor,
    c.a,
  )
}

function dimColor(c: RGBA): RGBA {
  return RGBA.fromValues(c.r * 0.6, c.g * 0.6, c.b * 0.6, c.a)
}

interface StatusLineProps {
  selectionActive?: boolean
  selectionChars?: number
  selectedText?: string
}

export function StatusLine(props?: StatusLineProps) {
  const { theme: colors } = useTheme()
  const {
    usage, modelName, planMode, autoMode, connected, statusText,
    messages, tokenRate, isGenerating,
    degradation, planTasks, pdcaPhase, compactionProgress,
    scrollOffset,
  } = useAppStateContext()

  // ── Elapsed timer ──
  let startTime = 0
  const [elapsed, setElapsed] = createSignal(0)
  let interval: ReturnType<typeof setInterval> | undefined

  onMount(() => {
    if (isGenerating() && startTime === 0) startTime = Date.now()
  })

  interval = setInterval(() => {
    const gen = isGenerating()
    if (gen) {
      if (startTime === 0) startTime = Date.now()
      setElapsed(Math.floor((Date.now() - startTime) / 1000))
    } else {
      startTime = 0
      if (elapsed() > 0) setElapsed(0)
    }
  }, 1000)

  onCleanup(() => {
    if (interval) clearInterval(interval)
  })

  // ── Memoized color transforms (avoid RGBA alloc per tick) ──
  const brandColor = createMemo(() => soften(colors.primary, 0.88))
  const successFg = createMemo(() => soften(colors.success, 0.88))
  const warningFg = createMemo(() => soften(colors.warning, 0.80))
  const errorFg = createMemo(() => soften(colors.error, 0.75))
  const muted = createMemo(() => dimColor(colors.textMuted))
  const dimText = createMemo(() => dimColor(colors.text))

  // ── Computed values ──
  const pct = () => Math.min(100, Math.round(usage().usageRatio * 100))
  const filled = () => Math.round(pct() / 10)
  const bar = () => "█".repeat(filled()) + "░".repeat(10 - filled())
  const model = () => modelName() || (connected() ? "ready" : "connecting...")
  const connIcon = () => (connected() ? "●" : "○")
  const rateStr = () => formatRate(tokenRate())
  const elapsedStr = () => formatElapsed(elapsed())
  const p = () => usage().promptTokens
  const c = () => usage().completionTokens
  const totalMsgs = () => messages().length
  const hasScrolled = () => scrollOffset() > 0
  const scrollLabel = () => `↑${scrollOffset()}`

  // Status text — truncated early so it fits inline
  const statusDisplay = createMemo(() => {
    const st = statusText()
    if (!st || st === "connecting...") return ""
    return st.slice(0, 50)
  })

  const isStatusError = () => statusText().startsWith("Error:")

  // Whether secondary info (degradation / compaction) needs a second row
  const showSecondary = () => degradation().active || compactionProgress() !== null

  return (
    <box flexDirection="column" width="100%">
      {/* ── Row 1: status text early, token bar/time at the end ── */}
      <box height={1}>
        <text fg={brandColor()}>jwcode</text>
        <text fg={muted()}> </text>

        {hasScrolled() && (
          <>
            <text fg={colors.warning}>{scrollLabel()}</text>
            <text fg={muted()}> </text>
          </>
        )}

        <text
          bg={planMode() ? colors.secondary : colors.success}
          fg={RGBA.fromInts(0, 0, 0)}
        >
          {planMode() ? " Plan" : " Act"}
        </text>

        {autoMode() && (
          <text bg={colors.warning} fg={RGBA.fromInts(0, 0, 0)}> AUTO</text>
        )}

        {pdcaPhase() && (
          <>
            <text bg={colors.warning} fg={RGBA.fromInts(0, 0, 0)}> {pdcaPhase()}</text>
            {planTasks().length > 0 && (
              <>
                <text fg={muted()}> </text>
                <text fg={colors.primary}>
                  {planTasks().filter((t) => t.status === "completed").length}
                </text>
                <text fg={muted()}>/</text>
                <text fg={colors.text}>{planTasks().length}</text>
              </>
            )}
          </>
        )}

        <text fg={connected() ? colors.success : colors.error}>{connIcon()}</text>
        <text fg={successFg()}>{model()}</text>
        <text fg={muted()}> </text>

        {totalMsgs() > 0 && (
          <>
            <text fg={colors.text}>{totalMsgs()}</text>
            <text fg={muted()}>msgs </text>
          </>
        )}

        {props?.selectionActive && !!props?.selectionChars && (
          <>
            <text fg={colors.info}>sel{props.selectionChars}</text>
            <text fg={muted()}> </text>
          </>
        )}

        {/* Status text early */}
        {statusDisplay() && (
          <text
            fg={isStatusError() ? colors.error : dimText()}
            attributes={isStatusError() ? 1 : 0}
          >
            {statusDisplay()}
          </text>
        )}

        {/* Token/time bar compacted at the end */}
        <text fg={muted()}> </text>
        <text>{formatTokens(p())}</text>
        <text fg={muted()}>+</text>
        <text fg={successFg()}>{formatTokens(c())}</text>
        <text fg={muted()}>=</text>
        <text fg={warningFg()}>{formatTokens(usage().totalTokens)}</text>
        <text fg={muted()}> </text>
        <text
          fg={
            pct() > 90 ? errorFg() : pct() > 70 ? warningFg() : colors.text
          }
        >
          {bar()} {pct()}%
        </text>

        {isGenerating() && rateStr() && (
          <text> {rateStr()}</text>
        )}
        {isGenerating() && elapsedStr() && (
          <text> {elapsedStr()}</text>
        )}
      </box>

      {/* ── Row 2: Degradation / compaction (only when active) ── */}
      {showSecondary() && (
        <box height={1}>
          {degradation().active ? (
            <text fg={colors.warning}>
              [{degradation().mode.toUpperCase()}] {degradation().message}
              {degradation().retryCount > 0
                ? ` (${degradation().retryCount}/${degradation().maxRetries})`
                : ""}
            </text>
          ) : compactionProgress()!.percent >= 100 ? (
            <text fg={colors.success}>
              ✓ {compactionProgress()!.message}
            </text>
          ) : (
            <>
              <text fg={colors.primary}>
                ⚙ {compactionProgress()!.message}
              </text>
              <text fg={colors.warning}>
                {" "}
                {"█".repeat(Math.round(compactionProgress()!.percent / 10))}
                {"░".repeat(
                  10 - Math.round(compactionProgress()!.percent / 10),
                )}
              </text>
              <text fg={muted()}> {compactionProgress()!.percent}%</text>
            </>
          )}
        </box>
      )}
    </box>
  )
}

/**
 * Sub-character-precision progress bar using 9 Unicode block characters.
 *
 * Pattern from claude-code-source-main/src/components/design-system/ProgressBar.tsx
 * (BLOCKS table). Avoids the 10-step "█░" staircase and supports fractional
 * fills (e.g. 33.5% → "█████▌        ") without any graphics library.
 *
 * Width=20 typical. Renders exactly `width` columns.
 */

const BLOCKS = [' ', '▏', '▎', '▍', '▌', '▋', '▊', '▉', '█']

export interface ProgressBarProps {
  /** 0..1 */
  ratio: number
  width: number
  fg?: string
  bg?: string
}

export function ProgressBar(props: ProgressBarProps) {
  const r = Math.min(1, Math.max(0, props.ratio))
  const whole = Math.floor(r * props.width)
  const rem = r * props.width - whole
  const hasPartial = whole < props.width
  const mid = hasPartial ? BLOCKS[Math.floor(rem * BLOCKS.length)] : ''
  const emptyCount = props.width - whole - (hasPartial ? 1 : 0)
  const text = BLOCKS[8].repeat(whole) + mid + (emptyCount > 0 ? BLOCKS[0].repeat(emptyCount) : '')

  return (
    <text fg={props.fg} bg={props.bg}>
      {text}
    </text>
  )
}

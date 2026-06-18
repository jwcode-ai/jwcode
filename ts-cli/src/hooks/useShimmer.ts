import { useState, useEffect } from 'react';

export interface ShimmerChar {
  char: string;
  intensity: number; // 0..1
}

const START = Date.now();
const PERIOD_MS = 2000;
const BAND_HALF = 5;
const TICK_MS = 100;

function blendChannel(base: number, high: number, k: number): number {
  return Math.round(base + (high - base) * k);
}

/**
 * Blend two `#rrggbb` colors by intensity (0..1).
 * Exported for testing / advanced callers.
 */
export function blendColor(baseHex: string, highHex: string, intensity: number): string {
  const b = parseInt(baseHex.slice(1), 16);
  const h = parseInt(highHex.slice(1), 16);
  const br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
  const hr = (h >> 16) & 0xff, hg = (h >> 8) & 0xff, hb = h & 0xff;
  const k = Math.max(0, Math.min(1, intensity));
  const r = blendChannel(br, hr, k);
  const g = blendChannel(bg, hg, k);
  const bl = blendChannel(bb, hb, k);
  return '#' + [r, g, bl].map(v => v.toString(16).padStart(2, '0')).join('');
}

export function isTrueColor(): boolean {
  return process.env.COLORTERM === 'truecolor' || process.env.COLORTERM === '24bit';
}

/** Map intensity to a dim/normal/bold weight when truecolor is unavailable. */
export function intensityWeight(intensity: number): 'dim' | 'normal' | 'bold' {
  if (intensity < 0.33) return 'dim';
  if (intensity < 0.66) return 'normal';
  return 'bold';
}

/**
 * Split `text` into per-character spans with a sweeping intensity band.
 * When `active`, a cosine band (half-width 5) sweeps across the text every
 * `PERIOD_MS`. Drives its own re-render via a ticker so the band animates.
 * When inactive, returns spans with intensity 0.
 */
export function useShimmerSpans(text: string, active: boolean): ShimmerChar[] {
  const [, setTick] = useState(0);
  useEffect(() => {
    if (!active) return;
    const id = setInterval(() => setTick(t => (t + 1) % 1_000_000), TICK_MS);
    return () => clearInterval(id);
  }, [active]);

  // Computed every render (cheap). The ticker above forces re-renders so the
  // band position advances; without it the spans would be static.
  const chars = Array.from(text);
  if (!active || chars.length === 0) {
    return chars.map(char => ({ char, intensity: 0 }));
  }
  const now = Date.now() - START;
  const span = chars.length + 2 * BAND_HALF;
  const pos = (now / PERIOD_MS) * span;
  return chars.map((char, i) => {
    const dist = Math.abs(i - pos + BAND_HALF);
    const clamped = Math.min(dist, BAND_HALF);
    const intensity = 0.5 * (1 + Math.cos((Math.PI * clamped) / BAND_HALF));
    return { char, intensity };
  });
}

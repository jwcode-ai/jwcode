import { useState, useEffect } from 'react';

// Braille spinner frames (codex-style).
const FRAMES = [
  '\u280B', '\u2819', '\u2839', '\u2838', '\u283C',
  '\u2834', '\u2826', '\u2827', '\u2807', '\u280F',
];

/**
 * Returns the current Braille spinner character while `active`.
 * Drives its own re-render via an interval; cleanup on inactive/unmount.
 */
export function useSpinner(active: boolean, fps = 10): string {
  const [frame, setFrame] = useState(0);
  useEffect(() => {
    if (!active) return;
    const interval = Math.max(16, Math.floor(1000 / fps));
    const id = setInterval(() => setFrame(f => (f + 1) % FRAMES.length), interval);
    return () => clearInterval(id);
  }, [active, fps]);
  return active ? FRAMES[frame]! : '';
}

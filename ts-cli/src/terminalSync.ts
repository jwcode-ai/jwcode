/**
 * Terminal synchronized output (DEC 2026) wrapper.
 *
 * Wraps every process.stdout.write() in BSU (\x1b[?2026h) ... ESU (\x1b[?2026l)
 * so the terminal commits each frame atomically. Prevents intra-frame tearing
 * that produces visible flicker during streaming.
 *
 * Why this works for Ink 5 (which lacks cell-level diff and damage tracking):
 * - Ink 5 writes the entire non-Static subtree to stdout as a single ANSI
 *   string per render frame.
 * - Without BSU/ESU, terminals that have already painted the cursor can
 *   flicker mid-frame as Ink's single write is processed character-by-character.
 * - With BSU/ESU, the terminal buffers the entire BSU..ESU block and commits
 *   atomically on ESU.
 *
 * Detection: returns false on tmux (passes sequences through but breaks
 * atomicity), and on terminals that don't support DEC 2026.
 */

const BSU = '\x1b[?2026h';
const ESU = '\x1b[?2026l';

// Terminals known to support DEC 2026 synchronized output.
const KNOWN_SUPPORTED = new Set([
  'iTerm.app',
  'WezTerm',
  'WarpTerminal',
  'ghostty',
  'contour',
  'vscode',       // VS Code integrated terminal
  'alacritty',
]);

export function isSynchronizedOutputSupported(): boolean {
  // tmux passes through CSI sequences but does not preserve atomicity.
  if (process.env.TMUX) return false;

  const tp = process.env.TERM_PROGRAM || '';
  if (KNOWN_SUPPORTED.has(tp)) return true;

  // kitty / foot set distinctive TERM values
  const term = process.env.TERM || '';
  if (term.includes('kitty') || term.includes('foot')) return true;

  // Windows Terminal sets WT_SESSION
  if (process.env.WT_SESSION) return true;

  // VTE-based terminals ≥ 0.68 (GNOME Terminal, etc.) advertise support via
  // XTVERSION; we can't probe that synchronously, so fall back to a heuristic.
  if (tp === 'gnome-terminal' || tp === 'GNOME Terminal') return true;

  return false;
}

type WriteFn = (chunk: string | Uint8Array, ...args: unknown[]) => boolean;

let installed = false;
let origWrite: WriteFn | null = null;

/**
 * Install BSU/ESU wrapping on process.stdout.write. Idempotent — calling twice
 * is a no-op. Returns true if wrapping was installed, false if the terminal
 * does not support synchronized output.
 */
export function installSyncOutput(): boolean {
  if (installed) return true;
  if (!isSynchronizedOutputSupported()) return false;

  origWrite = process.stdout.write.bind(process.stdout) as WriteFn;

  const wrapped: WriteFn = (chunk, ...args) => {
    if (chunk === null || chunk === undefined) return origWrite!(chunk, ...args);

    if (typeof chunk === 'string') {
      if (chunk.length === 0) return origWrite!(chunk, ...args);
      return origWrite!(BSU + chunk + ESU, ...args);
    }

    // Uint8Array / Buffer
    const bytes = chunk as Uint8Array;
    if (bytes.byteLength === 0) return origWrite!(chunk, ...args);
    const bsuBuf = Buffer.from(BSU);
    const esuBuf = Buffer.from(ESU);
    const buf = Buffer.concat([bsuBuf, Buffer.isBuffer(bytes) ? bytes : Buffer.from(bytes), esuBuf]);
    return origWrite!(buf, ...args);
  };

  // Replace the write method on the stdout instance. We can't easily preserve
  // every overload signature, so cast through unknown.
  (process.stdout as unknown as { write: WriteFn }).write = wrapped;
  installed = true;
  return true;
}

/** Restore the original write. Used in tests. */
export function uninstallSyncOutput(): void {
  if (!installed || !origWrite) return;
  (process.stdout as unknown as { write: WriteFn }).write = origWrite;
  installed = false;
  origWrite = null;
}

/** @internal — exposed for tests. */
export function _isInstalled(): boolean {
  return installed;
}

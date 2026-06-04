/**
 * Paste buffer — stores pasted content so the UI can show a compact
 * summary ("[Pasted text #N +X chars]") instead of flooding the input line.
 *
 * The terminal wraps paste in \e[200~ ... \e[201~ (bracketed paste mode).
 * TextInput detects these markers, buffers the content here, and replaces
 * the visible input with a summary token.  On submit, expandPastes() swaps
 * the token back to the real content.
 */

const PASTE_TOKEN_RE = /\[Pasted text #(\d+) \+(\d+) chars(?:\s*\+(\d+) lines)?\]/g;

let pasteIdCounter = 0;
const pasteStore = new Map<number, string>();

export function storePaste(content: string): number {
  const id = ++pasteIdCounter;
  pasteStore.set(id, content);
  return id;
}

export function getPaste(id: number): string | undefined {
  return pasteStore.get(id);
}

export function clearPaste(id: number): void {
  pasteStore.delete(id);
}

/** Build the small summary label shown in the input line. */
export function pasteSummary(content: string): { label: string; id: number } {
  const id = storePaste(content);
  const lines = (content.match(/\n/g) || []).length + 1;
  const parts = [`Pasted text #${id} +${content.length} chars`];
  if (lines > 1) parts.push(`+${lines} lines`);
  return { id, label: `[${parts.join(' ')}]` };
}

/**
 * Replace "[Pasted text #N ...]" tokens in a string with their stored content.
 * If a paste ID is not found the token is left as-is.
 */
export function expandPastes(text: string): string {
  PASTE_TOKEN_RE.lastIndex = 0;
  return text.replace(PASTE_TOKEN_RE, (_match, idStr) => {
    const id = parseInt(idStr, 10);
    const content = pasteStore.get(id);
    return content !== undefined ? content : _match;
  });
}

/** Remove all stored pastes (e.g. on /clear). */
export function clearAllPastes(): void {
  pasteStore.clear();
  pasteIdCounter = 0;
}

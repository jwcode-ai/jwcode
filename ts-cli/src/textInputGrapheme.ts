/**
 * Grapheme-aware text utilities — cursor movement, word boundaries.
 * Uses Intl.Segmenter (Node 18+) for grapheme cluster segmentation.
 */

const segmenter = new Intl.Segmenter('en', { granularity: 'grapheme' });

/** Return [start, end) code-unit offsets for each grapheme cluster. */
export function graphemeOffsets(text: string): number[] {
  const offsets: number[] = [0];
  if (!text) return offsets;
  for (const seg of segmenter.segment(text)) {
    offsets.push(seg.index + seg.segment.length);
  }
  if (offsets[offsets.length - 1] !== text.length) {
    offsets.push(text.length);
  }
  return offsets;
}

/** Number of grapheme clusters in text. */
export function graphemeCount(text: string): number {
  let count = 0;
  for (const _seg of segmenter.segment(text)) count++;
  return count;
}

/** Convert code-unit position to grapheme cluster index. */
export function cursorToGrapheme(text: string, codeUnitPos: number): number {
  let count = 0;
  for (const seg of segmenter.segment(text)) {
    if (seg.index >= codeUnitPos) break;
    count++;
  }
  return count;
}

/** Convert grapheme cluster index to code-unit position. */
export function graphemeToCursor(text: string, graphemeIdx: number): number {
  let i = 0;
  for (const seg of segmenter.segment(text)) {
    if (i === graphemeIdx) return seg.index;
    i++;
  }
  return text.length;
}

/** Get the grapheme cluster string at a given grapheme index, or empty string. */
export function graphemeAt(text: string, graphemeIdx: number): string {
  let i = 0;
  for (const seg of segmenter.segment(text)) {
    if (i === graphemeIdx) return seg.segment;
    i++;
  }
  return '';
}

// ---- Word movement ----

const CJK_RE = /[一-鿿㐀-䶿豈-﫿]/;
const WORD_SEPARATORS = new Set(' \t\n\r!@#$%^&*()_+-=[]{}|;\':",.<>?/~`');

function isWordSep(ch: string): boolean {
  if (ch.length === 0) return true;
  if (CJK_RE.test(ch)) return true;
  if (ch.length === 1 && WORD_SEPARATORS.has(ch)) return true;
  return false;
}

/** Move cursor left one word boundary. Returns grapheme index. */
export function wordStartLeft(text: string, graphemeIdx: number): number {
  const offsets = graphemeOffsets(text);
  let pos = Math.min(graphemeIdx, graphemeCount(text));

  // Skip current word separators
  while (pos > 0) {
    const g = graphemeAt(text, pos - 1);
    if (!isWordSep(g)) break;
    pos--;
  }
  // Skip current word (non-separators)
  while (pos > 0) {
    const g = graphemeAt(text, pos - 1);
    if (isWordSep(g)) break;
    pos--;
  }
  return pos;
}

/** Move cursor right one word boundary. Returns grapheme index. */
export function wordEndRight(text: string, graphemeIdx: number): number {
  const total = graphemeCount(text);
  let pos = Math.min(graphemeIdx, total);

  // Skip current word separators
  while (pos < total) {
    const g = graphemeAt(text, pos);
    if (!isWordSep(g)) break;
    pos++;
  }
  // Skip current word (non-separators)
  while (pos < total) {
    const g = graphemeAt(text, pos);
    if (isWordSep(g)) break;
    pos++;
  }
  return Math.min(pos, total);
}

/** Delete word backward from cursor. Returns {kept, killed, newGraphemePos}. */
export function killWordBackward(text: string, graphemeIdx: number): { kept: string; killed: string; newGraphemePos: number } {
  const offsets = graphemeOffsets(text);
  const newGIdx = wordStartLeft(text, graphemeIdx);
  const killEnd = graphemeToCursor(text, graphemeIdx);
  const killStart = graphemeToCursor(text, newGIdx);
  const killed = text.slice(killStart, killEnd);
  const kept = text.slice(0, killStart) + text.slice(killEnd);
  return { kept, killed, newGraphemePos: newGIdx };
}

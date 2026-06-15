/**
 * Fuzzy matching — subsequence-based with scoring and match indices.
 * Pattern from codex fuzzy-match (rust utils).
 *
 * Score = window - needle length + prefix bonus.
 * Lower score is better. Prefix match at index 0 gets -100 bonus.
 */

export interface FuzzyResult {
  score: number;
  indices: number[];
}

/**
 * Case-insensitive subsequence match.
 * Returns null if no match, or {score, indices} where indices are
 * character positions in the original target string.
 */
export function fuzzyMatch(query: string, target: string): FuzzyResult | null {
  if (!query) return { score: Number.MAX_SAFE_INTEGER, indices: [] };
  if (!target) return null;

  const q = query.toLowerCase();
  const t = target.toLowerCase();

  let qi = 0;
  const indices: number[] = [];

  for (let ti = 0; ti < t.length && qi < q.length; ti++) {
    if (q[qi] === t[ti]) {
      indices.push(ti);
      qi++;
    }
  }

  if (qi < q.length) return null; // not all characters matched

  // Score computation
  const firstIdx = indices[0];
  const lastIdx = indices[indices.length - 1];
  const windowSize = lastIdx - firstIdx;
  const prefixBonus = firstIdx === 0 ? -100 : 0;
  const score = windowSize + prefixBonus;

  return { score, indices };
}

/**
 * Fuzzy filter an array of strings, returning results sorted by score.
 * @param query — the search query
 * @param targets — array of strings to search
 * @param extract — optional function to extract search text from each item
 * @param maxResults — maximum results to return (default 8)
 */
export function fuzzyFilter<T>(
  query: string,
  targets: T[],
  extract: (item: T) => string = (item: T) => String(item),
  maxResults = 8,
): Array<{ item: T; score: number; indices: number[] }> {
  if (!query.trim()) {
    // Return a default ordering for empty query — just return first N
    return targets.slice(0, maxResults).map(item => ({
      item,
      score: 0,
      indices: [],
    }));
  }

  const results: Array<{ item: T; score: number; indices: number[] }> = [];

  for (const item of targets) {
    const text = extract(item);
    const match = fuzzyMatch(query, text);
    if (match) {
      results.push({ item, score: match.score, indices: match.indices });
    }
  }

  // Sort by score ascending (lower is better)
  results.sort((a, b) => a.score - b.score);

  return results.slice(0, maxResults);
}

export function highlightSpans(text: string, indices: number[]): Array<{ text: string; highlighted: boolean }> {
  if (!indices.length) return [{ text, highlighted: false }]
  const spans: Array<{ text: string; highlighted: boolean }> = []
  const idxSet = new Set(indices)
  let i = 0
  while (i < text.length) {
    if (idxSet.has(i)) {
      let chunk = ""
      while (i < text.length && idxSet.has(i)) chunk += text[i++]
      spans.push({ text: chunk, highlighted: true })
    } else {
      let chunk = ""
      while (i < text.length && !idxSet.has(i)) chunk += text[i++]
      spans.push({ text: chunk, highlighted: false })
    }
  }
  return spans
}

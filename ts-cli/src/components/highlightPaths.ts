// Tiny utility: split text into segments, marking path-like tokens so the
// caller can color them with t.filePath.

export interface TextSegment {
  text: string;
  isPath: boolean;
}

// Matches:
//  - Windows drive paths:  C:\dir\file.ts
//  - POSIX absolute paths: /foo/bar.ts
//  - Relative multi-segment: src/foo/bar.ts
//  - Bare files with known extensions: bar.ts
const PATH_RE = new RegExp(
  '(?:' +
    '[A-Za-z]:\\\\[\\w.\\\\/-]+' +          // Windows drive path
    '|\\/[\\w./-]+' +                        // POSIX absolute path
    '|(?:[\\w.-]+\\/)+[\\w.-]+' +            // relative multi-segment
    '|[\\w.\\\\/-]+\\.(?:ts|tsx|js|jsx|mjs|cjs|py|md|json|java|rs|go|c|cpp|cc|h|hpp|sh|yml|yaml|toml|txt|html|css|xml|sql)' + // bare file with ext
  ')',
  'g',
);

export function highlightPaths(text: string): TextSegment[] {
  if (!text) return [];
  const segments: TextSegment[] = [];
  let last = 0;
  PATH_RE.lastIndex = 0;
  let m: RegExpExecArray | null;
  while ((m = PATH_RE.exec(text)) !== null) {
    if (m.index > last) {
      segments.push({ text: text.slice(last, m.index), isPath: false });
    }
    segments.push({ text: m[0], isPath: true });
    last = m.index + m[0].length;
    if (m[0].length === 0) { // guard against zero-width loops
      PATH_RE.lastIndex++;
    }
  }
  if (last < text.length) {
    segments.push({ text: text.slice(last), isPath: false });
  }
  return segments;
}

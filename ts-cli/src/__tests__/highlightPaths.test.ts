import { describe, it, expect } from 'vitest';
import { highlightPaths } from '../components/highlightPaths.js';

describe('highlightPaths', () => {
  it('marks posix and windows paths separately from prose', () => {
    const segs = highlightPaths('/foo/bar.ts and C:\\x\\y.py');
    const paths = segs.filter(s => s.isPath).map(s => s.text);
    expect(paths).toEqual(['/foo/bar.ts', 'C:\\x\\y.py']);
  });

  it('produces alternating path/non-path segments', () => {
    const segs = highlightPaths('/foo/bar.ts and C:\\x\\y.py');
    expect(segs.length).toBe(3);
    expect(segs[0]!.isPath).toBe(true);
    expect(segs[1]!.isPath).toBe(false);
    expect(segs[1]!.text).toBe(' and ');
    expect(segs[2]!.isPath).toBe(true);
  });

  it('matches bare files with known extensions', () => {
    const segs = highlightPaths('edited src/app.tsx then README.md');
    const paths = segs.filter(s => s.isPath).map(s => s.text);
    expect(paths).toContain('src/app.tsx');
    expect(paths).toContain('README.md');
  });

  it('returns empty for empty input', () => {
    expect(highlightPaths('')).toEqual([]);
  });

  it('returns a single non-path segment for plain text', () => {
    const segs = highlightPaths('hello world');
    expect(segs.length).toBe(1);
    expect(segs[0]!.isPath).toBe(false);
  });
});

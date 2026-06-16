import { describe, it, expect } from 'vitest';
import { pasteSummary, expandPastes, clearAllPastes, getPaste } from '../pasteBuffer.js';

describe('pasteBuffer', () => {
  it('stores paste and returns summary label', () => {
    const { id, label } = pasteSummary('hello world');
    expect(id).toBeGreaterThan(0);
    expect(label).toContain('Pasted text');
    expect(label).toContain('+11 chars');
  });

  it('summary includes line count for multi-line paste', () => {
    const { label } = pasteSummary('line1\nline2\nline3');
    expect(label).toContain('+3 lines');
  });

  it('expandPastes replaces token with stored content', () => {
    clearAllPastes();
    const { id } = pasteSummary('some pasted code');
    const text = `Review this: [Pasted text #${id} +17 chars]`;
    const expanded = expandPastes(text);
    expect(expanded).toBe('Review this: some pasted code');
  });

  it('expandPastes handles multiple paste tokens', () => {
    clearAllPastes();
    const a = pasteSummary('AAA');
    const b = pasteSummary('BBB');
    const text = `x [Pasted text #${a.id} +3 chars] y [Pasted text #${b.id} +3 chars]`;
    expect(expandPastes(text)).toBe('x AAA y BBB');
  });

  it('expandPastes leaves unknown tokens as-is', () => {
    const result = expandPastes('[Pasted text #99999 +5 chars]');
    expect(result).toBe('[Pasted text #99999 +5 chars]');
  });

  it('clearAllPastes removes all stored pastes', () => {
    const { id } = pasteSummary('test');
    clearAllPastes();
    expect(getPaste(id)).toBeUndefined();
  });
});

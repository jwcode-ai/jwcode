import { describe, it, expect } from 'vitest';
import { estimateTokens } from '../solid/components/TextInput.js';

describe('estimateTokens', () => {
  it('empty string is 0', () => {
    expect(estimateTokens('')).toBe(0);
  });

  it('English text: ~4 chars per token', () => {
    // 40 English chars → ~10 tokens
    const tokens = estimateTokens('Hello world this is a test message here');
    expect(tokens).toBeGreaterThan(6);
    expect(tokens).toBeLessThan(20);
  });

  it('Chinese text: ~1.5 chars per token', () => {
    // 15 Chinese chars → ~10 tokens
    const tokens = estimateTokens('这是一段中文测试文字用于验证分词估算');
    expect(tokens).toBeGreaterThan(8);
    expect(tokens).toBeLessThan(15);
  });

  it('mixed text combines both ratios', () => {
    const tokens = estimateTokens('Hello 世界 this 测试 works');
    expect(tokens).toBeGreaterThan(0);
  });

  it('code block is mostly other chars', () => {
    const code = 'function hello() { return 42; }';
    const tokens = estimateTokens(code);
    expect(tokens).toBeGreaterThan(2);
    expect(tokens).toBeLessThan(15);
  });

  it('100K token threshold is detectable', () => {
    // Very rough: 400K chars ≈ 100K tokens
    const long = 'x'.repeat(400_000);
    const tokens = estimateTokens(long);
    expect(tokens).toBeGreaterThanOrEqual(100_000);
  });

  it('handles grapheme clusters (emoji + ZWJ sequences)', () => {
    // Each emoji is a single grapheme but multi-codepoint; the function
    // iterates by code unit, so an emoji counts as 2 chars (surrogate pair).
    // Just assert it returns a positive number, not throw.
    const tokens = estimateTokens('👨‍👩‍👧‍👦 family');
    expect(tokens).toBeGreaterThan(0);
  });
});

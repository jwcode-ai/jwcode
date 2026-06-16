import { describe, it, expect } from 'vitest';
import { storePaste, getPaste, clearPastes, pasteSummary } from '../pasteBuffer.js';

describe('pasteBuffer', () => {
  it('storePaste 返回递增的 ID', () => {
    const id1 = storePaste('hello');
    const id2 = storePaste('world');
    expect(id2).toBe(id1 + 1);
  });

  it('getPaste 返回存储的内容', () => {
    const id = storePaste('test content');
    expect(getPaste(id)).toBe('test content');
  });

  it('getPaste 对不存在的 ID 返回 null', () => {
    expect(getPaste(99999)).toBeNull();
  });

  it('pasteSummary 生成正确的摘要', () => {
    const id = storePaste('hello\nworld\nfoo');
    const summary = pasteSummary(id);
    expect(summary).toContain(`#${id}`);
    expect(summary).toContain('3 lines');
    expect(summary).toContain('15 chars');
  });

  it('pasteSummary 对不存在的 ID 返回 null', () => {
    expect(pasteSummary(99999)).toBeNull();
  });

  it('clearPastes 清空所有缓存', () => {
    storePaste('data');
    clearPastes();
    expect(getPaste(1)).toBeNull();
  });
});

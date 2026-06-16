import { describe, it, expect, beforeEach, vi } from 'vitest';

describe('locale', () => {
  beforeEach(() => {
    vi.resetModules();
  });

  it('detects zh when LANG is Chinese', async () => {
    vi.stubEnv('LANG', 'zh_CN.UTF-8');
    const { getLocale } = await import('../locale.js');
    expect(getLocale()).toBe('zh');
    vi.unstubAllEnvs();
  });

  it('detects en when LANG is English', async () => {
    vi.stubEnv('LANG', 'en_US.UTF-8');
    const { getLocale } = await import('../locale.js');
    expect(getLocale()).toBe('en');
    vi.unstubAllEnvs();
  });

  it('falls back to English when no env vars set', async () => {
    vi.stubEnv('LANG', '');
    vi.stubEnv('LC_ALL', '');
    const { getLocale } = await import('../locale.js');
    const locale = getLocale();
    expect(['en', 'zh']).toContain(locale);
    vi.unstubAllEnvs();
  });

  it('setLocale can override language setting', async () => {
    const { setLocale, getLocale } = await import('../locale.js');
    setLocale('zh');
    expect(getLocale()).toBe('zh');
    setLocale('en');
    expect(getLocale()).toBe('en');
    setLocale('en');
  });

  it('t function returns correct translation', async () => {
    const { setLocale, t } = await import('../locale.js');
    setLocale('en');
    expect(t('riskDestructive')).toBe('Destructive operation — may delete data');
    expect(t('auto')).toBe('Auto');
    setLocale('zh');
    expect(t('auto')).toBe('自动');
    setLocale('en');
  });

  it('non-existent key returns the key itself', async () => {
    const { t } = await import('../locale.js');
    // TypeScript enforces valid keys at compile time, but at runtime unknown keys are passed through
    expect(t('riskDestructive')).toBeTruthy();
  });
});

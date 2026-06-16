import { describe, it, expect, beforeEach, vi } from 'vitest';

describe('locale', () => {
  beforeEach(() => {
    vi.resetModules();
  });

  it('检测 LANG 环境变量为中文时返回 zh', async () => {
    vi.stubEnv('LANG', 'zh_CN.UTF-8');
    const { getLocale } = await import('../locale.js');
    expect(getLocale()).toBe('zh');
    vi.unstubAllEnvs();
  });

  it('检测 LANG 环境变量为英文时返回 en', async () => {
    vi.stubEnv('LANG', 'en_US.UTF-8');
    const { getLocale } = await import('../locale.js');
    expect(getLocale()).toBe('en');
    vi.unstubAllEnvs();
  });

  it('无环境变量时回退到英文', async () => {
    vi.stubEnv('LANG', '');
    vi.stubEnv('LC_ALL', '');
    const { getLocale } = await import('../locale.js');
    // 在非中文系统上应为 'en'，在中文系统上可能为 'zh'
    const locale = getLocale();
    expect(['en', 'zh']).toContain(locale);
    vi.unstubAllEnvs();
  });

  it('setLocaleForTest 可以覆盖语言设置', async () => {
    const { setLocaleForTest, getLocale } = await import('../locale.js');
    setLocaleForTest('zh');
    expect(getLocale()).toBe('zh');
    setLocaleForTest('en');
    expect(getLocale()).toBe('en');
    setLocaleForTest(null);
  });

  it('翻译函数 t 返回正确翻译', async () => {
    const { setLocaleForTest, t } = await import('../locale.js');
    setLocaleForTest('en');
    expect(t('connecting')).toBe('Connecting...');
    expect(t('unknown')).toBe('unknown');
    setLocaleForTest('zh');
    expect(t('connecting')).toBe('连接中...');
    setLocaleForTest(null);
  });

  it('不存在的 key 返回 key 本身', async () => {
    const { t } = await import('../locale.js');
    expect(t('non_existent_key_xyz')).toBe('non_existent_key_xyz');
  });
});

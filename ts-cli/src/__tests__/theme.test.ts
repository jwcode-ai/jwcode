import { describe, it, expect, afterEach } from 'vitest';
import { getTheme, setTheme, t } from '../theme.js';

describe('theme', () => {
  afterEach(() => {
    setTheme('dark');
  });

  it('default theme is dark', () => {
    const theme = getTheme();
    expect(theme.primary).toBe('#d77757');
    expect(theme.success).toBe('#4eba65');
    expect(theme.error).toBe('#ff6b80');
  });

  it('setTheme changes current theme', () => {
    setTheme('light');
    const theme = getTheme();
    expect(theme.muted).toBe('#656d76');
  });

  it('all required color keys exist', () => {
    const required = [
      'bg', 'text', 'muted', 'border', 'brand',
      'primary', 'success', 'warning', 'error', 'info',
      'user', 'assistant', 'system', 'tool', 'thinking',
      'plan', 'auto', 'connected', 'disconnected',
      'diffAdded', 'diffRemoved', 'diffAddedBg', 'diffRemovedBg',
      'diffHeader', 'diffFileHeader', 'diffPlaceholder',
    ];
    const theme = getTheme();
    for (const key of required) {
      expect(theme).toHaveProperty(key);
    }
  });

  it('module-level t exports dark theme initially', () => {
    expect(t.primary).toBe('#d77757');
  });

  it('theme switches preserve all keys', () => {
    setTheme('light');
    const light = getTheme();
    setTheme('dark');
    const dark = getTheme();
    expect(Object.keys(light)).toEqual(Object.keys(dark));
  });

  it('diff tokens have distinct values per theme', () => {
    const dark = getTheme();
    expect(dark.diffAdded).toBe('#38a660');
    expect(dark.diffRemoved).toBe('#b3596b');

    setTheme('light');
    const light = getTheme();
    expect(light.diffAdded).toBe('#116329');
    expect(light.diffRemoved).toBe('#cf222e');
  });
});


import { describe, it, expect, afterEach, vi } from 'vitest';
import { getTheme, setTheme, t, detectTerminalBg, reapplyTheme } from '../theme.js';
import type { ThemeName } from '../theme.js';

describe('theme', () => {
  afterEach(() => {
    setTheme('dark');
    vi.unstubAllEnvs();
  });

  it('default theme is dark', () => {
    const theme = getTheme();
    expect(theme.primary).toBe('cyan');
    expect(theme.success).toBe('green');
    expect(theme.error).toBe('red');
  });

  it('setTheme changes current theme', () => {
    setTheme('light');
    const theme = getTheme();
    expect(theme.muted).toBe('blackBright');
  });

  it('all required color keys exist (incl. semantic keys)', () => {
    const required = [
      'bg', 'text', 'muted', 'border',
      'primary', 'success', 'warning', 'error', 'info',
      'user', 'assistant', 'system', 'tool', 'thinking',
      'plan', 'auto', 'connected', 'disconnected',
      // semantic
      'toolName', 'toolArgs', 'toolResult', 'filePath',
      'stepTitle', 'stepAction', 'stepThought',
    ];
    const theme = getTheme();
    for (const key of required) {
      expect(theme).toHaveProperty(key);
    }
  });

  it('semantic keys have distinct values', () => {
    const theme = getTheme();
    expect(theme.toolName).toBeTruthy();
    expect(theme.filePath).toBeTruthy();
    expect(theme.toolResult).toBe('green');
  });

  it('module-level t exports dark theme initially', () => {
    expect(t.primary).toBe('cyan');
  });

  it('theme switches preserve all keys', () => {
    setTheme('light');
    const light = getTheme();
    setTheme('dark');
    const dark = getTheme();
    expect(Object.keys(light)).toEqual(Object.keys(dark));
  });

  it('detectTerminalBg returns dark on non-TTY', () => {
    // vitest runs without a TTY on stdout.
    expect(detectTerminalBg()).toBe('dark');
  });

  it('adaptive priority: JWCODE_THEME env overrides detection', () => {
    // Non-TTY detection would return dark; env must win.
    vi.stubEnv('JWCODE_THEME', 'light');
    reapplyTheme();
    expect(getTheme().muted).toBe('blackBright');

    vi.stubEnv('JWCODE_THEME', 'dark');
    reapplyTheme();
    expect(getTheme().muted).toBe('grey');
  });

  it('default falls back to dark when no env and non-TTY', () => {
    vi.stubEnv('JWCODE_THEME', '');
    reapplyTheme();
    expect(getTheme().primary).toBe('cyan');
  });
});

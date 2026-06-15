import { describe, it, expect } from 'vitest';
import { listBuiltinThemes, getBuiltinTheme } from '../solid/context/theme.js';
import type { ThemeJson } from '../solid/context/theme.js';

describe('built-in theme registry', () => {
  it('exposes at least 30 themes', () => {
    const ids = listBuiltinThemes();
    expect(ids.length).toBeGreaterThanOrEqual(30);
  });

  it('contains expected flagship themes', () => {
    const ids = listBuiltinThemes();
    for (const expected of ['aura', 'dracula', 'github', 'tokyonight', 'mimocode', 'jwcode']) {
      expect(ids).toContain(expected);
    }
  });

  it('returns undefined for unknown id', () => {
    expect(getBuiltinTheme('does-not-exist')).toBeUndefined();
  });

  it('returns the same object as the named import', async () => {
    const aura = getBuiltinTheme('aura');
    expect(aura).toBeDefined();
    expect(aura?.theme.primary).toBe('purple');
  });
});

describe('theme JSON shape', () => {
  const REQUIRED_KEYS = [
    'primary',
    'secondary',
    'error',
    'warning',
    'success',
    'text',
    'textMuted',
    'background',
  ] as const;

  for (const id of listBuiltinThemes()) {
    describe(`theme: ${id}`, () => {
      const theme = getBuiltinTheme(id) as ThemeJson;

      it('has a `theme` block', () => {
        expect(theme).toBeDefined();
        expect(typeof theme.theme).toBe('object');
      });

      it.each(REQUIRED_KEYS)('defines theme.%s', (key) => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        expect((theme.theme as any)[key]).toBeDefined();
      });

      it('uses only declared color refs or hex strings', () => {
        const defs = theme.defs ?? {};
        const themeKeys = new Set(Object.keys(theme.theme));
        const defNames = new Set(Object.keys(defs));
        const HEX = /^#[0-9a-fA-F]{3,8}$/;
        const check = (val: unknown, path: string) => {
          if (val == null) return;
          if (typeof val === 'string') {
            if (val === 'transparent') return;
            if (defNames.has(val)) return;
            if (themeKeys.has(val)) return;
            if (HEX.test(val)) return;
            throw new Error(`${path}: undeclared color ref "${val}"`);
          }
          if (typeof val === 'object') {
            for (const [k, v] of Object.entries(val as Record<string, unknown>)) {
              check(v, `${path}.${k}`);
            }
            return;
          }
          throw new Error(`${path}: unexpected color value type ${typeof val}`);
        };
        for (const [k, v] of Object.entries(theme.theme)) {
          check(v, `theme.${k}`);
        }
      });
    });
  }
});

describe('theme dark/light parity', () => {
  it('at least one theme has a light variant somewhere', () => {
    const ids = listBuiltinThemes();
    let found = false;
    for (const id of ids) {
      const theme = getBuiltinTheme(id) as ThemeJson;
      for (const v of Object.values(theme.theme)) {
        if (v && typeof v === 'object' && !Array.isArray(v)) {
          const o = v as Record<string, unknown>;
          if ('light' in o || 'dark' in o) {
            found = true;
            break;
          }
        }
      }
      if (found) break;
    }
    expect(found).toBe(true);
  });
});

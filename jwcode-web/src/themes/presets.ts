import { CustomThemeColors } from '../types';

export interface ThemePreset {
  id: string;
  nameKey: string;
  descriptionKey: string;
  dark: CustomThemeColors;
  light: CustomThemeColors;
}

/**
 * 10 curated theme presets.
 * Each preset has both dark and light variants, so switching mode
 * auto-switches to the correct color set.
 */

export const THEME_PRESETS: Record<string, ThemePreset> = {
  // ── GitHub (neutral) — current default, backward compatible ──
  github: {
    id: 'github',
    nameKey: 'settings.themePresetGithub',
    descriptionKey: 'settings.themePresetGithubDesc',
    dark: {
      bg: '#0d1117',
      surface: '#161b22',
      border: '#30363d',
      text: '#c9d1d9',
      muted: '#8b949e',
      accentBlue: '#58a6ff',
      accentGreen: '#238636',
      accentRed: '#f85149',
      accentYellow: '#d29922',
      accentPurple: '#a855f7',
    },
    light: {
      bg: '#ffffff',
      surface: '#f6f8fa',
      border: '#d0d7de',
      text: '#24292f',
      muted: '#57606a',
      accentBlue: '#0969da',
      accentGreen: '#1a7f37',
      accentRed: '#cf222e',
      accentYellow: '#9a6700',
      accentPurple: '#8250df',
    },
  },

  // ── Cherry Studio — blue gradient professional ──
  cherry: {
    id: 'cherry',
    nameKey: 'settings.themePresetCherry',
    descriptionKey: 'settings.themePresetCherryDesc',
    dark: {
      bg: '#0f1419',
      surface: '#1a2332',
      border: '#2d3f50',
      text: '#e1e8ed',
      muted: '#8899aa',
      accentBlue: '#01a9ff',
      accentGreen: '#00c853',
      accentRed: '#ff5252',
      accentYellow: '#ffab00',
      accentPurple: '#7c4dff',
    },
    light: {
      bg: '#f5f9ff',
      surface: '#ffffff',
      border: '#cce0ff',
      text: '#1a2332',
      muted: '#5c6f87',
      accentBlue: '#0160ff',
      accentGreen: '#00a844',
      accentRed: '#e53935',
      accentYellow: '#f9a825',
      accentPurple: '#651fff',
    },
  },

  // ── Ocean — deep blue/cyan cool ──
  ocean: {
    id: 'ocean',
    nameKey: 'settings.themePresetOcean',
    descriptionKey: 'settings.themePresetOceanDesc',
    dark: {
      bg: '#0b1e2e',
      surface: '#122b3d',
      border: '#1e4057',
      text: '#d4e6f5',
      muted: '#7a9bb5',
      accentBlue: '#00bcd4',
      accentGreen: '#26a69a',
      accentRed: '#ef5350',
      accentYellow: '#ffca28',
      accentPurple: '#ab47bc',
    },
    light: {
      bg: '#edf7fc',
      surface: '#ffffff',
      border: '#b3d9e8',
      text: '#0b1e2e',
      muted: '#4f7a94',
      accentBlue: '#00838f',
      accentGreen: '#00897b',
      accentRed: '#d32f2f',
      accentYellow: '#f57f17',
      accentPurple: '#8e24aa',
    },
  },

  // ── Mint — teal/mint green cool ──
  mint: {
    id: 'mint',
    nameKey: 'settings.themePresetMint',
    descriptionKey: 'settings.themePresetMintDesc',
    dark: {
      bg: '#0d1f1c',
      surface: '#162d28',
      border: '#264b41',
      text: '#d1f0e8',
      muted: '#7dafa3',
      accentBlue: '#26c6da',
      accentGreen: '#66bb6a',
      accentRed: '#ef5350',
      accentYellow: '#ffd54f',
      accentPurple: '#ad8aff',
    },
    light: {
      bg: '#edf7f4',
      surface: '#ffffff',
      border: '#b8d9cf',
      text: '#0d1f1c',
      muted: '#4a7f72',
      accentBlue: '#0097a7',
      accentGreen: '#388e3c',
      accentRed: '#d32f2f',
      accentYellow: '#f9a825',
      accentPurple: '#7c4dff',
    },
  },

  // ── Lavender — soft purple cool ──
  lavender: {
    id: 'lavender',
    nameKey: 'settings.themePresetLavender',
    descriptionKey: 'settings.themePresetLavenderDesc',
    dark: {
      bg: '#1a1428',
      surface: '#261f38',
      border: '#3c3354',
      text: '#e2daf2',
      muted: '#9387b5',
      accentBlue: '#86bef5',
      accentGreen: '#81c784',
      accentRed: '#ef9a9a',
      accentYellow: '#ffe082',
      accentPurple: '#ce93d8',
    },
    light: {
      bg: '#f5f0ff',
      surface: '#ffffff',
      border: '#d4c8e8',
      text: '#1a1428',
      muted: '#6c5b8a',
      accentBlue: '#1565c0',
      accentGreen: '#2e7d32',
      accentRed: '#c62828',
      accentYellow: '#f57f17',
      accentPurple: '#7b1fa2',
    },
  },

  // ── Midnight — deep navy, minimal, high contrast ──
  midnight: {
    id: 'midnight',
    nameKey: 'settings.themePresetMidnight',
    descriptionKey: 'settings.themePresetMidnightDesc',
    dark: {
      bg: '#0a0e1a',
      surface: '#111726',
      border: '#1e2740',
      text: '#c8d2e8',
      muted: '#6a7596',
      accentBlue: '#4fc3f7',
      accentGreen: '#4caf50',
      accentRed: '#e57373',
      accentYellow: '#ffd54f',
      accentPurple: '#b39ddb',
    },
    light: {
      bg: '#eef1f7',
      surface: '#ffffff',
      border: '#c0c9dc',
      text: '#0a0e1a',
      muted: '#4f5a78',
      accentBlue: '#0288d1',
      accentGreen: '#2e7d32',
      accentRed: '#d32f2f',
      accentYellow: '#f57f17',
      accentPurple: '#5e35b1',
    },
  },

  // ── Sunset — warm orange/golden ──
  sunset: {
    id: 'sunset',
    nameKey: 'settings.themePresetSunset',
    descriptionKey: 'settings.themePresetSunsetDesc',
    dark: {
      bg: '#1e1410',
      surface: '#2a1d16',
      border: '#443124',
      text: '#f0dfcf',
      muted: '#b8957a',
      accentBlue: '#5fa5e8',
      accentGreen: '#81c784',
      accentRed: '#ff7043',
      accentYellow: '#ffb74d',
      accentPurple: '#e0a0e0',
    },
    light: {
      bg: '#fff6f0',
      surface: '#ffffff',
      border: '#f0d5c0',
      text: '#2a1d16',
      muted: '#8a6e5a',
      accentBlue: '#1976d2',
      accentGreen: '#388e3c',
      accentRed: '#e64a19',
      accentYellow: '#ef6c00',
      accentPurple: '#8e24aa',
    },
  },

  // ── Forest — natural green, earthy ──
  forest: {
    id: 'forest',
    nameKey: 'settings.themePresetForest',
    descriptionKey: 'settings.themePresetForestDesc',
    dark: {
      bg: '#0e1a12',
      surface: '#172a1d',
      border: '#2a4733',
      text: '#d1e8d6',
      muted: '#7da684',
      accentBlue: '#5bb8e8',
      accentGreen: '#66bb6a',
      accentRed: '#ef9a9a',
      accentYellow: '#e6c35c',
      accentPurple: '#ae8ad9',
    },
    light: {
      bg: '#f0f7f0',
      surface: '#ffffff',
      border: '#b8d9be',
      text: '#0e1a12',
      muted: '#477a50',
      accentBlue: '#1565c0',
      accentGreen: '#2e7d32',
      accentRed: '#c62828',
      accentYellow: '#827717',
      accentPurple: '#6a1b9a',
    },
  },

  // ── Candy — bright multi-color, vibrant ──
  candy: {
    id: 'candy',
    nameKey: 'settings.themePresetCandy',
    descriptionKey: 'settings.themePresetCandyDesc',
    dark: {
      bg: '#16101a',
      surface: '#22182a',
      border: '#3d284a',
      text: '#f0def5',
      muted: '#b28dc2',
      accentBlue: '#5fc3f5',
      accentGreen: '#81e080',
      accentRed: '#ff6b6b',
      accentYellow: '#ffd93d',
      accentPurple: '#e066ff',
    },
    light: {
      bg: '#faf0ff',
      surface: '#ffffff',
      border: '#e0cce8',
      text: '#1a0a24',
      muted: '#7a5c8a',
      accentBlue: '#1976d2',
      accentGreen: '#388e3c',
      accentRed: '#d32f2f',
      accentYellow: '#f9a825',
      accentPurple: '#8e24aa',
    },
  },

  // ── Nord — arctic blue, popular well-designed palette ──
  nord: {
    id: 'nord',
    nameKey: 'settings.themePresetNord',
    descriptionKey: 'settings.themePresetNordDesc',
    dark: {
      bg: '#2e3440',
      surface: '#3b4252',
      border: '#4c566a',
      text: '#eceff4',
      muted: '#81a1c1',
      accentBlue: '#88c0d0',
      accentGreen: '#a3be8c',
      accentRed: '#bf616a',
      accentYellow: '#ebcb8b',
      accentPurple: '#b48ead',
    },
    light: {
      bg: '#f5f7fa',
      surface: '#ffffff',
      border: '#d8dee9',
      text: '#2e3440',
      muted: '#5e6d85',
      accentBlue: '#5e81ac',
      accentGreen: '#6f9e6f',
      accentRed: '#bf616a',
      accentYellow: '#b48d56',
      accentPurple: '#8f6f9e',
    },
  },
};

/** Default preset ID (backward compatible with current hardcoded defaults) */
export const DEFAULT_PRESET_ID = 'github';

/**
 * Get the effective theme colors for a given preset and mode,
 * optionally merging custom overrides on top.
 */
export function getThemeColors(
  presetId: string,
  mode: 'dark' | 'light',
  overrides?: Partial<CustomThemeColors>,
): CustomThemeColors {
  const active = THEME_PRESETS[presetId] || THEME_PRESETS[DEFAULT_PRESET_ID] as ThemePreset | undefined;
  if (!active) {
    // Ultimate fallback — should never happen since DEFAULT_PRESET_ID is always registered
    return { bg: '#0d1117', surface: '#161b22', border: '#30363d', text: '#c9d1d9', muted: '#8b949e', accentBlue: '#58a6ff', accentGreen: '#238636', accentRed: '#f85149', accentYellow: '#d29922', accentPurple: '#a855f7' };
  }
  const colors = { ...(mode === 'dark' ? active.dark : active.light) };
  if (overrides) {
    Object.assign(colors, overrides);
  }
  return colors;
}

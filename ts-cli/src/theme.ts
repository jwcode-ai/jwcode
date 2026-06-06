/**
 * Color theme for components. Switch by setting JWCODE_THEME env or config.
 * Custom colors can be set via JWCODE_THEME_COLORS env var as JSON.
 *
 * Design inspiration: Claude Code terminal UI color palette.
 *   brand orange:  rgb(215, 119, 87)  → #d77757
 *   text white:    rgb(255, 255, 255)  → #ffffff
 *   muted gray:    rgb(153, 153, 153)  → #999999
 *   subtle border: rgb(80, 80, 80)     → #505050
 *   success green: rgb(78, 186, 101)   → #4eba65
 *   error red:     rgb(255, 107, 128)  → #ff6b80
 *   warning amber: rgb(255, 193, 7)    → #ffc107
 *   suggestion:    rgb(177, 185, 249)  → #b1b9f9
 *   planMode sage: rgb(72, 150, 140)   → #48968c
 *   fastMode:      rgb(255, 120, 20)   → #ff7814
 */
export type ThemeName = 'dark' | 'light';

export interface ThemeColors {
  bg: string;
  text: string;
  muted: string;
  border: string;
  brand: string;
  primary: string;
  success: string;
  warning: string;
  error: string;
  info: string;
  user: string;
  assistant: string;
  system: string;
  tool: string;
  thinking: string;
  plan: string;
  auto: string;
  connected: string;
  disconnected: string;
  // Diff-specific tokens
  diffAdded: string;
  diffRemoved: string;
  diffAddedBg: string;
  diffRemovedBg: string;
  diffHeader: string;
  diffFileHeader: string;
  diffPlaceholder: string;
}

type DeepPartial<T> = { [P in keyof T]?: T[P] extends string ? string : DeepPartial<T[P]> };

const dark: ThemeColors = {
  bg: '',
  text: '#ffffff',
  muted: '#999999',
  border: '#505050',
  brand: '#d77757',
  primary: '#d77757',
  success: '#4eba65',
  warning: '#ffc107',
  error: '#ff6b80',
  info: '#b1b9f9',
  user: '#7ab4e8',
  assistant: '#ffffff',
  system: '#ff6b80',
  tool: '#b1b9f9',
  thinking: '#999999',
  plan: '#48968c',
  auto: '#ff7814',
  connected: '#4eba65',
  disconnected: '#ff6b80',
  diffAdded: '#38a660',
  diffRemoved: '#b3596b',
  diffAddedBg: '#225c2b',
  diffRemovedBg: '#7a2936',
  diffHeader: '#b1b9f9',
  diffFileHeader: '#d77757',
  diffPlaceholder: '#505050',
};

const light: ThemeColors = {
  ...dark,
  text: '#1f2328',
  assistant: '#1f2328',
  muted: '#656d76',
  border: '#d0d7de',
  primary: '#cf5a2f',
  diffAdded: '#116329',
  diffRemoved: '#cf222e',
  diffAddedBg: '#dafbe1',
  diffRemovedBg: '#ffebe9',
  diffHeader: '#0550ae',
  diffFileHeader: '#cf5a2f',
  diffPlaceholder: '#6e7781',
};

const themes: Record<ThemeName, ThemeColors> = { dark, light };

function detectTheme(): ThemeName {
  if (process.env.JWCODE_THEME === 'light') return 'light';
  return 'dark';
}

function loadCustomColors(): Partial<ThemeColors> | null {
  try {
    const raw = process.env.JWCODE_THEME_COLORS;
    if (raw) return JSON.parse(raw) as Partial<ThemeColors>;
  } catch {}
  return null;
}

let current: ThemeName = detectTheme();
let customColors: Partial<ThemeColors> | null = loadCustomColors();

export function getTheme(): ThemeColors {
  const base = themes[current];
  if (!customColors) return base;
  return { ...base, ...customColors };
}

export function setTheme(name: ThemeName): void {
  current = name;
}

export function setCustomColors(colors: Partial<ThemeColors> | null): void {
  customColors = colors;
}

export const t = getTheme();


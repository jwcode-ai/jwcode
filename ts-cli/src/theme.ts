// Color theme for components. Switch by setting JWCODE_THEME env or config.
// Custom colors can be set via JWCODE_THEME_COLORS env var as JSON.
export type ThemeName = 'dark' | 'light';

export interface ThemeColors {
  bg: string;
  text: string;
  muted: string;
  border: string;
  primary: string;
  success: string;
  warning: string;
  error: string;
  info: string;
  brand: string;
  user: string;
  assistant: string;
  system: string;
  tool: string;
  thinking: string;
  plan: string;
  auto: string;
  connected: string;
  disconnected: string;
}

type DeepPartial<T> = { [P in keyof T]?: T[P] extends string ? string : DeepPartial<T[P]> };

const dark: ThemeColors = {
  bg: '',
  text: '',
  muted: 'grey',
  border: 'grey',
  primary: 'cyan',
  success: 'green',
  warning: 'yellow',
  error: 'red',
  info: 'blue',
  brand: 'cyan',
  user: 'green',
  assistant: '',
  system: 'red',
  tool: 'magenta',
  thinking: 'grey',
  plan: 'yellow',
  auto: 'magenta',
  connected: 'green',
  disconnected: 'red',
};

const light: ThemeColors = {
  ...dark,
  muted: 'blackBright',
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

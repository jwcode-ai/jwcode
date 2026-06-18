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
  // Semantic keys (codex-style)
  toolName: string;
  toolArgs: string;
  toolResult: string;
  filePath: string;
  stepTitle: string;
  stepAction: string;
  stepThought: string;
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
  toolName: 'magentaBright',
  toolArgs: 'cyan',
  toolResult: 'green',
  filePath: 'blueBright',
  stepTitle: 'yellow',
  stepAction: 'yellow',
  stepThought: 'blue',
};

const light: ThemeColors = {
  ...dark,
  muted: 'blackBright',
  toolName: 'magenta',
  filePath: 'blue',
};

const themes: Record<ThemeName, ThemeColors> = { dark, light };

/**
 * Detect terminal background lightness via the OSC 11 query.
 * Returns 'light' or 'dark'. Falls back to 'dark' on any failure or non-TTY.
 * On Windows the synchronous stdin read is unreliable (console handles block),
 * so the OSC query is skipped there and we fall back to 'dark'.
 */
export function detectTerminalBg(): 'light' | 'dark' {
  if (!process.stdout.isTTY) return 'dark';
  if (process.platform === 'win32') return 'dark';
  try {
    const fs = require('node:fs');
    const fd = 0;
    process.stdout.write('\x1b]11;?\x07');
    const start = Date.now();
    let buf = '';
    const chunk = Buffer.alloc(64);
    while (Date.now() - start < 50) {
      let n = 0;
      try {
        n = fs.readSync(fd, chunk, 0, 64, null);
      } catch {
        break;
      }
      if (n > 0) buf += chunk.slice(0, n).toString('latin1');
      const m = buf.match(/11;rgb:([0-9a-fA-F]{2,4})\/([0-9a-fA-F]{2,4})\/([0-9a-fA-F]{2,4})/);
      if (m) {
        const hex = (s: string) => parseInt(s.length === 4 ? s.slice(0, 2) : s, 16);
        const r = hex(m[1]!), g = hex(m[2]!), b = hex(m[3]!);
        const lum = 0.299 * r + 0.587 * g + 0.114 * b;
        return lum > 128 ? 'light' : 'dark';
      }
    }
  } catch {}
  return 'dark';
}

function detectTheme(): ThemeName {
  if (process.env.JWCODE_THEME === 'light') return 'light';
  if (process.env.JWCODE_THEME === 'dark') return 'dark';
  return detectTerminalBg();
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

/** Re-run theme detection (env > adaptive > default). Useful after env changes. */
export function reapplyTheme(): void {
  current = detectTheme();
}

export const t = getTheme();

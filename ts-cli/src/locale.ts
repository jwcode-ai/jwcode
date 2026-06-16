/**
 * Simple locale detection for the TUI.
 * Language is detected from LANG/LC_ALL env var, falling back to English.
 */

export type Locale = 'en' | 'zh';

let _locale: Locale | null = null;

function detectLocale(): Locale {
  const lang = process.env.LANG || process.env.LC_ALL || '';
  if (/^zh/i.test(lang)) return 'zh';
  return 'en';
}

export function getLocale(): Locale {
  if (!_locale) _locale = detectLocale();
  return _locale;
}

/** Override for testing. */
export function setLocale(l: Locale): void {
  _locale = l;
}

type StrKey = keyof typeof EN;

const EN = {
  riskDestructive: 'Destructive operation — may delete data',
  riskHighCmd: 'High-risk command — system-level operation',
  riskShell: 'Shell command execution',
  riskFileWrite: 'File write operation',
  riskPackageMgr: 'Package manager operation',
  riskGitDestructive: 'Git destructive operation',
  riskNetwork: 'Network request',
  riskReadOnly: 'Read-only operation',
  riskTool: 'Tool invocation',

  permissionRequired: 'Permission Required',
  auto: 'Auto',
  s: 's',

  critical: 'CRITICAL',
  high: 'HIGH',
  medium: 'MEDIUM',
  low: 'LOW',

  allowExec: 'Allow — execute this command',
  confirmHint: 'Press Enter or y to confirm',
  denyCancel: 'Deny — cancel this operation',
  denyHint: 'Press Esc or n to cancel',
  allowSession: 'Allow always — this session',
  sessionHint: 'Don\'t ask again for {tool}',
  autoMode: 'Auto mode — allow all hooks',
  autoHint: 'All future hooks will be auto-approved',

  criticalWarning: 'This may cause irreversible changes — verify carefully',
  selectHint: '1/2/3/4 to select · ↑↓ to navigate · Enter to confirm · Esc to deny',

  previewFile: 'File: ',
};

const ZH: Record<StrKey, string> = {
  riskDestructive: '破坏性操作 — 可能删除数据',
  riskHighCmd: '高危命令 — 系统级操作',
  riskShell: 'Shell 命令执行',
  riskFileWrite: '文件写入操作',
  riskPackageMgr: '包管理器操作',
  riskGitDestructive: 'Git 破坏性操作',
  riskNetwork: '网络请求',
  riskReadOnly: '只读操作',
  riskTool: '工具调用',

  permissionRequired: '权限请求',
  auto: '自动',
  s: '秒',

  critical: '严重',
  high: '高',
  medium: '中',
  low: '低',

  allowExec: '允许 — 执行此命令',
  confirmHint: '按 Enter 或 y 确认',
  denyCancel: '拒绝 — 取消此操作',
  denyHint: '按 Esc 或 n 拒绝',
  allowSession: '始终允许 — 本次会话',
  sessionHint: '不再询问 {tool}',
  autoMode: '自动模式 — 允许所有钩子',
  autoHint: '后续所有钩子自动批准',

  criticalWarning: '此操作不可逆 — 请谨慎确认',
  selectHint: '1/2/3/4 选择 · ↑↓ 导航 · Enter 确认 · Esc 拒绝',

  previewFile: '文件: ',
};

export function t(key: StrKey, vars?: Record<string, string>): string {
  const locale = getLocale();
  const table = locale === 'zh' ? ZH : EN;
  let s = table[key] ?? EN[key] ?? key;
  if (vars) {
    for (const [k, v] of Object.entries(vars)) {
      s = s.replace(`{${k}}`, v);
    }
  }
  return s;
}

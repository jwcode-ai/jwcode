/**
 * Command definitions — Chinese descriptions.
 * Local commands handled by TUI; WS commands sent to backend.
 */
export interface CmdEntry {
  cmd: string;
  desc: string;
  via: 'local' | 'ws';
  action: string | null;
}

// Local TUI commands
export const LOCAL_COMMANDS: CmdEntry[] = [
  { cmd: '/help', desc: '显示所有命令', via: 'local', action: null },
  { cmd: '/plan', desc: '切换规划模式 (先规划再执行)', via: 'local', action: 'plan_mode' },
  { cmd: '/auto', desc: '切换自动模式 (自动批准工具执行)', via: 'local', action: 'auto_mode' },
  { cmd: '/clean', desc: '清空当前会话消息', via: 'local', action: 'clear' },
  { cmd: '/context', desc: '显示当前会话状态', via: 'local', action: 'show_context' },
  { cmd: '/exit', desc: '退出 JWCode', via: 'local', action: '__exit__' },
];

// WebSocket commands (sent to backend)
export const WS_COMMANDS: CmdEntry[] = [
  { cmd: '/pdca', desc: '显示 PDCA 看板', via: 'ws', action: null },
  { cmd: '/tasks', desc: '列出所有任务', via: 'ws', action: null },
  { cmd: '/memory', desc: '显示记忆信息', via: 'ws', action: null },
  { cmd: '/model', desc: '切换模型', via: 'ws', action: 'switch_model' },
  { cmd: '/arch', desc: '显示架构信息', via: 'ws', action: null },
  { cmd: '/cost', desc: '显示 token 消耗', via: 'ws', action: null },
];

export const ALL_COMMANDS: CmdEntry[] = [...LOCAL_COMMANDS, ...WS_COMMANDS];

// Action map: slash command -> { action, needsArg? }
export const SLASH_COMMANDS: Record<string, { action: string; needsArg?: boolean } | null> = {
  '/help': null,
  '/plan': { action: 'plan_mode' },
  '/auto': { action: 'auto_mode' },
  '/clean': { action: 'clear' },
  '/context': { action: 'show_context' },
  '/exit': { action: '__exit__' },
  '/quit': { action: '__exit__' },
  '/stop': { action: 'stop' },
  '/pause': { action: 'pause' },
  '/resume': { action: 'resume' },
  '/doctor': { action: 'doctor' },
  '/rewind': { action: 'rewind' },
  '/compact': { action: 'compact' },
  '/model': { action: 'model_change', needsArg: true },
  '/init': { action: 'init' },
  '/effort': { action: 'effort', needsArg: true },
  '/branch': { action: 'branch', needsArg: true },
  '/mcp': { action: 'mcp', needsArg: true },
  '/skills': { action: 'skills' },
  '/agents': { action: 'agents' },
  '/config': { action: 'config', needsArg: true },
  '/plugin': { action: 'plugin', needsArg: true },
  '/tokens': { action: 'tokens' },
  '/memory': { action: 'memory' },
  '/export': { action: 'export', needsArg: true },
  '/checkpoint': { action: 'checkpoint' },
  '/test': { action: 'test' },
  '/lint': { action: 'lint' },
  '/search': { action: 'search', needsArg: true },
  '/project': { action: 'project' },
};

// Help text used by /help command
export const HELP_TEXT = [
  '━━━ 本地命令 ━━━',
  ...LOCAL_COMMANDS.map(c => `  ${c.cmd.padEnd(16)} ${c.desc}`),
  '',
  '━━━ 后端命令 ━━━',
  ...WS_COMMANDS.map(c => `  ${c.cmd.padEnd(16)} ${c.desc}`),
].join('\n');

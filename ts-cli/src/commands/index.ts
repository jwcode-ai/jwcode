export type CommandVia = 'local' | 'ws';
export type CommandCategory = 'core' | 'session' | 'workspace' | 'tools' | 'config';

export interface CmdEntry {
  cmd: string;
  desc: string;
  via: CommandVia;
  action: string | null;
  category: CommandCategory;
  usage?: string;
  needsArg?: boolean;
}

export const LOCAL_COMMANDS: CmdEntry[] = [
  { cmd: '/help', desc: 'Show all commands', via: 'local', action: null, category: 'core' },
  { cmd: '/plan', desc: 'Toggle plan mode before execution', via: 'local', action: 'plan_mode', category: 'core' },
  { cmd: '/auto', desc: 'Toggle auto-approval mode', via: 'local', action: 'auto_mode', category: 'core' },
  { cmd: '/context', desc: 'Show current conversation state', via: 'local', action: 'show_context', category: 'session' },
  { cmd: '/clear', desc: 'Clear local conversation messages', via: 'local', action: 'clear', category: 'session' },
  { cmd: '/tokens', desc: 'Show token usage details', via: 'local', action: 'tokens', category: 'session' },
  { cmd: '/export', desc: 'Export conversation to a Markdown file', via: 'local', action: 'export', category: 'session', usage: '/export <path>', needsArg: true },
  { cmd: '/checkpoint', desc: 'Create, list, or restore local checkpoints', via: 'local', action: 'checkpoint', category: 'session', usage: '/checkpoint [list|restore <file>|name]' },
  { cmd: '/memory', desc: 'Browse project memory files', via: 'local', action: 'memory', category: 'workspace', usage: '/memory [file]' },
  { cmd: '/project', desc: 'Show a compact project summary', via: 'local', action: 'project', category: 'workspace' },
  { cmd: '/search', desc: 'Search text in the current workspace', via: 'local', action: 'search', category: 'workspace', usage: '/search <keyword>', needsArg: true },
  { cmd: '/test', desc: 'Run the project test script', via: 'local', action: 'test', category: 'tools', usage: '/test [extra args]' },
  { cmd: '/lint', desc: 'Run the project lint script', via: 'local', action: 'lint', category: 'tools', usage: '/lint [extra args]' },
  { cmd: '/config', desc: 'Manage ~/.jwcode/config.yaml', via: 'local', action: 'config', category: 'config', usage: '/config list|get <key>|set <key> <value>' },
  { cmd: '/skills', desc: 'List available local skills', via: 'local', action: 'skills', category: 'config' },
  { cmd: '/agents', desc: 'List project agents', via: 'local', action: 'agents', category: 'config' },
  { cmd: '/plugin', desc: 'List local plugins', via: 'local', action: 'plugin', category: 'config', usage: '/plugin list' },
  { cmd: '/exit', desc: 'Exit JWCode', via: 'local', action: '__exit__', category: 'core' },
  { cmd: '/quit', desc: 'Exit JWCode', via: 'local', action: '__exit__', category: 'core' },
];

export const WS_COMMANDS: CmdEntry[] = [
  { cmd: '/confirm', desc: 'Confirm current plan and start execution', via: 'ws', action: '__confirm_plan', category: 'session' },
  { cmd: '/cancel', desc: 'Cancel current plan', via: 'ws', action: '__cancel_plan', category: 'session' },
  { cmd: '/stop', desc: 'Stop current AI generation', via: 'ws', action: 'stop', category: 'session' },
  { cmd: '/pause', desc: 'Pause current AI generation', via: 'ws', action: 'pause', category: 'session' },
  { cmd: '/resume', desc: 'Resume paused AI generation', via: 'ws', action: 'resume', category: 'session' },
  { cmd: '/doctor', desc: 'Run backend diagnostics', via: 'ws', action: 'doctor', category: 'tools' },
  { cmd: '/rewind', desc: 'Ask backend to rewind to its latest checkpoint', via: 'ws', action: 'rewind', category: 'session' },
  { cmd: '/compact', desc: 'Compress backend conversation context', via: 'ws', action: 'compact', category: 'session' },
  { cmd: '/model', desc: 'Switch AI model', via: 'ws', action: 'model_change', category: 'config', usage: '/model <model>', needsArg: true },
  { cmd: '/init', desc: 'Analyze project and generate JWCODE.md', via: 'ws', action: 'init', category: 'workspace' },
  { cmd: '/effort', desc: 'Set reasoning effort level', via: 'ws', action: 'effort', category: 'config', usage: '/effort low|medium|high', needsArg: true },
  { cmd: '/branch', desc: 'Create a branch conversation', via: 'ws', action: 'branch', category: 'session', usage: '/branch <name>', needsArg: true },
  { cmd: '/mcp', desc: 'Manage backend MCP servers', via: 'ws', action: 'mcp', category: 'config', usage: '/mcp list|add|remove', needsArg: true },
];

export const ALL_COMMANDS: CmdEntry[] = [...LOCAL_COMMANDS, ...WS_COMMANDS];

export const SLASH_COMMANDS: Record<string, { action: string; needsArg?: boolean } | null> =
  Object.fromEntries(
    ALL_COMMANDS.map(c => [c.cmd, c.action === null ? null : { action: c.action, needsArg: c.needsArg }]),
  );

export function getCommandEntry(cmd: string): CmdEntry | undefined {
  return ALL_COMMANDS.find(c => c.cmd === cmd);
}

export function getUsage(cmd: string): string {
  const entry = getCommandEntry(cmd);
  return entry?.usage || cmd;
}

const CATEGORY_LABELS: Record<CommandCategory, string> = {
  core: 'Core',
  session: 'Session',
  workspace: 'Workspace',
  tools: 'Tools',
  config: 'Config',
};

export const HELP_TEXT = [
  'JWCode commands',
  '',
  ...(['core', 'session', 'workspace', 'tools', 'config'] as CommandCategory[]).flatMap(category => {
    const commands = ALL_COMMANDS.filter(c => c.category === category);
    return [
      CATEGORY_LABELS[category],
      ...commands.map(c => {
        const lhs = (c.usage || c.cmd).padEnd(28, ' ');
        const scope = c.via === 'local' ? 'local' : 'backend';
        return `  ${lhs} ${c.desc} (${scope})`;
      }),
      '',
    ];
  }),
  'Shortcuts',
  '  / opens this command palette',
  '  @ references project files',
  '  PgUp/PgDn scrolls help and messages',
  '  Esc closes panels or cancels approval prompts',
].join('\n');

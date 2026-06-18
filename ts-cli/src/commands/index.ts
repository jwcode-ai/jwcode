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

/** Pure UI commands handled entirely in the CLI. */
export const LOCAL_COMMANDS: CmdEntry[] = [
  { cmd: '/help', desc: 'Show all commands', via: 'local', action: null, category: 'core' },
  { cmd: '/plan', desc: 'Toggle plan mode before execution', via: 'local', action: 'plan_mode', category: 'core' },
  { cmd: '/auto', desc: 'Toggle auto-approval mode', via: 'local', action: 'auto_mode', category: 'core' },
  { cmd: '/context', desc: 'Show current conversation state', via: 'local', action: 'show_context', category: 'session' },
  { cmd: '/clear', desc: 'Clear local conversation messages', via: 'local', action: 'clear', category: 'session' },
  { cmd: '/exit', desc: 'Exit JWCode', via: 'local', action: '__exit__', category: 'core' },
  { cmd: '/quit', desc: 'Exit JWCode', via: 'local', action: '__exit__', category: 'core' },
];

/** Control-flow commands that keep their dedicated WS message types (not command_execute). */
export const CONTROL_COMMANDS: CmdEntry[] = [
  { cmd: '/confirm', desc: 'Confirm current plan and start execution', via: 'ws', action: '__confirm_plan', category: 'session' },
  { cmd: '/cancel', desc: 'Cancel current plan', via: 'ws', action: '__cancel_plan', category: 'session' },
  { cmd: '/stop', desc: 'Stop current AI generation', via: 'ws', action: 'stop', category: 'session' },
  { cmd: '/pause', desc: 'Pause current AI generation', via: 'ws', action: 'pause', category: 'session' },
  { cmd: '/resume', desc: 'Resume paused AI generation', via: 'ws', action: 'resume', category: 'session' },
];

/** Commands discovered from the backend manifest at runtime. */
export let DYNAMIC_COMMANDS: CmdEntry[] = [];

export let ALL_COMMANDS: CmdEntry[] = [...LOCAL_COMMANDS, ...CONTROL_COMMANDS];

const CATEGORY_LABELS: Record<CommandCategory, string> = {
  core: 'Core',
  session: 'Session',
  workspace: 'Workspace',
  tools: 'Tools',
  config: 'Config',
};

function buildSlashMap(cmds: CmdEntry[]): Record<string, { action: string; needsArg?: boolean } | null> {
  return Object.fromEntries(
    cmds.map(c => [c.cmd, c.action === null ? null : { action: c.action, needsArg: c.needsArg }]),
  );
}

function buildHelpText(cmds: CmdEntry[]): string {
  return [
    'JWCode commands',
    '',
    ...(['core', 'session', 'workspace', 'tools', 'config'] as CommandCategory[]).flatMap(category => {
      const list = cmds.filter(c => c.category === category);
      return [
        CATEGORY_LABELS[category],
        ...list.map(c => {
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
}

export let SLASH_COMMANDS: Record<string, { action: string; needsArg?: boolean } | null> = buildSlashMap(ALL_COMMANDS);

export let HELP_TEXT: string = buildHelpText(ALL_COMMANDS);

/** Populate dynamic commands from the backend /api/commands manifest. */
export function setDynamicCommands(cmds: CmdEntry[]): void {
  const reserved = new Set<string>([...LOCAL_COMMANDS, ...CONTROL_COMMANDS].map(c => c.cmd));
  DYNAMIC_COMMANDS = cmds.filter(c => !reserved.has(c.cmd));
  ALL_COMMANDS = [...LOCAL_COMMANDS, ...CONTROL_COMMANDS, ...DYNAMIC_COMMANDS];
  SLASH_COMMANDS = buildSlashMap(ALL_COMMANDS);
  HELP_TEXT = buildHelpText(ALL_COMMANDS);
}

export function getCommandEntry(cmd: string): CmdEntry | undefined {
  return ALL_COMMANDS.find(c => c.cmd === cmd);
}

export function getUsage(cmd: string): string {
  const entry = getCommandEntry(cmd);
  return entry?.usage || cmd;
}

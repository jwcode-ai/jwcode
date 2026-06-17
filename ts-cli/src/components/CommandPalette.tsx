import { useState, useMemo, useEffect, memo } from 'react';
import { Box, Text, useInput, useStdout } from 'ink';
import { ALL_COMMANDS, type CmdEntry, type CommandCategory } from '../commands/index.js';

interface Props {
  filter: string;
  onSelect: (cmd: string | null) => void;
}

const CATEGORY_LABELS: Record<CommandCategory, string> = {
  core: 'Core',
  session: 'Session',
  workspace: 'Workspace',
  tools: 'Tools',
  config: 'Config',
};

function scoreCommand(c: CmdEntry, query: string): number {
  if (!query) return 1;
  const cmd = c.cmd.toLowerCase();
  const desc = c.desc.toLowerCase();
  if (cmd === `/${query}`) return 100;
  if (cmd.startsWith(`/${query}`)) return 80;
  if (cmd.includes(query)) return 60;
  if (desc.includes(query)) return 40;
  if (c.category.includes(query as CommandCategory)) return 20;
  return 0;
}

export const CommandPalette = memo(function CommandPalette({ filter, onSelect }: Props) {
  const [selected, setSelected] = useState(0);
  const [scrollOffset, setScrollOffset] = useState(0);
  const { stdout } = useStdout();
  const terminalRows = (stdout as NodeJS.WriteStream)?.rows || 24;

  const visible = useMemo(() => {
    const f = filter.replace(/^\//, '').trim().toLowerCase();
    return ALL_COMMANDS
      .map(c => ({ command: c, score: scoreCommand(c, f) }))
      .filter(item => item.score > 0)
      .sort((a, b) => b.score - a.score || a.command.category.localeCompare(b.command.category) || a.command.cmd.localeCompare(b.command.cmd))
      .map(item => item.command);
  }, [filter]);

  useEffect(() => { setSelected(0); setScrollOffset(0); }, [filter]);

  const maxShow = Math.max(6, Math.min(terminalRows - 13, 12));

  useEffect(() => {
    setScrollOffset(prev => {
      if (selected < prev) return selected;
      if (selected >= prev + maxShow) return selected - maxShow + 1;
      return prev;
    });
  }, [selected, maxShow]);

  const sliced = visible.slice(scrollOffset, scrollOffset + maxShow);

  useInput((_input, key) => {
    if (key.escape) { onSelect(null); return; }
    if (key.downArrow) { setSelected(prev => Math.min(prev + 1, Math.max(visible.length - 1, 0))); return; }
    if (key.upArrow) { setSelected(prev => Math.max(prev - 1, 0)); return; }
    if (key.pageDown) { setSelected(prev => Math.min(prev + maxShow, Math.max(visible.length - 1, 0))); return; }
    if (key.pageUp) { setSelected(prev => Math.max(prev - maxShow, 0)); return; }
    if ((key as any).home) { setSelected(0); return; }
    if ((key as any).end) { setSelected(Math.max(visible.length - 1, 0)); return; }
    if (key.return && visible[selected]) onSelect(visible[selected].cmd);
  });

  return (
    <Box flexDirection="column" borderStyle="single" borderColor="cyan" paddingX={1} width={72}>
      <Box justifyContent="space-between">
        <Text bold color="cyan">Commands</Text>
        <Text dimColor>Up/Down select  Enter insert  Esc close</Text>
      </Box>
      {sliced.length === 0 && (
        <Box paddingLeft={1}>
          <Text dimColor>No commands match "{filter}".</Text>
        </Box>
      )}
      {sliced.map((cmd, i) => {
        const idx = scrollOffset + i;
        const selectedRow = idx === selected;
        const scopeColor = cmd.via === 'ws' ? 'yellow' : 'blue';
        return (
          <Box key={cmd.cmd} paddingLeft={1}>
            <Text color={selectedRow ? 'cyan' : undefined} bold={selectedRow}>
              {selectedRow ? '> ' : '  '}
            </Text>
            <Text color="green">{(cmd.usage || cmd.cmd).padEnd(28, ' ')}</Text>
            <Text color={scopeColor}>{cmd.via === 'ws' ? 'backend' : 'local'}</Text>
            <Text dimColor>  {CATEGORY_LABELS[cmd.category].padEnd(9, ' ')}</Text>
            <Text>{cmd.desc}</Text>
          </Box>
        );
      })}
      {visible.length > maxShow && (
        <Box>
          <Text dimColor>  {scrollOffset + 1}-{Math.min(scrollOffset + maxShow, visible.length)} / {visible.length}</Text>
        </Box>
      )}
    </Box>
  );
});

/**
 * CommandPalette — / filterable popup.
 * Character input is handled by TextInput; this only handles navigation.
 */
import { useState, useMemo, useEffect } from 'react';
import { Box, Text, useInput, useStdout } from 'ink';
import { ALL_COMMANDS, type CmdEntry } from '../commands/index.js';

interface Props {
  filter: string;
  onSelect: (cmd: string | null) => void;
}

export function CommandPalette({ filter, onSelect }: Props) {
  const [selected, setSelected] = useState(0);
  const [scrollOffset, setScrollOffset] = useState(0);
  const { stdout } = useStdout();
  const terminalRows = (stdout as NodeJS.WriteStream)?.rows || 24;

  const visible = useMemo(() => {
    const f = filter.replace(/^\//, '').toLowerCase();
    if (!f) return ALL_COMMANDS;
    return ALL_COMMANDS.filter(c =>
      c.cmd.toLowerCase().includes(f) || c.desc.includes(f),
    );
  }, [filter]);

  useEffect(() => { setSelected(0); setScrollOffset(0); }, [filter]);

  const maxShow = Math.max(5, Math.min(terminalRows - 13, 10));

  // Keep selected row in view
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
    if (key.downArrow) { setSelected(prev => Math.min(prev + 1, visible.length - 1)); return; }
    if (key.upArrow) { setSelected(prev => Math.max(prev - 1, 0)); return; }
    if (key.pageDown) { setSelected(prev => Math.min(prev + maxShow, visible.length - 1)); return; }
    if (key.pageUp) { setSelected(prev => Math.max(prev - maxShow, 0)); return; }
    if ((key as any).home) { setSelected(0); return; }
    if ((key as any).end) { setSelected(visible.length - 1); return; }
    if (key.return) {
      if (visible.length > 0 && selected >= 0 && selected < visible.length) {
        onSelect(visible[selected].cmd);
      }
    }
  });

  return (
    <Box flexDirection="column" borderStyle="single" borderColor="cyan" paddingX={1} width={52}>
      <Box><Text bold color="cyan">命令列表</Text><Text dimColor>  ↑↓选择 / PgUp/PgDn翻页 / 回车确认 / Esc取消</Text></Box>
      {sliced.map((cmd, i) => {
        const idx = scrollOffset + i;
        return (
          <Box key={cmd.cmd} paddingLeft={1}>
            <Text color={idx === selected ? 'cyan' : undefined} bold={idx === selected}>
              {idx === selected ? '> ' : '  '}
            </Text>
            <Text color="green">{cmd.cmd}</Text>
            <Text dimColor>  {cmd.desc}</Text>
            <Text color={cmd.via === 'ws' ? 'yellow' : 'blue'} dimColor={idx !== selected}>
              ({cmd.via === 'ws' ? '后端' : '本地'})
            </Text>
          </Box>
        );
      })}
      {visible.length > maxShow && (
        <Box><Text dimColor>  {scrollOffset + 1}-{Math.min(scrollOffset + maxShow, visible.length)} / {visible.length}</Text></Box>
      )}
    </Box>
  );
}

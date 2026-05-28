/**
 * CommandPalette — / filterable popup.
 * Character input is handled by TextInput; this only handles navigation.
 */
import { useState, useMemo, useEffect } from 'react';
import { Box, Text, useInput } from 'ink';
import { ALL_COMMANDS, type CmdEntry } from '../commands/index.js';

interface Props {
  filter: string;
  onSelect: (cmd: string | null) => void;
}

export function CommandPalette({ filter, onSelect }: Props) {
  const [selected, setSelected] = useState(0);

  const visible = useMemo(() => {
    const f = filter.replace(/^\//, '').toLowerCase();
    if (!f) return ALL_COMMANDS;
    return ALL_COMMANDS.filter(c =>
      c.cmd.toLowerCase().includes(f) || c.desc.includes(f),
    );
  }, [filter]);

  useEffect(() => { setSelected(0); }, [filter]);

  useInput((_input, key) => {
    if (key.escape) { onSelect(null); return; }
    if (key.downArrow) { setSelected(prev => Math.min(prev + 1, visible.length - 1)); return; }
    if (key.upArrow) { setSelected(prev => Math.max(prev - 1, 0)); return; }
    if (key.return) {
      if (visible.length > 0 && selected >= 0 && selected < visible.length) {
        onSelect(visible[selected].cmd);
      }
    }
  });

  const maxShow = 12;
  const sliced = visible.slice(0, maxShow);

  return (
    <Box flexDirection="column" borderStyle="single" borderColor="cyan" paddingX={1} width={52}>
      <Box><Text bold color="cyan">命令列表</Text><Text dimColor>  ↑↓选择 / 回车确认 / Esc取消</Text></Box>
      {sliced.map((cmd, i) => (
        <Box key={cmd.cmd} paddingLeft={1}>
          <Text color={i === selected ? 'cyan' : undefined} bold={i === selected}>
            {i === selected ? '> ' : '  '}
          </Text>
          <Text color="green">{cmd.cmd}</Text>
          <Text dimColor>  {cmd.desc}</Text>
          <Text color={cmd.via === 'ws' ? 'yellow' : 'blue'} dimColor={i !== selected}>
            ({cmd.via === 'ws' ? '后端' : '本地'})
          </Text>
        </Box>
      ))}
      {visible.length > maxShow && (
        <Box><Text dimColor>  ... 还有 {visible.length - maxShow} 条命令</Text></Box>
      )}
    </Box>
  );
}

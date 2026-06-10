/**
 * SessionPicker — session history list with search, resume, delete.
 */
import { useState, useMemo, useEffect } from 'react';
import { Box, Text, useInput, useStdout } from 'ink';
import { t } from '../theme.js';

export interface SessionInfo {
  id: string;
  title: string;
  createdAt: string;
  updatedAt?: string;
  messageCount: number;
}

interface Props {
  sessions: SessionInfo[];
  onSelect: (session: SessionInfo | null) => void;
  onDelete?: (sessionId: string) => void;
}

export function SessionPicker({ sessions, onSelect, onDelete }: Props) {
  const [selected, setSelected] = useState(0);
  const [scrollOffset, setScrollOffset] = useState(0);
  const [filter, setFilter] = useState('');
  const { stdout } = useStdout();
  const terminalRows = (stdout as NodeJS.WriteStream)?.rows || 24;

  const visible = useMemo(() => {
    const f = filter.toLowerCase();
    if (!f) return sessions;
    return sessions.filter(s =>
      s.title.toLowerCase().includes(f) || s.id.toLowerCase().includes(f),
    );
  }, [sessions, filter]);

  useEffect(() => { setSelected(0); setScrollOffset(0); }, [filter]);

  const maxShow = Math.max(5, Math.min(terminalRows - 12, 10));

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
    if (key.return) {
      if (visible.length > 0 && selected >= 0 && selected < visible.length) {
        onSelect(visible[selected]);
      }
    }
    if (key.delete && onDelete && visible.length > 0 && selected >= 0 && selected < visible.length) {
      onDelete(visible[selected].id);
    }
    // Filter by typing
    if (!key.ctrl && !key.meta && _input && _input.length === 1 && !key.return && !key.escape) {
      setFilter(prev => prev + _input);
    }
    if (key.backspace || key.delete) {
      setFilter(prev => prev.slice(0, -1));
    }
  });

  const formatDate = (iso: string) => {
    try {
      const d = new Date(iso);
      return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch { return iso; }
  };

  return (
    <Box flexDirection="column" borderStyle="single" borderColor={t.primary} paddingX={1} width={66}>
      <Box>
        <Text bold color={t.primary}>Session History</Text>
        <Text dimColor>  type to filter / Enter resume / Del delete / Esc close</Text>
      </Box>
      {filter && (
        <Box paddingLeft={1}>
          <Text dimColor>Filter: </Text>
          <Text color={t.warning}>{filter}</Text>
        </Box>
      )}
      {sliced.length === 0 && (
        <Box paddingLeft={2}><Text dimColor>No sessions found.</Text></Box>
      )}
      {sliced.map((s, i) => {
        const idx = scrollOffset + i;
        return (
          <Box key={s.id} paddingLeft={1}>
            <Text color={idx === selected ? t.primary : undefined} bold={idx === selected}>
              {idx === selected ? '> ' : '  '}
            </Text>
            <Text color={t.success}>{s.id}</Text>
            <Text dimColor>  {s.title.slice(0, 30)}</Text>
            <Text color={t.muted}>  [{s.messageCount} msgs]</Text>
            <Text dimColor>  {formatDate(s.updatedAt || s.createdAt)}</Text>
          </Box>
        );
      })}
      {visible.length > maxShow && (
        <Box><Text dimColor>  {scrollOffset + 1}-{Math.min(scrollOffset + maxShow, visible.length)} / {visible.length}</Text></Box>
      )}
    </Box>
  );
}

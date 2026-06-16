/**
 * FilePalette — @ file reference popup.
 * Shows matching files when user types @ in the input.
 */
import { useState, useMemo, useEffect } from 'react';
import { Box, Text, useInput, useStdout } from 'ink';

interface Props {
  query: string;   // text after @
  files: string[]; // matching file paths
  onSelect: (path: string | null) => void;
}

const MAX_DISPLAY_LENGTH = 60;

function basename(path: string): string {
  const parts = path.replace(/\\/g, '/').split('/');
  return parts[parts.length - 1] || path;
}

function dirname(path: string): string {
  const parts = path.replace(/\\/g, '/').split('/');
  parts.pop();
  return parts.join('/') || '.';
}

export function FilePalette({ query, files, onSelect }: Props) {
  const [selected, setSelected] = useState(0);
  const [scrollOffset, setScrollOffset] = useState(0);
  const { stdout } = useStdout();
  const terminalRows = (stdout as NodeJS.WriteStream)?.rows || 24;

  // Client-side fuzzy filter
  const visible = useMemo(() => {
    if (!query) return files;
    const q = query.toLowerCase().replace(/\\/g, '/');
    return files.filter(f => {
      const lower = f.toLowerCase().replace(/\\/g, '/');
      return lower.includes(q);
    });
  }, [files, query]);

  useEffect(() => { setSelected(0); setScrollOffset(0); }, [query]);

  const maxShow = Math.max(4, Math.min(terminalRows - 13, 8));

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
  });

  return (
    <Box flexDirection="column" borderStyle="single" borderColor="yellow" paddingX={1} width={64}>
      <Box>
        <Text bold color="yellow">@ 搜索: </Text>
        <Text color="green">{query || '(输入文件名)'}</Text>
        <Text dimColor>  ↑↓ 选择 · Enter 插入 · Esc 关闭</Text>
      </Box>
      {visible.length === 0 && (
        <Box paddingLeft={1}><Text dimColor>  未找到匹配文件</Text></Box>
      )}
      {sliced.map((path, i) => {
        const idx = scrollOffset + i;
        const name = basename(path);
        const dir = dirname(path);
        const truncatedPath = path.length > MAX_DISPLAY_LENGTH
          ? '...' + path.slice(-(MAX_DISPLAY_LENGTH - 3))
          : path;
        return (
          <Box key={path} paddingLeft={1}>
            <Text color={idx === selected ? 'yellow' : undefined} bold={idx === selected}>
              {idx === selected ? '❯ ' : '  '}
            </Text>
            <Text color="green">{name}</Text>
            <Text dimColor> — {dir}</Text>
          </Box>
        );
      })}
      {visible.length > maxShow && (
        <Box>
          <Text dimColor>
            {'  '}{scrollOffset + 1}-{Math.min(scrollOffset + maxShow, visible.length)} / {visible.length}
          </Text>
        </Box>
      )}
    </Box>
  );
}

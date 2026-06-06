/**
 * ModelPicker — interactive model selection popup.
 *
 * Fetches available models from GET /api/models and renders a scrollable
 * list.  Arrow keys navigate, Enter selects, Esc cancels.
 */
import { useState, useEffect, useMemo } from 'react';
import { Box, Text, useInput, useStdout } from 'ink';
import { t } from '../theme.js';

export interface ModelInfo {
  name: string;
  id?: string;
  provider?: string;
  enabled?: boolean;
  priority?: number;
}

interface Props {
  backendUrl: string;
  onSelect: (model: ModelInfo) => void;
  onCancel: () => void;
}

export function ModelPicker({ backendUrl, onSelect, onCancel }: Props) {
  const [models, setModels] = useState<ModelInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState(0);
  const [scrollOffset, setScrollOffset] = useState(0);
  const { stdout } = useStdout();
  const terminalRows = (stdout as NodeJS.WriteStream)?.rows || 24;

  useEffect(() => {
    let cancelled = false;
    fetch(`${backendUrl}/api/models`)
      .then(r => r.json())
      .then(d => {
        if (cancelled) return;
        const list: ModelInfo[] = (d as any).data?.models || (d as any).models || [];
        setModels(list);
        setLoading(false);
      })
      .catch(e => {
        if (cancelled) return;
        setError(String(e));
        setLoading(false);
      });
    return () => { cancelled = true; };
  }, [backendUrl]);

  const maxShow = Math.max(4, Math.min(terminalRows - 14, 10));

  useEffect(() => {
    setScrollOffset(prev => {
      if (selected < prev) return selected;
      if (selected >= prev + maxShow) return selected - maxShow + 1;
      return prev;
    });
  }, [selected, maxShow]);

  const sliced = useMemo(
    () => models.slice(scrollOffset, scrollOffset + maxShow),
    [models, scrollOffset, maxShow],
  );

  useInput((_input, key) => {
    if (key.escape) { onCancel(); return; }
    if (key.downArrow) { setSelected(prev => Math.min(prev + 1, models.length - 1)); return; }
    if (key.upArrow) { setSelected(prev => Math.max(prev - 1, 0)); return; }
    if (key.pageDown) { setSelected(prev => Math.min(prev + maxShow, models.length - 1)); return; }
    if (key.pageUp) { setSelected(prev => Math.max(prev - maxShow, 0)); return; }
    if (key.return) {
      if (models.length > 0 && selected >= 0 && selected < models.length) {
        onSelect(models[selected]);
      }
    }
  });

  return (
    <Box flexDirection="column" borderStyle="single" borderColor={t.primary} paddingX={1} width={60}>
      <Box>
        <Text bold color={t.primary}>Available Models</Text>
        <Text dimColor>  ↑↓/PgUp/PgDn navigate  Enter select  Esc cancel</Text>
      </Box>

      {loading && (
        <Box paddingY={1}>
          <Text color={t.warning}>Loading models...</Text>
        </Box>
      )}

      {error && (
        <Box paddingY={1}>
          <Text color={t.error}>Failed to load models: {error}</Text>
        </Box>
      )}

      {!loading && !error && models.length === 0 && (
        <Box paddingY={1}>
          <Text dimColor>No models configured. Run /setup to add a provider.</Text>
        </Box>
      )}

      {sliced.map((m, i) => {
        const idx = scrollOffset + i;
        const isSel = idx === selected;
        const providerTag = m.provider ? ` [${m.provider}]` : '';
        return (
          <Box key={m.id || m.name || i} paddingLeft={1}>
            <Text color={isSel ? t.primary : undefined} bold={isSel}>
              {isSel ? '❯ ' : '  '}
            </Text>
            <Text color={isSel ? t.success : undefined}>{m.name || m.id}</Text>
            {providerTag ? <Text dimColor>{providerTag}</Text> : null}
            {m.priority !== undefined ? <Text dimColor>  p={m.priority}</Text> : null}
          </Box>
        );
      })}

      {models.length > maxShow && (
        <Box>
          <Text dimColor>
            {scrollOffset + 1}-{Math.min(scrollOffset + maxShow, models.length)} / {models.length}
          </Text>
        </Box>
      )}
    </Box>
  );
}

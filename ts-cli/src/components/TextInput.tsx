import { useState, useRef, useCallback } from 'react';
import { Box, Text, useInput } from 'ink';

// Rough token estimation: English ~4 chars/token, CJK ~1.5 chars/token
function estimateTokens(text: string): number {
  let cjk = 0;
  let other = 0;
  for (const ch of text) {
    if (/[一-鿿㐀-䶿豈-﫿　-〿＀-￯]/.test(ch)) {
      cjk++;
    } else {
      other++;
    }
  }
  return Math.ceil(cjk / 1.5 + other / 4);
}

const MAX_HISTORY = 30;
const HISTORY_KEY = 'jwcode-tscli-history';

function loadHistory(): string[] {
  try {
    const raw = process.env.JWCODE_HISTORY
      || (typeof sessionStorage !== 'undefined' ? sessionStorage.getItem(HISTORY_KEY) : null);
    return raw ? JSON.parse(raw) : [];
  } catch { return []; }
}

function saveHistory(entries: string[]) {
  try {
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.setItem(HISTORY_KEY, JSON.stringify(entries));
    }
  } catch { /* ignore */ }
}

export function saveToHistory(text: string) {
  const trimmed = text.trim();
  if (!trimmed) return;
  const history = loadHistory().filter(h => h !== trimmed);
  history.unshift(trimmed);
  saveHistory(history.slice(0, MAX_HISTORY));
}

interface Props {
  value: string;
  onChange: (value: string) => void;
  onSubmit: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
}

export function TextInput({ value, onChange, onSubmit, placeholder, disabled }: Props) {
  const historyRef = useRef<string[]>(loadHistory());
  const histIdxRef = useRef(-1);
  const draftRef = useRef('');

  const navigateHistory = useCallback((dir: 'up' | 'down'): string | null => {
    const history = historyRef.current;
    if (history.length === 0) return null;

    if (histIdxRef.current === -1) {
      draftRef.current = value;
      if (dir === 'up') {
        histIdxRef.current = 0;
        return history[0];
      }
      return null;
    }

    if (dir === 'up') {
      const next = Math.min(histIdxRef.current + 1, history.length - 1);
      histIdxRef.current = next;
      return history[next];
    } else {
      const next = histIdxRef.current - 1;
      if (next < 0) {
        histIdxRef.current = -1;
        return draftRef.current;
      }
      histIdxRef.current = next;
      return history[next];
    }
  }, [value]);

  const resetHistory = useCallback(() => {
    histIdxRef.current = -1;
    draftRef.current = '';
  }, []);

  useInput((input, key) => {
    if (disabled) return;

    if (key.return) {
      onSubmit(value);
      resetHistory();
      return;
    }

    if (key.upArrow) {
      const hist = navigateHistory('up');
      if (hist !== null) onChange(hist);
      return;
    }
    if (key.downArrow) {
      const hist = navigateHistory('down');
      if (hist !== null) onChange(hist);
      return;
    }

    // Any manual edit resets history navigation
    if (histIdxRef.current !== -1 && input) {
      resetHistory();
    }

    if (key.backspace || key.delete) {
      onChange(value.slice(0, -1));
      resetHistory();
    } else if (input && !key.ctrl && !key.meta && !key.tab && !key.escape) {
      onChange(value + input);
    }
  });

  const display = value || '';
  const showPlaceholder = !display && placeholder;
  const tokenEstimate = display ? estimateTokens(display) : 0;
  const charCount = display.length;

  return (
    <Box flexDirection="column">
      <Box>
        {display ? <Text>{display}</Text> : <Text dimColor>{placeholder}</Text>}
        <Text dimColor>▊</Text>
      </Box>
      {charCount > 0 && (
        <Box>
          <Text dimColor>  {charCount} 字符 ≈ {tokenEstimate} tokens</Text>
          {tokenEstimate > 100000 && (
            <Text color="red">  ⚠ 接近上下文上限</Text>
          )}
        </Box>
      )}
    </Box>
  );
}

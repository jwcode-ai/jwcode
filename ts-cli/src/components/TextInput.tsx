import { useState, useRef, useCallback } from 'react';
import { Box, Text, useInput } from 'ink';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { homedir } from 'node:os';
import { pasteSummary } from '../pasteBuffer.js';

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
const HISTORY_DIR = join(homedir(), '.jwcode');
const HISTORY_FILE = join(HISTORY_DIR, 'history.json');

function ensureHistoryDir() {
  try { mkdirSync(HISTORY_DIR, { recursive: true }); } catch { /* ignore */ }
}

function loadHistory(): string[] {
  try {
    if (process.env.JWCODE_HISTORY) return JSON.parse(process.env.JWCODE_HISTORY);
    const raw = readFileSync(HISTORY_FILE, 'utf-8');
    return JSON.parse(raw);
  } catch { return []; }
}

function saveHistory(entries: string[]) {
  try {
    ensureHistoryDir();
    writeFileSync(HISTORY_FILE, JSON.stringify(entries), 'utf-8');
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
  disableHistory?: boolean;
}

export function TextInput({ value, onChange, onSubmit, placeholder, disabled, disableHistory }: Props) {
  const historyRef = useRef<string[]>(loadHistory());
  const histIdxRef = useRef(-1);
  const draftRef = useRef('');
  const cursorRef = useRef(value.length);
  const [, forceRender] = useState(0);

  // Sync cursor when value changes externally
  if (cursorRef.current > value.length) {
    cursorRef.current = value.length;
  }

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

  // Bracketed paste state — persists across renders via refs
  const pasteState = useRef<{ accumulating: boolean; buf: string }>({ accumulating: false, buf: '' });

  useInput((input, key) => {
    if (disabled) return;

    // ── bracketed paste detection ──
    // Terminal wraps paste in \e[200~ ... \e[201~.  Ink strips the leading
    // \e so we see "[200~" / "[201~".  Content between markers is buffered
    // into pasteBuffer and replaced with a compact summary.
    const PASTE_START = '[200~';
    const PASTE_END = '[201~';

    if (input.includes(PASTE_START)) {
      pasteState.current.accumulating = true;
      pasteState.current.buf = input.split(PASTE_START).slice(1).join(PASTE_START);
      if (pasteState.current.buf.includes(PASTE_END)) {
        const parts = pasteState.current.buf.split(PASTE_END);
        pasteState.current.buf = parts[0] || '';
        pasteState.current.accumulating = false;
        const { label } = pasteSummary(pasteState.current.buf);
        onChange(value + label);
      }
      return;
    }

    if (pasteState.current.accumulating && input.includes(PASTE_END)) {
      const parts = input.split(PASTE_END);
      pasteState.current.buf += parts[0] || '';
      pasteState.current.accumulating = false;
      const { label } = pasteSummary(pasteState.current.buf);
      onChange(value + label);
      return;
    }

    if (pasteState.current.accumulating) {
      pasteState.current.buf += input;
      return;
    }

    // ── normal input ──

    if (key.return) {
      onSubmit(value);
      resetHistory();
      return;
    }

    if (key.leftArrow) {
      cursorRef.current = Math.max(0, cursorRef.current - 1);
      forceRender(c => c + 1);
      return;
    }
    if (key.rightArrow) {
      cursorRef.current = Math.min(value.length, cursorRef.current + 1);
      forceRender(c => c + 1);
      return;
    }

    if (key.upArrow && !disableHistory) {
      const hist = navigateHistory('up');
      if (hist !== null) { onChange(hist); cursorRef.current = hist.length; forceRender(c => c + 1); }
      return;
    }
    if (key.downArrow && !disableHistory) {
      const hist = navigateHistory('down');
      if (hist !== null) { onChange(hist); cursorRef.current = hist.length; forceRender(c => c + 1); }
      return;
    }

    // Any manual edit resets history navigation
    if (histIdxRef.current !== -1 && input) {
      resetHistory();
    }

    if (key.backspace) {
      if (cursorRef.current > 0) {
        var p = cursorRef.current;
        onChange(value.slice(0, p-1) + value.slice(p));
        cursorRef.current = p - 1;
        forceRender(c => c + 1);
        resetHistory();
      }
      return;
    }
    if (key.delete) {
      if (cursorRef.current < value.length) {
        var p = cursorRef.current;
        onChange(value.slice(0, p) + value.slice(p+1));
        resetHistory();
      }
      return;
    }
    if (input && !key.ctrl && !key.meta && !key.tab && !key.escape) {
      var p = cursorRef.current;
      onChange(value.slice(0, p) + input + value.slice(p));
      cursorRef.current = p + input.length;
      forceRender(c => c + 1);
      resetHistory();
    }
  });

  const display = value || '';
  const showPlaceholder = !display && placeholder;
  const tokenEstimate = display ? estimateTokens(display) : 0;
  const charCount = display.length;
  const cursor = Math.min(cursorRef.current, value.length);

  return (
    <Box flexDirection="column">
      <Box>
        {display ? (
          <Text>
            <Text>{value.slice(0, cursor)}</Text>
            <Text inverse>{value[cursor] || ' '}</Text>
            <Text>{value.slice(cursor + 1)}</Text>
          </Text>
        ) : (
          <Text>
            <Text dimColor>{placeholder}</Text>
            <Text dimColor>▊</Text>
          </Text>
        )}
      </Box>
      {charCount > 0 && (
        <Box>
          <Text dimColor>  {charCount} 字符 ≈ {tokenEstimate} tokens</Text>
          <Text dimColor>  [{cursor+1}/{charCount}]</Text>
          {tokenEstimate > 100000 && (
            <Text color="red">  ⚠ 接近上下文上限</Text>
          )}
        </Box>
      )}
    </Box>
  );
}

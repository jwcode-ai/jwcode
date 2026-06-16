import { memo, useRef, useState, useEffect } from 'react';
import { Box, Text, useInput } from 'ink';
import { pasteSummary } from '../pasteBuffer.js';
import { t } from '../theme.js';

// Input history for arrow-key recall
const _history: string[] = [];
const MAX_HISTORY = 100;

export function saveToHistory(text: string): void {
  if (!text) return;
  if (_history[_history.length - 1] === text) return;
  _history.push(text);
  if (_history.length > MAX_HISTORY) _history.shift();
}

function estimateTokens(text: string): number {
  let cjk = 0;
  let other = 0;
  for (const ch of text) {
    if (/[一-鿿㐀-䶿豈-﫿]/.test(ch)) {
      cjk++;
    } else {
      other++;
    }
  }
  return Math.ceil(cjk / 1.5 + other / 4);
}

interface Props {
  value: string;
  onChange: (value: string) => void;
  onSubmit: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
}

export const TextInput = memo(function TextInput({ value, onChange, onSubmit, placeholder, disabled }: Props) {
  const cursorRef = useRef(value.length);
  // Local re-render trigger for cursor-only changes (arrow keys); the parent's
  // `value` prop doesn't change in that case, so memo would otherwise skip us.
  const [, forceRender] = useState(0);
  const valueRef = useRef(value);
  const onChangeRef = useRef(onChange);
  const onSubmitRef = useRef(onSubmit);
  const disabledRef = useRef(disabled);
  const histIdxRef = useRef(-1);
  const draftRef = useRef('');

  // Keep refs in sync so the useInput callback never sees stale closures
  useEffect(() => { valueRef.current = value; });
  useEffect(() => { onChangeRef.current = onChange; });
  useEffect(() => { onSubmitRef.current = onSubmit; });
  useEffect(() => { disabledRef.current = disabled; });

  if (cursorRef.current > value.length) {
    cursorRef.current = value.length;
  }

  const pasteState = useRef<{ accumulating: boolean; buf: string }>({ accumulating: false, buf: '' });

  useInput((input, key) => {
    if (disabledRef.current) return;

    // Ink strips the leading ESC, so we see "[200~" / "[201~"
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
        onChangeRef.current(valueRef.current + label);
      }
      return;
    }

    if (pasteState.current.accumulating && input.includes(PASTE_END)) {
      const parts = input.split(PASTE_END);
      pasteState.current.buf += parts[0] || '';
      pasteState.current.accumulating = false;
      const { label } = pasteSummary(pasteState.current.buf);
      onChangeRef.current(valueRef.current + label);
      return;
    }

    if (pasteState.current.accumulating) {
      pasteState.current.buf += input;
      return;
    }

    if (key.return) {
      onSubmitRef.current(valueRef.current);
      return;
    }

    if (key.leftArrow) {
      cursorRef.current = Math.max(0, cursorRef.current - 1);
      forceRender(c => c + 1);
      return;
    }
    if (key.rightArrow) {
      cursorRef.current = Math.min(valueRef.current.length, cursorRef.current + 1);
      forceRender(c => c + 1);
      return;
    }

    // Up/down: input history recall
    if (key.upArrow) {
      if (_history.length === 0) return;
      if (histIdxRef.current === -1) {
        draftRef.current = valueRef.current;
        histIdxRef.current = 0;
      } else {
        histIdxRef.current = Math.min(histIdxRef.current + 1, _history.length - 1);
      }
      const entry = _history[_history.length - 1 - histIdxRef.current];
      onChangeRef.current(entry);
      cursorRef.current = entry.length;
      forceRender(c => c + 1);
      return;
    }
    if (key.downArrow) {
      if (histIdxRef.current === -1) return;
      histIdxRef.current = histIdxRef.current - 1;
      if (histIdxRef.current < 0) {
        histIdxRef.current = -1;
        onChangeRef.current(draftRef.current);
        cursorRef.current = draftRef.current.length;
      } else {
        const entry = _history[_history.length - 1 - histIdxRef.current];
        onChangeRef.current(entry);
        cursorRef.current = entry.length;
      }
      forceRender(c => c + 1);
      return;
    }

    if (key.backspace) {
      if (cursorRef.current > 0) {
        const p = cursorRef.current;
        const cur = valueRef.current;
        onChangeRef.current(cur.slice(0, p - 1) + cur.slice(p));
        cursorRef.current = p - 1;
        histIdxRef.current = -1;
        forceRender(c => c + 1);
      }
      return;
    }
    if (key.delete && cursorRef.current < valueRef.current.length) {
      const p = cursorRef.current;
      const cur = valueRef.current;
      onChangeRef.current(cur.slice(0, p) + cur.slice(p + 1));
      histIdxRef.current = -1;
      return;
    }
    // Fallback: some terminals send backspace as \x7f (DEL) or \b
    if ((input === '\x7f' || input === '\b' || input === '') && !key.ctrl && !key.meta) {
      if (cursorRef.current > 0) {
        const p = cursorRef.current;
        const cur = valueRef.current;
        onChangeRef.current(cur.slice(0, p - 1) + cur.slice(p));
        cursorRef.current = p - 1;
        histIdxRef.current = -1;
        forceRender(c => c + 1);
      }
      return;
    }
    if (input && !key.ctrl && !key.meta && !key.tab && !key.escape) {
      const p = cursorRef.current;
      const cur = valueRef.current;
      onChangeRef.current(cur.slice(0, p) + input + cur.slice(p));
      cursorRef.current = p + input.length;
      histIdxRef.current = -1;
      forceRender(c => c + 1);
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
            <Text dimColor>{'▶'}</Text>
          </Text>
        )}
      </Box>
      {/* Always render the stats line so TextInput height stays at 2 rows.
          Variable height (1↔2) between renders was the trigger for Ink's
          log-update eraseLines() to desync on Windows, causing frames to
          stack instead of replacing. When empty, the line shows
          "0 chars ~ 0 tokens  [1/0]" which is harmless. */}
      <Box>
        <Text dimColor>  {charCount} chars ~ {tokenEstimate} tokens</Text>
        <Text dimColor>  [{cursor + 1}/{charCount}]</Text>
        {tokenEstimate > 100000 && (
          <Text color={t.error}>  WARN: Approaching context limit</Text>
        )}
      </Box>
    </Box>
  );
});

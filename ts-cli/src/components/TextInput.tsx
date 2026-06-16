import { memo, useRef, useState, useEffect, useCallback } from 'react';
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
    } else if (ch !== '\n') {
      other++;
    }
  }
  return Math.ceil(cjk * 1.5 + other * 0.25);
}

interface Props {
  value: string;
  onChange: (value: string) => void;
  onSubmit: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  isPaletteActive?: boolean;
}

export const TextInput = memo(function TextInput({
  value,
  onChange,
  onSubmit,
  placeholder = '',
  disabled = false,
  isPaletteActive = false,
}: Props) {
  const [cursorOffset, setCursorOffset] = useState(value.length);
  const [historyIdx, setHistoryIdx] = useState(-1);
  const valueRef = useRef(value);
  valueRef.current = value;

  // Keep cursor in bounds
  useEffect(() => {
    setCursorOffset((prev) => Math.min(prev, value.length));
  }, [value.length]);

  // Handle paste detection (Shift+Insert)
  const [shiftInsertCount, setShiftInsertCount] = useState(0);
  const pasteTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useInput(
    (input, key) => {
      if (disabled) return;

      // --- Paste detection: Shift+Insert key sequence ---
      if (key.shift && key.return) {
        setShiftInsertCount((c) => c + 1);
        if (pasteTimerRef.current) clearTimeout(pasteTimerRef.current);
        pasteTimerRef.current = setTimeout(() => setShiftInsertCount(0), 300);
        return;
      }

      // --- Regular input ---
      if (key.return) {
        const val = valueRef.current.trim();
        if (val) {
          setHistoryIdx(-1);
          onSubmit(val);
        }
        return;
      }

      if (key.leftArrow) {
        setCursorOffset((prev) => Math.max(0, prev - 1));
        return;
      }

      if (key.rightArrow) {
        setCursorOffset((prev) => Math.min(valueRef.current.length, prev + 1));
        return;
      }

      if (key.upArrow && !isPaletteActive) {
        const idx = historyIdx < _history.length - 1 ? historyIdx + 1 : historyIdx;
        if (idx < _history.length) {
          setHistoryIdx(idx);
          const histVal = _history[_history.length - 1 - idx];
          onChange(histVal);
          setCursorOffset(histVal.length);
        }
        return;
      }

      if (key.downArrow && !isPaletteActive) {
        if (historyIdx > 0) {
          const idx = historyIdx - 1;
          setHistoryIdx(idx);
          const histVal = _history[_history.length - 1 - idx];
          onChange(histVal);
          setCursorOffset(histVal.length);
        } else {
          setHistoryIdx(-1);
          onChange('');
          setCursorOffset(0);
        }
        return;
      }

      if (key.delete) {
        if (cursorOffset < valueRef.current.length) {
          const newVal = valueRef.current.slice(0, cursorOffset) + valueRef.current.slice(cursorOffset + 1);
          onChange(newVal);
        }
        return;
      }

      if (key.backspace) {
        if (cursorOffset > 0) {
          const newVal = valueRef.current.slice(0, cursorOffset - 1) + valueRef.current.slice(cursorOffset);
          onChange(newVal);
          setCursorOffset((prev) => prev - 1);
        }
        return;
      }

      // Regular character input
      if (input && !key.ctrl && !key.meta && !key.escape) {
        const newVal = valueRef.current.slice(0, cursorOffset) + input + valueRef.current.slice(cursorOffset);
        onChange(newVal);
        setCursorOffset((prev) => prev + input.length);
      }
    },
    { isActive: !disabled },
  );

  // Show placeholder when empty
  const displayText = value || placeholder;
  const isPlaceholder = !value && !!placeholder;
  const tokenCount = estimateTokens(value);
  const showCursor = !disabled;

  return (
    <Box flexGrow={1}>
      <Text>
        {isPlaceholder ? (
          <Text dimColor>{displayText}</Text>
        ) : (
          <Text>{displayText}</Text>
        )}
        {showCursor && (
          <Text color={t.primary} dimColor={false}>
            {'▍'}
          </Text>
        )}
      </Text>
      {value && tokenCount > 0 && (
        <Text dimColor> {tokenCount}t</Text>
      )}
    </Box>
  );
});

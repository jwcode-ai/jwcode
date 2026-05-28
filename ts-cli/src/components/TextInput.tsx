/**
 * TextInput using ink's useInput — positions the real terminal cursor
 * so IME candidate windows track the input position.
 */
import { useState, useEffect, useRef } from 'react';
import { Box, Text, useInput, useStdout } from 'ink';

interface Props {
  value: string;
  onChange: (value: string) => void;
  onSubmit: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
}

export function TextInput({ value, onChange, onSubmit, placeholder, disabled }: Props) {
  const { stdout } = useStdout();
  const valueRef = useRef(value);
  valueRef.current = value;

  // Position the real terminal cursor at the input position so IME can track it.
  // Ink renders from top to bottom; the input sits at a fixed bottom area.
  // We write ANSI codes after Ink's render to move and show the real cursor.
  useEffect(() => {
    const writeCursor = () => {
      const rows = (process.stdout as any)?.rows || 40;
      const cols = (process.stdout as any)?.columns || 120;
      // Input area: 2 lines from bottom (inside border box).
      // Column: left-border(1) + padding(1) + prompt(2) + text-length
      const targetRow = rows - 1;
      const textLen = valueRef.current.length;
      const targetCol = Math.min(4 + textLen, cols - 1);
      stdout.write(`\x1b[${targetRow};${targetCol}H\x1b[?25h`);
    };

    // Write cursor position after each Ink frame
    const interval = setInterval(writeCursor, 120);
    // Also write immediately
    writeCursor();

    return () => {
      clearInterval(interval);
      stdout.write('\x1b[?25l'); // hide cursor on unmount
    };
  }, [stdout]);

  useInput((input, key) => {
    if (disabled) return;
    if (key.return) {
      onSubmit(value);
    } else if (key.backspace || key.delete) {
      onChange(value.slice(0, -1));
    } else if (input && !key.ctrl && !key.meta && !key.tab && !key.escape) {
      onChange(value + input);
    }
  });

  const display = value || '';
  const showPlaceholder = !display && placeholder;

  return (
    <Box>
      <Text dimColor={!showPlaceholder}>
        {showPlaceholder ? placeholder : display}
      </Text>
    </Box>
  );
}

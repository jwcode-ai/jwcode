/**
 * TextInput using ink's useInput — simple visual cursor.
 * IME positioning depends on the terminal emulator, not app-level ANSI codes.
 */
import { Box, Text, useInput } from 'ink';

interface Props {
  value: string;
  onChange: (value: string) => void;
  onSubmit: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
}

export function TextInput({ value, onChange, onSubmit, placeholder, disabled }: Props) {
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
      {display ? <Text>{display}</Text> : <Text dimColor>{placeholder}</Text>}
      <Text dimColor>▊</Text>
    </Box>
  );
}

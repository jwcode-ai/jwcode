/**
 * ApprovalModal — permission prompt in Claude Code style.
 * Arrow keys to select, Enter to confirm, Esc to cancel.
 */
import { useState } from 'react';
import { Box, Text, useInput } from 'ink';

interface Props {
  toolName: string;
  payload: string;
  onAllow: () => void;
  onDeny: () => void;
}

export function ApprovalModal({ toolName, payload, onAllow, onDeny }: Props) {
  const [selected, setSelected] = useState(0); // 0=allow, 1=deny

  useInput((_input, key) => {
    if (key.escape || key.tab) {
      onDeny();
      return;
    }
    if (key.upArrow || key.downArrow) {
      setSelected(prev => prev === 0 ? 1 : 0);
      return;
    }
    if (key.return) {
      if (selected === 0) onAllow();
      else onDeny();
      return;
    }
    if (_input === '1') { onAllow(); return; }
    if (_input === '2') { onDeny(); return; }
    if (_input === 'y' || _input === 'Y') { onAllow(); return; }
    if (_input === 'n' || _input === 'N') { onDeny(); return; }
  });

  const desc = payload
    ? (payload.length > 200 ? payload.slice(0, 200) + '...' : payload)
    : '';

  return (
    <Box flexDirection="column" borderStyle="round" borderColor="yellow" paddingX={2} paddingY={1} marginTop={1}>
      <Box marginBottom={1}>
        <Text bold>Do you want to proceed?</Text>
      </Box>
      <Box flexDirection="column" marginLeft={2} marginBottom={1}>
        <Box>
          <Text color={selected === 0 ? 'green' : undefined}>
            {selected === 0 ? ' ❯' : '  '} 1. Allow
          </Text>
        </Box>
        <Box>
          <Text color={selected === 1 ? 'red' : undefined}>
            {selected === 1 ? ' ❯' : '  '} 2. Deny
          </Text>
        </Box>
      </Box>
      <Box marginBottom={1}>
        <Text dimColor>Tool: </Text>
        <Text color="cyan">{toolName}</Text>
        {desc ? <Text dimColor>  {desc}</Text> : null}
      </Box>
      <Box>
        <Text dimColor> Esc to cancel · </Text>
        <Text dimColor>↑↓ to select · </Text>
        <Text dimColor>Enter to confirm</Text>
      </Box>
    </Box>
  );
}

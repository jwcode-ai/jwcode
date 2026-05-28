/**
 * StatusLine — top bar showing model, token usage, plan indicator.
 */
import { Box, Text } from 'ink';
import { useAppState } from '../hooks/useAppState.js';

function formatTokens(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${Math.round(n / 1_000)}K`;
  return String(n);
}

export function StatusLine() {
  const state = useAppState();
  const { usage, modelName, planMode, autoMode, connected, statusText } = state;

  const pct = Math.min(100, Math.round(usage.usageRatio * 100));
  const filled = Math.round(pct / 10);
  const bar = '='.repeat(filled) + '-'.repeat(10 - filled);
  const model = modelName || (connected ? 'ready' : 'connecting...');
  const plan = planMode ? ' [PLAN]' : '';
  const auto = autoMode ? ' [AUTO]' : '';
  const isError = statusText.startsWith('Error:');

  return (
    <Box flexDirection="column" width="100%" paddingRight={1}>
      <Box height={1}>
        <Text bold color="cyan">jwcode</Text>
        <Text color="yellow">{plan}</Text>
        <Text color="magenta">{auto}</Text>
        <Text>   </Text>
        <Text color="green">{model}</Text>
        <Text>   tokens: </Text>
        <Text color="yellow">{formatTokens(usage.totalTokens)}</Text>
        <Text>  </Text>
        <Text color={pct > 90 ? 'red' : 'white'}>{bar} {pct}%</Text>
      </Box>
      {statusText && statusText !== 'connecting...' && (
        <Box height={1}>
          <Text color={isError ? 'red' : 'grey'} dimColor={!isError}>
            {statusText.slice(0, 100)}
          </Text>
        </Box>
      )}
    </Box>
  );
}

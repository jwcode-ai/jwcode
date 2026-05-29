/**
 * ChatArea — renders message list with markdown, tool calls, steps, thinking.
 * Shows newest messages at the bottom; uses scrollOffset to page through history.
 */
import { Box, Text } from 'ink';
import { type Message, type ToolCall, type Step } from '../protocol.js';

const SEP = '─'.repeat(60);

interface Props {
  messages: Message[];
  currentMessage: Message | null;
  scrollOffset: number;
  terminalRows: number;
  reservedRows: number;
}

export function ChatArea({ messages, currentMessage, scrollOffset, terminalRows, reservedRows }: Props) {
  const allMessages = currentMessage
    ? [...messages.filter(m => m.id !== currentMessage.id)]
    : messages;

  const availableRows = Math.max(10, terminalRows - reservedRows);
  const maxVisible = Math.max(5, Math.floor(availableRows / 4));
  const total = allMessages.length;

  // scrollOffset = how many messages scrolled above the bottom
  // 0 = at bottom (show newest), N = N messages scrolled up
  const clampedOffset = Math.min(scrollOffset, total - 1);
  const end = total - clampedOffset;
  const start = Math.max(0, end - maxVisible);
  const visibleMessages = allMessages.slice(start, end);

  const isScrolledUp = clampedOffset > 0;
  const hiddenAbove = start;
  const hiddenBelow = clampedOffset;

  return (
    <Box flexDirection="column" width="100%">
      {isScrolledUp && (
        <Box>
          <Text color="yellow" dimColor>
            ▲ [{start + 1}-{end}/{total}] 上翻中 (PgUp/PgDn 翻页, Home 开头, End 最新)
          </Text>
        </Box>
      )}
      {!isScrolledUp && total > maxVisible && (
        <Box>
          <Text color="grey" dimColor>
            [{start + 1}-{end}/{total}] ↑/PgUp 查看更早消息
          </Text>
        </Box>
      )}
      {visibleMessages.map(msg => (
        <Box key={msg.id} flexDirection="column" marginBottom={1}>
          {msg.type === 'user' && (
            <Box flexDirection="column">
              <Text dimColor>{SEP}</Text>
              <Text color="green" bold>&gt; {msg.content}</Text>
            </Box>
          )}
          {msg.type === 'assistant' && (
            <Box flexDirection="column">
              <Text>{' '}</Text>
              {msg.steps.map((step, i) => (
                <StepDisplay key={step.id || i} step={step} />
              ))}
              {msg.thinking && (
                <Text dimColor italic>{truncate(msg.thinking, 200)}</Text>
              )}
              {msg.toolCalls.map((tc, i) => (
                <ToolCallDisplay key={tc.id || i} tc={tc} />
              ))}
              {msg.content && <Text>{msg.content}</Text>}
              <Text dimColor>{SEP}</Text>
            </Box>
          )}
          {msg.type === 'system' && (
            <Box>
              <Text color="red">Error: {msg.content}</Text>
            </Box>
          )}
        </Box>
      ))}
      {currentMessage && (
        <Box key={currentMessage.id} flexDirection="column">
          {currentMessage.thinking && (
            <Text dimColor italic>{truncate(currentMessage.thinking, 200)}</Text>
          )}
          {currentMessage.toolCalls.map((tc, i) => (
            <ToolCallDisplay key={tc.id || i} tc={tc} />
          ))}
          {currentMessage.content && <Text>{currentMessage.content}</Text>}
        </Box>
      )}
    </Box>
  );
}

function StepDisplay({ step }: { step: Step }) {
  const icon = step.status === 'success' ? '✓' : step.status === 'error' ? '✗' : '▶';
  const color = step.status === 'success' ? 'green' : step.status === 'error' ? 'red' : 'cyan';
  return (
    <Box flexDirection="column">
      <Text color={color}>  {icon} {step.title}</Text>
      {step.thought && <Text color="blue" dimColor>    {truncate(step.thought, 200)}</Text>}
      {step.action && <Text color="yellow">    {truncate(step.action, 200)}</Text>}
      {step.result && <Text color="green">    {truncate(step.result, 300)}</Text>}
    </Box>
  );
}

function ToolCallDisplay({ tc }: { tc: ToolCall }) {
  const argsStr = tc.args ? truncate(formatJson(tc.args), 200) : '';
  const statusIcon = tc.status === 'complete' ? '✓' : tc.status === 'running' ? '◷' : '✗';
  const statusColor = tc.status === 'complete' ? 'green' : tc.status === 'running' ? 'yellow' : 'red';
  return (
    <Box flexDirection="column" paddingLeft={1}>
      <Box>
        <Text color={statusColor}>  {statusIcon} </Text>
        <Text bold color="magenta">{tc.name}</Text>
        {argsStr && <Text dimColor>  {argsStr}</Text>}
      </Box>
      {tc.result && (
        <Box paddingLeft={4}>
          <Text color="green" dimColor>{truncate(tc.result, 200)}</Text>
        </Box>
      )}
    </Box>
  );
}

function formatJson(s: string): string {
  try {
    return JSON.stringify(JSON.parse(s), null, 2);
  } catch {
    return s;
  }
}

function truncate(s: string, max: number): string {
  if (s.length <= max) return s;
  return s.slice(0, max) + '...';
}

/**
 * ChatArea — enhanced virtual-scroll message list with markdown, tool calls, steps, thinking.
 *
 * Enhancements:
 * - Message size estimation for better window fit
 * - Expand/collapse long tool results
 * - Adaptive rendering based on terminal height
 * - Smoother scroll indicator with percentage
 * - Live elapsed timer for running tool calls
 * - Duration display for completed tool calls
 * - Improved step display with status and timing
 */
import { Box, Text } from 'ink';
import { useState, useEffect } from 'react';
import { type Message, type ToolCall, type Step } from '../protocol.js';

const SEP = '─'.repeat(60);
const MAX_TOOL_RESULT = 300;
const MAX_THINKING = 200;
const MAX_CONTENT_PREVIEW = 500;

interface Props {
  messages: Message[];
  currentMessage: Message | null;
  scrollOffset: number;
  terminalRows: number;
  reservedRows: number;
}

function formatDuration(sec: number): string {
  if (sec <= 0) return '';
  if (sec >= 60) {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m}m${s}s`;
  }
  return `${sec}s`;
}

export function ChatArea({ messages, currentMessage, scrollOffset, terminalRows, reservedRows }: Props) {
  const [expandedTools, setExpandedTools] = useState<Set<string>>(new Set());
  const [expandedMessages, setExpandedMessages] = useState<Set<string>>(new Set());
  const [, setTick] = useState(0); // force re-render every second for elapsed timers

  // Re-render every second for live elapsed timers
  useEffect(() => {
    const timer = setInterval(() => setTick(t => t + 1), 1000);
    return () => clearInterval(timer);
  }, []);

  const allMessages = currentMessage
    ? [...messages.filter(m => m.id !== currentMessage.id)]
    : messages;

  const availableRows = Math.max(10, terminalRows - reservedRows);
  const linesPerMessage = availableRows > 60 ? 3 : availableRows > 30 ? 4 : 5;
  const maxVisible = Math.max(5, Math.floor(availableRows / linesPerMessage));
  const total = allMessages.length;

  const clampedOffset = Math.min(scrollOffset, Math.max(0, total - 1));
  const end = total - clampedOffset;
  const start = Math.max(0, end - maxVisible);
  const visibleMessages = allMessages.slice(start, end);

  const isScrolledUp = clampedOffset > 0;
  const scrollPercent = total > 0 ? Math.round(((total - end) / total) * 100) : 0;

  const toggleExpandTool = (id: string) => {
    setExpandedTools(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const toggleExpandMessage = (id: string) => {
    setExpandedMessages(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  return (
    <Box flexDirection="column" width="100%">
      {total > 0 && (
        <Box>
          <Text color="grey" dimColor>
            [{start + 1}-{end}/{total}] {scrollPercent}%
            {isScrolledUp ? ' ▲ PgUp/PgDn·Home·End' : ' ↑/PgUp for older'}
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
                <Box flexDirection="column">
                  <Text dimColor italic>
                    {expandedMessages.has(msg.id)
                      ? msg.thinking
                      : truncate(msg.thinking, MAX_THINKING)}
                  </Text>
                  {msg.thinking.length > MAX_THINKING && (
                    <Text color="blue" dimColor>
                      [{msg.thinking.length - MAX_THINKING} more chars]
                      <Text color="cyan" dimColor>
                        {' '}[Tab to expand]
                      </Text>
                    </Text>
                  )}
                </Box>
              )}
              {msg.toolCalls.map((tc, i) => (
                <ToolCallDisplay
                  key={tc.id || i}
                  tc={tc}
                  expanded={expandedTools.has(tc.id || `${msg.id}-${i}`)}
                  onToggle={() => toggleExpandTool(tc.id || `${msg.id}-${i}`)}
                />
              ))}
              {msg.content && (
                <Box flexDirection="column">
                  <Text>{msg.content.length > MAX_CONTENT_PREVIEW && !expandedMessages.has(msg.id)
                    ? msg.content.substring(0, MAX_CONTENT_PREVIEW) + '...'
                    : msg.content
                  }</Text>
                  {msg.content.length > MAX_CONTENT_PREVIEW && (
                    <Text color="blue" dimColor>
                      [{msg.content.length - MAX_CONTENT_PREVIEW} more chars — use /expand to view full]
                    </Text>
                  )}
                </Box>
              )}
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
            <Text dimColor italic>{truncate(currentMessage.thinking, MAX_THINKING)}</Text>
          )}
          {currentMessage.toolCalls.map((tc, i) => (
            <Box key={tc.id || i} flexDirection="column" paddingLeft={1}>
              <Box>
                <Text color="yellow">  ◷ </Text>
                <Text bold color="magenta">{tc.name}</Text>
                {tc.args && <Text dimColor>  {truncate(formatJson(tc.args), 120)}</Text>}
                {tc.timestamp && (
                  <Text color="cyan">  {formatDuration(Math.floor((Date.now() - tc.timestamp) / 1000))}</Text>
                )}
              </Box>
            </Box>
          ))}
          {currentMessage.content && <Text>{currentMessage.content}</Text>}
        </Box>
      )}
    </Box>
  );
}

function StepDisplay({ step }: { step: Step }) {
  const icon = step.status === 'success' ? '✓' : step.status === 'error' ? '✗'
    : step.status === 'running' ? '⟳' : '▶';
  const color = step.status === 'success' ? 'green' : step.status === 'error' ? 'red'
    : step.status === 'running' ? 'cyan' : 'cyan';

  const durStr = step.duration
    ? formatDuration(step.duration)
    : step.status === 'running' && step.timestamp
      ? formatDuration(Math.floor((Date.now() - step.timestamp) / 1000))
      : '';

  return (
    <Box flexDirection="column">
      <Box>
        <Text color={color}>  {icon} </Text>
        <Text bold color={color}>{step.title}</Text>
        {durStr && (
          <>
            <Text dimColor>  </Text>
            <Text color="grey" dimColor>{durStr}</Text>
          </>
        )}
      </Box>
      {step.thought && <Text color="blue" dimColor>    {truncate(step.thought, 200)}</Text>}
      {step.action && <Text color="yellow">    {truncate(step.action, 200)}</Text>}
      {step.result && <Text color="green">    {truncate(step.result, 300)}</Text>}
    </Box>
  );
}

function ToolCallDisplay({ tc, expanded, onToggle }: { tc: ToolCall; expanded: boolean; onToggle: () => void }) {
  const argsStr = tc.args ? truncate(formatJson(tc.args), 200) : '';
  const statusIcon = tc.status === 'complete' ? '✓' : tc.status === 'running' ? '⟳' : '✗';
  const statusColor = tc.status === 'complete' ? 'green' : tc.status === 'running' ? 'yellow' : 'red';
  const resultPreview = tc.result ? (expanded ? tc.result : truncate(tc.result, MAX_TOOL_RESULT)) : null;
  const hasMore = tc.result && tc.result.length > MAX_TOOL_RESULT;

  // Duration: use stored duration, or compute elapsed for running tools
  const durStr = tc.duration
    ? formatDuration(tc.duration)
    : tc.status === 'running' && tc.timestamp
      ? formatDuration(Math.floor((Date.now() - tc.timestamp) / 1000))
      : '';

  return (
    <Box flexDirection="column" paddingLeft={1}>
      <Box>
        <Text color={statusColor}>  {statusIcon} </Text>
        <Text bold color="magenta">{tc.name}</Text>
        {argsStr && <Text dimColor>  {argsStr}</Text>}
        {durStr && (
          <>
            <Text dimColor>  </Text>
            <Text color="grey" dimColor>{durStr}</Text>
          </>
        )}
        {hasMore && (
          <Text color="cyan" dimColor> [{tc.result!.length - MAX_TOOL_RESULT} more]</Text>
        )}
      </Box>
      {resultPreview && (
        <Box paddingLeft={4} flexDirection="column">
          <Text color={tc.status === 'error' ? 'red' : 'green'} dimColor>{resultPreview}</Text>
          {hasMore && (
            <Text color="blue" dimColor>
              [{expanded ? 'Collapse' : 'Expand'} with Tab]
            </Text>
          )}
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

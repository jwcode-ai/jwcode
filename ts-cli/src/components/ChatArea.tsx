import { Box, Text } from 'ink';
import { useState, useMemo, memo, useEffect } from 'react';
import { type Message, type ToolCall, type Step } from '../protocol.js';
import { updateAppState, useAppChatArea, useAppSlice } from '../hooks/useAppState.js';
import { DiffDisplay } from './DiffDisplay.js';
import { t } from '../theme.js';

const SEP = '-'.repeat(60);
const MAX_VISIBLE_THINKING = 200;

interface Props {
  terminalCols: number;
  terminalRows: number;
}

function formatDuration(sec: number): string {
  if (sec <= 0) return '';
  if (sec >= 60) {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return m + 'm' + s + 's';
  }
  return sec + 's';
}

/**
 * Detect whether text content contains a unified diff.
 * Checks for file headers (---/+++) or hunk headers (@@) with a min change count.
 */
function isDiffContent(text: string): boolean {
  if (!text || text.length < 20) return false;
  const lines = text.split('\n');
  let hasFileHeader = false;
  let hasHunkHeader = false;
  let changeCount = 0;
  for (const line of lines) {
    if (/^--- .+\/|^\+\+\+ .+\//.test(line)) hasFileHeader = true;
    if (/^@@ -\d+,\d+ +\d+,\d+ @@/.test(line)) hasHunkHeader = true;
    if (/^[+-]/.test(line) && !/^[+-]{3}/.test(line) && !/^[+-]{4}/.test(line)) changeCount++;
  }
  return (hasFileHeader || hasHunkHeader) && changeCount >= 2;
}

function shouldStartCollapsed(toolCalls: ToolCall[], index: number): boolean {
  const tc = toolCalls[index];
  if (!tc) return true;
  if (tc.status === 'running') return false;
  let lastFinishedIdx = -1;
  for (let i = toolCalls.length - 1; i >= 0; i--) {
    if (toolCalls[i].status === 'complete' || toolCalls[i].status === 'error') {
      lastFinishedIdx = i;
      break;
    }
  }
  return index !== lastFinishedIdx;
}

// ---- Sub-components ----

/**
 * Render message content as DiffDisplay if it looks like a unified diff,
 * otherwise as plain text.
 */
function MessageContent({ content, terminalCols }: { content: string; terminalCols: number }) {
  if (isDiffContent(content)) {
    return <DiffDisplay content={content} terminalCols={terminalCols} />;
  }
  return <Text>{content}</Text>;
}

/**
 * Render tool result ¡ª use DiffDisplay for diffs, otherwise truncate.
 */
function ToolResult({ result, terminalCols }: { result: string; terminalCols: number }) {
  if (isDiffContent(result)) {
    const lines = result.split('\n');
    const addCount = lines.filter(l => l.startsWith('+') && !l.startsWith('+++')).length;
    const delCount = lines.filter(l => l.startsWith('-') && !l.startsWith('---')).length;
    const summary = `+${addCount}/-${delCount}`;
    const truncated = result.length > 2000;
    const display = truncated ? result.slice(0, 2000) + '\n... (truncated)' : result;
    return (
      <Box flexDirection="column" paddingLeft={2}>
        <Text dimColor>Diff: {summary}</Text>
        <DiffDisplay content={display} terminalCols={terminalCols} />
      </Box>
    );
  }
  const truncated = result.length > 500 ? result.slice(0, 500) + '...' : result;
  return <Text color={t.muted} dimColor>{truncated}</Text>;
}

const StepDisplay = memo(function StepDisplay({ step }: { step: Step }) {
  const icon = step.status === 'success' ? '[ok]' : step.status === 'error' ? '[!!]'
    : step.status === 'running' ? '[..]' : '[--]';
  const color = step.status === 'success' ? t.success : step.status === 'error' ? t.error
    : step.status === 'running' ? t.primary : t.primary;
  const durStr = step.duration
    ? formatDuration(step.duration)
    : step.status === 'running' && step.timestamp
      ? formatDuration(Math.floor((Date.now() - step.timestamp) / 1000))
      : '';
  return (
    <Box flexDirection="column">
      <Box>
        <Text color={color}>{'  '}{icon}{' '}</Text>
        <Text bold color={color}>{step.title}</Text>
        {durStr && (
          <><Text dimColor>  </Text><Text color={t.muted} dimColor>{durStr}</Text></>
        )}
      </Box>
      {step.thought && <Text color={t.info} dimColor>{'    '}{truncate(step.thought, 200)}</Text>}
      {step.action && <Text color={t.warning}>{'    '}{truncate(step.action, 200)}</Text>}
      {step.result && <Text color={t.success}>{'    '}{truncate(step.result, 300)}</Text>}
    </Box>
  );
});

const ToolCallDisplay = memo(function ToolCallDisplay({
  tc, collapsed, onToggle, terminalCols,
}: {
  tc: ToolCall;
  collapsed: boolean;
  onToggle: () => void;
  terminalCols: number;
}) {
  const statusIcon = tc.status === 'complete' ? '[ok]' : tc.status === 'running' ? '[..]' : '[!!]';
  const statusColor = tc.status === 'complete' ? t.success : tc.status === 'running' ? t.warning : t.error;
  const durStr = tc.duration
    ? formatDuration(tc.duration)
    : tc.status === 'running' && tc.timestamp
      ? formatDuration(Math.floor((Date.now() - tc.timestamp) / 1000))
      : '';
  return (
    <Box flexDirection="column" paddingLeft={1}>
      <Box>
        <Text color={statusColor}>{'  '}{statusIcon}{' '}</Text>
        <Text bold color={t.tool}>{tc.name}</Text>
        {durStr && (<><Text dimColor>  </Text><Text color={t.muted} dimColor>{durStr}</Text></>)}
        <Text dimColor>  </Text>
        <Text color={t.info} dimColor>[{collapsed ? '+' : '-'}]</Text>
      </Box>
      {!collapsed && tc.args && (
        <Box paddingLeft={4}><Text dimColor>{truncate(formatJson(tc.args), 200)}</Text></Box>
      )}
      {tc.result && (
        <Box paddingLeft={2} flexDirection="column">
          <ToolResult result={tc.result} terminalCols={terminalCols} />
        </Box>
      )}
    </Box>
  );
});

const MessageItem = memo(function MessageItem({
  msg, expandedMessages, expandedTools, toolCallsExpanded,
  onToggleTool, onToggleMessage, terminalCols,
}: {
  msg: Message;
  expandedMessages: Set<string>;
  expandedTools: Set<string>;
  toolCallsExpanded: boolean;
  onToggleTool: (id: string) => void;
  onToggleMessage: (id: string) => void;
  terminalCols: number;
}) {
  return (
    <Box flexDirection="column" marginBottom={1}>
      {msg.type === 'user' && (
        <Box flexDirection="column">
          <Text dimColor>{SEP}</Text>
          <Text color={t.user} bold>{'>'} {msg.content}</Text>
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
                  : truncate(msg.thinking, MAX_VISIBLE_THINKING)}
              </Text>
              {msg.thinking.length > MAX_VISIBLE_THINKING && (
                <Text dimColor>
                  [{msg.thinking.length - MAX_VISIBLE_THINKING} more chars]
                  {' -> to expand'}
                </Text>
              )}
            </Box>
          )}
          {msg.toolCalls.map((tc, i) => {
            const key = tc.id || tc.name || 'tool-' + msg.id + '-' + i;
            const startCollapsed = shouldStartCollapsed(msg.toolCalls, i);
            const isExpanded = expandedTools.has(key);
            return (
              <ToolCallDisplay
                key={key}
                tc={tc}
                terminalCols={terminalCols}
                collapsed={toolCallsExpanded ? false : (isExpanded ? false : startCollapsed)}
                onToggle={() => onToggleTool(key)}
              />
            );
          })}
          {msg.content && (
            <Box paddingLeft={1}>
              <MessageContent content={msg.content} terminalCols={terminalCols} />
            </Box>
          )}
          <Text dimColor>{SEP}</Text>
        </Box>
      )}
      {msg.type === 'system' && (
        <Box><Text color={t.error}>Error: {msg.content}</Text></Box>
      )}
    </Box>
  );
}, (prev, next) => {
  return prev.msg.id === next.msg.id
    && prev.msg.content === next.msg.content
    && prev.msg.thinking === next.msg.thinking
    && prev.msg.type === next.msg.type
    && prev.expandedMessages.has(prev.msg.id) === next.expandedMessages.has(next.msg.id)
    && prev.toolCallsExpanded === next.toolCallsExpanded
    && prev.terminalCols === next.terminalCols;
});

const StreamingMessage = memo(function StreamingMessage({
  msg, expandedTools, toolCallsExpanded, onToggleTool, terminalCols,
}: {
  msg: Message;
  expandedTools: Set<string>;
  toolCallsExpanded: boolean;
  onToggleTool: (id: string) => void;
  terminalCols: number;
}) {
  return (
    <Box flexDirection="column">
      {msg.thinking && (
        <Text dimColor italic>{truncate(msg.thinking, MAX_VISIBLE_THINKING)}</Text>
      )}
      {msg.toolCalls.map((tc, i) => {
        const key = tc.id || tc.name || 'tool-' + msg.id + '-' + i;
        const startCollapsed = shouldStartCollapsed(msg.toolCalls, i);
        const isExpanded = expandedTools.has(key);
        return (
          <ToolCallDisplay
            key={key}
            tc={tc}
            terminalCols={terminalCols}
            collapsed={toolCallsExpanded ? false : (isExpanded ? false : startCollapsed)}
            onToggle={() => onToggleTool(key)}
          />
        );
      })}
      {msg.content && (
        <Box paddingLeft={1}>
          <MessageContent content={msg.content} terminalCols={terminalCols} />
        </Box>
      )}
    </Box>
  );
});

export const ChatArea = memo(function ChatArea({
  terminalCols, terminalRows,
}: Props) {
  const { messages, currentMessage, scrollOffset } = useAppChatArea();
  const toolCallsExpanded = useAppSlice(s => s.toolCallsExpanded);
  const [expandedTools, setExpandedTools] = useState<Set<string>>(new Set());
  const [expandedMessages, setExpandedMessages] = useState<Set<string>>(new Set());

  const allMessages = useMemo(() =>
    currentMessage
      ? messages.filter(m => m.id !== currentMessage.id)
      : messages,
    [messages, currentMessage && currentMessage.id]);

  const maxVisible = Math.max(5, terminalRows - 10);
  const total = allMessages.length;
  const maxScroll = Math.max(0, total - maxVisible);
  const safeOffset = Math.min(scrollOffset, maxScroll);

  useEffect(() => {
    if (scrollOffset > maxScroll) {
      updateAppState(prev => ({ ...prev, scrollOffset: maxScroll }));
    }
  }, [scrollOffset, maxScroll]);

  const startIdx = Math.max(0, total - maxVisible - safeOffset);
  const endIdx = total - safeOffset;
  const visibleMessages = allMessages.slice(startIdx, endIdx > 0 ? endIdx : undefined);
  const visibleCount = visibleMessages.length;

  const toggleExpandTool = (id: string) => {
    setExpandedTools(prev => { const next = new Set(prev); if (next.has(id)) next.delete(id); else next.add(id); return next; });
  };

  const toggleExpandMessage = (id: string) => {
    setExpandedMessages(prev => { const next = new Set(prev); if (next.has(id)) next.delete(id); else next.add(id); return next; });
  };

  return (
    <Box flexDirection="column" width="100%">
      {total > maxVisible && (
        <Box>
          <Text dimColor>
            [{safeOffset + 1}-{safeOffset + visibleCount} / {total}]
          </Text>
          <Text color={t.warning}>
            {'¨€'.repeat(Math.min(10, Math.round((1 - safeOffset / (maxScroll || 1)) * 10)))}
          </Text>
          <Text dimColor>
            {'?'.repeat(10 - Math.min(10, Math.round((1 - safeOffset / (maxScroll || 1)) * 10)))}
          </Text>
          <Text dimColor>  PgUp/PgDn ¡ü¡ý  Home/End</Text>
        </Box>
      )}
      {visibleMessages.map(msg => (
        <MessageItem
          key={msg.id}
          msg={msg}
          expandedMessages={expandedMessages}
          expandedTools={expandedTools}
          terminalCols={terminalCols}
          toolCallsExpanded={toolCallsExpanded}
          onToggleTool={toggleExpandTool}
          onToggleMessage={toggleExpandMessage}
        />
      ))}
      {currentMessage && (
        <StreamingMessage
          key={currentMessage.id}
          msg={currentMessage}
          terminalCols={terminalCols}
          expandedTools={expandedTools}
          toolCallsExpanded={toolCallsExpanded}
          onToggleTool={toggleExpandTool}
        />
      )}
    </Box>
  );
});

function formatJson(s: unknown): string {
  if (typeof s !== 'string') return JSON.stringify(s, null, 2);
  try { return JSON.stringify(JSON.parse(s), null, 2); }
  catch { return s; }
}

function truncate(s: unknown, max: number): string {
  const str = typeof s === 'string' ? s : String(s ?? '');
  if (str.length <= max) return str;
  return str.slice(0, max) + '...';
}

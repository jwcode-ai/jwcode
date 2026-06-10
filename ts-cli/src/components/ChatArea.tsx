import { Box, Text } from 'ink';
import { useState, useMemo, useRef, useLayoutEffect, memo, forwardRef } from 'react';
import { measureElement, type DOMElement } from 'ink';
import { type Message, type ToolCall, type Step } from '../protocol.js';
import { updateAppState, useAppChatArea, useAppSlice } from '../hooks/useAppState.js';
import { setScrollGeometry } from '../hooks/useMouseWheel.js';
import { DiffDisplay } from './DiffDisplay.js';
import { MarkdownRenderer } from './MarkdownRenderer.js';
import { t } from '../theme.js';

const SEP = '-'.repeat(60);
const MAX_VISIBLE_THINKING = 200;
const RESERVED_ROWS = 6; // StatusLine 3-4 rows + TextInput 1 row + borders/padding

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

// ---- Scrollbar ----

const SCROLLBAR_TRACK = '·';
const SCROLLBAR_THUMB = '#';
const SCROLLBAR_ARROW_UP = '^';
const SCROLLBAR_ARROW_DOWN = 'v';

function Scrollbar({
  contentHeight, offset, viewportHeight,
}: {
  contentHeight: number; offset: number; viewportHeight: number;
}) {
  // outer ChatArea decides whether to render this — we just draw the bar.
  const trackHeight = Math.max(2, viewportHeight - 2);
  const thumbH = Math.max(1, Math.floor(trackHeight * viewportHeight / Math.max(contentHeight, 1)));
  const maxOffset = Math.max(0, contentHeight - viewportHeight);
  const thumbPos = maxOffset > 0
    ? Math.round((trackHeight - thumbH) * offset / maxOffset)
    : 0;

  const chars: string[] = [];
  for (let i = 0; i < trackHeight; i++) {
    if (i >= thumbPos && i < thumbPos + thumbH) {
      chars.push(SCROLLBAR_THUMB);
    } else {
      chars.push(SCROLLBAR_TRACK);
    }
  }

  return (
    <Box flexDirection="column" width={2} flexShrink={0} minWidth={2}>
      <Text color={t.border}>{SCROLLBAR_ARROW_UP}</Text>
      {chars.map((c, i) => (
        <Text key={i} color={c === SCROLLBAR_THUMB ? t.text : t.border}>{c}</Text>
      ))}
      <Text color={t.border}>{SCROLLBAR_ARROW_DOWN}</Text>
    </Box>
  );
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
  return <MarkdownRenderer content={content} terminalCols={terminalCols} />;
}

/**
 * Render tool result — use DiffDisplay for diffs, otherwise truncate.
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

const MessageItem = memo(forwardRef<DOMElement, {
  msg: Message;
  expandedMessages: Set<string>;
  expandedTools: Set<string>;
  toolCallsExpanded: boolean;
  onToggleTool: (id: string) => void;
  onToggleMessage: (id: string) => void;
  terminalCols: number;
}>(function MessageItem({
  msg, expandedMessages, expandedTools, toolCallsExpanded,
  onToggleTool, onToggleMessage, terminalCols,
}, ref) {
  return (
    <Box flexDirection="column" marginBottom={1} ref={ref}>
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
}), (prev, next) => {
  return prev.msg.id === next.msg.id
    && prev.msg.content === next.msg.content
    && prev.msg.thinking === next.msg.thinking
    && prev.msg.type === next.msg.type
    && prev.expandedMessages.has(prev.msg.id) === next.expandedMessages.has(next.msg.id)
    && prev.toolCallsExpanded === next.toolCallsExpanded
    && prev.terminalCols === next.terminalCols;
});

const StreamingMessage = memo(forwardRef<DOMElement, {
  msg: Message;
  expandedTools: Set<string>;
  toolCallsExpanded: boolean;
  onToggleTool: (id: string) => void;
  terminalCols: number;
}>(function StreamingMessage({
  msg, expandedTools, toolCallsExpanded, onToggleTool, terminalCols,
}, ref) {
  return (
    <Box flexDirection="column" ref={ref}>
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
}));

// Heuristic fallback height for a message whose DOM node has not been
// measured yet. After the first useLayoutEffect the real measured height
// replaces this value.
function defaultMessageHeight(msg: Message, terminalCols: number): number {
  if (msg.type === 'user') return 3; // sep + content + bottom margin
  // assistant: thinking + step + tool call + content + separators
  let h = 4;
  if (msg.thinking) h += Math.ceil(msg.thinking.length / Math.max(1, terminalCols)) + 1;
  h += msg.steps.length;
  for (const tc of msg.toolCalls) {
    h += 2;
    if (tc.result) h += Math.min(20, Math.ceil(tc.result.length / Math.max(1, terminalCols)));
  }
  if (msg.content) h += Math.ceil(msg.content.length / Math.max(1, terminalCols));
  return h;
}

// Walk up the Yoga tree to get the absolute top row of `node` in the terminal.
function getAbsoluteTopRow(node: DOMElement | null): number {
  let cur: DOMElement | null = node;
  let top = 0;
  while (cur) {
    const y: any = (cur as any).yogaNode;
    if (!y || typeof y.getComputedTop !== 'function') break;
    top += y.getComputedTop();
    cur = (cur as any).parentNode ?? null;
  }
  return top;
}

export const ChatArea = memo(function ChatArea({
  terminalCols, terminalRows,
}: Props) {
  const { messages, currentMessage, scrollOffset } = useAppChatArea();
  const toolCallsExpanded = useAppSlice(s => s.toolCallsExpanded);
  const [expandedTools, setExpandedTools] = useState<Set<string>>(new Set());
  const [expandedMessages, setExpandedMessages] = useState<Set<string>>(new Set());
  const [, setMeasureTick] = useState(0);

  // viewport = visible area in terminal rows for the message list
  const viewportHeight = Math.max(3, terminalRows - RESERVED_ROWS);

  // Map<id, DOMElement> of currently rendered (sliced) message nodes.
  // Plain ref so updating it does not cause a re-render.
  const messageRefs = useRef<Map<string, DOMElement>>(new Map());
  // Map<id, number> of last measured height per message.
  const messageHeightsRef = useRef<Map<string, number>>(new Map());
  // Last reported contentHeight — used to avoid redundant re-renders.
  const lastTotalRef = useRef<number>(0);
  // Ref to the outer row <Box> — used to compute absolute top row of the scrollbar.
  const viewportRef = useRef<DOMElement | null>(null);
  // Force re-measure on terminal resize (parent re-renders pass new terminalRows).

  const allMessages = useMemo(() =>
    currentMessage
      ? messages.filter(m => m.id !== currentMessage.id)
      : messages,
    [messages, currentMessage && currentMessage.id]);

  // --- measurement loop ---
  // Runs after every render where messages / currentMessage / measureTick change.
  // measureElement() reads Yoga's computed height (Ink runs Yoga before
  // useLayoutEffect), so values are accurate on the very first measurement.
  useLayoutEffect(() => {
    let total = 0;
    const refs = messageRefs.current;
    const heights = messageHeightsRef.current;
    const cols = Math.max(10, terminalCols - 4);

    // Sum measured heights for currently-sliced (rendered) messages.
    for (const [id, node] of refs) {
      try {
        const { height } = measureElement(node);
        if (height > 0) {
          heights.set(id, height);
          total += height;
        }
      } catch {
        // node not yet attached — skip; previous value (or default) holds
      }
    }
    // Add fallback heights for messages that exist but are NOT in the current
    // slice (i.e. above the visible window). This lets the scrollbar's
    // `contentHeight` reflect the full document even when only a subset is
    // mounted.
    for (const m of allMessages) {
      if (!refs.has(m.id)) {
        const h = heights.get(m.id) ?? defaultMessageHeight(m, cols);
        heights.set(m.id, h);
        total += h;
      }
    }
    if (currentMessage) {
      const cm = currentMessage;
      const h = heights.get(cm.id) ?? defaultMessageHeight(cm, cols);
      heights.set(cm.id, h);
      total += h;
    }

    // Reserve one extra line for the `[N/M]` header above the message list.
    total += 1;

    if (total !== lastTotalRef.current) {
      lastTotalRef.current = total;
      updateAppState(prev => (prev.contentHeight === total ? prev : { ...prev, contentHeight: total }));
    }

    // Publish geometry to mouse handler. The viewport's absolute top row is
    // its own yogaNode top + 1 (for the [N/M] header line).
    const topRow = getAbsoluteTopRow(viewportRef.current) + 1;
    setScrollGeometry({
      topRow,
      trackHeight: Math.max(2, viewportHeight - 2),
      contentHeight: total,
      viewportHeight,
      termCols: terminalCols,
    });
  });

  // --- slicing ---
  // Find the smallest startIdx such that messages[startIdx..end] covers the
  // viewport at the current scrollOffset. We walk from the end backwards,
  // accumulating heights, until we have `viewportHeight + safeOffset` lines.
  const safeOffset = Math.min(scrollOffset, Math.max(0, lastTotalRef.current - viewportHeight));
  const total = allMessages.length;
  let startIdx = 0;
  if (safeOffset === 0) {
    // stick-to-bottom path: include as many trailing messages as the viewport
    // can hold so a new stream frame doesn't blow out the slice.
    let acc = 0;
    for (let i = total - 1; i >= 0; i--) {
      const h = messageHeightsRef.current.get(allMessages[i].id)
        ?? defaultMessageHeight(allMessages[i], Math.max(10, terminalCols - 4));
      acc += h;
      if (acc > viewportHeight) { startIdx = i + 1; break; }
      startIdx = i;
    }
  } else {
    // Scrolled up: from the last message, walk back until accumulated height
    // exceeds safeOffset + viewportHeight. The first message we encounter
    // that pushes us over is the start of the slice.
    let acc = 0;
    const needed = safeOffset + viewportHeight;
    for (let i = total - 1; i >= 0; i--) {
      const h = messageHeightsRef.current.get(allMessages[i].id)
        ?? defaultMessageHeight(allMessages[i], Math.max(10, terminalCols - 4));
      acc += h;
      if (acc > needed) { startIdx = i; break; }
      startIdx = i;
    }
  }
  startIdx = Math.max(0, startIdx);
  const visibleMessages = allMessages.slice(startIdx);

  // Clamp scrollOffset if the document shrank under it (e.g. /clear, /compact).
  useLayoutEffect(() => {
    const maxOff = Math.max(0, lastTotalRef.current - viewportHeight);
    if (scrollOffset > maxOff) {
      updateAppState(prev => ({ ...prev, scrollOffset: maxOff }));
    }
  }, [scrollOffset, viewportHeight]);

  // Garbage-collect height entries for messages that no longer exist.
  useLayoutEffect(() => {
    const live = new Set(allMessages.map(m => m.id));
    if (currentMessage) live.add(currentMessage.id);
    for (const id of messageHeightsRef.current.keys()) {
      if (!live.has(id)) {
        messageHeightsRef.current.delete(id);
        messageRefs.current.delete(id);
      }
    }
  });

  const toggleExpandTool = (id: string) => {
    setExpandedTools(prev => { const next = new Set(prev); if (next.has(id)) next.delete(id); else next.add(id); return next; });
  };

  const toggleExpandMessage = (id: string) => {
    setExpandedMessages(prev => { const next = new Set(prev); if (next.has(id)) next.delete(id); else next.add(id); return next; });
  };

  const contentHeight = lastTotalRef.current;
  const needsScrollbar = contentHeight > viewportHeight || scrollOffset > 0;

  // Callback ref factory: registers the DOM node in messageRefs by message id.
  const registerMessageRef = (id: string) => (node: DOMElement | null) => {
    if (node) messageRefs.current.set(id, node);
    else messageRefs.current.delete(id);
  };

  // Force a measurement re-run whenever the slice membership changes
  // (so newly mounted nodes are measured on the next paint).
  useLayoutEffect(() => {
    setMeasureTick(t => t + 1);
  }, [startIdx, visibleMessages.length, currentMessage && currentMessage.id, expandedTools.size, expandedMessages.size, toolCallsExpanded]);

  return (
    <Box flexDirection="row" width="100%" overflow="hidden" ref={viewportRef}>
      <Box flexGrow={1} flexDirection="column" overflow="hidden">
        <Box>
          <Text dimColor={!needsScrollbar} bold={needsScrollbar}>
            {needsScrollbar
              ? `[${startIdx + 1}-${total} / ${total}]`
              : `[${total}]`}
          </Text>
        </Box>
        {visibleMessages.map(msg => (
          <MessageItem
            key={msg.id}
            ref={registerMessageRef(msg.id)}
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
            ref={registerMessageRef(currentMessage.id)}
            msg={currentMessage}
            terminalCols={terminalCols}
            expandedTools={expandedTools}
            toolCallsExpanded={toolCallsExpanded}
            onToggleTool={toggleExpandTool}
          />
        )}
      </Box>
      {needsScrollbar && (
        <Scrollbar
          contentHeight={contentHeight}
          offset={safeOffset}
          viewportHeight={viewportHeight}
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

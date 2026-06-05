import { Box, Text } from "ink";
import { useState, useMemo, memo, useEffect } from "react";
import { type Message, type ToolCall, type Step } from "../protocol.js";
import { updateAppState } from "../hooks/useAppState.js";

const SEP = "-".repeat(60);
const MAX_THINKING = 200;

interface Props {
  messages: Message[];
  currentMessage: Message | null;
  terminalCols: number;
  terminalRows: number;
  scrollOffset: number;
  toolCallsExpanded: boolean;
}

function formatDuration(sec: number): string {
  if (sec <= 0) return "";
  if (sec >= 60) {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return m + "m" + s + "s";
  }
  return sec + "s";
}

function shouldStartCollapsed(toolCalls: ToolCall[], index: number): boolean {
  const tc = toolCalls[index];
  if (!tc) return true;
  if (tc.status === "running") return false;
  let lastFinishedIdx = -1;
  for (let i = toolCalls.length - 1; i >= 0; i--) {
    if (toolCalls[i].status === "complete" || toolCalls[i].status === "error") {
      lastFinishedIdx = i;
      break;
    }
  }
  return index !== lastFinishedIdx;
}

const StepDisplay = memo(function StepDisplay({ step }: { step: Step }) {
  const icon = step.status === "success" ? "[ok]" : step.status === "error" ? "[!!]"
    : step.status === "running" ? "[..]" : "[--]";
  const color = step.status === "success" ? "green" : step.status === "error" ? "red"
    : step.status === "running" ? "cyan" : "cyan";
  const durStr = step.duration
    ? formatDuration(step.duration)
    : step.status === "running" && step.timestamp
      ? formatDuration(Math.floor((Date.now() - step.timestamp) / 1000))
      : "";
  return (
    <Box flexDirection="column">
      <Box>
        <Text color={color}>  {icon} </Text>
        <Text bold color={color}>{step.title}</Text>
        {durStr && (
          <><Text dimColor>  </Text><Text color="grey" dimColor>{durStr}</Text></>
        )}
      </Box>
      {step.thought && <Text color="blue" dimColor>    {truncate(step.thought, 200)}</Text>}
      {step.action && <Text color="yellow">    {truncate(step.action, 200)}</Text>}
      {step.result && <Text color="green">    {truncate(step.result, 300)}</Text>}
    </Box>
  );
});

const ToolCallDisplay = memo(function ToolCallDisplay({
  tc, collapsed, onToggle,
}: {
  tc: ToolCall;
  collapsed: boolean;
  onToggle: () => void;
}) {
  const statusIcon = tc.status === "complete" ? "[ok]" : tc.status === "running" ? "[..]" : "[!!]";
  const statusColor = tc.status === "complete" ? "green" : tc.status === "running" ? "yellow" : "red";
  const durStr = tc.duration
    ? formatDuration(tc.duration)
    : tc.status === "running" && tc.timestamp
      ? formatDuration(Math.floor((Date.now() - tc.timestamp) / 1000))
      : "";
  return (
    <Box flexDirection="column" paddingLeft={1}>
      <Box>
        <Text color={statusColor}>  {statusIcon} </Text>
        <Text bold color="magenta">{tc.name}</Text>
        {durStr && (<><Text dimColor>  </Text><Text color="grey" dimColor>{durStr}</Text></>)}
        <Text dimColor>  </Text>
        <Text color="blue" dimColor>[{collapsed ? "+" : "-"}]</Text>
      </Box>
      {!collapsed && tc.args && (
        <Box paddingLeft={4}><Text dimColor>{truncate(formatJson(tc.args), 200)}</Text></Box>
      )}
      {tc.result && (
        <Box paddingLeft={4} flexDirection="column">
          <Text color={tc.status === "error" ? "red" : "green"} dimColor>{truncate(tc.result, 500)}</Text>
        </Box>
      )}
    </Box>
  );
});

const MessageItem = memo(function MessageItem({
  msg, expandedMessages, expandedTools, toolCallsExpanded,
  onToggleTool, onToggleMessage,
}: {
  msg: Message;
  expandedMessages: Set<string>;
  expandedTools: Set<string>;
  toolCallsExpanded: boolean;
  onToggleTool: (id: string) => void;
  onToggleMessage: (id: string) => void;
}) {
  return (
    <Box flexDirection="column" marginBottom={1}>
      {msg.type === "user" && (
        <Box flexDirection="column">
          <Text dimColor>{SEP}</Text>
          <Text color="green" bold>{">"} {msg.content}</Text>
        </Box>
      )}
      {msg.type === "assistant" && (
        <Box flexDirection="column">
          <Text>{" "}</Text>
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
                  <Text color="cyan" dimColor> {"-> to expand"}</Text>
                </Text>
              )}
            </Box>
          )}
          {msg.toolCalls.map((tc, i) => {
            const key = tc.id || tc.name || "tool-" + msg.id + "-" + i;
            const startCollapsed = shouldStartCollapsed(msg.toolCalls, i);
            const isExpanded = expandedTools.has(key);
            return (
              <ToolCallDisplay
                key={key}
                tc={tc}
                collapsed={toolCallsExpanded ? false : (isExpanded ? false : startCollapsed)}
                onToggle={() => onToggleTool(key)}
              />
            );
          })}
          {msg.content && <Text>{msg.content}</Text>}
          <Text dimColor>{SEP}</Text>
        </Box>
      )}
      {msg.type === "system" && (
        <Box><Text color="red">Error: {msg.content}</Text></Box>
      )}
    </Box>
  );
}, (prev, next) => {
  return prev.msg.id === next.msg.id
    && prev.msg.content === next.msg.content
    && prev.msg.thinking === next.msg.thinking
    && prev.msg.type === next.msg.type
    && prev.expandedMessages.has(prev.msg.id) === next.expandedMessages.has(next.msg.id)
    && prev.toolCallsExpanded === next.toolCallsExpanded;
});

const StreamingMessage = memo(function StreamingMessage({
  msg, expandedTools, toolCallsExpanded, onToggleTool,
}: {
  msg: Message;
  expandedTools: Set<string>;
  toolCallsExpanded: boolean;
  onToggleTool: (id: string) => void;
}) {
  return (
    <Box flexDirection="column">
      {msg.thinking && (
        <Text dimColor italic>{truncate(msg.thinking, MAX_THINKING)}</Text>
      )}
      {msg.toolCalls.map((tc, i) => {
        const key = tc.id || tc.name || "tool-" + msg.id + "-" + i;
        const startCollapsed = shouldStartCollapsed(msg.toolCalls, i);
        const isExpanded = expandedTools.has(key);
        return (
          <ToolCallDisplay
            key={key}
            tc={tc}
            collapsed={toolCallsExpanded ? false : (isExpanded ? false : startCollapsed)}
            onToggle={() => onToggleTool(key)}
          />
        );
      })}
      {msg.content && <Text>{msg.content}</Text>}
    </Box>
  );
});

export const ChatArea = memo(function ChatArea({
  messages, currentMessage, terminalCols, terminalRows, scrollOffset, toolCallsExpanded,
}: Props) {
  const [expandedTools, setExpandedTools] = useState<Set<string>>(new Set());
  const [expandedMessages, setExpandedMessages] = useState<Set<string>>(new Set());

  const allMessages = useMemo(() =>
    currentMessage
      ? messages.filter(m => m.id !== currentMessage.id)
      : messages,
    [messages, currentMessage && currentMessage.id]);

  // scrollOffset: 0 = bottom (newest), higher = scrolled back to older messages
  const maxVisible = Math.max(5, terminalRows - 10);
  const total = allMessages.length;
  const maxScroll = Math.max(0, total - maxVisible);
  const safeOffset = Math.min(scrollOffset, maxScroll);

  // Clamp out-of-bounds scrollOffset (e.g. after /clear)
  useEffect(() => {
    if (scrollOffset > maxScroll) {
      updateAppState(prev => ({ ...prev, scrollOffset: maxScroll }));
    }
  }, [scrollOffset, maxScroll]);

  // Show slice from (total - maxVisible - safeOffset) to (total - safeOffset)
  // safeOffset=0 → newest maxVisible; safeOffset=maxScroll → oldest
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
            {'  '}
          </Text>
          <Text color="yellow">
            {"█".repeat(Math.min(10, Math.round((1 - safeOffset / maxScroll) * 10)))}
          </Text>
          <Text dimColor>
            {"░".repeat(10 - Math.min(10, Math.round((1 - safeOffset / maxScroll) * 10)))}
          </Text>
          <Text dimColor>  PgUp/PgDn ↑↓  Home/End</Text>
        </Box>
      )}
      {visibleMessages.map(msg => (
        <MessageItem
          key={msg.id}
          msg={msg}
          expandedMessages={expandedMessages}
          expandedTools={expandedTools}
          toolCallsExpanded={toolCallsExpanded}
          onToggleTool={toggleExpandTool}
          onToggleMessage={toggleExpandMessage}
        />
      ))}
      {currentMessage && (
        <StreamingMessage
          key={currentMessage.id}
          msg={currentMessage}
          expandedTools={expandedTools}
          toolCallsExpanded={toolCallsExpanded}
          onToggleTool={toggleExpandTool}
        />
      )}
    </Box>
  );
});

function formatJson(s: unknown): string {
  if (typeof s !== "string") return JSON.stringify(s, null, 2);
  try { return JSON.stringify(JSON.parse(s as string), null, 2); }
  catch { return s as string; }
}

function truncate(s: unknown, max: number): string {
  const str = typeof s === "string" ? s : String(s ?? "");
  if (str.length <= max) return str;
  return str.slice(0, max) + "...";
}

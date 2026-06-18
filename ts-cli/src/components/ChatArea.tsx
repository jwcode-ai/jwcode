import { Box, Text } from "ink";
import { useState, useMemo, useCallback, memo } from "react";
import { useStdout } from "ink";
import { type Message, type ToolCall, type Step } from "../protocol.js";
import { useAppChatArea, useAppToolCallsExpanded } from "../hooks/useAppState.js";
import { t } from "../theme.js";
import { useSpinner } from "../hooks/useSpinner.js";
import { useShimmerSpans, intensityWeight } from "../hooks/useShimmer.js";
import { highlightPaths } from "./highlightPaths.js";

const SEP = "-".repeat(60);
const MAX_THINKING = 200;

interface Props {
  messages: Message[];
  currentMessage: Message | null;
  terminalCols: number;
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

/** Renders text with path-like tokens colored via t.filePath. */
function PathText({ text, color, dimColor }: { text: string; color?: string; dimColor?: boolean }) {
  const segs = useMemo(() => highlightPaths(text), [text]);
  return (
    <Text color={color} dimColor={dimColor}>
      {segs.map((s, i) =>
        s.isPath
          ? <Text key={i} color={t.filePath} dimColor={dimColor}>{s.text}</Text>
          : <Text key={i}>{s.text}</Text>,
      )}
    </Text>
  );
}

/** Shimmering text: a cosine intensity band sweeps across while active. */
function ShimmerText({ text, color, active }: { text: string; color: string; active: boolean }) {
  const spans = useShimmerSpans(text, active);
  if (!active) {
    return <Text bold color={color}>{text}</Text>;
  }
  return (
    <Text>
      {spans.map((s, i) => {
        const w = intensityWeight(s.intensity);
        return (
          <Text key={i} color={color} bold={w === "bold"} dimColor={w === "dim"}>
            {s.char}
          </Text>
        );
      })}
    </Text>
  );
}

const StepDisplay = memo(function StepDisplay({ step }: { step: Step }) {
  const running = step.status === "running";
  const spinner = useSpinner(running);
  const icon = step.status === "success" ? "[ok]" : step.status === "error" ? "[!!]"
    : running ? "[" + spinner + "]" : "[--]";
  const color = step.status === "success" ? t.success : step.status === "error" ? t.error
    : running ? t.info : t.info;
  const durStr = step.duration
    ? formatDuration(step.duration)
    : running && step.timestamp
      ? formatDuration(Math.floor((Date.now() - step.timestamp) / 1000))
      : "";
  return (
    <Box flexDirection="column">
      <Box>
        <Text color={color}>  {icon} </Text>
        <ShimmerText text={step.title} color={t.stepTitle} active={running} />
        {durStr && (
          <><Text dimColor>  </Text><Text color={t.muted} dimColor>{durStr}</Text></>
        )}
      </Box>
      {step.thought && <Box paddingLeft={4}><Text color={t.stepThought} dimColor>{truncate(step.thought, 200)}</Text></Box>}
      {step.action && <Box paddingLeft={4}><Text color={t.stepAction}>{truncate(step.action, 200)}</Text></Box>}
      {step.result && <Box paddingLeft={4}><PathText text={truncate(step.result, 300)} color={t.toolResult} dimColor /></Box>}
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
  const running = tc.status === "running";
  const spinner = useSpinner(running);
  const statusIcon = tc.status === "complete" ? "[ok]" : running ? "[" + spinner + "]" : "[!!]";
  const statusColor = tc.status === "complete" ? t.success : running ? t.warning : t.error;
  const durStr = tc.duration
    ? formatDuration(tc.duration)
    : running && tc.timestamp
      ? formatDuration(Math.floor((Date.now() - tc.timestamp) / 1000))
      : "";
  return (
    <Box flexDirection="column" paddingLeft={1}>
      <Box>
        <Text color={statusColor}>  {statusIcon} </Text>
        <ShimmerText text={tc.name} color={t.toolName} active={running} />
        {durStr && (<><Text dimColor>  </Text><Text color={t.muted} dimColor>{durStr}</Text></>)}
        <Text dimColor>  </Text>
        <Text color={t.filePath} dimColor>[{collapsed ? "+" : "-"}]</Text>
      </Box>
      {!collapsed && tc.args && (
        <Box paddingLeft={4}><PathText text={truncate(formatJson(tc.args), 200)} color={t.toolArgs} dimColor /></Box>
      )}
      {tc.result && (
        <Box paddingLeft={4} flexDirection="column">
          <PathText
            text={truncate(tc.result, 500)}
            color={tc.status === "error" ? t.error : t.toolResult}
            dimColor
          />
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
          <Text color={t.user} bold>{">"} {msg.content}</Text>
        </Box>
      )}
      {msg.type === "assistant" && (
        <Box flexDirection="column">
          <Text>{" "}</Text>
          {(msg.steps || []).map((step, i) => (
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
                <Text color={t.info} dimColor>
                  [{msg.thinking.length - MAX_THINKING} more chars]
                  <Text color={t.brand} dimColor> {"-> to expand"}</Text>
                </Text>
              )}
            </Box>
          )}
          {(msg.toolCalls || []).map((tc, i) => {
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
        <Box><Text color={t.system}>Error: {msg.content}</Text></Box>
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
      {(msg.toolCalls || []).map((tc, i) => {
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
}, (prev, next) => {
  // Force re-render while any tool is running so spinner/shimmer animate and
  // running -> complete/error transitions are reflected. Otherwise fall back
  // to a shallow check that skips re-renders for unchanged streaming content.
  const prevRunning = prev.msg.toolCalls.some(tc => tc.status === "running");
  const nextRunning = next.msg.toolCalls.some(tc => tc.status === "running");
  if (prevRunning || nextRunning) return false;
  return prev.msg.id === next.msg.id
    && prev.msg.content === next.msg.content
    && prev.msg.thinking === next.msg.thinking
    && prev.msg.toolCalls.length === next.msg.toolCalls.length
    && prev.toolCallsExpanded === next.toolCallsExpanded;
});

export const ChatArea = memo(function ChatArea({
  messages, currentMessage, terminalCols, toolCallsExpanded,
}: Props) {
  const [expandedTools, setExpandedTools] = useState<Set<string>>(new Set());
  const [expandedMessages, setExpandedMessages] = useState<Set<string>>(new Set());

  const allMessages = useMemo(() =>
    currentMessage
      ? messages.filter(m => m.id !== currentMessage.id)
      : messages,
    [messages, currentMessage && currentMessage.id]);

  const toggleExpandTool = useCallback((id: string) => {
    setExpandedTools(prev => { const next = new Set(prev); if (next.has(id)) next.delete(id); else next.add(id); return next; });
  }, []);

  const toggleExpandMessage = useCallback((id: string) => {
    setExpandedMessages(prev => { const next = new Set(prev); if (next.has(id)) next.delete(id); else next.add(id); return next; });
  }, []);

  return (
    <Box flexDirection="column" width="100%">
      {allMessages.map(msg => (
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

/**
 * Standalone container that subscribes to store slices for ChatArea.
 * Keeps App.tsx from re-rendering on every stream flush.
 */
export const ChatAreaContainer = memo(function ChatAreaContainer() {
  const { messages, currentMessage } = useAppChatArea();
  const toolCallsExpanded = useAppToolCallsExpanded();
  const { stdout } = useStdout();
  const terminalCols = (stdout as any)?.columns || 80;

  return (
    <ChatArea
      messages={messages}
      currentMessage={currentMessage}
      terminalCols={terminalCols}
      toolCallsExpanded={toolCallsExpanded}
    />
  );
});

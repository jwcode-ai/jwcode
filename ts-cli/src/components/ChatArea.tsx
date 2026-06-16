import { Box, Text, Static } from "ink";
import { useState, useMemo, memo, useRef, useEffect } from "react";
import { useStdout } from "ink";
import { type Message, type ToolCall, type Step } from "../protocol.js";
import { useAppChatArea, useAppToolCallsExpanded } from "../hooks/useAppState.js";

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

interface MsgBlockProps {
  msg: Message;
  terminalCols: number;
  toolCallsExpanded: boolean;
  isCurrent: boolean;
}

const MsgBlock = memo(function MsgBlock({
  msg,
  terminalCols,
  toolCallsExpanded,
  isCurrent,
}: MsgBlockProps) {
  const roleColor = msg.role === "user" ? "green" : "blue";
  const roleLabel = msg.role === "user" ? "You" : "JWCode";

  // --- content block ---
  const contentBlocks = useMemo(() => {
    const blocks: React.ReactNode[] = [];
    let textContent = msg.content;

    // Strip thinking prefix from currentMessage if it has a separate thinking field
    if (isCurrent && msg.thinking) {
      // currentMessage already has thinking rendered separately, so skip it here
    } else if (msg.thinking) {
      // Already processed messages: thinking is in the thinking field
    }

    if (textContent) {
      blocks.push(
        <Text key="content">{textContent}</Text>
      );
    }

    // --- tool_calls block ---
    if (msg.tool_calls && msg.tool_calls.length > 0) {
      const collapsed = !toolCallsExpanded && !isCurrent;
      if (!collapsed) {
        for (const tc of msg.tool_calls) {
          blocks.push(
            <Box key={`tc-${tc.id || tc.name}`} flexDirection="column" marginLeft={2}>
              <Text color="yellow">  ▶ {tc.name}</Text>
              {tc.args && (
                <Text dimColor>
                  {truncate(JSON.stringify(tc.args, null, 1), terminalCols - 6)}
                </Text>
              )}
            </Box>
          );
        }
      }
    }

    // --- steps block ---
    if (msg.steps && msg.steps.length > 0) {
      for (const step of msg.steps) {
        const icon = step.status === "completed" ? "✓" : step.status === "running" ? "●" : "○";
        const color = step.status === "completed" ? "green" : step.status === "running" ? "yellow" : "gray";
        blocks.push(
          <Box key={`step-${step.id || step.description}`} marginLeft={2}>
            <Text color={color}>{icon} {step.description}</Text>
          </Box>
        );
      }
    }

    return blocks;
  }, [msg, terminalCols, toolCallsExpanded, isCurrent]);

  const thinkingBlocks = useMemo(() => {
    const blocks: React.ReactNode[] = [];
    if (msg.thinking) {
      const text = typeof msg.thinking === "string" ? msg.thinking : JSON.stringify(msg.thinking);
      const lines = text.split("\n");
      const truncated = lines.length > MAX_THINKING
        ? lines.slice(0, MAX_THINKING).join("\n") + "\n...(truncated)"
        : text;
      blocks.push(
        <Text key="thinking" color="gray" dimColor>{truncated}</Text>
      );
    }
    return blocks;
  }, [msg.thinking]);

  const toolResults = useMemo(() => {
    const blocks: React.ReactNode[] = [];
    if (msg.tool_results && msg.tool_results.length > 0) {
      for (const tr of msg.tool_results) {
        blocks.push(
          <Box key={`tr-${tr.tool_name || "result"}`} marginLeft={2} flexDirection="column">
            <Text color="cyan">  ◈ {tr.tool_name}</Text>
            {tr.output && (
              <Text dimColor>
                {truncate(tr.output, terminalCols - 6)}
              </Text>
            )}
          </Box>
        );
      }
    }
    return blocks;
  }, [msg.tool_results, terminalCols]);

  return (
    <Box key={msg.id || msg.role + (msg.timestamp || 0)} flexDirection="column" marginBottom={1}>
      <Box>
        <Text color={roleColor} bold>
          {roleLabel}
        </Text>
        <Text dimColor>{"  " + formatDuration(msg.duration || 0)}</Text>
        {msg.tokenUsage && (
          <Text dimColor>  {msg.tokenUsage.in + msg.tokenUsage.out}t</Text>
        )}
      </Box>

      {/* Thinking block (collapsed by default for non-current messages) */}
      {msg.thinking && !isCurrent && thinkingBlocks.length > 0 && (
        <Box marginLeft={2} flexDirection="column">
          {thinkingBlocks}
        </Box>
      )}

      {/* Content */}
      {contentBlocks.length > 0 && (
        <Box marginLeft={2} flexDirection="column">
          {contentBlocks}
        </Box>
      )}

      {/* Tool results */}
      {toolResults.length > 0 && (
        <Box marginLeft={2} flexDirection="column">
          {toolResults}
          <Text dimColor>{SEP}</Text>
        </Box>
      )}
    </Box>
  );
});

// ---- Utility ----

function truncate(s: string, max: number): string {
  if (max <= 0 || s.length <= max) return s;
  return s.slice(0, Math.max(0, max - 3)) + "...";
}

function formatDurationMs(ms: number): string {
  if (ms <= 0) return "";
  if (ms >= 60000) {
    const m = Math.floor(ms / 60000);
    const s = Math.round((ms % 60000) / 1000);
    return m + "m" + s + "s";
  }
  return (ms / 1000).toFixed(1) + "s";
}

// ---- ChatArea component (inner) ----

const ChatArea = memo(function ChatArea({
  messages,
  currentMessage,
  terminalCols,
  toolCallsExpanded,
}: Props) {
  // Use a stable set of message IDs to minimize re-renders during streaming
  const hasHistory = messages.length > 0;
  const stableMessages = useMemo(() => messages, [
    // Only re-compute when messages array length or last message ID changes
    messages.length,
    messages.length > 0 ? messages[messages.length - 1].id : null,
    messages.length > 0 ? messages[messages.length - 1].id : null,
  ]);

  return (
    <Box flexDirection="column" flexGrow={1}>
      {/* Static history — already completed messages, never re-rendered */}
      {stableMessages.length > 0 && (
        <Static items={stableMessages}>
          {(msg) => (
            <MsgBlock
              key={msg.id || msg.role + (msg.timestamp || Math.random())}
              msg={msg}
              terminalCols={terminalCols}
              toolCallsExpanded={toolCallsExpanded}
              isCurrent={false}
            />
          )}
        </Static>
      )}

      {/* Empty state placeholder: prevents flicker after clear */}
      {!hasHistory && !currentMessage && (
        <Box flexDirection="column" marginY={1}>
          <Text dimColor>── 会话已清空 ──</Text>
        </Box>
      )}

      {/* Live current message — only this part updates during streaming */}
      {currentMessage && (
        <Box flexDirection="column">
          <MsgBlock
            msg={currentMessage}
            terminalCols={terminalCols}
            toolCallsExpanded={toolCallsExpanded}
            isCurrent={true}
          />
        </Box>
      )}
    </Box>
  );
});

// ---- Container with state subscription ----

export function ChatAreaContainer() {
  const { messages, currentMessage } = useAppChatArea();
  const toolCallsExpanded = useAppToolCallsExpanded();
  const { stdout } = useStdout();
  const terminalCols = (stdout as unknown as { columns: number })?.columns ?? 80;

  // --- Streaming throttle: only update currentMessage at most every 50ms ---
  const [displayedCurrent, setDisplayedCurrent] = useState<Message | null>(null);
  const throttleRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastUpdateRef = useRef<number>(0);
  const pendingRef = useRef<Message | null>(null);
  const THROTTLE_MS = 50;

  useEffect(() => {
    if (!currentMessage) {
      // No current message — clear immediately
      if (throttleRef.current) clearTimeout(throttleRef.current);
      throttleRef.current = null;
      pendingRef.current = null;
      setDisplayedCurrent(null);
      return;
    }

    const now = Date.now();
    const elapsed = now - lastUpdateRef.current;

    if (elapsed >= THROTTLE_MS) {
      // Enough time since last update — apply immediately
      lastUpdateRef.current = now;
      setDisplayedCurrent(currentMessage);
      pendingRef.current = null;
    } else {
      // Defer update
      pendingRef.current = currentMessage;
      if (!throttleRef.current) {
        throttleRef.current = setTimeout(() => {
          throttleRef.current = null;
          if (pendingRef.current) {
            lastUpdateRef.current = Date.now();
            setDisplayedCurrent(pendingRef.current);
            pendingRef.current = null;
          }
        }, THROTTLE_MS - elapsed);
      }
    }
  }, [currentMessage]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (throttleRef.current) clearTimeout(throttleRef.current);
    };
  }, []);

  return (
    <ChatArea
      messages={messages}
      currentMessage={displayedCurrent}
      terminalCols={terminalCols}
      toolCallsExpanded={toolCallsExpanded}
    />
  );
}

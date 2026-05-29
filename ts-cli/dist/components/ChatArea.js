import { jsxs as _jsxs, jsx as _jsx } from "react/jsx-runtime";
/**
 * ChatArea — renders message list with markdown, tool calls, steps, thinking.
 * Shows newest messages at the bottom; uses scrollOffset to page through history.
 */
import { Box, Text } from 'ink';
const SEP = '─'.repeat(60);
export function ChatArea({ messages, currentMessage, scrollOffset, terminalRows, reservedRows }) {
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
    return (_jsxs(Box, { flexDirection: "column", width: "100%", children: [isScrolledUp && (_jsx(Box, { children: _jsxs(Text, { color: "yellow", dimColor: true, children: ["\u25B2 [", start + 1, "-", end, "/", total, "] \u4E0A\u7FFB\u4E2D (PgUp/PgDn \u7FFB\u9875, Home \u5F00\u5934, End \u6700\u65B0)"] }) })), !isScrolledUp && total > maxVisible && (_jsx(Box, { children: _jsxs(Text, { color: "grey", dimColor: true, children: ["[", start + 1, "-", end, "/", total, "] \u2191/PgUp \u67E5\u770B\u66F4\u65E9\u6D88\u606F"] }) })), visibleMessages.map(msg => (_jsxs(Box, { flexDirection: "column", marginBottom: 1, children: [msg.type === 'user' && (_jsxs(Box, { flexDirection: "column", children: [_jsx(Text, { dimColor: true, children: SEP }), _jsxs(Text, { color: "green", bold: true, children: ["> ", msg.content] })] })), msg.type === 'assistant' && (_jsxs(Box, { flexDirection: "column", children: [_jsx(Text, { children: ' ' }), msg.steps.map((step, i) => (_jsx(StepDisplay, { step: step }, step.id || i))), msg.thinking && (_jsx(Text, { dimColor: true, italic: true, children: truncate(msg.thinking, 200) })), msg.toolCalls.map((tc, i) => (_jsx(ToolCallDisplay, { tc: tc }, tc.id || i))), msg.content && _jsx(Text, { children: msg.content }), _jsx(Text, { dimColor: true, children: SEP })] })), msg.type === 'system' && (_jsx(Box, { children: _jsxs(Text, { color: "red", children: ["Error: ", msg.content] }) }))] }, msg.id))), currentMessage && (_jsxs(Box, { flexDirection: "column", children: [currentMessage.thinking && (_jsx(Text, { dimColor: true, italic: true, children: truncate(currentMessage.thinking, 200) })), currentMessage.toolCalls.map((tc, i) => (_jsx(ToolCallDisplay, { tc: tc }, tc.id || i))), currentMessage.content && _jsx(Text, { children: currentMessage.content })] }, currentMessage.id))] }));
}
function StepDisplay({ step }) {
    const icon = step.status === 'success' ? '✓' : step.status === 'error' ? '✗' : '▶';
    const color = step.status === 'success' ? 'green' : step.status === 'error' ? 'red' : 'cyan';
    return (_jsxs(Box, { flexDirection: "column", children: [_jsxs(Text, { color: color, children: ["  ", icon, " ", step.title] }), step.thought && _jsxs(Text, { color: "blue", dimColor: true, children: ["    ", truncate(step.thought, 200)] }), step.action && _jsxs(Text, { color: "yellow", children: ["    ", truncate(step.action, 200)] }), step.result && _jsxs(Text, { color: "green", children: ["    ", truncate(step.result, 300)] })] }));
}
function ToolCallDisplay({ tc }) {
    const argsStr = tc.args ? truncate(formatJson(tc.args), 200) : '';
    const statusIcon = tc.status === 'complete' ? '✓' : tc.status === 'running' ? '◷' : '✗';
    const statusColor = tc.status === 'complete' ? 'green' : tc.status === 'running' ? 'yellow' : 'red';
    return (_jsxs(Box, { flexDirection: "column", paddingLeft: 1, children: [_jsxs(Box, { children: [_jsxs(Text, { color: statusColor, children: ["  ", statusIcon, " "] }), _jsx(Text, { bold: true, color: "magenta", children: tc.name }), argsStr && _jsxs(Text, { dimColor: true, children: ["  ", argsStr] })] }), tc.result && (_jsx(Box, { paddingLeft: 4, children: _jsx(Text, { color: "green", dimColor: true, children: truncate(tc.result, 200) }) }))] }));
}
function formatJson(s) {
    try {
        return JSON.stringify(JSON.parse(s), null, 2);
    }
    catch {
        return s;
    }
}
function truncate(s, max) {
    if (s.length <= max)
        return s;
    return s.slice(0, max) + '...';
}

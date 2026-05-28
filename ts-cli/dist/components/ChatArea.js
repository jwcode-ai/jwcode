import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
/**
 * ChatArea — renders message list with markdown, tool calls, steps, thinking.
 * Mirrors python-cli/jwcode/widgets/chat.py.
 */
import { Box, Text } from 'ink';
const SEP = '─'.repeat(60);
export function ChatArea({ messages, currentMessage }) {
    const allMessages = currentMessage
        ? [...messages.filter(m => m.id !== currentMessage.id)]
        : messages;
    return (_jsxs(Box, { flexDirection: "column", width: "100%", children: [allMessages.map(msg => (_jsxs(Box, { flexDirection: "column", marginBottom: 1, children: [msg.type === 'user' && (_jsxs(Box, { flexDirection: "column", children: [_jsx(Text, { dimColor: true, children: SEP }), _jsxs(Text, { color: "green", bold: true, children: ["> ", msg.content] })] })), msg.type === 'assistant' && (_jsxs(Box, { flexDirection: "column", children: [_jsx(Text, { children: ' ' }), msg.steps.map((step, i) => (_jsx(StepDisplay, { step: step }, step.id || i))), msg.thinking && (_jsx(Text, { dimColor: true, italic: true, children: truncate(msg.thinking, 200) })), msg.toolCalls.map((tc, i) => (_jsx(ToolCallDisplay, { tc: tc }, tc.id || i))), msg.content && _jsx(Text, { children: msg.content }), _jsx(Text, { dimColor: true, children: SEP })] })), msg.type === 'system' && (_jsx(Box, { children: _jsxs(Text, { color: "red", children: ["Error: ", msg.content] }) }))] }, msg.id))), currentMessage && (_jsxs(Box, { flexDirection: "column", children: [currentMessage.thinking && (_jsx(Text, { dimColor: true, italic: true, children: truncate(currentMessage.thinking, 200) })), currentMessage.toolCalls.map((tc, i) => (_jsx(ToolCallDisplay, { tc: tc }, tc.id || i))), currentMessage.content && _jsx(Text, { children: currentMessage.content })] }, currentMessage.id))] }));
}
function StepDisplay({ step }) {
    const icon = step.status === 'success' ? '✓' : step.status === 'error' ? '✗' : '▶';
    const color = step.status === 'success' ? 'green' : step.status === 'error' ? 'red' : 'cyan';
    return (_jsxs(Box, { flexDirection: "column", children: [_jsxs(Text, { color: color, children: ["  ", icon, " ", step.title] }), step.thought && _jsxs(Text, { color: "blue", dimColor: true, children: ["    ", truncate(step.thought, 200)] }), step.action && _jsxs(Text, { color: "yellow", children: ["    ", truncate(step.action, 200)] }), step.result && _jsxs(Text, { color: "green", children: ["    ", truncate(step.result, 300)] })] }));
}
function ToolCallDisplay({ tc }) {
    const argsStr = tc.args ? truncate(formatJson(tc.args), 500) : '';
    return (_jsxs(Box, { flexDirection: "column", borderStyle: "round", borderColor: "magenta", paddingLeft: 1, children: [_jsxs(Text, { bold: true, color: "magenta", children: ["  Tool: ", tc.name] }), argsStr && _jsx(Text, { dimColor: true, children: argsStr }), tc.result && (_jsx(Box, { borderStyle: "single", borderColor: "green", paddingLeft: 1, children: _jsxs(Text, { color: "green", children: ["  Result: ", truncate(tc.result, 300)] }) }))] }));
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

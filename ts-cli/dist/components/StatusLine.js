import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { Box, Text } from 'ink';
import { useAppState } from '../hooks/useAppState.js';
function formatTokens(n) {
    if (n >= 1_000_000)
        return `${(n / 1_000_000).toFixed(1)}M`;
    if (n >= 1_000)
        return `${Math.round(n / 1_000)}K`;
    return String(n);
}
export function StatusLine() {
    const state = useAppState();
    const { usage, modelName, planMode, autoMode, connected, statusText, messages } = state;
    const msgCount = messages.length;
    const pct = Math.min(100, Math.round(usage.usageRatio * 100));
    const filled = Math.round(pct / 10);
    const bar = '='.repeat(filled) + '-'.repeat(10 - filled);
    const model = modelName || (connected ? 'ready' : 'connecting...');
    const modeLabel = planMode ? ' Plan ' : ' Act ';
    const modeColor = planMode ? 'cyan' : 'green';
    const connIcon = connected ? '●' : '○';
    const connColor = connected ? 'green' : 'red';
    const isError = statusText.startsWith('Error:');
    return (_jsxs(Box, { flexDirection: "column", width: "100%", paddingRight: 1, children: [_jsxs(Box, { height: 1, children: [_jsx(Text, { bold: true, color: "cyan", children: "jwcode" }), _jsx(Text, { children: "  " }), _jsxs(Text, { backgroundColor: modeColor, color: "black", children: [" ", modeLabel, " "] }), _jsx(Text, { children: "  " }), autoMode && (_jsxs(_Fragment, { children: [_jsx(Text, { backgroundColor: "magenta", color: "black", children: " AUTO " }), _jsx(Text, { children: "  " })] })), _jsxs(Text, { color: connColor, children: [connIcon, " "] }), _jsx(Text, { color: "green", children: model }), _jsx(Text, { children: "  " }), _jsxs(Text, { dimColor: true, children: [msgCount, "msgs"] }), _jsx(Text, { children: "  t: " }), _jsx(Text, { color: "yellow", children: formatTokens(usage.totalTokens) }), _jsx(Text, { children: "  " }), _jsxs(Text, { color: pct > 90 ? 'red' : 'white', children: [bar, " ", pct, "%"] })] }), statusText && statusText !== 'connecting...' && (_jsx(Box, { height: 1, children: _jsx(Text, { color: isError ? 'red' : 'grey', dimColor: !isError, children: statusText.slice(0, 100) }) }))] }));
}

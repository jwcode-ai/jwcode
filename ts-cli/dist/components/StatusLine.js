import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
/**
 * StatusLine — top bar showing model, token usage, plan indicator.
 */
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
    const { usage, modelName, planMode, connected } = state;
    const pct = Math.min(100, Math.round(usage.usageRatio * 100));
    const filled = Math.round(pct / 10);
    const bar = '='.repeat(filled) + '-'.repeat(10 - filled);
    const model = modelName || (connected ? 'ready' : 'connecting...');
    const plan = planMode ? ' [PLAN]' : '';
    return (_jsxs(Box, { width: "100%", height: 1, paddingRight: 1, children: [_jsx(Text, { bold: true, color: "cyan", children: "jwcode" }), _jsx(Text, { color: "yellow", children: plan }), _jsx(Text, { children: "   " }), _jsx(Text, { color: "green", children: model }), _jsx(Text, { children: "   tokens: " }), _jsx(Text, { color: "yellow", children: formatTokens(usage.totalTokens) }), _jsx(Text, { children: "  " }), _jsxs(Text, { color: pct > 90 ? 'red' : 'white', children: [bar, " ", pct, "%"] })] }));
}

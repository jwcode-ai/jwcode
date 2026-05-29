import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
/**
 * ApprovalModal — permission prompt in Claude Code style.
 * Arrow keys to select, Enter to confirm, Esc to cancel.
 */
import { useState } from 'react';
import { Box, Text, useInput } from 'ink';
export function ApprovalModal({ toolName, payload, onAllow, onDeny }) {
    const [selected, setSelected] = useState(0); // 0=allow, 1=deny
    useInput((_input, key) => {
        if (key.escape || key.tab) {
            onDeny();
            return;
        }
        if (key.upArrow || key.downArrow) {
            setSelected(prev => prev === 0 ? 1 : 0);
            return;
        }
        if (key.return) {
            if (selected === 0)
                onAllow();
            else
                onDeny();
            return;
        }
        if (_input === '1') {
            onAllow();
            return;
        }
        if (_input === '2') {
            onDeny();
            return;
        }
        if (_input === 'y' || _input === 'Y') {
            onAllow();
            return;
        }
        if (_input === 'n' || _input === 'N') {
            onDeny();
            return;
        }
    });
    const desc = payload
        ? (payload.length > 200 ? payload.slice(0, 200) + '...' : payload)
        : '';
    return (_jsxs(Box, { flexDirection: "column", borderStyle: "round", borderColor: "yellow", paddingX: 2, paddingY: 1, marginTop: 1, children: [_jsx(Box, { marginBottom: 1, children: _jsx(Text, { bold: true, children: "Do you want to proceed?" }) }), _jsxs(Box, { flexDirection: "column", marginLeft: 2, marginBottom: 1, children: [_jsx(Box, { children: _jsxs(Text, { color: selected === 0 ? 'green' : undefined, children: [selected === 0 ? ' ❯' : '  ', " 1. Allow"] }) }), _jsx(Box, { children: _jsxs(Text, { color: selected === 1 ? 'red' : undefined, children: [selected === 1 ? ' ❯' : '  ', " 2. Deny"] }) })] }), _jsxs(Box, { marginBottom: 1, children: [_jsx(Text, { dimColor: true, children: "Tool: " }), _jsx(Text, { color: "cyan", children: toolName }), desc ? _jsxs(Text, { dimColor: true, children: ["  ", desc] }) : null] }), _jsxs(Box, { children: [_jsx(Text, { dimColor: true, children: " Esc to cancel \u00B7 " }), _jsx(Text, { dimColor: true, children: "\u2191\u2193 to select \u00B7 " }), _jsx(Text, { dimColor: true, children: "Enter to confirm" })] })] }));
}

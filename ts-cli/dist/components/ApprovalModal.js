import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
/**
 * ApprovalModal — hook approval dialog (allow/deny tool execution).
 * Mirrors python-cli/jwcode/widgets/approval.py.
 */
import { Box, Text, useInput } from 'ink';
export function ApprovalModal({ toolName, payload, onAllow, onDeny }) {
    useInput((_input, key) => {
        if (key.escape) {
            onDeny();
        }
        else if (_input === 'y' || _input === 'Y') {
            onAllow();
        }
        else if (_input === 'n' || _input === 'N') {
            onDeny();
        }
    });
    return (_jsxs(Box, { flexDirection: "column", borderStyle: "double", borderColor: "yellow", paddingX: 2, paddingY: 1, width: 56, alignSelf: "center", marginTop: 1, children: [_jsx(Text, { bold: true, color: "yellow", children: "Hook Approval Required" }), _jsxs(Box, { marginY: 1, children: [_jsx(Text, { children: "Tool: " }), _jsx(Text, { bold: true, color: "cyan", children: toolName })] }), _jsx(Box, { borderStyle: "single", borderColor: "grey", paddingX: 1, marginBottom: 1, children: _jsx(Text, { dimColor: true, children: trunc(payload, 300) }) }), _jsxs(Box, { children: [_jsx(Text, { children: "[" }), _jsx(Text, { color: "green", bold: true, children: "Y" }), _jsx(Text, { children: "] Allow  [" }), _jsx(Text, { color: "red", bold: true, children: "N" }), _jsx(Text, { children: "] Deny  [" }), _jsx(Text, { dimColor: true, children: "Esc" }), _jsx(Text, { children: "] Cancel" })] })] }));
}
function trunc(s, max) {
    return s.length <= max ? s : s.slice(0, max) + '...';
}

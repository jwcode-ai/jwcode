import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
/**
 * CommandPalette — / popup with filterable command list.
 * Mirrors python-cli/jwcode/widgets/command_palette.py.
 */
import { useState, useEffect, useMemo } from 'react';
import { Box, Text, useInput } from 'ink';
const COMMANDS = [
    { cmd: '/help', desc: 'Show all commands', via: 'local' },
    { cmd: '/plan', desc: 'Toggle Plan mode', via: 'WS plan' },
    { cmd: '/doctor', desc: 'System diagnostics (8 checks)', via: 'WS doctor' },
    { cmd: '/rewind', desc: 'Rewind to checkpoint', via: 'WS rewind' },
    { cmd: '/update-docs', desc: 'Auto-update project docs', via: 'WS update_docs' },
    { cmd: '/compact', desc: 'Compact context', via: 'WS compact' },
    { cmd: '/model', desc: 'Switch model (type name)', via: 'WS model_change' },
    { cmd: '/exit', desc: 'Exit JWCode', via: 'local' },
];
export function CommandPalette({ onSelect }) {
    const [filter, setFilter] = useState('/');
    const [selected, setSelected] = useState(0);
    const visible = useMemo(() => {
        const f = filter.toLowerCase().replace(/^\//, '');
        const filtered = COMMANDS.filter(c => c.cmd.toLowerCase().includes(f) || c.desc.toLowerCase().includes(f));
        return filtered.length > 0 ? filtered : COMMANDS;
    }, [filter]);
    useEffect(() => {
        setSelected(0);
    }, [visible.length]);
    useInput((input, key) => {
        if (key.escape) {
            onSelect(null);
            return;
        }
        if (key.downArrow) {
            setSelected(prev => Math.min(prev + 1, visible.length - 1));
            return;
        }
        if (key.upArrow) {
            setSelected(prev => Math.max(prev - 1, 0));
            return;
        }
        if (key.return) {
            if (visible.length > 0 && selected >= 0 && selected < visible.length) {
                onSelect(visible[selected].cmd);
            }
            return;
        }
        if (key.backspace || key.delete) {
            setFilter(prev => prev.length > 1 ? prev.slice(0, -1) : '/');
            return;
        }
        // Append typed characters
        if (input && input.length === 1 && !key.ctrl && !key.meta) {
            setFilter(prev => prev + input);
        }
    });
    return (_jsxs(Box, { flexDirection: "column", borderStyle: "double", borderColor: "cyan", paddingX: 1, width: 56, alignSelf: "center", marginTop: 1, children: [_jsxs(Box, { children: [_jsx(Text, { bold: true, color: "cyan", children: "Command Palette" }), _jsx(Text, { dimColor: true, children: "  Up/Down select  Enter confirm  Esc cancel" })] }), _jsx(Box, { borderStyle: "single", borderColor: "grey", paddingX: 1, marginY: 1, children: _jsx(Text, { children: filter }) }), visible.map((cmd, i) => (_jsxs(Box, { paddingLeft: 1, children: [_jsxs(Text, { color: i === selected ? 'cyan' : undefined, backgroundColor: i === selected ? 'blue' : undefined, bold: i === selected, children: [' ', i === selected ? '> ' : '  '] }), _jsx(Text, { color: "green", bold: i === selected, children: cmd.cmd }), _jsxs(Text, { dimColor: true, children: ["  ", cmd.desc] }), _jsxs(Text, { color: "yellow", dimColor: i !== selected, children: ["  (", cmd.via, ")"] })] }, cmd.cmd)))] }));
}

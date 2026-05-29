import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
/**
 * CommandPalette — / filterable popup.
 * Character input is handled by TextInput; this only handles navigation.
 */
import { useState, useMemo, useEffect } from 'react';
import { Box, Text, useInput, useStdout } from 'ink';
import { ALL_COMMANDS } from '../commands/index.js';
export function CommandPalette({ filter, onSelect }) {
    const [selected, setSelected] = useState(0);
    const [scrollOffset, setScrollOffset] = useState(0);
    const { stdout } = useStdout();
    const terminalRows = stdout?.rows || 24;
    const visible = useMemo(() => {
        const f = filter.replace(/^\//, '').toLowerCase();
        if (!f)
            return ALL_COMMANDS;
        return ALL_COMMANDS.filter(c => c.cmd.toLowerCase().includes(f) || c.desc.includes(f));
    }, [filter]);
    useEffect(() => { setSelected(0); setScrollOffset(0); }, [filter]);
    const maxShow = Math.max(5, terminalRows - 13);
    // Keep selected row in view
    useEffect(() => {
        setScrollOffset(prev => {
            if (selected < prev)
                return selected;
            if (selected >= prev + maxShow)
                return selected - maxShow + 1;
            return prev;
        });
    }, [selected, maxShow]);
    const sliced = visible.slice(scrollOffset, scrollOffset + maxShow);
    useInput((_input, key) => {
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
        if (key.pageDown) {
            setSelected(prev => Math.min(prev + maxShow, visible.length - 1));
            return;
        }
        if (key.pageUp) {
            setSelected(prev => Math.max(prev - maxShow, 0));
            return;
        }
        if (key.home) {
            setSelected(0);
            return;
        }
        if (key.end) {
            setSelected(visible.length - 1);
            return;
        }
        if (key.return) {
            if (visible.length > 0 && selected >= 0 && selected < visible.length) {
                onSelect(visible[selected].cmd);
            }
        }
    });
    return (_jsxs(Box, { flexDirection: "column", borderStyle: "single", borderColor: "cyan", paddingX: 1, width: 52, children: [_jsxs(Box, { children: [_jsx(Text, { bold: true, color: "cyan", children: "\u547D\u4EE4\u5217\u8868" }), _jsx(Text, { dimColor: true, children: "  \u2191\u2193\u9009\u62E9 / PgUp/PgDn\u7FFB\u9875 / \u56DE\u8F66\u786E\u8BA4 / Esc\u53D6\u6D88" })] }), sliced.map((cmd, i) => {
                const idx = scrollOffset + i;
                return (_jsxs(Box, { paddingLeft: 1, children: [_jsx(Text, { color: idx === selected ? 'cyan' : undefined, bold: idx === selected, children: idx === selected ? '> ' : '  ' }), _jsx(Text, { color: "green", children: cmd.cmd }), _jsxs(Text, { dimColor: true, children: ["  ", cmd.desc] }), _jsxs(Text, { color: cmd.via === 'ws' ? 'yellow' : 'blue', dimColor: idx !== selected, children: ["(", cmd.via === 'ws' ? '后端' : '本地', ")"] })] }, cmd.cmd));
            }), visible.length > maxShow && (_jsx(Box, { children: _jsxs(Text, { dimColor: true, children: ["  ", scrollOffset + 1, "-", Math.min(scrollOffset + maxShow, visible.length), " / ", visible.length] }) }))] }));
}

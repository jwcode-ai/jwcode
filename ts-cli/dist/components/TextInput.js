import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useRef, useCallback } from 'react';
import { Box, Text, useInput } from 'ink';
// Rough token estimation: English ~4 chars/token, CJK ~1.5 chars/token
function estimateTokens(text) {
    let cjk = 0;
    let other = 0;
    for (const ch of text) {
        if (/[一-鿿㐀-䶿豈-﫿　-〿＀-￯]/.test(ch)) {
            cjk++;
        }
        else {
            other++;
        }
    }
    return Math.ceil(cjk / 1.5 + other / 4);
}
const MAX_HISTORY = 30;
const HISTORY_KEY = 'jwcode-tscli-history';
function loadHistory() {
    try {
        const raw = process.env.JWCODE_HISTORY
            || (typeof sessionStorage !== 'undefined' ? sessionStorage.getItem(HISTORY_KEY) : null);
        return raw ? JSON.parse(raw) : [];
    }
    catch {
        return [];
    }
}
function saveHistory(entries) {
    try {
        if (typeof sessionStorage !== 'undefined') {
            sessionStorage.setItem(HISTORY_KEY, JSON.stringify(entries));
        }
    }
    catch { /* ignore */ }
}
export function saveToHistory(text) {
    const trimmed = text.trim();
    if (!trimmed)
        return;
    const history = loadHistory().filter(h => h !== trimmed);
    history.unshift(trimmed);
    saveHistory(history.slice(0, MAX_HISTORY));
}
export function TextInput({ value, onChange, onSubmit, placeholder, disabled }) {
    const historyRef = useRef(loadHistory());
    const histIdxRef = useRef(-1);
    const draftRef = useRef('');
    const navigateHistory = useCallback((dir) => {
        const history = historyRef.current;
        if (history.length === 0)
            return null;
        if (histIdxRef.current === -1) {
            draftRef.current = value;
            if (dir === 'up') {
                histIdxRef.current = 0;
                return history[0];
            }
            return null;
        }
        if (dir === 'up') {
            const next = Math.min(histIdxRef.current + 1, history.length - 1);
            histIdxRef.current = next;
            return history[next];
        }
        else {
            const next = histIdxRef.current - 1;
            if (next < 0) {
                histIdxRef.current = -1;
                return draftRef.current;
            }
            histIdxRef.current = next;
            return history[next];
        }
    }, [value]);
    const resetHistory = useCallback(() => {
        histIdxRef.current = -1;
        draftRef.current = '';
    }, []);
    useInput((input, key) => {
        if (disabled)
            return;
        if (key.return) {
            onSubmit(value);
            resetHistory();
            return;
        }
        if (key.upArrow) {
            const hist = navigateHistory('up');
            if (hist !== null)
                onChange(hist);
            return;
        }
        if (key.downArrow) {
            const hist = navigateHistory('down');
            if (hist !== null)
                onChange(hist);
            return;
        }
        // Any manual edit resets history navigation
        if (histIdxRef.current !== -1 && input) {
            resetHistory();
        }
        if (key.backspace || key.delete) {
            onChange(value.slice(0, -1));
            resetHistory();
        }
        else if (input && !key.ctrl && !key.meta && !key.tab && !key.escape) {
            onChange(value + input);
        }
    });
    const display = value || '';
    const showPlaceholder = !display && placeholder;
    const tokenEstimate = display ? estimateTokens(display) : 0;
    const charCount = display.length;
    return (_jsxs(Box, { flexDirection: "column", children: [_jsxs(Box, { children: [display ? _jsx(Text, { children: display }) : _jsx(Text, { dimColor: true, children: placeholder }), _jsx(Text, { dimColor: true, children: "\u258A" })] }), charCount > 0 && (_jsxs(Box, { children: [_jsxs(Text, { dimColor: true, children: ["  ", charCount, " \u5B57\u7B26 \u2248 ", tokenEstimate, " tokens"] }), tokenEstimate > 100000 && (_jsx(Text, { color: "red", children: "  \u26A0 \u63A5\u8FD1\u4E0A\u4E0B\u6587\u4E0A\u9650" }))] }))] }));
}

import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
/**
 * Root Ink component — layout, WS connection, and event dispatch.
 * Mirrors JwCodeApp from python-cli/jwcode/app.py.
 */
import { useState, useEffect, useRef, useCallback } from 'react';
import { Box, Text, useInput, useApp, useStdout } from 'ink';
import { TextInput, saveToHistory } from './components/TextInput.js';
import { JwCodeClient } from './client.js';
import { StatusLine } from './components/StatusLine.js';
import { ChatArea } from './components/ChatArea.js';
import { CommandPalette } from './components/CommandPalette.js';
import { ApprovalModal } from './components/ApprovalModal.js';
import { updateAppState, useAppState, getStore } from './hooks/useAppState.js';
import { createMessage, parseData, } from './protocol.js';
import { SLASH_COMMANDS, HELP_TEXT } from './commands/index.js';
// Unwrap nested {"command": {"command": ...}} JSON from streaming tool args
function cleanArgs(raw) {
    let s = raw;
    for (let i = 0; i < 10; i++) {
        try {
            const obj = JSON.parse(s);
            if (obj && typeof obj === 'object' && !Array.isArray(obj)) {
                if (typeof obj.command === 'string')
                    return obj.command;
                if (typeof obj.command === 'object') {
                    s = JSON.stringify(obj.command);
                    continue;
                }
                return JSON.stringify(obj, null, 2);
            }
            return s;
        }
        catch {
            return s;
        }
    }
    return s;
}
export function App({ backendUrl, wsUrl, onExit }) {
    const [input, setInput] = useState('');
    const [showPalette, setShowPalette] = useState(false);
    const [showHelp, setShowHelp] = useState(false);
    const [helpScroll, setHelpScroll] = useState(0);
    const [showApproval, setShowApproval] = useState(null);
    const { exit } = useApp();
    const state = useAppState();
    const clientRef = useRef(null);
    const { stdout } = useStdout();
    const terminalRows = stdout?.rows || 24;
    const terminalCols = stdout?.columns || 80;
    // Reserve rows for: status(1) + scroll-hint(1) + plan-waiting(optional) + input-border(2) + palette(optional)
    const reservedRows = 8;
    const hline = '─'.repeat(terminalCols);
    // Initialize WebSocket connection
    useEffect(() => {
        const client = new JwCodeClient(backendUrl, wsUrl);
        clientRef.current = client;
        // Wire all event handlers
        wireHandlers(client);
        client.connect().then(() => {
            updateAppState(s => ({ ...s, connected: true }));
            // Fetch models
            fetch(`${backendUrl}/api/models`)
                .then(r => r.json())
                .then(d => {
                const models = d.data?.models;
                if (models?.length) {
                    updateAppState(s => ({ ...s, modelName: models[0].name || '' }));
                }
            })
                .catch(() => { });
        }).catch(err => {
            updateAppState(s => ({ ...s, statusText: `Connection failed: ${err.message}` }));
        });
        return () => { client.close(); };
    }, [backendUrl, wsUrl]);
    // Shared command execution — callable from both handleSubmit and palette select
    const executeCommand = useCallback((value) => {
        const text = value.trim();
        if (!text || !clientRef.current)
            return;
        setInput('');
        setShowHelp(false);
        setShowPalette(false);
        const parts = text.startsWith('/') ? text.split(/\s+/) : [];
        const cmd = parts[0] || null;
        const cmdArg = parts.slice(1).join(' ');
        if (cmd && cmd in SLASH_COMMANDS) {
            const def = SLASH_COMMANDS[cmd];
            if (def === null) {
                setShowHelp(true);
                setHelpScroll(0);
                return;
            }
            const { action, needsArg } = def;
            const client = clientRef.current;
            switch (action) {
                case '__exit__':
                    onExit();
                    return;
                case '__confirm_plan':
                    updateAppState(prev => {
                        if (!prev.planWaiting)
                            return prev;
                        client?.planConfirm();
                        return { ...prev, planWaiting: false };
                    });
                    return;
                case '__cancel_plan':
                    updateAppState(prev => ({ ...prev, planWaiting: false }));
                    return;
                case 'plan_mode':
                    updateAppState(prev => ({ ...prev, planMode: !prev.planMode }));
                    return;
                case 'auto_mode':
                    updateAppState(prev => ({ ...prev, autoMode: !prev.autoMode }));
                    return;
                case 'clear':
                    updateAppState(prev => ({ ...prev, messages: [], currentMessage: null }));
                    return;
                case 'model_change':
                    if (needsArg && cmdArg)
                        client?.switchModel(cmdArg);
                    return;
                case 'show_context':
                    updateAppState(prev => ({
                        ...prev,
                        statusText: `会话消息: ${prev.messages.length} | 模式: ${prev.planMode ? '规划' : '执行'} | 自动: ${prev.autoMode ? '开' : '关'} | 模型: ${prev.modelName || '未连接'}`,
                    }));
                    return;
                // WS commands — send directly via client method
                case 'stop':
                    client?.stop();
                    return;
                case 'pause':
                    client?.pause();
                    return;
                case 'resume':
                    client?.resume();
                    return;
                case 'doctor':
                    client?.doctor();
                    return;
                case 'rewind':
                    client?.rewind();
                    return;
                case 'compact':
                    client?.compact();
                    return;
                case 'init':
                    client?.init();
                    return;
                case 'effort':
                    if (cmdArg)
                        client?.effort(cmdArg);
                    return;
                case 'branch':
                    if (cmdArg)
                        client?.branch(cmdArg);
                    return;
                case 'mcp':
                    if (cmdArg)
                        client?.mcp(cmdArg);
                    return;
                case 'skills':
                    client?.skills();
                    return;
                case 'agents':
                    client?.agents();
                    return;
                case 'config':
                    if (cmdArg)
                        client?.config(cmdArg);
                    return;
                case 'plugin':
                    if (cmdArg)
                        client?.plugin(cmdArg);
                    return;
            }
            return;
        }
        // Normal chat — don't send unmatched / commands as chat
        if (text.startsWith('/') && !(cmd && cmd in SLASH_COMMANDS))
            return;
        saveToHistory(text);
        const msg = createMessage('user', text);
        updateAppState(prev => ({ ...prev, messages: [...prev.messages, msg] }));
        clientRef.current.chat(text, state.planMode);
    }, [onExit, state.planMode]);
    const handleSubmit = useCallback((value) => {
        // When palette is open, Enter is handled by CommandPalette — avoid double execution
        if (showPalette)
            return;
        executeCommand(value);
    }, [executeCommand, showPalette]);
    // / opens command palette; backspace past / closes it
    const handleChange = useCallback((value) => {
        setInput(value);
        if (value.startsWith('/')) {
            setShowPalette(true);
        }
        else {
            setShowPalette(false);
        }
    }, []);
    const handlePaletteSelect = useCallback((cmd) => {
        if (cmd) {
            // Execute the command directly (palette Enter = select + execute)
            executeCommand(cmd);
        }
        else {
            setShowPalette(false);
            setInput('');
        }
    }, [executeCommand]);
    const handleApprovalAllow = useCallback((approvalId) => {
        clientRef.current?.approveHook(approvalId);
        setShowApproval(null);
    }, []);
    const handleApprovalDeny = useCallback((approvalId) => {
        clientRef.current?.denyHook(approvalId);
        setShowApproval(null);
    }, []);
    // Wire WS handlers — all streaming updates go through one batched render per tick
    function wireHandlers(client) {
        // Shared batch state — accumulate then apply in a single updateAppState per cycle
        const INTERVAL = 150; // ms between renders during streaming
        let pendingContent = '';
        let pendingThinking = '';
        let pendingToolCalls = [];
        let batchTimer = null;
        let batchChanged = false;
        function applyBatch() {
            batchTimer = null;
            if (!batchChanged)
                return;
            batchChanged = false;
            const c = pendingContent;
            pendingContent = '';
            const t = pendingThinking;
            pendingThinking = '';
            const tcfns = pendingToolCalls;
            pendingToolCalls = [];
            updateAppState(prev => {
                if (!prev.currentMessage)
                    return prev;
                if (c)
                    prev.currentMessage.content += c;
                if (t)
                    prev.currentMessage.thinking += t;
                for (const fn of tcfns)
                    fn(prev.currentMessage);
                return { ...prev };
            });
        }
        function scheduleBatch() {
            batchChanged = true;
            if (!batchTimer)
                batchTimer = setTimeout(applyBatch, INTERVAL);
        }
        // Flush immediately and cancel timer (used at start/complete)
        function flushNow() {
            if (batchTimer) {
                clearTimeout(batchTimer);
                batchTimer = null;
            }
            applyBatch();
        }
        client.on('start', () => {
            flushNow();
            const msg = createMessage('assistant');
            updateAppState(prev => ({
                ...prev,
                currentMessage: msg,
                messages: [...prev.messages, msg],
                scrollOffset: prev.scrollOffset > 0 ? prev.scrollOffset + 1 : 0,
            }));
        });
        client.on('content', (m) => {
            const text = typeof m.data === 'string' ? m.data : (m.data ? String(m.data) : '');
            pendingContent += text;
            scheduleBatch();
        });
        client.on('thinking', (m) => {
            pendingThinking += typeof m.data === 'string' ? m.data : '';
            scheduleBatch();
        });
        client.on('tool_call', (m) => {
            const d = parseData(m);
            pendingToolCalls.push((msg) => {
                let existingIdx = d.id
                    ? msg.toolCalls.findIndex(t => t.id === d.id)
                    : -1;
                if (existingIdx < 0 && d.name) {
                    existingIdx = msg.toolCalls.findIndex(t => t.name === d.name && t.status === 'running');
                }
                if (existingIdx >= 0) {
                    const existing = { ...msg.toolCalls[existingIdx] };
                    if (d.args)
                        existing.args = cleanArgs(d.args);
                    if (d.complete)
                        existing.status = 'complete';
                    if (d.result)
                        existing.result = d.result;
                    msg.toolCalls = [...msg.toolCalls];
                    msg.toolCalls[existingIdx] = existing;
                }
                else {
                    const updated = [...msg.toolCalls];
                    updated.push({
                        id: d.id || (d.name ? `${d.name}-${Date.now()}` : ''),
                        name: d.name || '',
                        args: d.args ? cleanArgs(d.args) : undefined,
                        status: d.complete ? 'complete' : 'running',
                        complete: !!d.complete,
                    });
                    msg.toolCalls = updated;
                }
            });
            scheduleBatch();
        });
        client.on('tool_result', (m) => {
            const d = parseData(m);
            pendingToolCalls.push((msg) => {
                const tcs = [...msg.toolCalls];
                for (let i = tcs.length - 1; i >= 0; i--) {
                    if (tcs[i].name === d.toolName && !tcs[i].result) {
                        tcs[i] = { ...tcs[i], result: d.result || '', status: 'complete' };
                        break;
                    }
                }
                msg.toolCalls = tcs;
            });
            scheduleBatch();
        });
        client.on('complete', () => {
            flushNow();
            updateAppState(prev => ({ ...prev, currentMessage: null }));
        });
        client.on('error', (m) => {
            const text = String(m.data || 'Error');
            // Show errors compactly in status bar, avoid flooding chat
            updateAppState(prev => ({
                ...prev,
                statusText: `Error: ${text.slice(0, 120)}`,
            }));
        });
        let firstTokenUpdate = true;
        client.on('token_update', (m) => {
            // Try parsing data — backend sends it as a JSON string inside the WS data field
            let d = {};
            if (typeof m.data === 'string') {
                try {
                    d = JSON.parse(m.data);
                }
                catch { /* ignore */ }
            }
            else if (m.data && typeof m.data === 'object') {
                d = m.data;
            }
            // One-time debug: log raw format to help diagnose parsing issues
            if (firstTokenUpdate) {
                firstTokenUpdate = false;
                console.log('[token_update] raw data type:', typeof m.data, '| parsed keys:', Object.keys(d).join(','));
            }
            const promptTokens = Number(d.promptTokens) || 0;
            const completionTokens = Number(d.completionTokens) || 0;
            const totalTokens = Number(d.totalTokens) || 0;
            const usageRatio = Number(d.usageRatio) || 0;
            if (totalTokens > 0) {
                updateAppState(prev => ({
                    ...prev,
                    usage: { promptTokens, completionTokens, totalTokens, usageRatio },
                    modelName: d.model || prev.modelName,
                }));
            }
        });
        client.on('hook_ask', (m) => {
            const d = parseData(m);
            const approvalId = d.approvalId || '';
            // Auto-approve when auto mode is on
            if (getStore().getState().autoMode) {
                client.approveHook(approvalId);
                return;
            }
            setShowApproval({
                approvalId,
                toolName: d.toolName || '',
                payload: d.askPayload || d.payload || JSON.stringify(d),
            });
        });
        client.on('plan_start', () => {
            updateAppState(prev => ({ ...prev, planWaiting: false }));
        });
        client.on('plan_complete', (m) => {
            const d = parseData(m);
            const status = d.status;
            if (status === 'waiting_confirm') {
                updateAppState(prev => ({ ...prev, planWaiting: true }));
            }
            else {
                updateAppState(prev => ({ ...prev, planWaiting: false }));
            }
        });
        client.on('notification', (m) => {
            const text = String(m.data || '');
            updateAppState(prev => ({
                ...prev,
                statusText: text,
                connected: text === 'Reconnected.' ? true : prev.connected,
            }));
        });
    }
    // Keyboard: escape + scroll
    useInput((input, key) => {
        if (key.escape && showApproval) {
            handleApprovalDeny(showApproval.approvalId);
            return;
        }
        if (key.escape && showHelp) {
            setShowHelp(false);
            return;
        }
        // Help scrolling (when help is visible)
        if (showHelp) {
            const helpLines = HELP_TEXT.split('\n');
            const helpMax = Math.max(5, terminalRows - 12);
            if (key.pageUp || key.upArrow) {
                setHelpScroll(prev => Math.min(prev + (key.pageUp ? helpMax : 1), helpLines.length - 1));
                return;
            }
            if (key.pageDown || key.downArrow) {
                setHelpScroll(prev => Math.max(0, prev - (key.pageDown ? helpMax : 1)));
                return;
            }
            if (key.home) {
                setHelpScroll(helpLines.length - 1);
                return;
            }
            if (key.end) {
                setHelpScroll(0);
                return;
            }
        }
        // Scroll: arrow keys = fine, page keys = coarse
        if (key.pageUp) {
            updateAppState(prev => ({
                ...prev,
                scrollOffset: Math.min(prev.scrollOffset + 5, prev.messages.length),
            }));
            return;
        }
        if (key.upArrow && !showApproval) {
            updateAppState(prev => ({
                ...prev,
                scrollOffset: Math.min(prev.scrollOffset + 1, prev.messages.length),
            }));
            return;
        }
        if (key.pageDown) {
            updateAppState(prev => ({
                ...prev,
                scrollOffset: Math.max(0, prev.scrollOffset - 5),
            }));
            return;
        }
        if (key.downArrow && !showApproval) {
            updateAppState(prev => ({
                ...prev,
                scrollOffset: Math.max(0, prev.scrollOffset - 1),
            }));
            return;
        }
        if (key.home || (key.home && key.ctrl)) {
            updateAppState(prev => ({ ...prev, scrollOffset: prev.messages.length }));
            return;
        }
        if (key.end || (key.end && key.ctrl)) {
            updateAppState(prev => ({ ...prev, scrollOffset: 0 }));
            return;
        }
        // Tab: toggle plan/act mode
        if (key.tab) {
            updateAppState(prev => ({ ...prev, planMode: !prev.planMode }));
            return;
        }
    });
    const placeholder = '';
    return (_jsxs(Box, { flexDirection: "column", width: "100%", height: "100%", children: [_jsx(StatusLine, {}), _jsx(Box, { flexGrow: 1, flexDirection: "column", children: _jsx(ChatArea, { messages: state.messages, currentMessage: state.currentMessage, scrollOffset: state.scrollOffset, terminalRows: terminalRows, reservedRows: reservedRows }) }), _jsxs(Box, { flexDirection: "row", borderStyle: "single", borderColor: state.connected ? 'cyan' : 'red', paddingLeft: 1, children: [_jsx(Text, { color: "green", bold: true, children: "> " }), _jsx(Box, { flexGrow: 1, children: _jsx(TextInput, { value: input, onChange: handleChange, onSubmit: handleSubmit, placeholder: placeholder, disabled: showApproval !== null }) })] }), _jsxs(Box, { paddingLeft: 2, height: 1, children: [_jsx(Text, { color: state.planMode ? 'cyan' : 'grey', bold: state.planMode, dimColor: !state.planMode, children: state.planMode ? '◉ Plan' : '○ Plan' }), _jsx(Text, { children: "  " }), _jsx(Text, { color: !state.planMode ? 'green' : 'grey', bold: !state.planMode, dimColor: state.planMode, children: !state.planMode ? '◉ Act' : '○ Act' }), _jsx(Text, { dimColor: true, children: "  Tab \u5207\u6362" })] }), !state.connected && (_jsxs(Box, { children: [_jsx(Text, { color: "red", children: "\u540E\u7AEF\u672A\u8FDE\u63A5 \u2014 WebSocket \u91CD\u8BD5\u4E2D\u3002\u5982\u540E\u7AEF\u672A\u542F\u52A8\u8BF7\u7528 " }), _jsx(Text, { color: "yellow", bold: true, children: "npm start" })] })), state.planWaiting && (_jsx(Box, { children: _jsx(Text, { color: "yellow", bold: true, children: "Plan ready \u2014 /confirm to execute, /cancel to discard." }) })), showPalette && _jsx(CommandPalette, { filter: input, onSelect: handlePaletteSelect }), showHelp && (() => {
                const helpLines = HELP_TEXT.split('\n');
                const helpMax = Math.max(5, terminalRows - 12);
                const helpEnd = Math.max(0, helpLines.length - helpScroll);
                const helpStart = Math.max(0, helpEnd - helpMax);
                const visibleHelp = helpLines.slice(helpStart, helpEnd);
                return (_jsxs(Box, { flexDirection: "column", borderStyle: "single", borderColor: "cyan", paddingX: 1, children: [helpLines.length > helpMax && (_jsx(Box, { children: _jsxs(Text, { dimColor: true, children: ["  ", helpStart + 1, "-", helpEnd, " / ", helpLines.length, "  PgUp/PgDn\u7FFB\u9875 / Esc\u5173\u95ED"] }) })), visibleHelp.map((line, i) => (_jsx(Text, { color: "cyan", children: line }, i)))] }));
            })(), showApproval && (_jsx(ApprovalModal, { toolName: showApproval.toolName, payload: showApproval.payload, onAllow: () => handleApprovalAllow(showApproval.approvalId), onDeny: () => handleApprovalDeny(showApproval.approvalId) }))] }));
}

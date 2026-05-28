import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
/**
 * Root Ink component — layout, WS connection, and event dispatch.
 * Mirrors JwCodeApp from python-cli/jwcode/app.py.
 */
import { useState, useEffect, useRef, useCallback } from 'react';
import { Box, Text, useInput, useApp } from 'ink';
import { TextInput } from './components/TextInput.js';
import { JwCodeClient } from './client.js';
import { StatusLine } from './components/StatusLine.js';
import { ChatArea } from './components/ChatArea.js';
import { CommandPalette } from './components/CommandPalette.js';
import { ApprovalModal } from './components/ApprovalModal.js';
import { updateAppState, useAppState } from './hooks/useAppState.js';
import { createMessage, SLASH_COMMANDS, parseData, } from './protocol.js';
export function App({ backendUrl, wsUrl, onExit }) {
    const [input, setInput] = useState('');
    const [showPalette, setShowPalette] = useState(false);
    const [showApproval, setShowApproval] = useState(null);
    const { exit } = useApp();
    const state = useAppState();
    const clientRef = useRef(null);
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
    // Input handling
    const handleSubmit = useCallback((value) => {
        const text = value.trim();
        if (!text || !clientRef.current)
            return;
        setInput('');
        const cmd = text.startsWith('/') ? text.split(/\s+/)[0] : null;
        if (cmd && cmd in SLASH_COMMANDS) {
            const action = SLASH_COMMANDS[cmd];
            if (action === null) {
                setShowPalette(true);
            }
            else if (action === '__exit__') {
                onExit();
            }
            else if (action === '__confirm_plan') {
                const s = updateAppState;
                s(prev => {
                    if (!prev.planWaiting)
                        return prev;
                    clientRef.current?.planConfirm();
                    return { ...prev, planWaiting: false };
                });
            }
            else if (action === '__cancel_plan') {
                updateAppState(prev => {
                    if (!prev.planWaiting)
                        return prev;
                    return { ...prev, planWaiting: false };
                });
            }
            else if (action === 'plan_mode') {
                updateAppState(prev => ({ ...prev, planMode: !prev.planMode }));
            }
            else if (action === 'model_change') {
                const parts = text.split(/\s+/);
                const model = parts[1];
                if (model) {
                    clientRef.current?.switchModel(model);
                    updateAppState(prev => ({ ...prev, modelName: model }));
                }
            }
            else {
                // Other WS commands
                clientRef.current?.send(action);
            }
            return;
        }
        // Normal chat
        const msg = createMessage('user', text);
        updateAppState(prev => ({ ...prev, messages: [...prev.messages, msg] }));
        clientRef.current.chat(text, state.planMode);
    }, [onExit, state.planMode]);
    // / opens command palette
    const handleChange = useCallback((value) => {
        setInput(value);
        if (value === '/') {
            setShowPalette(true);
        }
    }, []);
    const handlePaletteSelect = useCallback((cmd) => {
        setShowPalette(false);
        if (cmd) {
            setInput(cmd + ' ');
        }
        else {
            setInput('');
        }
    }, []);
    const handleApprovalAllow = useCallback((approvalId) => {
        clientRef.current?.approveHook(approvalId);
        setShowApproval(null);
    }, []);
    const handleApprovalDeny = useCallback((approvalId) => {
        clientRef.current?.denyHook(approvalId);
        setShowApproval(null);
    }, []);
    // Wire WS handlers that need React state access
    function wireHandlers(client) {
        client.on('start', () => {
            const msg = createMessage('assistant');
            updateAppState(prev => ({ ...prev, currentMessage: msg, messages: [...prev.messages, msg] }));
        });
        client.on('content', (m) => {
            const text = typeof m.data === 'string' ? m.data : (m.data ? String(m.data) : '');
            updateAppState(prev => {
                if (prev.currentMessage) {
                    prev.currentMessage.content += text;
                }
                return { ...prev };
            });
        });
        client.on('thinking', (m) => {
            const text = typeof m.data === 'string' ? m.data : '';
            updateAppState(prev => {
                if (prev.currentMessage)
                    prev.currentMessage.thinking += text;
                return { ...prev };
            });
        });
        client.on('tool_call', (m) => {
            const d = parseData(m);
            updateAppState(prev => {
                if (prev.currentMessage) {
                    prev.currentMessage.toolCalls.push({
                        id: d.id || '',
                        name: d.name || '',
                        args: d.args,
                        status: 'running',
                        complete: false,
                    });
                }
                return { ...prev };
            });
        });
        client.on('complete', () => {
            updateAppState(prev => ({ ...prev, currentMessage: null }));
        });
        client.on('error', (m) => {
            const text = m.data || 'Error';
            updateAppState(prev => {
                const errMsg = createMessage('system', String(text));
                return { ...prev, messages: [...prev.messages, errMsg] };
            });
        });
        client.on('token_update', (m) => {
            const d = parseData(m);
            updateAppState(prev => ({
                ...prev,
                usage: {
                    promptTokens: d.promptTokens || 0,
                    completionTokens: d.completionTokens || 0,
                    totalTokens: d.totalTokens || 0,
                    usageRatio: d.usageRatio || 0,
                },
                modelName: d.model || prev.modelName,
            }));
        });
        client.on('hook_ask', (m) => {
            const d = parseData(m);
            setShowApproval({
                approvalId: d.approvalId || '',
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
            updateAppState(prev => ({ ...prev, statusText: String(m.data || '') }));
        });
    }
    // Keyboard escape handler
    useInput((input, key) => {
        if (key.escape && showApproval) {
            handleApprovalDeny(showApproval.approvalId);
        }
    });
    const placeholder = state.planMode
        ? 'Message... [PLAN] (Enter send, / for commands)'
        : 'Message... (Enter send, / for commands)';
    const hints = ' / commands  /plan toggle  /doctor check  /compact trim  /help  /exit';
    return (_jsxs(Box, { flexDirection: "column", width: "100%", height: "100%", children: [_jsx(StatusLine, {}), _jsx(Box, { flexGrow: 1, flexDirection: "column", children: _jsx(ChatArea, { messages: state.messages, currentMessage: state.currentMessage }) }), state.planWaiting && (_jsx(Box, { children: _jsx(Text, { color: "yellow", bold: true, children: "Plan ready \u2014 /confirm to execute, /cancel to discard." }) })), _jsxs(Box, { flexDirection: "row", borderStyle: "single", borderColor: "cyan", paddingLeft: 1, children: [_jsx(Text, { color: "green", bold: true, children: "> " }), _jsx(Box, { flexGrow: 1, children: _jsx(TextInput, { value: input, onChange: handleChange, onSubmit: handleSubmit, placeholder: placeholder, disabled: showPalette || showApproval !== null }) })] }), _jsx(Box, { children: _jsx(Text, { color: "grey", dimColor: true, children: hints }) }), showPalette && _jsx(CommandPalette, { onSelect: handlePaletteSelect }), showApproval && (_jsx(ApprovalModal, { toolName: showApproval.toolName, payload: showApproval.payload, onAllow: () => handleApprovalAllow(showApproval.approvalId), onDeny: () => handleApprovalDeny(showApproval.approvalId) }))] }));
}

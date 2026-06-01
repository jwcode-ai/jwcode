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
import {
  updateAppState, getStore,
  useAppCurrentMessage, useAppConnected,
  useAppPlanMode, useAppPlanWaiting,
  useAppStatusText, useAppChatArea,
  useAppIsGenerating,
  useAppToolCallsExpanded,
} from './hooks/useAppState.js';
import { setClient } from './hooks/useWebSocket.js';
import {
  createMessage, parseData,
  type WSMessage, type ToolCall, type Message,
} from './protocol.js';
import { SLASH_COMMANDS, HELP_TEXT } from './commands/index.js';

// Unwrap nested {"command": {"command": ...}} JSON from streaming tool args
function cleanArgs(raw: string): string {
  let s = raw;
  for (let i = 0; i < 10; i++) {
    try {
      const obj = JSON.parse(s);
      if (obj && typeof obj === 'object' && !Array.isArray(obj)) {
        if (typeof obj.command === 'string') return obj.command;
        if (typeof obj.command === 'object') { s = JSON.stringify(obj.command); continue; }
        return JSON.stringify(obj, null, 2);
      }
      return s;
    } catch { return s; }
  }
  return s;
}

interface Props {
  backendUrl: string;
  wsUrl: string;
  onExit: () => void;
}

export function App({ backendUrl, wsUrl, onExit }: Props) {
  const [input, setInput] = useState('');
  const [showPalette, setShowPalette] = useState(false);
  const [showHelp, setShowHelp] = useState(false);
  const [helpScroll, setHelpScroll] = useState(0);
  const [showApproval, setShowApproval] = useState<{
    approvalId: string; toolName: string; payload: string;
  } | null>(null);
  const { exit } = useApp();
  // Selector-based subscriptions — each component re-renders only when its slice changes
  const connected = useAppConnected();
  const planMode = useAppPlanMode();
  const planWaiting = useAppPlanWaiting();
  const currentMessage = useAppCurrentMessage();
  const isGenerating = useAppIsGenerating();
  const chatAreaProps = useAppChatArea();
  const toolCallsExpanded = useAppToolCallsExpanded();

  // Refs for stable callback access
  const clientRef = useRef<JwCodeClient | null>(null);
  const lastEscRef = useRef(0);
  const currentMessageRef = useRef(currentMessage);
  currentMessageRef.current = currentMessage;
  const planModeRef = useRef(planMode);
  planModeRef.current = planMode;

  const { stdout } = useStdout();
  const terminalRows = (stdout as NodeJS.WriteStream)?.rows || 24;
  const terminalCols = (stdout as NodeJS.WriteStream)?.columns || 80;
  // Reserve rows for: input-border(3) + mode-toggle(1) + status-line(1-2)
  const reservedRows = 6;
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
          const models = (d as any).data?.models;
          if (models?.length) {
            updateAppState(s => ({ ...s, modelName: models[0].name || '' }));
          }
        })
        .catch(() => {});
    }).catch(err => {
      updateAppState(s => ({ ...s, statusText: `Connection failed: ${err.message}` }));
    });

    return () => { client.close(); };
  }, [backendUrl, wsUrl]);

  // Shared command execution — callable from both handleSubmit and palette select
  const executeCommand = useCallback((value: string) => {
    const text = value.trim();
    if (!text || !clientRef.current) return;
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
            if (!prev.planWaiting) return prev;
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
          updateAppState(prev => ({
            ...prev,
            messages: [], currentMessage: null,
          }));
          return;
        case 'model_change':
          if (needsArg && cmdArg) client?.switchModel(cmdArg);
          return;
        case 'show_context':
          updateAppState(prev => ({
            ...prev,
            statusText: `会话消息: ${prev.messages.length} | 模式: ${prev.planMode ? '规划' : '执行'} | 自动: ${prev.autoMode ? '开' : '关'} | 模型: ${prev.modelName || '未连接'}`,
          }));
          return;
        // WS commands — send directly via client method
        case 'stop': client?.stop(); return;
        case 'pause': client?.pause(); return;
        case 'resume': client?.resume(); return;
        case 'doctor': client?.doctor(); return;
        case 'rewind': client?.rewind(); return;
        case 'compact': client?.compact(); return;
        case 'init': client?.init(); return;
        case 'effort': if (cmdArg) client?.effort(cmdArg); return;
        case 'branch': if (cmdArg) client?.branch(cmdArg); return;
        case 'mcp': if (cmdArg) client?.mcp(cmdArg); return;
        case 'skills': client?.skills(); return;
        case 'agents': client?.agents(); return;
        case 'config': if (cmdArg) client?.config(cmdArg); return;
        case 'plugin': if (cmdArg) client?.plugin(cmdArg); return;
      }
      return;
    }

    // Normal chat — don't send unmatched / commands as chat
    if (text.startsWith('/') && !(cmd && cmd in SLASH_COMMANDS)) return;
    saveToHistory(text);
    const msg = createMessage('user', text);
    updateAppState(prev => ({ ...prev, messages: [...prev.messages, msg] }));
    clientRef.current!.chat(text, planModeRef.current);
  }, [onExit]);

  const handleSubmit = useCallback((value: string) => {
    // When palette is open, Enter is handled by CommandPalette — avoid double execution
    if (showPalette) return;
    executeCommand(value);
  }, [executeCommand, showPalette]);

  // / opens command palette; backspace past / closes it
  const handleChange = useCallback((value: string) => {
    setInput(value);
    if (value.startsWith('/')) {
      setShowPalette(true);
    } else {
      setShowPalette(false);
    }
  }, []);

  const handlePaletteSelect = useCallback((cmd: string | null) => {
    if (cmd) {
      // Execute the command directly (palette Enter = select + execute)
      executeCommand(cmd);
    } else {
      setShowPalette(false);
      setInput('');
    }
  }, [executeCommand]);

  const handleApprovalAllow = useCallback((approvalId: string) => {
    clientRef.current?.approveHook(approvalId);
    setShowApproval(null);
  }, []);

  const handleApprovalDeny = useCallback((approvalId: string) => {
    clientRef.current?.denyHook(approvalId);
    setShowApproval(null);
  }, []);

  // Wire WS handlers — streaming updates batched via microtask scheduler
  function wireHandlers(client: JwCodeClient) {
    // Microtask-based batch: same-tick events merge, cross-tick events flush per tick.
    // Ink's internal 32ms throttle then composites flushes into smooth ~30fps frames.
    let _pendingContent = '';
    let _pendingThinking = '';
    let _pendingToolFns: Array<(msg: Message) => Message> = [];
    let _flushScheduled = false;

    function doStreamFlush() {
      _flushScheduled = false;
      const c = _pendingContent; _pendingContent = '';
      const t = _pendingThinking; _pendingThinking = '';
      const fns = _pendingToolFns; _pendingToolFns = [];

      if (!c && !t && fns.length === 0) return;

      updateAppState(prev => {
        if (!prev.currentMessage) return prev;
        let msg = prev.currentMessage;
        if (c) msg = { ...msg, content: msg.content + c };
        if (t) msg = { ...msg, thinking: msg.thinking + t };
        for (const fn of fns) msg = fn(msg);
        return { ...prev, currentMessage: msg };
      });
    }

    function scheduleStreamFlush() {
      if (_flushScheduled) return;
      _flushScheduled = true;
      queueMicrotask(doStreamFlush);
      // 16ms fallback: guarantees flush even if microtask queue is starved
      setTimeout(() => { if (_flushScheduled) doStreamFlush(); }, 16);
    }

    // Synchronous flush (used at start/complete boundaries)
    function flushNow() { doStreamFlush(); }

    // ---- token_update throttle ----
    let _lastTotal = 0;
    let _lastTotalTs = 0;
    let _firstTokenUpdate = true;
    let _pendingToken: Record<string, unknown> | null = null;
    let _tokenScheduled = false;

    function flushToken() {
      _tokenScheduled = false;
      const d = _pendingToken;
      if (!d) return;
      _pendingToken = null;

      const promptTokens = Number(d.promptTokens) || 0;
      const completionTokens = Number(d.completionTokens) || 0;
      const totalTokens = Number(d.totalTokens) || 0;
      const usageRatio = Number(d.usageRatio) || 0;
      if (totalTokens <= 0) return;

      const now = Date.now();
      let tokenRate = 0;
      if (_lastTotalTs > 0 && _lastTotal > 0 && now > _lastTotalTs && totalTokens > _lastTotal) {
        const deltaTokens = totalTokens - _lastTotal;
        const deltaSec = (now - _lastTotalTs) / 1000;
        const instantRate = deltaTokens / deltaSec;
        const prevRate = getStore().getState().tokenRate;
        tokenRate = prevRate > 0 ? prevRate * 0.6 + instantRate * 0.4 : instantRate;
      }
      _lastTotal = totalTokens;
      _lastTotalTs = now;

      updateAppState(prev => ({
        ...prev,
        usage: { promptTokens, completionTokens, totalTokens, usageRatio },
        modelName: (d!.model as string) || prev.modelName,
        tokenRate,
      }));
    }

    // ---- event wiring ----

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

    client.on('content', (m: WSMessage) => {
      const text = typeof m.data === 'string' ? m.data : (m.data ? String(m.data) : '');
      _pendingContent += text;
      scheduleStreamFlush();
    });

    client.on('thinking', (m: WSMessage) => {
      _pendingThinking += typeof m.data === 'string' ? m.data : '';
      scheduleStreamFlush();
    });

    client.on('tool_call', (m: WSMessage) => {
      const d = parseData(m) as unknown as ToolCall;
      _pendingToolFns.push((msg: Message): Message => {
        let existingIdx = d.id
          ? msg.toolCalls.findIndex((t: ToolCall) => t.id === d.id)
          : -1;
        if (existingIdx < 0 && d.name) {
          existingIdx = msg.toolCalls.findIndex(
            (t: ToolCall) => t.name === d.name && t.status === 'running'
          );
        }
        const tcs = [...msg.toolCalls];
        if (existingIdx >= 0) {
          const existing = { ...tcs[existingIdx] };
          if (d.args) existing.args = cleanArgs(d.args);
          if (d.complete) existing.status = 'complete';
          if (d.result) existing.result = d.result;
          tcs[existingIdx] = existing;
        } else {
          tcs.push({
            id: d.id || (d.name ? `${d.name}-${Date.now()}` : ''),
            name: d.name || '',
            args: d.args ? cleanArgs(d.args) : undefined,
            status: d.complete ? 'complete' : 'running',
            complete: !!d.complete,
            timestamp: Date.now(),
          });
        }
        return { ...msg, toolCalls: tcs };
      });
      scheduleStreamFlush();
    });

    client.on('tool_result', (m: WSMessage) => {
      const d = parseData(m) as { toolName?: string; result?: string };
      _pendingToolFns.push((msg: Message): Message => {
        const tcs = [...msg.toolCalls];
        for (let i = tcs.length - 1; i >= 0; i--) {
          if (tcs[i].name === d.toolName && !tcs[i].result) {
            const tc = tcs[i];
            const duration = tc.timestamp ? Math.floor((Date.now() - tc.timestamp) / 1000) : undefined;
            tcs[i] = { ...tc, result: d.result || '', status: 'complete', duration };
            break;
          }
        }
        return { ...msg, toolCalls: tcs };
      });
      scheduleStreamFlush();
    });

    client.on('complete', () => {
      flushNow();
      updateAppState(prev => {
        if (!prev.currentMessage) return prev;
        const msgs = [...prev.messages];
        const cm = prev.currentMessage;
        for (let i = msgs.length - 1; i >= 0; i--) {
          if (msgs[i].type === 'assistant' && msgs[i].id === cm.id) {
            msgs[i] = cm;
            break;
          }
        }
        return { ...prev, currentMessage: null, messages: msgs };
      });
    });

    client.on('error', (m: WSMessage) => {
      const text = String(m.data || 'Error');
      updateAppState(prev => ({
        ...prev,
        statusText: `Error: ${text.slice(0, 120)}`,
      }));
    });

    client.on('token_update', (m: WSMessage) => {
      let d: Record<string, unknown> = {};
      if (typeof m.data === 'string') {
        try { d = JSON.parse(m.data); } catch { /* ignore */ }
      } else if (m.data && typeof m.data === 'object') {
        d = m.data as Record<string, unknown>;
      }
      if (_firstTokenUpdate) {
        _firstTokenUpdate = false;
        console.log('[token_update] raw data type:', typeof m.data, '| parsed keys:', Object.keys(d).join(','));
      }
      const totalTokens = Number(d.totalTokens) || 0;
      if (totalTokens > 0) {
        _pendingToken = d;
        if (!_tokenScheduled) {
          _tokenScheduled = true;
          setTimeout(flushToken, 100);
        }
      }
    });

    client.on('context_compressed', (m: WSMessage) => {
      let d: Record<string, unknown> = {};
      if (typeof m.data === 'string') {
        try { d = JSON.parse(m.data); } catch { /* ignore */ }
      } else if (m.data && typeof m.data === 'object') {
        d = m.data as Record<string, unknown>;
      }
      const orig = Number(d.originalCount) || 0;
      const comp = Number(d.compressedCount) || 0;
      const saved = Number(d.tokensSaved) || 0;
      const tokensStr = saved >= 1000 ? `${(saved / 1000).toFixed(1)}K` : String(saved);
      updateAppState(prev => ({
        ...prev,
        statusText: `上下文压缩: ${orig}→${comp} 条消息, 释放 ${tokensStr} tokens`,
        usage: { ...prev.usage, usageRatio: Math.max(0, prev.usage.usageRatio - 0.15) },
      }));
    });

    client.on('hook_ask', (m: WSMessage) => {
      const d = parseData(m);
      const approvalId = (d.approvalId as string) || '';
      if (getStore().getState().autoMode) {
        client.approveHook(approvalId);
        return;
      }
      setShowApproval({
        approvalId,
        toolName: (d.toolName as string) || '',
        payload: (d.askPayload as string) || (d.payload as string) || JSON.stringify(d),
      });
    });

    client.on('plan_start', () => {
      flushNow();
      const msg = createMessage('assistant');
      updateAppState(prev => ({
        ...prev,
        planWaiting: false,
        currentMessage: msg,
        messages: [...prev.messages, msg],
        scrollOffset: prev.scrollOffset > 0 ? prev.scrollOffset + 1 : 0,
      }));
    });

    client.on('plan_thinking', (m: WSMessage) => {
      const text = typeof m.data === 'string' ? m.data : (m.data ? String(m.data) : '');
      _pendingThinking += text + '\n';
      scheduleStreamFlush();
    });

    client.on('plan_tasks', () => {
      _pendingContent += '\n📋 任务清单已生成\n';
      scheduleStreamFlush();
    });

    client.on('plan_complete', (m: WSMessage) => {
      flushNow();
      const status = (m as any).status as string | undefined;
      const planText = typeof m.data === 'string' ? m.data : '';
      updateAppState(prev => {
        if (!prev.currentMessage) return prev;
        const msgs = [...prev.messages];
        const cm = prev.currentMessage;
        for (let i = msgs.length - 1; i >= 0; i--) {
          if (msgs[i].type === 'assistant' && msgs[i].id === cm.id) {
            msgs[i] = { ...cm, content: planText || 'Plan complete.' };
            break;
          }
        }
        return {
          ...prev,
          currentMessage: null,
          messages: msgs,
          planWaiting: status === 'waiting_confirm',
        };
      });
    });

    client.on('notification', (m: WSMessage) => {
      const text = String(m.data || '');
      updateAppState(prev => ({
        ...prev,
        statusText: text,
        connected: text === 'Reconnected.' ? true : prev.connected,
      }));
    });
  }

  // Keyboard: escape + scroll
  // ESC priority: 1) close approval  2) close help  3) pause/stop generation
  useInput((input, key) => {
    if (key.escape) {
      if (showApproval) {
        handleApprovalDeny(showApproval.approvalId);
        return;
      }
      if (showHelp) {
        setShowHelp(false);
        return;
      }
      // Pause/stop generation — single ESC pauses, double ESC within 500ms stops
      if (isGenerating) {
        const now = Date.now();
        const prev = lastEscRef.current;
        lastEscRef.current = now;
        if (prev > 0 && (now - prev) < 500) {
          clientRef.current?.stop();
          updateAppState(prev => ({ ...prev, statusText: '⏹ 已终止 (ESC×2)' }));
        } else {
          clientRef.current?.pause();
          updateAppState(prev => ({ ...prev, statusText: '⏸ 已暂停 — 再按 ESC 终止' }));
        }
        return;
      }
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
      if ((key as any).home) { setHelpScroll(helpLines.length - 1); return; }
      if ((key as any).end) { setHelpScroll(0); return; }
    }
    // Ctrl+E: toggle tool call expand/collapse
    if (key.ctrl && input === 'e') {
      updateAppState(prev => ({
        ...prev,
        toolCallsExpanded: !prev.toolCallsExpanded,
      }));
      return;
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
    if ((key as any).home || ((key as any).home && key.ctrl)) {
      updateAppState(prev => ({
        ...prev,
        scrollOffset: prev.messages.length,
      }));
      return;
    }
    if ((key as any).end || ((key as any).end && key.ctrl)) {
      updateAppState(prev => ({
        ...prev,
        scrollOffset: 0,
      }));
      return;
    }
    // Tab: toggle plan/act mode
    if (key.tab) {
      updateAppState(prev => ({ ...prev, planMode: !prev.planMode, planWaiting: false }));
      return;
    }
  });

  const placeholder = '';

  return (
    <Box flexDirection="column" width="100%" height="100%">
      <Box flexGrow={1} flexDirection="column">
        <ChatArea messages={chatAreaProps.messages} currentMessage={chatAreaProps.currentMessage} scrollOffset={chatAreaProps.scrollOffset} terminalRows={terminalRows} reservedRows={reservedRows} terminalCols={terminalCols} toolCallsExpanded={toolCallsExpanded} />
      </Box>
      {/* Input — fixed below ChatArea, above any status/palette; ChatArea height stable */}
      <Box flexDirection="row" borderStyle="single" borderColor={connected ? 'cyan' : 'red'} paddingLeft={1}>
        <Text color="green" bold>&gt; </Text>
        <Box flexGrow={1}>
          <TextInput
            value={input}
            onChange={handleChange}
            onSubmit={handleSubmit}
            placeholder={placeholder}
            disabled={showApproval !== null}
          />
        </Box>
      </Box>
      {/* Mode toggle */}
      <Box paddingLeft={2} height={1}>
        <Text
          color={planMode ? 'cyan' : 'grey'}
          bold={planMode}
          dimColor={!planMode}
        >
          {planMode ? '◉ Plan' : '○ Plan'}
        </Text>
        <Text>  </Text>
        <Text
          color={!planMode ? 'green' : 'grey'}
          bold={!planMode}
          dimColor={planMode}
        >
          {!planMode ? '◉ Act' : '○ Act'}
        </Text>
        <Text dimColor>  Tab 切换</Text>
      </Box>
      {/* Status / palette / help — below input,不影响 ChatArea 高度 */}
      {!connected && (
        <Box>
          <Text color="red">后端未连接 — WebSocket 重试中。如后端未启动请用 </Text>
          <Text color="yellow" bold>npm start</Text>
        </Box>
      )}
      {planWaiting && (
        <Box>
          <Text color="yellow" bold>Plan ready — /confirm to execute, /cancel to discard.</Text>
        </Box>
      )}
      {showPalette && <CommandPalette filter={input} onSelect={handlePaletteSelect} />}
      {showHelp && (() => {
        const helpLines = HELP_TEXT.split('\n');
        const helpMax = Math.max(5, terminalRows - 12);
        const helpEnd = Math.max(0, helpLines.length - helpScroll);
        const helpStart = Math.max(0, helpEnd - helpMax);
        const visibleHelp = helpLines.slice(helpStart, helpEnd);
        return (
          <Box flexDirection="column" borderStyle="single" borderColor="cyan" paddingX={1}>
            {helpLines.length > helpMax && (
              <Box><Text dimColor>  {helpStart + 1}-{helpEnd} / {helpLines.length}  PgUp/PgDn翻页 / Esc关闭</Text></Box>
            )}
            {visibleHelp.map((line, i) => (
              <Text key={i} color="cyan">{line}</Text>
            ))}
          </Box>
        );
      })()}
      {showApproval && (
        <ApprovalModal
          toolName={showApproval.toolName}
          payload={showApproval.payload}
          onAllow={() => handleApprovalAllow(showApproval.approvalId)}
          onDeny={() => handleApprovalDeny(showApproval.approvalId)}
        />
      )}
      <StatusLine />
    </Box>
  );
}

/**
 * Root Ink component -- layout, WS connection, and event dispatch.
 */
import { useState, useEffect, useRef, useCallback } from 'react';
import { Box, Text, useStdout } from 'ink';
import { TextInput, saveToHistory } from './components/TextInput.js';
import { JwCodeClient } from './client.js';
import { StatusLine } from './components/StatusLine.js';
import { ChatArea } from './components/ChatArea.js';
import { CommandPalette } from './components/CommandPalette.js';
import { FilePalette } from './components/FilePalette.js';
import { ApprovalModal } from './components/ApprovalModal.js';
import { updateAppState, useAppSlice } from './hooks/useAppState.js';
import { createMessage } from './protocol.js';
import { SLASH_COMMANDS, HELP_TEXT } from './commands/index.js';
import { useStreamHandlers } from './hooks/useStreamHandlers.js';
import { useKeyboardInput } from './hooks/useKeyboardInput.js';
// import { useMouseWheel } from './hooks/useMouseWheel.js';
import { setClient } from './hooks/useWebSocket.js';

interface AppProps {
  backendUrl: string;
  wsUrl: string;
  onExit: () => void;
}

/** Find last @ not preceded by a word char (to exclude emails). Returns index or -1. */
function findAtTrigger(value: string): number {
  for (let i = value.length - 1; i >= 0; i--) {
    if (value[i] === '@') {
      if (i > 0 && /\w/.test(value[i - 1])) continue; // email part, skip
      return i;
    }
  }
  return -1;
}

export function App({ backendUrl, wsUrl, onExit }: AppProps) {
  const [input, setInput] = useState('');
  const [showPalette, setShowPalette] = useState(false);
  // @ file reference state
  const [showFilePalette, setShowFilePalette] = useState(false);
  const [fileQuery, setFileQuery] = useState('');
  const [fileList, setFileList] = useState<string[]>([]);
  const fileDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const referencedFilesRef = useRef<string[]>([]);
  const [showHelp, setShowHelp] = useState(false);
  const [helpScroll, setHelpScroll] = useState(0);
  const [showApproval, setShowApproval] = useState<{
    approvalId: string;
    toolName: string;
    payload: string;
  } | null>(null);

  const clientRef = useRef<JwCodeClient | null>(null);
  const { stdout } = useStdout();
  const terminalRows = (stdout as any)?.rows || 24;
  const terminalCols = (stdout as any)?.columns || 80;

  const planMode = useAppSlice(s => s.planMode);
  const connected = useAppSlice(s => s.connected);
  const planWaiting = useAppSlice(s => s.planWaiting);
  const messages = useAppSlice(s => s.messages);
  const currentMessage = useAppSlice(s => s.currentMessage);
  const toolCallsExpanded = useAppSlice(s => s.toolCallsExpanded);

  const wireHandlers = useStreamHandlers(setShowApproval);

  useEffect(() => {
    const client = new JwCodeClient(backendUrl, wsUrl);
    clientRef.current = client;
    setClient(client);
    wireHandlers(client);

    client.connect().then(() => {
      updateAppState(s => ({ ...s, connected: true }));
      fetch(backendUrl + '/api/models')
        .then(r => r.json())
        .then(d => {
          const models = (d as any).data?.models;
          if (models?.length) {
            updateAppState(s => ({ ...s, modelName: models[0].name || '' }));
          }
        })
        .catch(() => {});
    }).catch((err: Error) => {
      updateAppState(s => ({ ...s, statusText: 'Connection failed: ' + err.message }));
    });

    return () => { client.close(); };
  }, [backendUrl, wsUrl, wireHandlers]);

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
      const def = (SLASH_COMMANDS as Record<string, { action: string; needsArg?: boolean } | null>)[cmd];
      if (def === null) {
        setShowHelp(true);
        setHelpScroll(0);
        return;
      }
      const { action, needsArg } = def;
      const cl = clientRef.current;
      switch (action) {
        case '__exit__': onExit(); return;
        case '__confirm_plan':
          updateAppState(prev => {
            if (!prev.planWaiting) return prev;
            cl?.planConfirm();
            return { ...prev, planWaiting: false };
          });
          return;
        case '__cancel_plan': updateAppState(prev => ({ ...prev, planWaiting: false })); return;
        case 'plan_mode': updateAppState(prev => ({ ...prev, planMode: !prev.planMode })); return;
        case 'auto_mode': updateAppState(prev => ({ ...prev, autoMode: !prev.autoMode })); return;
        case 'clear': updateAppState(prev => ({ ...prev, messages: [], currentMessage: null })); return;
        case 'model_change': if (needsArg && cmdArg) cl?.switchModel(cmdArg); return;
        case 'show_context':
          updateAppState(prev => ({
            ...prev,
            statusText: 'Messages: ' + prev.messages.length + ' | Plan: ' + (prev.planMode ? 'On' : 'Off') + ' | Model: ' + (prev.modelName || 'N/A'),
          }));
          return;
        case 'stop': cl?.stop(); return;
        case 'pause': cl?.pause(); return;
        case 'resume': cl?.resume(); return;
        case 'doctor': cl?.doctor(); return;
        case 'rewind': cl?.rewind(); return;
        case 'compact': cl?.compact(); return;
        case 'init': cl?.init(); return;
        case 'effort': if (cmdArg) cl?.effort(cmdArg); return;
        case 'branch': if (cmdArg) cl?.branch(cmdArg); return;
        case 'mcp': if (cmdArg) cl?.mcp(cmdArg); return;
        case 'skills': cl?.skills(); return;
        case 'agents': cl?.agents(); return;
        case 'config': if (cmdArg) cl?.config(cmdArg); return;
        case 'plugin': if (cmdArg) cl?.plugin(cmdArg); return;
        case 'tokens': cl?.send('tokens'); return;
        case 'memory': cl?.send('memory'); return;
        case 'export': if (cmdArg) cl?.send('export', undefined, { path: cmdArg }); return;
        case 'checkpoint': cl?.send('checkpoint'); return;
        case 'test': cl?.send('test'); return;
        case 'lint': cl?.send('lint'); return;
        case 'search': if (cmdArg) cl?.send('search', undefined, { query: cmdArg }); return;
        case 'project': cl?.send('project'); return;
      }
      return;
    }

    if (text.startsWith('/') && !(cmd && cmd in SLASH_COMMANDS)) return;
    // Resolve @ file references
    resolveAndSend(text, planMode);
  }, [onExit, planMode]);

  // Resolve @-referenced file contents and send message
  const resolveAndSend = useCallback(async (text: string, planMode: boolean) => {
    const cl = clientRef.current;
    if (!cl) return;

    let finalText = text;
    const files = referencedFilesRef.current;
    referencedFilesRef.current = [];

    const fileCtxs: string[] = [];
    for (const filePath of files) {
      try {
        const content = await cl.readFileContent(filePath);
        if (content) {
          // Remove the raw path reference from the message
          finalText = finalText.replace(filePath, '').trim();
          const ext = filePath.split('.').pop() || '';
          fileCtxs.push(
            `<context ref="${filePath}">\n\`\`\`${ext}\n${content}\n\`\`\`\n</context>`
          );
        }
      } catch { /* file not readable, leave path in text */ }
    }
    if (fileCtxs.length > 0) {
      finalText = fileCtxs.join('\n\n') + '\n\n' + finalText.trim();
      finalText = finalText.trim();
    }

    saveToHistory(finalText);
    const msg = createMessage('user', finalText);
    updateAppState(prev => ({ ...prev, messages: [...prev.messages, msg] }));
    cl.chat(finalText, planMode);
  }, []);

  const handleSubmit = useCallback((value: string) => {
    if (showPalette || showFilePalette) return;
    executeCommand(value);
  }, [executeCommand, showPalette, showFilePalette]);

  const handleChange = useCallback((value: string) => {
    setInput(value);
    setShowPalette(value.startsWith('/'));

    // @ file reference detection: find last @ not preceded by word char
    const atIdx = findAtTrigger(value);
    if (atIdx >= 0) {
      const query = value.slice(atIdx + 1);
      // Only trigger if query looks like a file path
      if (/^[\w.\-\\\/\s]*$/.test(query) && query.length < 200) {
        setFileQuery(query);
        setShowFilePalette(true);
        // Debounced file fetch
        if (fileDebounceRef.current) clearTimeout(fileDebounceRef.current);
        fileDebounceRef.current = setTimeout(async () => {
          const cl = clientRef.current;
          if (cl) {
            const files = await cl.listFiles(query.trim() || undefined);
            setFileList(files);
          }
        }, 150);
        return;
      }
    }
    // Close file palette when no valid @ trigger
    setShowFilePalette(false);
    setFileQuery('');
  }, []);

  const handleFileSelect = useCallback((path: string | null) => {
    if (path) {
      // Replace @query with file path
      const atIdx = findAtTrigger(input);
      if (atIdx >= 0) {
        const newValue = input.slice(0, atIdx) + path + ' ';
        setInput(newValue);
        referencedFilesRef.current.push(path);
      }
    }
    setShowFilePalette(false);
    setFileQuery('');
    setFileList([]);
  }, [input]);

  const handlePaletteSelect = useCallback((cmd: string | null) => {
    if (cmd) { setInput(cmd); setShowPalette(false); }
    else { setShowPalette(false); setInput(''); }
  }, []);

  const handleApprovalAllow = useCallback((approvalId: string) => {
    clientRef.current?.approveHook(approvalId);
    setShowApproval(null);
  }, []);

  const handleApprovalDeny = useCallback((approvalId: string) => {
    clientRef.current?.denyHook(approvalId);
    setShowApproval(null);
  }, []);

  const isGenerating = currentMessage !== null;
  useKeyboardInput({
    showApproval: showApproval !== null,
    showHelp,
    isGenerating,
    terminalRows,
    clientRef: clientRef as React.MutableRefObject<JwCodeClient | null>,
    onDenyApproval: () => { if (showApproval) handleApprovalDeny(showApproval.approvalId); },
    onCloseHelp: () => setShowHelp(false),
    setHelpScroll,
  });

  const handleApprovalAllowForModal = useCallback(() => {
    if (showApproval) handleApprovalAllow(showApproval.approvalId);
  }, [showApproval, handleApprovalAllow]);

  const handleApprovalDenyForModal = useCallback(() => {
    if (showApproval) handleApprovalDeny(showApproval.approvalId);
  }, [showApproval, handleApprovalDeny]);

  const placeholder = 'Type a message or / for commands...';

  return (
    <Box flexDirection="column" width="100%">
      <Box height={1} paddingLeft={2}>
        <Text color="cyan">JWCode v3.0.0</Text>
        {messages.length === 0 && !currentMessage && <Text dimColor>  --  Type / for commands</Text>}
      </Box>
      <Box flexGrow={1} flexDirection="column">
        <ChatArea
          messages={messages}
          currentMessage={currentMessage}
          terminalCols={terminalCols}
          toolCallsExpanded={toolCallsExpanded}
        />
      </Box>
      <Box flexDirection="row" borderStyle="single" borderColor={connected ? 'cyan' : 'red'} paddingLeft={1}>
        <Text color="green" bold>&gt; </Text>
        <TextInput
          value={input}
          onChange={handleChange}
          onSubmit={handleSubmit}
          placeholder={placeholder}
          disabled={showApproval !== null}
        />
      </Box>
      <Box>
        {showPalette && (
          <CommandPalette filter={input} onSelect={handlePaletteSelect} />
        )}
        {showFilePalette && (
          <FilePalette query={fileQuery} files={fileList} onSelect={handleFileSelect} />
        )}
        {showHelp && (() => {
          const helpLines = HELP_TEXT.split('\\n');
          const helpMax = Math.max(5, Math.min(terminalRows - 12, 10));
          const helpEnd = Math.max(0, helpLines.length - helpScroll);
          const helpStart = Math.max(0, helpEnd - helpMax);
          const visibleHelp = helpLines.slice(helpStart, helpEnd);
          return (
            <Box flexDirection="column" borderStyle="single" borderColor="cyan" paddingX={1}>
              {helpLines.length > helpMax && (
                <Box>
                  <Text dimColor>{'  ' + (helpStart + 1) + '-' + helpEnd + ' / ' + helpLines.length + '  PgUp/PgDn scroll / Esc close'}</Text>
                </Box>
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
            onAllow={handleApprovalAllowForModal}
            onDeny={handleApprovalDenyForModal}
            />
          )}
      </Box>
      <StatusLine />
      <Box height={1}>
        {!connected && (
          <Text color="red">Backend not connected -- WebSocket reconnecting.</Text>
        )}
      </Box>
      <Box height={1}>
        {planWaiting && (
          <Text color="yellow" bold>Plan ready -- /confirm to execute, /cancel to discard.</Text>
        )}
      </Box>
    </Box>
  );
}

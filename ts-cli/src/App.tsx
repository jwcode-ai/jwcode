/**
 * Root Ink component -- layout, WS connection, and event dispatch.
 */
import { useState, useEffect, useRef, useCallback } from 'react';
import { Box, Text, useStdout } from 'ink';
import { TextInput } from './components/TextInput.js';
import { t } from './theme.js';
import { JwCodeClient } from './client.js';
import { StatusLine } from './components/StatusLine.js';
import { ChatAreaContainer } from './components/ChatArea.js';
import { CommandPalette } from './components/CommandPalette.js';
import { FilePalette } from './components/FilePalette.js';
import { ApprovalModal } from './components/ApprovalModal.js';
import { updateAppState, useAppSlice } from './hooks/useAppState.js';
import { getCommandEntry, HELP_TEXT } from './commands/index.js';
import { useStreamHandlers } from './hooks/useStreamHandlers.js';
import { useKeyboardInput } from './hooks/useKeyboardInput.js';
import { setClient } from './hooks/useWebSocket.js';
import { installSyncOutput } from './terminalSync.js';
import { useCommandHandler } from './hooks/useCommandHandler.js';

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

  // Install BSU/ESU (DEC 2026) wrapping on process.stdout once at mount.
  // Idempotent and safe to call on terminals that don't support it.
  useEffect(() => {
    installSyncOutput();
  }, []);
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
  const sessionAllowRef = useRef<Set<string>>(new Set());

  const clientRef = useRef<JwCodeClient | null>(null);
  const { stdout } = useStdout();
  const terminalRows = (stdout as any)?.rows || 24;

  const planMode = useAppSlice(s => s.planMode);
  const planModeRef = useRef(planMode);
  planModeRef.current = planMode;
  const connected = useAppSlice(s => s.connected);

  // Ref holding the latest executeCommand for QueryGuard dequeue to call
  const dequeueRef = useRef<((input: string) => void) | null>(null);
  const planWaiting = useAppSlice(s => s.planWaiting);
  const isGenerating = useAppSlice(s => s.currentMessage !== null);
  const messagesLen = useAppSlice(s => s.messages.length);
  const modelName = useAppSlice(s => s.modelName);

  const wireHandlers = useStreamHandlers(setShowApproval, sessionAllowRef, dequeueRef);

  useEffect(() => {
    const client = new JwCodeClient(backendUrl, wsUrl);
    clientRef.current = client;
    setClient(client);
    wireHandlers(client);

    client.connect().then(async () => {
      try {
        const r = await fetch(backendUrl + '/api/models');
        const d = await r.json();
        const models = (d as any).data?.models;
        const name = models?.[0]?.name || '';
        updateAppState(s => ({ ...s, connected: true, modelName: name }));
      } catch {
        updateAppState(s => ({ ...s, connected: true }));
      }
    }).catch((err: Error) => {
      updateAppState(s => ({ ...s, statusText: 'Connection failed: ' + err.message }));
    });

    return () => { client.close(); };
  }, [backendUrl, wsUrl, wireHandlers]);

  const { executeCommand } = useCommandHandler({
    clientRef,
    planModeRef,
    referencedFilesRef,
    onExit,
    setInput,
    setShowHelp,
    setShowPalette,
  });
  dequeueRef.current = executeCommand;

  const handleSubmit = useCallback((value: string) => {
    if (showPalette || showFilePalette) return;
    void executeCommand(value);
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
    if (cmd) {
      const entry = getCommandEntry(cmd);
      setInput(entry?.needsArg ? `${cmd} ` : cmd);
      setShowPalette(false);
    }
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

  const paletteActive = showPalette || showFilePalette;
  useKeyboardInput({
    showApproval: showApproval !== null,
    showHelp,
    isGenerating,
    terminalRows,
    paletteOpen: paletteActive,
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

  const handleAllowSession = useCallback(() => {
    if (showApproval) {
      sessionAllowRef.current.add(showApproval.toolName);
      handleApprovalAllow(showApproval.approvalId);
    }
  }, [showApproval, handleApprovalAllow]);

  const handleAutoMode = useCallback(() => {
    updateAppState(prev => ({ ...prev, autoMode: true }));
    if (showApproval) handleApprovalAllow(showApproval.approvalId);
  }, [showApproval, handleApprovalAllow]);

  const placeholder = 'Type a message or / for commands...';

  return (
    <Box flexDirection="column" width="100%">
      {connected ? (
        <Box flexDirection="column">
          {messagesLen === 0 && !isGenerating && !!modelName && (
            <Box key="welcome-banner" flexDirection="column" borderStyle="round" borderColor={t.primary} paddingX={1} marginBottom={1}>
              <Box>
                <Text color={t.primary} bold>&gt;_ JWCode v3.0.0</Text>
              </Box>
              <Box>
                <Text dimColor>model:     </Text>
                <Text color={t.success}>{modelName || 'connecting...'}</Text>
                <Text dimColor>   /model to change</Text>
              </Box>
              <Box>
                <Text dimColor>directory: </Text>
                <Text color={t.warning}>{process.cwd()}</Text>
              </Box>
            </Box>
          )}
          {messagesLen === 0 && !isGenerating && !!modelName && (
            <Box key="tip-line" paddingLeft={3} marginBottom={1}>
              <Text dimColor>Tip: Type / for commands, @ to reference files, ↑↓ for history</Text>
            </Box>
          )}
          {/* flexGrow=0 when palette open: prevents Yoga layout overflow + Ink ghost content duplication */}
          <Box flexGrow={paletteActive ? 0 : 1} flexDirection="column">
            <ChatAreaContainer />
          </Box>
          {/* Help box — rendered above input so text appears between messages and input */}
          {showHelp && (() => {
            const helpLines = HELP_TEXT.split('\\n');
            const helpMax = Math.max(5, Math.min(terminalRows - 12, 10));
            const helpEnd = Math.max(0, helpLines.length - helpScroll);
            const helpStart = Math.max(0, helpEnd - helpMax);
            const visibleHelp = helpLines.slice(helpStart, helpEnd);
            return (
              <Box key="help-box" flexDirection="column" borderStyle="single" borderColor={t.primary} paddingX={1}>
                {helpLines.length > helpMax && (
                  <Box>
                    <Text dimColor>{'  ' + (helpStart + 1) + '-' + helpEnd + ' / ' + helpLines.length + '  PgUp/PgDn scroll / Esc close'}</Text>
                  </Box>
                )}
                {visibleHelp.map((line, i) => (
                  <Text key={i} color={t.primary}>{line}</Text>
                ))}
              </Box>
            );
          })()}
          <Box flexDirection="row" borderStyle="single" borderColor={t.primary} paddingLeft={1}>
            <Text color={t.success} bold>&gt; </Text>
            <TextInput
              value={input}
              onChange={handleChange}
              onSubmit={handleSubmit}
              placeholder={placeholder}
              disabled={showApproval !== null}
              isPaletteActive={paletteActive}
            />
          </Box>
          {/* flexDirection=column required: Ink 5 defaults to row, which miscalculates overlay heights */}
          <Box flexDirection="column">
            {showPalette && (
              <CommandPalette key="command-palette" filter={input} onSelect={handlePaletteSelect} />
            )}
            {showFilePalette && (
              <FilePalette key="file-palette" query={fileQuery} files={fileList} onSelect={handleFileSelect} />
            )}
            {showApproval && (
              <ApprovalModal
                key="approval-modal"
                toolName={showApproval.toolName}
                payload={showApproval.payload}
                onAllow={handleApprovalAllowForModal}
                onDeny={handleApprovalDenyForModal}
                onAllowSession={handleAllowSession}
                onAutoMode={handleAutoMode}
                />
              )}
          </Box>
        </Box>
      ) : (
        <Box flexDirection="column">
          <Box flexGrow={1} flexDirection="column">
            <ChatAreaContainer />
          </Box>
          <Box paddingLeft={1}>
            <Text dimColor>Connecting...</Text>
          </Box>
        </Box>
      )}
      <StatusLine />
      <Box height={1}>
        {!connected && (
          <Text color={t.error}>Backend not connected -- WebSocket reconnecting.</Text>
        )}
      </Box>
      <Box height={1}>
        {planWaiting && (
          <Text color={t.warning} bold>Plan ready -- /confirm to execute, /cancel to discard.</Text>
        )}
      </Box>
    </Box>
  );
}

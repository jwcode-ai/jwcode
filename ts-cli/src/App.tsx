/**
 * Root Ink component -- layout, WS connection, and event dispatch.
 *
 * Performance optimizations:
 * 1. Input state (text input, palettes) isolated from global state to prevent
 *    re-rendering the entire tree on every keystroke.
 * 2. Heavy sub-trees (ChatArea, StatusLine) receive data via selectors only
 *    when their specific slice changes.
 * 3. Palette overlays are conditionally rendered with stable keys.
 */
import { useState, useEffect, useRef, useCallback, memo } from 'react';
import { Box, Text, useStdout } from 'ink';
import { TextInput, saveToHistory } from './components/TextInput.js';
import { t } from './theme.js';
import { JwCodeClient } from './client.js';
import { StatusLine } from './components/StatusLine.js';
import { ChatAreaContainer } from './components/ChatArea.js';
import { CommandPalette } from './components/CommandPalette.js';
import { FilePalette } from './components/FilePalette.js';
import { ApprovalModal } from './components/ApprovalModal.js';
import { updateAppState, useAppConnected, useAppCurrentMessage, useAppModelName, useAppMessages } from './hooks/useAppState.js';
import { setClient, getClient } from './hooks/useWebSocket.js';
import { useKeyboardInput } from './hooks/useKeyboardInput.js';
import { PlanTaskBoard } from './components/PlanTaskBoard.js';
import type { PlanTask } from './protocol.js';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------
const HELP_TEXT = `JWCode CLI — Help
${'-'.repeat(40)}
Commands:
  /help          Show this help
  /model         Switch AI model
  /plan          Toggle plan mode
  /auto          Toggle auto mode
  /clear         Clear chat
  /exit          Exit
  /reset         Reset session

Shortcuts:
  Ctrl+C         Cancel generation
  Ctrl+D         Exit
  Ctrl+L         Clear screen
  Tab            Insert file reference (@)
  ↑↓             Input history
  Esc            Close palette / modal

Tips:
  @filename      Reference a file in the workspace
  /cmd           Quick command access
`;

// ---------------------------------------------------------------------------
// findAtTrigger helper
// ---------------------------------------------------------------------------
function findAtTrigger(input: string): number {
  for (let i = input.length - 1; i >= 0; i--) {
    const ch = input[i];
    if (ch === '@') {
      // Must not be preceded by a word char
      if (i === 0 || !/[a-zA-Z0-9_\u4e00-\u9fff]/.test(input[i - 1])) {
        return i;
      }
    }
    if (/[\s\n]/.test(ch)) break;
  }
  return -1;
}

// ---------------------------------------------------------------------------
// InputBar — isolated component to prevent App re-render on keystroke
// ---------------------------------------------------------------------------
interface InputBarProps {
  onSubmit: (value: string) => void;
  disabled: boolean;
  isPaletteActive: boolean;
  placeholder: string;
}

const InputBar = memo(function InputBar({ onSubmit, disabled, isPaletteActive, placeholder }: InputBarProps) {
  const [input, setInput] = useState('');
  const [showPalette, setShowPalette] = useState(false);
  const [showFilePalette, setShowFilePalette] = useState(false);
  const [fileQuery, setFileQuery] = useState('');
  const [fileList, setFileList] = useState<string[]>([]);
  const fileDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const referencedFilesRef = useRef<string[]>([]);

  const handleChange = useCallback((value: string) => {
    setInput(value);
    setShowPalette(value.startsWith('/'));

    // @ file reference detection
    const atIdx = findAtTrigger(value);
    if (atIdx >= 0) {
      const query = value.slice(atIdx + 1);
      if (/^[\w.\-\\\/\s]*$/.test(query) && query.length < 200) {
        setFileQuery(query);
        setShowFilePalette(true);
        if (fileDebounceRef.current) clearTimeout(fileDebounceRef.current);
        fileDebounceRef.current = setTimeout(() => {
          const client = getClient();
          if (client) {
            client.listFiles(query.trim() || undefined).then((files: string[]) => {
              setFileList(files);
            }).catch(() => {});
          }
        }, 150);
      }
    } else {
      setShowFilePalette(false);
      setFileQuery('');
      referencedFilesRef.current = [];
    }
  }, []);

  const handleSubmit = useCallback((value: string) => {
    if (!value.trim()) return;
    saveToHistory(value);
    setInput('');
    setShowPalette(false);
    setShowFilePalette(false);
    // Build @ file references
    const files = referencedFilesRef.current;
    let refStr = '';
    for (const filePath of files) {
      refStr += ' @' + filePath;
    }
    onSubmit(value + refStr);
  }, [onSubmit]);

  const handlePaletteSelect = useCallback((cmd: string | null) => {
    if (cmd) {
      setInput(cmd + ' ');
      setShowPalette(false);
    } else {
      setShowPalette(false);
    }
  }, []);

  const handleFileSelect = useCallback((path: string | null) => {
    if (path) {
      const atIdx = findAtTrigger(input);
      if (atIdx >= 0) {
        const before = input.slice(0, atIdx);
        const newInput = before + path + ' ';
        setInput(newInput);
        referencedFilesRef.current.push(path);
      }
    }
    setShowFilePalette(false);
  }, [input]);

  const paletteActive = showPalette || showFilePalette;

  return (
    <Box flexDirection="column">
      {/* Input line */}
      <Box flexDirection="row" borderStyle="single" borderColor={t.primary} paddingLeft={1}>
        <Text color={t.success} bold>&gt; </Text>
        <TextInput
          value={input}
          onChange={handleChange}
          onSubmit={handleSubmit}
          placeholder={placeholder}
          disabled={disabled}
          isPaletteActive={paletteActive}
        />
      </Box>
      {/* Overlays */}
      <Box flexDirection="column">
        {showPalette && (
          <CommandPalette key="command-palette" filter={input} onSelect={handlePaletteSelect} />
        )}
        {showFilePalette && (
          <FilePalette key="file-palette" query={fileQuery} files={fileList} onSelect={handleFileSelect} />
        )}
      </Box>
    </Box>
  );
});

// ---------------------------------------------------------------------------
// ApprovalBar — isolated approval modal
// ---------------------------------------------------------------------------
interface ApprovalBarProps {
  approval: {
    approvalId: string;
    toolName: string;
    payload: string;
    onAllow: () => void;
    onDeny: () => void;
    onAllowSession: () => void;
    onAutoMode: () => void;
  } | null;
}

const ApprovalBar = memo(function ApprovalBar({ approval }: ApprovalBarProps) {
  if (!approval) return null;
  return (
    <ApprovalModal
      key="approval-modal"
      toolName={approval.toolName}
      payload={approval.payload}
      onAllow={approval.onAllow}
      onDeny={approval.onDeny}
      onAllowSession={approval.onAllowSession}
      onAutoMode={approval.onAutoMode}
    />
  );
});

// ---------------------------------------------------------------------------
// HelpPanel — isolated help view
// ---------------------------------------------------------------------------
interface HelpPanelProps {
  visible: boolean;
  terminalRows: number;
  onClose: () => void;
  scroll: number;
  onScroll: (s: number) => void;
}

const HelpPanel = memo(function HelpPanel({ visible, terminalRows, onClose, scroll, onScroll }: HelpPanelProps) {
  if (!visible) return null;

  const helpLines = HELP_TEXT.split('\n');
  const helpMax = Math.max(10, terminalRows - 6);
  const helpEnd = Math.min(scroll + helpMax, helpLines.length);
  const helpStart = Math.max(0, helpEnd - helpMax);
  const visibleHelp = helpLines.slice(helpStart, helpEnd);

  return (
    <Box key="help-box" flexDirection="column" borderStyle="single" borderColor={t.primary} paddingX={1}>
      {helpLines.length > helpMax && (
        <Box>
          <Text dimColor>
            {'  ' + (helpStart + 1) + '-' + helpEnd + ' / ' + helpLines.length + '  PgUp/PgDn scroll / Esc close'}
          </Text>
        </Box>
      )}
      {visibleHelp.map((line, i) => (
        <Text key={i} color={t.primary}>{line}</Text>
      ))}
    </Box>
  );
});

// ---------------------------------------------------------------------------
// WelcomeBanner
// ---------------------------------------------------------------------------
const WelcomeBanner = memo(function WelcomeBanner({ modelName }: { modelName: string }) {
  if (!modelName) return null;
  return (
    <Box key="welcome-banner" flexDirection="column" borderStyle="round" borderColor={t.primary} paddingX={1} marginBottom={1}>
      <Box>
        <Text color={t.primary} bold>&gt;_ JWCode v3.0.0</Text>
      </Box>
      <Box>
        <Text dimColor>model:     </Text>
        <Text color={t.success}>{modelName}</Text>
        <Text dimColor>   /model to change</Text>
      </Box>
      <Box>
        <Text dimColor>directory: </Text>
        <Text color={t.warning}>{process.cwd()}</Text>
      </Box>
    </Box>
  );
});

// ---------------------------------------------------------------------------
// App (Root Component)
// ---------------------------------------------------------------------------
interface AppProps {
  backendUrl?: string;
  wsUrl?: string;
  onExit: () => void;
}

export function App({ backendUrl, wsUrl, onExit }: AppProps) {
  // --- Client ref ---
  const clientRef = useRef<JwCodeClient | null>(null);
  const sessionAllowRef = useRef<Set<string>>(new Set());

  // --- Only global states that affect the root layout ---
  const [showHelp, setShowHelp] = useState(false);
  const [helpScroll, setHelpScroll] = useState(0);
  const [showApproval, setShowApproval] = useState<{
    approvalId: string;
    toolName: string;
    payload: string;
  } | null>(null);
  const [planTasks, setPlanTasks] = useState<PlanTask[]>([]);

  // --- Global state (via selectors — only re-render when these change) ---
  const messages = useAppMessages();
  const currentMessage = useAppCurrentMessage();
  const connected = useAppConnected();
  const modelName = useAppModelName();

  // --- Terminal dimensions ---
  const { stdout } = useStdout();
  const terminalCols = (stdout as unknown as { columns: number })?.columns ?? 80;
  const terminalRows = (stdout as unknown as { rows: number })?.rows ?? 24;

  // --- WebSocket connection ---
  useEffect(() => {
    const client = new JwCodeClient(wsUrl || 'ws://localhost:8080/ws');
    clientRef.current = client;
    setClient(client);

    // Register handlers
    client.on('message', (msg) => {
      import('./hooks/useStreamHandlers.js').then(({ handleStreamMessage }) => {
        handleStreamMessage(msg, client, {
          onApproval: (approvalId, toolName, payload) => {
            setShowApproval({ approvalId, toolName, payload });
          },
          onPlanTasks: (tasks) => {
            setPlanTasks(tasks);
          },
        });
      });
    });

    client.on('connected', () => {
      updateAppState((prev) => ({ ...prev, connected: true }));
    });

    client.on('disconnected', () => {
      updateAppState((prev) => ({ ...prev, connected: false }));
    });

    client.connect();

    return () => {
      client.disconnect();
      clientRef.current = null;
    };
  }, [wsUrl]);

  // --- Command execution ---
  const executeCommand = useCallback((value: string) => {
    const client = clientRef.current;
    if (!client) return;

    updateAppState((prev) => ({ ...prev, statusText: 'generating...' }));

    client.send({
      type: 'message',
      data: { role: 'user', content: value },
    });
  }, []);

  // --- Approval handlers ---
  const handleApprovalAllow = useCallback((approvalId: string) => {
    clientRef.current?.approveHook(approvalId);
    setShowApproval(null);
  }, []);

  const handleApprovalDeny = useCallback((approvalId: string) => {
    clientRef.current?.denyHook(approvalId);
    setShowApproval(null);
  }, []);

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
    if (showApproval) {
      updateAppState((prev) => ({ ...prev, autoMode: true }));
      handleApprovalAllow(showApproval.approvalId);
    }
  }, [showApproval, handleApprovalAllow]);

  // --- Keyboard input ---
  const isGenerating = currentMessage !== null;
  const paletteActive = false; // palette managed inside InputBar

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

  const messagesLen = messages.length;

  // Build approval modal data
  const approvalData = showApproval ? {
    approvalId: showApproval.approvalId,
    toolName: showApproval.toolName,
    payload: showApproval.payload,
    onAllow: handleApprovalAllowForModal,
    onDeny: handleApprovalDenyForModal,
    onAllowSession: handleAllowSession,
    onAutoMode: handleAutoMode,
  } : null;

  return (
    <Box flexDirection="column" height="100%">
      {/* Status line — top bar */}
      <StatusLine />

      {/* Main content area — scrollable */}
      <Box flexGrow={1} flexDirection="column" overflow="hidden">
        {/* Welcome banner */}
        {messagesLen === 0 && !isGenerating && !!modelName && (
          <WelcomeBanner modelName={modelName} />
        )}

        {/* Tip line */}
        {messagesLen === 0 && !isGenerating && !!modelName && (
          <Box key="tip-line" paddingLeft={3} marginBottom={1}>
            <Text dimColor>Tip: Type / for commands, @ to reference files, ↑↓ for history</Text>
          </Box>
        )}

        {/* Chat area */}
        <Box flexGrow={1} flexDirection="column">
          <ChatAreaContainer />
        </Box>

        {/* Plan task board */}
        {planTasks.length > 0 && (
          <PlanTaskBoard tasks={planTasks} terminalCols={terminalCols} />
        )}
      </Box>

      {/* Help panel overlay */}
      <HelpPanel
        visible={showHelp}
        terminalRows={terminalRows}
        onClose={() => setShowHelp(false)}
        scroll={helpScroll}
        onScroll={setHelpScroll}
      />

      {/* Input bar — isolated, won't re-render App on keystroke */}
      <InputBar
        onSubmit={executeCommand}
        disabled={showApproval !== null}
        isPaletteActive={false}
        placeholder={isGenerating ? 'Generating...' : connected ? 'Type your message...' : 'Connecting...'}
      />

      {/* Approval modal overlay */}
      <ApprovalBar approval={approvalData} />
    </Box>
  );
}

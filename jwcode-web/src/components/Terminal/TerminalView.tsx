import { useEffect, useRef, useState, useCallback } from 'react';
import { Terminal as TerminalIcon, Trash2, Copy, Download, Settings, RefreshCw } from 'lucide-react';
import { Terminal } from 'xterm';
import { FitAddon } from 'xterm-addon-fit';
import { AttachAddon } from 'xterm-addon-attach';
import { useTerminalStore } from '../../stores/terminalStore';
import { useSettingsStore } from '../../stores/settingsStore';
import { apiClient } from '../../services/api/client';
import type { TerminalStartResponse } from '../../types';
import './xterm.css';

// VS Code-inspired terminal theme
const TERM_THEME = {
  background: '#1e1e1e',
  foreground: '#d4d4d4',
  cursor: '#d4d4d4',
  cursorAccent: '#1e1e1e',
  black: '#1e1e1e',
  red: '#f44747',
  green: '#608b4e',
  yellow: '#dcdcaa',
  blue: '#569cd6',
  magenta: '#c586c0',
  cyan: '#4ec9b0',
  white: '#d4d4d4',
  brightBlack: '#808080',
  brightRed: '#f44747',
  brightGreen: '#608b4e',
  brightYellow: '#dcdcaa',
  brightBlue: '#569cd6',
  brightMagenta: '#c586c0',
  brightCyan: '#4ec9b0',
  brightWhite: '#ffffff',
};

export function TerminalView() {
  const terminalRef = useRef<HTMLDivElement>(null);
  const xtermRef = useRef<Terminal | null>(null);
  const fitAddonRef = useRef<FitAddon | null>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [ttydAvailable, setTtydAvailable] = useState<boolean | null>(null); // null = checking

  const { status, errorMessage } = useTerminalStore();
  const { setStarting, setRunning, setStopping, setIdle, setError } = useTerminalStore();
  const { workspaceDir } = useSettingsStore();

  // Check ttyd availability
  useEffect(() => {
    apiClient.get<{ ttydAvailable?: boolean }>('/api/terminal/status')
      .then(res => setTtydAvailable(res.success && res.data ? res.data.ttydAvailable !== false : true))
      .catch(() => setTtydAvailable(false));
  }, []);

  const cleanup = useCallback(() => {
    if (wsRef.current) {
      wsRef.current.onclose = null;
      wsRef.current.close();
      wsRef.current = null;
    }
  }, []);

  const startSession = useCallback(async (term: Terminal) => {
    setStarting();
    term.writeln('\x1b[90mStarting terminal session...\x1b[0m');

    try {
      const response = await apiClient.post<TerminalStartResponse>(
        '/api/terminal/start',
        { workspaceDir }
      );

      if (!response.success || !response.data) {
        const msg = response.error || 'Failed to start terminal';
        setError(msg);
        term.writeln(`\x1b[31mError: ${msg}\x1b[0m`);
        return;
      }

      const { wsUrl } = response.data;
      setRunning(response.data.ttydPort, wsUrl);

      const ws = new WebSocket(wsUrl);
      wsRef.current = ws;

      ws.onopen = () => {
        term.clear();
        term.writeln('\x1b[32mTerminal connected\x1b[0m\n');

        const attachAddon = new AttachAddon(ws);
        term.loadAddon(attachAddon);

        term.onResize(({ cols, rows }) => {
          if (ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({ cols, rows }));
          }
        });

        setTimeout(() => fitAddonRef.current?.fit(), 100);
      };

      ws.onerror = () => {
        setError('WebSocket connection error');
        term.writeln('\x1b[31mConnection error\x1b[0m');
      };

      ws.onclose = () => {
        if (wsRef.current === ws) {
          wsRef.current = null;
          setError('Terminal disconnected');
          term.writeln('\x1b[33mTerminal disconnected — press Restart to reconnect\x1b[0m');
        }
      };
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Unknown error';
      setError(msg);
      term.writeln(`\x1b[31mError: ${msg}\x1b[0m`);
    }
  }, [workspaceDir, setStarting, setRunning, setError]);

  const stopSession = useCallback(async () => {
    cleanup();
    setStopping();
    try {
      await apiClient.post('/api/terminal/stop');
    } catch { /* best-effort */ }
    setIdle();
  }, [cleanup, setStopping, setIdle]);

  // Initialize xterm + start session on mount
  useEffect(() => {
    if (!terminalRef.current) return;

    const term = new Terminal({
      theme: TERM_THEME,
      fontFamily: '"Cascadia Code", "Fira Code", Consolas, "Courier New", monospace',
      fontSize: 14,
      lineHeight: 1.2,
      cursorBlink: true,
      cursorStyle: 'block',
      scrollback: 10000,
      convertEol: true,
      allowProposedApi: true,
    });

    const fitAddon = new FitAddon();
    term.loadAddon(fitAddon);
    term.open(terminalRef.current);
    fitAddon.fit();

    xtermRef.current = term;
    fitAddonRef.current = fitAddon;

    term.writeln('\x1b[36m══ JWCode Terminal ══\x1b[0m');

    const handleResize = () => fitAddonRef.current?.fit();
    window.addEventListener('resize', handleResize);

    // Wait a tick for xterm to fully initialize, then start
    const timer = setTimeout(() => startSession(term), 50);

    return () => {
      clearTimeout(timer);
      window.removeEventListener('resize', handleResize);
      stopSession();
      term.dispose();
      xtermRef.current = null;
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handleClear = () => {
    if (xtermRef.current) {
      xtermRef.current.clear();
      xtermRef.current.writeln('\x1b[90m--- Terminal cleared ---\x1b[0m');
    }
  };

  const handleCopy = () => {
    if (xtermRef.current) {
      const selection = xtermRef.current.getSelection();
      if (selection) {
        navigator.clipboard.writeText(selection);
      }
    }
  };

  const handleDownload = () => {
    // xterm doesn't expose full buffer easily, so download what we can
    const content = xtermRef.current
      ? 'Terminal log export — use Copy to save specific content'
      : 'No content';
    const blob = new Blob([content], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `terminal-${Date.now()}.log`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleRestart = () => {
    if (wsRef.current) {
      wsRef.current.onclose = null;
      wsRef.current.close();
      wsRef.current = null;
    }
    if (xtermRef.current) {
      xtermRef.current.clear();
      startSession(xtermRef.current);
    }
  };

  const statusLabel = {
    idle: '',
    starting: 'Connecting...',
    running: 'Connected',
    stopping: 'Stopping...',
    error: 'Error',
  }[status];

  const statusColor = {
    idle: 'bg-dark-border',
    starting: 'bg-accent-yellow',
    running: 'bg-accent-green',
    stopping: 'bg-accent-yellow',
    error: 'bg-accent-red',
  }[status];

  return (
    <div
      className={`flex flex-col bg-[#1e1e1e] border-t border-dark-border ${
        isFullscreen ? 'fixed inset-0 z-50' : 'flex-1 min-h-0'
      }`}
    >
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-1.5 bg-[#252526] border-b border-[#3c3c3c]">
        <div className="flex items-center gap-2">
          <TerminalIcon size={14} className="text-dark-muted" />
          <span className="text-xs text-dark-muted">Terminal</span>
          {status !== 'idle' && (
            <>
              <span className={`w-2 h-2 rounded-full ${statusColor} ${status === 'running' ? 'animate-pulse' : ''}`} />
              <span className="text-xs text-dark-muted">{statusLabel}</span>
            </>
          )}
          {errorMessage && (
            <span className="text-xs text-accent-red truncate max-w-[240px]">{errorMessage}</span>
          )}
        </div>
        <div className="flex items-center gap-1">
          {status === 'error' && (
            <button
              onClick={handleRestart}
              className="p-1 rounded hover:bg-[#3c3c3c] text-dark-muted hover:text-dark-text"
              title="Restart"
            >
              <RefreshCw size={12} />
            </button>
          )}
          <button
            onClick={handleCopy}
            className="p-1 rounded hover:bg-[#3c3c3c] text-dark-muted hover:text-dark-text"
            title="Copy"
          >
            <Copy size={12} />
          </button>
          <button
            onClick={handleDownload}
            className="p-1 rounded hover:bg-[#3c3c3c] text-dark-muted hover:text-dark-text"
            title="Download log"
          >
            <Download size={12} />
          </button>
          <button
            onClick={handleClear}
            className="p-1 rounded hover:bg-[#3c3c3c] text-dark-muted hover:text-dark-text"
            title="Clear"
          >
            <Trash2 size={12} />
          </button>
          <button
            onClick={() => setIsFullscreen(!isFullscreen)}
            className="p-1 rounded hover:bg-[#3c3c3c] text-dark-muted hover:text-dark-text"
            title={isFullscreen ? 'Exit fullscreen' : 'Fullscreen'}
          >
            <Settings size={12} />
          </button>
        </div>
      </div>

      {/* Terminal content */}
      {ttydAvailable === null ? (
        <div className="flex-1 flex items-center justify-center bg-[#1e1e1e]">
          <span className="text-dark-muted">Checking terminal availability...</span>
        </div>
      ) : ttydAvailable === false ? (
        <div className="flex-1 flex flex-col items-center justify-center bg-[#1e1e1e] gap-4 p-8">
          <TerminalIcon size={48} className="text-dark-muted opacity-30" />
          <div className="text-center space-y-2">
            <p className="text-dark-muted text-lg">Terminal unavailable</p>
            <p className="text-dark-muted text-sm max-w-md">
              ttyd is not installed. Install it to enable the web terminal.
            </p>
            <a
              href="https://github.com/tsl0922/ttyd/releases"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-block mt-2 px-4 py-2 bg-accent-blue text-white rounded-lg text-sm hover:opacity-90 transition-opacity"
            >
              Download ttyd →
            </a>
            <p className="text-dark-muted text-xs mt-4">
              After installing, add ttyd to your PATH and restart JwCode.
            </p>
          </div>
        </div>
      ) : (
        <div
          ref={terminalRef}
          className="flex-1 overflow-hidden"
        />
      )}
    </div>
  );
}

export default TerminalView;

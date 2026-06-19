import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Terminal as TerminalIcon, Trash2, Copy, Download, ArrowDownToLine, RefreshCw, Maximize2, Minimize2 } from 'lucide-react';
import { Terminal } from 'xterm';
import { FitAddon } from 'xterm-addon-fit';
import { useTerminalStore } from '../../stores/terminalStore';
import { useSettingsStore } from '../../stores/settingsStore';
import { apiClient } from '../../services/api/client';
import { toast } from '../../stores/toastStore';
import type { TerminalStartResponse, TerminalStatusResponse } from '../../types';
import 'xterm/css/xterm.css';
import './xterm.css';

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
  const [isAvailable, setIsAvailable] = useState<boolean | null>(null);
  const [autoFollow, setAutoFollow] = useState(true);
  const autoFollowRef = useRef(true);
  const [workspaceLabel, setWorkspaceLabel] = useState('');

  /** 鎶?xterm 褰撳墠灏哄鍙戠粰鍚庣 PTY锛岃 shell 鐭ラ亾缁堢澶у皬 */
  const sendResize = useCallback(() => {
    const term = xtermRef.current;
    const ws = wsRef.current;
    if (!term || !ws || ws.readyState !== WebSocket.OPEN) return;
    ws.send(JSON.stringify({ type: 'resize', cols: term.cols, rows: term.rows }));
  }, []);

  const { status, errorMessage, port } = useTerminalStore();
  const { setStarting, setRunning, setStopping, setIdle, setError } = useTerminalStore();
  const { workspaceDir } = useSettingsStore();

  const cleanupWs = useCallback(() => {
    if (wsRef.current) {
      wsRef.current.onclose = null;
      wsRef.current.onmessage = null;
      wsRef.current.onerror = null;
      wsRef.current.onopen = null;
      wsRef.current.close();
      wsRef.current = null;
    }
  }, []);

  const startSession = useCallback(async (term: Terminal) => {
    setStarting();
    term.writeln('\x1b[90mStarting local terminal...\x1b[0m');

    try {
      const response = await apiClient.post<TerminalStartResponse>('/api/terminal/start', { workspaceDir });
      if (!response.success || !response.data) {
        const msg = response.error || 'Failed to start terminal';
        setError(msg);
        term.writeln(`\x1b[31m${msg}\x1b[0m`);
        return;
      }

      const nextPort = response.data.port ?? response.data.ttydPort;
      const nextWsUrl = response.data.wsUrl;
      if (!nextPort || !nextWsUrl) {
        const msg = 'Invalid terminal start response';
        setError(msg);
        term.writeln(`\x1b[31m${msg}\x1b[0m`);
        return;
      }
      setRunning(nextPort, nextWsUrl);
      setWorkspaceLabel(response.data.workspaceDir || workspaceDir);

      const ws = new WebSocket(nextWsUrl);
      wsRef.current = ws;

      ws.onopen = () => {
        term.clear();
        term.focus();
        term.writeln('\x1b[32mConnected to local shell\x1b[0m');
        setTimeout(() => {
          fitAddonRef.current?.fit();
          sendResize();
          if (autoFollowRef.current) xtermRef.current?.scrollToBottom();
        }, 50);
      };

      ws.onmessage = (event) => {
        if (typeof event.data === 'string') {
          term.write(event.data, () => {
            if (autoFollowRef.current) term.scrollToBottom();
          });
        }
      };

      ws.onerror = () => {
        setError('Terminal connection error');
      };

      ws.onclose = () => {
        if (wsRef.current === ws) {
          wsRef.current = null;
          setError('Terminal disconnected');
          term.writeln('\x1b[33mTerminal disconnected\x1b[0m');
        }
      };
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Unknown error';
      setError(msg);
      term.writeln(`\x1b[31m${msg}\x1b[0m`);
    }
  }, [workspaceDir, setStarting, setRunning, setError, sendResize]);

  const stopSession = useCallback(async () => {
    cleanupWs();
    setStopping();
    try {
      await apiClient.post('/api/terminal/stop');
    } catch {
      // best effort
    }
    setIdle();
  }, [cleanupWs, setStopping, setIdle]);

  useEffect(() => {
    if (!terminalRef.current) return;

    apiClient.get<TerminalStatusResponse>('/api/terminal/status')
      .then((res) => {
        const data = res.success && res.data ? res.data : null;
        setIsAvailable(data?.terminalAvailable !== false);
        if (data?.workspaceDir) {
          setWorkspaceLabel(data.workspaceDir);
        }
      })
      .catch(() => setIsAvailable(false));
  }, []);

  useEffect(() => {
    if (isAvailable !== true || !terminalRef.current) return;

    const term = new Terminal({
      theme: TERM_THEME,
      fontFamily: '"Cascadia Code", "Fira Code", Consolas, "Courier New", monospace',
      fontSize: 14,
      lineHeight: 1.2,
      cursorBlink: true,
      cursorStyle: 'block',
      scrollback: 10000,
      convertEol: true,
    });

    const fitAddon = new FitAddon();
    term.loadAddon(fitAddon);
    term.open(terminalRef.current);
    term.focus();
    fitAddon.fit();

    xtermRef.current = term;
    fitAddonRef.current = fitAddon;

    term.writeln('\x1b[36mJWCode Local Terminal\x1b[0m');
    term.writeln('\x1b[90mRun commands directly on this machine.\x1b[0m');
    const dataDisposable = term.onData((data) => {
      if (wsRef.current?.readyState === WebSocket.OPEN) {
        wsRef.current.send(data);
      }
    });

    // 鐢ㄦ埛鎵嬪姩婊氬姩锛氱寮€搴曢儴鑷姩鍏抽棴璺熼殢锛屽洖鍒板簳閮ㄨ嚜鍔ㄦ仮澶?    
    const scrollDisposable = term.onScroll(() => {
      const buf = term.buffer.active;
      const atBottom = buf.baseY + term.rows >= buf.length;
      if (atBottom !== autoFollowRef.current) {
        autoFollowRef.current = atBottom;
        setAutoFollow(atBottom);
      }
    });

    const handleResize = () => {
      fitAddonRef.current?.fit();
      sendResize();
      if (autoFollowRef.current) term.scrollToBottom();
    };
    window.addEventListener('resize', handleResize);

    const timer = window.setTimeout(() => startSession(term), 50);

    return () => {
      clearTimeout(timer);
      window.removeEventListener('resize', handleResize);
      dataDisposable.dispose();
      scrollDisposable.dispose();
      stopSession();
      term.dispose();
      xtermRef.current = null;
    };
  }, [isAvailable, startSession, stopSession, sendResize]);

  const handleCopy = useCallback(() => {
    const selection = xtermRef.current?.getSelection();
    if (selection) {
      navigator.clipboard.writeText(selection);
      toast.success('Copied selection');
    }
  }, []);

  const handleClear = useCallback(() => {
    xtermRef.current?.clear();
    if (autoFollowRef.current) xtermRef.current?.scrollToBottom();
  }, []);

  const handleRestart = useCallback(() => {
    cleanupWs();
    if (xtermRef.current) {
      xtermRef.current.clear();
      startSession(xtermRef.current);
    }
  }, [cleanupWs, startSession]);

  // 鎵嬪姩鍒囨崲璺熼殢锛氬紑鍚椂绔嬪嵆婊氬埌搴?
  const toggleAutoFollow = useCallback(() => {
    setAutoFollow((prev) => {
      const next = !prev;
      autoFollowRef.current = next;
      if (next) xtermRef.current?.scrollToBottom();
      return next;
    });
  }, []);

  const handleDownloadLog = useCallback(() => {
    const text = `Workspace: ${workspaceLabel || workspaceDir}\nTerminal session: ${port ?? ''}\n`;
    const blob = new Blob([text], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `terminal-${Date.now()}.log`;
    a.click();
    URL.revokeObjectURL(url);
  }, [workspaceDir, workspaceLabel, port]);

  const statusLabel = useMemo(() => ({
    idle: 'Idle',
    starting: 'Connecting',
    running: 'Connected',
    stopping: 'Stopping',
    error: 'Error',
  }[status]), [status]);

  if (isAvailable === false) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center bg-[#1e1e1e] gap-3 p-8">
        <TerminalIcon size={44} className="text-dark-muted opacity-40" />
        <div className="text-sm text-dark-muted">Local terminal unavailable</div>
      </div>
    );
  }

  return (
    <div className={`flex flex-col bg-[#1e1e1e] border-t border-dark-border ${isFullscreen ? 'fixed inset-0 z-50' : 'flex-1 min-h-0'}`}>
      <div className="flex items-center justify-between gap-3 px-3 py-2 bg-[#252526] border-b border-[#3c3c3c]">
        <div className="flex items-center gap-2 min-w-0">
          <TerminalIcon size={14} className="text-dark-muted shrink-0" />
          <span className="text-xs text-dark-muted shrink-0">Terminal</span>
          <span className="text-xs text-dark-muted truncate">{workspaceLabel || workspaceDir}</span>
          <span className="text-xs text-dark-muted">[{statusLabel}]</span>
          {errorMessage && <span className="text-xs text-accent-red truncate max-w-[240px]">{errorMessage}</span>}
        </div>

        <div className="flex items-center gap-1 flex-wrap justify-end">
          <button onClick={toggleAutoFollow} className={`p-1 rounded hover:bg-[#3c3c3c] ${autoFollow ? 'text-accent-blue' : 'text-dark-muted'} hover:text-dark-text`} title={autoFollow ? '璺熼殢杈撳嚭锛氬紑' : '璺熼殢杈撳嚭锛氬叧'}>
            <ArrowDownToLine size={12} />
          </button>
          <button onClick={handleRestart} className="p-1 rounded hover:bg-[#3c3c3c] text-dark-muted hover:text-dark-text" title="Restart">
            <RefreshCw size={12} />
          </button>
          <button onClick={handleCopy} className="p-1 rounded hover:bg-[#3c3c3c] text-dark-muted hover:text-dark-text" title="Copy">
            <Copy size={12} />
          </button>
          <button onClick={handleDownloadLog} className="p-1 rounded hover:bg-[#3c3c3c] text-dark-muted hover:text-dark-text" title="Download log">
            <Download size={12} />
          </button>
          <button onClick={handleClear} className="p-1 rounded hover:bg-[#3c3c3c] text-dark-muted hover:text-dark-text" title="Clear">
            <Trash2 size={12} />
          </button>
          <button onClick={() => setIsFullscreen(v => !v)} className="p-1 rounded hover:bg-[#3c3c3c] text-dark-muted hover:text-dark-text" title="Fullscreen">
            {isFullscreen ? <Minimize2 size={12} /> : <Maximize2 size={12} />}
          </button>
        </div>
      </div>

      <div ref={terminalRef} className="flex-1 overflow-hidden" />
    </div>
  );
}

export default TerminalView;

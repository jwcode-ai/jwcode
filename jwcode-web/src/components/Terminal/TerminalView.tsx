import { useEffect, useRef, useState } from 'react';
import { Terminal as TerminalIcon, Trash2, Copy, Download, Settings } from 'lucide-react';
import { Terminal } from 'xterm';
import { FitAddon } from 'xterm-addon-fit';
import { useTerminalStore } from '../../stores/terminalStore';
import './xterm.css';

export function TerminalView() {
  const terminalRef = useRef<HTMLDivElement>(null);
  const xtermRef = useRef<Terminal | null>(null);
  const fitAddonRef = useRef<FitAddon | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);

  const { output, isExecuting, clearOutput } = useTerminalStore();

  // Initialize xterm
  useEffect(() => {
    if (!terminalRef.current || xtermRef.current) return;

    const term = new Terminal({
      theme: {
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
      },
      fontFamily: '"Cascadia Code", "Fira Code", Consolas, monospace',
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
    fitAddon.fit();

    xtermRef.current = term;
    fitAddonRef.current = fitAddon;

    // Write welcome message
    term.writeln('\x1b[36mJwCode Terminal\x1b[0m');
    term.writeln('\x1b[90mType "help" for available commands\x1b[0m');
    term.writeln('');

    // Handle resize
    const handleResize = () => {
      if (fitAddonRef.current) {
        fitAddonRef.current.fit();
      }
    };
    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      term.dispose();
      xtermRef.current = null;
    };
  }, []);

  // Sync output from store to xterm
  useEffect(() => {
    if (!xtermRef.current || output.length === 0) return;

    const term = xtermRef.current;
    const lastLine = output[output.length - 1];
    
    // Only write new lines
    term.write(`\r\n${lastLine}`);
  }, [output]);

  // Handle user input
  const handleKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (!xtermRef.current) return;

    const term = xtermRef.current;
    
    if (e.key === 'Enter') {
      // Handle command execution
      e.preventDefault();
    } else if (e.key === 'Backspace') {
      term.write('\b \b');
    } else if (e.key.length === 1) {
      term.write(e.key);
    }
  };

  const handleClear = () => {
    if (xtermRef.current) {
      xtermRef.current.clear();
      xtermRef.current.writeln('\x1b[90m--- Terminal cleared ---\x1b[0m');
    }
    clearOutput();
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
    const content = output.join('\n');
    const blob = new Blob([content], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `terminal-${Date.now()}.log`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div 
      className={`flex flex-col bg-[#1e1e1e] border-t border-dark-border ${
        isFullscreen ? 'fixed inset-0 z-50' : 'h-80'
      }`}
    >
      {/* Terminal Header */}
      <div className="flex items-center justify-between px-3 py-1.5 bg-[#252526] border-b border-[#3c3c3c]">
        <div className="flex items-center gap-2">
          <TerminalIcon size={14} className="text-dark-muted" />
          <span className="text-xs text-dark-muted">Terminal</span>
          {isExecuting && (
            <span className="w-2 h-2 bg-accent-green rounded-full animate-pulse" />
          )}
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={handleCopy}
            className="p-1 rounded hover:bg-[#3c3c3c] text-dark-muted hover:text-dark-text"
            title="复制"
          >
            <Copy size={12} />
          </button>
          <button
            onClick={handleDownload}
            className="p-1 rounded hover:bg-[#3c3c3c] text-dark-muted hover:text-dark-text"
            title="下载日志"
          >
            <Download size={12} />
          </button>
          <button
            onClick={handleClear}
            className="p-1 rounded hover:bg-[#3c3c3c] text-dark-muted hover:text-dark-text"
            title="清空"
          >
            <Trash2 size={12} />
          </button>
          <button
            onClick={() => setIsFullscreen(!isFullscreen)}
            className="p-1 rounded hover:bg-[#3c3c3c] text-dark-muted hover:text-dark-text"
            title={isFullscreen ? '退出全屏' : '全屏'}
          >
            <Settings size={12} />
          </button>
        </div>
      </div>

      {/* Terminal Content */}
      <div 
        ref={terminalRef}
        className="flex-1 overflow-hidden"
        onKeyDown={handleKeyDown}
        tabIndex={0}
      />
    </div>
  );
}

export default TerminalView;

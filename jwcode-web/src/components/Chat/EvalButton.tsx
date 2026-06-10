import { useState, useRef, useEffect } from 'react';
import { useSessionStore } from '../../stores/sessionStore';
import wsService from '../../services/websocket';
import { FlaskConical, ChevronDown } from 'lucide-react';

type EvalMode = 'quick' | 'full' | 'list';

const EVAL_OPTIONS: { mode: EvalMode; label: string; desc: string }[] = [
  { mode: 'quick', label: 'Quick Eval', desc: '验收检查 (无需 LLM)' },
  { mode: 'full', label: 'Full Eval', desc: '真实 LLM 调用 (耗时)' },
  { mode: 'list', label: 'List Tasks', desc: '列出所有评测任务' },
];

export function EvalButton() {
  const [open, setOpen] = useState(false);
  const [running, setRunning] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const runEval = (mode: EvalMode) => {
    const sessionId = useSessionStore.getState().activeSessionId;
    if (!sessionId) return;
    setOpen(false);
    setRunning(true);

    const cmd = mode === 'list' ? '/test list' : `/test ${mode === 'full' ? 'full' : 'all'}`;
    wsService.setSessionId(sessionId);
    wsService.send({ type: 'chat', sessionId, message: cmd });

    setTimeout(() => setRunning(false), 2000);
  };

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(!open)}
        disabled={running}
        className={`h-6 px-2 text-[11px] rounded flex items-center gap-1 transition-colors ${
          running
            ? 'bg-accent-green/20 text-accent-green cursor-wait'
            : 'bg-dark-bg text-dark-muted hover:text-dark-text hover:bg-dark-hover border border-dark-border'
        }`}
        title="Run Capability Eval"
      >
        <FlaskConical size={12} className={running ? 'animate-pulse' : ''} />
        <span>{running ? 'Running...' : 'Eval'}</span>
        <ChevronDown size={10} />
      </button>

      {open && (
        <div className="absolute bottom-full mb-1 right-0 w-48 bg-dark-surface border border-dark-border rounded-lg shadow-xl z-50 py-1">
          {EVAL_OPTIONS.map((opt) => (
            <button
              key={opt.mode}
              onClick={() => runEval(opt.mode)}
              className="w-full text-left px-3 py-2 hover:bg-dark-hover transition-colors"
            >
              <div className="text-xs text-dark-text">{opt.label}</div>
              <div className="text-[10px] text-dark-muted">{opt.desc}</div>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

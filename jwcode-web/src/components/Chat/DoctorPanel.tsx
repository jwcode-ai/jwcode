import { memo, useState } from 'react';
import wsService from '../../services/websocket';

interface DoctorResult { name: string; ok: boolean; detail: string; }

export const DoctorPanel = memo(function DoctorPanel() {
  const [results, setResults] = useState<DoctorResult[]>([]);
  const [running, setRunning] = useState(false);

  const run = () => {
    setRunning(true);
    const handler = (msg: any) => {
      try {
        const lines = (JSON.parse(msg.data || '{}').data || '').split('\n').filter(Boolean);
        setResults(lines.map((l: string) => {
          const ok = l.startsWith('✅');
          return { name: l.slice(3, l.indexOf(':')), ok, detail: l.slice(l.indexOf(':') + 2) };
        }));
      } catch {}
      setRunning(false);
    };
    wsService.onMessage(handler);
    wsService.send({ type: 'doctor' });
  };

  return (
    <div className="space-y-2">
      <button onClick={run} disabled={running}
        className="px-3 py-1.5 bg-accent-blue text-white rounded-lg text-xs hover:opacity-90 disabled:opacity-50">
        {running ? '⏳ 诊断中...' : '🔍 运行诊断'}
      </button>
      {results.length > 0 && (
        <div className="space-y-0.5 text-xs font-mono">
          {results.map((r) => (
            <div key={r.name} className={r.ok ? 'text-accent-green' : 'text-accent-red'}>
              {r.ok ? '✅' : '❌'} {r.name}: {r.detail}
            </div>
          ))}
        </div>
      )}
    </div>
  );
});

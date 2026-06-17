import { memo, useState } from 'react';
import { X, ChevronDown, ChevronRight, AlertCircle, AlertTriangle, Info, Siren } from 'lucide-react';
import { useErrorStore, type ErrorLevel } from '../../stores/errorStore';

const LEVEL_CONFIG: Record<ErrorLevel, { icon: typeof AlertCircle; border: string; bg: string; text: string }> = {
  critical: { icon: Siren, border: 'border-accent-red', bg: 'bg-accent-red/10', text: 'text-accent-red' },
  error: { icon: AlertCircle, border: 'border-accent-red/60', bg: 'bg-accent-red/5', text: 'text-accent-red' },
  warning: { icon: AlertTriangle, border: 'border-accent-yellow/60', bg: 'bg-accent-yellow/5', text: 'text-accent-yellow' },
  info: { icon: Info, border: 'border-accent-blue/60', bg: 'bg-accent-blue/5', text: 'text-accent-blue' },
};

export const ErrorToast = memo(function ErrorToast() {
  const errors = useErrorStore((s) => s.errors.filter((e) => !e.dismissed));
  const dismiss = useErrorStore((s) => s.dismiss);

  if (errors.length === 0) return null;

  return (
    <div className="fixed bottom-4 right-4 z-[110] flex flex-col gap-2 max-w-sm">
      {errors.map((err) => {
        const cfg = LEVEL_CONFIG[err.level];
        const Icon = cfg.icon;
        return <ErrorCard key={err.id} entry={err} icon={Icon} config={cfg} onDismiss={() => dismiss(err.id)} />;
      })}
    </div>
  );
});

function ErrorCard({ entry, icon: Icon, config, onDismiss }: {
  entry: { title: string; detail?: string; action?: { label: string; onClick: () => void } };
  icon: typeof AlertCircle;
  config: typeof LEVEL_CONFIG.critical;
  onDismiss: () => void;
}) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className={`flex flex-col px-3 py-2.5 rounded-lg border shadow-lg animate-fade-in-up ${config.bg} ${config.border}`}>
      <div className="flex items-start gap-2">
        <Icon size={16} className={`shrink-0 mt-0.5 ${config.text}`} />
        <span className={`text-xs flex-1 ${config.text}`}>{entry.title}</span>
        <div className="flex items-center gap-1 shrink-0">
          {entry.detail && (
            <button
              onClick={() => setExpanded(!expanded)}
              className="p-0.5 rounded hover:bg-dark-hover/50 transition-colors"
            >
              {expanded ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
            </button>
          )}
          <button onClick={onDismiss} className="p-0.5 rounded hover:bg-dark-hover/50 transition-colors">
            <X size={12} />
          </button>
        </div>
      </div>
      {expanded && entry.detail && (
        <div className="mt-1.5 ml-6 text-[11px] text-dark-muted whitespace-pre-wrap break-words leading-relaxed border-t border-dark-border/50 pt-1.5">
          {entry.detail}
        </div>
      )}
      {entry.action && (
        <button
          onClick={entry.action.onClick}
          className="mt-1.5 ml-6 self-start text-[11px] px-2 py-0.5 rounded bg-dark-hover hover:bg-dark-border transition-colors"
        >
          {entry.action.label}
        </button>
      )}
    </div>
  );
}

export default ErrorToast;

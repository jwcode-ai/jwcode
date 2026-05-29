import { memo } from 'react';
import { X, Info, CheckCircle, AlertTriangle, AlertCircle } from 'lucide-react';
import { useToastStore, type ToastType } from '../../stores/toastStore';

const ICON_MAP: Record<ToastType, typeof Info> = {
  info: Info,
  success: CheckCircle,
  warning: AlertTriangle,
  error: AlertCircle,
};

const COLOR_MAP: Record<ToastType, string> = {
  info: 'border-accent-blue/30 bg-accent-blue/10 text-accent-blue',
  success: 'border-accent-green/30 bg-accent-green/10 text-accent-green',
  warning: 'border-accent-yellow/30 bg-accent-yellow/10 text-accent-yellow',
  error: 'border-accent-red/30 bg-accent-red/10 text-accent-red',
};

export const ToastContainer = memo(function ToastContainer() {
  const { toasts, dismissToast } = useToastStore();

  if (toasts.length === 0) return null;

  return (
    <div className="fixed bottom-20 right-4 z-[100] flex flex-col gap-2 max-w-sm">
      {toasts.map((toast) => {
        const Icon = ICON_MAP[toast.type];
        return (
          <div
            key={toast.id}
            className={`flex items-start gap-2 px-3 py-2.5 rounded-lg border shadow-lg animate-fade-in-up ${COLOR_MAP[toast.type]}`}
          >
            <Icon size={16} className="shrink-0 mt-0.5" />
            <span className="text-xs flex-1">{toast.message}</span>
            <button
              onClick={() => dismissToast(toast.id)}
              className="shrink-0 p-0.5 rounded hover:bg-dark-hover/50 transition-colors"
            >
              <X size={12} />
            </button>
          </div>
        );
      })}
    </div>
  );
});

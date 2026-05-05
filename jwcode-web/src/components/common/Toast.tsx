import { useState, useEffect, createContext, useContext, useCallback, ReactNode } from 'react';
import { X, CheckCircle, AlertCircle, Info, AlertTriangle } from 'lucide-react';
import { cn } from '../../utils/cn';

// Toast类型
type ToastType = 'success' | 'error' | 'info' | 'warning';

interface Toast {
  id: string;
  type: ToastType;
  message: string;
  duration?: number;
}

// Toast Context
interface ToastContextType {
  showToast: (type: ToastType, message: string, duration?: number) => void;
}

const ToastContext = createContext<ToastContextType | null>(null);

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within ToastProvider');
  }
  return context;
}

// Toast Provider
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const showToast = useCallback((type: ToastType, message: string, duration = 3000) => {
    const id = `toast-${Date.now()}-${Math.random()}`;
    setToasts(prev => [...prev, { id, type, message, duration }]);
  }, []);

  const removeToast = useCallback((id: string) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  }, []);

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      <ToastContainer toasts={toasts} onRemove={removeToast} />
    </ToastContext.Provider>
  );
}

// Toast Container
function ToastContainer({ toasts, onRemove }: { toasts: Toast[]; onRemove: (id: string) => void }) {
  return (
    <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-sm">
      {toasts.map(toast => (
        <ToastItem key={toast.id} toast={toast} onRemove={onRemove} />
      ))}
    </div>
  );
}

// Toast Item
function ToastItem({ toast, onRemove }: { toast: Toast; onRemove: (id: string) => void }) {
  useEffect(() => {
    if (toast.duration === 0) return;
    
    const timer = setTimeout(() => {
      onRemove(toast.id);
    }, toast.duration || 3000);

    return () => clearTimeout(timer);
  }, [toast, onRemove]);

  const icons = {
    success: <CheckCircle className="w-5 h-5 text-accent-green" />,
    error: <AlertCircle className="w-5 h-5 text-accent-red" />,
    info: <Info className="w-5 h-5 text-accent-blue" />,
    warning: <AlertTriangle className="w-5 h-5 text-accent-yellow" />,
  };

  const bgColors = {
    success: 'bg-accent-green/10 border-accent-green/30',
    error: 'bg-accent-red/10 border-accent-red/30',
    info: 'bg-accent-blue/10 border-accent-blue/30',
    warning: 'bg-accent-yellow/10 border-accent-yellow/30',
  };

  return (
    <div 
      className={cn(
        'flex items-center gap-3 px-4 py-3 rounded-lg border shadow-lg backdrop-blur-sm animate-slide-in',
        'bg-dark-surface',
        bgColors[toast.type]
      )}
    >
      {icons[toast.type]}
      <p className="flex-1 text-sm text-dark-text">{toast.message}</p>
      <button
        onClick={() => onRemove(toast.id)}
        className="text-dark-muted hover:text-dark-text transition-colors"
      >
        <X className="w-4 h-4" />
      </button>
    </div>
  );
}

// 使用示例:
// const { showToast } = useToast();
// showToast('success', '操作成功！');
// showToast('error', '操作失败，请重试');
// showToast('info', '这是一条信息提示');
// showToast('warning', '警告信息');
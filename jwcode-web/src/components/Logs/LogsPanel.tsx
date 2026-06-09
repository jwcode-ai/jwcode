import { memo, useRef, useCallback, useEffect, useState } from 'react';
import { ScrollText, FileText, Download, Eye, ChevronLeft, Loader2 } from 'lucide-react';
import { LogEntry } from '../../types';
import { api, LogFileInfo, LogFileContent } from '../../services/api';

interface LogsPanelProps {
  logs: LogEntry[];
  onClear: () => void;
  compact?: boolean;
}

const levelColors: Record<string, string> = {
  info: 'bg-accent-blue',
  warn: 'bg-accent-yellow',
  error: 'bg-accent-red',
  success: 'bg-accent-green',
  tool: 'bg-accent-purple',
};

const levelIcons: Record<string, string> = {
  info: '\u2139',
  warn: '\u26a0',
  error: '\u2717',
  success: '\u2713',
  tool: '\ud83d\udd27',
};

function LogFileBrowser() {
  const [files, setFiles] = useState<LogFileInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedFile, setSelectedFile] = useState<LogFileInfo | null>(null);
  const [fileContent, setFileContent] = useState<LogFileContent | null>(null);
  const [loadingContent, setLoadingContent] = useState(false);

  const loadFiles = useCallback(async () => {
    setLoading(true);
    const res = await api.logs.list();
    if (res.success && res.data) {
      setFiles(res.data);
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    loadFiles();
  }, [loadFiles]);

  const handlePreview = useCallback(async (file: LogFileInfo) => {
    if (!file.previewable) return;
    setSelectedFile(file);
    setLoadingContent(true);
    const res = await api.logs.read(file.name, 500);
    if (res.success && res.data) {
      setFileContent(res.data);
    }
    setLoadingContent(false);
  }, []);

  const handleBack = useCallback(() => {
    setSelectedFile(null);
    setFileContent(null);
  }, []);

  const formatSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  const formatTime = (ms: number) => {
    return new Date(ms).toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  if (selectedFile && fileContent) {
    return (
      <div className="flex flex-col min-h-0 h-full">
        <div className="flex items-center gap-2 mb-2 shrink-0">
          <button
            onClick={handleBack}
            className="p-1 rounded hover:bg-dark-hover transition-colors"
            title="\u8fd4\u56de\u6587\u4ef6\u5217\u8868"
          >
            <ChevronLeft size={16} />
          </button>
          <span className="text-sm font-medium text-dark-text truncate flex-1">{selectedFile.name}</span>
          <a
            href={api.logs.downloadUrl(selectedFile.name)}
            download={selectedFile.name}
            className="flex items-center gap-1 px-2 py-1 text-xs bg-accent-blue text-white rounded hover:opacity-90 transition-opacity"
          >
            <Download size={12} />
            \u4e0b\u8f7d
          </a>
        </div>
        {loadingContent ? (
          <div className="flex-1 flex items-center justify-center">
            <Loader2 size={20} className="animate-spin text-accent-blue" />
          </div>
        ) : (
          <>
            <div className="text-[10px] text-dark-muted mb-1 shrink-0">
              {formatSize(fileContent.size)} \u00b7 {fileContent.totalLines} \u884c
              {fileContent.startLine > 1 && ` (\u663e\u793a\u6700\u65b0 ${fileContent.totalLines - fileContent.startLine + 1} \u884c)`}
            </div>
            <div
              className="flex-1 overflow-y-auto min-h-0 font-mono text-[11px] leading-relaxed whitespace-pre-wrap break-all
                bg-dark-bg border border-dark-border rounded p-3 custom-scrollbar"
              style={{
                scrollbarWidth: 'thin',
                scrollbarColor: '#30363d transparent',
              }}
            >
              {fileContent.content || (
                <span className="text-dark-muted italic">(\u7a7a\u6587\u4ef6)</span>
              )}
            </div>
          </>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col min-h-0 h-full">
      <div className="flex items-center justify-between mb-2 shrink-0">
        <span className="text-xs font-medium text-dark-text">\u65e5\u5fd7\u6587\u4ef6</span>
        <button
          onClick={loadFiles}
          className="px-2 py-1 text-[10px] bg-dark-hover rounded hover:bg-dark-border transition-colors"
          disabled={loading}
        >
          {loading ? '\u5237\u65b0\u4e2d...' : '\u5237\u65b0'}
        </button>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-8">
          <Loader2 size={16} className="animate-spin text-accent-blue" />
        </div>
      ) : files.length === 0 ? (
        <div className="text-center py-8 text-dark-muted">
          <FileText size={32} className="mx-auto mb-1 opacity-50" />
          <p className="text-xs">\u6682\u65e0\u65e5\u5fd7\u6587\u4ef6</p>
        </div>
      ) : (
        <div className="flex-1 overflow-y-auto min-h-0 space-y-1 custom-scrollbar">
          {files.map(file => (
            <div
              key={file.name}
              className="flex items-center gap-2 p-2 rounded hover:bg-dark-hover transition-colors group"
            >
              <FileText size={14} className="text-accent-blue shrink-0" />
              <div className="flex-1 min-w-0">
                <div className="text-xs text-dark-text truncate">{file.name}</div>
                <div className="text-[10px] text-dark-muted">
                  {formatSize(file.size)} \u00b7 {formatTime(file.modified)}
                </div>
              </div>
              <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                {file.previewable && (
                  <button
                    onClick={() => handlePreview(file)}
                    className="p-1.5 rounded hover:bg-dark-border transition-colors"
                    title="\u9884\u89c8"
                  >
                    <Eye size={14} />
                  </button>
                )}
                <a
                  href={api.logs.downloadUrl(file.name)}
                  download={file.name}
                  className="p-1.5 rounded hover:bg-dark-border transition-colors block"
                  title="\u4e0b\u8f7d"
                >
                  <Download size={14} />
                </a>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export const LogsPanel = memo(function LogsPanel({ logs, onClear, compact }: LogsPanelProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const isAtBottomRef = useRef(true);

  const checkIsAtBottom = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return true;
    const threshold = 50;
    return el.scrollHeight - el.scrollTop - el.clientHeight < threshold;
  }, []);

  const scrollToBottom = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    el.scrollTop = el.scrollHeight;
  }, []);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const handleScroll = () => {
      isAtBottomRef.current = checkIsAtBottom();
    };
    el.addEventListener('scroll', handleScroll, { passive: true });
    return () => el.removeEventListener('scroll', handleScroll);
  }, [checkIsAtBottom]);

  useEffect(() => {
    if (isAtBottomRef.current) {
      scrollToBottom();
    }
  }, [logs, scrollToBottom]);

  return (
    <div className={`flex flex-col min-h-0 ${compact ? 'flex-1 p-2' : 'flex-1 overflow-hidden p-4'}`}>
      {!compact && (
        <div className="flex items-center justify-between mb-4 shrink-0 flex-wrap gap-2">
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <ScrollText size={18} className="text-accent-blue" />
            \u540e\u53f0\u65e5\u5fd7
            <span className="text-sm font-normal text-dark-muted">({logs.length})</span>
          </h2>
          <div className="flex items-center gap-2">
            <button
              onClick={onClear}
              className="px-3 py-1.5 text-sm bg-dark-surface border border-dark-border rounded hover:bg-dark-hover transition-colors"
            >
              \u6e05\u7a7a
            </button>
          </div>
        </div>
      )}

      {!compact && (
        <div className="mb-4 border border-dark-border rounded-lg p-3 bg-dark-surface shrink-0 max-h-[260px] overflow-hidden flex flex-col">
          <LogFileBrowser />
        </div>
      )}

      {!compact && (
        <div className="flex items-center gap-2 mb-2 shrink-0">
          <span className="text-xs font-medium text-dark-muted">\u5b9e\u65f6\u65e5\u5fd7\u6d41</span>
        </div>
      )}

      <div
        ref={scrollRef}
        className="flex-1 overflow-y-auto min-h-0 font-mono text-xs space-y-0.5"
        style={{
          scrollbarWidth: 'thin',
          scrollbarColor: '#30363d transparent',
        }}
      >
        {logs.length === 0 ? (
          <div className="text-center text-dark-muted py-12">
            <ScrollText size={48} className="mx-auto mb-2 opacity-50" />
            <p>\u6682\u65e0\u5b9e\u65f6\u65e5\u5fd7</p>
          </div>
        ) : (
          logs.map(log => (
            <div
              key={log.id}
              className={`flex gap-2 p-2 rounded hover:bg-dark-surface transition-colors ${
                log.level === 'error' ? 'bg-accent-red/10' :
                log.level === 'warn' ? 'bg-accent-yellow/10' :
                log.level === 'success' ? 'bg-accent-green/10' : ''
              }`}
            >
              <span className="text-dark-muted shrink-0 text-xs">
                {new Date(log.timestamp).toLocaleTimeString('zh-CN', {
                  hour: '2-digit',
                  minute: '2-digit',
                  second: '2-digit',
                })}
              </span>
              <span className="text-lg shrink-0 leading-none">{levelIcons[log.level]}</span>
              <span className={`px-1.5 py-0.5 rounded text-white text-xs shrink-0 ${levelColors[log.level]}`}>
                {log.level.toUpperCase()}
              </span>
              <span className="text-dark-muted shrink-0 text-xs">[{log.source}]</span>
              <span className="text-dark-text break-all">{log.message}</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
});

import { useState, useEffect, useCallback } from 'react';
import { FileText, Save, RefreshCw, AlertTriangle } from 'lucide-react';
import api from '../../services/api';
import { toast } from '../../stores/toastStore';

interface ConfigFileInfo {
  name: string;
  size: number;
  modified: number;
  editable: boolean;
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatTime(ts: number): string {
  return new Date(ts).toLocaleString();
}

export function ConfigFileEditor() {
  const [files, setFiles] = useState<ConfigFileInfo[]>([]);
  const [selectedFile, setSelectedFile] = useState<string | null>(null);
  const [content, setContent] = useState('');
  const [originalContent, setOriginalContent] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const loadFiles = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.config.files.list();
      if (res.success && res.data) {
        setFiles(res.data as ConfigFileInfo[]);
      }
    } catch {
      toast.error('Failed to load config files');
    } finally {
      setLoading(false);
    }
  }, []);

  const loadFile = useCallback(async (fileName: string) => {
    try {
      const res = await api.config.files.read(fileName);
      if (res.success && res.data) {
        const data = res.data as { name: string; content: string; editable: boolean };
        setSelectedFile(data.name);
        setContent(data.content);
        setOriginalContent(data.content);
      }
    } catch {
      toast.error(`Failed to read ${fileName}`);
    }
  }, []);

  useEffect(() => {
    loadFiles();
  }, [loadFiles]);

  const handleSave = useCallback(async () => {
    if (!selectedFile) return;
    setSaving(true);
    try {
      const res = await api.config.files.write(selectedFile, content);
      if (res.success) {
        setOriginalContent(content);
        toast.success(`${selectedFile} saved`);
        loadFiles(); // refresh file list for size/modified
      } else {
        toast.error(res.error || 'Failed to save');
      }
    } catch {
      toast.error('Failed to save file');
    } finally {
      setSaving(false);
    }
  }, [selectedFile, content, loadFiles]);

  const hasChanges = content !== originalContent;

  const isYaml = selectedFile?.endsWith('.yaml') || selectedFile?.endsWith('.yml');
  const isJson = selectedFile?.endsWith('.json');

  // Basic syntax highlighting via CSS classes on a pre element overlaid on textarea
  const highlightLine = (line: string): string => {
    if (isYaml) {
      // YAML: highlight comments, keys, strings
      return line
        .replace(/(#.*)$/, '<span class="text-dark-muted">$1</span>')
        .replace(/^(\s*)([\w-]+)(:)/, '$1<span class="text-accent-blue">$2</span>$3')
        .replace(/:\s+(true|false|null|~)(\s*)$/, ': <span class="text-accent-purple">$1</span>$2')
        .replace(/:\s+(\d+\.?\d*)(\s*)$/, ': <span class="text-accent-green">$1</span>$2')
        .replace(/:\s+"([^"]*)"(\s*)$/, ': <span class="text-accent-yellow">"$1"</span>$2');
    }
    if (isJson) {
      return line
        .replace(/(\s*"[^"]*")(\s*:)/g, '<span class="text-accent-blue">$1</span>$2')
        .replace(/:\s+(true|false|null)/g, ': <span class="text-accent-purple">$1</span>')
        .replace(/:\s+(\d+\.?\d*)/g, ': <span class="text-accent-green">$1</span>')
        .replace(/:\s+"([^"]*)"/g, ': <span class="text-accent-yellow">"$1"</span>');
    }
    return line;
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h4 className="text-sm font-medium text-dark-text">Config Files</h4>
        <button
          onClick={loadFiles}
          disabled={loading}
          className="p-1.5 rounded hover:bg-dark-hover transition-colors text-dark-muted"
          title="Refresh file list"
        >
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
        </button>
      </div>

      {files.length === 0 && !loading && (
        <p className="text-xs text-dark-muted">No config files found in ~/.jwcode/</p>
      )}

      {files.length > 0 && (
        <div className="flex gap-3">
          {/* File list */}
          <div className="w-48 shrink-0 space-y-0.5 max-h-[300px] overflow-y-auto custom-scrollbar">
            {files.map((f) => (
              <button
                key={f.name}
                onClick={() => loadFile(f.name)}
                className={`w-full text-left px-2.5 py-1.5 rounded text-xs flex items-center gap-2 transition-colors ${
                  selectedFile === f.name
                    ? 'bg-accent-blue/10 text-accent-blue'
                    : 'text-dark-text hover:bg-dark-hover'
                }`}
              >
                <FileText size={12} className="shrink-0" />
                <span className="truncate flex-1">{f.name}</span>
                {!f.editable && (
                  <span className="text-[10px] text-dark-muted shrink-0" title="Read-only">
                    read
                  </span>
                )}
              </button>
            ))}
          </div>

          {/* Editor area */}
          {selectedFile ? (
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between mb-1.5">
                <div className="flex items-center gap-2 text-xs text-dark-muted">
                  <span className="text-dark-text font-medium">{selectedFile}</span>
                  {files.find((f) => f.name === selectedFile) && (
                    <span>
                      {formatSize(files.find((f) => f.name === selectedFile)!.size)}
                      {' · '}
                      {formatTime(files.find((f) => f.name === selectedFile)!.modified)}
                    </span>
                  )}
                  {hasChanges && (
                    <span className="text-accent-yellow flex items-center gap-1">
                      <AlertTriangle size={10} />
                      unsaved changes
                    </span>
                  )}
                </div>
                <button
                  onClick={handleSave}
                  disabled={!hasChanges || saving}
                  className={`flex items-center gap-1 px-2.5 py-1 rounded text-xs transition-all ${
                    hasChanges
                      ? 'bg-accent-blue text-white hover:opacity-90'
                      : 'bg-dark-hover text-dark-muted cursor-not-allowed'
                  }`}
                >
                  <Save size={12} />
                  {saving ? 'Saving...' : 'Save'}
                </button>
              </div>

              {/* Syntax highlight overlay + textarea */}
              <div className="relative">
                <pre
                  className="w-full h-[260px] p-3 rounded-lg bg-dark-bg border border-dark-border font-mono text-xs leading-relaxed overflow-auto custom-scrollbar pointer-events-none"
                  dangerouslySetInnerHTML={{
                    __html: content.split('\n').map(highlightLine).join('\n') || '&nbsp;',
                  }}
                />
                <textarea
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  className="absolute inset-0 w-full h-full p-3 rounded-lg bg-transparent border border-dark-border font-mono text-xs leading-relaxed resize-none overflow-auto custom-scrollbar text-transparent caret-dark-text focus:outline-none focus:border-accent-blue/50"
                  style={{ color: 'transparent' }}
                  spellCheck={false}
                />
              </div>
            </div>
          ) : (
            <div className="flex-1 flex items-center justify-center text-xs text-dark-muted">
              Select a file to edit
            </div>
          )}
        </div>
      )}
    </div>
  );
}

import { useState, useEffect, useCallback, useRef } from 'react';
import Editor from '@monaco-editor/react';
import { FolderTree, Folder, File, ChevronRight, ChevronDown, RefreshCw, Home, Search, Save, X, Upload, Download } from 'lucide-react';
import type { FileNode } from '../../types';
import { api } from '../../services/api';
import { toast } from '../../stores/toastStore';

function getLanguage(filename: string): string {
  const ext = filename.split('.').pop()?.toLowerCase();
  const map: Record<string, string> = {
    ts: 'typescript', tsx: 'typescript', js: 'javascript', jsx: 'javascript',
    java: 'java', py: 'python', rs: 'rust', go: 'go', rb: 'ruby',
    json: 'json', xml: 'xml', html: 'html', css: 'css', scss: 'scss',
    md: 'markdown', yaml: 'yaml', yml: 'yaml', toml: 'toml',
    sql: 'sql', sh: 'shell', bat: 'bat', ps1: 'powershell',
    dockerfile: 'dockerfile', properties: 'properties',
    c: 'c', cpp: 'cpp', h: 'c', hpp: 'cpp', cs: 'csharp',
    vue: 'html', svelte: 'html', svg: 'xml',
  };
  return map[ext || ''] || 'plaintext';
}

export function FileTreeView() {
  const [files, setFiles] = useState<FileNode[]>([]);
  const [selectedFile, setSelectedFile] = useState<string | null>(null);
  const [fileContent, setFileContent] = useState<string>('');
  const [originalContent, setOriginalContent] = useState<string>('');
  const [loadingContent, setLoadingContent] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [selectedDir, setSelectedDir] = useState<string>('');
  const uploadInputRef = useRef<HTMLInputElement>(null);

  // Load file content when selected
  useEffect(() => {
    if (!selectedFile) { setFileContent(''); setOriginalContent(''); return; }
    setLoadingContent(true);
    api.files.read(selectedFile).then(result => {
      if (result.success && result.data) {
        const content = typeof result.data === 'string' ? result.data : JSON.stringify(result.data);
        setFileContent(content);
        setOriginalContent(content);
      } else {
        toast.error(result.error || '读取文件失败');
      }
    }).catch(() => toast.error('读取文件失败'))
      .finally(() => setLoadingContent(false));
  }, [selectedFile]);

  const loadFiles = async (path?: string) => {
    setLoading(true);
    try {
      const result = await api.files.list(path);
      if (result.success && result.data) {
        setFiles(result.data.map(node => ({ ...node, expanded: false })));
      }
    } finally { setLoading(false); }
  };

  const loadChildren = async (parentNode: FileNode) => {
    if (parentNode.type !== 'directory') return;
    try {
      const result = await api.files.list(parentNode.path);
      if (result.success && result.data) {
        setFiles(prev => updateNodeChildren(prev, parentNode.id, result.data!.map(n => ({ ...n, expanded: false }))));
      }
    } catch (err) { /* silent */ }
  };

  const updateNodeChildren = (nodes: FileNode[], nodeId: string, children: FileNode[]): FileNode[] => {
    return nodes.map(node => {
      if (node.id === nodeId) return { ...node, children, expanded: true };
      if (node.children) return { ...node, children: updateNodeChildren(node.children, nodeId, children) };
      return node;
    });
  };

  useEffect(() => { loadFiles(); }, []);

  const toggleFolder = (nodeId: string) => {
    const node = findNode(files, nodeId);
    if (node && node.type === 'directory' && !node.children) { loadChildren(node); return; }
    setFiles(prev => toggleNode(prev, nodeId));
  };

  const toggleNode = (nodes: FileNode[], nodeId: string): FileNode[] =>
    nodes.map(n => n.id === nodeId && n.type === 'directory' ? { ...n, expanded: !n.expanded } : n.children ? { ...n, children: toggleNode(n.children, nodeId) } : n);

  const findNode = (nodes: FileNode[], nodeId: string): FileNode | null => {
    for (const n of nodes) {
      if (n.id === nodeId) return n;
      if (n.children) { const f = findNode(n.children, nodeId); if (f) return f; }
    }
    return null;
  };

  const filterTree = (nodes: FileNode[], query: string): FileNode[] => {
    if (!query) return nodes;
    return nodes.reduce<FileNode[]>((acc, node) => {
      if (node.name.toLowerCase().includes(query.toLowerCase())) {
        acc.push(node);
      } else if (node.children) {
        const fc = filterTree(node.children, query);
        if (fc.length > 0) acc.push({ ...node, children: fc, expanded: true });
      }
      return acc;
    }, []);
  };

  const handleSave = useCallback(async () => {
    if (!selectedFile) return;
    setSaving(true);
    try {
      const result = await api.files.update(selectedFile, fileContent);
      if (result.success) {
        setOriginalContent(fileContent);
        toast.success('文件已保存');
      } else {
        toast.error(result.error || '保存失败');
      }
    } catch {
      toast.error('保存失败');
    } finally { setSaving(false); }
  }, [selectedFile, fileContent]);

  const handleCloseFile = () => {
    setSelectedFile(null);
    setFileContent('');
    setOriginalContent('');
  };

  const handleDownload = useCallback(async (filePath: string) => {
    const result = await api.files.download(filePath);
    if (!result.success) {
      toast.error(result.error || '下载失败');
    } else {
      toast.success('已开始下载');
    }
  }, []);

  const handleUploadClick = () => uploadInputRef.current?.click();

  const handleFiles = useCallback(async (fileList: FileList | null) => {
    if (!fileList || fileList.length === 0) return;
    // 上传目标目录：选中的目录 > 选中文件所在目录 > 工作区根
    const dir = selectedDir || (selectedFile ? selectedFile.replace(/[\\/][^\\/]+$/, '') : '');
    setUploading(true);
    let ok = 0, fail = 0;
    for (const file of Array.from(fileList)) {
      const targetPath = dir ? `${dir}/${file.name}` : file.name;
      const result = await api.files.upload(file, targetPath);
      if (result.success) ok++; else fail++;
    }
    setUploading(false);
    if (ok > 0) toast.success(`已上传 ${ok} 个文件`);
    if (fail > 0) toast.error(`${fail} 个文件上传失败`);
    await loadFiles();
  }, [selectedDir, selectedFile]);

  const isModified = fileContent !== originalContent;

  const renderNode = (node: FileNode, depth = 0) => {
    const isDir = node.type === 'directory';
    const isSel = selectedFile === node.path;
    const isSelDir = selectedDir === node.path;
    return (
      <div key={node.id}>
        <div
          className={`group flex items-center gap-1 px-2 py-0.5 cursor-pointer rounded text-xs transition-colors ${isSel ? 'bg-accent-blue/20 text-accent-blue' : isSelDir ? 'bg-accent-blue/10' : 'hover:bg-dark-hover'}`}
          style={{ paddingLeft: `${depth * 14 + 6}px` }}
          onClick={() => isDir ? (setSelectedDir(node.path), toggleFolder(node.id)) : setSelectedFile(node.path)}
          title={isDir ? '点击设为上传目录并展开' : node.path}
        >
          {isDir && <span className="text-dark-muted shrink-0">{node.expanded ? <ChevronDown size={12} /> : <ChevronRight size={12} />}</span>}
          {isDir ? <Folder size={12} className="text-accent-yellow shrink-0" /> : <span className="w-3" />}
          <span className="truncate flex-1">{node.name}</span>
          {!isDir && (
            <button
              onClick={(e) => { e.stopPropagation(); void handleDownload(node.path); }}
              className="opacity-0 group-hover:opacity-100 p-0.5 rounded hover:bg-dark-border text-dark-muted hover:text-dark-text shrink-0"
              title="下载"
            >
              <Download size={11} />
            </button>
          )}
        </div>
        {isDir && node.expanded && node.children?.map(c => renderNode(c, depth + 1))}
      </div>
    );
  };

  const filteredFiles = searchQuery ? filterTree(files, searchQuery) : files;

  return (
    <div className="flex-1 flex overflow-hidden">
      {/* File Tree Panel */}
      <div className="w-64 shrink-0 flex flex-col overflow-hidden border-r border-dark-border">
        <div className="flex items-center justify-between px-3 py-2 border-b border-dark-border">
          <h2 className="text-xs font-semibold flex items-center gap-1.5"><FolderTree size={14} />文件浏览器</h2>
          <div className="flex items-center gap-0.5">
            <button onClick={handleUploadClick} disabled={uploading} className="p-1 rounded hover:bg-dark-hover disabled:opacity-50" title={selectedDir ? `上传到 ${selectedDir}` : '上传到工作区根目录'}>
              <Upload size={12} className={uploading ? 'animate-pulse' : ''} />
            </button>
            <button onClick={() => loadFiles()} disabled={loading} className="p-1 rounded hover:bg-dark-hover" title="刷新"><RefreshCw size={12} className={loading ? 'animate-spin' : ''} /></button>
            <button onClick={() => { setFiles(files.map(f => ({ ...f, expanded: false }))); setSelectedFile(null); }} className="p-1 rounded hover:bg-dark-hover" title="折叠"><Home size={12} /></button>
          </div>
        </div>
        <div className="p-1.5 border-b border-dark-border">
          <div className="relative">
            <Search size={12} className="absolute left-2 top-1/2 -translate-y-1/2 text-dark-muted" />
            <input type="text" value={searchQuery} onChange={e => setSearchQuery(e.target.value)} placeholder="搜索..." className="w-full pl-6 pr-2 py-1 text-xs bg-dark-bg border border-dark-border rounded focus:border-accent-blue focus:outline-none" />
          </div>
        </div>
        <div className="flex-1 overflow-y-auto py-1">{filteredFiles.map(n => renderNode(n))}</div>
        <input
          ref={uploadInputRef}
          type="file"
          multiple
          className="hidden"
          onChange={(e) => { void handleFiles(e.target.files); e.currentTarget.value = ''; }}
        />
      </div>

      {/* Editor Panel */}
      <div className="flex-1 flex flex-col min-w-0 bg-dark-bg">
        {selectedFile ? (
          <>
            <div className="flex items-center justify-between px-3 py-1.5 border-b border-dark-border bg-dark-surface">
              <div className="flex items-center gap-2 text-xs min-w-0">
                <File size={12} className="text-dark-muted shrink-0" />
                <span className="truncate">{selectedFile}</span>
                {isModified && <span className="text-accent-yellow text-[10px] shrink-0">● 已修改</span>}
              </div>
              <div className="flex items-center gap-1">
                <button
                  onClick={handleSave}
                  disabled={!isModified || saving}
                  className={`px-2 py-0.5 text-[10px] rounded flex items-center gap-1 transition-colors ${isModified ? 'bg-accent-green text-white hover:opacity-90' : 'bg-dark-border text-dark-muted cursor-not-allowed'}`}
                >
                  <Save size={10} />{saving ? '保存中...' : '保存'}
                </button>
                <button onClick={() => void handleDownload(selectedFile)} className="p-1 rounded hover:bg-dark-hover" title="下载"><Download size={12} /></button>
                <button onClick={handleCloseFile} className="p-1 rounded hover:bg-dark-hover" title="关闭"><X size={12} /></button>
              </div>
            </div>
            <div className="flex-1 min-h-0">
              {loadingContent ? (
                <div className="flex items-center justify-center h-full"><div className="w-4 h-4 border-2 border-accent-blue border-t-transparent rounded-full animate-spin" /></div>
              ) : (
                <Editor
                  language={getLanguage(selectedFile)}
                  value={fileContent}
                  onChange={v => setFileContent(v || '')}
                  theme="vs-dark"
                  options={{
                    fontSize: 13,
                    fontFamily: '"Cascadia Code", "Fira Code", Consolas, monospace',
                    minimap: { enabled: false },
                    lineNumbers: 'on',
                    scrollBeyondLastLine: false,
                    wordWrap: 'on',
                    automaticLayout: true,
                    tabSize: 2,
                  }}
                />
              )}
            </div>
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center">
            <div className="text-center">
              <File size={32} className="text-dark-muted mx-auto mb-2" />
              <p className="text-sm text-dark-muted">选择文件以预览或编辑</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default FileTreeView;

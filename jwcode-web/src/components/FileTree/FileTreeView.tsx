import { useState, useEffect } from 'react';
import { FolderTree, Folder, File, ChevronRight, ChevronDown, RefreshCw, Home, Search } from 'lucide-react';
import type { FileNode } from '../../types';
import { api } from '../../services/api';

export function FileTreeView() {
  const [files, setFiles] = useState<FileNode[]>([]);
  const [selectedFile, setSelectedFile] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [_error, setError] = useState<string | null>(null);

  const loadFiles = async (path?: string) => {
    setLoading(true);
    setError(null);
    try {
      const result = await api.files.list(path);
      if (result.success && result.data) {
        // 后端返回 type: 'file' | 'directory'，需要展开根目录
        const nodes = result.data.map(node => ({
          ...node,
          expanded: false,
        }));
        setFiles(nodes);
      } else {
        setError(result.error || '加载文件列表失败');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setLoading(false);
    }
  };

  const loadChildren = async (parentNode: FileNode) => {
    if (parentNode.type !== 'directory') return;
    try {
      const result = await api.files.list(parentNode.path);
      if (result.success && result.data) {
        const children = result.data.map(node => ({
          ...node,
          expanded: false,
        }));
        setFiles(prev => updateNodeChildren(prev, parentNode.id, children));
      }
    } catch (err) {
      console.error('Failed to load children:', err);
    }
  };

  const updateNodeChildren = (nodes: FileNode[], nodeId: string, children: FileNode[]): FileNode[] => {
    return nodes.map(node => {
      if (node.id === nodeId) {
        return { ...node, children, expanded: true };
      }
      if (node.children) {
        return { ...node, children: updateNodeChildren(node.children, nodeId, children) };
      }
      return node;
    });
  };

  useEffect(() => {
    loadFiles();
  }, []);

  const toggleFolder = (nodeId: string) => {
    const node = findNode(files, nodeId);
    if (node && node.type === 'directory' && !node.children) {
      // 首次展开时懒加载子目录
      loadChildren(node);
      return;
    }
    
    const toggle = (nodes: FileNode[]): FileNode[] => {
      return nodes.map(node => {
        if (node.id === nodeId && node.type === 'directory') {
          return { ...node, expanded: !node.expanded };
        }
        if (node.children) {
          return { ...node, children: toggle(node.children) };
        }
        return node;
      });
    };
    setFiles(toggle(files));
  };
  
  const findNode = (nodes: FileNode[], nodeId: string): FileNode | null => {
    for (const node of nodes) {
      if (node.id === nodeId) return node;
      if (node.children) {
        const found = findNode(node.children, nodeId);
        if (found) return found;
      }
    }
    return null;
  };


  const filterTree = (nodes: FileNode[], query: string): FileNode[] => {
    if (!query) return nodes;
    
    return nodes.reduce<FileNode[]>((acc, node) => {
      if (node.name.toLowerCase().includes(query.toLowerCase())) {
        acc.push(node);
      } else if (node.children) {
        const filteredChildren = filterTree(node.children, query);
        if (filteredChildren.length > 0) {
          acc.push({ ...node, children: filteredChildren, expanded: true });
        }
      }
      return acc;
    }, []);
  };

  const renderNode = (node: FileNode, depth: number = 0) => {
    const isDirectory = node.type === 'directory';
    const isExpanded = node.expanded;
    const isSelected = selectedFile === node.path;

    return (
      <div key={node.id}>
        <div
          className={`flex items-center gap-1 px-2 py-1 cursor-pointer rounded transition-colors ${
            isSelected ? 'bg-accent-blue/20 text-accent-blue' : 'hover:bg-dark-hover'
          }`}
          style={{ paddingLeft: `${depth * 16 + 8}px` }}
          onClick={() => {
            if (isDirectory) {
              toggleFolder(node.id);
            } else {
              setSelectedFile(node.path);
            }
          }}
        >
          {isDirectory && (
            <span className="text-dark-muted">
              {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
            </span>
          )}
          {isDirectory ? (
            <Folder size={14} className="text-accent-yellow" />
          ) : (
            <span className="w-[14px]" />
          )}
          <span className="text-sm truncate">{node.name}</span>
        </div>
        {isDirectory && isExpanded && node.children && (
          <div>
            {node.children.map(child => renderNode(child, depth + 1))}
          </div>
        )}
      </div>
    );
  };

  const filteredFiles = searchQuery ? filterTree(files, searchQuery) : files;

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between p-3 border-b border-dark-border">
        <h2 className="text-sm font-semibold flex items-center gap-2">
          <FolderTree size={16} />
          文件浏览器
        </h2>
        <div className="flex items-center gap-1">
          <button
            onClick={() => loadFiles()}
            disabled={loading}
            className="p-1 rounded hover:bg-dark-hover disabled:opacity-50"
            title="刷新"
          >
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
          </button>
          <button
            onClick={() => {
              setFiles(files.map(f => ({ ...f, expanded: false })));
              setSelectedFile(null);
            }}
            className="p-1 rounded hover:bg-dark-hover"
            title="折叠全部"
          >
            <Home size={14} />
          </button>
        </div>
      </div>

      {/* Search */}
      <div className="p-2 border-b border-dark-border">
        <div className="relative">
          <Search size={14} className="absolute left-2 top-1/2 -translate-y-1/2 text-dark-muted" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="搜索文件..."
            className="w-full pl-7 pr-3 py-1.5 text-sm bg-dark-bg border border-dark-border rounded focus:border-accent-blue focus:outline-none"
          />
        </div>
      </div>

      {/* File Tree */}
      <div className="flex-1 overflow-y-auto py-2">
        {filteredFiles.map(node => renderNode(node))}
      </div>

      {/* Selected File Info */}
      {selectedFile && (
        <div className="p-3 border-t border-dark-border bg-dark-surface">
          <div className="flex items-center gap-2 text-xs text-dark-muted">
            <File size={12} />
            <span className="truncate">{selectedFile}</span>
          </div>
        </div>
      )}
    </div>
  );
}

export default FileTreeView;

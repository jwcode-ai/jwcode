import { useState, useEffect } from 'react';
import { FolderTree, Folder, File, ChevronRight, ChevronDown, RefreshCw, Home, Search } from 'lucide-react';
import type { FileNode } from '../../types';

// Mock data for demo - in production this would come from API
const mockFileTree: FileNode[] = [
  {
    id: '1',
    name: 'src',
    path: '/src',
    type: 'folder',
    expanded: true,
    children: [
      {
        id: '2',
        name: 'components',
        path: '/src/components',
        type: 'folder',
        children: [
          { id: '3', name: 'App.tsx', path: '/src/components/App.tsx', type: 'file' },
          { id: '4', name: 'index.ts', path: '/src/components/index.ts', type: 'file' },
        ],
      },
      {
        id: '5',
        name: 'stores',
        path: '/src/stores',
        type: 'folder',
        children: [
          { id: '6', name: 'chatStore.ts', path: '/src/stores/chatStore.ts', type: 'file' },
          { id: '7', name: 'settingsStore.ts', path: '/src/stores/settingsStore.ts', type: 'file' },
        ],
      },
      { id: '8', name: 'main.tsx', path: '/src/main.tsx', type: 'file' },
      { id: '9', name: 'types.ts', path: '/src/types.ts', type: 'file' },
    ],
  },
  {
    id: '10',
    name: 'package.json',
    path: '/package.json',
    type: 'file',
  },
  {
    id: '11',
    name: 'README.md',
    path: '/README.md',
    type: 'file',
  },
];

export function FileTreeView() {
  const [files, setFiles] = useState<FileNode[]>(mockFileTree);
  const [selectedFile, setSelectedFile] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(false);

  const loadFiles = async () => {
    setLoading(true);
    // Simulate API call
    await new Promise(resolve => setTimeout(resolve, 500));
    setLoading(false);
  };

  useEffect(() => {
    loadFiles();
  }, []);

  const toggleFolder = (nodeId: string) => {
    const toggle = (nodes: FileNode[]): FileNode[] => {
      return nodes.map(node => {
        if (node.id === nodeId && node.type === 'folder') {
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
    const isFolder = node.type === 'folder';
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
            if (isFolder) {
              toggleFolder(node.id);
            } else {
              setSelectedFile(node.path);
            }
          }}
        >
          {isFolder && (
            <span className="text-dark-muted">
              {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
            </span>
          )}
          {isFolder ? (
            <Folder size={14} className="text-accent-yellow" />
          ) : (
            <span className="w-[14px]" />
          )}
          <span className="text-sm truncate">{node.name}</span>
        </div>
        {isFolder && isExpanded && node.children && (
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
            onClick={loadFiles}
            disabled={loading}
            className="p-1 rounded hover:bg-dark-hover disabled:opacity-50"
            title="刷新"
          >
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
          </button>
          <button
            onClick={() => {
              setFiles(mockFileTree.map(f => ({ ...f, expanded: false })));
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

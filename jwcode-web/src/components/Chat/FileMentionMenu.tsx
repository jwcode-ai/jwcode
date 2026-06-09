import { File, Folder, Loader2 } from 'lucide-react';
import type { FileNode } from '../../types';

interface FileMentionMenuProps {
  isOpen: boolean;
  files: FileNode[];
  selectedIndex: number;
  query: string;
  isSearching?: boolean;
  onSelect: (file: FileNode) => void;
  onHover: (index: number) => void;
}

export function FileMentionMenu({ isOpen, files, selectedIndex, query, isSearching, onSelect, onHover }: FileMentionMenuProps) {
  if (!isOpen) return null;

  return (
    <div className="absolute bottom-full left-0 right-0 mb-2 bg-dark-surface border border-dark-border rounded-lg shadow-lg overflow-hidden z-50 max-h-56 overflow-y-auto">
      <div className="px-3 py-2 text-xs text-dark-muted border-b border-dark-border bg-dark-bg flex items-center justify-between">
        <span>{query ? `@ 搜索: "${query}"` : '@ 引用文件'}</span>
        <span className="opacity-60">↑↓ 选择 · Enter 插入 · Esc 关闭</span>
      </div>
      {files.length === 0 ? (
        <div className="px-3 py-6 text-center text-dark-muted text-xs flex flex-col items-center gap-1">
          {isSearching ? (
            <>
              <Loader2 size={16} className="animate-spin" />
              <span>搜索文件中...</span>
            </>
          ) : (
            <span>未找到匹配的文件</span>
          )}
        </div>
      ) : (
        files.map((file, i) => (
          <button
            key={file.id || file.path}
            onClick={() => onSelect(file)}
            onMouseEnter={() => onHover(i)}
            className={`w-full px-3 py-1.5 flex items-center gap-2 text-left text-xs transition-colors ${
              i === selectedIndex ? 'bg-accent-blue/20 text-accent-blue' : 'hover:bg-dark-hover text-dark-text'
            }`}
          >
            {file.type === 'directory' ? (
              <Folder size={12} className="text-accent-yellow shrink-0" />
            ) : (
              <File size={12} className="text-dark-muted shrink-0" />
            )}
            <span className="truncate">{file.name}</span>
            <span className="text-[10px] text-dark-muted truncate ml-auto">{file.path}</span>
          </button>
        ))
      )}
    </div>
  );
}


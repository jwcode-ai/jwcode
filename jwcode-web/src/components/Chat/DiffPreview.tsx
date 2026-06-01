import { memo, useState } from 'react';

interface DiffPreviewProps {
  filePath?: string;
  content?: string;
  language?: string;
}

interface DiffStats {
  additions: number;
  deletions: number;
  hunks: number;
  filesChanged: number;
}

/**
 * 增强版 Diff 预览组件 — 支持 unified/side-by-side 视图、diff 统计、语法高亮。
 */
export const DiffPreview = memo(function DiffPreview({ filePath, content, language }: DiffPreviewProps) {
  if (!content && !filePath) return null;

  const [viewMode, setViewMode] = useState<'unified' | 'side-by-side'>('unified');
  const [collapsed, setCollapsed] = useState(false);

  const lang = detectLanguage(filePath, language);
  const isDiff = content?.includes('@@') || content?.includes('+++') || content?.includes('---');
  const stats = isDiff ? computeStats(content || '') : null;

  return (
    <div className="mt-2 border border-dark-border rounded-lg overflow-hidden">
      {/* Header */}
      <div className="flex items-center gap-2 px-3 py-1.5 bg-dark-surface border-b border-dark-border">
        <span className="text-accent-purple text-xs">📝</span>
        <span className="text-xs font-mono text-dark-fg truncate">{filePath || 'file'}</span>
        {stats && (
          <span className="text-xs ml-2 flex gap-2">
            <span className="text-green-400">+{stats.additions}</span>
            <span className="text-red-400">-{stats.deletions}</span>
          </span>
        )}
        <span className="text-xs text-dark-muted ml-auto">{lang}</span>
        {/* View toggle */}
        {isDiff && (
          <div className="flex gap-0.5 ml-2">
            <button
              onClick={() => setViewMode('unified')}
              className={`text-xs px-1.5 py-0.5 rounded ${
                viewMode === 'unified' ? 'bg-accent-blue/20 text-accent-blue' : 'text-dark-muted hover:text-dark-fg'
              }`}
              title="Unified view"
            >Unified</button>
            <button
              onClick={() => setViewMode('side-by-side')}
              className={`text-xs px-1.5 py-0.5 rounded ${
                viewMode === 'side-by-side' ? 'bg-accent-blue/20 text-accent-blue' : 'text-dark-muted hover:text-dark-fg'
              }`}
              title="Side-by-side view"
            >Side</button>
          </div>
        )}
        <button
          onClick={() => setCollapsed(!collapsed)}
          className="text-xs text-dark-muted hover:text-dark-fg ml-1"
        >{collapsed ? '▶' : '▼'}</button>
      </div>

      {/* Content */}
      {!collapsed && (
        <div className="bg-dark-bg overflow-x-auto max-h-96 overflow-y-auto">
          {isDiff ? (
            viewMode === 'unified' ? renderUnifiedDiff(content || '') : renderSideBySideDiff(content || '')
          ) : (
            <pre className="text-xs font-mono p-2 text-dark-fg whitespace-pre-wrap">
              {content || '(no content)'}
            </pre>
          )}
          {/* Stats footer */}
          {stats && (
            <div className="sticky bottom-0 bg-dark-surface border-t border-dark-border px-3 py-1 flex gap-4 text-xs text-dark-muted">
              <span className="text-green-400">+{stats.additions} additions</span>
              <span className="text-red-400">-{stats.deletions} deletions</span>
              <span>{stats.hunks} hunks</span>
            </div>
          )}
        </div>
      )}
    </div>
  );
});

function detectLanguage(path?: string, lang?: string): string {
  if (lang) return lang;
  if (!path) return 'plaintext';
  const ext = path.split('.').pop()?.toLowerCase();
  const langMap: Record<string, string> = {
    java: 'java', ts: 'typescript', tsx: 'typescript', js: 'javascript',
    jsx: 'javascript', py: 'python', go: 'go', rs: 'rust', cpp: 'cpp',
    c: 'c', h: 'c', css: 'css', html: 'html', json: 'json', xml: 'xml',
    yml: 'yaml', yaml: 'yaml', md: 'markdown', sql: 'sql', sh: 'bash',
    ps1: 'powershell', properties: 'properties', toml: 'toml',
    gradle: 'groovy', kt: 'kotlin', swift: 'swift',
  };
  return langMap[ext || ''] || 'plaintext';
}

function computeStats(text: string): DiffStats {
  const lines = text.split('\n');
  let additions = 0, deletions = 0, hunks = 0;
  for (const line of lines) {
    if (line.startsWith('@@')) hunks++;
    else if (line.startsWith('+') && !line.startsWith('+++')) additions++;
    else if (line.startsWith('-') && !line.startsWith('---')) deletions++;
  }
  return { additions, deletions, hunks, filesChanged: 1 };
}

function renderUnifiedDiff(text: string) {
  const lines = text.split('\n');
  return lines.map((line, i) => {
    let className = 'text-dark-fg';
    let prefix = ' ';
    if ((line.startsWith('+++') || line.startsWith('---')) && i < 3) {
      className = 'text-dark-muted font-bold text-xs';
    } else if (line.startsWith('+')) {
      className = 'text-green-400 bg-green-900/20';
      prefix = '+';
    } else if (line.startsWith('-')) {
      className = 'text-red-400 bg-red-900/20';
      prefix = '-';
    } else if (line.startsWith('@@')) {
      className = 'text-accent-blue font-bold bg-accent-blue/5';
    }
    return (
      <div key={i} className={`text-xs font-mono leading-snug px-2 ${className}`}
           style={{ minHeight: '1.25rem' }}>
        <span className="select-none inline-block w-4 text-dark-muted text-right mr-1">
          {className.includes('text-accent-blue') ? '@' : prefix}
        </span>
        {line || ' '}
      </div>
    );
  });
}

function renderSideBySideDiff(text: string) {
  // Parse into left (old) and right (new) chunks per hunk
  const lines = text.split('\n');
  const rows: { left: string; right: string; type: 'context' | 'delete' | 'add' | 'header' }[] = [];

  let currentHunk: { leftLines: string[]; rightLines: string[] } | null = null;

  for (const line of lines) {
    if (line.startsWith('@@')) {
      // Flush previous hunk
      if (currentHunk) alignAndPush(rows, currentHunk);
      currentHunk = { leftLines: [], rightLines: [] };
      rows.push({ left: line, right: line, type: 'header' });
    } else if (line.startsWith('---') || line.startsWith('+++')) {
      rows.push({ left: '', right: line, type: 'header' });
    } else if (line.startsWith('-') && currentHunk) {
      currentHunk.leftLines.push(line.substring(1));
    } else if (line.startsWith('+') && currentHunk) {
      currentHunk.rightLines.push(line.substring(1));
    } else if (currentHunk) {
      alignAndPush(rows, currentHunk);
      rows.push({ left: line, right: line, type: 'context' });
    } else {
      rows.push({ left: line, right: line, type: 'context' });
    }
  }
  if (currentHunk) alignAndPush(rows, currentHunk);

  // Side-by-side header
  return (
    <div>
      <div className="flex border-b border-dark-border text-xs font-bold text-dark-muted">
        <div className="flex-1 px-2 py-0.5 border-r border-dark-border">Old</div>
        <div className="flex-1 px-2 py-0.5">New</div>
      </div>
      {rows.map((row, i) => (
        <div key={i} className="flex" style={{ minHeight: '1.25rem' }}>
          <div className={`flex-1 px-2 border-r border-dark-border/30 font-mono text-xs leading-snug ${
            row.type === 'delete' ? 'bg-red-900/20 text-red-400' :
            row.type === 'header' ? 'bg-accent-blue/5 text-accent-blue' : 'text-dark-fg'
          }`}>{row.left || ' '}</div>
          <div className={`flex-1 px-2 font-mono text-xs leading-snug ${
            row.type === 'add' ? 'bg-green-900/20 text-green-400' :
            row.type === 'header' ? 'bg-accent-blue/5 text-accent-blue' : 'text-dark-fg'
          }`}>{row.right || ' '}</div>
        </div>
      ))}
    </div>
  );
}

function alignAndPush(
  rows: { left: string; right: string; type: 'context' | 'delete' | 'add' | 'header' }[],
  hunk: { leftLines: string[]; rightLines: string[] }
) {
  const maxLen = Math.max(hunk.leftLines.length, hunk.rightLines.length);
  for (let i = 0; i < maxLen; i++) {
    const left = i < hunk.leftLines.length ? hunk.leftLines[i] : '';
    const right = i < hunk.rightLines.length ? hunk.rightLines[i] : '';
    rows.push({
      left: i < hunk.leftLines.length ? '- ' + left : '',
      right: i < hunk.rightLines.length ? '+ ' + right : '',
      type: i < hunk.leftLines.length && i < hunk.rightLines.length ? 'context'
        : i < hunk.leftLines.length ? 'delete' : 'add'
    });
  }
  hunk.leftLines = [];
  hunk.rightLines = [];
}

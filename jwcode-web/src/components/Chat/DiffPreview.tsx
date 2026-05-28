import { memo } from 'react';

interface DiffPreviewProps {
  filePath?: string;
  content?: string;
  language?: string;
}

/**
 * Diff preview component that shows file edit results with syntax highlighting.
 * Renders a unified diff view when possible, otherwise shows raw content.
 */
export const DiffPreview = memo(function DiffPreview({ filePath, content, language }: DiffPreviewProps) {
  if (!content && !filePath) return null;

  const detectLanguage = (path?: string): string => {
    if (!path) return language || 'plaintext';
    const ext = path.split('.').pop()?.toLowerCase();
    const langMap: Record<string, string> = {
      java: 'java', ts: 'typescript', tsx: 'typescript', js: 'javascript',
      jsx: 'javascript', py: 'python', go: 'go', rs: 'rust', cpp: 'cpp',
      c: 'c', h: 'c', css: 'css', html: 'html', json: 'json', xml: 'xml',
      yml: 'yaml', yaml: 'yaml', md: 'markdown', sql: 'sql', sh: 'bash',
      ps1: 'powershell', properties: 'properties',
    };
    return langMap[ext || ''] || 'plaintext';
  };

  // Parse unified diff lines for coloring
  const renderDiffLines = (text: string) => {
    const lines = text.split('\n');
    return lines.map((line, i) => {
      let className = 'text-dark-fg';
      if (line.startsWith('+++') || line.startsWith('---')) {
        className = 'text-dark-muted font-bold';
      } else if (line.startsWith('+')) {
        className = 'text-green-400 bg-green-900/20';
      } else if (line.startsWith('-')) {
        className = 'text-red-400 bg-red-900/20';
      } else if (line.startsWith('@@')) {
        className = 'text-accent-blue font-bold';
      }
      return (
        <div key={i} className={`text-xs font-mono leading-relaxed px-2 ${className}`}>
          {line || ' '}
        </div>
      );
    });
  };

  const isDiff = content?.includes('@@') || content?.includes('+++') || content?.includes('---');

  return (
    <div className="mt-2 border border-dark-border rounded-lg overflow-hidden">
      <div className="flex items-center gap-2 px-3 py-1.5 bg-dark-surface border-b border-dark-border">
        <span className="text-accent-purple text-xs">📝</span>
        <span className="text-xs font-mono text-dark-fg truncate">
          {filePath || 'file'}
        </span>
        <span className="text-xs text-dark-muted ml-auto">
          {detectLanguage(filePath)}
        </span>
      </div>
      <div className="bg-dark-bg overflow-x-auto max-h-64 overflow-y-auto">
        {isDiff ? (
          renderDiffLines(content || '')
        ) : (
          <pre className="text-xs font-mono p-2 text-dark-fg whitespace-pre-wrap">
            {content || '(no content)'}
          </pre>
        )}
      </div>
    </div>
  );
});

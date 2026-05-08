import { useEffect, useRef } from 'react';
import ReactMarkdown from 'react-markdown';
import Prism from 'prismjs';
import 'prismjs/components/prism-javascript';
import 'prismjs/components/prism-typescript';
import 'prismjs/components/prism-jsx';
import 'prismjs/components/prism-tsx';
import 'prismjs/components/prism-css';
import 'prismjs/components/prism-json';
import 'prismjs/components/prism-bash';
import 'prismjs/components/prism-python';
import 'prismjs/components/prism-java';
import 'prismjs/components/prism-markdown';
import 'prismjs/components/prism-yaml';
import 'prismjs/components/prism-sql';
import { CheckCircle, Copy } from 'lucide-react';
import { useState } from 'react';

interface MarkdownRendererProps {
  content: string;
  className?: string;
}

// Language display names
const LANG_NAMES: Record<string, string> = {
  js: 'JavaScript',
  ts: 'TypeScript',
  jsx: 'JSX',
  tsx: 'TSX',
  py: 'Python',
  python: 'Python',
  java: 'Java',
  bash: 'Bash',
  shell: 'Shell',
  css: 'CSS',
  json: 'JSON',
  yaml: 'YAML',
  yml: 'YAML',
  sql: 'SQL',
  md: 'Markdown',
  markdown: 'Markdown',
  xml: 'XML',
  html: 'HTML',
};

// Code block with copy button
function CodeBlock({ language, code }: { language: string; code: string }) {
  const [copied, setCopied] = useState(false);
  const codeRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (codeRef.current) {
      Prism.highlightElement(codeRef.current);
    }
  }, [code, language]);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy:', err);
    }
  };

  const langDisplay = LANG_NAMES[language] || language.toUpperCase();

  return (
    <div className="relative group rounded-lg overflow-hidden border border-dark-border my-2">
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-1.5 bg-dark-surface border-b border-dark-border">
        <span className="text-xs text-dark-muted font-mono">{langDisplay}</span>
        <button
          onClick={handleCopy}
          className="flex items-center gap-1 text-xs px-2 py-1 rounded hover:bg-dark-hover transition-colors"
        >
          {copied ? (
            <>
              <CheckCircle size={12} className="text-accent-green" />
              <span className="text-accent-green">已复制</span>
            </>
          ) : (
            <>
              <Copy size={12} />
              <span>复制</span>
            </>
          )}
        </button>
      </div>
      
      {/* Code content */}
      <pre className="bg-dark-bg p-3 overflow-x-auto text-sm">
        <code ref={codeRef} className={`language-${language}`}>
          {code}
        </code>
      </pre>
    </div>
  );
}

export function MarkdownRenderer({ content, className = '' }: MarkdownRendererProps) {
  // Ensure line breaks are preserved - process escaped newlines first
  const processedContent = (content || '')
    .replace(/\\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n');
  
  return (
    <div className={`markdown-content ${className}`} style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
      <ReactMarkdown
        components={{
          // Custom code block renderer
          code({ className, children, ...props }) {
            const match = /language-(\w+)/.exec(className || '');
            const code = String(children).replace(/\n$/, '');
            
            // If it's a code block (has language), use custom renderer
            if (match && match[1]) {
              return <CodeBlock language={match[1]} code={code} />;
            }
            
            // Inline code
            return (
              <code className="bg-dark-hover px-1.5 py-0.5 rounded text-sm font-mono text-accent-blue" {...props}>
                {children}
              </code>
            );
          },
          
          // Custom link renderer
          a({ href, children }) {
            return (
              <a 
                href={href} 
                target="_blank" 
                rel="noopener noreferrer"
                className="text-accent-blue hover:underline"
              >
                {children}
              </a>
            );
          },
          
          // Custom table renderer
          table({ children }) {
            return (
              <div className="overflow-x-auto my-2">
                <table className="w-full border-collapse border border-dark-border">
                  {children}
                </table>
              </div>
            );
          },
          
          th({ children }) {
            return (
              <th className="border border-dark-border px-3 py-2 text-left bg-dark-hover font-medium">
                {children}
              </th>
            );
          },
          
          td({ children }) {
            return (
              <td className="border border-dark-border px-3 py-2">
                {children}
              </td>
            );
          },
          
          // Custom paragraph - compact spacing
          p({ children }) {
            return (
              <p className="mb-0.5 leading-snug last:mb-0">
                {children}
              </p>
            );
          },
          
          // Custom blockquote
          blockquote({ children }) {
            return (
              <blockquote className="border-l-4 border-accent-blue pl-3 my-1 text-dark-muted italic">
                {children}
              </blockquote>
            );
          },
          
          // Custom heading with anchor
          h1({ children }) {
            return (
              <h1 className="text-2xl font-bold mt-3 mb-1 text-dark-text border-b border-dark-border pb-1">
                {children}
              </h1>
            );
          },
          
          h2({ children }) {
            return (
              <h2 className="text-xl font-semibold mt-3 mb-1 text-dark-text border-b border-dark-border pb-1">
                {children}
              </h2>
            );
          },
          
          h3({ children }) {
            return (
              <h3 className="text-lg font-semibold mt-2 mb-1 text-dark-text">
                {children}
              </h3>
            );
          },
        }}
      >
        {processedContent}
      </ReactMarkdown>
    </div>
  );
}

export default MarkdownRenderer;

import { memo, useState } from 'react';

interface ExpandableResultProps {
  text: string;
  maxLength?: number;
  preformatted?: boolean;
}

export const ExpandableResult = memo(function ExpandableResult({
  text,
  maxLength = 300,
  preformatted = false,
}: ExpandableResultProps) {
  const [expanded, setExpanded] = useState(false);
  const needsExpand = text.length > maxLength;

  if (!needsExpand) {
    return preformatted
      ? <pre className="text-xs font-mono bg-dark-bg p-1.5 rounded overflow-x-auto mt-1 text-accent-green">{text}</pre>
      : <div className="text-dark-text leading-snug">{text}</div>;
  }

  return (
    <div>
      {preformatted ? (
        <pre className="text-xs font-mono bg-dark-bg p-1.5 rounded overflow-x-auto mt-1 text-accent-green">
          {expanded ? text : text.slice(0, maxLength) + '...'}
        </pre>
      ) : (
        <div className="text-dark-text leading-snug">
          {expanded ? text : text.slice(0, maxLength) + '...'}
        </div>
      )}
      <button
        onClick={(e) => { e.stopPropagation(); setExpanded(!expanded); }}
        className="mt-1 text-[10px] px-2 py-0.5 rounded bg-dark-hover text-accent-blue hover:bg-dark-border transition-colors"
      >
        {expanded ? '收起' : '展开'}
      </button>
    </div>
  );
});

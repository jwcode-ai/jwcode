/**
 * DiffDisplay -- renders unified diff text with syntax-colored lines.
 *
 * Parses standard unified diff format (---/+++/@@) and renders each line
 * with appropriate foreground and background colors. Large diffs are
 * collapsed by default with a summary banner.
 */
import { memo, useState, useMemo } from 'react';
import { Box, Text } from 'ink';
import { t } from '../theme.js';

// ---- Helpers ----

type DiffLineType = 'fileHeader' | 'hunkHeader' | 'addition' | 'deletion' | 'placeholder' | 'context';

interface DiffLine {
  type: DiffLineType;
  text: string;
  raw: string;
}

const RE_FILE_HEADER  = /^(---|\+\+\+) /;
const RE_HUNK_HEADER  = /^@@ /;
const RE_PLACEHOLDER  = /^\\(?!.*\\$)/;
const RE_ADDLINE_COLLAPSE = /^\+/;
const RE_DELLINE_COLLAPSE = /^\-/;

const COLLAPSE_THRESHOLD = 30;

/**
 * Parse raw unified diff text into classified lines.
 */
function parseDiff(text: string): DiffLine[] {
  const lines = text.split('\n');
  const result: DiffLine[] = [];

  for (const raw of lines) {
    let type: DiffLineType = 'context';

    if (RE_FILE_HEADER.test(raw)) {
      type = 'fileHeader';
    } else if (RE_HUNK_HEADER.test(raw)) {
      type = 'hunkHeader';
    } else if (raw.startsWith('+')) {
      type = 'addition';
    } else if (raw.startsWith('-')) {
      type = 'deletion';
    } else if (RE_PLACEHOLDER.test(raw)) {
      type = 'placeholder';
    }

    // Extract display text: strip leading +/- for colored rendering
    let text = raw;
    if (type === 'addition' || type === 'deletion') {
      text = raw.slice(1); // remove +/- prefix for display (we add it back)
    }

    result.push({ type, text, raw });
  }

  return result;
}

function countByType(lines: DiffLine[], type: DiffLineType): number {
  return lines.filter(l => l.type === type).length;
}

// ---- Component ----

interface Props {
  /** Raw unified diff text */
  content: string;
  /** Terminal width hint for line wrapping */
  terminalCols?: number;
  /** Whether to start collapsed (default: auto based on line count) */
  startCollapsed?: boolean;
}

export const DiffDisplay = memo(function DiffDisplay({
  content,
  terminalCols = 120,
  startCollapsed,
}: Props) {
  const lines = useMemo(() => parseDiff(content), [content]);
  const totalLines = lines.length;
  const addCount = useMemo(() => countByType(lines, 'addition'), [lines]);
  const delCount = useMemo(() => countByType(lines, 'deletion'), [lines]);

  const shouldStartCollapsed = startCollapsed ?? (totalLines > COLLAPSE_THRESHOLD);
  const [collapsed, setCollapsed] = useState(shouldStartCollapsed);
  const toggle = () => setCollapsed(c => !c);

  const maxLineWidth = terminalCols - 6;

  // File name extraction for the summary header
  const fileNames = useMemo(() => {
    const names: string[] = [];
    for (const line of lines) {
      if (line.type === 'fileHeader' && line.raw.startsWith('+++')) {
        const name = line.raw.replace(/^\+\+\+ (a|b)\//, '').trim();
        if (name && name !== '/dev/null') names.push(name);
      }
    }
    return [...new Set(names)];
  }, [lines]);

  // Summary line
  const summaryText = fileNames.length > 0
    ? fileNames.join(', ') + `  \u2192  +${addCount}/-${delCount}`
    : `Diff  \u2192  +${addCount}/-${delCount}`;

  if (totalLines === 0) return null;

  // Visible lines: when collapsed, show only first N lines
  const maxVisible = COLLAPSE_THRESHOLD;
  const visibleLines = collapsed ? lines.slice(0, maxVisible) : lines;
  const hiddenCount = totalLines - maxVisible;

  return (
    <Box flexDirection="column" paddingLeft={1}>
      {/* Collapse toggle + summary */}
      <Box>
        <Text color={t.primary} bold>
          {'  '}[{collapsed ? '+' : '-'}]{' '}
        </Text>
        <Text color={t.muted}>{summaryText}</Text>
      </Box>

      {/* Visible diff lines */}
      <Box flexDirection="column">
        {visibleLines.map((line, i) => (
          <RenderDiffLine key={i} line={line} maxWidth={maxLineWidth} />
        ))}
      </Box>

      {/* Collapse indicator */}
      {collapsed && hiddenCount > 0 && (
        <Box>
          <Text color={t.info} dimColor>
            {'    \u2026 '}{hiddenCount} more lines{' '}
          </Text>
          <Text color={t.primary} dimColor underline>
            [click or press to expand]
          </Text>
        </Box>
      )}
    </Box>
  );
});

// ---- Internal line renderer ----

function truncateLine(text: string, max: number): string {
  if (text.length <= max) return text;
  return text.slice(0, max - 3) + '...';
}

const RenderDiffLine = memo(function RenderDiffLine({
  line,
  maxWidth,
}: {
  line: DiffLine;
  maxWidth: number;
}) {
  const display = truncateLine(line.text, maxWidth);

  switch (line.type) {
    case 'fileHeader':
      return (
        <Box>
          <Text color={t.diffFileHeader} bold dimColor={false}>
            {'    '}{line.raw.slice(0, maxWidth)}
          </Text>
        </Box>
      );

    case 'hunkHeader':
      return (
        <Box>
          <Text color={t.diffHeader} dimColor>
            {'    '}{line.raw.slice(0, maxWidth)}
          </Text>
        </Box>
      );

    case 'addition':
      return (
        <Box>
          <Text color={t.diffAdded} backgroundColor={t.diffAddedBg}>
            {'  + '}{display}
          </Text>
        </Box>
      );

    case 'deletion':
      return (
        <Box>
          <Text color={t.diffRemoved} backgroundColor={t.diffRemovedBg}>
            {'  - '}{display}
          </Text>
        </Box>
      );

    case 'placeholder':
      return (
        <Box>
          <Text color={t.diffPlaceholder} dimColor>
            {'    '}{line.raw.slice(0, maxWidth)}
          </Text>
        </Box>
      );

    case 'context':
    default:
      return (
        <Box>
          <Text color={t.muted} dimColor>
            {'    '}{display}
          </Text>
        </Box>
      );
  }
});


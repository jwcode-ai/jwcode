/**
 * Markdown renderer for Ink TUI — 对标 Codex 的 pulldown-cmark + syntect。
 *
 * Uses the `marked` library (already a dependency) to parse Markdown into
 * tokens, then renders each token with Ink's Text/Box primitives.
 *
 * Supported: headings, bold, italic, inline code, code blocks (with lang),
 *            links (URL shown), unordered lists, blockquotes, horizontal rules.
 */
import { Box, Text } from 'ink';
import { memo } from 'react';
import { t } from '../theme.js';

// marked is already in package.json dependencies
let markedLib: typeof import('marked') | null = null;

function getMarked() {
  if (!markedLib) {
    try {
      markedLib = require('marked');
    } catch {
      return null; // graceful degradation
    }
  }
  return markedLib;
}

// Use marked's own Token type
type MarkedToken = ReturnType<typeof import('marked').Lexer['lex']> extends Array<infer T> ? T : never;

function renderInlineTokens(tokens: MarkedToken[], keyPrefix: string): React.ReactNode[] {
  const nodes: React.ReactNode[] = [];
  let i = 0;
  for (const raw of tokens) {
    const tok = raw as any;
    const k = keyPrefix + '-' + i++;
    switch (tok.type) {
      case 'strong':
        nodes.push(<Text key={k} bold>{renderPlainText(tok.tokens || [])}</Text>);
        break;
      case 'em':
        nodes.push(<Text key={k} italic>{renderPlainText(tok.tokens || [])}</Text>);
        break;
      case 'codespan':
        nodes.push(<Text key={k} color={t.warning} backgroundColor="#2d2d2d">{tok.text || ''}</Text>);
        break;
      case 'link': {
        const label = renderPlainText(tok.tokens || []);
        const href = tok.href || '';
        nodes.push(<Text key={k} color={t.info}>{label + ' (' + href + ')'}</Text>);
        break;
      }
      case 'text':
      default:
        nodes.push(<Text key={k}>{tok.text || tok.raw || ''}</Text>);
        break;
    }
  }
  return nodes;
}

function renderPlainText(tokens: MarkedToken[]): string {
  return tokens.map(tk => (tk as any).text || (tk as any).raw || '').join('');
}

interface MarkdownRendererProps {
  content: string;
  terminalCols: number;
}

export const MarkdownRenderer = memo(function MarkdownRenderer({
  content, terminalCols,
}: MarkdownRendererProps) {
  const md = getMarked();
  if (!md || !content) {
    return <Text>{content}</Text>;
  }

  let tokens: MarkedToken[] = [];
  try {
    const lexer = new md.Lexer();
    tokens = lexer.lex(content) as MarkedToken[];
  } catch {
    return <Text>{content}</Text>;
  }

  const lines: React.ReactNode[] = [];
  let idx = 0;

  for (const rawTok of tokens) {
    const k = 'md-' + idx++;
    const tok = rawTok as any;

    switch (tok.type) {
      case 'heading': {
        const depth = tok.depth || 1;
        const prefix = '#'.repeat(depth) + ' ';
        lines.push(
          <Box key={k} marginTop={depth === 1 ? 1 : 0}>
            <Text bold color={depth <= 2 ? t.primary : t.muted}>
              {prefix}{renderPlainText(tok.tokens || [])}
            </Text>
          </Box>
        );
        break;
      }

      case 'paragraph':
        lines.push(
          <Box key={k} paddingLeft={0}>
            <Text>{renderInlineTokens(tok.tokens || [], k)}</Text>
          </Box>
        );
        break;

      case 'code': {
        const lang = tok.lang || '';
        const code = tok.text || '';
        const maxWidth = Math.min(terminalCols - 4, 100);
        const codeLines = code.split('\n');
        lines.push(
          <Box key={k} flexDirection="column" marginY={0}>
            {lang && (
              <Box paddingLeft={1}>
                <Text dimColor>{'┌─ '}{lang}</Text>
              </Box>
            )}
            {codeLines.map((line: string, li: number) => (
              <Box key={k + '-l' + li} paddingLeft={2}>
                <Text color={t.muted} dimColor>{line.slice(0, maxWidth)}</Text>
              </Box>
            ))}
          </Box>
        );
        break;
      }

      case 'blockquote':
        lines.push(
          <Box key={k} flexDirection="column" paddingLeft={2}>
            {(tok.tokens || []).map((bt: any, bi: number) => (
              <Box key={k + '-b' + bi}>
                <Text dimColor>{'│'} </Text>
                <Text italic>{bt.text || renderPlainText(bt.tokens || [])}</Text>
              </Box>
            ))}
          </Box>
        );
        break;

      case 'list': {
        const items = tok.items || [];
        lines.push(
          <Box key={k} flexDirection="column">
            {items.map((item: any, ii: number) => {
              const bullet = tok.ordered
                ? ((tok.start || 1) + ii) + '. '
                : '  • ';
              return (
                <Box key={k + '-i' + ii} paddingLeft={1}>
                  <Text dimColor>{bullet}</Text>
                  <Box flexDirection="column">
                    {(item.tokens || []).map((it: any, i2: number) =>
                      it.type === 'text' ? (
                        <Text key={k + '-it' + i2}>{renderInlineTokens(it.tokens || [], k + '-it' + i2)}</Text>
                      ) : (
                        <Text key={k + '-ip' + i2}>{it.text || ''}</Text>
                      )
                    )}
                  </Box>
                </Box>
              );
            })}
          </Box>
        );
        break;
      }

      case 'hr':
        lines.push(
          <Box key={k}>
            <Text dimColor>{'─'.repeat(Math.min(terminalCols - 2, 60))}</Text>
          </Box>
        );
        break;

      case 'space':
        break;

      default:
        if (tok.raw) {
          lines.push(<Text key={k}>{tok.raw.trimEnd()}</Text>);
        }
        break;
    }
  }

  return (
    <Box flexDirection="column">
      {lines}
    </Box>
  );
});

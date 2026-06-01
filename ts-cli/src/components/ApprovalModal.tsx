/**
 * ApprovalModal — enhanced permission prompt with risk levels, countdown, preview.
 *
 * Features:
 * - Risk classification (CRITICAL/HIGH/MEDIUM/LOW) with color coding
 * - Countdown auto-approval timer (15s default)
 * - Tool name + command preview
 * - Arrow keys / 1/2 / y/n shortcuts
 */
import { useState, useEffect, useRef } from 'react';
import { Box, Text, useInput } from 'ink';

type RiskLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

interface Props {
  toolName: string;
  payload: string;
  onAllow: () => void;
  onDeny: () => void;
}

const COUNTDOWN_S = 15;

function classifyRisk(toolName: string, payload: string): { level: RiskLevel; reason: string } {
  const name = toolName.toLowerCase();

  if (/\b(rm|del|delete|drop|truncate|format|mkfs)\b/.test(name)) {
    return { level: 'CRITICAL', reason: 'Destructive — may delete data' };
  }
  if (/\b(bash|shell|exec|cmd|powershell|terminal)\b/.test(name)) {
    if (/\b(rm\s+-rf|sudo|chmod\s+777|curl.*\|\s*(ba)?sh|wget.*-O|>\/dev\/|mkfs)\b/i.test(payload)) {
      return { level: 'CRITICAL', reason: 'High-risk command — system-level operation' };
    }
    return { level: 'HIGH', reason: 'Shell command execution' };
  }
  if (/\b(write|edit|save|upload|deploy|publish)\b/i.test(name)) {
    return { level: 'HIGH', reason: 'File write operation' };
  }
  if (/\b(install|uninstall|npm|pip|cargo|gem|apt|brew)\b/i.test(name)) {
    return { level: 'HIGH', reason: 'Package manager operation' };
  }
  if (/\b(git)\b/.test(name) && /\b(push|force|hard\s*reset|rebase)\b/i.test(payload)) {
    return { level: 'HIGH', reason: 'Git destructive operation' };
  }
  if (/\b(http|fetch|curl|wget|api|request|download)\b/i.test(name)) {
    return { level: 'MEDIUM', reason: 'Network request' };
  }
  if (/\b(read|open|list|ls|dir|cat|view|search|find|grep|glob)\b/i.test(name)) {
    return { level: 'LOW', reason: 'Read-only operation' };
  }
  return { level: 'MEDIUM', reason: 'Tool invocation' };
}

const RISK_COLOR: Record<RiskLevel, string> = {
  CRITICAL: 'red',
  HIGH: 'yellow',
  MEDIUM: 'yellow',
  LOW: 'cyan',
};

const RISK_ICON: Record<RiskLevel, string> = {
  CRITICAL: '⛔',
  HIGH: '⚠️',
  MEDIUM: '⚡',
  LOW: '📋',
};

function extractPreview(toolName: string, payload: string): string {
  // Show command for shell tools
  if (/\b(bash|shell|exec|cmd|powershell|terminal)\b/i.test(toolName)) {
    return payload.slice(0, 200);
  }
  // Show file path + preview for write tools
  if (/\b(write|edit|save|create)\b/i.test(toolName)) {
    const match = payload.match(/(?:file_path|path|file)["\s:=]+([^\s",}]+)/i);
    if (match) return `File: ${match[1]}\n${payload.slice(0, 160)}`;
  }
  return payload.length > 200 ? payload.slice(0, 200) + '...' : payload;
}

export function ApprovalModal({ toolName, payload, onAllow, onDeny }: Props) {
  const [selected, setSelected] = useState(0); // 0=allow, 1=deny
  const [countdown, setCountdown] = useState(COUNTDOWN_S);
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const { level, reason } = classifyRisk(toolName, payload);
  const riskColor = RISK_COLOR[level];
  const riskIcon = RISK_ICON[level];
  const preview = extractPreview(toolName, payload);

  // Countdown timer
  useEffect(() => {
    setCountdown(COUNTDOWN_S);
    countdownRef.current = setInterval(() => {
      setCountdown(prev => {
        if (prev <= 1) {
          if (countdownRef.current) clearInterval(countdownRef.current);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => { if (countdownRef.current) clearInterval(countdownRef.current); };
  }, [toolName, payload]);

  // Auto-approve on countdown reach 0
  useEffect(() => {
    if (countdown === 0) onAllow();
  }, [countdown]);

  const countdownUrgent = countdown <= 5;

  useInput((_input, key) => {
    if (key.escape) { onDeny(); return; }
    if (key.upArrow || key.downArrow) { setSelected(prev => prev === 0 ? 1 : 0); return; }
    if (key.return) { if (selected === 0) onAllow(); else onDeny(); return; }
    if (_input === '1') { onAllow(); return; }
    if (_input === '2') { onDeny(); return; }
    if (_input === 'y' || _input === 'Y') { onAllow(); return; }
    if (_input === 'n' || _input === 'N') { onDeny(); return; }
  });

  const borderColor = level === 'CRITICAL' ? 'red' : level === 'HIGH' ? 'yellow' : 'yellow';

  return (
    <Box flexDirection="column" borderStyle="round" borderColor={borderColor} paddingX={2} paddingY={1} marginTop={1}>
      {/* Header: risk badge + tool name */}
      <Box marginBottom={1}>
        <Text color={riskColor}>{riskIcon} </Text>
        <Text bold color={riskColor}>{level}</Text>
        <Text dimColor> — {reason}</Text>
      </Box>

      {/* Tool name */}
      <Box marginBottom={1}>
        <Text dimColor>Tool: </Text>
        <Text color="cyan" bold>{toolName}</Text>
      </Box>

      {/* Command/args preview */}
      {preview ? (
        <Box flexDirection="column" marginBottom={1} paddingX={1}>
          <Box>
            <Text dimColor>{'┌─ preview ─'.slice(0, preview.length > 0 ? 12 : 0)}</Text>
          </Box>
          <Box>
            <Text dimColor>│ </Text>
            <Text>{preview}</Text>
          </Box>
          <Box>
            <Text dimColor>{'└' + '─'.repeat(Math.min(10, preview.length))}</Text>
          </Box>
        </Box>
      ) : null}

      {/* CRITICAL warning */}
      {level === 'CRITICAL' && (
        <Box marginBottom={1}>
          <Text color="red" bold>⚠ This may cause irreversible changes — verify carefully</Text>
        </Box>
      )}

      {/* Options */}
      <Box flexDirection="column" marginLeft={2} marginBottom={1}>
        <Box>
          <Text color={selected === 0 ? 'green' : undefined} bold={selected === 0}>
            {selected === 0 ? ' ❯' : '  '} 1. Allow
          </Text>
        </Box>
        <Box>
          <Text color={selected === 1 ? 'red' : undefined} bold={selected === 1}>
            {selected === 1 ? ' ❯' : '  '} 2. Deny
          </Text>
        </Box>
      </Box>

      {/* Countdown bar */}
      <Box marginBottom={1}>
        <Text dimColor>Auto-approve in: </Text>
        <Text color={countdownUrgent ? 'red' : 'green'} bold={countdownUrgent}>
          {countdown}s
        </Text>
        <Text>  </Text>
        <Text color={countdownUrgent ? 'red' : 'green'}>
          {'█'.repeat(Math.ceil(countdown / COUNTDOWN_S * 10))}
          {'░'.repeat(10 - Math.ceil(countdown / COUNTDOWN_S * 10))}
        </Text>
      </Box>

      {/* Help */}
      <Box>
        <Text dimColor>1/2/y/n to decide · ↑↓ to select · Enter to confirm · Esc to deny</Text>
      </Box>
    </Box>
  );
}

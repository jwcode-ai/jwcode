/**
 * ApprovalModal — numbered option list style, inspired by Claude Code plan approval prompt.
 */
import { useState, useEffect, useRef } from "react";
import { Box, Text, useInput } from "ink";
import { t } from "../theme.js";

type RiskLevel = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";

interface Props {
  toolName: string;
  payload: string;
  onAllow: () => void;
  onDeny: () => void;
  onAllowSession: () => void;
  onAutoMode: () => void;
  queuePosition?: { current: number; total: number };
}

const COUNTDOWN_S = 15;

function classifyRisk(toolName: string, payload: string): { level: RiskLevel; reason: string } {
  const name = toolName.toLowerCase();
  if (/\b(rm|del|delete|drop|truncate|format|mkfs)\b/.test(name)) {
    return { level: "CRITICAL", reason: "Destructive - may delete data" };
  }
  if (/\b(bash|shell|exec|cmd|powershell|terminal)\b/.test(name)) {
    if (/\b(rm\s+-rf|sudo|chmod\s+777|curl.*\|\s*(ba)?sh|wget.*-O|>\/dev\/|mkfs)\b/i.test(payload)) {
      return { level: "CRITICAL", reason: "High-risk command - system-level operation" };
    }
    return { level: "HIGH", reason: "Shell command execution" };
  }
  if (/\b(write|edit|save|upload|deploy|publish)\b/i.test(name)) {
    return { level: "HIGH", reason: "File write operation" };
  }
  if (/\b(install|uninstall|npm|pip|cargo|gem|apt|brew)\b/i.test(name)) {
    return { level: "HIGH", reason: "Package manager operation" };
  }
  if (/\b(git)\b/.test(name) && /\b(push|force|hard\s*reset|rebase)\b/i.test(payload)) {
    return { level: "HIGH", reason: "Git destructive operation" };
  }
  if (/\b(http|fetch|curl|wget|api|request|download)\b/i.test(name)) {
    return { level: "MEDIUM", reason: "Network request" };
  }
  if (/\b(read|open|list|ls|dir|cat|view|search|find|grep|glob)\b/i.test(name)) {
    return { level: "LOW", reason: "Read-only operation" };
  }
  return { level: "MEDIUM", reason: "Tool invocation" };
}

const RISK_COLOR: Record<RiskLevel, string> = {
  CRITICAL: t.error,
  HIGH: t.warning,
  MEDIUM: t.warning,
  LOW: t.info,
};

const RISK_ICON: Record<RiskLevel, string> = {
  CRITICAL: "!",
  HIGH: "!",
  MEDIUM: "*",
  LOW: "~",
};

function extractPreview(toolName: string, payload: string): string {
  if (/\b(bash|shell|exec|cmd|powershell|terminal)\b/i.test(toolName)) {
    return payload.slice(0, 200);
  }
  if (/\b(write|edit|save|create)\b/i.test(toolName)) {
    const match = payload.match(/(?:file_path|path|file)["\s:=]+([^\s",}]+)/i);
    if (match) return "File: " + match[1] + "\n" + payload.slice(0, 160);
  }
  return payload.length > 200 ? payload.slice(0, 200) + "..." : payload;
}

export function ApprovalModal({ toolName, payload, onAllow, onDeny, onAllowSession, onAutoMode, queuePosition }: Props) {
  const [selected, setSelected] = useState(0);
  const [countdown, setCountdown] = useState(COUNTDOWN_S);
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const { level, reason } = classifyRisk(toolName, payload);
  const riskColor = RISK_COLOR[level];
  const preview = extractPreview(toolName, payload);

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

  useEffect(() => {
    if (countdown === 0) onAllow();
  }, [countdown, onAllow]);

  const countdownUrgent = countdown <= 5;

  const options = [
    { label: "Allow — execute this command", hint: "Press Enter or y to confirm", action: onAllow },
    { label: "Deny — cancel this operation", hint: "Press Esc or n to cancel", action: onDeny },
    { label: "Allow always this session", hint: "Don't ask again for " + toolName, action: onAllowSession },
    { label: "Auto mode — allow all hooks", hint: "All future hooks will be auto-approved", action: onAutoMode },
  ];

  const execSelected = () => options[selected]!.action();

  useInput((_input, key) => {
    if (key.escape) { onDeny(); return; }
    if (key.upArrow) { setSelected(prev => prev === 0 ? 3 : prev - 1); return; }
    if (key.downArrow) { setSelected(prev => prev === 3 ? 0 : prev + 1); return; }
    if (key.return) { execSelected(); return; }
    if (_input === "1") { onAllow(); return; }
    if (_input === "2") { onDeny(); return; }
    if (_input === "3") { onAllowSession(); return; }
    if (_input === "4") { onAutoMode(); return; }
    if (_input === "y" || _input === "Y") { onAllow(); return; }
    if (_input === "n" || _input === "N") { onDeny(); return; }
  });

  const borderColor = level === "CRITICAL" ? t.error : t.warning;

  return (
    <Box
      flexDirection="column"
      borderStyle="round"
      borderColor={borderColor}
      marginTop={1}
    >
      {/* Title row */}
      <Box paddingLeft={1} paddingRight={1} justifyContent="space-between">
        <Box gap={1}>
          <Text bold>Permission Required</Text>
          <Text dimColor>{"· "}{toolName}</Text>
          {queuePosition && queuePosition.total > 1 && (
            <Text dimColor>({queuePosition.current}/{queuePosition.total})</Text>
          )}
        </Box>
        <Text color={countdownUrgent ? t.error : t.muted}>
          Auto {countdown}s
        </Text>
      </Box>

      {/* Risk description */}
      <Box paddingLeft={1} paddingRight={1}>
        <Text color={riskColor} bold={level === "CRITICAL"}>
          {RISK_ICON[level]} {level}
        </Text>
        <Text dimColor> — {reason}</Text>
      </Box>

      {/* Preview */}
      {preview ? (
        <Box flexDirection="column" paddingLeft={1} paddingRight={1} marginTop={1}>
          <Box marginLeft={2}>
            <Text color={t.tool}>{preview}</Text>
          </Box>
        </Box>
      ) : null}

      {/* Critical warning */}
      {level === "CRITICAL" && (
        <Box paddingLeft={1} paddingRight={1} marginTop={1}>
          <Text color={t.error}>This may cause irreversible changes — verify carefully</Text>
        </Box>
      )}

      {/* Options */}
      <Box flexDirection="column" marginTop={1}>
        {options.map((opt, i) => (
          <Box key={i} flexDirection="column" paddingLeft={1} paddingRight={1}>
            <Box>
              <Text color={selected === i ? t.brand : t.muted}>
                {selected === i ? "❯" : " "} {i + 1}. {opt.label}
              </Text>
            </Box>
            <Box marginLeft={4}>
              <Text dimColor>{opt.hint}</Text>
            </Box>
          </Box>
        ))}
      </Box>

      {/* Footer */}
      <Box paddingLeft={1} paddingRight={1} marginTop={1}>
        <Text dimColor>
          1/2/3/4 to select · ↑↓ to navigate · Enter to confirm · Esc to deny
        </Text>
      </Box>
    </Box>
  );
}

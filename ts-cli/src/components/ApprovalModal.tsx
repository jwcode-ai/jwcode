/**
 * ApprovalModal — numbered option list style, i18n support.
 * Language auto-detected from LANG/LC_ALL env var.
 */
import { useState, useEffect, useRef } from "react";
import { Box, Text, useInput } from "ink";
import { t as th } from "../theme.js";
import { t as tr } from "../locale.js";

type RiskLevel = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";

interface Props {
  toolName: string;
  payload: string;
  onAllow: () => void;
  onDeny: () => void;
  onAllowSession: () => void;
  onAutoMode: () => void;
}

const COUNTDOWN_S = 15;

function classifyRisk(toolName: string, payload: string): { level: RiskLevel; reason: string } {
  const name = toolName.toLowerCase();
  if (/\b(rm|del|delete|drop|truncate|format|mkfs)\b/.test(name)) {
    return { level: "CRITICAL", reason: tr("riskDestructive") };
  }
  if (/\b(bash|shell|exec|cmd|powershell|terminal)\b/.test(name)) {
    if (/\b(rm\s+-rf|sudo|chmod\s+777|curl.*\|\s*(ba)?sh|wget.*-O|>\/dev\/|mkfs)\b/i.test(payload)) {
      return { level: "CRITICAL", reason: tr("riskHighCmd") };
    }
    return { level: "HIGH", reason: tr("riskShell") };
  }
  if (/\b(write|edit|save|upload|deploy|publish)\b/i.test(name)) {
    return { level: "HIGH", reason: tr("riskFileWrite") };
  }
  if (/\b(install|uninstall|npm|pip|cargo|gem|apt|brew)\b/i.test(name)) {
    return { level: "HIGH", reason: tr("riskPackageMgr") };
  }
  if (/\b(git)\b/.test(name) && /\b(push|force|hard\s*reset|rebase)\b/i.test(payload)) {
    return { level: "HIGH", reason: tr("riskGitDestructive") };
  }
  if (/\b(http|fetch|curl|wget|api|request|download)\b/i.test(name)) {
    return { level: "MEDIUM", reason: tr("riskNetwork") };
  }
  if (/\b(read|open|list|ls|dir|cat|view|search|find|grep|glob)\b/i.test(name)) {
    return { level: "LOW", reason: tr("riskReadOnly") };
  }
  return { level: "MEDIUM", reason: tr("riskTool") };
}

const RISK_COLOR: Record<RiskLevel, string> = {
  CRITICAL: th.error,
  HIGH: th.warning,
  MEDIUM: th.warning,
  LOW: th.info,
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
    if (match) return tr("previewFile") + match[1] + "\n" + payload.slice(0, 160);
  }
  return payload.length > 200 ? payload.slice(0, 200) + "..." : payload;
}

export function ApprovalModal({ toolName, payload, onAllow, onDeny, onAllowSession, onAutoMode }: Props) {
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

  const levelLabelKey = `risk${level.charAt(0) + level.slice(1).toLowerCase()}` as const;
  const levelKey = level.toLowerCase() as "critical" | "high" | "medium" | "low";

  const options = [
    { label: tr("allowExec"), hint: tr("confirmHint"), action: onAllow },
    { label: tr("denyCancel"), hint: tr("denyHint"), action: onDeny },
    { label: tr("allowSession"), hint: tr("sessionHint", { tool: toolName }), action: onAllowSession },
    { label: tr("autoMode"), hint: tr("autoHint"), action: onAutoMode },
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

  const borderColor = level === "CRITICAL" ? th.error : th.warning;

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
          <Text bold>{tr("permissionRequired")}</Text>
          <Text dimColor>{"· "}{toolName}</Text>
        </Box>
        <Text color={countdownUrgent ? th.error : th.muted}>
          {tr("auto")} {countdown}{tr("s")}
        </Text>
      </Box>

      {/* Risk description */}
      <Box paddingLeft={1} paddingRight={1}>
        <Text color={riskColor} bold={level === "CRITICAL"}>
          {RISK_ICON[level]} {level === "CRITICAL" ? tr("critical") : level === "HIGH" ? tr("high") : level === "MEDIUM" ? tr("medium") : tr("low")}
        </Text>
        <Text dimColor> — {reason}</Text>
      </Box>

      {/* Preview */}
      {preview ? (
        <Box flexDirection="column" paddingLeft={1} paddingRight={1} marginTop={1}>
          <Box marginLeft={2}>
            <Text color={th.tool}>{preview}</Text>
          </Box>
        </Box>
      ) : null}

      {/* Critical warning */}
      {level === "CRITICAL" && (
        <Box paddingLeft={1} paddingRight={1} marginTop={1}>
          <Text color={th.error}>{tr("criticalWarning")}</Text>
        </Box>
      )}

      {/* Options */}
      <Box flexDirection="column" marginTop={1}>
        {options.map((opt, i) => (
          <Box key={i} flexDirection="column" paddingLeft={1} paddingRight={1}>
            <Box>
              <Text color={selected === i ? th.brand : th.muted}>
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
          {tr("selectHint")}
        </Text>
      </Box>
    </Box>
  );
}

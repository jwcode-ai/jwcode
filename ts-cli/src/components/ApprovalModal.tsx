/**
 * ApprovalModal -- codex-style: title question + risk band + bordered preview
 * (with path/keyword highlighting) + numbered options + countdown progress
 * bar + footer hint. Keeps risk classification and auto-allow countdown.
 */
import { useState, useEffect, useRef, useMemo } from "react";
import { Box, Text, useInput } from "ink";
import { t as th } from "../theme.js";
import { t as tr } from "../locale.js";
import { highlightPaths } from "./highlightPaths.js";

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
const ARROW = "\u25B6"; // ?
const BLOCK_FULL = "\u2588";
const BLOCK_EMPTY = "\u2591";
const BAR_LEN = 10;

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
  MEDIUM: th.info,
  LOW: th.muted,
};

const RISK_BG: Record<RiskLevel, string> = {
  CRITICAL: "red",
  HIGH: "yellow",
  MEDIUM: "cyan",
  LOW: "grey",
};

const RISK_LABEL: Record<RiskLevel, string> = {
  CRITICAL: tr("critical"),
  HIGH: tr("high"),
  MEDIUM: tr("medium"),
  LOW: tr("low"),
};

function questionFor(toolName: string): string {
  const n = toolName.toLowerCase();
  if (/\b(bash|shell|exec|cmd|powershell|terminal)\b/.test(n)) {
    return "Would you like to run the following command?";
  }
  if (/\b(write|edit|save|create|upload|deploy|publish)\b/.test(n)) {
    return "Would you like to apply the following file change?";
  }
  if (/\b(http|fetch|curl|wget|api|request|download)\b/.test(n)) {
    return "Would you like to allow the following network request?";
  }
  return "Would you like to allow the following tool?";
}

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

const KEYWORDS_RE = /\b(rm\s+-rf|sudo|chmod\s+777|mkfs|push|force|reset\s+--hard|rebase|del|delete|drop|truncate|format|>\s*\/dev\/sd|curl.*\|\s*(ba)?sh)\b/gi;

/** Renders preview text with paths (t.filePath) and dangerous keywords (t.error). */
function PreviewText({ text, highlightKeywords }: { text: string; highlightKeywords: boolean }) {
  const lines = useMemo(() => text.split("\n"), [text]);
  return (
    <Box flexDirection="column">
      {lines.map((line, li) => {
        // First split by keywords, then by paths within each piece.
        const pieces: { text: string; kw: boolean }[] = [];
        if (highlightKeywords) {
          KEYWORDS_RE.lastIndex = 0;
          let last = 0;
          let m: RegExpExecArray | null;
          while ((m = KEYWORDS_RE.exec(line)) !== null) {
            if (m.index > last) pieces.push({ text: line.slice(last, m.index), kw: false });
            pieces.push({ text: m[0], kw: true });
            last = m.index + m[0].length;
          }
          if (last < line.length) pieces.push({ text: line.slice(last), kw: false });
        } else {
          pieces.push({ text: line, kw: false });
        }
        return (
          <Text key={li}>
            {pieces.map((p, pi) =>
              p.kw
                ? <Text key={pi} color={th.error} bold>{p.text}</Text>
                : <PathSpan key={pi} text={p.text} />,
            )}
          </Text>
        );
      })}
    </Box>
  );
}

function PathSpan({ text }: { text: string }) {
  const segs = useMemo(() => highlightPaths(text), [text]);
  if (segs.length === 1 && !segs[0]!.isPath) return <>{text}</>;
  return (
    <>
      {segs.map((s, i) =>
        s.isPath
          ? <Text key={i} color={th.filePath}>{s.text}</Text>
          : <Text key={i}>{s.text}</Text>,
      )}
    </>
  );
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
  const filled = Math.max(0, Math.min(BAR_LEN, Math.round((countdown / COUNTDOWN_S) * BAR_LEN)));
  const bar = BLOCK_FULL.repeat(filled) + BLOCK_EMPTY.repeat(BAR_LEN - filled);

  const options = [
    { label: tr("allowExec"), hint: tr("confirmHint"), action: onAllow, key: "y" },
    { label: tr("denyCancel"), hint: tr("denyHint"), action: onDeny, key: "n" },
    { label: tr("allowSession"), hint: tr("sessionHint", { tool: toolName }), action: onAllowSession, key: "s" },
    { label: tr("autoMode"), hint: tr("autoHint"), action: onAutoMode, key: "r" },
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
    <Box flexDirection="column" borderStyle="round" borderColor={borderColor} marginTop={1}>
      {/* Title question */}
      <Box paddingLeft={1} paddingRight={1}>
        <Text bold color={th.warning}>{questionFor(toolName)}</Text>
      </Box>

      {/* Risk band */}
      <Box paddingLeft={1} paddingRight={1}>
        <Text backgroundColor={RISK_BG[level]} color="black" bold>
          {" RISK: " + RISK_LABEL[level] + " "}
        </Text>
        <Text dimColor>  {"-- " + reason}</Text>
      </Box>

      {/* Preview */}
      {preview ? (
        <Box flexDirection="column" paddingLeft={1} paddingRight={1} marginTop={1}>
          <Box borderStyle="single" borderColor={riskColor} paddingX={1}>
            <PreviewText text={preview} highlightKeywords={level === "CRITICAL" || level === "HIGH"} />
          </Box>
        </Box>
      ) : null}

      {/* Critical warning */}
      {level === "CRITICAL" && (
        <Box paddingLeft={1} paddingRight={1} marginTop={1}>
          <Text color={th.error}>{tr("criticalWarning")}</Text>
        </Box>
      )}

      {/* Numbered options */}
      <Box flexDirection="column" marginTop={1}>
        {options.map((opt, i) => {
          const isSel = selected === i;
          return (
            <Box key={i} flexDirection="column" paddingLeft={1} paddingRight={1}>
              <Box>
                <Text color={isSel ? th.brand : th.muted}>
                  {isSel ? ARROW + " " : "   "}
                </Text>
                <Text bold color={isSel ? th.brand : undefined}>
                  {i + 1}. {opt.label}
                </Text>
                <Text dimColor>  [{" + opt.key + "}]</Text>
              </Box>
              <Box marginLeft={5}>
                <Text dimColor>{opt.hint}</Text>
              </Box>
            </Box>
          );
        })}
      </Box>

      {/* Countdown + progress bar */}
      <Box paddingLeft={1} paddingRight={1} marginTop={1}>
        <Text color={countdownUrgent ? th.error : th.muted}>
          {tr("auto")} {countdown}{tr("s")}
        </Text>
        <Text dimColor>  </Text>
        <Text color={countdownUrgent ? th.error : th.muted}>{bar}</Text>
      </Box>

      {/* Footer hint */}
      <Box paddingLeft={1} paddingRight={1} marginTop={1}>
        <Text dimColor>{tr("selectHint")}</Text>
      </Box>
    </Box>
  );
}

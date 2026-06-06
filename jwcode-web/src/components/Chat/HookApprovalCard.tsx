import { memo, useState, useCallback, useEffect, useRef } from 'react';
import { Shield, Check, X, AlertTriangle, Clock } from 'lucide-react';
import { HookApprovalInfo } from '../../types';
import wsService from '../../services/websocket';
import { useHookApprovalStore } from '../../stores/useHookApprovalStore';

interface HookApprovalCardProps {
  approval: HookApprovalInfo;
  onResolved?: (approvalId: string, status: 'approved' | 'denied') => void;
}

type RiskLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

function classifyRisk(toolName: string, askPayload: string): { level: RiskLevel; reason: string } {
  const name = toolName.toLowerCase();
  if (/\b(rm|del|delete|drop|truncate|format|mkfs)\b/.test(name)) {
    return { level: 'CRITICAL', reason: '破坏性操作 - 可能删除数据' };
  }
  if (/\b(bash|shell|exec|cmd|powershell|terminal)\b/.test(name)) {
    if (/\b(rm\s+-rf|sudo|chmod\s+777|curl.*\|\s*(ba)?sh|wget.*-O|>\/dev\/|mkfs)\b/i.test(askPayload)) {
      return { level: 'CRITICAL', reason: '高危命令 - 包含系统级操作' };
    }
    return { level: 'HIGH', reason: 'Shell 命令执行' };
  }
  if (/\b(Write|Edit|save|upload|deploy|publish)\b/i.test(name)) {
    return { level: 'HIGH', reason: '文件写入操作' };
  }
  if (/\b(install|uninstall|npm|pip|cargo|gem|apt|brew|choco|yum|dnf)\b/i.test(name)) {
    return { level: 'HIGH', reason: '包管理操作' };
  }
  if (/\b(git|commit|push|merge|rebase)\b/i.test(name)) {
    const isDestructive = /\b(push|force|hard\s*reset|rebase)\b/i.test(askPayload);
    return { level: isDestructive ? 'HIGH' : 'MEDIUM', reason: isDestructive ? 'Git 强制操作' : 'Git 操作' };
  }
  if (/\b(http|fetch|curl|wget|api|request|download)\b/i.test(name)) {
    return { level: 'MEDIUM', reason: '网络请求' };
  }
  if (/\b(read|open|list|ls|dir|cat|view|search|find|grep|glob)\b/i.test(name)) {
    return { level: 'LOW', reason: '只读操作' };
  }
  return { level: 'MEDIUM', reason: '工具调用' };
}

const RISK_CONFIG: Record<RiskLevel, { bg: string; border: string; text: string; badge: string; icon: string }> = {
  CRITICAL: { bg: 'bg-red-900/20', border: 'border-red-500/40', text: 'text-red-400', badge: 'bg-red-500/20 text-red-400 border-red-500/30', icon: '⛔' },
  HIGH:     { bg: 'bg-orange-900/15', border: 'border-orange-500/30', text: 'text-orange-400', badge: 'bg-orange-500/20 text-orange-400 border-orange-500/30', icon: '⚠️' },
  MEDIUM:   { bg: 'bg-yellow-900/10', border: 'border-yellow-500/25', text: 'text-yellow-400', badge: 'bg-yellow-500/15 text-yellow-400 border-yellow-500/25', icon: '⚡' },
  LOW:      { bg: 'bg-blue-900/10', border: 'border-blue-500/25', text: 'text-blue-400', badge: 'bg-blue-500/15 text-blue-400 border-blue-500/25', icon: '📋' },
};

const COUNTDOWN_SECONDS = 15;

interface OptionItem {
  id: string;
  index: number;
  label: string;
  hint: string;
  action: () => void;
}

export const HookApprovalCard = memo(function HookApprovalCard({
  approval,
  onResolved,
}: HookApprovalCardProps) {
  const [resolving, setResolving] = useState(false);
  const [countdown, setCountdown] = useState(COUNTDOWN_SECONDS);
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const isPending = approval.status === 'pending';
  const { level, reason } = classifyRisk(approval.toolName, approval.askPayload);
  const rc = RISK_CONFIG[level];

  useEffect(() => {
    if (!isPending) return;
    setCountdown(COUNTDOWN_SECONDS);
    countdownRef.current = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          if (countdownRef.current) clearInterval(countdownRef.current);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => { if (countdownRef.current) clearInterval(countdownRef.current); };
  }, [isPending, approval.approvalId]);

  useEffect(() => {
    if (countdown === 0 && isPending && !resolving) {
      handleAllow();
    }
  }, [countdown]);

  const preview = extractPreview(approval.toolName, approval.askPayload);

  const handleAllow = useCallback(() => {
    if (!isPending || resolving) return;
    setResolving(true);
    if (countdownRef.current) clearInterval(countdownRef.current);
    wsService.send({
      type: 'hook_allow' as any,
      data: JSON.stringify({ approvalId: approval.approvalId }),
    });
    onResolved?.(approval.approvalId, 'approved');
  }, [isPending, resolving, approval.approvalId, onResolved]);

  const handleDeny = useCallback(() => {
    if (!isPending || resolving) return;
    setResolving(true);
    if (countdownRef.current) clearInterval(countdownRef.current);
    wsService.send({
      type: 'hook_deny' as any,
      data: JSON.stringify({ approvalId: approval.approvalId }),
    });
    onResolved?.(approval.approvalId, 'denied');
  }, [isPending, resolving, approval.approvalId, onResolved]);

  const handleAllowSession = useCallback(() => {
    if (!isPending || resolving) return;
    setResolving(true);
    if (countdownRef.current) clearInterval(countdownRef.current);
    const approvalStore = useHookApprovalStore.getState();
    approvalStore.addToSessionAllowList(approval.toolName);
    wsService.send({
      type: 'hook_allow' as any,
      data: JSON.stringify({ approvalId: approval.approvalId }),
    });
    onResolved?.(approval.approvalId, 'approved');
  }, [isPending, resolving, approval.approvalId, approval.toolName, onResolved]);

  const handleAutoMode = useCallback(() => {
    if (!isPending || resolving) return;
    setResolving(true);
    if (countdownRef.current) clearInterval(countdownRef.current);
    useHookApprovalStore.getState().setAutoMode(true);
    wsService.send({
      type: 'hook_allow' as any,
      data: JSON.stringify({ approvalId: approval.approvalId }),
    });
    onResolved?.(approval.approvalId, 'approved');
  }, [isPending, resolving, approval.approvalId, onResolved]);

  if (!isPending) {
    const isApproved = approval.status === 'approved';
    return (
      <div className={`flex items-center gap-2 px-3 py-2 rounded-lg text-xs ${
        isApproved
          ? 'bg-accent-green/10 border border-accent-green/30 text-accent-green'
          : 'bg-accent-red/10 border border-accent-red/30 text-accent-red'
      }`}>
        {isApproved ? (
          <><Check size={14} /><span>已允许：{approval.toolName}</span></>
        ) : (
          <><X size={14} /><span>已拒绝：{approval.toolName}</span></>
        )}
      </div>
    );
  }

  const countdownPct = (countdown / COUNTDOWN_SECONDS) * 100;
  const countdownUrgent = countdown <= 5;

  const options: OptionItem[] = [
    { id: 'allow', index: 1, label: 'Allow — execute this command', hint: 'Click or press Enter to confirm', action: handleAllow },
    { id: 'deny', index: 2, label: 'Deny — cancel this operation', hint: 'Click or press Esc to cancel', action: handleDeny },
    { id: 'allow_session', index: 3, label: 'Allow always this session', hint: `Don't ask again for ${approval.toolName}`, action: handleAllowSession },
    { id: 'auto_mode', index: 4, label: 'Auto mode — allow all hooks', hint: 'All future hooks will be auto-approved', action: handleAutoMode },
  ];

  return (
    <div className={`bg-dark-bg border rounded-lg overflow-hidden ${rc.border}`}>
      {/* Header: risk badge + tool name + countdown */}
      <div className={`flex items-center gap-2 px-3 py-2 ${rc.bg} border-b ${rc.border}`}>
        <span className={`px-1.5 py-0.5 rounded text-[10px] font-bold border ${rc.badge}`}>
          {rc.icon} {level}
        </span>
        <Shield size={14} className={rc.text} />
        <span className="text-xs font-medium text-dark-text">权限申请</span>
        <span className="text-[10px] text-dark-muted ml-auto">{reason}</span>

        {/* Countdown bar */}
        <div className="flex items-center gap-1.5 ml-2">
          <Clock size={11} className={countdownUrgent ? 'text-accent-red' : 'text-dark-muted'} />
          <span className={`text-[11px] font-mono ${countdownUrgent ? 'text-accent-red animate-pulse' : 'text-dark-muted'}`}>
            {countdown}s
          </span>
          <div className="w-10 h-1 bg-dark-border rounded-full overflow-hidden">
            <div
              className={`h-full rounded-full transition-all duration-1000 ${
                countdownUrgent ? 'bg-accent-red' : countdown <= 8 ? 'bg-accent-yellow' : 'bg-accent-green'
              }`}
              style={{ width: `${countdownPct}%` }}
            />
          </div>
        </div>
      </div>

      {/* Body */}
      <div className="px-3 py-2.5 space-y-2">
        {/* Tool name */}
        <div className="flex items-center gap-2 text-xs">
          <span className="text-dark-muted shrink-0">工具：</span>
          <code className="text-accent-blue font-mono text-xs bg-dark-surface px-1.5 py-0.5 rounded">
            {approval.toolName}
          </code>
        </div>

        {/* Command/Args preview */}
        {preview && (
          <div className="bg-dark-surface rounded border border-dark-border p-2 max-h-24 overflow-y-auto">
            <pre className="text-[11px] font-mono text-dark-text whitespace-pre-wrap break-all leading-relaxed">
              {preview}
            </pre>
          </div>
        )}

        {/* Alert for CRITICAL/HIGH risk */}
        {(level === 'CRITICAL' || level === 'HIGH') && (
          <div className={`flex items-center gap-1.5 text-[11px] px-2 py-1 rounded ${rc.bg} ${rc.text}`}>
            <AlertTriangle size={12} />
            <span>{level === 'CRITICAL' ? '此操作可能造成不可逆的影响，请仔细确认' : '请确认此操作为预期行为'}</span>
          </div>
        )}

        {/* Numbered option list */}
        <div className="space-y-0.5 pt-1">
          {options.map((opt) => (
            <button
              key={opt.id}
              onClick={opt.action}
              disabled={resolving}
              className="w-full text-left px-2 py-1.5 rounded hover:bg-dark-hover transition-colors disabled:opacity-50 group"
            >
              <div className="flex items-baseline gap-1.5">
                <span className="text-xs text-dark-muted font-mono shrink-0">
                  {opt.index}.
                </span>
                <span className="text-xs text-dark-text group-hover:text-white transition-colors">
                  {opt.label}
                </span>
              </div>
              <div className="text-[10px] text-dark-muted/60 ml-5 mt-0.5">
                {opt.hint}
              </div>
            </button>
          ))}
        </div>

        {/* Footer shortcut hints */}
        <div className="text-[10px] text-dark-muted/50 pt-0.5 border-t border-dark-border/50">
          Click to select · 1/2/3/4 keyboard shortcuts
        </div>
      </div>
    </div>
  );
});

function extractPreview(toolName: string, askPayload: string): string | null {
  if (/\b(bash|shell|exec|cmd|powershell|terminal)\b/i.test(toolName)) {
    return askPayload || null;
  }
  if (/\b(write|edit|save|create)\b/i.test(toolName)) {
    const fileMatch = askPayload.match(/(?:file_path|path|file)["\s:=]+([^\s",}]+)/i);
    if (fileMatch) {
      return `📄 ${fileMatch[1]}\n${askPayload.slice(0, 200)}`;
    }
    return askPayload.slice(0, 200) || null;
  }
  if (askPayload && askPayload.length > 0) {
    return askPayload.length > 200 ? askPayload.slice(0, 200) + '...' : askPayload;
  }
  return null;
}

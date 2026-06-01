import { memo, useState, useCallback, useEffect, useRef } from 'react';
import { Shield, Check, X, ChevronDown, AlertTriangle, Zap, Clock } from 'lucide-react';
import { HookApprovalInfo } from '../../types';
import wsService from '../../services/websocket';
import { useHookApprovalStore } from '../../stores/useHookApprovalStore';

interface HookApprovalCardProps {
  approval: HookApprovalInfo;
  onResolved?: (approvalId: string, status: 'approved' | 'denied') => void;
}

type RiskLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

// Heuristic risk classification by tool name
function classifyRisk(toolName: string, askPayload: string): { level: RiskLevel; reason: string } {
  const name = toolName.toLowerCase();

  // CRITICAL: destructive operations, shell execution, network access
  if (/\b(rm|del|delete|drop|truncate|format|mkfs)\b/.test(name)) {
    return { level: 'CRITICAL', reason: '破坏性操作 - 可能删除数据' };
  }
  if (/\b(bash|shell|exec|cmd|powershell|terminal)\b/.test(name)) {
    // Check for dangerous patterns in the payload
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

export const HookApprovalCard = memo(function HookApprovalCard({
  approval,
  onResolved,
}: HookApprovalCardProps) {
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [resolving, setResolving] = useState(false);
  const [countdown, setCountdown] = useState(COUNTDOWN_SECONDS);
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const isPending = approval.status === 'pending';
  const { level, reason } = classifyRisk(approval.toolName, approval.askPayload);
  const rc = RISK_CONFIG[level];

  // Countdown timer — auto-approve when it hits 0
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

  // Auto-approve when countdown reaches 0
  useEffect(() => {
    if (countdown === 0 && isPending && !resolving) {
      handleAllow();
    }
  }, [countdown]);

  // Extract command/file preview from askPayload
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

  const handleSelectOption = useCallback((option: 'allow' | 'deny' | 'allow_session' | 'allow_always') => {
    setIsDropdownOpen(false);
    switch (option) {
      case 'allow': handleAllow(); break;
      case 'deny': handleDeny(); break;
      case 'allow_session': handleAllowSession(); break;
      case 'allow_always':
        setResolving(true);
        if (countdownRef.current) clearInterval(countdownRef.current);
        useHookApprovalStore.getState().setAutoMode(true);
        wsService.send({
          type: 'hook_allow' as any,
          data: JSON.stringify({ approvalId: approval.approvalId }),
        });
        onResolved?.(approval.approvalId, 'approved');
        break;
    }
  }, [handleAllow, handleDeny, handleAllowSession, approval.approvalId, onResolved]);

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
        {/* Tool name + reason */}
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

        {/* Ask reason */}
        {approval.askPayload && !preview && (
          <div className="text-xs text-dark-muted leading-relaxed">
            <span className="text-dark-muted/70">原因：</span>
            <span className="text-dark-text">{approval.askPayload}</span>
          </div>
        )}

        {/* Alert for CRITICAL/HIGH risk */}
        {(level === 'CRITICAL' || level === 'HIGH') && (
          <div className={`flex items-center gap-1.5 text-[11px] px-2 py-1 rounded ${rc.bg} ${rc.text}`}>
            <AlertTriangle size={12} />
            <span>{level === 'CRITICAL' ? '此操作可能造成不可逆的影响，请仔细确认' : '请确认此操作为预期行为'}</span>
          </div>
        )}

        {/* Action buttons */}
        <div className="flex gap-2 pt-1">
          <button
            onClick={handleAllow}
            disabled={resolving}
            className="flex-1 px-3 py-1.5 text-xs bg-green-500/20 text-green-400 border border-green-500/30 rounded-lg hover:bg-green-500/30 transition-colors disabled:opacity-50 flex items-center justify-center gap-1.5"
          >
            <Check size={12} />
            <span>允许</span>
            <span className="text-[10px] opacity-60">({countdown}s)</span>
          </button>

          <button
            onClick={handleDeny}
            disabled={resolving}
            className="flex-1 px-3 py-1.5 text-xs bg-red-500/20 text-red-400 border border-red-500/30 rounded-lg hover:bg-red-500/30 transition-colors disabled:opacity-50 flex items-center justify-center gap-1.5"
          >
            <X size={12} />
            <span>拒绝</span>
          </button>

          {/* More options dropdown */}
          <div className="relative">
            <button
              onClick={() => setIsDropdownOpen(!isDropdownOpen)}
              disabled={resolving}
              className="px-2 py-1.5 text-xs bg-dark-surface border border-dark-border rounded-lg hover:bg-dark-hover transition-colors disabled:opacity-50 flex items-center gap-1"
              title="更多选项"
            >
              <ChevronDown size={12} />
            </button>

            {isDropdownOpen && (
              <>
                <div className="fixed inset-0 z-10" onClick={() => setIsDropdownOpen(false)} />
                <div className="absolute right-0 top-full mt-1 z-20 w-52 bg-dark-surface border border-dark-border rounded-lg shadow-xl overflow-hidden">
                  <button
                    onClick={() => handleSelectOption('allow_session')}
                    className="w-full px-3 py-2 text-xs text-left text-dark-text hover:bg-dark-hover transition-colors flex items-center gap-2"
                  >
                    <span className="text-green-400">✅</span>
                    <div>
                      <div>本次会话始终允许</div>
                      <div className="text-[10px] text-dark-muted">当前会话中不再询问此工具</div>
                    </div>
                  </button>
                  <div className="border-t border-dark-border" />
                  <button
                    onClick={() => handleSelectOption('allow_always')}
                    className="w-full px-3 py-2 text-xs text-left text-dark-text hover:bg-dark-hover transition-colors flex items-center gap-2"
                  >
                    <span className="text-purple-400"><Zap size={14} /></span>
                    <div>
                      <div>自动模式（全部允许）</div>
                      <div className="text-[10px] text-dark-muted">自动批准所有 Hook 请求</div>
                    </div>
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
});

function extractPreview(toolName: string, askPayload: string): string | null {
  // For Bash/Shell tools, show the command
  if (/\b(bash|shell|exec|cmd|powershell|terminal)\b/i.test(toolName)) {
    return askPayload || null;
  }
  // For Write/Edit tools, try to extract file path and snippet
  if (/\b(write|edit|save|create)\b/i.test(toolName)) {
    // Try to extract file_path from JSON-like payload
    const fileMatch = askPayload.match(/(?:file_path|path|file)["\s:=]+([^\s",}]+)/i);
    if (fileMatch) {
      return `📄 ${fileMatch[1]}\n${askPayload.slice(0, 200)}`;
    }
    return askPayload.slice(0, 200) || null;
  }
  // Generic: show first 200 chars
  if (askPayload && askPayload.length > 0) {
    return askPayload.length > 200 ? askPayload.slice(0, 200) + '...' : askPayload;
  }
  return null;
}

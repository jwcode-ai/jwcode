import { memo, useState, useCallback, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Shield, Check, X, AlertTriangle, Clock } from 'lucide-react';
import { HookApprovalInfo } from '../../types';
import wsService from '../../services/websocket';
import { useHookApprovalStore } from '../../stores/useHookApprovalStore';
import { classifyRisk, RISK_CONFIG, extractPreview } from '../../utils/hookRisk';

interface HookApprovalCardProps {
  approval: HookApprovalInfo;
  onResolved?: (approvalId: string, status: 'approved' | 'denied') => void;
}

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
  const { t } = useTranslation('hook');
  const [resolving, setResolving] = useState(false);
  const [countdown, setCountdown] = useState(COUNTDOWN_SECONDS);
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const isPending = approval.status === 'pending';
    // Always show inline buttons; the modal is a separate component shown via App.tsx
  const isModalActive = false;
  const { level, reasonKey } = classifyRisk(approval.toolName, approval.askPayload);
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
          <><Check size={14} /><span>{t('alreadyAllowed')}{approval.toolName}</span></>
        ) : (
          <><X size={14} /><span>{t('alreadyDenied')}{approval.toolName}</span></>
        )}
      </div>
    );
  }

  const countdownPct = (countdown / COUNTDOWN_SECONDS) * 100;
  const countdownUrgent = countdown <= 5;

  const options: OptionItem[] = [
    { id: 'allow', index: 1, label: t('optionAllow'), hint: t('optionAllowHint'), action: handleAllow },
    { id: 'deny', index: 2, label: t('optionDeny'), hint: t('optionDenyHint'), action: handleDeny },
    { id: 'allow_session', index: 3, label: t('optionAllowAlways'), hint: t('optionAllowAlwaysHint', { tool: approval.toolName }), action: handleAllowSession },
    { id: 'auto_mode', index: 4, label: t('optionAutoMode'), hint: t('optionAutoModeHint'), action: handleAutoMode },
  ];

  return (
    <div className={`bg-dark-bg border rounded-lg overflow-hidden ${rc.border}`}>
      {/* Header: risk badge + tool name + countdown */}
      <div className={`flex items-center gap-2 px-3 py-2 ${rc.bg} border-b ${rc.border}`}>
        <span className={`px-1.5 py-0.5 rounded text-[10px] font-bold border ${rc.badge}`}>
          {rc.icon} {level}
        </span>
        <Shield size={14} className={rc.text} />
        <span className="text-xs font-medium text-dark-text">{t('permissionRequest')}</span>
        <span className="text-[10px] text-dark-muted ml-auto">{t(reasonKey)}</span>

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
          <span className="text-dark-muted shrink-0">{t('tool')}</span>
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
            <span>{level === 'CRITICAL' ? t('highRiskWarning') : t('mediumRiskConfirm')}</span>
          </div>
        )}

        {isModalActive ? (
          /* 弹窗模式 — 卡片仅保留历史记录，无操作按钮 */
          <div className="flex items-center gap-2 px-2 py-2 text-xs text-dark-muted bg-dark-surface rounded border border-dark-border/50">
            <span>{t('modalHint')} · {t('shortcutHint')}</span>
          </div>
        ) : (
          /* 内联模式 — 显示操作按钮（当弹窗不可用时） */
          <>
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
            <div className="text-[10px] text-dark-muted/50 pt-0.5 border-t border-dark-border/50">
              {t('clickToSelect')}
            </div>
          </>
        )}
      </div>
    </div>
  );
});

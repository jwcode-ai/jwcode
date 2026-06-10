import { useState, useEffect, useCallback, useRef } from 'react';
import { Shield, Clock, AlertTriangle } from 'lucide-react';
import { Modal } from '../common/Modal';
import { useHookApprovalStore } from '../../stores/useHookApprovalStore';
import { classifyRisk, RISK_CONFIG, extractPreview } from '../../utils/hookRisk';
import wsService from '../../services/websocket';

interface HookApprovalModalProps {
  isOpen: boolean;
}

const COUNTDOWN_SECONDS = 15;

export function HookApprovalModal({ isOpen }: HookApprovalModalProps) {
  const approvalStore = useHookApprovalStore();
  const pendingApprovals = approvalStore.pendingApprovals;

  const autoMode = approvalStore.autoMode;
  const setAutoMode = approvalStore.setAutoMode;
  const showConfigHint = approvalStore.showConfigHint;
  const setShowConfigHint = approvalStore.setShowConfigHint;

  const [currentIndex, setCurrentIndex] = useState(0);
  const [resolving, setResolving] = useState(false);
  const [countdown, setCountdown] = useState(COUNTDOWN_SECONDS);
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const resolvingRef = useRef(false);

  if (pendingApprovals.length === 0) {
    return null;
  }

  if (autoMode) {
    pendingApprovals.forEach((item) => {
      wsService.send({
        type: 'hook_allow' as any,
        data: JSON.stringify({ approvalId: item.approvalId }),
      });
    });
    approvalStore.clearApprovals();
    return null;
  }

  const currentApproval = pendingApprovals[currentIndex];
  if (!currentApproval) {
    return null;
  }

  const total = pendingApprovals.length;
  const remaining = total - currentIndex - 1;

  // ── 倒计时 ──
  useEffect(() => {
    if (resolving || !isOpen) return;
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
    return () => {
      if (countdownRef.current) clearInterval(countdownRef.current);
    };
  }, [resolving, isOpen, currentApproval?.approvalId]);

  useEffect(() => {
    if (countdown === 0 && isOpen && !resolving && currentApproval) {
      setResolving(true);
      resolvingRef.current = true;
      wsService.send({
        type: 'hook_allow' as any,
        data: JSON.stringify({ approvalId: currentApproval.approvalId }),
      });
      advanceQueue();
    }
  }, [countdown]);

  // ── 键盘快捷键 ──
  useEffect(() => {
    if (!isOpen) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        e.stopPropagation();
        handleDeny();
      } else if (e.key === 'Enter') {
        e.preventDefault();
        handleAllow();
      } else if (e.key === '1') handleAllow();
      else if (e.key === '2') handleDeny();
      else if (e.key === '3') handleAllowSession();
      else if (e.key === '4') handleAutoMode();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [isOpen, currentApproval]);

  const advanceQueue = useCallback(() => {
    setResolving(true);
    setTimeout(() => {
      approvalStore.removeApproval(currentApproval.approvalId);
      setCurrentIndex(0);
      setResolving(false);
      resolvingRef.current = false;
      setCountdown(COUNTDOWN_SECONDS);
    }, 300);
  }, [currentApproval?.approvalId, approvalStore]);

  const handleAllow = useCallback(() => {
    if (!currentApproval || resolving || resolvingRef.current) return;
    resolvingRef.current = true;
    if (countdownRef.current) clearInterval(countdownRef.current);
    wsService.send({
      type: 'hook_allow' as any,
      data: JSON.stringify({ approvalId: currentApproval.approvalId }),
    });
    advanceQueue();
  }, [currentApproval, resolving, advanceQueue]);

  const handleDeny = useCallback(() => {
    if (!currentApproval || resolving || resolvingRef.current) return;
    resolvingRef.current = true;
    if (countdownRef.current) clearInterval(countdownRef.current);
    wsService.send({
      type: 'hook_deny' as any,
      data: JSON.stringify({ approvalId: currentApproval.approvalId }),
    });
    advanceQueue();
  }, [currentApproval, resolving, advanceQueue]);

  const handleAllowSession = useCallback(() => {
    if (!currentApproval || resolving || resolvingRef.current) return;
    resolvingRef.current = true;
    if (countdownRef.current) clearInterval(countdownRef.current);
    approvalStore.addToSessionAllowList(currentApproval.toolName);
    wsService.send({
      type: 'hook_allow' as any,
      data: JSON.stringify({ approvalId: currentApproval.approvalId }),
    });
    advanceQueue();
  }, [currentApproval, resolving, advanceQueue, approvalStore]);

  const handleAutoMode = useCallback(() => {
    if (!currentApproval || resolving || resolvingRef.current) return;
    resolvingRef.current = true;
    if (countdownRef.current) clearInterval(countdownRef.current);
    setAutoMode(true);
    wsService.send({
      type: 'hook_allow' as any,
      data: JSON.stringify({ approvalId: currentApproval.approvalId }),
    });
    advanceQueue();
  }, [currentApproval, resolving, advanceQueue, setAutoMode]);

  // ── 风险等级 ──
  const { level, reason } = classifyRisk(currentApproval.toolName, currentApproval.askPayload);
  const rc = RISK_CONFIG[level];

  // ── 预览 ──
  const preview = extractPreview(currentApproval.toolName, currentApproval.askPayload);

  // ── 倒计时显示 ──
  const countdownPct = (countdown / COUNTDOWN_SECONDS) * 100;
  const countdownUrgent = countdown <= 5;

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleDeny}
      title={
        <div className="flex items-center gap-2">
          <Shield className="w-5 h-5 text-yellow-400" />
          <span>Hook 拦截审批</span>
          {total > 1 && (
            <span className="text-xs text-dark-muted font-normal">
              ({currentIndex + 1}/{total})
            </span>
          )}
        </div>
      }
      size="lg"
    >
      <div className="space-y-4">
        {/* ── 风险等级徽标 + 倒计时 ── */}
        <div className={`flex items-center gap-2 px-3 py-2 rounded-lg ${rc.bg} border ${rc.border}`}>
          <span className={`px-1.5 py-0.5 rounded text-xs font-bold border ${rc.badge}`}>
            {rc.icon} {level}
          </span>
          <span className="text-xs text-dark-muted">{reason}</span>

          {/* 倒计时条 */}
          <div className="flex items-center gap-1.5 ml-auto">
            <Clock size={12} className={countdownUrgent ? 'text-accent-red' : 'text-dark-muted'} />
            <span className={`text-xs font-mono ${countdownUrgent ? 'text-accent-red animate-pulse' : 'text-dark-muted'}`}>
              {countdown}s
            </span>
            <div className="w-12 h-1.5 bg-dark-border rounded-full overflow-hidden">
              <div
                className={`h-full rounded-full transition-all duration-1000 ${
                  countdownUrgent ? 'bg-accent-red' : countdown <= 8 ? 'bg-accent-yellow' : 'bg-accent-green'
                }`}
                style={{ width: `${countdownPct}%` }}
              />
            </div>
          </div>
        </div>

        {/* ── 审批信息 ── */}
        <div className="bg-dark-bg rounded-lg p-4 border border-dark-border space-y-2">
          <div className="flex items-center gap-2 text-sm">
            <span className="text-dark-muted shrink-0">工具：</span>
            <code className="text-blue-400 font-mono text-xs bg-dark-surface px-1.5 py-0.5 rounded">
              {currentApproval.toolName}
            </code>
          </div>

          {/* 命令/参数预览 */}
          {preview && (
            <div className="bg-dark-surface rounded border border-dark-border p-2 max-h-32 overflow-y-auto">
              <pre className="text-xs font-mono text-dark-text whitespace-pre-wrap break-all leading-relaxed">
                {preview}
              </pre>
            </div>
          )}

          {/* CRITICAL/HIGH 风险警告 */}
          {(level === 'CRITICAL' || level === 'HIGH') && (
            <div className={`flex items-center gap-1.5 text-xs px-2 py-1.5 rounded ${rc.bg} ${rc.text}`}>
              <AlertTriangle size={13} />
              <span>{level === 'CRITICAL' ? '此操作可能造成不可逆的影响，请仔细确认' : '请确认此操作为预期行为'}</span>
            </div>
          )}
        </div>

        {/* ── 编号选项列表 ── */}
        <div className="space-y-0.5">
          {[
            { id: 'allow', label: 'Allow — 执行此操作', hint: '允许本次操作', action: handleAllow },
            { id: 'deny', label: 'Deny — 取消此操作', hint: '拒绝本次操作', action: handleDeny },
            { id: 'allow_session', label: 'Allow always this session — 本次会话允许', hint: `不再询问 ${currentApproval.toolName}`, action: handleAllowSession },
            { id: 'auto_mode', label: 'Auto mode — 自动批准所有 Hook', hint: '之后所有 Hook 将自动批准', action: handleAutoMode },
          ].map((opt, i) => (
            <button
              key={opt.id}
              onClick={opt.action}
              disabled={resolving}
              className="w-full text-left px-3 py-2 rounded-lg hover:bg-dark-hover transition-colors disabled:opacity-50 group border border-transparent hover:border-dark-border/50"
            >
              <div className="flex items-baseline gap-2">
                <span className="text-sm text-dark-muted font-mono shrink-0">{i + 1}.</span>
                <span className="text-sm text-dark-text group-hover:text-white transition-colors">{opt.label}</span>
              </div>
              <div className="text-xs text-dark-muted/60 ml-7 mt-0.5">{opt.hint}</div>
            </button>
          ))}
        </div>

        {/* ── 底部提示 ── */}
        <div className="flex items-center justify-between text-xs text-dark-muted/50">
          <span>快捷键: 1-4 选择 · Enter 允许 · Esc 拒绝</span>
          {remaining > 0 ? (
            <span>还有 {remaining} 项待审批 · 共 {total} 项</span>
          ) : (
            <span>最后一项</span>
          )}
        </div>

        {/* ── 自动模式开关 ── */}
        <div className="flex items-center justify-between px-3 py-2 bg-dark-bg rounded-lg border border-dark-border">
          <div className="flex flex-col">
            <span className="text-sm text-dark-text">自动模式</span>
            <span className="text-xs text-dark-muted">自动批准所有 Hook 请求</span>
          </div>
          <button
            onClick={() => setAutoMode(!autoMode)}
            className={`relative w-10 h-5 rounded-full transition-colors ${
              autoMode ? 'bg-green-500' : 'bg-dark-border'
            }`}
          >
            <span
              className={`absolute top-0.5 w-4 h-4 rounded-full bg-white transition-transform ${
                autoMode ? 'translate-x-5' : 'translate-x-0.5'
              }`}
            />
          </button>
        </div>

        {/* ── hooks.json 配置说明 ── */}
        <div className="border border-dark-border rounded-lg overflow-hidden">
          <button
            onClick={() => setShowConfigHint(!showConfigHint)}
            className="w-full flex items-center justify-between px-3 py-2 text-xs text-dark-muted hover:text-dark-text hover:bg-dark-surface/50 transition-colors"
          >
            <span>自定义 Hook 配置说明</span>
            <span>{showConfigHint ? '▲' : '▼'}</span>
          </button>
          {showConfigHint && (
            <div className="px-3 pb-3 text-xs text-dark-muted space-y-2">
              <p>
                可在项目根目录的 <code className="text-blue-400">.jwcode/hooks.json</code> 中
                添加或修改 Hook 拦截规则。
              </p>
              <p>
                Hook 支持 <strong>SHELL</strong>（外部脚本）和 <strong>HTTP</strong>（REST 端点）两种类型，
                可在工具执行前（PRE_TOOL_USE）或执行后（POST_TOOL_USE）触发。
              </p>
              <div className="bg-dark-bg rounded p-2 font-mono text-[11px] leading-relaxed overflow-x-auto">
                <pre>{`{
  "hooks": [{
    "name": "my-custom-hook",
    "events": ["PRE_TOOL_USE"],
    "implementation": {
      "type": "SHELL",
      "command": "python3 script.py"
    },
    "priority": "USER",
    "tools": ["BashTool"],
    "enabled": true
  }]
}`}</pre>
              </div>
              <p className="text-dark-muted/70">
                内置 BashSafetyHook（危险命令拦截）、FileWriteAuditHook（文件写入审计）、
                ToolUsageStatsHook（工具调用统计）。设置 <code>enabled: false</code> 可禁用任意 Hook。
              </p>
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
}

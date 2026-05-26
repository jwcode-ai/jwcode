import { memo, useState, useCallback } from 'react';
import { Shield, Check, X, ChevronDown } from 'lucide-react';
import { HookApprovalInfo } from '../../types';
import wsService from '../../services/websocket';
import { useHookApprovalStore } from '../../stores/useHookApprovalStore';

interface HookApprovalCardProps {
  approval: HookApprovalInfo;
  /** 回调：审批完成后更新消息状态 */
  onResolved?: (approvalId: string, status: 'approved' | 'denied') => void;
}

/**
 * HookApprovalCard — 嵌入对话消息中的权限申请卡片。
 *
 * 展示工具名称、申请原因，并提供"允许"、"拒绝"按钮，
 * 以及一个下拉菜单选择更细粒度的控制选项。
 */
export const HookApprovalCard = memo(function HookApprovalCard({
  approval,
  onResolved,
}: HookApprovalCardProps) {
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [resolving, setResolving] = useState(false);

  const isPending = approval.status === 'pending';

  const handleAllow = useCallback(() => {
    if (!isPending || resolving) return;
    setResolving(true);
    wsService.send({
      type: 'hook_allow' as any,
      data: JSON.stringify({ approvalId: approval.approvalId }),
    });
    onResolved?.(approval.approvalId, 'approved');
  }, [isPending, resolving, approval.approvalId, onResolved]);

  const handleDeny = useCallback(() => {
    if (!isPending || resolving) return;
    setResolving(true);
    wsService.send({
      type: 'hook_deny' as any,
      data: JSON.stringify({ approvalId: approval.approvalId }),
    });
    onResolved?.(approval.approvalId, 'denied');
  }, [isPending, resolving, approval.approvalId, onResolved]);

  const handleAllowSession = useCallback(() => {
    if (!isPending || resolving) return;
    setResolving(true);
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
      case 'allow':
        handleAllow();
        break;
      case 'deny':
        handleDeny();
        break;
      case 'allow_session':
        handleAllowSession();
        break;
      case 'allow_always':
        // 持久化允许：设置 autoMode 并批准
        useHookApprovalStore.getState().setAutoMode(true);
        wsService.send({
          type: 'hook_allow' as any,
          data: JSON.stringify({ approvalId: approval.approvalId }),
        });
        onResolved?.(approval.approvalId, 'approved');
        break;
    }
  }, [handleAllow, handleDeny, handleAllowSession, approval.approvalId, onResolved]);

  // 已处理状态显示
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

  return (
    <div className="bg-dark-bg border border-accent-yellow/40 rounded-lg overflow-hidden">
      {/* 头部：标题 + 状态 */}
      <div className="flex items-center gap-2 px-3 py-2 bg-accent-yellow/5 border-b border-accent-yellow/20">
        <Shield size={16} className="text-yellow-400 shrink-0" />
        <span className="text-xs font-medium text-dark-text">权限申请</span>
        <span className="text-[10px] text-yellow-400 ml-auto animate-pulse">等待确认</span>
      </div>

      {/* 内容 */}
      <div className="px-3 py-2.5 space-y-2">
        {/* 工具名称 */}
        <div className="flex items-center gap-2 text-xs">
          <span className="text-dark-muted shrink-0">工具：</span>
          <code className="text-blue-400 font-mono text-xs bg-dark-surface px-1.5 py-0.5 rounded">
            {approval.toolName}
          </code>
        </div>

        {/* 申请原因 */}
        {approval.askPayload && (
          <div className="text-xs text-dark-muted leading-relaxed">
            <span className="text-dark-muted/70">原因：</span>
            <span className="text-dark-text">{approval.askPayload}</span>
          </div>
        )}

        {/* 按钮组 */}
        <div className="flex gap-2 pt-1">
          {/* 允许按钮 */}
          <button
            onClick={handleAllow}
            disabled={resolving}
            className="flex-1 px-3 py-1.5 text-xs bg-green-500/20 text-green-400 border border-green-500/30 rounded-lg hover:bg-green-500/30 transition-colors disabled:opacity-50 flex items-center justify-center gap-1.5"
          >
            <Check size={12} />
            <span>允许</span>
          </button>

          {/* 拒绝按钮 */}
          <button
            onClick={handleDeny}
            disabled={resolving}
            className="flex-1 px-3 py-1.5 text-xs bg-red-500/20 text-red-400 border border-red-500/30 rounded-lg hover:bg-red-500/30 transition-colors disabled:opacity-50 flex items-center justify-center gap-1.5"
          >
            <X size={12} />
            <span>拒绝</span>
          </button>

          {/* 更多选项下拉按钮 */}
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
                {/* 点击外部关闭 */}
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
                    <span className="text-blue-400">🔄</span>
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

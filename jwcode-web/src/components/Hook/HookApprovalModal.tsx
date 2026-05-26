import { useState } from 'react';
import { Shield, Check, X, ChevronDown, ChevronUp } from 'lucide-react';
import { Modal } from '../common/Modal';
import { useHookApprovalStore } from '../../stores/useHookApprovalStore';
import wsService from '../../services/websocket';

interface HookApprovalModalProps {
  isOpen: boolean;
  onClose: () => void;
}

/**
 * HookApprovalModal — Hook ASK 审批弹窗。
 *
 * 当后端 Hook 返回 ASK 决策时，此弹窗展示给用户，
 * 用户可以选择允许、拒绝、或设为自动模式。
 */
export function HookApprovalModal({ isOpen, onClose }: HookApprovalModalProps) {
  const approvalStore = useHookApprovalStore();
  const pendingApprovals = approvalStore.pendingApprovals;

  const autoMode = approvalStore.autoMode;
  const setAutoMode = approvalStore.setAutoMode;
  const showConfigHint = approvalStore.showConfigHint;
  const setShowConfigHint = approvalStore.setShowConfigHint;

  const [currentIndex, setCurrentIndex] = useState(0);

  // 如果没有待审批项，不显示
  if (pendingApprovals.length === 0) {
    return null;
  }

  // 自动模式：自动批准所有
  if (autoMode) {
    pendingApprovals.forEach((item) => {
      wsService.send({
        type: 'hook_allow' as any,
        data: JSON.stringify({
          approvalId: item.approvalId,
        }),
      });
    });
    approvalStore.clearApprovals();
    return null;
  }

  const currentApproval = pendingApprovals[currentIndex];
  if (!currentApproval) {
    return null;
  }

  const handleAllow = () => {
    wsService.send({
      type: 'hook_allow' as any,
      data: JSON.stringify({
        approvalId: currentApproval.approvalId,
      }),
    });
    approvalStore.removeApproval(currentApproval.approvalId);
    if (currentIndex >= pendingApprovals.length - 1) {
      onClose();
    } else {
      setCurrentIndex(0);
    }
  };

  const handleAllowSession = () => {
    approvalStore.addToSessionAllowList(currentApproval.toolName);
    handleAllow();
  };

  const handleDeny = () => {
    wsService.send({
      type: 'hook_deny' as any,
      data: JSON.stringify({
        approvalId: currentApproval.approvalId,
      }),
    });
    approvalStore.removeApproval(currentApproval.approvalId);
    if (currentIndex >= pendingApprovals.length - 1) {
      onClose();
    } else {
      setCurrentIndex(0);
    }
  };

  const remaining = pendingApprovals.length - currentIndex - 1;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Hook 拦截审批"
      size="md"
    >
      <div className="space-y-4">
        {/* 审批信息 */}
        <div className="bg-dark-bg rounded-lg p-4 border border-dark-border">
          <div className="flex items-center gap-2 mb-2">
            <Shield className="w-5 h-5 text-yellow-400" />
            <span className="text-sm font-medium text-dark-text">
              Hook 请求确认
            </span>
            {remaining > 0 && (
              <span className="text-xs text-dark-muted ml-auto">
                还有 {remaining} 项待审批
              </span>
            )}
          </div>
          <div className="space-y-1.5 text-sm">
            <div className="flex items-baseline gap-2">
              <span className="text-dark-muted shrink-0">工具：</span>
              <code className="text-blue-400 font-mono text-xs">
                {currentApproval.toolName}
              </code>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="text-dark-muted shrink-0">原因：</span>
              <span className="text-dark-text">
                {currentApproval.askPayload || '需要用户确认此操作'}
              </span>
            </div>
          </div>
        </div>

        {/* 操作按钮 */}
        <div className="space-y-3">
          <div className="flex gap-2">
            <button
              onClick={handleAllow}
              className="flex-1 px-4 py-2.5 text-sm bg-green-500 text-white rounded-lg hover:bg-green-600 transition-colors flex items-center justify-center gap-2"
            >
              <Check className="w-4 h-4" />
              <span>允许执行</span>
            </button>
            <button
              onClick={handleDeny}
              className="flex-1 px-4 py-2.5 text-sm bg-red-500/20 text-red-400 border border-red-500/30 rounded-lg hover:bg-red-500/30 transition-colors flex items-center justify-center gap-2"
            >
              <X className="w-4 h-4" />
              <span>拒绝</span>
            </button>
          </div>

          <button
            onClick={handleAllowSession}
            className="w-full px-4 py-2 text-sm bg-dark-surface border border-dark-border rounded-lg hover:bg-dark-hover transition-colors flex items-center justify-center gap-2"
          >
            <span>✅</span>
            <span>本次会话始终允许 <code className="text-xs text-dark-muted">{currentApproval.toolName}</code></span>
          </button>
        </div>

        {/* 自动模式开关 */}
        <div className="flex items-center justify-between px-3 py-2 bg-dark-bg rounded-lg border border-dark-border">
          <div className="flex flex-col">
            <span className="text-sm text-dark-text">自动模式</span>
            <span className="text-xs text-dark-muted">
              自动批准所有 Hook 请求
            </span>
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

        {/* hooks.json 配置说明 */}
        <div className="border border-dark-border rounded-lg overflow-hidden">
          <button
            onClick={() => setShowConfigHint(!showConfigHint)}
            className="w-full flex items-center justify-between px-3 py-2 text-xs text-dark-muted hover:text-dark-text hover:bg-dark-surface/50 transition-colors"
          >
            <span className="flex items-center gap-1.5">
              <span>📝</span>
              <span>自定义 Hook 配置说明</span>
            </span>
            {showConfigHint ? (
              <ChevronUp className="w-4 h-4" />
            ) : (
              <ChevronDown className="w-4 h-4" />
            )}
          </button>
          {showConfigHint && (
            <div className="px-3 pb-3 text-xs text-dark-muted space-y-2">
              <p>
                你可以在项目根目录的 <code className="text-blue-400">.jwcode/hooks.json</code> 文件中
                添加或修改 Hook 拦截规则。
              </p>
              <p>
                Hook 支持 <strong>SHELL</strong>（外部脚本）和 <strong>HTTP</strong>（REST 端点）两种类型，
                可以在工具执行前（PRE_TOOL_USE）或执行后（POST_TOOL_USE）触发。
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
                内置了 BashSafetyHook（危险命令拦截）、FileWriteAuditHook（文件写入审计）、
                ToolUsageStatsHook（工具调用统计）三套 Hook。
                设置 <code>enabled: false</code> 可禁用任意 Hook。
              </p>
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
}

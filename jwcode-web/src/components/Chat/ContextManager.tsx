import { useCallback, useMemo, useState } from 'react';
import { useTokenStore } from '../../stores/tokenStore';
import { useChatStore } from '../../stores/chatStore';
import { useSessionStore } from '../../stores/sessionStore';
import { Trash2, ChevronDown, ChevronUp, Gauge, Scissors, MessageSquare } from 'lucide-react';

export function ContextManager() {
  const { currentUsage, maxContextTokens, showTokenInfo, setShowTokenInfo, estimateTokens } = useTokenStore();
  const messagesBySession = useChatStore((s) => s.messagesBySession);
  const activeSessionId = useSessionStore((s) => s.activeSessionId);
  // 只使用当前激活会话的消息
  const messages = activeSessionId ? (messagesBySession[activeSessionId] || []) : [];
  const [expanded, setExpanded] = useState(false);
  const [pruneRatio, setPruneRatio] = useState(0.5);

  // 实时计算当前会话的 token 明细
  const tokenDetails = useMemo(() => {
    if (messages.length === 0) return null;
    let totalChars = 0;
    let userTokens = 0;
    let assistantTokens = 0;
    for (const msg of messages) {
      const text = msg.content || '';
      totalChars += text.length;
      const tokens = estimateTokens(text);
      if (msg.type === 'user') userTokens += tokens;
      else assistantTokens += tokens;
    }
    const total = userTokens + assistantTokens;
    return {
      totalChars,
      userTokens,
      assistantTokens,
      total,
      avgPerMsg: Math.round(total / messages.length),
    };
  }, [messages, estimateTokens]);

  const usagePercent = maxContextTokens > 0
    ? Math.min(100, Math.round((currentUsage.totalTokens / maxContextTokens) * 100))
    : 0;

  const getStatusColor = () => {
    if (usagePercent < 50) return 'text-green-400';
    if (usagePercent < 80) return 'text-yellow-400';
    return 'text-red-400';
  };

  const getBarColor = () => {
    if (usagePercent < 50) return 'bg-green-500';
    if (usagePercent < 80) return 'bg-yellow-500';
    return 'bg-red-500';
  };

  // 客户端裁剪 — 直接截断当前会话消息，保留 pruneRatio 比例
  const handlePrune = useCallback(() => {
    if (messages.length === 0) return;
    const keepCount = Math.max(1, Math.floor(messages.length * pruneRatio));
    const keepSet = new Set(messages.slice(-keepCount).map((m) => m.id));
    const cutMessages = messages.length - keepCount;
    // 通过 chatStore action 实际裁剪消息
    useChatStore.setState((state) => {
      if (!activeSessionId) return state;
      const current = state.messagesBySession[activeSessionId] || [];
      const filtered = current.filter((m) => keepSet.has(m.id));
      return {
        ...state,
        messagesBySession: { ...state.messagesBySession, [activeSessionId]: filtered },
      };
    });
    console.log(`[ContextManager] Pruned ${cutMessages} messages, kept last ${keepCount}`);
  }, [messages, pruneRatio, activeSessionId]);

  if (!showTokenInfo) {
    // Mini indicator bar
    return (
      <button
        onClick={() => setShowTokenInfo(true)}
        className="flex items-center gap-1.5 px-2 py-1 text-xs text-dark-muted hover:text-dark-text transition-colors"
        title="显示上下文信息"
      >
        <Gauge size={12} />
        <div className="w-16 h-1.5 bg-dark-border rounded-full overflow-hidden">
          <div className={`h-full ${getBarColor()} rounded-full transition-all duration-500`} 
               style={{ width: `${usagePercent}%` }} />
        </div>
        <span className="font-mono text-[10px]">{usagePercent}%</span>
      </button>
    );
  }

  return (
    <div className="border-t border-dark-border bg-dark-surface/80 backdrop-blur-sm">
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center justify-between px-3 py-2 text-xs text-dark-muted hover:text-dark-text transition-colors"
      >
        <div className="flex items-center gap-2">
          <Gauge size={14} className={getStatusColor()} />
          <span>上下文管理</span>
          <div className="w-20 h-1.5 bg-dark-border rounded-full overflow-hidden">
            <div className={`h-full ${getBarColor()} rounded-full transition-all duration-500`} 
                 style={{ width: `${usagePercent}%` }} />
          </div>
          <span className="font-mono text-[10px] text-dark-muted">
            {currentUsage.totalTokens.toLocaleString()} / {maxContextTokens.toLocaleString()} tokens
          </span>
        </div>
        {expanded ? <ChevronDown size={14} /> : <ChevronUp size={14} />}
      </button>

      {expanded && (
        <div className="px-3 pb-3 space-y-2">
          {/* Token breakdown — 带滚动条，消息多时可滚动 */}
          <div className="max-h-[160px] overflow-y-auto scrollbar-thin scrollbar-thumb-dark-border scrollbar-track-transparent">
            <div className="grid grid-cols-3 gap-2">
              <div className="bg-dark-bg rounded p-2 text-center">
                <div className="text-[10px] text-dark-muted">提示词</div>
                <div className="text-sm font-mono text-accent-blue">
                  {currentUsage.promptTokens.toLocaleString()}
                </div>
                {tokenDetails && (
                  <div className="text-[9px] text-dark-muted/60 mt-0.5">
                    ~{tokenDetails.userTokens.toLocaleString()} (消息)
                  </div>
                )}
              </div>
              <div className="bg-dark-bg rounded p-2 text-center">
                <div className="text-[10px] text-dark-muted">补全</div>
                <div className="text-sm font-mono text-accent-green">
                  {currentUsage.completionTokens.toLocaleString()}
                </div>
                {tokenDetails && (
                  <div className="text-[9px] text-dark-muted/60 mt-0.5">
                    ~{tokenDetails.assistantTokens.toLocaleString()} (消息)
                  </div>
                )}
              </div>
              <div className="bg-dark-bg rounded p-2 text-center">
                <div className="text-[10px] text-dark-muted">总计</div>
                <div className={`text-sm font-mono ${getStatusColor()}`}>
                  {currentUsage.totalTokens.toLocaleString()}
                </div>
                {tokenDetails && (
                  <div className="text-[9px] text-dark-muted/60 mt-0.5">
                    ~{tokenDetails.total.toLocaleString()} (消息)
                  </div>
                )}
              </div>
            </div>

            {/* Token 明细行 */}
            {tokenDetails && (
              <div className="mt-2 grid grid-cols-2 gap-2">
                <div className="bg-dark-bg/60 rounded p-1.5 text-center">
                  <div className="text-[9px] text-dark-muted">总字符数</div>
                  <div className="text-xs font-mono text-dark-text">
                    {tokenDetails.totalChars.toLocaleString()}
                  </div>
                </div>
                <div className="bg-dark-bg/60 rounded p-1.5 text-center">
                  <div className="text-[9px] text-dark-muted">平均 token/条</div>
                  <div className="text-xs font-mono text-dark-text">
                    {tokenDetails.avgPerMsg.toLocaleString()}
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Cost estimate */}
          {currentUsage.estimatedCost !== undefined && currentUsage.estimatedCost > 0 && (
            <div className="text-[10px] text-dark-muted text-center">
              预估成本: ${currentUsage.estimatedCost.toFixed(4)}
            </div>
          )}

          {/* Pruning controls */}
          <div className="bg-dark-bg rounded p-2 space-y-2">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1.5 text-xs text-dark-muted">
                <Scissors size={12} />
                <span>上下文裁剪</span>
              </div>
              <span className="text-xs text-dark-text">
                {Math.round(pruneRatio * 100)}% 保留
              </span>
            </div>
            <input
              type="range"
              min="10"
              max="90"
              value={pruneRatio * 100}
              onChange={(e) => setPruneRatio(Number(e.target.value) / 100)}
              className="w-full h-1.5 bg-dark-border rounded-full appearance-none cursor-pointer 
                         accent-accent-blue [&::-webkit-slider-thumb]:appearance-none 
                         [&::-webkit-slider-thumb]:w-3 [&::-webkit-slider-thumb]:h-3 
                         [&::-webkit-slider-thumb]:bg-accent-blue [&::-webkit-slider-thumb]:rounded-full"
            />
            <div className="flex justify-between text-[10px] text-dark-muted">
              <span>保留更多</span>
              <span>裁剪更多</span>
            </div>
            <button
              onClick={handlePrune}
              className="w-full flex items-center justify-center gap-1.5 px-3 py-1.5 text-xs 
                         bg-accent-blue/10 text-accent-blue rounded hover:bg-accent-blue/20 
                         transition-colors"
            >
              <Trash2 size={12} />
              裁剪上下文 (保留 ~{Math.round(messages.length * pruneRatio)} 条消息)
            </button>
          </div>

          {/* Message count */}
          <div className="flex items-center justify-center gap-1 text-[10px] text-dark-muted">
            <MessageSquare size={10} />
            <span>
              当前 <span className="text-dark-text font-mono">{messages.length}</span> 条消息
              {tokenDetails && (
                <> · 共 <span className="text-dark-text font-mono">{tokenDetails.totalChars.toLocaleString()}</span> 字符</>
              )}
            </span>
          </div>
        </div>
      )}
    </div>
  );
}

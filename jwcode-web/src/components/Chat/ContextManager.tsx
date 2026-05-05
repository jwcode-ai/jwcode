import { useCallback, useState } from 'react';
import { useTokenStore } from '../../stores/tokenStore';
import { useChatStore } from '../../stores/chatStore';
import { Trash2, Info, Sliders, ChevronDown, ChevronUp, Gauge, Zap, Scissors } from 'lucide-react';

export function ContextManager() {
  const { currentUsage, maxContextTokens, showTokenInfo, setShowTokenInfo, pruneContext, resetUsage } = useTokenStore();
  const { messages } = useChatStore();
  const [expanded, setExpanded] = useState(false);
  const [pruneRatio, setPruneRatio] = useState(0.5);

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

  const handlePrune = useCallback(() => {
    const result = pruneContext(pruneRatio);
    // Also clear some messages from the chat store
    if (result.cutMessages > 0 && messages.length > result.cutMessages + 2) {
      const keepMessages = messages.slice(0, 1); // Keep system message
      const recentMessages = messages.slice(-Math.max(2, messages.length - result.cutMessages));
      useChatStore.getState().setMessages([...keepMessages, ...recentMessages]);
    }
  }, [pruneRatio, pruneContext, messages]);

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
          {/* Token breakdown */}
          <div className="grid grid-cols-3 gap-2">
            <div className="bg-dark-bg rounded p-2 text-center">
              <div className="text-[10px] text-dark-muted">提示词</div>
              <div className="text-sm font-mono text-accent-blue">
                {currentUsage.promptTokens.toLocaleString()}
              </div>
            </div>
            <div className="bg-dark-bg rounded p-2 text-center">
              <div className="text-[10px] text-dark-muted">补全</div>
              <div className="text-sm font-mono text-accent-green">
                {currentUsage.completionTokens.toLocaleString()}
              </div>
            </div>
            <div className="bg-dark-bg rounded p-2 text-center">
              <div className="text-[10px] text-dark-muted">总计</div>
              <div className="text-sm font-mono" className={getStatusColor()}>
                {currentUsage.totalTokens.toLocaleString()}
              </div>
            </div>
          </div>

          {/* Cost estimate */}
          {currentUsage.estimatedCost && (
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
          <div className="text-[10px] text-dark-muted text-center">
            当前 {messages.length} 条消息 · 如需重置请清空对话
          </div>
        </div>
      )}
    </div>
  );
}

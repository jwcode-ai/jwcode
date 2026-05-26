import { memo, useState, useCallback } from 'react';
import { Plus, Minus } from 'lucide-react';
import { Message } from '../../types';
import { MarkdownRenderer } from '../common/MarkdownRenderer';
import { StepItem } from './StepItem';
import { ToolCallItem } from './ToolCallItem';
import { HookApprovalCard } from './HookApprovalCard';
import { useChatStore } from '../../stores/chatStore';

interface MessageBubbleProps {
  message: Message;
  sessionId?: string;
}

export const MessageBubble = memo(function MessageBubble({
  message,
  sessionId,
}: MessageBubbleProps) {
  const isUser = message.type === 'user';
  const [isThinkingCollapsed, setIsThinkingCollapsed] = useState(true);

  // Hook 审批完成回调：更新消息中的审批状态
  const handleHookResolved = useCallback((_approvalId: string, status: 'approved' | 'denied') => {
    if (!sessionId) return;
    const chatStore = useChatStore.getState();
    const msgs = chatStore.getMessages(sessionId);
    const msgIndex = msgs.findIndex(m => m.id === message.id);
    if (msgIndex === -1) return;

    const msg = msgs[msgIndex];
    if (!msg) return;

    const updatedMsg: Message = {
      ...msg,
      hookApproval: msg.hookApproval
        ? { ...msg.hookApproval, status }
        : undefined,
    };
    const newMsgs = [...msgs];
    newMsgs[msgIndex] = updatedMsg;
    // 直接更新 store 中的消息
    useChatStore.setState({
      messagesBySession: {
        ...useChatStore.getState().messagesBySession,
        [sessionId]: newMsgs,
      },
    });
  }, [sessionId, message.id]);

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'} animate-fade-in`}>
      <div
        className={`max-w-[85%] md:max-w-[75%] px-4 py-3 rounded-2xl ${
          isUser
            ? 'bg-accent-blue text-white rounded-br-md'
            : 'bg-dark-surface border border-dark-border text-dark-text rounded-bl-md'
        }`}
      >
        {/* Hook 审批卡片 — 优先展示 */}
        {message.hookApproval && (
          <div className="mb-2">
            <HookApprovalCard
              approval={message.hookApproval}
              onResolved={handleHookResolved}
            />
          </div>
        )}

        {/* Steps - Collapsible with ToolCalls */}
        {message.steps && message.steps.length > 0 && (
          <div className="mb-1 space-y-0.5">
            {message.steps.map((step) => (
              <StepItem key={step.id} step={step} defaultCollapsed={false} />
            ))}
          </div>
        )}

        {/* Fallback: 没有 steps 时才独立展示 thinking / toolCalls */}
        {!message.steps?.length && message.thinking && !message.hookApproval && (
          <div className="mb-1 bg-dark-bg border border-dark-border rounded-lg overflow-hidden">
            <div
              className="flex items-center gap-1.5 px-2 py-1.5 cursor-pointer hover:bg-dark-hover/30 transition-colors select-none"
              onClick={() => setIsThinkingCollapsed(!isThinkingCollapsed)}
            >
              {isThinkingCollapsed ? <Plus size={13} className="text-accent-blue" /> : <Minus size={13} className="text-accent-blue" />}
              <span className="text-xs text-accent-blue flex items-center gap-1">
                <span>💭</span>
                <span>思考过程</span>
              </span>
              {isThinkingCollapsed && (
                <span className="text-[10px] text-dark-muted ml-1 truncate max-w-[200px]">
                  {message.thinking.slice(0, 60).replace(/\n/g, ' ')}...
                </span>
              )}
            </div>
            {!isThinkingCollapsed && (
              <div className="px-2 pb-2 text-sm text-dark-muted italic border-t border-dark-border/50 pt-1.5">
                {message.thinking.replace(/\n\s*\n+/g, '\n')}
              </div>
            )}
          </div>
        )}

        {!message.steps?.length && message.toolCalls && message.toolCalls.length > 0 && !message.hookApproval && (
          <div className="mb-1 space-y-0.5">
            {message.toolCalls.map(toolCall => (
              <ToolCallItem key={toolCall.id} toolCall={toolCall} defaultCollapsed={true} />
            ))}
          </div>
        )}

        {/* Content with Markdown - Fixed line breaks */}
        {message.content ? (
          <div className="whitespace-pre-wrap break-words leading-relaxed">
            {isUser ? (
              <span>{message.content.replace(/\n\s*\n+/g, '\n')}</span>
            ) : (
              <MarkdownRenderer content={message.content.replace(/\n\s*\n+/g, '\n')} className="whitespace-pre-wrap" />
            )}
          </div>
        ) : !isUser && message.thinking && !message.hookApproval ? (
          <div className="whitespace-pre-wrap break-words leading-relaxed text-dark-muted italic">
            <span className="inline-flex items-center gap-2">
              <span className="w-2 h-2 bg-accent-blue rounded-full animate-bounce" />
              <span className="w-2 h-2 bg-accent-blue rounded-full animate-bounce" style={{ animationDelay: '0.1s' }} />
              <span className="w-2 h-2 bg-accent-blue rounded-full animate-bounce" style={{ animationDelay: '0.2s' }} />
              💭 正在思考...
            </span>
          </div>
        ) : null}

        {/* Timestamp */}
        <div className={`text-[10px] mt-2 ${isUser ? 'text-white/70' : 'text-dark-muted'}`}>
          {new Date(message.timestamp).toLocaleTimeString('zh-CN', {
            hour: '2-digit',
            minute: '2-digit',
          })}
        </div>
      </div>
    </div>
  );
});

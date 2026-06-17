import { memo, useState, useCallback } from 'react';
import { ChevronRight, ChevronDown } from 'lucide-react';
import { Message } from '../../types';
import { MarkdownRenderer } from '../common/MarkdownRenderer';
import { StepItem } from './StepItem';
import { ToolCallItem } from './ToolCallItem';
import { HookApprovalCard } from './HookApprovalCard';
import { SwarmVisualizer } from './SwarmVisualizer';
import { useChatStore } from '../../stores/chatStore';

interface MessageBubbleProps {
  message: Message;
  sessionId?: string;
}

export const MessageBubble = memo(function MessageBubble({
  message,
  sessionId,
}: MessageBubbleProps) {
  // Tombstone: 被后端标记为已删除的消息不渲染
  if (message.deleted) {
    return null;
  }

  const isUser = message.type === 'user';
  const isSystem = message.type === 'system';
  const isGenerating = sessionId ? useChatStore(s => s.generatingSessions.includes(sessionId)) : false;
  const [isThinkingCollapsed, setIsThinkingCollapsed] = useState(false);

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
      hookApproval: msg.hookApproval ? { ...msg.hookApproval, status } : undefined,
    };
    const newMsgs = [...msgs];
    newMsgs[msgIndex] = updatedMsg;
    useChatStore.setState({
      messagesBySession: {
        ...useChatStore.getState().messagesBySession,
        [sessionId]: newMsgs,
      },
    });
  }, [sessionId, message.id]);

  // Claude Code style: user messages right-aligned, plain text with dim label
  if (isUser) {
    return (
      <div className="flex justify-end animate-fade-in-up px-1">
        <div className="max-w-[92%] md:max-w-[80%]">
          <div className="text-xs text-dark-muted mb-0.5 text-right">You</div>
          <div className="text-dark-text whitespace-pre-wrap break-words leading-relaxed">
            {message.content?.replace(/\n{3,}/g, '\n\n')}
          </div>
        </div>
      </div>
    );
  }

  // System messages: dim, centered
  if (isSystem) {
    return (
      <div className="flex justify-center animate-fade-in-up px-1">
        <div className="text-[11px] text-dark-muted italic max-w-[90%] text-center">
          {message.content}
        </div>
      </div>
    );
  }

  // Assistant messages: left-border accent + indent (Claude Code terminal style)
  return (
    <div className="flex animate-fade-in-up px-1">
      <div className="w-full max-w-full">
        {/* Swarm visualizer */}
        {sessionId && <SwarmVisualizer sessionId={sessionId} />}

        {/* Steps with tool calls */}
        {message.steps && message.steps.length > 0 && (
          <div className="mb-2 space-y-1">
            {message.steps.map((step) => (
              <StepItem key={step.id} step={step} defaultCollapsed={false} />
            ))}
          </div>
        )}

        {/* Fallback thinking block (no steps) */}
        {!message.steps?.length && message.thinking && !message.hookApproval && (
          <div className="mb-2 border-l-2 border-accent-blue/30 pl-3">
            <div
              className="flex items-center gap-1.5 cursor-pointer select-none group"
              onClick={() => setIsThinkingCollapsed(!isThinkingCollapsed)}
            >
              {isThinkingCollapsed
                ? <ChevronRight size={14} className="text-accent-blue/60" />
                : <ChevronDown size={14} className="text-accent-blue/60" />
              }
              <span className="text-xs text-accent-blue/80">Thinking</span>
              {isThinkingCollapsed && (
                <span className="text-[10px] text-dark-muted truncate max-w-[300px]">
                  {message.thinking.slice(0, 80).replace(/\n/g, ' ')}...
                </span>
              )}
            </div>
            {!isThinkingCollapsed && (
              <div className="mt-1 text-xs text-dark-muted italic whitespace-pre-wrap break-words">
                {message.thinking.replace(/\n{3,}/g, '\n\n')}
              </div>
            )}
          </div>
        )}

        {/* Fallback tool calls (no steps) */}
        {!message.steps?.length && message.toolCalls && message.toolCalls.length > 0 && !message.hookApproval && (
          <div className="mb-2 space-y-1">
            {message.toolCalls.map(toolCall => (
              <ToolCallItem key={toolCall.id} toolCall={toolCall} defaultCollapsed={toolCall.status !== 'running'} />
            ))}
          </div>
        )}

        {/* Content with Markdown */}
        {message.content ? (
          <div className="leading-relaxed">
            <MarkdownRenderer content={message.content.replace(/\n{3,}/g, '\n\n')} />
          </div>
        ) : !message.thinking && !message.hookApproval && message.steps?.length ? null : !message.thinking && !message.hookApproval && !message.steps?.length && isGenerating ? (
          <div className="flex items-center gap-3 py-1 text-xs text-dark-muted">
            <span className="flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-accent-blue animate-blink" />
              <span className="w-1.5 h-1.5 rounded-full bg-accent-blue animate-blink" style={{ animationDelay: '0.2s' }} />
              <span className="w-1.5 h-1.5 rounded-full bg-accent-blue animate-blink" style={{ animationDelay: '0.4s' }} />
            </span>
            Thinking...
          </div>
        ) : null}

        {/* Hook approval card — always at the bottom so it stays visible */}
        {message.hookApproval && (
          <div className="mt-3 border-l-2 border-accent-yellow/50 pl-3">
            <HookApprovalCard
              approval={message.hookApproval}
              onResolved={handleHookResolved}
            />
          </div>
        )}

        {/* Timestamp */}
        <div className="text-[10px] text-dark-muted/50 mt-1.5">
          {new Date(message.timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}
        </div>
      </div>
    </div>
  );
});

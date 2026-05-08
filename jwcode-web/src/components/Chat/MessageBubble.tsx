import { memo } from 'react';
import { Message } from '../../types';
import { MarkdownRenderer } from '../common/MarkdownRenderer';
import { StepItem } from './StepItem';
import { ToolCallItem } from './ToolCallItem';

interface MessageBubbleProps {
  message: Message;
}

export const MessageBubble = memo(function MessageBubble({
  message,
}: MessageBubbleProps) {
  const isUser = message.type === 'user';

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'} animate-fade-in`}>
      <div
        className={`max-w-[85%] md:max-w-[75%] px-4 py-3 rounded-2xl ${
          isUser
            ? 'bg-accent-blue text-white rounded-br-md'
            : 'bg-dark-surface border border-dark-border text-dark-text rounded-bl-md'
        }`}
      >
        {/* Steps - Collapsible with ToolCalls */}
        {message.steps && message.steps.length > 0 && (
          <div className="mb-1 space-y-0.5">
            {message.steps.map((step) => (
              <StepItem key={step.id} step={step} defaultCollapsed={false} />
            ))}
          </div>
        )}

        {/* Fallback: 没有 steps 时才独立展示 thinking / toolCalls */}
        {!message.steps?.length && message.thinking && (
          <div className="mb-1 p-1.5 bg-dark-bg border border-dark-border rounded-lg">
            <div className="text-xs text-accent-blue mb-0.5 flex items-center gap-1">
              <span>💭</span>
              <span>思考过程</span>
            </div>
            <div className="text-sm text-dark-muted italic">{message.thinking.replace(/\n\s*\n+/g, '\n')}</div>
          </div>
        )}

        {!message.steps?.length && message.toolCalls && message.toolCalls.length > 0 && (
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
        ) : !isUser && message.thinking ? (
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

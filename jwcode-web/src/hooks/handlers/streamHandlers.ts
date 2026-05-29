import { useChatStore } from '../../stores/chatStore';
import { useTokenStore } from '../../stores/tokenStore';

function recalcToken(sid: string) {
  const msgs = useChatStore.getState().messagesBySession[sid];
  if (msgs) useTokenStore.getState().recalculateFromMessages(msgs);
}

export function handleStreamMessage(rawType: string, rawData: any, sessionId: string) {
  const chatStore = useChatStore.getState();

  switch (rawType) {
    case 'start':
      chatStore.startGeneration(sessionId);
      chatStore.addMessage(sessionId, {
        id: `msg-${Date.now()}`, type: 'assistant', content: '', timestamp: Date.now(),
      });
      break;

    case 'content':
      chatStore.appendToLastMessage(sessionId, rawData || '');
      break;

    case 'thinking':
      chatStore.appendToLastMessageThinking(sessionId, rawData || '');
      break;

    case 'tool_call':
      try {
        const toolData = JSON.parse(rawData || '{}');
        const toolId = toolData.id || `tool-${Date.now()}`;
        const toolIndex = typeof toolData.index === 'number' ? toolData.index : undefined;
        const toolArgs = toolData.args || toolData.arguments || '';

        const msgs = chatStore.messagesBySession[sessionId] || [];
        const lastMsg = msgs[msgs.length - 1];
        const allToolCalls = [
          ...(lastMsg?.steps?.flatMap((s: any) => s.toolCalls || []) || []),
          ...(lastMsg?.toolCalls || [])
        ];
        const existing = allToolCalls.find((tc: any) =>
          (toolIndex !== undefined && tc.index === toolIndex) || tc.id === toolId
        );

        if (existing) {
          chatStore.updateToolCall(sessionId, toolId, { args: toolArgs });
        } else {
          chatStore.addToolCall(sessionId, {
            id: toolId, index: toolIndex, name: toolData.name || 'Unknown',
            args: toolArgs, status: 'running', timestamp: Date.now(),
          });
        }
      } catch (e) { console.error('Failed to parse tool call:', e); }
      break;

    case 'tool_result':
      try {
        const toolData = JSON.parse(rawData || '{}');
        const state = useChatStore.getState();
        const msgs = state.messagesBySession[sessionId] || [];
        const lastMsg = msgs[msgs.length - 1];
        const allToolCalls = [
          ...(lastMsg?.steps?.flatMap((s: any) => s.toolCalls || []) || []),
          ...(lastMsg?.toolCalls || [])
        ];
        const matched = allToolCalls.find((tc: any) => tc.name === toolData.toolName);
        if (matched) {
          chatStore.updateToolCall(sessionId, matched.id, { status: 'completed', result: toolData.result });
        }
      } catch (e) { console.error('Failed to parse tool result:', e); }
      break;

    case 'complete':
      chatStore.endGeneration(sessionId);
      recalcToken(sessionId);
      break;

    case 'generation_paused':
      chatStore.pauseGeneration(sessionId);
      break;

    case 'generation_resumed':
      chatStore.resumeGeneration(sessionId);
      break;

    case 'error':
      chatStore.endGeneration(sessionId);
      recalcToken(sessionId);
      chatStore.addMessage(sessionId, {
        id: `msg-${Date.now()}`, type: 'assistant',
        content: `❌ 错误: ${rawData}`, timestamp: Date.now(),
      });
      break;
  }
}

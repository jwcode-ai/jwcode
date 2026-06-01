import { useChatStore } from '../../stores/chatStore';
import { useTokenStore } from '../../stores/tokenStore';
import { toast } from '../../stores/toastStore';

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
        // 搜索最近 3 条消息（而非仅最后 1 条），防止新消息已创建导致匹配失败
        const recentMsgs = msgs.slice(Math.max(0, msgs.length - 3));
        const allToolCalls: any[] = [];
        for (const msg of recentMsgs) {
          const steps = msg.steps || [];
          for (const step of steps) {
            if (step.toolCalls) allToolCalls.push(...step.toolCalls);
          }
          if (msg.toolCalls) allToolCalls.push(...msg.toolCalls);
        }
        // 优先按 id 匹配，其次按 index，最后按 name + status='running'（FIFO：最早的时间戳）
        const toolName = toolData.toolName;
        const toolId = toolData.id;
        const toolIndex = typeof toolData.index === 'number' ? toolData.index : undefined;
        let matched: any = undefined;
        if (toolId) {
          matched = allToolCalls.find((tc: any) => tc.id === toolId);
        }
        if (!matched && toolIndex !== undefined) {
          matched = allToolCalls.find((tc: any) => tc.index === toolIndex && tc.name === toolName);
        }
        if (!matched) {
          const runningByName = allToolCalls.filter((tc: any) =>
            tc.name === toolName && tc.status === 'running'
          );
          runningByName.sort((a: any, b: any) => (a.timestamp || 0) - (b.timestamp || 0));
          matched = runningByName[0];
        }
        if (matched) {
          const duration = Math.floor((Date.now() - (matched.timestamp || Date.now())) / 1000);
          const isError = typeof toolData.result === 'string' && toolData.result.startsWith('Error:');
          chatStore.updateToolCall(sessionId, matched.id, {
            status: isError ? 'error' : 'completed',
            result: toolData.result,
            duration,
          });
        }
      } catch (e) { console.error('Failed to parse tool result:', e); }
      break;

    case 'complete':
      chatStore.endGeneration(sessionId);
      break;

    case 'generation_paused':
      chatStore.pauseGeneration(sessionId);
      break;

    case 'generation_resumed':
      chatStore.resumeGeneration(sessionId);
      break;

    case 'context_compressed':
      try {
        const cd = JSON.parse(rawData || '{}');
        const msg = `上下文压缩: ${cd.originalCount} → ${cd.compressedCount} 条消息, 释放 ${cd.tokensSaved?.toLocaleString() || 0} tokens`;
        chatStore.appendToLastMessage(sessionId, `\n\n---\n📦 ${msg}\n\n`);
        toast.info(msg, 5000);
        useTokenStore.getState().setCompacting({
          originalCount: cd.originalCount || 0,
          compressedCount: cd.compressedCount || 0,
          tokensSaved: cd.tokensSaved || 0,
        });
      } catch (e) { console.error('Failed to parse context_compressed:', e); }
      break;

    case 'error':
      chatStore.endGeneration(sessionId);
      chatStore.addMessage(sessionId, {
        id: `msg-${Date.now()}`, type: 'assistant',
        content: `❌ 错误: ${rawData}`, timestamp: Date.now(),
      });
      break;
  }
}

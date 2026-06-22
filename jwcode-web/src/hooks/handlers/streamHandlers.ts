import { useChatStore } from '../../stores/chatStore';
import { useTokenStore } from '../../stores/tokenStore';
import { toast } from '../../stores/toastStore';
import { errLog } from '../../stores/errorStore';

export function handleStreamMessage(rawType: string, rawData: any, sessionId: string) {
  const chatStore = useChatStore.getState();

  switch (rawType) {
    case 'start':
      chatStore.startGeneration(sessionId);
      chatStore.addMessage(sessionId, {
        id: crypto.randomUUID(), type: 'assistant', content: '', timestamp: Date.now(),
      });
      break;

    case 'content':
      { const filtered = (rawData || '').replace(/\[FINISH\]/g, ''); if (filtered) chatStore.appendToLastMessage(sessionId, filtered); }
      break;

    case 'thinking':
      chatStore.appendToLastMessageThinking(sessionId, rawData || '');
      break;

    case 'tool_call':
      try {
        const toolData = JSON.parse(rawData || '{}');
        const toolId = toolData.id || crypto.randomUUID();
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
      } catch (e) { errLog.warn('Failed to parse tool call', String(e)); }
      break;

    case 'tool_result':
      try {
        const toolData = JSON.parse(rawData || '{}');
        const state = useChatStore.getState();
        const msgs = state.messagesBySession[sessionId] || [];
        const allToolCalls: any[] = [];
        for (const msg of msgs) {
          const steps = msg.steps || [];
          for (const step of steps) {
            if (step.toolCalls) allToolCalls.push(...step.toolCalls);
          }
          if (msg.toolCalls) allToolCalls.push(...msg.toolCalls);
        }
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
        if (!matched) {
          const erroredByName = allToolCalls.filter((tc: any) =>
            tc.name === toolName && tc.status === 'error' &&
            tc.result === '生成已结束（工具执行超时或连接中断）'
          );
          erroredByName.sort((a: any, b: any) => (b.timestamp || 0) - (a.timestamp || 0));
          matched = erroredByName[0];
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
      } catch (e) { errLog.warn('Failed to parse tool result', String(e)); }
      break;

    case 'complete':
      chatStore.endGeneration(sessionId);
      // 生成结束时刷新 token 估算（若后端未返回 usage，则使用启发式估算）
      {
        const msgs = useChatStore.getState().messagesBySession[sessionId] || [];
        if (msgs.length > 0) {
          useTokenStore.getState().recalculateFromMessages(msgs);
        }
      }
      break;

    case 'generation_paused':
      chatStore.pauseGeneration(sessionId);
      break;

    case 'generation_resumed':
      chatStore.resumeGeneration(sessionId);
      break;

    case 'compaction_progress':
      try {
        const cp = JSON.parse(rawData || '{}');
        useTokenStore.getState().setCompactionProgress({
          stage: cp.stage || '',
          percent: cp.percent || 0,
          message: cp.message || '',
        });
      } catch (e) { console.error('Failed to parse compaction_progress:', e); }
      break;

    case 'context_compressed':
      try {
        const cd = JSON.parse(rawData || '{}');
        const msg = `Context compressed: ${cd.originalCount} -> ${cd.compressedCount} msgs, freed ${cd.tokensSaved} tokens`;
        chatStore.appendToLastMessage(sessionId, `\n\n---\n${msg}\n\n`);
        toast.info(msg, 5000);
        useTokenStore.getState().setCompacting({
          originalCount: cd.originalCount || 0,
          compressedCount: cd.compressedCount || 0,
          tokensSaved: cd.tokensSaved || 0,
        });
        useTokenStore.getState().setCompactionProgress(null);
      } catch (e) { console.error('Failed to parse context_compressed:', e); }
      break;

    case 'error':
      chatStore.endGeneration(sessionId);
      chatStore.addMessage(sessionId, {
        id: crypto.randomUUID(), type: 'assistant',
        content: 'Error occurred', timestamp: Date.now(),
      });
      break;

    case 'tombstone':
      try {
        const td = JSON.parse(rawData || '{}');
        const messageIds: string[] = td.messageIds || [];
        if (messageIds.length > 0) {
          chatStore.applyTombstone(sessionId, messageIds);
        }
      } catch (e) { errLog.warn('Failed to parse tombstone', String(e)); }
      break;
  }
}

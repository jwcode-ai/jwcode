import { useChatStore } from '../../stores/chatStore';
import { usePlanStore } from '../../stores/planStore';

export function handlePlanMessage(rawType: string, rawData: any, sessionId: string) {
  const planStore = usePlanStore.getState();
  const chatStore = useChatStore.getState();

  switch (rawType) {
    case 'plan_start':
      chatStore.startGeneration(sessionId);
      chatStore.addMessage(sessionId, {
        id: `msg-${Date.now()}`, type: 'assistant', content: '', timestamp: Date.now(),
      });
      break;

    case 'plan_thinking':
      planStore.setThinkingStatus(sessionId, rawData || '正在分析需求...');
      break;

    case 'plan_complete':
      chatStore.endGeneration(sessionId);
      break;

    case 'plan_error':
      chatStore.endGeneration(sessionId);
      break;

    case 'plan_mode_change': {
      const modeData = JSON.parse(rawData || '{}');
      if (modeData.newMode === 'plan') planStore.setMode('plan');
      else if (modeData.newMode === 'act' || modeData.newMode === 'normal') planStore.setMode('act');
      break;
    }
  }
}

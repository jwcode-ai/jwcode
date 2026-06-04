import { useEffect, useCallback } from 'react';
import wsService from '../services/websocket';
import { useChatStore } from '../stores/chatStore';
import { useSessionStore } from '../stores/sessionStore';
import { LogEntry } from '../types';
import { handlePlanMessage } from './handlers/planHandlers';
import { handleStreamMessage } from './handlers/streamHandlers';
import { handleSystemMessage } from './handlers/systemHandlers';
import { handleHookAsk, handleTaskUpdate, handleStepMessage, handleTodoUpdate, handleTodoItemDone } from './handlers/interactionHandlers';

const DEBUG = false;

interface UseWebSocketOptions {
  activeTab: string;
  setLogs: React.Dispatch<React.SetStateAction<LogEntry[]>>;
  setUnreadLogs: React.Dispatch<React.SetStateAction<number>>;
}

export function useWebSocket({ activeTab, setLogs, setUnreadLogs }: UseWebSocketOptions) {
  const chatStore = useChatStore;

  const getLatestStep = useCallback((sessionId: string) => {
    const state = chatStore.getState();
    const msgs = state.messagesBySession[sessionId] || [];
    const lastMessage = msgs[msgs.length - 1];
    return lastMessage?.steps?.[lastMessage.steps.length - 1];
  }, [chatStore]);

  const ensureStep = useCallback((sessionId: string, type: string, stepData: any) => {
    const lastStep = getLatestStep(sessionId);
    const isCompleteEvent = type === 'step_complete';
    const stepFinished = lastStep && (lastStep.status === 'success' || lastStep.status === 'error');

    if (!lastStep || (stepFinished && !isCompleteEvent)) {
      const stepTitle = stepData?.step || type.replace('step_', '').replace('_', ' ');
      chatStore.getState().addStep(sessionId, {
        id: `step-${Date.now()}`,
        title: stepTitle,
        description: stepData?.description || '执行中...',
        status: 'running',
        timestamp: Date.now(),
      });
      return getLatestStep(sessionId);
    }
    return lastStep;
  }, [getLatestStep, chatStore]);

  const handleWSMessage = useCallback((msg: { type: string; data?: string; sessionId?: string }) => {
    const rawType = msg.type;
    const rawData = msg.data;
    const activeSessionId = useSessionStore.getState().activeSessionId;
    const sessionId = msg.sessionId || activeSessionId;
    if (!sessionId) { DEBUG && console.warn('[WS] no sessionId, message dropped:', rawType); return; }

    // Category-based routing
    if (rawType === 'hook_ask') { handleHookAsk(rawData, sessionId); return; }
    if (rawType === 'task_update') { handleTaskUpdate(rawData, sessionId); return; }
    if (rawType.startsWith('step_')) { handleStepMessage(rawType, rawData, sessionId, ensureStep); return; }
    if (rawType.startsWith('plan_')) {
      try { handlePlanMessage(rawType, rawData, sessionId); } catch (e) { console.error(`Failed to handle ${rawType}:`, e); }
      return;
    }
    if (rawType === 'step_prompt') {
      try { handlePlanMessage(rawType, rawData, sessionId); } catch (e) { console.error(`Failed to handle ${rawType}:`, e); }
      return;
    }
    if (rawType === 'todo_update') { handleTodoUpdate(rawData, sessionId); return; }
    if (rawType === 'todo_item_done') { handleTodoItemDone(rawData, sessionId); return; }
    if (rawType === 'todo_progress') return; // no-op

    // Stream messages
    if (['start', 'content', 'thinking', 'tool_call', 'tool_result', 'complete', 'generation_paused', 'generation_resumed', 'error'].includes(rawType)) {
      handleStreamMessage(rawType, rawData, sessionId);
      return;
    }

    // System messages
    if (['token_update', 'auth_required', 'auth_success', 'auth_failed', 'log', 'commands_list', 'ping', 'workspace_changed', 'degradation_update', 'doctor_result'].includes(rawType)) {
      handleSystemMessage(rawType, rawData, sessionId, { activeTab, setLogs, setUnreadLogs });
      return;
    }

    DEBUG && console.log('[WS] unhandled message type:', rawType);
  }, [activeTab, ensureStep, setLogs, setUnreadLogs]);

  useEffect(() => {
    wsService.connect();
    const unsub = wsService.onMessage(handleWSMessage);
    return () => unsub();
  }, [handleWSMessage]);
}

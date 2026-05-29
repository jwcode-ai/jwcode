import { useChatStore } from '../../stores/chatStore';
import { useSessionStore } from '../../stores/sessionStore';
import { useHookApprovalStore } from '../../stores/useHookApprovalStore';
import wsService from '../../services/websocket';
import type { SessionTask } from '../../types';

const DEBUG = false;

interface InteractionCtx {
  ensureStep: (sessionId: string, type: string, stepData: any) => any;
  sessionId: string;
}

export function handleHookAsk(rawData: any, sessionId: string) {
  try {
    const data = typeof rawData === 'string' ? JSON.parse(rawData) : (rawData || {});
    const approvalStore = useHookApprovalStore.getState();
    const { approvalId, toolName, askPayload } = data;

    if (approvalStore.isSessionAllowed(toolName)) {
      wsService.send({ type: 'hook_allow' as any, data: JSON.stringify({ approvalId }) });
      return;
    }

    const chatStore = useChatStore.getState();
    chatStore.addMessage(sessionId, {
      id: `hook-approval-${approvalId || Date.now()}`, type: 'system',
      content: '', timestamp: Date.now(),
      hookApproval: {
        approvalId: approvalId || '', toolName: toolName || 'unknown',
        askPayload: askPayload || '', status: 'pending', timestamp: Date.now(),
      },
    });

    approvalStore.addApproval({
      approvalId: approvalId || '', toolName: toolName || 'unknown',
      askPayload: askPayload || '', timestamp: Date.now(),
    });
  } catch (e) {
    DEBUG && console.warn('[WS] hook_ask parse error:', e);
  }
}

export function handleTaskUpdate(rawData: any, sessionId: string) {
  try {
    const data = typeof rawData === 'string' ? JSON.parse(rawData) : (rawData || {});
    const sessionStore = useSessionStore.getState();
    const { action, taskId, data: taskData } = data;

    if (action === 'created' && taskData) {
      const sessionTask: SessionTask = {
        id: taskId || `task-${Date.now()}`, title: taskData.title || '',
        completed: taskData.status === 'COMPLETED', createdAt: Date.parse(taskData.createdAt) || Date.now(),
        backendId: taskId, backendStatus: taskData.status, description: taskData.description,
      };
      const existing = sessionStore.tasksBySession[sessionId] || [];
      sessionStore.setSessionTasks(sessionId, [...existing, sessionTask]);
    } else if ((action === 'updated' || action === 'status_changed') && taskData) {
      sessionStore.updateTaskPlanStatus(sessionId, taskId, {
        backendStatus: taskData.status, progress: taskData.progress,
      });
    } else if (action === 'deleted') {
      const existing = sessionStore.tasksBySession[sessionId] || [];
      sessionStore.setSessionTasks(sessionId, existing.filter(t => t.id !== taskId && t.backendId !== taskId));
    }
  } catch (e) {
    DEBUG && console.warn('[WS] task_update parse error:', e);
  }
}

export function handleStepMessage(rawType: string, rawData: any, sessionId: string, ensureStep: InteractionCtx['ensureStep']) {
  try {
    const stepData = JSON.parse(rawData || '{}');
    const lastStep = ensureStep(sessionId, rawType, stepData);

    if (lastStep) {
      const chatStore = useChatStore.getState();
      switch (rawType) {
        case 'step_start':
          chatStore.updateStep(sessionId, lastStep.id, {
            title: stepData.step || lastStep.title,
            description: stepData.description || lastStep.description,
            status: stepData.status === 'start' ? 'running' : (stepData.status || 'running')
          });
          break;
        case 'step_thinking':
          chatStore.updateStep(sessionId, lastStep.id, { thought: stepData.thought });
          break;
        case 'step_action':
          chatStore.updateStep(sessionId, lastStep.id, { action: stepData.action });
          break;
        case 'step_complete':
          chatStore.updateStep(sessionId, lastStep.id, { status: 'success', result: stepData.result });
          break;
      }
    }
  } catch (e) {
    console.error(`Failed to parse ${rawType}:`, e);
  }
}

export function handleTodoUpdate(rawData: any, sessionId: string) {
  try {
    const parsed = typeof rawData === 'string' ? JSON.parse(rawData) : (rawData || {});
    let tasks: any[] = [];
    if (Array.isArray(parsed)) tasks = parsed;
    else if (parsed.tasks?.length) tasks = parsed.tasks;
    else if (parsed.items?.length) tasks = parsed.items;

    if (tasks.length > 0 && sessionId) {
      const sessionStore = useSessionStore.getState();
      tasks.forEach((task: any) => {
        const title = task.content || task.title || task.name || '';
        if (title.trim()) {
          const existing = sessionStore.getSessionTasks(sessionId);
          const dup = existing.find(t => t.title === title.trim());
          if (!dup) {
            sessionStore.addSessionTask(sessionId, title.trim());
            const isCompleted = task.completed || task.status === 'completed' || task.status === 'COMPLETED' || task.status === 'done' || task.activeForm === 'completed';
            if (isCompleted) {
              const updated = sessionStore.getSessionTasks(sessionId);
              const added = updated.find(t => t.title === title.trim());
              if (added) sessionStore.toggleSessionTask(sessionId, added.id);
            }
          }
        }
      });
    }
  } catch (e) { DEBUG && console.warn('[WS] todo_update parse error:', e); }
}

export function handleTodoItemDone(rawData: any, sessionId: string) {
  try {
    const doneData = typeof rawData === 'string' ? JSON.parse(rawData) : (rawData || {});
    const taskTitle = doneData.title || doneData.name || '';
    if (taskTitle && sessionId) {
      const tasks = useSessionStore.getState().getSessionTasks(sessionId);
      const match = tasks.find(t => t.title === taskTitle && !t.completed);
      if (match) useSessionStore.getState().toggleSessionTask(sessionId, match.id);
    }
  } catch (e) { DEBUG && console.warn('[WS] todo_item_done parse error:', e); }
}

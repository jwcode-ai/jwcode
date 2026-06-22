import { useChatStore } from '../../stores/chatStore';
import { useExecutionModeStore } from '../../stores/executionModeStore';
import { useSessionStore } from '../../stores/sessionStore';
import { normalizeBlackboardTaskStatus, useWorkflowStore, type BlackboardTask } from '../../stores/workflowStore';
import type { SessionTask } from '../../types';

const parseData = (rawData: any) => {
  if (rawData == null || rawData === '') return {};
  if (typeof rawData !== 'string') return rawData;
  try {
    return JSON.parse(rawData);
  } catch {
    return rawData;
  }
};

const textFromData = (data: any, fallback = ''): string => {
  if (typeof data === 'string') return data;
  return String(data?.content || data?.message || data?.text || data?.error || fallback || '');
};

const listFromData = (data: any): any[] => {
  if (Array.isArray(data)) return data;
  if (Array.isArray(data?.tasks)) return data.tasks;
  if (Array.isArray(data?.items)) return data.items;
  if (Array.isArray(data?.planTasks)) return data.planTasks;
  return [];
};

const normalizePlanTask = (task: any, sessionId: string, index = 0): BlackboardTask => {
  const id = String(task.id || task.taskId || task.backendId || `plan-task-${index + 1}`);
  const status = normalizeBlackboardTaskStatus(task.status || task.planStatus, task.completed);
  const timestamp = Date.now() + index;
  return {
    id,
    sessionId,
    title: String(task.title || task.name || task.content || task.description || `Task ${index + 1}`),
    description: task.description || task.details || task.content,
    status,
    source: 'plan',
    agentType: task.agentType || task.agent || task.role,
    progress: task.progress != null ? Number(task.progress) : undefined,
    result: task.result,
    error: task.error,
    dependencies: Array.isArray(task.dependencies) ? task.dependencies.map(String) : undefined,
    startedAt: task.startedAt ? Number(task.startedAt) : status === 'running' ? timestamp : undefined,
    completedAt: task.completedAt ? Number(task.completedAt) : status === 'completed' || status === 'failed' ? timestamp : undefined,
    createdAt: task.createdAt ? Number(task.createdAt) || timestamp : timestamp,
    updatedAt: timestamp,
    raw: task,
  };
};

const toSessionTask = (task: BlackboardTask): SessionTask => ({
  id: task.id,
  title: task.title,
  completed: task.status === 'completed',
  createdAt: task.createdAt,
  description: task.description,
  planStatus: task.status,
  agentType: task.agentType,
  dependencies: task.dependencies,
  result: task.result,
  error: task.error,
  startedAt: task.startedAt,
  completedAt: task.completedAt,
  progress: task.progress,
});

const mergeSessionTask = (sessionId: string, task: BlackboardTask) => {
  const sessionStore = useSessionStore.getState();
  const existing = sessionStore.getSessionTasks(sessionId);
  const sessionTask = toSessionTask(task);
  if (existing.some((item) => item.id === task.id || item.backendId === task.id)) {
    sessionStore.updateTaskPlanStatus(sessionId, task.id, sessionTask);
    return;
  }
  sessionStore.setSessionTasks(sessionId, [...existing, sessionTask]);
};

const ensureAssistantMessage = (sessionId: string) => {
  const chatStore = useChatStore.getState();
  const messages = chatStore.getMessages(sessionId);
  const last = messages[messages.length - 1];
  if (!last || last.type !== 'assistant') {
    chatStore.addMessage(sessionId, {
      id: `plan-${Date.now()}`,
      type: 'assistant',
      content: '',
      timestamp: Date.now(),
    });
  }
};

export function handlePlanMessage(rawType: string, rawData: any, sessionId: string) {
  const planStore = useExecutionModeStore.getState();
  const chatStore = useChatStore.getState();
  const board = useWorkflowStore.getState();
  const data = parseData(rawData);

  switch (rawType) {
    case 'plan_start': {
      chatStore.startGeneration(sessionId);
      ensureAssistantMessage(sessionId);
      const message = textFromData(data, 'Plan started');
      if (message) planStore.setThinkingStatus(sessionId, message);
      board.recordEvent(sessionId, {
        type: rawType,
        source: 'plan',
        title: 'Plan started',
        message,
        status: 'running',
        data,
      });
      break;
    }

    case 'plan_thinking': {
      const status = textFromData(data, 'Analyzing request...');
      planStore.setThinkingStatus(sessionId, status);
      ensureAssistantMessage(sessionId);
      chatStore.setThinking(sessionId, status);
      board.recordEvent(sessionId, {
        type: rawType,
        source: 'plan',
        title: 'Plan thinking',
        message: status,
        status: 'running',
        data,
      });
      break;
    }

    case 'plan_complete': {
      const content = data?.plan || data?.content || data?.result || data?.markdown;
      if (content) {
        planStore.setPlanContent(String(content));
        ensureAssistantMessage(sessionId);
        const messages = chatStore.getMessages(sessionId);
        const last = messages[messages.length - 1];
        if (last?.type === 'assistant' && !last.content.trim()) {
          chatStore.updateLastMessage(sessionId, String(content));
        }
      } else {
        const lastAssistant = [...chatStore.getMessages(sessionId)].reverse().find((message) => message.type === 'assistant');
        if (lastAssistant?.content) planStore.setPlanContent(lastAssistant.content);
      }
      chatStore.endGeneration(sessionId);
      board.recordEvent(sessionId, {
        type: rawType,
        source: 'plan',
        title: 'Plan complete',
        message: textFromData(data, 'Plan complete'),
        status: 'completed',
        data,
      });
      break;
    }

    case 'plan_error': {
      const error = textFromData(data, 'Plan failed');
      ensureAssistantMessage(sessionId);
      chatStore.updateLastMessage(sessionId, `Plan failed: ${error}`);
      chatStore.endGeneration(sessionId, error);
      board.recordEvent(sessionId, {
        type: rawType,
        source: 'plan',
        title: 'Plan failed',
        message: error,
        status: 'failed',
        data,
      });
      break;
    }

    case 'plan_tasks': {
      const tasks = listFromData(data).map((task, index) => normalizePlanTask(task, sessionId, index));
      board.setTasks(sessionId, tasks, 'plan');
      useSessionStore.getState().setSessionTasks(sessionId, tasks.map(toSessionTask));
      board.recordEvent(sessionId, {
        type: rawType,
        source: 'plan',
        title: 'Plan tasks updated',
        message: `${tasks.length} tasks`,
        status: 'pending',
        data,
      });
      break;
    }

    case 'plan_task_start':
    case 'plan_task_update':
    case 'plan_task_result': {
      const task = normalizePlanTask(data, sessionId);
      const status =
        rawType === 'plan_task_start' ? 'running' :
        rawType === 'plan_task_result' ? normalizeBlackboardTaskStatus(data.status || (data.error ? 'failed' : 'completed')) :
        normalizeBlackboardTaskStatus(data.status || data.planStatus);
      const nextTask = {
        ...task,
        status,
        startedAt: task.startedAt || (status === 'running' ? Date.now() : undefined),
        completedAt: task.completedAt || (status === 'completed' || status === 'failed' ? Date.now() : undefined),
      };
      board.upsertTask(sessionId, nextTask);
      mergeSessionTask(sessionId, nextTask);
      board.recordEvent(sessionId, {
        type: rawType,
        source: 'plan',
        title: nextTask.title,
        message: nextTask.result || nextTask.error || nextTask.description,
        status: nextTask.status,
        taskId: nextTask.id,
        data,
      });
      break;
    }

    case 'step_prompt': {
      const prompt = textFromData(data);
      const taskId = data?.taskId || data?.id;
      if (taskId) {
        board.upsertTask(sessionId, {
          id: String(taskId),
          title: data.title || data.step || String(taskId),
          status: 'running',
          source: 'plan',
          description: prompt,
        });
      }
      board.recordEvent(sessionId, {
        type: rawType,
        source: 'plan',
        title: data?.step || 'Step prompt',
        message: prompt,
        status: 'running',
        taskId: taskId ? String(taskId) : undefined,
        data,
      });
      break;
    }

    case 'plan_mode_change': {
      const newMode = data?.newMode || data?.frontendMode;
      if (newMode === 'plan') planStore.applyBackendMode('plan');
      else if (newMode === 'act' || newMode === 'normal') planStore.applyBackendMode('act');
      board.recordEvent(sessionId, {
        type: rawType,
        source: 'system',
        title: 'Mode changed',
        message: `${data?.previousMode || 'unknown'} -> ${newMode || 'unknown'}`,
        status: data?.success === false ? 'failed' : 'completed',
        data,
      });
      break;
    }
  }
}

import { useChatStore } from '../../stores/chatStore';
import { useSessionStore } from '../../stores/sessionStore';
import { usePlanStore, diffTasks } from '../../stores/planStore';
import { useTokenStore } from '../../stores/tokenStore';
import wsService from '../../services/websocket';
import type { SessionTask } from '../../types';

export function handlePlanMessage(rawType: string, rawData: any, sessionId: string) {
  const planStore = usePlanStore.getState();
  const chatStore = useChatStore.getState();

  function recalcToken(sid: string) {
    const msgs = useChatStore.getState().messagesBySession[sid];
    if (msgs) useTokenStore.getState().recalculateFromMessages(msgs);
  }

  switch (rawType) {
    case 'plan_start':
      planStore.startPlanning(sessionId, rawData || '');
      chatStore.startGeneration(sessionId);
      chatStore.addMessage(sessionId, {
        id: `msg-${Date.now()}`, type: 'assistant', content: '', timestamp: Date.now(),
      });
      break;

    case 'plan_thinking':
      planStore.setThinkingStatus(sessionId, rawData || '正在分析需求...');
      break;

    case 'plan_tasks': {
      const data = JSON.parse(rawData || '{}');
      let tasksData: any = data.structuredTasks?.length ? data.structuredTasks
        : data.tasks?.structuredTasks?.length ? data.tasks.structuredTasks
        : Array.isArray(data.tasks) ? data.tasks : null;

      if (tasksData) {
        const sessionStore = useSessionStore.getState();
        const flattenTasks = (list: any[]): SessionTask[] => {
          const result: SessionTask[] = [];
          for (const t of list) {
            const task: SessionTask = {
              id: t.id || `task-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
              title: t.title || t.name || '', completed: false, createdAt: Date.now(),
              planStatus: 'pending', agentType: t.agentType || 'default',
              dependencies: t.dependencies || [], description: t.description,
              stepNumber: t.stepNumber, action: t.action, stepPrompt: t.stepPrompt,
              executionMode: t.executionMode, phase: t.phase, parallelGroup: t.parallelGroup,
              context: t.context,
            };
            if (t.children?.length) task.children = flattenTasks(t.children);
            result.push(task);
          }
          return result;
        };
        sessionStore.setSessionTasks(sessionId, flattenTasks(tasksData));

        const buildStructured = (list: any[]): any[] => list.map((t: any) => ({
          id: t.id || `task-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
          title: t.title || t.name || '', description: t.description || '',
          status: t.status || 'pending', agentType: t.agentType || 'default',
          dependencies: t.dependencies || [], executionMode: t.executionMode || 'SEQUENTIAL',
          phase: t.phase || 'GENERAL', parallelGroup: t.parallelGroup,
          stepNumber: t.stepNumber, action: t.action, stepPrompt: t.stepPrompt,
          context: t.context, progress: t.progress || 0,
          children: t.children?.length ? buildStructured(t.children) : [],
        }));
        const structured = buildStructured(tasksData);

        const oldTasks = planStore.getStructuredTasks(sessionId);
        if (oldTasks.length > 0) {
          const diff = diffTasks(oldTasks, structured);
          planStore.setTaskDiff(sessionId, diff);
        }
        planStore.setStructuredTasks(sessionId, structured);
        window.dispatchEvent(new CustomEvent('switch-tab', { detail: 'plan' }));
      }

      const analysis = data.analysis || '';
      const currentPlan = planStore.getPlan(sessionId);
      if (currentPlan) {
        planStore.setPlan(sessionId, { ...currentPlan, tasks: [], phase: 'executing' });
      } else {
        planStore.startPlanning(sessionId, analysis || '任务计划');
        setTimeout(() => {
          const p = planStore.getPlan(sessionId);
          if (p) planStore.setPlan(sessionId, { ...p, tasks: [], phase: 'executing' });
        }, 0);
      }
      break;
    }

    case 'plan_refine':
      planStore.setPlanRefining(false);
      break;

    case 'plan_task_start': {
      const d = JSON.parse(rawData || '{}');
      useSessionStore.getState().updateTaskPlanStatus(sessionId, d.id, { planStatus: 'running', startedAt: Date.now(), logs: d.logs || [] });
      break;
    }

    case 'plan_task_update': {
      const d = JSON.parse(rawData || '{}');
      useSessionStore.getState().updateTaskPlanStatus(sessionId, d.id, { progress: d.progress, logs: d.logs });
      break;
    }

    case 'plan_task_result': {
      const d = JSON.parse(rawData || '{}');
      useSessionStore.getState().updateTaskPlanStatus(sessionId, d.id, {
        planStatus: d.status || 'completed', result: d.result, error: d.error,
        completedAt: Date.now(), logs: d.logs,
      });
      break;
    }

    case 'plan_complete': {
      const planData = JSON.parse(rawData || '{}');
      chatStore.endGeneration(sessionId);
      recalcToken(sessionId);
      planStore.setPlanRefining(false);
      if (planData.status === 'waiting_confirm') {
        planStore.setPhase(sessionId, 'planning');
        planStore.setShowConfirmButton(true);
      } else {
        planStore.setPhase(sessionId, 'result');
        planStore.setShowConfirmButton(false);
        const nextMsg = planStore.dequeueMessage();
        if (nextMsg) {
          const sid = useSessionStore.getState().activeSessionId;
          if (sid) {
            wsService.setSessionId(sid);
            wsService.send({ type: 'chat', sessionId: sid, message: nextMsg.content });
          }
        }
      }
      break;
    }

    case 'plan_error':
      chatStore.endGeneration(sessionId);
      recalcToken(sessionId);
      planStore.setPhase(sessionId, 'error');
      break;

    case 'plan_mode_change': {
      const modeData = JSON.parse(rawData || '{}');
      if (modeData.newMode === 'plan') planStore.setMode('plan');
      else if (modeData.newMode === 'act' || modeData.newMode === 'normal') planStore.setMode('act');
      break;
    }

    case 'step_prompt': {
      const data = JSON.parse(rawData || '{}');
      planStore.setCurrentStepPrompt({
        sessionId, taskId: data.taskId || '', stepIndex: data.stepIndex || 0,
        stepNumber: data.stepNumber || 1, description: data.description || '',
        action: data.action || '', stepPrompt: data.stepPrompt || '',
        agentType: data.agentType || '',
      });
      break;
    }
  }
}

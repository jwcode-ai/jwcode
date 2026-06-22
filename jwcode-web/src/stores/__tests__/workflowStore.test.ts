import { beforeEach, describe, expect, it } from 'vitest';
import { useWorkflowStore } from '../workflowStore';

describe('workflowStore', () => {
  beforeEach(() => {
    localStorage.clear();
    useWorkflowStore.setState({
      bySession: {},
      byRun: {},
      agentsBySession: {},
      tasksBySession: {},
      eventsBySession: {},
    });
  });

  it('tracks workflow lifecycle progress by session and run', () => {
    const store = useWorkflowStore.getState();

    store.upsert('session-1', {
      runId: 'run-1',
      status: 'RUNNING',
      completedPhases: 1,
      totalPhases: 3,
      completedEffects: 2,
      totalEffects: 5,
      tokensUsed: 10,
      tokensRemaining: 90,
      lastEventType: 'effect.completed',
    });

    const bySession = useWorkflowStore.getState().getForSession('session-1');
    const byRun = useWorkflowStore.getState().byRun['run-1'];

    expect(bySession).toMatchObject({
      runId: 'run-1',
      status: 'RUNNING',
      completedPhases: 1,
      totalPhases: 3,
      completedEffects: 2,
      totalEffects: 5,
      tokensUsed: 10,
      tokensRemaining: 90,
      lastEventType: 'effect.completed',
    });
    expect(byRun).toEqual(bySession);
  });

  it('stores workflow errors as workflow state only', () => {
    useWorkflowStore.getState().fail('session-err', 'run-err', 'boom');

    expect(useWorkflowStore.getState().getForSession('session-err')).toMatchObject({
      runId: 'run-err',
      status: 'ERROR',
      error: 'boom',
    });
  });

  it('persists the latest run and progress counters', () => {
    useWorkflowStore.getState().upsert('session-persist', {
      runId: 'run-persist',
      status: 'PAUSED',
      completedPhases: 2,
      totalPhases: 4,
      completedEffects: 7,
      totalEffects: 9,
      tokensUsed: 123,
      tokensRemaining: 456,
      lastEventType: 'run.paused',
    });

    const raw = localStorage.getItem('jwcode-workflow-store');

    expect(raw).toBeTruthy();
    const parsed = JSON.parse(raw || '{}');
    expect(parsed.state.bySession['session-persist']).toMatchObject({
      runId: 'run-persist',
      status: 'PAUSED',
      completedPhases: 2,
      totalPhases: 4,
      completedEffects: 7,
      totalEffects: 9,
      lastEventType: 'run.paused',
    });
  });

  it('stores plan tasks in the session blackboard', () => {
    const store = useWorkflowStore.getState();

    store.setTasks('session-plan', [
      { id: 'task-1', title: 'Read code', status: 'pending', source: 'plan' },
      { id: 'task-2', title: 'Write tests', status: 'running', source: 'plan', progress: 30 },
    ]);
    store.upsertTask('session-plan', {
      id: 'task-1',
      title: 'Read code',
      status: 'completed',
      source: 'plan',
    });

    expect(useWorkflowStore.getState().getTasksForSession('session-plan')).toMatchObject([
      { id: 'task-2', status: 'running', progress: 30 },
      { id: 'task-1', status: 'completed' },
    ]);
  });

  it('ingests agent flow events into agents, tasks, and events', () => {
    const store = useWorkflowStore.getState();

    store.ingestAgentFlowEvent('session-agent', {
      eventType: 'task_start',
      data: { taskId: 'a1', type: 'EXECUTION', description: 'Implement change', agentId: 'coder-1' },
    });
    store.ingestAgentFlowEvent('session-agent', {
      eventType: 'task_complete',
      data: { taskId: 'a1', type: 'EXECUTION', success: true, agentId: 'coder-1' },
    });

    expect(useWorkflowStore.getState().getAgentsForSession('session-agent')[0]).toMatchObject({
      id: 'coder-1',
      name: 'Coder',
      status: 'completed',
    });
    expect(useWorkflowStore.getState().getTasksForSession('session-agent')).toContainEqual(expect.objectContaining({
      id: 'a1',
      status: 'completed',
      source: 'agent',
    }));
    expect(useWorkflowStore.getState().getEventsForSession('session-agent')).toHaveLength(2);
  });
});

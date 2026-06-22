import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render } from '@testing-library/react';
import { useChatStore } from '../../stores/chatStore';
import { useSessionStore } from '../../stores/sessionStore';
import { useWorkflowStore } from '../../stores/workflowStore';
import { useWebSocket } from '../useWebSocket';

const mockState = vi.hoisted(() => ({
  messageHandler: undefined as
    | ((message: { type: string; data?: string; sessionId?: string }) => void)
    | undefined,
  wsServiceMock: {
    connect: vi.fn(),
    onMessage: vi.fn((handler) => {
      mockState.messageHandler = handler;
      return vi.fn();
    }),
    onClose: vi.fn(() => vi.fn()),
  },
}));

vi.mock('../../services/websocket', () => ({
  default: mockState.wsServiceMock,
  wsService: mockState.wsServiceMock,
}));

function Harness() {
  useWebSocket({
    activeTab: 'chat',
    setLogs: vi.fn(),
    setUnreadLogs: vi.fn(),
  });
  return null;
}

describe('useWebSocket workflow messages', () => {
  beforeEach(() => {
    localStorage.clear();
    mockState.messageHandler = undefined;
    vi.clearAllMocks();
    useWorkflowStore.setState({
      bySession: {},
      byRun: {},
      agentsBySession: {},
      tasksBySession: {},
      eventsBySession: {},
    });
    useChatStore.setState({
      messagesBySession: {},
      generatingSessions: [],
      pausedSessions: [],
      sessionInputs: {},
    });
    useSessionStore.setState({ activeSessionId: 'session-active' });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('updates workflowStore for workflow lifecycle messages', () => {
    render(<Harness />);
    expect(mockState.wsServiceMock.connect).toHaveBeenCalled();
    expect(mockState.messageHandler).toBeTruthy();

    act(() => {
      mockState.messageHandler?.({
        type: 'workflow_started',
        sessionId: 'session-1',
        data: JSON.stringify({ runId: 'run-1', status: 'RUNNING' }),
      });
      mockState.messageHandler?.({
        type: 'workflow_event',
        sessionId: 'session-1',
        data: JSON.stringify({
          runId: 'run-1',
          type: 'effect.completed',
          completedEffects: 1,
          totalEffects: 2,
          completedPhases: 0,
          totalPhases: 1,
          tokensUsed: 10,
          tokensRemaining: 90,
        }),
      });
      mockState.messageHandler?.({
        type: 'workflow_progress',
        sessionId: 'session-1',
        data: JSON.stringify({
          runId: 'run-1',
          status: 'PAUSED',
          completedEffects: 1,
          totalEffects: 2,
          completedPhases: 0,
          totalPhases: 1,
        }),
      });
      mockState.messageHandler?.({
        type: 'workflow_finished',
        sessionId: 'session-1',
        data: JSON.stringify({ runId: 'run-1', status: 'COMPLETED' }),
      });
    });

    expect(useWorkflowStore.getState().getForSession('session-1')).toMatchObject({
      runId: 'run-1',
      status: 'COMPLETED',
      completedEffects: 0,
      totalEffects: 0,
      lastEventType: 'workflow_finished',
    });
    expect(useWorkflowStore.getState().byRun['run-1']).toMatchObject({
      sessionId: 'session-1',
      status: 'COMPLETED',
    });
  });

  it('stores workflow errors without adding a normal assistant reply', () => {
    render(<Harness />);

    act(() => {
      mockState.messageHandler?.({
        type: 'workflow_error',
        sessionId: 'session-error',
        data: JSON.stringify({ runId: 'run-error', error: 'boom' }),
      });
    });

    expect(useWorkflowStore.getState().getForSession('session-error')).toMatchObject({
      runId: 'run-error',
      status: 'ERROR',
      error: 'boom',
    });
    expect(useChatStore.getState().messagesBySession['session-error']).toBeUndefined();
  });

  it('handles replayed workflow messages with explicit session ids', () => {
    useSessionStore.setState({ activeSessionId: null });
    render(<Harness />);

    act(() => {
      mockState.messageHandler?.({
        type: 'workflow_progress',
        sessionId: 'session-replayed',
        data: JSON.stringify({
          runId: 'run-replayed',
          status: 'RUNNING',
          completedEffects: 2,
          totalEffects: 4,
          type: 'effect.completed',
        }),
      });
    });

    expect(useWorkflowStore.getState().getForSession('session-replayed')).toMatchObject({
      runId: 'run-replayed',
      status: 'RUNNING',
      completedEffects: 2,
      totalEffects: 4,
      lastEventType: 'effect.completed',
    });
  });

  it('renders workflow finished output as an assistant reply', () => {
    render(<Harness />);

    act(() => {
      mockState.messageHandler?.({
        type: 'workflow_finished',
        sessionId: 'session-goal',
        data: JSON.stringify({
          runId: 'run-goal',
          status: 'COMPLETED',
          output: [
            [{ role: 'explorer', content: 'explored' }],
            [{ role: 'coder', content: 'implemented' }],
            [{ role: 'verifier', content: 'verified final answer' }],
          ],
        }),
      });
    });

    expect(useChatStore.getState().messagesBySession['session-goal']).toContainEqual(expect.objectContaining({
      type: 'assistant',
      content: 'verified final answer',
    }));
  });

  it('keeps ordinary assistant stream content on the normal chat path', () => {
    render(<Harness />);

    act(() => {
      mockState.messageHandler?.({ type: 'start', sessionId: 'session-chat' });
      mockState.messageHandler?.({ type: 'content', sessionId: 'session-chat', data: 'hello' });
      mockState.messageHandler?.({ type: 'complete', sessionId: 'session-chat' });
    });

    const messages = useChatStore.getState().messagesBySession['session-chat'];
    expect(messages).toHaveLength(1);
    expect(messages?.[0]).toMatchObject({
      type: 'assistant',
      content: 'hello',
    });
    expect(useWorkflowStore.getState().getForSession('session-chat')).toBeUndefined();
  });

  it('stores plan task messages in the blackboard', () => {
    render(<Harness />);

    act(() => {
      mockState.messageHandler?.({
        type: 'plan_tasks',
        sessionId: 'session-plan',
        data: JSON.stringify({
          tasks: [
            { id: 'p1', title: 'Inspect files', status: 'pending' },
            { id: 'p2', title: 'Design fix', status: 'pending' },
          ],
        }),
      });
      mockState.messageHandler?.({
        type: 'plan_task_start',
        sessionId: 'session-plan',
        data: JSON.stringify({ id: 'p1', title: 'Inspect files' }),
      });
      mockState.messageHandler?.({
        type: 'plan_task_result',
        sessionId: 'session-plan',
        data: JSON.stringify({ id: 'p1', title: 'Inspect files', result: 'done' }),
      });
    });

    expect(useWorkflowStore.getState().getTasksForSession('session-plan')).toEqual(expect.arrayContaining([
      expect.objectContaining({ id: 'p1', status: 'completed', result: 'done' }),
      expect.objectContaining({ id: 'p2', status: 'pending' }),
    ]));
  });

  it('stores goal workflow agent effects in the agent workspace blackboard', () => {
    render(<Harness />);

    act(() => {
      mockState.messageHandler?.({
        type: 'workflow_event',
        sessionId: 'session-goal-agent',
        data: JSON.stringify({
          runId: 'run-goal-agent',
          type: 'effect.scheduled',
          completedEffects: 0,
          totalEffects: 3,
          data: {
            effectId: 'run-goal-agent:e1-explore',
            nodeId: 'e1-explore',
            kind: 'agent',
            role: 'explorer',
          },
        }),
      });
      mockState.messageHandler?.({
        type: 'workflow_event',
        sessionId: 'session-goal-agent',
        data: JSON.stringify({
          runId: 'run-goal-agent',
          type: 'effect.completed',
          completedEffects: 1,
          totalEffects: 3,
          data: {
            effectId: 'run-goal-agent:e1-explore',
            nodeId: 'e1-explore',
            kind: 'agent',
            artifactRef: 'artifacts/e1-explore.json',
          },
        }),
      });
    });

    expect(useWorkflowStore.getState().getAgentsForSession('session-goal-agent')).toContainEqual(expect.objectContaining({
      id: 'e1-explore',
      role: 'explorer',
      status: 'completed',
      source: 'workflow',
      runId: 'run-goal-agent',
    }));
  });

  it('stores goal workflow tool effects as workflow blackboard tasks and events', () => {
    render(<Harness />);

    act(() => {
      mockState.messageHandler?.({
        type: 'workflow_event',
        sessionId: 'session-goal-tool',
        data: JSON.stringify({
          runId: 'run-goal-tool',
          type: 'effect.scheduled',
          data: {
            effectId: 'run-goal-tool:t1',
            nodeId: 't1',
            kind: 'tool',
            toolName: 'FileReadTool',
          },
        }),
      });
      mockState.messageHandler?.({
        type: 'workflow_event',
        sessionId: 'session-goal-tool',
        data: JSON.stringify({
          runId: 'run-goal-tool',
          type: 'effect.completed',
          data: {
            effectId: 'run-goal-tool:t1',
            nodeId: 't1',
            kind: 'tool',
            toolName: 'FileReadTool',
            artifactRef: 'artifacts/run-goal-tool-t1.json',
          },
        }),
      });
    });

    expect(useWorkflowStore.getState().getTasksForSession('session-goal-tool')).toContainEqual(expect.objectContaining({
      id: 'run-goal-tool:t1',
      title: 'FileReadTool',
      status: 'completed',
      source: 'workflow',
      runId: 'run-goal-tool',
    }));
    expect(useWorkflowStore.getState().getEventsForSession('session-goal-tool')).toEqual(expect.arrayContaining([
      expect.objectContaining({
        type: 'effect.completed',
        source: 'workflow',
        title: 'FileReadTool',
        taskId: 'run-goal-tool:t1',
      }),
    ]));
  });

  it('stores agent_flow_event in the agent workspace blackboard', () => {
    render(<Harness />);

    act(() => {
      mockState.messageHandler?.({
        type: 'agent_flow_event',
        sessionId: 'session-agent',
        data: JSON.stringify({
          eventType: 'task_start',
          data: { taskId: 'task-agent', type: 'EXECUTION', description: 'Code change', agentId: 'coder' },
        }),
      });
    });

    expect(useWorkflowStore.getState().getAgentsForSession('session-agent')).toContainEqual(expect.objectContaining({
      id: 'coder',
      status: 'running',
      currentTask: 'Code change',
    }));
  });
});

import { describe, it, expect } from 'vitest';
import { appendMessage, cleanArgs } from '../solid/hooks/useStreamHandlers.js';
import { createMessage } from '../protocol.js';
import type { AppState } from '../solid/hooks/AppStateProvider.js';

function makeState(messagesCount = 0): AppState {
  return {
    messages: Array.from({ length: messagesCount }, (_, i) =>
      createMessage('assistant', `init-${i}`),
    ),
    currentMessage: null,
    usage: { promptTokens: 0, completionTokens: 0, totalTokens: 0, usageRatio: 0 },
    planMode: false,
    autoMode: false,
    planWaiting: false,
    scrollOffset: 0,
    modelName: '',
    connected: false,
    statusText: '',
    tokenRate: 0,
    toolCallsExpanded: false,
    planTasks: [],
    pdcaPhase: '',
    degradation: { active: false, retryCount: 0, maxRetries: 0, mode: 'normal', message: '' },
    compactionProgress: null,
    approvalQueue: [],
  };
}

describe('appendMessage', () => {
  it('adds a message to an empty list', () => {
    const state = makeState();
    const next = appendMessage(state, createMessage('user', 'hi'));
    expect(next.messages).toHaveLength(1);
    expect(next.messages[0].content).toBe('hi');
  });

  it('preserves order of existing messages', () => {
    const state = makeState(3);
    const next = appendMessage(state, createMessage('user', 'new'));
    expect(next.messages.map((m) => m.content)).toEqual(['init-0', 'init-1', 'init-2', 'new']);
  });

  it('returns a new object (immutable)', () => {
    const state = makeState(2);
    const next = appendMessage(state, createMessage('user', 'x'));
    expect(next).not.toBe(state);
    expect(next.messages).not.toBe(state.messages);
    // original state unchanged
    expect(state.messages).toHaveLength(2);
  });

  it('caps at 200 messages and drops the oldest (FIFO)', () => {
    const state = makeState(200);
    const oldest = state.messages[0].id;
    const next = appendMessage(state, createMessage('user', 'overflow'));
    expect(next.messages).toHaveLength(200);
    // The original first message should have been dropped
    expect(next.messages.some((m) => m.id === oldest)).toBe(false);
    // The new one is at the tail
    expect(next.messages[next.messages.length - 1].content).toBe('overflow');
  });

  it('does not cap below 200 messages', () => {
    const state = makeState(199);
    const next = appendMessage(state, createMessage('user', 'two-hundredth'));
    expect(next.messages).toHaveLength(200);
  });
});

describe('cleanArgs', () => {
  it('returns plain string unchanged', () => {
    expect(cleanArgs('ls -la')).toBe('ls -la');
  });

  it('extracts command from JSON object', () => {
    expect(cleanArgs({ command: 'echo hello' })).toBe('echo hello');
  });

  it('stringifies plain object when no command field', () => {
    expect(cleanArgs({ path: '/tmp', mode: 'r' })).toBe(
      JSON.stringify({ path: '/tmp', mode: 'r' }, null, 2),
    );
  });

  it('unwraps nested command-as-object', () => {
    const input = { command: { bash: 'make build' } };
    // First iteration: obj.command is an object → s = JSON.stringify(obj.command) = '{"bash":"make build"}'
    // Second iteration: parses to { bash: "make build" }, no .command → JSON.stringify(..., null, 2)
    expect(cleanArgs(input)).toBe(JSON.stringify({ bash: 'make build' }, null, 2));
  });

  it('handles stringified JSON input', () => {
    expect(cleanArgs('{"command":"npm test"}')).toBe('npm test');
  });

  it('returns unparseable string as-is', () => {
    expect(cleanArgs('not json at all')).toBe('not json at all');
  });

  it('returns array as-is (not unwrapped)', () => {
    const arr = ['a', 'b'];
    // Arrays are detected and fall through to `return s`
    expect(cleanArgs(arr)).toBe(JSON.stringify(arr));
  });
});

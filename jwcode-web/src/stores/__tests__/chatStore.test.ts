import { describe, it, expect, beforeEach } from 'vitest';
import { useChatStore } from '../chatStore';
import type { Message } from '../../types';

function makeMessage(id: string, role: 'user' | 'assistant' = 'user'): Message {
  return {
    id,
    role,
    content: [{ type: 'text', text: `message ${id}` }],
    timestamp: Date.now(),
  };
}

describe('chatStore tombstone', () => {
  const sessionId = 'test-session';

  beforeEach(() => {
    const state = useChatStore.getState();
    state.clearMessages(sessionId);
  });

  // === 用例 25: applyTombstone 标记删除 ===
  it('applyTombstone marks specified messages as deleted', () => {
    const store = useChatStore.getState();
    store.addMessage(sessionId, makeMessage('msg-1', 'user'));
    store.addMessage(sessionId, makeMessage('msg-2', 'assistant'));
    store.addMessage(sessionId, makeMessage('msg-3', 'user'));

    store.applyTombstone(sessionId, ['msg-2']);

    const messages = store.getMessages(sessionId);
    expect(messages).toHaveLength(3);
    expect(messages.find(m => m.id === 'msg-1')?.deleted).toBeUndefined();
    expect(messages.find(m => m.id === 'msg-2')?.deleted).toBe(true);
    expect(messages.find(m => m.id === 'msg-3')?.deleted).toBeUndefined();
  });

  it('applyTombstone can mark multiple messages', () => {
    const store = useChatStore.getState();
    store.addMessage(sessionId, makeMessage('a1'));
    store.addMessage(sessionId, makeMessage('a2'));
    store.addMessage(sessionId, makeMessage('a3'));

    store.applyTombstone(sessionId, ['a1', 'a3']);

    const messages = store.getMessages(sessionId);
    expect(messages.find(m => m.id === 'a1')?.deleted).toBe(true);
    expect(messages.find(m => m.id === 'a2')?.deleted).toBeUndefined();
    expect(messages.find(m => m.id === 'a3')?.deleted).toBe(true);
  });

  // === 用例 26: 已删除消息不渲染 ===
  it('visibleMessages filters out deleted messages', () => {
    const store = useChatStore.getState();
    store.addMessage(sessionId, makeMessage('keep'));
    store.addMessage(sessionId, makeMessage('remove'));
    store.addMessage(sessionId, makeMessage('keep2'));

    store.applyTombstone(sessionId, ['remove']);

    const messages = store.getMessages(sessionId);
    const visible = messages.filter(m => !m.deleted);

    expect(visible).toHaveLength(2);
    expect(visible.map(m => m.id)).toEqual(['keep', 'keep2']);
  });

  // === 用例 27: 空 ID 列表不影响 ===
  it('applyTombstone with empty list does nothing', () => {
    const store = useChatStore.getState();
    store.addMessage(sessionId, makeMessage('m1'));
    store.addMessage(sessionId, makeMessage('m2'));

    store.applyTombstone(sessionId, []);

    const messages = store.getMessages(sessionId);
    expect(messages.every(m => !m.deleted)).toBe(true);
  });

  it('applyTombstone with non-existent IDs does nothing', () => {
    const store = useChatStore.getState();
    store.addMessage(sessionId, makeMessage('real'));

    store.applyTombstone(sessionId, ['nonexistent']);

    const messages = store.getMessages(sessionId);
    expect(messages[0].deleted).toBeUndefined();
  });
});


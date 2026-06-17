import { describe, it, expect, beforeEach } from 'vitest';
import { QueryGuard } from '../hooks/useQueryGuard';

describe('Input Queue (C4)', () => {
  let guard: QueryGuard;

  beforeEach(() => {
    guard = new QueryGuard();
  });

  // === 用例 33: 流式期间输入排队 ===
  it('queues inputs while generation is in progress', () => {
    guard.reserve();
    guard.markRunning();
    guard.enqueue('input-1');
    guard.enqueue('input-2');
    guard.enqueue('input-3');
    expect(guard.queueLength).toBe(3);
    expect(guard.state).toBe('running');
  });

  // === 用例 34: 完成后自动出队 ===
  it('dequeues and returns next input on completion', () => {
    guard.enqueue('pending-input');
    expect(guard.queueLength).toBe(1);
    const dequeued = guard.markComplete();
    expect(dequeued).toBe('pending-input');
    expect(guard.queueLength).toBe(0);
  });

  // === 用例 35: 队列顺序 A->B->C ===
  it('processes queued inputs in FIFO order', () => {
    guard.enqueue('A');
    guard.enqueue('B');
    guard.enqueue('C');
    expect(guard.markComplete()).toBe('A');
    expect(guard.markComplete()).toBe('B');
    expect(guard.markComplete()).toBe('C');
    expect(guard.queueLength).toBe(0);
  });

  it('end-to-end: queue during streaming, drain on complete', () => {
    guard.reserve();
    guard.markRunning();
    guard.enqueue('follow-up-A');
    guard.enqueue('follow-up-B');
    expect(guard.queueLength).toBe(2);
    const firstDrained = guard.markComplete();
    expect(firstDrained).toBe('follow-up-A');
    expect(guard.state).toBe('idle');
    guard.reserve();
    guard.markRunning();
    const secondDrained = guard.markComplete();
    expect(secondDrained).toBe('follow-up-B');
    expect(guard.queueLength).toBe(0);
  });
});

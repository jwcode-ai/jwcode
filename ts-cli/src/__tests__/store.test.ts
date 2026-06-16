import { describe, it, expect } from 'vitest';
import { createStore } from '../store.js';

interface TestState {
  count: number;
  name: string;
}

// createStore batches listener notifications onto queueMicrotask.
// Tests that assert listener side effects need to await one microtask.
const flush = () => new Promise<void>((r) => queueMicrotask(r));

describe('store', () => {
  it('creates store with initial state', () => {
    const store = createStore<TestState>({ count: 0, name: 'test' });
    expect(store.getState().count).toBe(0);
    expect(store.getState().name).toBe('test');
  });

  it('setState updates state immutably', () => {
    const store = createStore<TestState>({ count: 0, name: 'test' });
    const prev = store.getState();
    store.setState(s => ({ ...s, count: 5 }));
    expect(store.getState().count).toBe(5);
    expect(prev.count).toBe(0); // original unchanged
  });

  it('subscribe receives updates', async () => {
    const store = createStore<TestState>({ count: 0, name: 'test' });
    let called = false;
    store.subscribe(() => { called = true; });
    store.setState(s => ({ ...s, count: 10 }));
    await flush();
    expect(called).toBe(true);
    expect(store.getState().count).toBe(10);
  });

  it('subscribe returns unsubscribe function', async () => {
    const store = createStore<TestState>({ count: 0, name: 'test' });
    let callCount = 0;
    const unsub = store.subscribe(() => { callCount++; });
    store.setState(s => ({ ...s, count: 1 }));
    await flush();
    expect(callCount).toBe(1);
    unsub();
    store.setState(s => ({ ...s, count: 2 }));
    await flush();
    expect(callCount).toBe(1); // not called again
  });

  it('multiple subscribers all notified', async () => {
    const store = createStore<TestState>({ count: 0, name: 'test' });
    let calls = 0;
    store.subscribe(() => { calls++; });
    store.subscribe(() => { calls++; });
    store.setState(s => ({ ...s, count: 42 }));
    await flush();
    expect(calls).toBe(2);
    expect(store.getState().count).toBe(42);
  });

  it('coalesces multiple setState calls in the same task into one notification', async () => {
    const store = createStore<TestState>({ count: 0, name: 'test' });
    let calls = 0;
    store.subscribe(() => { calls++; });
    store.setState(s => ({ ...s, count: 1 }));
    store.setState(s => ({ ...s, count: 2 }));
    store.setState(s => ({ ...s, count: 3 }));
    await flush();
    expect(calls).toBe(1);
    expect(store.getState().count).toBe(3);
  });
});

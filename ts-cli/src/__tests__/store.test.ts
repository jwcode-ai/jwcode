import { describe, it, expect } from 'vitest';
import { createStore } from '../store.js';

interface TestState {
  count: number;
  name: string;
}

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

  it('subscribe receives updates', () => {
    const store = createStore<TestState>({ count: 0, name: 'test' });
    let called = false;
    store.subscribe(() => { called = true; });
    store.setState(s => ({ ...s, count: 10 }));
    expect(called).toBe(true);
    expect(store.getState().count).toBe(10);
  });

  it('subscribe returns unsubscribe function', () => {
    const store = createStore<TestState>({ count: 0, name: 'test' });
    let callCount = 0;
    const unsub = store.subscribe(() => { callCount++; });
    store.setState(s => ({ ...s, count: 1 }));
    expect(callCount).toBe(1);
    unsub();
    store.setState(s => ({ ...s, count: 2 }));
    expect(callCount).toBe(1); // not called again
  });

  it('multiple subscribers all notified', () => {
    const store = createStore<TestState>({ count: 0, name: 'test' });
    let calls = 0;
    store.subscribe(() => { calls++; });
    store.subscribe(() => { calls++; });
    store.setState(s => ({ ...s, count: 42 }));
    expect(calls).toBe(2);
    expect(store.getState().count).toBe(42);
  });
});

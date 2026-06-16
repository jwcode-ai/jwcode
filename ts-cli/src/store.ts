/**
 * Generic state store — adopted from claude-code-source-main/src/state/store.ts
 */
type Listener = () => void;
type OnChange<T> = (args: { newState: T; oldState: T }) => void;

export interface Store<T> {
  getState: () => T;
  setState: (updater: (prev: T) => T) => void;
  subscribe: (listener: Listener) => () => void;
}

export function createStore<T>(
  initialState: T,
  onChange?: OnChange<T>,
): Store<T> {
  let state = initialState;
  const listeners = new Set<Listener>();
  // Microtask-level batching: multiple synchronous setState calls in the same
  // task tick coalesce into a single listener notification. Eliminates the
  // double-render in boundary events (start/complete/plan_start/plan_complete)
  // that call updateAppState twice in immediate succession.
  let pendingNotify = false;

  function flush() {
    pendingNotify = false;
    for (const listener of listeners) listener();
  }

  return {
    getState: () => state,

    setState: (updater: (prev: T) => T) => {
      const prev = state;
      const next = updater(prev);
      if (Object.is(next, prev)) return;
      state = next;
      onChange?.({ newState: next, oldState: prev });
      if (!pendingNotify) {
        pendingNotify = true;
        queueMicrotask(flush);
      }
    },

    subscribe: (listener: Listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
  };
}

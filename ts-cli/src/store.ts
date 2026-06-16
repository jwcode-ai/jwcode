/**
 * Generic state store — adopted from claude-code-source-main/src/state/store.ts
 *
 * Optimized with shallow-equal notification batching to reduce unnecessary re-renders.
 */
type Listener = () => void;
type OnChange<T> = (args: { newState: T; oldState: T }) => void;

export interface Store<T> {
  getState: () => T;
  setState: (updater: (prev: T) => T) => void;
  subscribe: (listener: Listener) => () => void;
}

/**
 * Shallow compare two arrays by reference identity of each element.
 * Returns true if arrays are shallow-equal.
 */
function shallowArrayEqual(a: unknown[], b: unknown[]): boolean {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) {
    if (!Object.is(a[i], b[i])) return false;
  }
  return true;
}

/**
 * Shallow compare two plain objects by own keys and reference identity.
 * Returns true if objects are shallow-equal.
 */
function shallowObjectEqual(a: Record<string, unknown>, b: Record<string, unknown>): boolean {
  const ka = Object.keys(a);
  const kb = Object.keys(b);
  if (ka.length !== kb.length) return false;
  for (const k of ka) {
    if (!Object.is(a[k], b[k])) return false;
  }
  return true;
}

export function createStore<T extends object>(
  initialState: T,
  onChange?: OnChange<T>,
): Store<T> {
  let state = { ...initialState } as T;
  const listeners = new Set<Listener>();

  function notify() {
    for (const listener of listeners) {
      listener();
    }
  }

  return {
    getState: () => state,

    setState: (updater: (prev: T) => T) => {
      const prev = state;
      const next = updater(prev);
      if (Object.is(next, prev)) return; // no change

      // Shallow compare top-level keys to detect real changes
      if (typeof next === 'object' && typeof prev === 'object' && next !== null && prev !== null) {
        const nextObj = next as Record<string, unknown>;
        const prevObj = prev as Record<string, unknown>;
        const keys = new Set([...Object.keys(nextObj), ...Object.keys(prevObj)]);
        let changed = false;
        for (const k of keys) {
          const nv = nextObj[k];
          const pv = prevObj[k];
          if (Array.isArray(nv) && Array.isArray(pv)) {
            if (!shallowArrayEqual(nv, pv)) { changed = true; break; }
          } else if (typeof nv === 'object' && typeof pv === 'object' && nv !== null && pv !== null) {
            if (!shallowObjectEqual(nv as Record<string, unknown>, pv as Record<string, unknown>)) { changed = true; break; }
          } else if (!Object.is(nv, pv)) {
            changed = true;
            break;
          }
        }
        if (!changed) return; // skip notification if nothing meaningfully changed
      }

      if (onChange) onChange({ newState: next, oldState: prev });
      state = next as T;
      notify();
    },

    subscribe: (listener: Listener) => {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
}

/**
 * Application state management — React context wrapping the generic store.
 */
import { useEffect, useState, useCallback } from 'react';
import { createStore, type Store } from '../store.js';
import type { Message, TokenUsage } from '../protocol.js';

export interface AppState {
  messages: Message[];
  currentMessage: Message | null;
  usage: TokenUsage;
  planMode: boolean;
  autoMode: boolean;
  planWaiting: boolean;
  scrollOffset: number;
  modelName: string;
  connected: boolean;
  statusText: string;
}

const initialState: AppState = {
  messages: [],
  currentMessage: null,
  usage: { promptTokens: 0, completionTokens: 0, totalTokens: 0, usageRatio: 0 },
  planMode: false,
  autoMode: false,
  planWaiting: false,
  scrollOffset: 0,
  modelName: '',
  connected: false,
  statusText: 'connecting...',
};

let _store: Store<AppState> | null = null;

export function getStore(): Store<AppState> {
  if (!_store) _store = createStore<AppState>(initialState);
  return _store;
}

// Provide context manually since we need to use it outside React
// We use a module-level store accessed via getStore()
export function useAppState(): AppState {
  const store = getStore();
  const [state, setState] = useState<AppState>(store.getState());

  useEffect(() => {
    return store.subscribe(() => setState(store.getState()));
  }, []);

  return state;
}

export function useSetState() {
  const store = getStore();
  return useCallback((updater: (prev: AppState) => AppState) => {
    store.setState(updater);
  }, []);
}

export function updateAppState(updater: (prev: AppState) => AppState): void {
  getStore().setState(updater);
}

/**
 * Application state management — selector-based subscriptions via useSyncExternalStore.
 * Components only re-render when their specific slice changes.
 */
import { useSyncExternalStore, useRef } from 'react';
import { createStore, type Store } from '../store.js';
import type { Message, PlanTask, TokenUsage } from '../protocol.js';

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
  tokenRate: number;
  toolCallsExpanded: boolean;
  planTasks: PlanTask[];
  pdcaPhase: string;
  degradation: import("../protocol.js").DegradationStatus;
  compactionProgress: { stage: string; percent: number; message: string } | null;
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
  tokenRate: 0,
  toolCallsExpanded: false,
  planTasks: [],
  pdcaPhase: '',
  degradation: { active: false, retryCount: 0, maxRetries: 0, mode: "normal", message: "" },
  compactionProgress: null,
};

let _store: Store<AppState> | null = null;

export function getStore(): Store<AppState> {
  if (!_store) _store = createStore<AppState>(initialState);
  return _store;
}

// ---- stable module-level selectors ----

const selMessages = (s: AppState) => s.messages;
const selCurrentMessage = (s: AppState) => s.currentMessage;
const selUsage = (s: AppState) => s.usage;
const selPlanMode = (s: AppState) => s.planMode;
const selAutoMode = (s: AppState) => s.autoMode;
const selPlanWaiting = (s: AppState) => s.planWaiting;
const selScrollOffset = (s: AppState) => s.scrollOffset;
const selModelName = (s: AppState) => s.modelName;
const selConnected = (s: AppState) => s.connected;
const selStatusText = (s: AppState) => s.statusText;
const selTokenRate = (s: AppState) => s.tokenRate;
const selIsGenerating = (s: AppState) => s.currentMessage !== null;
const selToolCallsExpanded = (s: AppState) => s.toolCallsExpanded;
const selPlanTasks = (s: AppState) => s.planTasks;
const selPdcaPhase = (s: AppState) => s.pdcaPhase;
const selDegradation = (s: AppState) => s.degradation;

// ChatArea compound selector: messages + currentMessage change together during streaming.
// scrollOffset intentionally excluded — ChatArea doesn't use it, and bundling it would
// cause unnecessary re-renders on every scroll event.
const selChatArea = (s: AppState) => ({
  messages: s.messages,
  currentMessage: s.currentMessage,
} as const);

// StatusLine compound selector: all status fields
// Includes isGenerating/currentMessageTimestamp as primitives so the selector
// is stable during streaming (timestamp doesn't change per flush) —
// StatusLine avoids re-rendering 5×/s just for the elapsed counter.
const selStatusLine = (s: AppState) => ({
  usage: s.usage,
  modelName: s.modelName,
  planMode: s.planMode,
  autoMode: s.autoMode,
  connected: s.connected,
  statusText: s.statusText,
  messagesLen: s.messages.length,
  tokenRate: s.tokenRate,
  compactionProgress: s.compactionProgress,
  isGenerating: s.currentMessage !== null,
  currentMessageTimestamp: s.currentMessage?.timestamp ?? null,
} as const);

// ---- shallow equality for selector cache ----

function shallowEqual(a: unknown, b: unknown): boolean {
  if (Object.is(a, b)) return true;
  if (a === null || b === null) return false;
  if (typeof a !== 'object' || typeof b !== 'object') return false;
  const keysA = Object.keys(a as Record<string, unknown>);
  const keysB = Object.keys(b as Record<string, unknown>);
  if (keysA.length !== keysB.length) return false;
  return keysA.every(k =>
    Object.is(
      (a as Record<string, unknown>)[k],
      (b as Record<string, unknown>)[k],
    ),
  );
}

/**
 * Subscribe to a single slice of AppState.
 * Component re-renders only when the selected value changes (shallow equality).
 */
export function useAppSlice<T>(selector: (state: AppState) => T): T {
  const store = getStore();
  const cacheRef = useRef<{ value: T } | null>(null);

  const getSnapshot = () => {
    const next = selector(store.getState());
    const cached = cacheRef.current;
    if (cached !== null && shallowEqual(next, cached.value)) {
      return cached.value;
    }
    cacheRef.current = { value: next };
    return next;
  };

  return useSyncExternalStore(store.subscribe, getSnapshot, getSnapshot);
}

// ---- convenience hooks ----

export const useAppMessages = () => useAppSlice(selMessages);
export const useAppCurrentMessage = () => useAppSlice(selCurrentMessage);
export const useAppUsage = () => useAppSlice(selUsage);
export const useAppPlanMode = () => useAppSlice(selPlanMode);
export const useAppAutoMode = () => useAppSlice(selAutoMode);
export const useAppPlanWaiting = () => useAppSlice(selPlanWaiting);
export const useAppScrollOffset = () => useAppSlice(selScrollOffset);
export const useAppModelName = () => useAppSlice(selModelName);
export const useAppConnected = () => useAppSlice(selConnected);
export const useAppStatusText = () => useAppSlice(selStatusText);
export const useAppTokenRate = () => useAppSlice(selTokenRate);
export const useAppIsGenerating = () => useAppSlice(selIsGenerating);
export const useAppChatArea = () => useAppSlice(selChatArea);
export const useAppStatusLine = () => useAppSlice(selStatusLine);
export const useAppToolCallsExpanded = () => useAppSlice(selToolCallsExpanded);
export const useAppPlanTasks = () => useAppSlice(selPlanTasks);
export const useAppPdcaPhase = () => useAppSlice(selPdcaPhase);
export const useAppDegradation = () => useAppSlice(selDegradation);

/** @deprecated Use specific hooks (useAppMessages, useAppConnected, etc.) */
export function useAppState(): AppState {
  return useAppSlice(s => s);
}

// ---- imperative updates (for non-React contexts) ----

export function updateAppState(updater: (prev: AppState) => AppState): void {
  getStore().setState(updater);
}

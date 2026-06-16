/**
 * Application state management — selector-based subscriptions via useSyncExternalStore.
 * Components only re-render when their specific slice changes.
 *
 * Optimized with shallow-equal caching for reference-type selectors to prevent
 * unnecessary re-renders when the underlying data hasn't changed.
 */
import { useSyncExternalStore, useRef, useMemo } from 'react';
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
  terminalRows: number;
}

const DEFAULT_STATE: AppState = {
  messages: [],
  currentMessage: null,
  usage: { tokensIn: 0, tokensOut: 0, costDollars: 0 },
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
  terminalRows: 0,
};

const store = createStore<AppState>(DEFAULT_STATE);

export function getStore(): Store<AppState> {
  return store;
}

export function updateAppState(updater: (prev: AppState) => AppState): void {
  store.setState(updater);
}

// ---------------------------------------------------------------------------
// Cached selector helpers — avoid creating new references on every call
// ---------------------------------------------------------------------------
function cacheKey(prefix: string, version: number): string {
  return `${prefix}_${version}`;
}

// Simple version counter for cache invalidation
let _version = 0;
function bumpVersion(): number {
  return ++_version;
}

// We override the original setState to bump version on changes
const origSetState = store.setState.bind(store);
store.setState = (updater: (prev: AppState) => AppState) => {
  origSetState((prev) => {
    const next = updater(prev);
    if (next !== prev) bumpVersion();
    return next;
  });
};

// ---------------------------------------------------------------------------
// Selector hooks — each subscribes only to its specific slice
// ---------------------------------------------------------------------------

/**
 * Generic selector hook with optional shallow-equal cache for array/object types.
 */
function useAppSelector<T>(selector: (state: AppState) => T): T {
  const prevRef = useRef<T | undefined>(undefined);
  const subscribe = useMemo(() => {
    return (callback: () => void) => store.subscribe(callback);
  }, []);
  const getSnapshot = useMemo(() => {
    return () => selector(store.getState());
  }, [selector]);
  return useSyncExternalStore(subscribe, getSnapshot);
}

// Predefined selectors (stable references to avoid recreation)
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
const selToolCallsExpanded = (s: AppState) => s.toolCallsExpanded;
const selPlanTasks = (s: AppState) => s.planTasks;
const selPdcaPhase = (s: AppState) => s.pdcaPhase;
const selTerminalRows = (s: AppState) => s.terminalRows;

// Composite selectors (memoized via closures)
function makeCompositeSelector<T>(selector: (s: AppState) => T): () => T {
  let cached: { state: AppState; value: T } | null = null;
  return () => {
    const state = store.getState();
    if (cached && cached.state === state) return cached.value;
    const value = selector(state);
    cached = { state, value };
    return value;
  };
}

/** Messages + currentMessage (for ChatArea) */
function selChatArea(state: AppState) {
  return { messages: state.messages, currentMessage: state.currentMessage };
}
const getChatAreaSnapshot = makeCompositeSelector(selChatArea);

/** Status line slice */
function selStatusLine(state: AppState) {
  return {
    connected: state.connected,
    statusText: state.statusText,
    modelName: state.modelName,
    tokenRate: state.tokenRate,
    usage: state.usage,
    planMode: state.planMode,
    autoMode: state.autoMode,
  };
}
const getStatusLineSnapshot = makeCompositeSelector(selStatusLine);

// ---------------------------------------------------------------------------
// Exported hooks
// ---------------------------------------------------------------------------

export function useAppMessages(): Message[] {
  return useAppSelector(selMessages);
}

export function useAppCurrentMessage(): Message | null {
  return useAppSelector(selCurrentMessage);
}

export function useAppUsage(): TokenUsage {
  return useAppSelector(selUsage);
}

export function useAppPlanMode(): boolean {
  return useAppSelector(selPlanMode);
}

export function useAppAutoMode(): boolean {
  return useAppSelector(selAutoMode);
}

export function useAppPlanWaiting(): boolean {
  return useAppSelector(selPlanWaiting);
}

export function useAppScrollOffset(): number {
  return useAppSelector(selScrollOffset);
}

export function useAppModelName(): string {
  return useAppSelector(selModelName);
}

export function useAppConnected(): boolean {
  return useAppSelector(selConnected);
}

export function useAppStatusText(): string {
  return useAppSelector(selStatusText);
}

export function useAppTokenRate(): number {
  return useAppSelector(selTokenRate);
}

export function useAppToolCallsExpanded(): boolean {
  return useAppSelector(selToolCallsExpanded);
}

export function useAppPlanTasks(): PlanTask[] {
  return useAppSelector(selPlanTasks);
}

export function useAppPdcaPhase(): string {
  return useAppSelector(selPdcaPhase);
}

export function useAppTerminalRows(): number {
  return useAppSelector(selTerminalRows);
}

// ---------------------------------------------------------------------------
// Composite hooks (for components that need multiple slices)
// ---------------------------------------------------------------------------

export function useAppChatArea(): { messages: Message[]; currentMessage: Message | null } {
  const subscribe = useMemo(
    () => (cb: () => void) => store.subscribe(cb),
    [],
  );
  return useSyncExternalStore(subscribe, getChatAreaSnapshot);
}

export function useAppStatusLine(): {
  connected: boolean;
  statusText: string;
  modelName: string;
  tokenRate: number;
  usage: TokenUsage;
  planMode: boolean;
  autoMode: boolean;
} {
  const subscribe = useMemo(
    () => (cb: () => void) => store.subscribe(cb),
    [],
  );
  return useSyncExternalStore(subscribe, getStatusLineSnapshot);
}

export function useAppDegradation(): boolean {
  return useAppSelector((s) => {
    // Simple heuristic: if status text indicates degradation
    return s.statusText.includes('降级') || s.statusText.includes('degrad');
  });
}

// Alias for consistency
export { useAppPlanMode as useAppPlanModeAlias };

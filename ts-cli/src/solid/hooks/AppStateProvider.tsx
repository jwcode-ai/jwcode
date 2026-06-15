/**
 * AppStateProvider — central application state context.
 * Replaces React's store.ts + useAppState.ts with a Solid createStore + context.
 *
 * Components access fine-grained slices via memoized getters, so they only
 * re-render when their specific data changes.
 */
import { createContext, createMemo, useContext, type ParentProps } from "solid-js"
import { createStore, type SetStoreFunction } from "solid-js/store"
import type { JSX } from "solid-js"
import type { Message, PlanTask, TokenUsage, DegradationStatus } from "../../protocol"

export interface ApprovalItem {
  approvalId: string
  toolName: string
  payload: string
}

export interface InitProgress {
  stage: string
  message: string
  percent: number
  error?: string
}

export interface AppState {
  messages: Message[]
  currentMessage: Message | null
  usage: TokenUsage
  planMode: boolean
  autoMode: boolean
  planWaiting: boolean
  scrollOffset: number
  modelName: string
  connected: boolean
  statusText: string
  tokenRate: number
  toolCallsExpanded: boolean
  planTasks: PlanTask[]
  pdcaPhase: string
  degradation: DegradationStatus
  compactionProgress: { stage: string; percent: number; message: string } | null
  approvalQueue: ApprovalItem[]
  showConfigPanel: boolean
  initProgress: InitProgress | null
}

const initialUsage: TokenUsage = {
  promptTokens: 0,
  completionTokens: 0,
  totalTokens: 0,
  usageRatio: 0,
}

const initialDegradation: DegradationStatus = {
  active: false,
  retryCount: 0,
  maxRetries: 0,
  mode: "normal",
  message: "",
}

const initialState: AppState = {
  messages: [],
  currentMessage: null,
  usage: initialUsage,
  planMode: false,
  autoMode: false,
  planWaiting: false,
  scrollOffset: 0,
  modelName: "",
  connected: false,
  statusText: "connecting...",
  tokenRate: 0,
  toolCallsExpanded: false,
  planTasks: [],
  pdcaPhase: "",
  degradation: initialDegradation,
  compactionProgress: null,
  approvalQueue: [],
  showConfigPanel: false,
  initProgress: null,
}

interface AppStateContextValue {
  state: AppState
  setState: SetStoreFunction<AppState>
  /** Live accessors (memoized) for fine-grained reactivity. */
  messages: () => Message[]
  currentMessage: () => Message | null
  isGenerating: () => boolean
  usage: () => TokenUsage
  planMode: () => boolean
  autoMode: () => boolean
  planWaiting: () => boolean
  connected: () => boolean
  modelName: () => string
  statusText: () => string
  tokenRate: () => number
  scrollOffset: () => number
  toolCallsExpanded: () => boolean
  planTasks: () => PlanTask[]
  approvalQueue: () => ApprovalItem[]
  compactionProgress: () => AppState["compactionProgress"]
  pdcaPhase: () => string
  degradation: () => DegradationStatus
  showConfigPanel: () => boolean
  initProgress: () => InitProgress | null
}

const ctx = createContext<AppStateContextValue>()

export function AppStateProvider(props: ParentProps) {
  const [state, setState] = createStore<AppState>(initialState)

  const value: AppStateContextValue = {
    state,
    setState,
    messages: createMemo(() => state.messages),
    currentMessage: createMemo(() => state.currentMessage),
    isGenerating: createMemo(() => state.currentMessage !== null),
    usage: createMemo(() => state.usage),
    planMode: createMemo(() => state.planMode),
    autoMode: createMemo(() => state.autoMode),
    planWaiting: createMemo(() => state.planWaiting),
    connected: createMemo(() => state.connected),
    modelName: createMemo(() => state.modelName),
    statusText: createMemo(() => state.statusText),
    tokenRate: createMemo(() => state.tokenRate),
    scrollOffset: createMemo(() => state.scrollOffset),
    toolCallsExpanded: createMemo(() => state.toolCallsExpanded),
    planTasks: createMemo(() => state.planTasks),
    approvalQueue: createMemo(() => state.approvalQueue),
    compactionProgress: createMemo(() => state.compactionProgress),
    pdcaPhase: createMemo(() => state.pdcaPhase),
    degradation: createMemo(() => state.degradation),
    showConfigPanel: createMemo(() => state.showConfigPanel),
    initProgress: createMemo(() => state.initProgress),
  }

  return <ctx.Provider value={value}>{props.children}</ctx.Provider>
}

export function useAppStateContext() {
  const v = useContext(ctx)
  if (!v) throw new Error("AppStateProvider not found")
  return v
}

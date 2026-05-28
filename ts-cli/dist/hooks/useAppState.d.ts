import { type Store } from '../store.js';
import type { Message, TokenUsage } from '../protocol.js';
export interface AppState {
    messages: Message[];
    currentMessage: Message | null;
    usage: TokenUsage;
    planMode: boolean;
    planWaiting: boolean;
    modelName: string;
    connected: boolean;
    statusText: string;
}
export declare function getStore(): Store<AppState>;
export declare function useAppState(): AppState;
export declare function useSetState(): (updater: (prev: AppState) => AppState) => void;
export declare function updateAppState(updater: (prev: AppState) => AppState): void;

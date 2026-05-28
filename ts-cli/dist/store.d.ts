/**
 * Generic state store — adopted from claude-code-source-main/src/state/store.ts
 */
type Listener = () => void;
type OnChange<T> = (args: {
    newState: T;
    oldState: T;
}) => void;
export interface Store<T> {
    getState: () => T;
    setState: (updater: (prev: T) => T) => void;
    subscribe: (listener: Listener) => () => void;
}
export declare function createStore<T>(initialState: T, onChange?: OnChange<T>): Store<T>;
export {};

/**
 * Application state management — React context wrapping the generic store.
 */
import { useEffect, useState, useCallback } from 'react';
import { createStore } from '../store.js';
const initialState = {
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
let _store = null;
export function getStore() {
    if (!_store)
        _store = createStore(initialState);
    return _store;
}
// Provide context manually since we need to use it outside React
// We use a module-level store accessed via getStore()
export function useAppState() {
    const store = getStore();
    const [state, setState] = useState(store.getState());
    useEffect(() => {
        return store.subscribe(() => setState(store.getState()));
    }, []);
    return state;
}
export function useSetState() {
    const store = getStore();
    return useCallback((updater) => {
        store.setState(updater);
    }, []);
}
export function updateAppState(updater) {
    getStore().setState(updater);
}

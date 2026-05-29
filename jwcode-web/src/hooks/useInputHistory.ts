import { useCallback, useRef } from 'react';

const STORAGE_KEY = 'jwcode-input-history';
const MAX_HISTORY = 30;

function loadHistory(): string[] {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch { return []; }
}

function saveHistory(entries: string[]) {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(entries.slice(0, MAX_HISTORY)));
  } catch { /* ignore quota */ }
}

export function addToHistory(text: string) {
  const trimmed = text.trim();
  if (!trimmed) return;
  const history = loadHistory().filter(h => h !== trimmed);
  history.unshift(trimmed);
  saveHistory(history);
}

export function useInputHistory(_sessionId: string) {
  const indexRef = useRef(-1);       // -1 = not browsing, 0+ = position in history
  const draftRef = useRef('');       // saved current input before browsing

  const navigate = useCallback((direction: 'up' | 'down', currentInput: string): string | null => {
    const history = loadHistory();
    if (history.length === 0) return null;

    if (indexRef.current === -1) {
      // Start browsing: save current draft
      draftRef.current = currentInput;
      if (direction === 'up') {
        indexRef.current = 0;
        return history[0] ?? null;
      }
      return null; // down at -1 is no-op
    }

    if (direction === 'up') {
      indexRef.current = Math.min(indexRef.current + 1, history.length - 1);
      return history[indexRef.current] ?? null;
    } else {
      indexRef.current = indexRef.current - 1;
      if (indexRef.current < 0) {
        // Exited history: restore draft
        indexRef.current = -1;
        return draftRef.current;
      }
      return history[indexRef.current] ?? null;
    }
  }, []);

  const reset = useCallback(() => {
    indexRef.current = -1;
    draftRef.current = '';
  }, []);

  return { navigate, reset };
}

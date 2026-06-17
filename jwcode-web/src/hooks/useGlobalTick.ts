/**
 * useGlobalTick — 单一 RAF/setInterval 全局时钟。
 *
 * 替代 StepItem/ToolCallItem 中各自的 setInterval(1000)。
 * 通过 useSyncExternalStore 订阅，所有组件共享同一个 interval，
 * 最后一个组件卸载时自动停止。
 */
import { useSyncExternalStore } from 'react';

type Listener = () => void;

const listeners = new Set<Listener>();
let intervalId: ReturnType<typeof setInterval> | null = null;
let tick = 0;

function subscribe(listener: Listener): () => void {
  listeners.add(listener);
  if (!intervalId) {
    intervalId = setInterval(() => {
      tick++;
      // Snapshot with forEach: safe even if a listener unsubscribes during iteration
      listeners.forEach((cb) => cb());
    }, 1000);
  }
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0 && intervalId) {
      clearInterval(intervalId);
      intervalId = null;
    }
  };
}

function getTick(): number {
  return tick;
}

/**
 * Returns a monotonically-increasing counter that increments every second.
 * Components using this hook re-render once per second.
 */
export function useGlobalTick(): number {
  return useSyncExternalStore(subscribe, getTick, getTick);
}

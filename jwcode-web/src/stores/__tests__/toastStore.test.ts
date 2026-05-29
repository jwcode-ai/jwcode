import { describe, it, expect, beforeEach, vi } from 'vitest';
import { useToastStore, toast } from '../toastStore';

describe('toastStore', () => {
  beforeEach(() => {
    useToastStore.setState({ toasts: [] });
    vi.useFakeTimers();
  });

  it('starts with empty toasts', () => {
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it('addToast adds a toast with auto-generated id', () => {
    useToastStore.getState().addToast('info', 'test message');
    const toasts = useToastStore.getState().toasts;
    expect(toasts).toHaveLength(1);
    expect(toasts[0].type).toBe('info');
    expect(toasts[0].message).toBe('test message');
    expect(toasts[0].id).toMatch(/^toast-/);
  });

  it('addToast supports custom duration', () => {
    useToastStore.getState().addToast('success', 'done', 1000);
    expect(useToastStore.getState().toasts[0].duration).toBe(1000);
  });

  it('toast auto-dismisses after default duration', () => {
    useToastStore.getState().addToast('info', 'auto-dismiss');
    expect(useToastStore.getState().toasts).toHaveLength(1);
    vi.advanceTimersByTime(4500);
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it('toast with duration 0 does not auto-dismiss', () => {
    useToastStore.getState().addToast('info', 'persistent', 0);
    vi.advanceTimersByTime(10000);
    expect(useToastStore.getState().toasts).toHaveLength(1);
  });

  it('dismissToast removes by id', () => {
    useToastStore.getState().addToast('info', 'msg1');
    useToastStore.getState().addToast('warning', 'msg2');
    const id = useToastStore.getState().toasts[0].id;
    useToastStore.getState().dismissToast(id);
    expect(useToastStore.getState().toasts).toHaveLength(1);
    expect(useToastStore.getState().toasts[0].message).toBe('msg2');
  });

  it('toast cap at 10', () => {
    for (let i = 0; i < 15; i++) {
      useToastStore.getState().addToast('info', `msg${i}`);
    }
    expect(useToastStore.getState().toasts).toHaveLength(10);
    expect(useToastStore.getState().toasts[9].message).toBe('msg14');
  });

  it('convenience helpers work', () => {
    toast.info('i');
    toast.success('s');
    toast.warning('w');
    toast.error('e');
    const toasts = useToastStore.getState().toasts;
    expect(toasts).toHaveLength(4);
    expect(toasts.map(t => t.type)).toEqual(['info', 'success', 'warning', 'error']);
  });

  it('clearAll removes everything', () => {
    toast.info('a');
    toast.info('b');
    useToastStore.getState().clearAll();
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });
});

import { describe, it, expect, beforeEach } from 'vitest';
import { useErrorStore, errLog } from '../errorStore';

describe('errorStore', () => {
  beforeEach(() => {
    useErrorStore.setState({ errors: [] });
  });

  // === 用例 21: 推送 error 级 ===
  it('push error level toast: title 和 detail 正确存储', () => {
    errLog.error('Connection failed', 'Lost connection to backend');
    const errors = useErrorStore.getState().errors;
    expect(errors).toHaveLength(1);
    expect(errors[0].level).toBe('error');
    expect(errors[0].title).toBe('Connection failed');
    expect(errors[0].detail).toBe('Lost connection to backend');
    expect(errors[0].dismissed).toBe(false);
  });

  // === 用例 22: 推送 warn 级 ===
  it('push warning level toast: 黄色级别', () => {
    errLog.warn('Rate limit approaching', 'You have used 80% of quota');
    const errors = useErrorStore.getState().errors;
    expect(errors).toHaveLength(1);
    expect(errors[0].level).toBe('warning');
    expect(errors[0].title).toBe('Rate limit approaching');
  });

  // === 用例 23: 展开详情 (push 时带有 detail) ===
  it('push with detail stores detail text correctly', () => {
    const detailText = 'Error: EACCESS permission denied\n at FileWriteTool.execute()';
    errLog.error('Permission denied', detailText);
    const errors = useErrorStore.getState().errors;
    expect(errors[0].detail).toBe(detailText);
  });

  // === 用例 24: 关闭 toast ===
  it('dismiss marks toast as dismissed', () => {
    errLog.error('Test error', 'detail');
    const id = useErrorStore.getState().errors[0].id;

    useErrorStore.getState().dismiss(id);

    const dismissed = useErrorStore.getState().errors.find(e => e.id === id);
    expect(dismissed?.dismissed).toBe(true);
  });

  it('dismissed items remain in store (not removed)', () => {
    errLog.error('Err1');
    errLog.error('Err2');
    const id = useErrorStore.getState().errors[0].id;

    useErrorStore.getState().dismiss(id);

    expect(useErrorStore.getState().errors).toHaveLength(2);
  });

  // === 额外: 所有级别 ===
  it('all error levels work via errLog helper', () => {
    errLog.info('Info message');
    errLog.warn('Warning message');
    errLog.error('Error message');
    errLog.critical('Critical message');

    const errors = useErrorStore.getState().errors;
    expect(errors).toHaveLength(4);
    expect(errors.map(e => e.level)).toEqual(['info', 'warning', 'error', 'critical']);
  });

  it('clearAll removes everything', () => {
    errLog.error('e1');
    errLog.warn('e2');
    useErrorStore.getState().clearAll();
    expect(useErrorStore.getState().errors).toHaveLength(0);
  });

  it('push returns a non-empty id', () => {
    const id = useErrorStore.getState().push('error', 'title');
    expect(id).toBeTruthy();
    expect(id).toMatch(/^err-/);
  });
});


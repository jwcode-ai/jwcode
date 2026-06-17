import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { MarkdownRenderer } from '../MarkdownRenderer';

describe('MarkdownRenderer throttle', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // === 用例 18: 200ms 内合并渲染 ===
  it('merges rapid updates within 200ms', () => {
    const { rerender } = render(<MarkdownRenderer content="initial" />);

    // Rapid updates within 200ms
    rerender(<MarkdownRenderer content="update-a" />);
    rerender(<MarkdownRenderer content="update-b" />);
    rerender(<MarkdownRenderer content="update-c" />);

    // Fast-forward 200ms to let throttle flush
    act(() => { vi.advanceTimersByTime(200); });

    // The last content should be rendered (latest wins)
    expect(screen.getByText('update-c')).toBeTruthy();
  });

  // === 用例 19: 超过 200ms 即时渲染 ===
  it('renders immediately for updates spaced >200ms apart', () => {
    const { rerender } = render(<MarkdownRenderer content="first" />);

    expect(screen.getByText('first')).toBeTruthy();

    // Wait past throttle window
    act(() => { vi.advanceTimersByTime(300); });

    rerender(<MarkdownRenderer content="second" />);
    expect(screen.getByText('second')).toBeTruthy();

    act(() => { vi.advanceTimersByTime(300); });

    rerender(<MarkdownRenderer content="third" />);
    expect(screen.getByText('third')).toBeTruthy();
  });

  // === 用例 20: 节流后显示最新内容 ===
  it('shows latest content after throttle flush', () => {
    const { rerender } = render(<MarkdownRenderer content="a" />);

    // Rapid updates a -> b -> c within 200ms
    rerender(<MarkdownRenderer content="b" />);
    rerender(<MarkdownRenderer content="c" />);

    // Wait 200ms for throttle flush
    act(() => { vi.advanceTimersByTime(200); });

    // Should now show 'c' (the latest), not 'a' or 'b'
    expect(screen.getByText('c')).toBeTruthy();
  });
});


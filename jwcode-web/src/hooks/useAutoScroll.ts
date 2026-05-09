import { useRef, useCallback, useEffect } from 'react';

/**
 * 智能自动滚动 Hook
 * - 当内容更新时，如果用户在底部则自动滚到底部
 * - 用户手动向上滚动查看历史时，停止自动滚动
 * - 用户滚回底部时，恢复自动滚动
 *
 * @param deps 依赖数组，当这些值变化时触发自动滚动检查
 * @returns containerRef - 绑定到滚动容器的 ref
 */
export function useAutoScroll(deps: any[]) {
  const containerRef = useRef<HTMLDivElement>(null);
  const isAtBottomRef = useRef(true);

  const checkIsAtBottom = useCallback(() => {
    const el = containerRef.current;
    if (!el) return true;
    // 使用 50px 阈值，让"在底部附近"也算在底部，体验更自然
    const threshold = 50;
    return el.scrollHeight - el.scrollTop - el.clientHeight < threshold;
  }, []);

  const scrollToBottom = useCallback(() => {
    const el = containerRef.current;
    if (!el) return;
    el.scrollTop = el.scrollHeight;
  }, []);

  // 监听滚动事件，更新 isAtBottomRef
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;

    const handleScroll = () => {
      isAtBottomRef.current = checkIsAtBottom();
    };

    el.addEventListener('scroll', handleScroll, { passive: true });
    return () => el.removeEventListener('scroll', handleScroll);
  }, [checkIsAtBottom]);

  // 依赖变化时，如果用户在底部则自动滚动
  useEffect(() => {
    if (isAtBottomRef.current) {
      scrollToBottom();
    }
  }, deps);

  return { containerRef, isAtBottomRef };
}

import { useRef, useCallback, useEffect } from 'react';

/**
 * Smart auto-scroll hook — scrolls to bottom when new content arrives
 * and the user is already at the bottom. Uses RAF to avoid jitter.
 */
export function useAutoScroll(deps: any[]) {
  const containerRef = useRef<HTMLDivElement>(null);
  const isAtBottomRef = useRef(true);
  const rafIdRef = useRef<number>(0);

  const checkIsAtBottom = useCallback(() => {
    const el = containerRef.current;
    if (!el) return true;
    const threshold = 50;
    return el.scrollHeight - el.scrollTop - el.clientHeight < threshold;
  }, []);

  const scrollToBottom = useCallback(() => {
    if (rafIdRef.current) cancelAnimationFrame(rafIdRef.current);
    rafIdRef.current = requestAnimationFrame(() => {
      const el = containerRef.current;
      if (!el) return;
      el.scrollTop = el.scrollHeight;
    });
  }, []);

  // Track user scroll position
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;

    let ticking = false;
    const handleScroll = () => {
      if (!ticking) {
        requestAnimationFrame(() => {
          isAtBottomRef.current = checkIsAtBottom();
          ticking = false;
        });
        ticking = true;
      }
    };

    el.addEventListener('scroll', handleScroll, { passive: true });
    return () => {
      el.removeEventListener('scroll', handleScroll);
      if (rafIdRef.current) cancelAnimationFrame(rafIdRef.current);
    };
  }, [checkIsAtBottom]);

  // Auto-scroll when content changes
  useEffect(() => {
    if (isAtBottomRef.current) {
      scrollToBottom();
    }
  }, deps);

  return { containerRef, isAtBottomRef };
}

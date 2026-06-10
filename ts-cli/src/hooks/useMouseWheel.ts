/**
 * useMouseWheel — enable terminal SGR mouse tracking, handle mouse wheel +
 * scrollbar click/drag events.
 *
 * Modern terminal emulators (Windows Terminal, iTerm2, kitty, etc.) support
 * SGR mouse mode (DECSET 1006). When enabled, mouse events arrive as
 * escape sequences on stdin. We parse wheel, press, motion, and release events
 * and translate them into scrollOffset changes for the message list.
 *
 * Ink v5 does not natively support mouse events, so we hook into Ink's
 * internal_eventEmitter to receive raw input without switching stdin to
 * flowing mode (which would break Ink's 'readable'-based input pipeline).
 *
 * Set JWCODE_NO_MOUSE=1 to disable mouse support.
 */
import { useEffect, useRef } from 'react';
import { useStdin } from 'ink';
import { updateAppState } from './useAppState.js';

const MOUSE_ENABLE  = '\x1b[?1000h\x1b[?1002h\x1b[?1006h'; // button events + motion + SGR
const MOUSE_DISABLE = '\x1b[?1006l\x1b[?1002l\x1b[?1000l';

// ---- shared scrollbar geometry (updated by ChatArea) ----

interface ScrollGeometry {
  topRow: number;          // absolute terminal row of scrollbar top (1-indexed via SGR)
  trackHeight: number;     // visible rows in scrollbar track (viewportHeight - 2)
  contentHeight: number;   // total measured content height in lines
  viewportHeight: number;  // viewport height in lines
  termCols: number;        // terminal width in columns
}

let _scrollGeo: ScrollGeometry = { topRow: 0, trackHeight: 0, contentHeight: 0, viewportHeight: 0, termCols: 80 };

export function setScrollGeometry(geo: ScrollGeometry): void {
  _scrollGeo = geo;
}

// ---- SGR parser ----

interface SgrEvent {
  btn: number;
  col: number;
  row: number;
  final: 'M' | 'm'; // M = press/motion, m = release
}

function parseSgr(data: string): SgrEvent | null {
  const match = data.match(/\x1b\[<(\d+);(\d+);(\d+)([Mm])/);
  if (!match) return null;
  return {
    btn: parseInt(match[1], 10),
    col: parseInt(match[2], 10),
    row: parseInt(match[3], 10),
    final: match[4] as 'M' | 'm',
  };
}

const SCROLLBAR_COL_WIDTH = 2; // rightmost N columns considered scrollbar area (matches Scrollbar width=2)

export function useMouseWheel(): void {
  const { stdin, internal_eventEmitter } = useStdin();
  const bufferRef = useRef('');
  const draggingRef = useRef(false);
  const lastDragTimeRef = useRef(0);

  useEffect(() => {
    if (!stdin || !internal_eventEmitter || process.env.JWCODE_NO_MOUSE === '1') return;

    stdin.write(MOUSE_ENABLE);

    const onData = (chunk: Buffer | string) => {
      const text = typeof chunk === 'string' ? chunk : chunk.toString('utf-8');
      bufferRef.current += text;

      let event = parseSgr(bufferRef.current);
      let processedUpTo = 0;

      if (event) {
        const seqStr = `\x1b[<${event.btn};${event.col};${event.row}`;
        const fullSeq = seqStr + event.final;
        const seqEnd = bufferRef.current.indexOf(fullSeq) + fullSeq.length;

        // ---- wheel ----
        if (event.btn === 64 || event.btn === 65) {
          const scrollStep = event.btn === 64 ? 3 : -3;
          updateAppState(prev => {
            const maxScroll = Math.max(0, _scrollGeo.contentHeight - _scrollGeo.viewportHeight);
            const newOffset = Math.max(0, Math.min(prev.scrollOffset + scrollStep, maxScroll));
            return { ...prev, scrollOffset: newOffset };
          });
        }

        // ---- left press in scrollbar area (rightmost columns) ----
        if (event.btn === 0 && event.final === 'M') {
          const geo = _scrollGeo;
          const inScrollbarCol = event.col > geo.termCols - SCROLLBAR_COL_WIDTH;
          if (inScrollbarCol && geo.trackHeight > 0 && geo.contentHeight > geo.viewportHeight) {
            // SGR rows are 1-indexed; topRow is the absolute row of the ▲ arrow.
            // The track itself starts at topRow + 1 (just below the ▲), so:
            const trackRow = event.row - geo.topRow - 1;
            if (trackRow >= 0 && trackRow < geo.trackHeight) {
              const maxOffset = geo.contentHeight - geo.viewportHeight;
              const ratio = 1 - trackRow / Math.max(1, geo.trackHeight - 1);
              const newOffset = Math.round(ratio * maxOffset);
              updateAppState(prev => ({
                ...prev,
                scrollOffset: Math.max(0, Math.min(newOffset, maxOffset)),
              }));
              draggingRef.current = true;
              lastDragTimeRef.current = Date.now();
            }
          }
        }

        // ---- motion while left button held (drag) ----
        if (event.btn === 32 && draggingRef.current) {
          const now = Date.now();
          if (now - lastDragTimeRef.current < 33) {
            processedUpTo = seqEnd;
            bufferRef.current = bufferRef.current.slice(processedUpTo);
            return;
          }
          lastDragTimeRef.current = now;
          const geo = _scrollGeo;
          const trackRow = event.row - geo.topRow - 1;
          const maxOffset = geo.contentHeight - geo.viewportHeight;
          const ratio = 1 - Math.max(0, Math.min(1, trackRow / Math.max(1, geo.trackHeight - 1)));
          const newOffset = Math.round(ratio * maxOffset);
          updateAppState(prev => ({
            ...prev,
            scrollOffset: Math.max(0, Math.min(newOffset, maxOffset)),
          }));
        }

        // ---- release ----
        if (event.btn === 0 && event.final === 'm') {
          draggingRef.current = false;
        }

        processedUpTo = seqEnd;
      }

      if (processedUpTo > 0) {
        bufferRef.current = bufferRef.current.slice(processedUpTo);
      } else if (bufferRef.current.length > 128) {
        bufferRef.current = bufferRef.current.slice(-64);
      }
    };

    internal_eventEmitter.on('input', onData);

    return () => {
      internal_eventEmitter.removeListener('input', onData);
      stdin.write(MOUSE_DISABLE);
      bufferRef.current = '';
      draggingRef.current = false;
    };
  }, [stdin, internal_eventEmitter]);
}

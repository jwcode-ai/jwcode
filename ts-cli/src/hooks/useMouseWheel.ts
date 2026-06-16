/**
 * useMouseWheel — enable terminal SGR mouse tracking and handle mouse wheel events.
 *
 * Modern terminal emulators (Windows Terminal, iTerm2, kitty, etc.) support
 * SGR mouse mode (DECSET 1006). When enabled, mouse events arrive as
 * escape sequences on stdin. We parse wheel events and translate them into
 * scrollOffset changes for the message list.
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

const MOUSE_ENABLE  = '\x1b[?1000h\x1b[?1002h\x1b[?1006h'; // button events + SGR
const MOUSE_DISABLE = '\x1b[?1006l\x1b[?1002l\x1b[?1000l';

function parseSgrMouse(data: string): { btn: number; col: number; row: number } | null {
  const match = data.match(/\x1b\[<(\d+);(\d+);(\d+)([Mm])/);
  if (!match) return null;
  return {
    btn: parseInt(match[1], 10),
    col: parseInt(match[2], 10),
    row: parseInt(match[3], 10),
  };
}

export function useMouseWheel(): void {
  const { stdin, internal_eventEmitter } = useStdin();
  const bufferRef = useRef('');

  useEffect(() => {
    if (!stdin || !internal_eventEmitter || process.env.JWCODE_NO_MOUSE === '1') return;

    stdin.write(MOUSE_ENABLE);

    const onData = (chunk: Buffer | string) => {
      const text = typeof chunk === 'string' ? chunk : chunk.toString('utf-8');

      bufferRef.current += text;

      let mouseEvent = parseSgrMouse(bufferRef.current);
      let processedUpTo = 0;

      if (mouseEvent) {
        const isWheelUp   = mouseEvent.btn === 64;
        const isWheelDown = mouseEvent.btn === 65;

        if (isWheelUp || isWheelDown) {
          const scrollStep = isWheelUp ? 3 : -3;
          updateAppState(prev => {
            const newOffset = Math.max(0, Math.min(
              prev.scrollOffset + scrollStep,
              prev.messages.length,
            ));
            return { ...prev, scrollOffset: newOffset };
          });
        }

        const seqStr = `\x1b[<${mouseEvent.btn};${mouseEvent.col};${mouseEvent.row}`;
        const lastChar = bufferRef.current.includes(seqStr + 'M') ? 'M' : 'm';
        const fullSeq = seqStr + lastChar;
        const seqEnd = bufferRef.current.indexOf(fullSeq) + fullSeq.length;
        processedUpTo = Math.max(processedUpTo, seqEnd);
      }

      if (processedUpTo > 0) {
        bufferRef.current = bufferRef.current.slice(processedUpTo);
      } else if (bufferRef.current.length > 128) {
        bufferRef.current = bufferRef.current.slice(-64);
      }
    };

    // Use internal_eventEmitter to avoid switching stdin into flowing mode,
    // which would break Ink's 'readable'-based input handling.
    internal_eventEmitter.on('input', onData);

    return () => {
      internal_eventEmitter.removeListener('input', onData);
      stdin.write(MOUSE_DISABLE);
      bufferRef.current = '';
    };
  }, [stdin, internal_eventEmitter]);
}

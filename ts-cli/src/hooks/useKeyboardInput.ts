/**
 * Keyboard input handler — escape, scroll, mode toggle.
 * Extracted from App.tsx to keep the root component focused on layout.
 */
import { useRef } from 'react';
import { useInput } from 'ink';
import type { JwCodeClient } from '../client.js';
import { updateAppState } from './useAppState.js';
import { HELP_TEXT } from '../commands/index.js';

interface KeyboardOptions {
  showApproval: boolean;
  showHelp: boolean;
  isGenerating: boolean;
  terminalRows: number;
  paletteOpen: boolean;
  clientRef: React.MutableRefObject<JwCodeClient | null>;
  onDenyApproval: () => void;
  onCloseHelp: () => void;
  setHelpScroll: (updater: (prev: number) => number) => void;
}

export function useKeyboardInput(opts: KeyboardOptions) {
  const {
    showApproval, showHelp, isGenerating, terminalRows, paletteOpen,
    clientRef, onDenyApproval, onCloseHelp, setHelpScroll,
  } = opts;

  const lastEscRef = useRef(0);

  useInput((input, key) => {
    if (key.escape) {
      if (showApproval) {
        // Let ApprovalModal handle Esc — skip to avoid double deny
        return;
      }
      if (showHelp) {
        onCloseHelp();
        return;
      }
      if (isGenerating) {
        const now = Date.now();
        const prev = lastEscRef.current;
        lastEscRef.current = now;
        if (prev > 0 && (now - prev) < 500) {
          clientRef.current?.stop();
          updateAppState(prev => ({ ...prev, statusText: '已终止 (ESC×2)' }));
        } else {
          clientRef.current?.pause();
          updateAppState(prev => ({ ...prev, statusText: '已暂停 — 再按 ESC 终止' }));
        }
        return;
      }
    }
    // Help scrolling
    if (showHelp) {
      const helpLines = HELP_TEXT.split('\n');
      const helpMax = Math.max(5, terminalRows - 12);
      if (key.pageUp || key.upArrow) {
        setHelpScroll(prev => Math.min(prev + (key.pageUp ? helpMax : 1), helpLines.length - 1));
        return;
      }
      if (key.pageDown || key.downArrow) {
        setHelpScroll(prev => Math.max(0, prev - (key.pageDown ? helpMax : 1)));
        return;
      }
      if ((key as any).home) { setHelpScroll(() => helpLines.length - 1); return; }
      if ((key as any).end) { setHelpScroll(() => 0); return; }
    }
    // Ctrl+E: toggle tool call expand/collapse
    if (key.ctrl && input === 'e') {
      updateAppState(prev => ({
        ...prev,
        toolCallsExpanded: !prev.toolCallsExpanded,
      }));
      return;
    }
    // Scroll message list
    // scrollOffset: 0 = bottom (newest messages), higher = scrolled back to older
    const maxVisible = Math.max(5, terminalRows - 10);
    if (key.pageUp) {
      updateAppState(prev => {
        const maxScroll = Math.max(0, prev.messages.length - maxVisible);
        return { ...prev, scrollOffset: Math.min(maxScroll, prev.scrollOffset + 5) };
      });
      return;
    }
    if (key.upArrow && !showApproval && !paletteOpen) {
      updateAppState(prev => {
        const maxScroll = Math.max(0, prev.messages.length - maxVisible);
        return { ...prev, scrollOffset: Math.min(maxScroll, prev.scrollOffset + 1) };
      });
      return;
    }
    if (key.pageDown) {
      updateAppState(prev => ({
        ...prev,
        scrollOffset: Math.max(0, prev.scrollOffset - 5),
      }));
      return;
    }
    if (key.downArrow && !showApproval && !paletteOpen) {
      updateAppState(prev => ({
        ...prev,
        scrollOffset: Math.max(0, prev.scrollOffset - 1),
      }));
      return;
    }
    if ((key as any).home) {
      updateAppState(prev => {
        const maxScroll = Math.max(0, prev.messages.length - maxVisible);
        return { ...prev, scrollOffset: maxScroll };
      });
      return;
    }
    if ((key as any).end) {
      updateAppState(prev => ({
        ...prev, scrollOffset: 0,
      }));
      return;
    }
    // Tab: toggle plan/act mode
    if (key.tab) {
      updateAppState(prev => ({ ...prev, planMode: !prev.planMode, planWaiting: false }));
      return;
    }
  });
}

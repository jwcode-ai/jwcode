/**
 * Keyboard input handler — 对标 Codex TUI 键盘体系。
 *
 * Shortcuts:
 *   Esc            Pause generation (double: stop)
 *   Ctrl+S         Pause generation
 *   Ctrl+L         Clear screen (messages)
 *   Ctrl+N         New session
 *   Ctrl+R         Session history picker
 *   Ctrl+E         Toggle tool call expand/collapse
 *   Ctrl+O         Expand all tool calls in current message
 *   F1 / Ctrl+H    Toggle help panel
 *   Tab            Toggle Plan/Act mode
 *   PgUp/PgDn      Scroll message history (±viewportHeight lines)
 *   Up/Down        Scroll message history (±1 line)
 *   Home/End       Jump to oldest/newest message
 *
 * scrollOffset unit is **lines** (offset from top of content into viewport).
 * `maxOffset` is derived from `contentHeight - viewportHeight` read live from
 * the store (set by ChatArea's measurement loop).
 */
import { useRef } from 'react';
import { useInput } from 'ink';
import type { JwCodeClient } from '../client.js';
import { getStore, updateAppState } from './useAppState.js';
import { HELP_TEXT } from '../commands/index.js';

interface KeyboardOptions {
  showApproval: boolean;
  showHelp: boolean;
  isGenerating: boolean;
  terminalRows: number;
  viewportHeight: number;
  paletteOpen: boolean;
  clientRef: React.MutableRefObject<JwCodeClient | null>;
  onDenyApproval: () => void;
  onCloseHelp: () => void;
  setHelpScroll: (updater: (prev: number) => number) => void;
  onToggleHelp?: () => void;
  onNewSession?: () => void;
  onSessionHistory?: () => void;
}

export function useKeyboardInput(opts: KeyboardOptions) {
  const {
    showApproval, showHelp, isGenerating, terminalRows, viewportHeight, paletteOpen,
    clientRef, onDenyApproval, onCloseHelp, setHelpScroll,
    onToggleHelp, onNewSession, onSessionHistory,
  } = opts;

  const lastEscRef = useRef(0);

  useInput((input, key) => {
    // --- F1 / Ctrl+H: Toggle help ---
    if ((key as any).f1 || (key.ctrl && input === 'h')) {
      if (onToggleHelp) {
        onToggleHelp();
      } else if (showHelp) {
        onCloseHelp();
      }
      return;
    }

    // --- Ctrl+N: New session ---
    if (key.ctrl && input === 'n' && !showApproval && !isGenerating) {
      onNewSession?.();
      return;
    }

    // --- Ctrl+R: Session history ---
    if (key.ctrl && input === 'r' && !showApproval && !isGenerating) {
      onSessionHistory?.();
      return;
    }

    // --- Escape ---
    if (key.escape) {
      if (showApproval) {
        return; // ApprovalModal handles Esc
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
          updateAppState(prev => ({ ...prev, statusText: 'Stopped (ESC×2)' }));
        } else {
          clientRef.current?.pause();
          updateAppState(prev => ({ ...prev, statusText: 'Paused — press ESC again to stop' }));
        }
        return;
      }
    }

    // --- Ctrl+S: Pause/Resume generation ---
    if (key.ctrl && input === 's' && !showApproval) {
      if (isGenerating) {
        clientRef.current?.pause();
        updateAppState(prev => ({ ...prev, statusText: 'Paused (Ctrl+S). Press again to resume.' }));
      }
      return;
    }

    // --- Ctrl+L: Clear screen ---
    if (key.ctrl && input === 'l' && !showApproval && !isGenerating) {
      updateAppState(prev => ({
        ...prev,
        messages: [],
        currentMessage: null,
        scrollOffset: 0,
        contentHeight: 0,
        statusText: 'Screen cleared',
      }));
      return;
    }

    // --- Ctrl+O: Expand all tool calls ---
    if (key.ctrl && input === 'o') {
      updateAppState(prev => ({
        ...prev,
        toolCallsExpanded: !prev.toolCallsExpanded,
      }));
      return;
    }

    // --- Ctrl+E: Toggle tool call expand/collapse ---
    if (key.ctrl && input === 'e') {
      updateAppState(prev => ({
        ...prev,
        toolCallsExpanded: !prev.toolCallsExpanded,
      }));
      return;
    }

    // --- Ctrl+G removed: duplicate of Home key ---

    // --- Help scrolling ---
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

    // --- Scroll message list (line-based, maxOffset read live from store) ---
    const maxOffsetLive = () => {
      const { contentHeight } = getStore().getState();
      return Math.max(0, contentHeight - viewportHeight);
    };
    const pageStep = Math.max(1, viewportHeight - 2);
    if (key.pageUp) {
      updateAppState(prev => ({
        ...prev,
        scrollOffset: Math.min(maxOffsetLive(), prev.scrollOffset + pageStep),
      }));
      return;
    }
    if (key.upArrow && !showApproval && !paletteOpen) {
      updateAppState(prev => ({
        ...prev,
        scrollOffset: Math.min(maxOffsetLive(), prev.scrollOffset + 1),
      }));
      return;
    }
    if (key.pageDown) {
      updateAppState(prev => ({
        ...prev,
        scrollOffset: Math.max(0, prev.scrollOffset - pageStep),
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
      updateAppState(prev => ({ ...prev, scrollOffset: maxOffsetLive() }));
      return;
    }
    if ((key as any).end) {
      updateAppState(prev => ({ ...prev, scrollOffset: 0 }));
      return;
    }

    // --- Tab: Toggle plan/act mode ---
    if (key.tab) {
      updateAppState(prev => ({ ...prev, planMode: !prev.planMode, planWaiting: false }));
      return;
    }
  });
}

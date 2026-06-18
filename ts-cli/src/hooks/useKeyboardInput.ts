import { useRef, type MutableRefObject } from 'react';
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
  clientRef: MutableRefObject<JwCodeClient | null>;
  onDenyApproval: () => void;
  onCloseHelp: () => void;
  setHelpScroll: (updater: (prev: number) => number) => void;
  onSwitchAnimal?: () => void;
}

export function useKeyboardInput(opts: KeyboardOptions) {
  const {
    showApproval, showHelp, isGenerating, terminalRows, paletteOpen,
    clientRef, onCloseHelp, setHelpScroll, onSwitchAnimal,
  } = opts;

  const lastEscRef = useRef(0);

  useInput((input, key) => {
    // Ctrl+. swaps the welcome mascot. Only responds in the welcome idle
    // state (no generation, no overlay, no palette) to avoid conflicts.
    if (key.ctrl && input === '.') {
      if (onSwitchAnimal && !showApproval && !showHelp && !paletteOpen && !isGenerating) {
        onSwitchAnimal();
      }
      return;
    }

    if (key.escape) {
      if (showApproval) return;
      if (showHelp) {
        onCloseHelp();
        return;
      }
      if (isGenerating) {
        const now = Date.now();
        const prev = lastEscRef.current;
        lastEscRef.current = now;
        if (prev > 0 && now - prev < 500) {
          clientRef.current?.stop();
          updateAppState(prevState => ({ ...prevState, statusText: 'Stopped (Esc twice)' }));
        } else {
          clientRef.current?.pause();
          updateAppState(prevState => ({ ...prevState, statusText: 'Paused -- press Esc again to stop' }));
        }
        return;
      }
    }

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

    if (key.ctrl && input === 'e') {
      updateAppState(prev => ({ ...prev, toolCallsExpanded: !prev.toolCallsExpanded }));
      return;
    }

    if (key.pageUp) {
      updateAppState(prev => ({ ...prev, scrollOffset: Math.max(0, prev.scrollOffset - 5) }));
      return;
    }
    if (key.upArrow && !showApproval && !paletteOpen) {
      updateAppState(prev => ({ ...prev, scrollOffset: Math.max(0, prev.scrollOffset - 1) }));
      return;
    }
    if (key.pageDown) {
      updateAppState(prev => ({ ...prev, scrollOffset: prev.scrollOffset + 5 }));
      return;
    }
    if (key.downArrow && !showApproval && !paletteOpen) {
      updateAppState(prev => ({ ...prev, scrollOffset: prev.scrollOffset + 1 })); 
      return;
    }
    if ((key as any).home) {
      updateAppState(prev => ({ ...prev, scrollOffset: 0 }));
      return;
    }
    if ((key as any).end) {
      updateAppState(prev => ({ ...prev, scrollOffset: prev.messages.length }));
      return;
    }
    if (key.tab) {
      updateAppState(prev => ({ ...prev, planMode: !prev.planMode, planWaiting: false }));
    }
  });
}

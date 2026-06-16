/**
 * Command execution hook — handles /commands and normal chat dispatch.
 * Extracted from App.tsx to keep the root component focused on layout.
 */
import { useCallback } from 'react';
import type { JwCodeClient } from '../client.js';
import { updateAppState } from './useAppState.js';
import { saveToHistory } from '../components/TextInput.js';
import { createMessage } from '../protocol.js';
import { SLASH_COMMANDS } from '../commands/index.js';
import { expandPastes, clearAllPastes } from '../pasteBuffer.js';

interface CommandHandlerOptions {
  clientRef: React.MutableRefObject<JwCodeClient | null>;
  planModeRef: React.MutableRefObject<boolean>;
  onExit: () => void;
  setInput: (v: string) => void;
  setShowHelp: (v: boolean, scroll?: number) => void;
  setShowPalette: (v: boolean) => void;
}

export function useCommandHandler(opts: CommandHandlerOptions) {
  const { clientRef, planModeRef, onExit, setInput, setShowHelp, setShowPalette } = opts;

  const executeCommand = useCallback((value: string) => {
    const text = value.trim();
    if (!text || !clientRef.current) return;
    setInput('');
    setShowHelp(false);
    setShowPalette(false);

    const parts = text.startsWith('/') ? text.split(/\s+/) : [];
    const cmd = parts[0] || null;
    const cmdArg = parts.slice(1).join(' ');

    if (cmd && cmd in SLASH_COMMANDS) {
      const def = SLASH_COMMANDS[cmd];
      if (def === null) {
        setShowHelp(true, 0);
        return;
      }
      const { action, needsArg } = def;
      const client = clientRef.current;

      switch (action) {
        case '__exit__':
          onExit();
          return;
        case '__confirm_plan':
          updateAppState(prev => {
            if (!prev.planWaiting) return prev;
            client?.planConfirm();
            return { ...prev, planWaiting: false };
          });
          return;
        case '__cancel_plan':
          updateAppState(prev => ({ ...prev, planWaiting: false }));
          return;
        case 'plan_mode':
          updateAppState(prev => ({ ...prev, planMode: !prev.planMode }));
          return;
        case 'auto_mode':
          updateAppState(prev => ({ ...prev, autoMode: !prev.autoMode }));
          return;
        case 'clear':
          console.clear();
          clearAllPastes();
          updateAppState(prev => ({
            ...prev,
            messages: [], currentMessage: null,
          }));
          return;
        case 'model_change':
          if (needsArg && cmdArg) client?.switchModel(cmdArg);
          return;
        case 'show_context':
          updateAppState(prev => ({
            ...prev,
            statusText: '会话消息: ' + prev.messages.length + ' | 模式: ' + (prev.planMode ? '规划' : '执行') + ' | 自动: ' + (prev.autoMode ? '开' : '关') + ' | 模型: ' + (prev.modelName || '未连接'),
          }));
          return;
        case 'stop': client?.stop(); return;
        case 'pause': client?.pause(); return;
        case 'resume': client?.resume(); return;
        case 'doctor': client?.doctor(); return;
        case 'rewind': client?.rewind(); return;
        case 'compact': client?.compact(); return;
        case 'init': client?.init(); return;
        case 'effort': if (cmdArg) client?.effort(cmdArg); return;
        case 'branch': if (cmdArg) client?.branch(cmdArg); return;
        case 'mcp': if (cmdArg) client?.mcp(cmdArg); return;
        case 'skills': client?.skills(); return;
        case 'agents': client?.agents(); return;
        case 'config': if (cmdArg) client?.config(cmdArg); return;
        case 'plugin': if (cmdArg) client?.plugin(cmdArg); return;
        case 'tokens': client?.send('tokens'); return;
        case 'export': if (cmdArg) client?.send('export', undefined, { path: cmdArg }); return;
        case 'checkpoint': client?.send('checkpoint'); return;
        case 'test': client?.send('test'); return;
        case 'lint': client?.send('lint'); return;
        case 'search': if (cmdArg) client?.send('search', undefined, { query: cmdArg }); return;
        case 'project': client?.send('project'); return;
      }
      return;
    }

    // Normal chat — ignore unmatched / prefixes
    if (text.startsWith('/') && !(cmd && cmd in SLASH_COMMANDS)) return;
    // Expand "[Pasted text #N ...]" tokens to the real clipboard content
    const expanded = expandPastes(text);
    saveToHistory(text); // save the compact view, not the expanded text
    const msg = createMessage('user', expanded);
    updateAppState(prev => ({ ...prev, messages: [...prev.messages, msg] }));
    clientRef.current!.chat(expanded, planModeRef.current);
  }, [clientRef, planModeRef, onExit, setInput, setShowHelp, setShowPalette]);

  return { executeCommand };
}


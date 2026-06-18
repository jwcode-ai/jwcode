import { useCallback, type MutableRefObject } from 'react';
import type { JwCodeClient } from '../client.js';
import { updateAppState } from './useAppState.js';
import { saveToHistory } from '../components/TextInput.js';
import { createMessage } from '../protocol.js';
import { SLASH_COMMANDS, getUsage } from '../commands/index.js';
import { expandPastes, clearAllPastes } from '../pasteBuffer.js';
import { queryGuard } from './useQueryGuard.js';

interface CommandHandlerOptions {
  clientRef: MutableRefObject<JwCodeClient | null>;
  planModeRef: MutableRefObject<boolean>;
  referencedFilesRef: MutableRefObject<string[]>;
  onExit: () => void;
  setInput: (v: string) => void;
  setShowHelp: (v: boolean, scroll?: number) => void;
  setShowPalette: (v: boolean) => void;
}

function pushAssistant(content: string): void {
  const msg = createMessage('assistant', content);
  updateAppState(prev => ({ ...prev, messages: [...prev.messages, msg] }));
}

export function useCommandHandler(opts: CommandHandlerOptions) {
  const { clientRef, planModeRef, referencedFilesRef, onExit, setInput, setShowHelp, setShowPalette } = opts;

  const executeCommand = useCallback(async (value: string) => {
    const text = value.trim();
    if (!text) return;
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
      if (needsArg && !cmdArg) {
        pushAssistant(`Usage: ${getUsage(cmd)}`);
        return;
      }

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
          clearAllPastes();
          updateAppState(prev => ({ ...prev, messages: [], currentMessage: null, statusText: '' }));
          return;
        case 'show_context':
          updateAppState(prev => ({
            ...prev,
            statusText: `Messages: ${prev.messages.length} | Mode: ${prev.planMode ? 'Plan' : 'Act'} | Auto: ${prev.autoMode ? 'on' : 'off'} | Model: ${prev.modelName || 'not connected'}`,
          }));
          return;
        case 'stop':
          client?.stop();
          return;
        case 'pause':
          client?.pause();
          return;
        case 'resume':
          client?.resume();
          return;
        default:
          client?.executeCommand(cmd, cmdArg);
          return;
      }
      return;
    }

    if (text.startsWith('/') && !(cmd && cmd in SLASH_COMMANDS)) {
      pushAssistant(`Unknown command: ${cmd}. Type /help to see available commands.`);
      return;
    }

    const client = clientRef.current;
    if (!client) {
      pushAssistant('Backend is not connected yet.');
      return;
    }

    const guardGen = queryGuard.reserve();
    if (guardGen === null) {
      queryGuard.enqueue(text);
      return;
    }

    const files = referencedFilesRef.current;
    referencedFilesRef.current = [];
    let finalText = text;
    const fileCtxs: string[] = [];
    for (const filePath of files) {
      try {
        const content = await client.readFileContent(filePath);
        if (content) {
          finalText = finalText.replace(filePath, '').trim();
          const ext = filePath.split('.').pop() || '';
          fileCtxs.push(
            `<context ref="${filePath}">\n\`\`\`${ext}\n${content}\n\`\`\`\n</context>`,
          );
        }
      } catch {
        // Leave the path in the prompt when the backend cannot read it.
      }
    }
    if (fileCtxs.length > 0) {
      finalText = `${fileCtxs.join('\n\n')}\n\n${finalText.trim()}`.trim();
    }

    const expanded = expandPastes(finalText);
    saveToHistory(finalText);
    const msg = createMessage('user', expanded);
    updateAppState(prev => ({ ...prev, messages: [...prev.messages, msg] }));
    client.chat(expanded, planModeRef.current);
  }, [clientRef, planModeRef, referencedFilesRef, onExit, setInput, setShowHelp, setShowPalette]);

  return { executeCommand };
}

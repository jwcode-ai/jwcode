/**
 * Command execution hook — handles /commands, normal chat dispatch,
 * @ file references, and paste expansion. Single entry point for all
 * input submitted from the prompt.
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
  referencedFilesRef: React.MutableRefObject<string[]>;
  onExit: () => void;
  setInput: (v: string) => void;
  setShowHelp: (v: boolean, scroll?: number) => void;
  setShowPalette: (v: boolean) => void;
}

export function useCommandHandler(opts: CommandHandlerOptions) {
  const { clientRef, planModeRef, referencedFilesRef, onExit, setInput, setShowHelp, setShowPalette } = opts;

  const executeCommand = useCallback(async (value: string) => {
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
            statusText: `会话消息: ${prev.messages.length} | 模式: ${prev.planMode ? '规划' : '执行'} | 自动: ${prev.autoMode ? '开' : '关'} | 模型: ${prev.modelName || '未连接'}`,
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
        case 'memory': client?.send('memory'); return;
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

    const client = clientRef.current;
    if (!client) return;

    // Resolve @ file references → attach as <context> blocks
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
            `<context ref="${filePath}">\n\`\`\`${ext}\n${content}\n\`\`\`\n</context>`
          );
        }
      } catch { /* file not readable, leave path in text */ }
    }
    if (fileCtxs.length > 0) {
      finalText = fileCtxs.join('\n\n') + '\n\n' + finalText.trim();
      finalText = finalText.trim();
    }

    // Expand "[Pasted text #N ...]" tokens to the real clipboard content
    const expanded = expandPastes(finalText);
    saveToHistory(finalText); // save the compact view, not the expanded text
    const msg = createMessage('user', expanded);
    updateAppState(prev => ({ ...prev, messages: [...prev.messages, msg] }));
    client.chat(expanded, planModeRef.current);
  }, [clientRef, planModeRef, referencedFilesRef, onExit, setInput, setShowHelp, setShowPalette]);

  return { executeCommand };
}

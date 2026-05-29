import { useTokenStore } from '../../stores/tokenStore';
import { useCommandStore } from '../../stores/commandStore';
import { useSettingsStore } from '../../stores/settingsStore';
import wsService from '../../services/websocket';
import type { LogEntry } from '../../types';

interface SystemCtx {
  activeTab: string;
  setLogs: React.Dispatch<React.SetStateAction<LogEntry[]>>;
  setUnreadLogs: React.Dispatch<React.SetStateAction<number>>;
}

export function handleSystemMessage(rawType: string, rawData: any, _sessionId: string, ctx: SystemCtx) {
  switch (rawType) {
    case 'token_update':
      try {
        const tokenData = typeof rawData === 'string' ? JSON.parse(rawData) : (rawData || {});
        if (tokenData.totalTokens > 0) {
          useTokenStore.getState().updateUsage({
            promptTokens: tokenData.promptTokens || 0,
            completionTokens: tokenData.completionTokens || 0,
            totalTokens: tokenData.totalTokens || 0,
          });
        }
        if (tokenData.model) useTokenStore.getState().setModel(tokenData.model);
      } catch { /* ignore */ }
      break;

    case 'auth_required':
      let savedToken = localStorage.getItem('auth_token');
      if (!savedToken) { savedToken = 'default-token'; localStorage.setItem('auth_token', savedToken); }
      wsService.setToken(savedToken);
      wsService.setAuthenticated(false);
      wsService.send({ type: 'auth', token: savedToken });
      break;

    case 'auth_success':
      wsService.setAuthenticated(true);
      wsService.send({ type: 'subscribe_logs' });
      wsService.send({ type: 'get_commands' });
      const currentDir = useSettingsStore.getState().workspaceDir;
      if (currentDir) wsService.send({ type: 'workspace', message: currentDir });
      break;

    case 'auth_failed':
      console.error('[WS] Auth failed:', rawData);
      break;

    case 'log':
      try {
        const logData = typeof rawData === 'string' ? JSON.parse(rawData) : rawData;
        const newLog: LogEntry = {
          id: `log-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
          level: logData.level || 'info', source: logData.source || 'System',
          message: logData.message || '', timestamp: logData.timestamp || Date.now(),
        };
        ctx.setLogs(prev => [...prev, newLog].slice(-500));
        if (ctx.activeTab !== 'logs') ctx.setUnreadLogs(prev => prev + 1);
      } catch (e) { console.error('Failed to parse log:', e); }
      break;

    case 'commands_list':
      try {
        const commands = JSON.parse(rawData || '[]');
        useCommandStore.getState().setBackendCommands(commands);
      } catch (e) { console.error('Failed to parse commands list:', e); }
      break;

    case 'ping':
      wsService.send({ type: 'pong', data: Date.now().toString() });
      break;

    case 'workspace_changed':
      try {
        const wsData = JSON.parse(rawData || '{}');
        if (wsData.newDir) useSettingsStore.getState().setWorkspaceDir(wsData.newDir);
      } catch {
        // ignore, keep old dir
      }
      break;
  }
}

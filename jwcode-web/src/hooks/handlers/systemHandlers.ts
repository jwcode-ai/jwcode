import { useTokenStore } from '../../stores/tokenStore';
import { useCommandStore } from '../../stores/commandStore';
import { useSettingsStore } from '../../stores/settingsStore';
import wsService from '../../services/websocket';
import { toast } from '../../stores/toastStore';
import { errLog } from '../../stores/errorStore';
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
      errLog.error('Auth failed', String(rawData));
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
      } catch (e) { errLog.warn('Failed to parse log entry', String(e)); }
      break;

    case 'commands_list':
      try {
        const commands = JSON.parse(rawData || '[]');
        useCommandStore.getState().setBackendCommands(commands);
      } catch (e) { errLog.warn('Failed to parse commands list', String(e)); }
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

    case "degradation_update":
      try {
        const degData = typeof rawData === "string" ? JSON.parse(rawData) : (rawData || {});
        const active = degData.active as boolean;
        const mode = (degData.mode as string) || "normal";
        const retryCount = Number(degData.retryCount) || 0;
        const maxRetries = Number(degData.maxRetries) || 0;
        const message = (degData.message as string) || "";

        const modeLabels: Record<string, string> = {
          normal: "normal",
          retrying: "retrying",
          alternative: "alternative model",
          degraded: "degraded",
          verifying: "verifying",
          verified_fail: "verify failed",
        };
        const label = modeLabels[mode] || mode;

        useTokenStore.getState().setDegradation({
          active,
          retryCount,
          maxRetries,
          mode: mode as any,
          message,
          label,
        });

        if (active) {
          const retryInfo = retryCount > 0 ? ` (${retryCount}/${maxRetries})` : "";
          toast.warning(message || `API Degraded: ${label}${retryInfo}`, 8000);
          ctx.setLogs(prev => [...prev, {
            id: `deg-${Date.now()}`,
            level: "warn" as const,
            source: "Infrastructure",
            message: `Degradation: ${label} | ${message || "no details"}`,
            timestamp: Date.now(),
          }].slice(-500));
        } else {
          toast.success("API Normal");
        }
      } catch (e) { errLog.warn('Degradation update parse error', String(e)); }
      break;

    case "doctor_result":
      try {
        const doctorData = typeof rawData === "string" ? JSON.parse(rawData) : (rawData || {});
        const status = doctorData.overallStatus || doctorData.status || "unknown";
        const healthRate = doctorData.healthRate;
        const issues = doctorData.issues || [];

        const statusLabels: Record<string, string> = {
          healthy: "healthy",
          degraded: "degraded",
          unhealthy: "unhealthy",
          unknown: "unknown",
        };
        const statusLabel = statusLabels[status as string] || status;
        const healthText = typeof healthRate === "number"
          ? ` (${Math.round(healthRate * 100)}%)`
          : "";

        if (status === "healthy") {
          toast.success(`Doctor: ${statusLabel}${healthText}`);
        } else {
          toast.warning(`Doctor: ${statusLabel}${healthText}`, 10000);
          if (issues && issues.length > 0) {
            for (const issue of issues) {
              ctx.setLogs(prev => [...prev, {
                id: `doctor-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
                level: "warn" as const,
                source: "Doctor",
                message: typeof issue === "string" ? issue : (issue.message || JSON.stringify(issue)),
                timestamp: Date.now(),
              }].slice(-500));
            }
          }
        }
      } catch (e) { errLog.warn('Doctor result parse error', String(e)); }
      break;
  }
}
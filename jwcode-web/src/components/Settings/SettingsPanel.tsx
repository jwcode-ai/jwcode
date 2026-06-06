import { memo, useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Settings, Palette, RefreshCw } from 'lucide-react';
import { useSettingsStore } from '../../stores/settingsStore';
import { useSessionStore } from '../../stores/sessionStore';
import wsService from '../../services/websocket';
import { FeatureToggle } from './FeatureToggle';
import { ConfigFileEditor } from './ConfigFileEditor';
import { CustomThemeColors } from '../../types';
import api from '../../services/api';
import { toast } from '../../stores/toastStore';

const COLOR_LABELS: { key: keyof CustomThemeColors; labelKey: string }[] = [
  { key: 'bg', labelKey: 'settings.colorBg' },
  { key: 'surface', labelKey: 'settings.colorSurface' },
  { key: 'border', labelKey: 'settings.colorBorder' },
  { key: 'text', labelKey: 'settings.colorText' },
  { key: 'muted', labelKey: 'settings.colorMuted' },
  { key: 'accentBlue', labelKey: 'settings.colorAccentBlue' },
  { key: 'accentGreen', labelKey: 'settings.colorAccentGreen' },
  { key: 'accentRed', labelKey: 'settings.colorAccentRed' },
  { key: 'accentYellow', labelKey: 'settings.colorAccentYellow' },
  { key: 'accentPurple', labelKey: 'settings.colorAccentPurple' },
];

export const SettingsPanel = memo(function SettingsPanel() {
  const { t } = useTranslation();
  const {
    theme, setTheme,
    language, setLanguage,
    yolo, setYoloEnabled,
    autoSwarm, setAutoSwarmEnabled,
    workspaceGuardBypass, setWorkspaceGuardBypass,
    customTheme, customThemeEnabled,
    setCustomTheme, setCustomThemeEnabled, resetCustomTheme,
  } = useSettingsStore();
  const activeSessionId = useSessionStore((s) => s.activeSessionId);
  const [showCustomTheme, setShowCustomTheme] = useState(customThemeEnabled);
  const [uptime, setUptime] = useState(0);
  const [restarting, setRestarting] = useState(false);

  useEffect(() => {
    let active = true;
    const poll = async () => {
      try {
        const res = await api.system.status();
        if (active && res.success && res.data) {
          setUptime((res.data as { uptime: number }).uptime);
        }
      } catch { /* server down during restart */ }
    };
    poll();
    const interval = setInterval(poll, restarting ? 2000 : 10000);
    return () => { active = false; clearInterval(interval); };
  }, [restarting]);

  const handleRestart = useCallback(async () => {
    if (!window.confirm('Restart the server? All active sessions will be disconnected.')) return;
    setRestarting(true);
    try {
      const res = await api.system.restart();
      if (res.success) {
        toast.info('Server restarting...');
      } else {
        toast.error(res.error || 'Restart failed');
        setRestarting(false);
      }
    } catch {
      toast.error('Failed to send restart request');
      setRestarting(false);
    }
  }, []);

  // Reset restarting state when uptime resets (server is back)
  useEffect(() => {
    if (restarting && uptime < 10000) {
      setRestarting(false);
      toast.success('Server restarted');
    }
  }, [uptime, restarting]);

  const formatUptime = (ms: number) => {
    const s = Math.floor(ms / 1000);
    const m = Math.floor(s / 60);
    const h = Math.floor(m / 60);
    if (h > 0) return `${h}h ${m % 60}m`;
    if (m > 0) return `${m}m ${s % 60}s`;
    return `${s}s`;
  };

  return (
    <div className="flex-1 overflow-y-auto p-4">
      <h2 className="text-lg font-semibold mb-4 flex items-center gap-2">
        <Settings size={18} className="text-accent-blue" />
        {t('settings.title')}
      </h2>

      <div className="space-y-6 max-w-2xl">
        {/* Language */}
        <div className="bg-dark-surface border border-dark-border rounded-lg p-4">
          <h3 className="font-medium mb-3 flex items-center gap-2">
            <span>🌐</span> {t('settings.language')}
          </h3>
          <div className="flex gap-2">
            {(['zh-CN', 'en'] as const).map(lang => (
              <button
                key={lang}
                onClick={() => setLanguage(lang)}
                className={`px-4 py-2 rounded-lg transition-all ${
                  language === lang
                    ? 'bg-accent-blue text-white'
                    : 'bg-dark-hover text-dark-text hover:bg-dark-border'
                }`}
              >
                {lang === 'zh-CN' ? t('settings.languageZh') : t('settings.languageEn')}
              </button>
            ))}
          </div>
        </div>

        {/* Theme */}
        <div className="bg-dark-surface border border-dark-border rounded-lg p-4">
          <h3 className="font-medium mb-3 flex items-center gap-2">
            <span>🎨</span> {t('settings.theme')}
          </h3>
          <div className="flex gap-2">
            {(['dark', 'light', 'auto'] as const).map(mode => (
              <button
                key={mode}
                onClick={() => setTheme(mode)}
                className={`px-4 py-2 rounded-lg transition-all ${
                  theme === mode
                    ? 'bg-accent-blue text-white'
                    : 'bg-dark-hover text-dark-text hover:bg-dark-border'
                }`}
              >
                {mode === 'dark' ? t('settings.themeDark') : mode === 'light' ? t('settings.themeLight') : t('settings.themeAuto')}
              </button>
            ))}
          </div>

          {/* Custom Theme Toggle */}
          <div className="mt-4 pt-4 border-t border-dark-border">
            <button
              onClick={() => {
                const next = !showCustomTheme;
                setShowCustomTheme(next);
                setCustomThemeEnabled(next);
              }}
              className="flex items-center gap-2 text-sm text-dark-muted hover:text-dark-text transition-colors"
            >
              <Palette size={14} />
              <span>{t('settings.customColors')}</span>
              <span className={`w-8 h-4 rounded-full transition-colors ${showCustomTheme ? 'bg-accent-blue' : 'bg-dark-border'}`}>
                <span className={`block w-3 h-3 rounded-full bg-white transition-transform mt-0.5 ${showCustomTheme ? 'ml-4' : 'ml-0.5'}`} />
              </span>
            </button>

            {showCustomTheme && (
              <div className="mt-3 space-y-2">
                <div className="grid grid-cols-2 gap-2">
                  {COLOR_LABELS.map(({ key, labelKey }) => (
                    <div key={key} className="flex items-center gap-2">
                      <input
                        type="color"
                        value={customTheme[key]}
                        onChange={(e) => setCustomTheme({ [key]: e.target.value })}
                        className="w-7 h-7 rounded border border-dark-border cursor-pointer bg-transparent p-0"
                      />
                      <span className="text-xs text-dark-muted flex-1">{t(labelKey)}</span>
                      <input
                        type="text"
                        value={customTheme[key]}
                        onChange={(e) => {
                          const v = e.target.value;
                          if (/^#[0-9a-fA-F]{0,6}$/.test(v)) setCustomTheme({ [key]: v });
                        }}
                        className="w-20 bg-dark-bg border border-dark-border rounded px-1.5 py-0.5 text-xs text-dark-text font-mono"
                        maxLength={7}
                      />
                    </div>
                  ))}
                </div>
                <button
                  onClick={resetCustomTheme}
                  className="text-xs text-dark-muted hover:text-accent-red transition-colors"
                >
                  {t('settings.resetColors')}
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Advanced Features */}
        <div className="bg-dark-surface border border-dark-border rounded-lg p-4">
          <h3 className="font-medium mb-3 flex items-center gap-2">
            <span>🚀</span> {t('settings.advancedFeatures')}
          </h3>
          <div className="space-y-3">
            <FeatureToggle
              title={t('settings.yoloMode')}
              subtitle={t('settings.yoloDesc')}
              enabled={yolo.enabled}
              onChange={(enabled) => {
                setYoloEnabled(enabled);
                wsService.send({
                  type: 'toggle_yolo',
                  sessionId: activeSessionId || '',
                  data: enabled ? 'true' : 'false',
                });
              }}
            />
            <FeatureToggle
              title={t('settings.autoSwarm')}
              subtitle={t('settings.autoSwarmDesc')}
              enabled={autoSwarm.enabled}
              onChange={(enabled) => {
                setAutoSwarmEnabled(enabled);
                wsService.send({
                  type: 'toggle_auto_swarm',
                  sessionId: activeSessionId || '',
                  data: enabled ? 'true' : 'false',
                });
              }}
            />
            <FeatureToggle
              title={t('settings.workspaceGuard')}
              subtitle={t('settings.workspaceGuardDesc')}
              enabled={workspaceGuardBypass}
              onChange={(enabled) => {
                setWorkspaceGuardBypass(enabled);
                wsService.send({
                  type: 'toggle_workspace_guard',
                  sessionId: activeSessionId || '',
                  data: enabled ? 'true' : 'false',
                });
              }}
            />
          </div>
        </div>

        {/* Config Files */}
        <div className="bg-dark-surface border border-dark-border rounded-lg p-4">
          <h3 className="font-medium mb-3 flex items-center gap-2">
            <span>📁</span> Config Files (~/.jwcode/)
          </h3>
          <ConfigFileEditor />
        </div>

        {/* System */}
        <div className="bg-dark-surface border border-dark-border rounded-lg p-4">
          <h3 className="font-medium mb-3 flex items-center gap-2">
            <span>⚙️</span> System
          </h3>
          <div className="space-y-3">
            <div className="flex items-center justify-between text-sm">
              <span className="text-dark-muted">Server Uptime</span>
              <span className="text-dark-text font-mono">{formatUptime(uptime)}</span>
            </div>
            <button
              onClick={handleRestart}
              disabled={restarting}
              className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm transition-all ${
                restarting
                  ? 'bg-dark-hover text-dark-muted cursor-wait'
                  : 'bg-accent-red/10 text-accent-red hover:bg-accent-red/20 border border-accent-red/30'
              }`}
            >
              <RefreshCw size={14} className={restarting ? 'animate-spin' : ''} />
              {restarting ? 'Restarting...' : 'Restart Server'}
            </button>
          </div>
        </div>

        {/* About */}
        <div className="bg-dark-surface border border-dark-border rounded-lg p-4">
          <h3 className="font-medium mb-3 flex items-center gap-2">
            <span>ℹ️</span> {t('settings.about')}
          </h3>
          <div className="text-sm text-dark-muted space-y-2">
            <p><span className="text-dark-text">JwCode Web</span> v1.0.0</p>
            <p>{t('settings.aboutDesc')}</p>
            <p className="text-xs">Powered by React + TypeScript + TailwindCSS</p>
          </div>
        </div>
      </div>
    </div>
  );
});

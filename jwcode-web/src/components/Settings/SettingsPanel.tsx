import { memo, useState } from 'react';
import { Settings, Palette } from 'lucide-react';
import { useSettingsStore } from '../../stores/settingsStore';
import { useSessionStore } from '../../stores/sessionStore';
import wsService from '../../services/websocket';
import { FeatureToggle } from './FeatureToggle';
import { CustomThemeColors } from '../../types';

const COLOR_LABELS: { key: keyof CustomThemeColors; label: string }[] = [
  { key: 'bg', label: '背景色' },
  { key: 'surface', label: '面板色' },
  { key: 'border', label: '边框色' },
  { key: 'text', label: '文字色' },
  { key: 'muted', label: '次要文字' },
  { key: 'accentBlue', label: '强调蓝' },
  { key: 'accentGreen', label: '强调绿' },
  { key: 'accentRed', label: '强调红' },
  { key: 'accentYellow', label: '强调黄' },
  { key: 'accentPurple', label: '强调紫' },
];

export const SettingsPanel = memo(function SettingsPanel() {
  const {
    theme, setTheme,
    thinking, setThinkingEnabled,
    yolo, setYoloEnabled,
    autoSwarm, setAutoSwarmEnabled,
    autoAI, setAutoAIEnabled,
    compression, setCompressionEnabled,
    workspaceGuardBypass, setWorkspaceGuardBypass,
    customTheme, customThemeEnabled,
    setCustomTheme, setCustomThemeEnabled, resetCustomTheme,
  } = useSettingsStore();
  const activeSessionId = useSessionStore((s) => s.activeSessionId);
  const [showCustomTheme, setShowCustomTheme] = useState(customThemeEnabled);

  return (
    <div className="flex-1 overflow-y-auto p-4">
      <h2 className="text-lg font-semibold mb-4 flex items-center gap-2">
        <Settings size={18} className="text-accent-blue" />
        设置
      </h2>

      <div className="space-y-6 max-w-2xl">
        {/* Theme */}
        <div className="bg-dark-surface border border-dark-border rounded-lg p-4">
          <h3 className="font-medium mb-3 flex items-center gap-2">
            <span>🎨</span> 主题
          </h3>
          <div className="flex gap-2">
            {(['dark', 'light', 'auto'] as const).map(t => (
              <button
                key={t}
                onClick={() => setTheme(t)}
                className={`px-4 py-2 rounded-lg transition-all ${
                  theme === t
                    ? 'bg-accent-blue text-white'
                    : 'bg-dark-hover text-dark-text hover:bg-dark-border'
                }`}
              >
                {t === 'dark' ? '🌙 深色' : t === 'light' ? '☀️ 浅色' : '🔄 自动'}
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
              <span>自定义配色</span>
              <span className={`w-8 h-4 rounded-full transition-colors ${showCustomTheme ? 'bg-accent-blue' : 'bg-dark-border'}`}>
                <span className={`block w-3 h-3 rounded-full bg-white transition-transform mt-0.5 ${showCustomTheme ? 'ml-4' : 'ml-0.5'}`} />
              </span>
            </button>

            {showCustomTheme && (
              <div className="mt-3 space-y-2">
                <div className="grid grid-cols-2 gap-2">
                  {COLOR_LABELS.map(({ key, label }) => (
                    <div key={key} className="flex items-center gap-2">
                      <input
                        type="color"
                        value={customTheme[key]}
                        onChange={(e) => setCustomTheme({ [key]: e.target.value })}
                        className="w-7 h-7 rounded border border-dark-border cursor-pointer bg-transparent p-0"
                      />
                      <span className="text-xs text-dark-muted flex-1">{label}</span>
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
                  重置为默认配色
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Advanced Features */}
        <div className="bg-dark-surface border border-dark-border rounded-lg p-4">
          <h3 className="font-medium mb-3 flex items-center gap-2">
            <span>🚀</span> 高级功能
          </h3>
          <div className="space-y-3">
            <FeatureToggle
              title="🧠 Thinking Mode"
              subtitle="深度推理模式 - 让 AI 进行更详细的思考"
              enabled={thinking.enabled}
              onChange={setThinkingEnabled}
            />
            <FeatureToggle
              title="⚡ YOLO Mode"
              subtitle="全自动模式 - 无需确认直接执行"
              enabled={yolo.enabled}
              onChange={setYoloEnabled}
            />
            <FeatureToggle
              title="🐝 Auto Swarm"
              subtitle="自动智能体集群 - 多 Agent 协同工作"
              enabled={autoSwarm.enabled}
              onChange={setAutoSwarmEnabled}
            />
            <FeatureToggle
              title="🤖 Auto AI"
              subtitle="自动 AI 规划 - 智能任务分解与执行"
              enabled={autoAI.enabled}
              onChange={setAutoAIEnabled}
            />
            <FeatureToggle
              title="📦 Context Compression"
              subtitle="上下文压缩 - 自动管理对话长度"
              enabled={compression.enabled}
              onChange={setCompressionEnabled}
            />
            <FeatureToggle
              title="🔓 工作区守卫绕过"
              subtitle="允许读取工作目录外的文件（临时取消路径限制）"
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

        {/* About */}
        <div className="bg-dark-surface border border-dark-border rounded-lg p-4">
          <h3 className="font-medium mb-3 flex items-center gap-2">
            <span>ℹ️</span> 关于
          </h3>
          <div className="text-sm text-dark-muted space-y-2">
            <p><span className="text-dark-text">JwCode Web</span> v1.0.0</p>
            <p>基于 Web 的 AI 编码助手界面</p>
            <p className="text-xs">Powered by React + TypeScript + TailwindCSS</p>
          </div>
        </div>
      </div>
    </div>
  );
});

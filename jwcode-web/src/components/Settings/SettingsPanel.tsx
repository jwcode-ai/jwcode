import { memo } from 'react';
import { Settings } from 'lucide-react';
import { useSettingsStore } from '../../stores/settingsStore';
import { FeatureToggle } from './FeatureToggle';

export const SettingsPanel = memo(function SettingsPanel() {
  const {
    theme, setTheme,
    thinking, setThinkingEnabled,
    yolo, setYoloEnabled,
    autoSwarm, setAutoSwarmEnabled,
    autoAI, setAutoAIEnabled,
    compression, setCompressionEnabled
  } = useSettingsStore();

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

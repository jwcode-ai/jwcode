import { useEffect, useState, useCallback } from 'react';
import { Brain, RefreshCw, Wifi, WifiOff, Activity, AlertTriangle, Plus, X, Key, CheckCircle, Eye, EyeOff } from 'lucide-react';
import { api, type Model } from '../../services/api';

interface ProviderInfo {
  baseUrl?: string;
  hasApiKey: boolean;
  modelCount: number;
  apiType?: string;
}

interface ProviderSummary {
  configured: boolean;
  defaultProvider: string | null;
  providers: Record<string, ProviderInfo>;
}

interface PresetProvider {
  key: string;
  name: string;
  baseUrl: string;
  apiType: string;
  defaultModel: string;
}

const PROVIDER_PRESETS: PresetProvider[] = [
  { key: 'openai', name: 'OpenAI', baseUrl: 'https://api.openai.com/v1', apiType: 'openai-completions', defaultModel: 'gpt-4o' },
  { key: 'anthropic', name: 'Anthropic', baseUrl: 'https://api.anthropic.com/v1', apiType: 'anthropic-messages', defaultModel: 'claude-sonnet-4-6' },
  { key: 'deepseek', name: 'DeepSeek', baseUrl: 'https://api.deepseek.com/v1', apiType: 'openai-completions', defaultModel: 'deepseek-chat' },
  { key: 'moonshot', name: 'Moonshot / Kimi', baseUrl: 'https://api.moonshot.cn/v1', apiType: 'openai-completions', defaultModel: 'moonshot-v1-8k' },
  { key: 'qwen', name: '通义千问 (Qwen)', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', apiType: 'openai-completions', defaultModel: 'qwen-plus' },
  { key: 'zhipu', name: '智谱 (GLM)', baseUrl: 'https://open.bigmodel.cn/api/paas/v4', apiType: 'openai-completions', defaultModel: 'glm-4-plus' },
  { key: 'baichuan', name: '百川 (Baichuan)', baseUrl: 'https://api.baichuan-ai.com/v1', apiType: 'openai-completions', defaultModel: 'Baichuan4' },
  { key: 'minimax', name: 'MiniMax / 海螺', baseUrl: 'https://api.minimax.chat/v1', apiType: 'openai-completions', defaultModel: 'abab6.5s-chat' },
  { key: 'doubao', name: '豆包 (Doubao)', baseUrl: 'https://ark.cn-beijing.volces.com/api/v3', apiType: 'openai-completions', defaultModel: 'doubao-pro-32k' },
  { key: 'hunyuan', name: '腾讯混元 (Hunyuan)', baseUrl: 'https://api.hunyuan.cloud.tencent.com/v1', apiType: 'openai-completions', defaultModel: 'hunyuan-pro' },
  { key: 'spark', name: '讯飞星火 (Spark)', baseUrl: 'https://spark-api-open.xf-yun.com/v1', apiType: 'openai-completions', defaultModel: 'generalv3.5' },
  { key: 'custom', name: 'Custom (自定义)', baseUrl: '', apiType: 'openai-completions', defaultModel: '' },
];

interface LocalModelStatus {
  overallStatus: 'healthy' | 'degraded' | 'unhealthy' | 'error';
  healthRate: number;
  healthyInstances: number;
  totalInstances: number;
  totalRequests: number;
}

export function ModelsView() {
  const [models, setModels] = useState<Model[]>([]);
  const [status, setStatus] = useState<LocalModelStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState<string | null>(null);
  const [showAddForm, setShowAddForm] = useState(false);
  const [addForm, setAddForm] = useState({
    provider: '',
    modelId: '',
    modelName: '',
    baseUrl: '',
    apiKeys: '',
    temperature: 1.0,
    maxTokens: 32768,
    contextWindow: 128000,
  });
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  // Provider config state
  const [providerSummary, setProviderSummary] = useState<ProviderSummary | null>(null);
  const [activePreset, setActivePreset] = useState<string | null>(null);
  const [presetForm, setPresetForm] = useState({ apiKey: '', modelId: '', baseUrl: '' });
  const [presetSaving, setPresetSaving] = useState(false);
  const [presetError, setPresetError] = useState<string | null>(null);
  const [showKey, setShowKey] = useState(false);

  const loadProviderSummary = useCallback(async () => {
    try {
      const res = await api.config.provider.get();
      if (res.success && res.data) {
        setProviderSummary(res.data as ProviderSummary);
      }
    } catch { /* ignore */ }
  }, []);

  const loadData = async () => {
    setLoading(true);
    setError(null);

    try {
      const [modelsRes, systemRes] = await Promise.all([
        api.models.list(),
        api.system.status(),
        loadProviderSummary(),
      ]);

      if (modelsRes.success && modelsRes.data) {
        setModels(modelsRes.data.models || []);
      }

      if (systemRes.success && systemRes.data) {
        const modelStats = systemRes.data.models || { total: 0, online: 0, offline: 0 };
        setStatus({
          overallStatus: modelStats.offline === 0 ? 'healthy' : modelStats.online > 0 ? 'degraded' : 'unhealthy',
          healthRate: modelStats.total > 0 ? modelStats.online / modelStats.total : 0,
          healthyInstances: modelStats.online,
          totalInstances: modelStats.total,
          totalRequests: 0,
        });
      }
    } catch (err) {
      setError('加载模型失败');
    }

    setLoading(false);
  };

  useEffect(() => {
    loadData();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handleToggle = async (_modelId: string, _currentStatus: string) => {
    await loadData();
  };

  const handleRefresh = async (modelId: string) => {
    setRefreshing(modelId);
    await api.models.test(modelId);
    await loadData();
    setRefreshing(null);
  };

  const handlePresetClick = (preset: PresetProvider) => {
    const existing = providerSummary?.providers?.[preset.key];
    if (existing?.hasApiKey) {
      // Already configured — just toggle form
      setActivePreset(activePreset === preset.key ? null : preset.key);
      return;
    }
    setActivePreset(preset.key);
    setPresetForm({
      apiKey: '',
      modelId: preset.defaultModel,
      baseUrl: preset.baseUrl,
    });
    setPresetError(null);
    setShowKey(false);
  };

  const handlePresetSave = async (preset: PresetProvider) => {
    if (!presetForm.apiKey.trim()) {
      setPresetError('请输入 API Key');
      return;
    }
    setPresetSaving(true);
    setPresetError(null);
    try {
      const body: Record<string, unknown> = {
        provider: preset.key,
        baseUrl: presetForm.baseUrl || preset.baseUrl,
        apiType: preset.apiType,
        apiKey: presetForm.apiKey.trim(),
        models: [{
          id: presetForm.modelId || preset.defaultModel,
          name: presetForm.modelId || preset.defaultModel,
          enabled: true,
          priority: 10,
        }],
      };
      // Set as default if this is the first provider
      if (!providerSummary?.configured) {
        body.setDefault = true;
      }
      const res = await api.config.provider.save(body);
      if (res.success) {
        setActivePreset(null);
        await loadProviderSummary();
        await loadData();
      } else {
        setPresetError(res.error || '保存失败');
      }
    } catch (err) {
      setPresetError(err instanceof Error ? err.message : '未知错误');
    } finally {
      setPresetSaving(false);
    }
  };

  const handlePresetDelete = async (providerKey: string) => {
    if (!confirm(`确定要移除 "${providerKey}" 的配置吗？这将删除其 API Key 和模型。`)) return;
    try {
      await api.config.provider.delete(providerKey);
      await loadProviderSummary();
      await loadData();
    } catch { /* ignore */ }
  };

  const getHealthColor = (healthStatus: string) => {
    switch (healthStatus) {
      case 'online': return 'text-accent-green';
      case 'offline': return 'text-dark-muted';
      case 'error': return 'text-accent-red';
      default: return 'text-dark-muted';
    }
  };

  const getHealthIcon = (healthStatus: string) => {
    switch (healthStatus) {
      case 'online': return <Wifi size={14} />;
      case 'offline': return <WifiOff size={14} />;
      case 'error': return <AlertTriangle size={14} />;
      default: return <Activity size={14} />;
    }
  };

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <div className="text-center">
          <RefreshCw size={32} className="animate-spin mx-auto mb-2 text-accent-blue" />
          <p className="text-dark-muted">加载中...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <div className="text-center">
          <AlertTriangle size={32} className="mx-auto mb-2 text-accent-red" />
          <p className="text-accent-red mb-4">{error}</p>
          <button onClick={loadData} className="px-4 py-2 bg-accent-blue text-white rounded-lg">
            重试
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 flex flex-col overflow-hidden p-4">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold flex items-center gap-2">
          <Brain size={20} />
          模型配置
        </h2>
        <div className="flex items-center gap-2">
          <button
            onClick={loadData}
            className="flex items-center gap-2 px-3 py-1.5 bg-dark-surface border border-dark-border rounded hover:bg-dark-hover transition-colors"
          >
            <RefreshCw size={14} />
            刷新
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto space-y-6">
        {/* Provider Configuration */}
        <div>
          <h3 className="text-sm font-medium mb-3 flex items-center gap-2">
            <Key size={14} className="text-accent-yellow" />
            Provider 配置
            {providerSummary?.configured && (
              <span className="text-xs text-green-400 flex items-center gap-1">
                <CheckCircle size={12} /> 已配置
              </span>
            )}
          </h3>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {PROVIDER_PRESETS.map(preset => {
              const configured = providerSummary?.providers?.[preset.key];
              const hasKey = configured?.hasApiKey;
              const isActive = activePreset === preset.key;
              const isDefault = providerSummary?.defaultProvider === preset.key;

              return (
                <div key={preset.key}>
                  <div
                    className={`bg-dark-surface border rounded-lg p-3 transition-colors cursor-pointer ${
                      isActive ? 'border-accent-blue' : hasKey ? 'border-accent-green/50' : 'border-dark-border hover:border-dark-hover'
                    }`}
                    onClick={() => handlePresetClick(preset)}
                  >
                    <div className="flex items-center justify-between mb-1">
                      <span className="font-medium text-sm text-dark-text">
                        {preset.name}
                        {isDefault && <span className="ml-1.5 text-[10px] px-1 py-0.5 bg-accent-blue/20 text-accent-blue rounded">默认</span>}
                      </span>
                      {hasKey ? (
                        <CheckCircle size={14} className="text-accent-green shrink-0" />
                      ) : (
                        <span className="text-[10px] text-dark-muted">未配置</span>
                      )}
                    </div>
                    {preset.baseUrl && (
                      <div className="text-[11px] text-dark-muted truncate mb-1">{preset.baseUrl}</div>
                    )}
                    {hasKey && configured && (
                      <div className="text-[11px] text-dark-muted">
                        {configured.modelCount} 个模型 · {configured.apiType === 'anthropic-messages' ? 'Anthropic' : 'OpenAI Compatible'}
                      </div>
                    )}
                    {!hasKey && (
                      <div className="text-[11px] text-accent-blue mt-1">点击配置 →</div>
                    )}
                  </div>

                  {/* Inline config form */}
                  {isActive && (
                    <div className="mt-2 bg-dark-surface border border-accent-blue/30 rounded-lg p-3 space-y-2">
                      {hasKey && (
                        <div className="text-xs text-dark-muted mb-1">
                          已配置 API Key，输入新 Key 将覆盖旧配置。
                        </div>
                      )}
                      <div>
                        <label className="block text-[11px] text-dark-muted mb-0.5">API Key *</label>
                        <div className="flex gap-1">
                          <input
                            type={showKey ? 'text' : 'password'}
                            value={presetForm.apiKey}
                            onChange={e => setPresetForm(f => ({ ...f, apiKey: e.target.value }))}
                            placeholder={hasKey ? '输入新 Key 以覆盖...' : 'sk-...'}
                            className="flex-1 bg-dark-bg border border-dark-border rounded px-2 py-1.5 text-sm focus:border-accent-blue outline-none"
                            onClick={e => e.stopPropagation()}
                          />
                          <button
                            onClick={e => { e.stopPropagation(); setShowKey(k => !k); }}
                            className="px-2 rounded border border-dark-border hover:bg-dark-hover text-dark-muted"
                          >
                            {showKey ? <EyeOff size={14} /> : <Eye size={14} />}
                          </button>
                        </div>
                      </div>
                      <div>
                        <label className="block text-[11px] text-dark-muted mb-0.5">Model ID</label>
                        <input
                          type="text"
                          value={presetForm.modelId}
                          onChange={e => setPresetForm(f => ({ ...f, modelId: e.target.value }))}
                          placeholder={preset.defaultModel}
                          className="w-full bg-dark-bg border border-dark-border rounded px-2 py-1.5 text-sm focus:border-accent-blue outline-none"
                          onClick={e => e.stopPropagation()}
                        />
                      </div>
                      {preset.key === 'custom' && (
                        <div>
                          <label className="block text-[11px] text-dark-muted mb-0.5">Base URL</label>
                          <input
                            type="text"
                            value={presetForm.baseUrl}
                            onChange={e => setPresetForm(f => ({ ...f, baseUrl: e.target.value }))}
                            placeholder="https://api.example.com/v1"
                            className="w-full bg-dark-bg border border-dark-border rounded px-2 py-1.5 text-sm focus:border-accent-blue outline-none"
                            onClick={e => e.stopPropagation()}
                          />
                        </div>
                      )}
                      {presetError && (
                        <div className="text-xs text-accent-red">{presetError}</div>
                      )}
                      <div className="flex gap-2 pt-1">
                        <button
                          onClick={e => { e.stopPropagation(); handlePresetSave(preset); }}
                          disabled={presetSaving || !presetForm.apiKey.trim()}
                          className="flex-1 py-1.5 bg-accent-blue text-white rounded text-sm hover:opacity-90 disabled:opacity-50"
                        >
                          {presetSaving ? '保存中...' : '保存'}
                        </button>
                        {hasKey && (
                          <button
                            onClick={e => { e.stopPropagation(); handlePresetDelete(preset.key); }}
                            className="px-3 py-1.5 border border-accent-red/30 text-accent-red rounded text-sm hover:bg-accent-red/10"
                          >
                            删除
                          </button>
                        )}
                        <button
                          onClick={e => { e.stopPropagation(); setActivePreset(null); }}
                          className="px-3 py-1.5 border border-dark-border rounded text-sm text-dark-muted hover:bg-dark-hover"
                        >
                          取消
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        {/* Status Overview */}
        {status && (
          <div className="grid grid-cols-4 gap-4">
            <StatusCard
              title="总体状态"
              value={status.overallStatus === 'healthy' ? '健康' : status.overallStatus === 'degraded' ? '降级' : '异常'}
              color={status.overallStatus === 'healthy' ? 'green' : status.overallStatus === 'degraded' ? 'yellow' : 'red'}
            />
            <StatusCard
              title="健康率"
              value={`${(status.healthRate * 100).toFixed(0)}%`}
              color={status.healthRate > 0.8 ? 'green' : status.healthRate > 0.5 ? 'yellow' : 'red'}
            />
            <StatusCard
              title="在线实例"
              value={`${status.healthyInstances}/${status.totalInstances}`}
              color={status.healthyInstances > 0 ? 'green' : 'red'}
            />
            <StatusCard
              title="总请求数"
              value={status.totalRequests.toLocaleString()}
              color="blue"
            />
          </div>
        )}

        {/* Models List */}
        <div>
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm font-medium">已配置模型</h3>
            <button
              onClick={() => { setShowAddForm(true); setSaveError(null); }}
              className="flex items-center gap-1.5 px-2.5 py-1 text-xs bg-accent-blue text-white rounded hover:opacity-90 transition-opacity"
            >
              <Plus size={12} />
              添加模型
            </button>
          </div>

          <div className="space-y-2">
            {models.map(model => (
              <div
                key={model.id}
                className={`bg-dark-surface border rounded-lg p-3 transition-colors ${
                  model.status === 'online' ? 'border-accent-green' : 'border-dark-border opacity-60'
                }`}
              >
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <span className={getHealthColor(model.status)}>
                        {getHealthIcon(model.status)}
                      </span>
                      <span className="font-medium text-sm">{model.name}</span>
                      <span className="text-[10px] text-dark-muted px-1.5 py-0.5 bg-dark-bg rounded">
                        {model.provider}
                      </span>
                    </div>

                    <div className="grid grid-cols-5 gap-3 text-xs">
                      <div>
                        <div className="text-dark-muted">负载</div>
                        <div>{model.load}/{model.maxLoad}</div>
                      </div>
                      <div>
                        <div className="text-dark-muted">Token</div>
                        <div>{model.tokens}/{model.maxTokens}</div>
                      </div>
                      <div>
                        <div className="text-dark-muted">输入价格</div>
                        <div>${model.price.input}/1K</div>
                      </div>
                      <div>
                        <div className="text-dark-muted">输出价格</div>
                        <div>${model.price.output}/1K</div>
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 ml-3">
                    <button
                      onClick={() => handleRefresh(model.id)}
                      disabled={refreshing === model.id}
                      className="p-1.5 rounded hover:bg-dark-hover disabled:opacity-50"
                      title="刷新"
                    >
                      <RefreshCw size={13} className={refreshing === model.id ? 'animate-spin' : ''} />
                    </button>
                    <button
                      onClick={() => handleToggle(model.id, model.status)}
                      className={`px-2 py-0.5 rounded text-xs transition-colors ${
                        model.status === 'online'
                          ? 'bg-accent-red/20 text-accent-red hover:bg-accent-red/30'
                          : 'bg-accent-green/20 text-accent-green hover:bg-accent-green/30'
                      }`}
                    >
                      {model.status === 'online' ? '禁用' : '启用'}
                    </button>
                  </div>
                </div>
              </div>
            ))}

            {models.length === 0 && (
              <div className="text-center text-dark-muted py-6">
                <Brain size={36} className="mx-auto mb-2 opacity-40" />
                <p className="text-sm">暂无模型 — 请先在上方配置 Provider</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Add Model Modal (for advanced — add extra models to existing provider) */}
      {showAddForm && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-dark-surface border border-dark-border rounded-xl p-6 w-full max-w-lg mx-4">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold flex items-center gap-2">
                <Plus size={18} />
                添加模型
              </h3>
              <button
                onClick={() => setShowAddForm(false)}
                className="p-1 rounded hover:bg-dark-hover"
              >
                <X size={18} />
              </button>
            </div>

            <div className="space-y-3">
              <div>
                <label className="block text-sm text-dark-muted mb-1">提供商名称 *</label>
                <input
                  type="text"
                  value={addForm.provider}
                  onChange={e => setAddForm(f => ({ ...f, provider: e.target.value }))}
                  placeholder="例如: openai, deepseek, moonshot"
                  className="w-full px-3 py-2 bg-dark-bg border border-dark-border rounded text-sm focus:outline-none focus:border-accent-blue"
                />
              </div>
              <div>
                <label className="block text-sm text-dark-muted mb-1">模型 ID *</label>
                <input
                  type="text"
                  value={addForm.modelId}
                  onChange={e => setAddForm(f => ({ ...f, modelId: e.target.value }))}
                  placeholder="例如: gpt-4, deepseek-chat"
                  className="w-full px-3 py-2 bg-dark-bg border border-dark-border rounded text-sm focus:outline-none focus:border-accent-blue"
                />
              </div>
              <div>
                <label className="block text-sm text-dark-muted mb-1">模型名称</label>
                <input
                  type="text"
                  value={addForm.modelName}
                  onChange={e => setAddForm(f => ({ ...f, modelName: e.target.value }))}
                  placeholder="留空则使用模型 ID"
                  className="w-full px-3 py-2 bg-dark-bg border border-dark-border rounded text-sm focus:outline-none focus:border-accent-blue"
                />
              </div>
              <div>
                <label className="block text-sm text-dark-muted mb-1">API 地址</label>
                <input
                  type="text"
                  value={addForm.baseUrl}
                  onChange={e => setAddForm(f => ({ ...f, baseUrl: e.target.value }))}
                  placeholder="例如: https://api.openai.com/v1"
                  className="w-full px-3 py-2 bg-dark-bg border border-dark-border rounded text-sm focus:outline-none focus:border-accent-blue"
                />
              </div>
              <div>
                <label className="block text-sm text-dark-muted mb-1">API Key（多个用逗号分隔）</label>
                <input
                  type="text"
                  value={addForm.apiKeys}
                  onChange={e => setAddForm(f => ({ ...f, apiKeys: e.target.value }))}
                  placeholder="sk-xxx, sk-yyy"
                  className="w-full px-3 py-2 bg-dark-bg border border-dark-border rounded text-sm focus:outline-none focus:border-accent-blue"
                />
              </div>
              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-sm text-dark-muted mb-1">温度</label>
                  <input
                    type="number"
                    step="0.1"
                    min="0"
                    max="2"
                    value={addForm.temperature}
                    onChange={e => setAddForm(f => ({ ...f, temperature: parseFloat(e.target.value) || 1.0 }))}
                    className="w-full px-3 py-2 bg-dark-bg border border-dark-border rounded text-sm focus:outline-none focus:border-accent-blue"
                  />
                </div>
                <div>
                  <label className="block text-sm text-dark-muted mb-1">最大 Token</label>
                  <input
                    type="number"
                    value={addForm.maxTokens}
                    onChange={e => setAddForm(f => ({ ...f, maxTokens: parseInt(e.target.value) || 32768 }))}
                    className="w-full px-3 py-2 bg-dark-bg border border-dark-border rounded text-sm focus:outline-none focus:border-accent-blue"
                  />
                </div>
                <div>
                  <label className="block text-sm text-dark-muted mb-1">上下文窗口</label>
                  <input
                    type="number"
                    value={addForm.contextWindow}
                    onChange={e => setAddForm(f => ({ ...f, contextWindow: parseInt(e.target.value) || 128000 }))}
                    className="w-full px-3 py-2 bg-dark-bg border border-dark-border rounded text-sm focus:outline-none focus:border-accent-blue"
                  />
                </div>
              </div>
            </div>

            {saveError && (
              <div className="mt-3 text-sm text-accent-red bg-accent-red/10 p-2 rounded">
                {saveError}
              </div>
            )}

            <div className="flex justify-end gap-2 mt-4">
              <button
                onClick={() => setShowAddForm(false)}
                className="px-4 py-2 rounded border border-dark-border hover:bg-dark-hover transition-colors text-sm"
              >
                取消
              </button>
              <button
                onClick={async () => {
                  if (!addForm.provider.trim() || !addForm.modelId.trim()) {
                    setSaveError('提供商名称和模型 ID 不能为空');
                    return;
                  }
                  setSaving(true);
                  setSaveError(null);
                  try {
                    const modelData: Record<string, unknown> = {
                      id: addForm.modelId.trim(),
                      name: addForm.modelName.trim() || addForm.modelId.trim(),
                      temperature: addForm.temperature,
                      maxTokens: addForm.maxTokens,
                      contextWindow: addForm.contextWindow,
                      enabled: true,
                    };
                    if (addForm.baseUrl.trim()) {
                      modelData.baseUrl = addForm.baseUrl.trim();
                    }
                    if (addForm.apiKeys.trim()) {
                      modelData.apiKeys = addForm.apiKeys.split(',').map(k => k.trim()).filter(Boolean);
                    }
                    const res = await api.models.create({
                      provider: addForm.provider.trim(),
                      model: modelData,
                    });
                    if (res.success) {
                      setShowAddForm(false);
                      setAddForm({
                        provider: '', modelId: '', modelName: '', baseUrl: '', apiKeys: '',
                        temperature: 1.0, maxTokens: 32768, contextWindow: 128000,
                      });
                      await loadData();
                    } else {
                      setSaveError(res.error || '保存失败');
                    }
                  } catch (err) {
                    setSaveError(err instanceof Error ? err.message : '未知错误');
                  } finally {
                    setSaving(false);
                  }
                }}
                disabled={saving}
                className="px-4 py-2 bg-accent-blue text-white rounded hover:bg-accent-blue/80 transition-colors text-sm disabled:opacity-50"
              >
                {saving ? '保存中...' : '保存'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

interface StatusCardProps {
  title: string;
  value: string;
  color: 'green' | 'yellow' | 'red' | 'blue';
}

function StatusCard({ title, value, color }: StatusCardProps) {
  const colorClasses = {
    green: 'bg-accent-green/10 text-accent-green',
    yellow: 'bg-accent-yellow/10 text-accent-yellow',
    red: 'bg-accent-red/10 text-accent-red',
    blue: 'bg-accent-blue/10 text-accent-blue',
  };

  return (
    <div className={`${colorClasses[color]} rounded-lg p-3`}>
      <div className="text-xs opacity-70">{title}</div>
      <div className="text-xl font-semibold">{value}</div>
    </div>
  );
}

export default ModelsView;

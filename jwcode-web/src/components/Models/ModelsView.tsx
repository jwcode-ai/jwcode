import { useEffect, useState, useCallback } from 'react';
import { Brain, RefreshCw, Wifi, WifiOff, AlertTriangle, Plus, X, Key, CheckCircle, EyeOff } from 'lucide-react';
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
  apiTypeBaseUrls?: Record<string, string>;
}

const PROVIDER_PRESETS: PresetProvider[] = [
  { key: 'openai', name: 'OpenAI', baseUrl: 'https://api.openai.com/v1', apiType: 'openai-completions', defaultModel: 'gpt-4o' },
  { key: 'anthropic', name: 'Anthropic', baseUrl: 'https://api.anthropic.com/v1', apiType: 'anthropic-messages', defaultModel: 'claude-sonnet-4-6' },
  { key: 'deepseek', name: 'DeepSeek', baseUrl: 'https://api.deepseek.com/v1', apiType: 'openai-completions', defaultModel: 'deepseek-chat', apiTypeBaseUrls: { 'openai-completions': 'https://api.deepseek.com/v1', 'anthropic-messages': 'https://api.deepseek.com/anthropic' } },
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
  const [presetForm, setPresetForm] = useState({ apiKey: '', modelId: '', baseUrl: '', apiType: 'openai-completions' });
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
    setActivePreset(preset.key);
    setPresetForm({
      apiKey: '',
      modelId: preset.defaultModel,
      baseUrl: existing?.baseUrl || preset.baseUrl,
      apiType: existing?.apiType || preset.apiType,
    });
    if (existing?.hasApiKey) {
      // Already configured, form is toggled with existing values
      return;
    }
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
      const existing = providerSummary?.providers?.[preset.key];
      const body: Record<string, unknown> = {
        provider: preset.key,
        baseUrl: presetForm.baseUrl || preset.baseUrl,
        apiType: presetForm.apiType || preset.apiType,
        apiKey: presetForm.apiKey.trim() || (existing?.hasApiKey ? undefined : ''),
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
    if (!confirm(`确定要移除"${providerKey}"的配置吗？这将删除其 API Key 和模型。`)) return;
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
      default: return <WifiOff size={14} />;
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-dark-muted">加载中...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-accent-red">{error}</div>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      {/* Provider Presets */}
      <div>
        <h2 className="text-base font-semibold mb-3">Provider 配置</h2>
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
          {PROVIDER_PRESETS.map(preset => {
            const configured = providerSummary?.providers?.[preset.key];
            const hasKey = configured?.hasApiKey;
            const isDefault = providerSummary?.defaultProvider === preset.key;
            const isActive = activePreset === preset.key;

            return (
              <div key={preset.key} className="relative">
                <button
                  onClick={() => handlePresetClick(preset)}
                  className={`w-full p-3 rounded-lg border text-left transition-all ${
                    isActive
                      ? 'border-accent-blue bg-accent-blue/10'
                      : hasKey
                        ? 'border-accent-green/30 bg-dark-surface hover:border-accent-green/50'
                        : 'border-dark-border bg-dark-surface hover:border-dark-hover'
                  }`}
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-sm font-medium">{preset.name}</span>
                    {hasKey && <CheckCircle size={14} className="text-accent-green" />}
                  </div>
                  <div className="text-[10px] text-dark-muted space-y-0.5">
                    <div>{preset.baseUrl || '自定义地址'}</div>
                    <div>{preset.defaultModel || '无默认模型'}</div>
                  </div>
                  {isDefault && (
                    <div className="mt-1 text-[10px] text-accent-blue">默认 Provider</div>
                  )}
                </button>

                {/* Inline config form */}
                {isActive && (
                  <div
                    className="absolute top-full left-0 right-0 mt-2 z-10 bg-dark-surface border border-dark-border rounded-lg p-3 shadow-xl"
                    onClick={e => e.stopPropagation()}
                  >
                    <div className="space-y-2">
                      {hasKey && (
                        <div className="text-[11px] text-accent-green mb-1">
                          ✓ 已配置 API Key
                        </div>
                      )}
                      <div className="relative">
                        <label className="block text-[11px] text-dark-muted mb-0.5">API Key</label>
                        <input
                          type={showKey ? 'text' : 'password'}
                          value={presetForm.apiKey}
                          onChange={e => setPresetForm(f => ({ ...f, apiKey: e.target.value }))}
                          placeholder={hasKey ? '留空则使用已保存的 Key' : '输入 API Key'}
                          className="w-full bg-dark-bg border border-dark-border rounded px-2 py-1.5 pr-7 text-sm focus:border-accent-blue outline-none"
                          onClick={e => e.stopPropagation()}
                        />
                        <button
                          onClick={e => { e.stopPropagation(); setShowKey(s => !s); }}
                          className="absolute right-1.5 top-[22px] text-dark-muted hover:text-white"
                        >
                          {showKey ? <EyeOff size={14} /> : <Key size={14} />}
                        </button>
                      </div>
                      <div>
                        <label className="block text-[11px] text-dark-muted mb-0.5">模型 ID</label>
                        <input
                          type="text"
                          value={presetForm.modelId}
                          onChange={e => setPresetForm(f => ({ ...f, modelId: e.target.value }))}
                          placeholder={preset.defaultModel || '输入模型 ID'}
                          className="w-full bg-dark-bg border border-dark-border rounded px-2 py-1.5 text-sm focus:border-accent-blue outline-none"
                          onClick={e => e.stopPropagation()}
                        />
                      </div>
                      {(preset.key === 'custom' || presetForm.baseUrl !== preset.baseUrl || presetForm.apiType !== preset.apiType) && (
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
                          <div className="text-[10px] text-dark-muted mt-0.5 opacity-70">
                            {presetForm.apiType === 'anthropic-messages'
                              ? '自动追加 /v1/messages · 输入不含后缀的根地址'
                              : '自动追加 /v1/chat/completions'}
                          </div>
                        </div>
                      )}
                      <div>
                        <label className="block text-[11px] text-dark-muted mb-0.5">API 类型</label>
                        <div className="flex gap-1" onClick={e => e.stopPropagation()}>
                          <label
                            className={`flex-1 flex items-center justify-center gap-1.5 px-2 py-1.5 rounded text-xs cursor-pointer border transition-colors ${
                              presetForm.apiType === 'openai-completions'
                                ? 'bg-accent-blue/20 border-accent-blue text-accent-blue'
                                : 'bg-dark-bg border-dark-border text-dark-muted hover:border-dark-hover'
                            }`}
                          >
                            <input
                              type="radio"
                              name="apiType"
                              value="openai-completions"
                              checked={presetForm.apiType === 'openai-completions'}
                              onChange={e => setPresetForm(f => ({ ...f, apiType: e.target.value, baseUrl: preset.apiTypeBaseUrls?.[e.target.value] ?? f.baseUrl }))}
                              className="sr-only"
                            />
                            OpenAI Compatible
                          </label>
                          <label
                            className={`flex-1 flex items-center justify-center gap-1.5 px-2 py-1.5 rounded text-xs cursor-pointer border transition-colors ${
                              presetForm.apiType === 'anthropic-messages'
                                ? 'bg-accent-blue/20 border-accent-blue text-accent-blue'
                                : 'bg-dark-bg border-dark-border text-dark-muted hover:border-dark-hover'
                            }`}
                          >
                            <input
                              type="radio"
                              name="apiType"
                              value="anthropic-messages"
                              checked={presetForm.apiType === 'anthropic-messages'}
                              onChange={e => setPresetForm(f => ({ ...f, apiType: e.target.value, baseUrl: preset.apiTypeBaseUrls?.[e.target.value] ?? f.baseUrl }))}
                              className="sr-only"
                            />
                            Anthropic Messages
                          </label>
                        </div>
                      </div>
                      {presetError && (
                        <div className="text-xs text-accent-red">{presetError}</div>
                      )}
                      <div className="flex gap-2 pt-1">
                        <button
                          onClick={e => { e.stopPropagation(); handlePresetSave(preset); }}
                          disabled={presetSaving || (!presetForm.apiKey.trim() && !providerSummary?.providers?.[preset.key]?.hasApiKey)}
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
                    {model.apiType && (
                      <span className={`text-[10px] px-1.5 py-0.5 rounded ${
                        model.apiType === 'anthropic-messages'
                          ? 'bg-accent-purple/20 text-accent-purple'
                          : 'bg-accent-blue/20 text-accent-blue'
                      }`}>
                        {model.apiType === 'anthropic-messages' ? 'Anthropic' : 'OpenAI'}
                      </span>
                    )}
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

      {/* Add Model Modal */}
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

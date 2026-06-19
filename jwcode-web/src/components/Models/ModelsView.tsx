import { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Brain, CheckCircle, ChevronUp, Edit3, EyeOff, Key, Plus, RefreshCw, RotateCcw, Sparkles, Trash2, Wifi, WifiOff } from 'lucide-react';
import { Modal } from '../common';
import { api, type Model } from '../../services/api';
import { toast } from '../../stores/toastStore';

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
  { key: 'minimax', name: 'MiniMax / 海螺', baseUrl: 'https://api.minimax.chat/v1', apiType: 'openai-completions', defaultModel: 'abab6.5s-chat' },
  { key: 'doubao', name: '豆包 (Doubao)', baseUrl: 'https://ark.cn-beijing.volces.com/api/v3', apiType: 'openai-completions', defaultModel: 'doubao-pro-32k' },
  { key: 'custom', name: 'Custom', baseUrl: '', apiType: 'openai-completions', defaultModel: '' },
];

interface LocalModelStatus {
  overallStatus: 'healthy' | 'degraded' | 'unhealthy' | 'error';
  healthRate: number;
  healthyInstances: number;
  totalInstances: number;
  totalRequests: number;
}

type AddFormState = {
  provider: string;
  modelId: string;
  modelName: string;
  baseUrl: string;
  apiKeys: string;
  apiType: string;
  temperature: number;
  maxTokens: number;
  contextWindow: number;
};

type ProviderModalMode = 'select' | 'form';

type TokenDraft = { inputTokens: number; outputTokens: number };

const EMPTY_ADD_FORM: AddFormState = {
  provider: '',
  modelId: '',
  modelName: '',
  baseUrl: '',
  apiKeys: '',
  apiType: 'openai-completions',
  temperature: 1.0,
  maxTokens: 32768,
  contextWindow: 128000,
};

const EMPTY_PROVIDER_FORM = {
  providerKey: '',
  apiKey: '',
  modelIds: '',
  baseUrl: '',
  apiType: 'openai-completions',
  setDefault: false,
};

export function ModelsView() {
  const [models, setModels] = useState<Model[]>([]);
  const [status, setStatus] = useState<LocalModelStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState<string | null>(null);

  const [showModelModal, setShowModelModal] = useState(false);
  const [editingModel, setEditingModel] = useState<{ provider: string; modelId: string } | null>(null);
  const [addForm, setAddForm] = useState<AddFormState>(EMPTY_ADD_FORM);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const [providerSummary, setProviderSummary] = useState<ProviderSummary | null>(null);
  const [showProviderModal, setShowProviderModal] = useState(false);
  const [providerModalMode, setProviderModalMode] = useState<ProviderModalMode>('select');
  const [activePreset, setActivePreset] = useState<string | null>(null);
  const [providerForm, setProviderForm] = useState(EMPTY_PROVIDER_FORM);
  const [presetSaving, setPresetSaving] = useState(false);
  const [presetError, setPresetError] = useState<string | null>(null);
  const [showKey, setShowKey] = useState(false);
  const [providerSectionExpanded, setProviderSectionExpanded] = useState(false);

  const [tokenDrafts, setTokenDrafts] = useState<Record<string, TokenDraft>>({});

  const loadProviderSummary = useCallback(async () => {
    try {
      const res = await api.config.provider.get();
      if (res.success && res.data) setProviderSummary(res.data as ProviderSummary);
    } catch {
      // ignore
    }
  }, []);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [modelsRes, systemRes, _] = await Promise.all([
        api.models.list(),
        api.system.status(),
        loadProviderSummary(),
      ]);

      if (modelsRes.success && modelsRes.data) setModels(modelsRes.data.models || []);
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
    } catch {
      setError('加载模型失败');
    } finally {
      setLoading(false);
    }
  }, [loadProviderSummary]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const modelKey = useCallback((model: Model) => `${model.provider}:${model.id}`, []);

  const getDraft = useCallback((model: Model): TokenDraft => {
    return tokenDrafts[modelKey(model)] || { inputTokens: 0, outputTokens: 0 };
  }, [modelKey, tokenDrafts]);

  const setDraft = useCallback((model: Model, field: keyof TokenDraft, value: number) => {
    const key = modelKey(model);
    setTokenDrafts(prev => ({
      ...prev,
      [key]: {
        inputTokens: field === 'inputTokens' ? value : (prev[key]?.inputTokens || 0),
        outputTokens: field === 'outputTokens' ? value : (prev[key]?.outputTokens || 0),
      },
    }));
  }, [modelKey]);

  const resetDraft = useCallback((model: Model) => {
    setTokenDrafts(prev => ({ ...prev, [modelKey(model)]: { inputTokens: 0, outputTokens: 0 } }));
  }, [modelKey]);

  const openProviderForm = useCallback((preset: PresetProvider) => {
    const existing = providerSummary?.providers?.[preset.key];
    const existingModels = models.filter(m => m.provider === preset.key).map(m => m.id);
    const initialModelIds = existingModels.length > 0 ? existingModels.join(', ') : preset.defaultModel;
    setActivePreset(preset.key);
    setProviderForm({
      providerKey: preset.key,
      apiKey: '',
      modelIds: initialModelIds,
      baseUrl: existing?.baseUrl || preset.baseUrl,
      apiType: existing?.apiType || preset.apiType,
      setDefault: providerSummary?.defaultProvider !== preset.key,
    });
    setProviderModalMode('form');
    setShowProviderModal(true);
    setPresetError(null);
    setShowKey(false);
  }, [models, providerSummary]);

  const openCustomProviderForm = useCallback(() => {
    setActivePreset('custom');
    setProviderForm({
      providerKey: '',
      apiKey: '',
      modelIds: '',
      baseUrl: '',
      apiType: 'openai-completions',
      setDefault: !providerSummary?.configured,
    });
    setProviderModalMode('form');
    setShowProviderModal(true);
    setPresetError(null);
    setShowKey(false);
  }, [providerSummary]);

  const handleToggle = async (modelId: string) => {
    const model = models.find(m => m.id === modelId);
    if (!model) return;
    const willDisable = model.enabled !== false;
    const label = model.name || modelId;
    const showDefaultModelHint = (msg: string) =>
      willDisable && /default model/i.test(msg);

    try {
      const res = await api.models.toggle(model.provider, modelId);
      if (res.success) {
        await loadData();
        return;
      }
      const err = (res as { error?: string }).error || '';
      if (showDefaultModelHint(err)) {
        toast.error(`“${label}” 是默认模型，无法直接禁用。请先在「Provider 配置」中更换默认模型，再进行禁用。`);
      } else {
        toast.error(err || '操作失败，请重试');
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : '';
      if (showDefaultModelHint(msg)) {
        toast.error(`“${label}” 是默认模型，无法直接禁用。请先在「Provider 配置」中更换默认模型，再进行禁用。`);
      } else {
        toast.error(msg || '操作失败，请重试');
      }
    }
  };

  const handleRefresh = async (modelId: string) => {
    setRefreshing(modelId);
    try {
      await api.models.test(modelId);
      await loadData();
    } finally {
      setRefreshing(null);
    }
  };

  const handleDelete = async (provider: string, modelId: string) => {
    if (!confirm(`确定要删除模型 "${modelId}" 吗？此操作不可撤销。`)) return;
    try {
      const res = await api.models.delete(provider, modelId);
      if (res.success) await loadData();
    } catch {
      // ignore
    }
  };

  const handleEdit = (model: Model) => {
    setEditingModel({ provider: model.provider, modelId: model.id });
    setAddForm({
      provider: model.provider,
      modelId: model.id,
      modelName: model.name,
      baseUrl: '',
      apiKeys: '',
      apiType: model.apiType || 'openai-completions',
      temperature: model.temperature ?? 1.0,
      maxTokens: model.maxTokens ?? 32768,
      contextWindow: model.contextWindow ?? 128000,
    });
    setSaveError(null);
    setShowModelModal(true);
  };

  const handleProviderSave = async () => {
    const preset = PROVIDER_PRESETS.find(p => p.key === activePreset) || null;
    const providerKey = activePreset === 'custom' ? providerForm.providerKey.trim() : (preset?.key || '');
    if (!providerKey) {
      setPresetError('请先输入 Provider 名称');
      return;
    }
    if (!providerForm.apiKey.trim()) {
      setPresetError('请输入 API Key');
      return;
    }

    const targetPreset = preset || {
      key: providerKey,
      name: providerKey,
      baseUrl: '',
      apiType: 'openai-completions',
      defaultModel: '',
    };

    const existing = providerSummary?.providers?.[providerKey];
    const body: Record<string, unknown> = {
      provider: providerKey,
      baseUrl: providerForm.baseUrl || targetPreset.baseUrl,
      apiType: providerForm.apiType || targetPreset.apiType,
      apiKey: providerForm.apiKey.trim() || (existing?.hasApiKey ? undefined : ''),
      models: (providerForm.modelIds || targetPreset.defaultModel || '').split(',').map((id, i) => ({
        id: id.trim(),
        name: id.trim(),
        enabled: true,
        priority: 10 + i,
      })).filter(m => m.id.length > 0),
    };

    if (!providerSummary?.configured || providerForm.setDefault) body.setDefault = true;

    setPresetSaving(true);
    setPresetError(null);
    try {
      const res = await api.config.provider.save(body);
      if (res.success) {
        setShowProviderModal(false);
        setProviderModalMode('select');
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

  const handleProviderDelete = async (providerKey: string) => {
    if (!confirm(`确定要移除 "${providerKey}" 的配置吗？这将删除其 API Key 和模型。`)) return;
    try {
      await api.config.provider.delete(providerKey);
      await loadProviderSummary();
      await loadData();
    } catch {
      // ignore
    }
  };

  const handleModelSave = async () => {
    if (!addForm.provider.trim() || !addForm.modelId.trim()) {
      setSaveError('提供商名称和模型 ID 不能为空');
      return;
    }

    setSaving(true);
    setSaveError(null);
    try {
      if (editingModel) {
        const modelData: Record<string, unknown> = {
          name: addForm.modelName.trim() || addForm.modelId.trim(),
          temperature: addForm.temperature,
          maxTokens: addForm.maxTokens,
          contextWindow: addForm.contextWindow,
        };
        const res = await api.models.update(editingModel.provider, editingModel.modelId, { model: modelData });
        if (res.success) {
          setShowModelModal(false);
          setEditingModel(null);
          setAddForm(EMPTY_ADD_FORM);
          await loadData();
        } else {
          setSaveError((res as { error?: string }).error || '保存失败');
        }
      } else {
        const modelData: Record<string, unknown> = {
          id: addForm.modelId.trim(),
          name: addForm.modelName.trim() || addForm.modelId.trim(),
          temperature: addForm.temperature,
          maxTokens: addForm.maxTokens,
          contextWindow: addForm.contextWindow,
          enabled: true,
          apiType: addForm.apiType,
        };
        if (addForm.baseUrl.trim()) modelData.baseUrl = addForm.baseUrl.trim();
        if (addForm.apiKeys.trim()) modelData.apiKeys = addForm.apiKeys.split(',').map(k => k.trim()).filter(Boolean);

        const res = await api.models.create({
          provider: addForm.provider.trim(),
          model: modelData,
        });
        if (res.success) {
          setShowModelModal(false);
          setAddForm(EMPTY_ADD_FORM);
          await loadData();
        } else {
          setSaveError(res.error || '保存失败');
        }
      }
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : '未知错误');
    } finally {
      setSaving(false);
    }
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

  const selectedPreset = useMemo(
    () => PROVIDER_PRESETS.find(preset => preset.key === activePreset) || null,
    [activePreset],
  );

  if (loading) {
    return <div className="flex h-64 items-center justify-center text-dark-muted">加载中...</div>;
  }

  if (error) {
    return <div className="flex h-64 items-center justify-center text-accent-red">{error}</div>;
  }

  return (
    <div className="p-6 space-y-6">
      <div>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-base font-semibold">Provider 配置</h2>
          <button
            onClick={() => setProviderSectionExpanded(v => !v)}
            className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs bg-accent-blue text-white rounded hover:opacity-90"
          >
            {providerSectionExpanded ? <ChevronUp size={12} /> : <Plus size={12} />}
            {providerSectionExpanded ? '收起' : '新增 Provider'}
          </button>
        </div>

        {providerSectionExpanded && (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-3">
          {PROVIDER_PRESETS.map(preset => {
            const configured = providerSummary?.providers?.[preset.key];
            const hasKey = configured?.hasApiKey;
            const isDefault = providerSummary?.defaultProvider === preset.key;

            return (
              <div
                className={`text-left p-3 rounded-lg border transition-colors relative ${
                  hasKey ? 'border-accent-green/30 bg-dark-surface hover:border-accent-green/50' : 'border-dark-border bg-dark-surface hover:border-dark-hover'
                }`}
              >
                <div
                  onClick={() => preset.key === 'custom' ? openCustomProviderForm() : openProviderForm(preset)}
                  className="cursor-pointer"
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-sm font-medium">{preset.name}</span>
                    {hasKey && <CheckCircle size={14} className="text-accent-green" />}
                  </div>
                  <div className="text-[10px] text-dark-muted space-y-0.5">
                    <div>{preset.baseUrl || '自定义地址'}</div>
                    <div>{preset.defaultModel || '无默认模型'}</div>
                    <div>{configured?.modelCount || 0} models</div>
                  </div>
                  {isDefault && <div className="mt-1 text-[10px] text-accent-blue">默认 Provider</div>}
                </div>
                {hasKey && (
                  <button
                    onClick={e => {
                      e.stopPropagation();
                      handleProviderDelete(preset.key);
                    }}
                    className="absolute top-1.5 right-1.5 p-1 rounded text-dark-muted hover:bg-accent-red/10 hover:text-accent-red transition-colors"
                    title="删除 Provider"
                    aria-label={`删除 ${preset.name}`}
                  >
                    <Trash2 size={13} />
                  </button>
                )}
              </div>
            );
          })}
        </div>
        )}
      </div>

      {status && (
        <div className="grid grid-cols-4 gap-4">
          <StatusCard title="总体状态" value={status.overallStatus === 'healthy' ? '健康' : status.overallStatus === 'degraded' ? '降级' : '异常'} color={status.overallStatus === 'healthy' ? 'green' : status.overallStatus === 'degraded' ? 'yellow' : 'red'} />
          <StatusCard title="健康率" value={`${(status.healthRate * 100).toFixed(0)}%`} color={status.healthRate > 0.8 ? 'green' : status.healthRate > 0.5 ? 'yellow' : 'red'} />
          <StatusCard title="在线实例" value={`${status.healthyInstances}/${status.totalInstances}`} color={status.healthyInstances > 0 ? 'green' : 'red'} />
          <StatusCard title="总请求数" value={status.totalRequests.toLocaleString()} color="blue" />
        </div>
      )}

      <div>
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-medium">已配置模型</h3>
          <button
            onClick={() => {
              setEditingModel(null);
              setAddForm(EMPTY_ADD_FORM);
              setSaveError(null);
              setShowModelModal(true);
            }}
            className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs bg-accent-blue text-white rounded hover:opacity-90"
          >
            <Plus size={12} />
            添加模型
          </button>
        </div>

        <div className="space-y-2 max-h-[480px] overflow-y-auto pr-1">
          {models.map(model => {
            const draft = getDraft(model);
            const totalDraft = draft.inputTokens + draft.outputTokens;

            return (
              <div
                key={model.id}
                className={`bg-dark-surface border rounded-lg p-3 ${model.status === 'online' ? 'border-accent-green' : 'border-dark-border opacity-70'}`}
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 mb-1 flex-wrap">
                      <span className={getHealthColor(model.status)}>{getHealthIcon(model.status)}</span>
                      <span className="font-medium text-sm">{model.name}</span>
                      <span className="text-[10px] text-dark-muted px-1.5 py-0.5 bg-dark-bg rounded">{model.provider}</span>
                      {model.apiType && (
                        <span className={`text-[10px] px-1.5 py-0.5 rounded ${model.apiType === 'anthropic-messages' ? 'bg-accent-purple/20 text-accent-purple' : 'bg-accent-blue/20 text-accent-blue'}`}>
                          {model.apiType === 'anthropic-messages' ? 'Anthropic' : 'OpenAI'}
                        </span>
                      )}
                      {model.isGlobalDefault && <span className="text-[10px] px-1.5 py-0.5 rounded bg-accent-green/20 text-accent-green font-medium">全局默认</span>}
                      {model.isPlanDefault && <span className="text-[10px] px-1.5 py-0.5 rounded bg-accent-blue/20 text-accent-blue font-medium">Plan 默认</span>}
                      {model.isActDefault && <span className="text-[10px] px-1.5 py-0.5 rounded bg-accent-yellow/20 text-accent-yellow font-medium">Act 默认</span>}
                    </div>

                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
                      <StatCell label="负载" value={`${model.load}/${model.maxLoad}`} />
                      <StatCell label="Token" value={`${model.tokens}/${model.maxTokens}`} />
                      <StatCell label="输入价格" value={`$${model.price.input}/1K`} />
                      <StatCell label="输出价格" value={`$${model.price.output}/1K`} />
                    </div>

                    <div className="mt-3 grid grid-cols-1 sm:grid-cols-4 gap-3 items-end">
                      <NumberInput label="输入 token" value={draft.inputTokens} onChange={v => setDraft(model, 'inputTokens', v)} />
                      <NumberInput label="输出 token" value={draft.outputTokens} onChange={v => setDraft(model, 'outputTokens', v)} />
                      <div>
                        <div className="text-[11px] text-dark-muted mb-1">总 token</div>
                        <div className="px-3 py-2 rounded border border-dark-border bg-dark-bg text-sm font-mono">{totalDraft.toLocaleString()}</div>
                      </div>
                      <button
                        onClick={() => resetDraft(model)}
                        className="inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded border border-dark-border text-sm text-dark-muted hover:bg-dark-hover"
                      >
                        <RotateCcw size={13} />
                        重置
                      </button>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 shrink-0">
                    {!model.isGlobalDefault && model.enabled !== false && (
                      <button
                        onClick={async () => {
                          try {
                            await api.models.setDefaults({ global: `${model.provider}:${model.id}` });
                            await loadData();
                          } catch {
                            // ignore
                          }
                        }}
                        className="px-2 py-1 rounded text-[10px] border border-accent-green/30 text-accent-green hover:bg-accent-green/10"
                        title="设为全局默认"
                      >
                        全局默认
                      </button>
                    )}
                    {!model.isPlanDefault && model.enabled !== false && (
                      <button
                        onClick={async () => {
                          try {
                            await api.models.setDefaults({ plan: `${model.provider}:${model.id}` });
                            await loadData();
                          } catch {
                            // ignore
                          }
                        }}
                        className="px-2 py-1 rounded text-[10px] border border-accent-blue/30 text-accent-blue hover:bg-accent-blue/10"
                        title="设为 Plan 默认"
                      >
                        Plan 默认
                      </button>
                    )}
                    {!model.isActDefault && model.enabled !== false && (
                      <button
                        onClick={async () => {
                          try {
                            await api.models.setDefaults({ act: `${model.provider}:${model.id}` });
                            await loadData();
                          } catch {
                            // ignore
                          }
                        }}
                        className="px-2 py-1 rounded text-[10px] border border-accent-yellow/30 text-accent-yellow hover:bg-accent-yellow/10"
                        title="设为 Act 默认"
                      >
                        Act 默认
                      </button>
                    )}
                    <button onClick={() => handleEdit(model)} className="p-1.5 rounded hover:bg-accent-blue/10 text-dark-muted hover:text-accent-blue" title="编辑模型">
                      <Edit3 size={13} />
                    </button>
                    <button onClick={() => handleDelete(model.provider, model.id)} className="p-1.5 rounded hover:bg-accent-red/10 text-dark-muted hover:text-accent-red" title="删除模型">
                      <Trash2 size={13} />
                    </button>
                    <button onClick={() => handleRefresh(model.id)} disabled={refreshing === model.id} className="p-1.5 rounded hover:bg-dark-hover disabled:opacity-50" title="刷新">
                      <RefreshCw size={13} className={refreshing === model.id ? 'animate-spin' : ''} />
                    </button>
                    <button
                      onClick={() => handleToggle(model.id)}
                      className={`px-2 py-0.5 rounded text-xs transition-colors ${model.status === 'online' ? 'bg-accent-red/20 text-accent-red hover:bg-accent-red/30' : 'bg-accent-green/20 text-accent-green hover:bg-accent-green/30'}`}
                    >
                      {model.status === 'online' ? '禁用' : '启用'}
                    </button>
                  </div>
                </div>
              </div>
            );
          })}

          {models.length === 0 && (
            <div className="text-center text-dark-muted py-6">
              <Brain size={36} className="mx-auto mb-2 opacity-40" />
              <p className="text-sm">暂无模型，请先在上方配置 Provider</p>
            </div>
          )}
        </div>
      </div>

      <Modal
        isOpen={showProviderModal}
        onClose={() => {
          setShowProviderModal(false);
          setProviderModalMode('select');
          setActivePreset(null);
        }}
        title={providerModalMode === 'select' ? '选择 Provider' : (selectedPreset?.name || 'Provider 配置')}
        size="xl"
      >
        {providerModalMode === 'select' ? (
          <div className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {PROVIDER_PRESETS.filter(p => p.key !== 'custom').map(preset => (
                <button
                  key={preset.key}
                  onClick={() => openProviderForm(preset)}
                  className="text-left p-3 rounded-lg border border-dark-border bg-dark-bg hover:border-accent-blue transition-colors"
                >
                  <div className="flex items-center gap-2 mb-1">
                    <CheckCircle size={14} className="text-accent-blue" />
                    <span className="text-sm font-medium">{preset.name}</span>
                  </div>
                  <div className="text-xs text-dark-muted">{preset.defaultModel}</div>
                </button>
              ))}
              <button
                onClick={openCustomProviderForm}
                className="text-left p-3 rounded-lg border border-dashed border-dark-border bg-dark-bg hover:border-accent-blue transition-colors"
              >
                <div className="flex items-center gap-2 mb-1">
                  <Sparkles size={14} className="text-accent-blue" />
                  <span className="text-sm font-medium">自定义 Provider</span>
                </div>
                <div className="text-xs text-dark-muted">手动填写 Provider 名称、Base URL 和模型 ID</div>
              </button>
            </div>
          </div>
        ) : (
          <div className="space-y-3">
            {activePreset === 'custom' && (
              <div>
                <label className="block text-sm text-dark-muted mb-1">Provider 名称 *</label>
                <input
                  value={providerForm.providerKey}
                  onChange={e => setProviderForm(f => ({ ...f, providerKey: e.target.value }))}
                  className="w-full px-3 py-2 bg-dark-bg border border-dark-border rounded text-sm focus:outline-none focus:border-accent-blue"
                  placeholder="例如: my-provider"
                />
              </div>
            )}
            <div>
              <label className="block text-sm text-dark-muted mb-1">API Key *</label>
              <div className="relative">
                <input
                  type={showKey ? 'text' : 'password'}
                  value={providerForm.apiKey}
                  onChange={e => setProviderForm(f => ({ ...f, apiKey: e.target.value }))}
                  className="w-full px-3 py-2 pr-10 bg-dark-bg border border-dark-border rounded text-sm focus:outline-none focus:border-accent-blue"
                  placeholder="输入 API Key"
                />
                <button onClick={() => setShowKey(s => !s)} className="absolute right-2 top-2 text-dark-muted hover:text-white">
                  {showKey ? <EyeOff size={14} /> : <Key size={14} />}
                </button>
              </div>
            </div>
            <div>
              <label className="block text-sm text-dark-muted mb-1">模型 ID（多个用逗号分隔）</label>
              <input
                value={providerForm.modelIds}
                onChange={e => setProviderForm(f => ({ ...f, modelIds: e.target.value }))}
                className="w-full px-3 py-2 bg-dark-bg border border-dark-border rounded text-sm focus:outline-none focus:border-accent-blue"
                placeholder={selectedPreset?.defaultModel || 'gpt-4o, gpt-4.1'}
              />
            </div>
            <div>
              <label className="block text-sm text-dark-muted mb-1">Base URL</label>
              <input
                value={providerForm.baseUrl}
                onChange={e => setProviderForm(f => ({ ...f, baseUrl: e.target.value }))}
                className="w-full px-3 py-2 bg-dark-bg border border-dark-border rounded text-sm focus:outline-none focus:border-accent-blue"
                placeholder="https://api.example.com/v1"
              />
            </div>
            <div>
              <label className="block text-sm text-dark-muted mb-1">API 类型</label>
              <div className="grid grid-cols-2 gap-2">
                {(['openai-completions', 'anthropic-messages'] as const).map(type => (
                  <label
                    key={type}
                    className={`flex items-center justify-center gap-1.5 px-3 py-2 rounded text-sm cursor-pointer border transition-colors ${
                      providerForm.apiType === type ? 'bg-accent-blue/20 border-accent-blue text-accent-blue' : 'bg-dark-bg border-dark-border text-dark-muted hover:border-dark-hover'
                    }`}
                  >
                    <input
                      type="radio"
                      name="providerApiType"
                      value={type}
                      checked={providerForm.apiType === type}
                      onChange={e => setProviderForm(f => ({ ...f, apiType: e.target.value }))}
                      className="sr-only"
                    />
                    {type === 'openai-completions' ? 'OpenAI Compatible' : 'Anthropic Messages'}
                  </label>
                ))}
              </div>
            </div>
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={providerForm.setDefault}
                onChange={e => setProviderForm(f => ({ ...f, setDefault: e.target.checked }))}
                className="accent-accent-blue"
              />
              <span className="text-sm text-dark-muted">设为默认 Provider</span>
            </label>

            {presetError && <div className="text-sm text-accent-red">{presetError}</div>}

            <div className="flex justify-between gap-2 pt-2">
              <div>
                {activePreset && activePreset !== 'custom' && providerSummary?.providers?.[activePreset]?.hasApiKey && (
                  <button
                    onClick={() => handleProviderDelete(activePreset)}
                    className="px-4 py-2 rounded border border-accent-red/30 text-accent-red hover:bg-accent-red/10 transition-colors text-sm"
                  >
                    删除 Provider
                  </button>
                )}
              </div>
              <button
                onClick={() => {
                  setProviderModalMode('select');
                  setActivePreset(null);
                  setShowProviderModal(false);
                }}
                className="px-4 py-2 rounded border border-dark-border hover:bg-dark-hover transition-colors text-sm"
              >
                取消
              </button>
              <button
                onClick={handleProviderSave}
                disabled={presetSaving}
                className="px-4 py-2 bg-accent-blue text-white rounded hover:bg-accent-blue/80 transition-colors text-sm disabled:opacity-50"
              >
                {presetSaving ? '保存中...' : '保存'}
              </button>
            </div>
          </div>
        )}
      </Modal>

      <Modal
        isOpen={showModelModal}
        onClose={() => {
          setShowModelModal(false);
          setEditingModel(null);
        }}
        title={editingModel ? '编辑模型' : '添加模型'}
        size="lg"
      >
        <div className="space-y-3">
          <div>
            <label className="block text-sm text-dark-muted mb-1">提供商名称 *</label>
            <input
              value={addForm.provider}
              onChange={e => setAddForm(f => ({ ...f, provider: e.target.value }))}
              disabled={!!editingModel}
              className="w-full px-3 py-2 bg-dark-bg border border-dark-border rounded text-sm focus:outline-none focus:border-accent-blue disabled:opacity-50"
              placeholder="例如: openai, deepseek"
            />
          </div>
          <div>
            <label className="block text-sm text-dark-muted mb-1">模型 ID *</label>
            <input
              value={addForm.modelId}
              onChange={e => setAddForm(f => ({ ...f, modelId: e.target.value }))}
              disabled={!!editingModel}
              className="w-full px-3 py-2 bg-dark-bg border border-dark-border rounded text-sm focus:outline-none focus:border-accent-blue disabled:opacity-50"
              placeholder="例如: gpt-4o, deepseek-chat"
            />
          </div>
          <div>
            <label className="block text-sm text-dark-muted mb-1">模型名称</label>
            <input
              value={addForm.modelName}
              onChange={e => setAddForm(f => ({ ...f, modelName: e.target.value }))}
              className="w-full px-3 py-2 bg-dark-bg border border-dark-border rounded text-sm focus:outline-none focus:border-accent-blue"
              placeholder="留空则使用模型 ID"
            />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <NumberField label="温度" value={addForm.temperature} step="0.1" min="0" max="2" onChange={v => setAddForm(f => ({ ...f, temperature: v }))} />
            <NumberField label="最大 Token" value={addForm.maxTokens} onChange={v => setAddForm(f => ({ ...f, maxTokens: v }))} />
            <NumberField label="上下文窗" value={addForm.contextWindow} onChange={v => setAddForm(f => ({ ...f, contextWindow: v }))} />
          </div>
          {!editingModel && (
            <>
              <div>
                <label className="block text-sm text-dark-muted mb-1">API 地址</label>
                <input
                  value={addForm.baseUrl}
                  onChange={e => setAddForm(f => ({ ...f, baseUrl: e.target.value }))}
                  className="w-full px-3 py-2 bg-dark-bg border border-dark-border rounded text-sm focus:outline-none focus:border-accent-blue"
                  placeholder="https://api.example.com/v1"
                />
              </div>
              <div>
                <label className="block text-sm text-dark-muted mb-1">API Key（多个用逗号分隔）</label>
                <input
                  value={addForm.apiKeys}
                  onChange={e => setAddForm(f => ({ ...f, apiKeys: e.target.value }))}
                  className="w-full px-3 py-2 bg-dark-bg border border-dark-border rounded text-sm focus:outline-none focus:border-accent-blue"
                  placeholder="sk-xxx, sk-yyy"
                />
              </div>
              <div>
                <label className="block text-sm text-dark-muted mb-1">API 类型</label>
                <div className="grid grid-cols-2 gap-2">
                  {(['openai-completions', 'anthropic-messages'] as const).map(type => (
                    <label
                      key={type}
                      className={`flex items-center justify-center gap-1.5 px-3 py-2 rounded text-sm cursor-pointer border transition-colors ${
                        addForm.apiType === type ? 'bg-accent-blue/20 border-accent-blue text-accent-blue' : 'bg-dark-bg border-dark-border text-dark-muted hover:border-dark-hover'
                      }`}
                    >
                      <input
                        type="radio"
                        name="addModelApiType"
                        value={type}
                        checked={addForm.apiType === type}
                        onChange={e => setAddForm(f => ({ ...f, apiType: e.target.value }))}
                        className="sr-only"
                      />
                      {type === 'openai-completions' ? 'OpenAI Compatible' : 'Anthropic Messages'}
                    </label>
                  ))}
                </div>
              </div>
            </>
          )}

          {saveError && <div className="text-sm text-accent-red bg-accent-red/10 p-2 rounded">{saveError}</div>}

          <div className="flex justify-end gap-2 pt-2">
            <button
              onClick={() => {
                setShowModelModal(false);
                setEditingModel(null);
                setAddForm(EMPTY_ADD_FORM);
              }}
              className="px-4 py-2 rounded border border-dark-border hover:bg-dark-hover transition-colors text-sm"
            >
              取消
            </button>
            <button
              onClick={handleModelSave}
              disabled={saving}
              className="px-4 py-2 bg-accent-blue text-white rounded hover:bg-accent-blue/80 transition-colors text-sm disabled:opacity-50"
            >
              {saving ? '保存中...' : '保存'}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}

function StatusCard({ title, value, color }: { title: string; value: string; color: 'green' | 'yellow' | 'red' | 'blue' }) {
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

function StatCell({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-dark-muted">{label}</div>
      <div>{value}</div>
    </div>
  );
}

function NumberInput({ label, value, onChange }: { label: string; value: number; onChange: (value: number) => void }) {
  return (
    <div>
      <label className="block text-[11px] text-dark-muted mb-1">{label}</label>
      <input
        type="number"
        min="0"
        value={value}
        onChange={e => onChange(parseInt(e.target.value, 10) || 0)}
        className="w-full px-3 py-2 rounded border border-dark-border bg-dark-bg text-sm focus:outline-none focus:border-accent-blue"
      />
    </div>
  );
}

function NumberField({
  label,
  value,
  onChange,
  min,
  max,
  step,
}: {
  label: string;
  value: number;
  onChange: (value: number) => void;
  min?: string;
  max?: string;
  step?: string;
}) {
  return (
    <div>
      <label className="block text-sm text-dark-muted mb-1">{label}</label>
      <input
        type="number"
        value={value}
        min={min}
        max={max}
        step={step}
        onChange={e => onChange(step ? (parseFloat(e.target.value) || 0) : (parseInt(e.target.value, 10) || 0))}
        className="w-full px-3 py-2 bg-dark-bg border border-dark-border rounded text-sm focus:outline-none focus:border-accent-blue"
      />
    </div>
  );
}

export default ModelsView;

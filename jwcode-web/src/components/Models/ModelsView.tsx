import { useEffect, useState } from 'react';
import { Brain, RefreshCw, Wifi, WifiOff, Activity, AlertTriangle, Plus, X } from 'lucide-react';
import { api, type Model } from '../../services/api';
// 本地兼容类型
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

  const loadData = async () => {
    setLoading(true);
    setError(null);
    
    try {
      const [modelsRes, systemRes] = await Promise.all([
        api.models.list(),
        api.system.status()
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
          totalRequests: 0
        });
      }
    } catch (err) {
      setError('加载模型失败');
    }
    
    setLoading(false);
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleToggle = async (_modelId: string, _currentStatus: string) => {
    // 模型配置来自YAML，动态切换状态需要后端支持
    // 目前仅做前端状态刷新
    await loadData();
  };

  const handleRefresh = async (modelId: string) => {
    setRefreshing(modelId);
    await api.models.test(modelId);
    await loadData();
    setRefreshing(null);
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
          模型状态
        </h2>
        <div className="flex items-center gap-2">
          <button
            onClick={() => { setShowAddForm(true); setSaveError(null); }}
            className="flex items-center gap-2 px-3 py-1.5 bg-accent-blue text-white rounded hover:bg-accent-blue/80 transition-colors"
          >
            <Plus size={14} />
            添加模型
          </button>
          <button
            onClick={loadData}
            className="flex items-center gap-2 px-3 py-1.5 bg-dark-surface border border-dark-border rounded hover:bg-dark-hover transition-colors"
          >
            <RefreshCw size={14} />
            刷新
          </button>
        </div>
      </div>

      {/* Status Overview */}
      {status && (
        <div className="grid grid-cols-4 gap-4 mb-6">
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
      <div className="flex-1 overflow-y-auto">
        <div className="space-y-3">
          {models.map(model => (
            <div
              key={model.id}
              className={`bg-dark-surface border rounded-lg p-4 transition-colors ${
                model.status === 'online' ? 'border-accent-green' : 'border-dark-border opacity-60'
              }`}
            >
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-1">
                    <span className={getHealthColor(model.status)}>
                      {getHealthIcon(model.status)}
                    </span>
                    <span className="font-medium">{model.name}</span>
                    <span className="text-xs text-dark-muted px-2 py-0.5 bg-dark-bg rounded">
                      {model.provider}
                    </span>
                  </div>
                  
                  <div className="grid grid-cols-5 gap-4 text-sm">
                    <div>
                      <div className="text-dark-muted text-xs">负载</div>
                      <div>{model.load}/{model.maxLoad}</div>
                    </div>
                    <div>
                      <div className="text-dark-muted text-xs">Token</div>
                      <div>{model.tokens}/{model.maxTokens}</div>
                    </div>
                    <div>
                      <div className="text-dark-muted text-xs">输入价格</div>
                      <div>${model.price.input}/1K</div>
                    </div>
                    <div>
                      <div className="text-dark-muted text-xs">输出价格</div>
                      <div>${model.price.output}/1K</div>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-2 ml-4">
                  <button
                    onClick={() => handleRefresh(model.id)}
                    disabled={refreshing === model.id}
                    className="p-1.5 rounded hover:bg-dark-hover disabled:opacity-50"
                    title="刷新"
                  >
                    <RefreshCw size={14} className={refreshing === model.id ? 'animate-spin' : ''} />
                  </button>
                  <button
                    onClick={() => handleToggle(model.id, model.status)}
                    className={`px-3 py-1 rounded text-sm transition-colors ${
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
            <div className="text-center text-dark-muted py-8">
              <Brain size={48} className="mx-auto mb-2 opacity-50" />
              <p>暂无配置的模型</p>
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

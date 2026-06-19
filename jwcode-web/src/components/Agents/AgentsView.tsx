import { useEffect, useState } from 'react';
import {
  AlertTriangle,
  ChevronDown,
  Circle,
  Layers,
  PowerOff,
  RefreshCw,
  ToggleLeft,
  ToggleRight,
  Users,
} from 'lucide-react';
import { api, type Agent, type Model } from '../../services/api';

export function AgentsView() {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [models, setModels] = useState<Model[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [savingAgent, setSavingAgent] = useState<string | null>(null);
  const [togglingAgent, setTogglingAgent] = useState<string | null>(null);
  const [expandedAgent, setExpandedAgent] = useState<string | null>(null);

  const loadData = async () => {
    setLoading(true);
    setError(null);

    try {
      const [agentsRes, modelsRes] = await Promise.all([
        api.agents.list(),
        api.models.list(),
      ]);

      if (agentsRes.success && agentsRes.data) {
        setAgents(agentsRes.data);
      } else {
        setError(agentsRes.error || '加载 Agents 失败');
      }

      if (modelsRes.success && modelsRes.data) {
        setModels(modelsRes.data.models || []);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载数据失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handleToggleEnabled = async (agent: Agent) => {
    const nextEnabled = agent.enabled === false;
    setTogglingAgent(agent.id);
    setError(null);

    try {
      const res = await api.agents.toggle(agent.id, nextEnabled);
      if (res.success && res.data) {
        setAgents(prev => prev.map(item => item.id === agent.id ? {
          ...item,
          enabled: res.data!.enabled,
          instanceCount: res.data!.instanceCount,
          state: res.data!.state,
        } : item));
      } else {
        setError(res.error || '更新 Agent 状态失败');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '更新 Agent 状态失败');
    } finally {
      setTogglingAgent(null);
    }
  };

  const handleModelBindingChange = async (
    agentId: string,
    mode: 'mode-default' | 'specified',
    modelRef?: string,
  ) => {
    setSavingAgent(agentId);
    try {
      const data: { mode: 'mode-default' | 'specified'; modelRef?: string } = { mode };
      if (mode === 'specified' && modelRef) {
        data.modelRef = modelRef;
      }
      const res = await api.agents.setModelBinding(agentId, data);
      if (res.success) {
        await loadData();
      }
    } catch (err) {
      console.error('Failed to set model binding:', err);
    } finally {
      setSavingAgent(null);
    }
  };

  const getStateIcon = (agent: Agent) => {
    const instanceCount = agent.instanceCount ?? 0;
    if (agent.enabled === false) return <PowerOff size={14} className="text-dark-muted" />;
    if (instanceCount > 0) return <RefreshCw size={14} className="text-accent-blue animate-spin" />;
    if (agent.state === 'error') return <AlertTriangle size={14} className="text-accent-red" />;
    return <Circle size={14} className="text-accent-green" />;
  };

  const getStateLabel = (agent: Agent) => {
    if (agent.enabled === false) return '已关闭';
    if ((agent.instanceCount ?? 0) > 0) return '运行中';
    if (agent.state === 'error') return '异常';
    return '已启用';
  };

  const getBindingLabel = (agent: Agent) => {
    const binding = agent.modelBinding;
    if (!binding || binding.mode === 'mode-default') return '跟随模式默认';
    if (binding.mode === 'specified' && binding.modelRef) return binding.modelRef;
    return '跟随模式默认';
  };

  const hasFallback = (agent: Agent) => {
    return agent.effectivePlanModel?.fallback || agent.effectiveActModel?.fallback;
  };

  const getFallbackHint = (agent: Agent) => {
    const hints: string[] = [];
    if (agent.effectivePlanModel?.fallback) {
      hints.push('Plan: ' + (agent.effectivePlanModel.fallbackReason || '已降级'));
    }
    if (agent.effectiveActModel?.fallback) {
      hints.push('Act: ' + (agent.effectiveActModel.fallbackReason || '已降级'));
    }
    return hints.join('; ');
  };

  const getModelDisplayName = (model: Model) => {
    return model.name || model.id;
  };

  const getModelOptionLabel = (model: Model) => {
    return `${model.provider}:${model.id} (${getModelDisplayName(model)})`;
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
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold flex items-center gap-2">
          <Users size={20} />
          Agent 管理
          <span className="text-sm font-normal text-dark-muted">({agents.length})</span>
        </h2>
        <button
          onClick={loadData}
          className="flex items-center gap-2 px-3 py-1.5 bg-dark-surface border border-dark-border rounded hover:bg-dark-hover transition-colors"
        >
          <RefreshCw size={14} />
          刷新
        </button>
      </div>

      <div className="flex-1 overflow-y-auto">
        <div className="space-y-3">
          {agents.map(agent => {
            const enabled = agent.enabled !== false;
            const instanceCount = agent.instanceCount ?? 0;
            const isBusy = instanceCount > 0;
            const isToggling = togglingAgent === agent.id;

            return (
              <div
                key={agent.id}
                className={`bg-dark-surface border rounded-lg p-4 transition-all ${
                  enabled ? 'border-dark-border' : 'border-dark-border opacity-75'
                } ${isBusy ? 'shadow-lg shadow-accent-blue/10' : ''}`}
              >
                <div className="flex items-center justify-between gap-4">
                  <div className="flex items-center gap-3 min-w-0">
                    <div
                      className="w-10 h-10 rounded-full flex items-center justify-center text-white font-bold shrink-0"
                      style={{ backgroundColor: agent.color }}
                    >
                      {agent.name.charAt(0).toUpperCase()}
                    </div>
                    <div className="min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <h3 className="font-medium truncate">{agent.name}</h3>
                        <span className={`px-2 py-0.5 rounded text-xs ${
                          enabled ? 'bg-accent-green/20 text-accent-green' : 'bg-dark-bg text-dark-muted'
                        }`}>
                          {enabled ? '已启用' : '已关闭'}
                        </span>
                      </div>
                      <div className="flex items-center gap-3 mt-1 text-xs text-dark-muted flex-wrap">
                        <span className="flex items-center gap-1">
                          {getStateIcon(agent)}
                          {getStateLabel(agent)}
                        </span>
                        <span className="truncate">{agent.description}</span>
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 shrink-0">
                    <span
                      className={`flex items-center gap-1 text-xs px-2 py-1 rounded ${
                        instanceCount > 0
                          ? 'bg-accent-blue/20 text-accent-blue'
                          : 'bg-accent-red/20 text-accent-red'
                      }`}
                      title={instanceCount > 0 ? '当前后台运行实例数' : '无后台运行实例'}
                    >
                      <Layers size={14} />
                      后台实例 {instanceCount}
                    </span>

                    <span
                      className={`text-xs px-2 py-1 rounded cursor-pointer hover:opacity-80 ${
                        hasFallback(agent)
                          ? 'bg-accent-yellow/20 text-accent-yellow'
                          : 'bg-dark-bg text-dark-muted'
                      }`}
                      onClick={() => setExpandedAgent(expandedAgent === agent.id ? null : agent.id)}
                      title={hasFallback(agent) ? getFallbackHint(agent) : '点击展开模型设置'}
                    >
                      {getBindingLabel(agent)}
                      {hasFallback(agent) && ' !'}
                    </span>

                    <button
                      onClick={() => handleToggleEnabled(agent)}
                      disabled={isToggling}
                      title={enabled ? '点击关闭' : '点击启用'}
                      className={`relative inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-full transition-colors text-xs font-medium disabled:opacity-60 ${
                        enabled
                          ? 'bg-accent-green/20 text-accent-green hover:bg-accent-green/30'
                          : 'bg-accent-red/20 text-accent-red hover:bg-accent-red/30'
                      }`}
                    >
                      {isToggling ? (
                        <RefreshCw size={14} className="animate-spin" />
                      ) : enabled ? (
                        <ToggleRight size={22} />
                      ) : (
                        <ToggleLeft size={22} />
                      )}
                      {enabled ? '启用' : '关闭'}
                    </button>
                  </div>
                </div>

                {expandedAgent === agent.id && (
                  <div className="mt-3 pt-3 border-t border-dark-border">
                    <h4 className="text-xs font-medium text-dark-muted mb-2">模型绑定配置</h4>

                    <div className="flex items-center gap-3 mb-2">
                      <label className="flex items-center gap-1.5 cursor-pointer">
                        <input
                          type="radio"
                          name={`binding-mode-${agent.id}`}
                          checked={!agent.modelBinding || agent.modelBinding.mode === 'mode-default'}
                          onChange={() => handleModelBindingChange(agent.id, 'mode-default')}
                          className="accent-accent-blue"
                        />
                        <span className="text-xs text-dark-muted">跟随模式默认</span>
                      </label>
                      <label className="flex items-center gap-1.5 cursor-pointer">
                        <input
                          type="radio"
                          name={`binding-mode-${agent.id}`}
                          checked={agent.modelBinding?.mode === 'specified'}
                          onChange={() => {
                            const firstModel = models.find(m => m.enabled !== false);
                            if (firstModel) {
                              handleModelBindingChange(agent.id, 'specified', `${firstModel.provider}:${firstModel.id}`);
                            }
                          }}
                          className="accent-accent-blue"
                        />
                        <span className="text-xs text-dark-muted">指定模型</span>
                      </label>
                      {savingAgent === agent.id && (
                        <RefreshCw size={12} className="animate-spin text-accent-blue" />
                      )}
                    </div>

                    {agent.modelBinding?.mode === 'specified' && (
                      <div className="mb-3">
                        <div className="relative">
                          <select
                            value={agent.modelBinding.modelRef || ''}
                            onChange={e => handleModelBindingChange(agent.id, 'specified', e.target.value)}
                            className="w-full max-w-xs px-2 py-1.5 bg-dark-bg border border-dark-border rounded text-xs focus:border-accent-blue outline-none appearance-none cursor-pointer"
                          >
                            <option value="" disabled>选择模型...</option>
                            {models
                              .filter(m => m.enabled !== false)
                              .map(model => (
                                <option key={`${model.provider}:${model.id}`} value={`${model.provider}:${model.id}`}>
                                  {getModelOptionLabel(model)}
                                </option>
                              ))}
                          </select>
                          <ChevronDown size={12} className="absolute right-2 top-1/2 -translate-y-1/2 text-dark-muted pointer-events-none" />
                        </div>
                      </div>
                    )}

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
                      <div className="bg-dark-bg rounded p-2">
                        <div className="text-dark-muted mb-1">Plan 模式生效模型</div>
                        {agent.effectivePlanModel ? (
                          <div>
                            {agent.effectivePlanModel.usable ? (
                              <span className="text-accent-green">
                                {agent.effectivePlanModel.modelRef || '无'}
                                {agent.effectivePlanModel.fallback && (
                                  <span className="text-accent-yellow ml-1" title={agent.effectivePlanModel.fallbackReason}>
                                    (已降级)
                                  </span>
                                )}
                              </span>
                            ) : (
                              <span className="text-accent-red" title={agent.effectivePlanModel.error}>
                                不可用: {agent.effectivePlanModel.error || '未知错误'}
                              </span>
                            )}
                          </div>
                        ) : (
                          <span className="text-dark-muted">未加载</span>
                        )}
                      </div>
                      <div className="bg-dark-bg rounded p-2">
                        <div className="text-dark-muted mb-1">Act 模式生效模型</div>
                        {agent.effectiveActModel ? (
                          <div>
                            {agent.effectiveActModel.usable ? (
                              <span className="text-accent-green">
                                {agent.effectiveActModel.modelRef || '无'}
                                {agent.effectiveActModel.fallback && (
                                  <span className="text-accent-yellow ml-1" title={agent.effectiveActModel.fallbackReason}>
                                    (已降级)
                                  </span>
                                )}
                              </span>
                            ) : (
                              <span className="text-accent-red" title={agent.effectiveActModel.error}>
                                不可用: {agent.effectiveActModel.error || '未知错误'}
                              </span>
                            )}
                          </div>
                        ) : (
                          <span className="text-dark-muted">未加载</span>
                        )}
                      </div>
                    </div>
                  </div>
                )}
              </div>
            );
          })}

          {agents.length === 0 && (
            <div className="text-center text-dark-muted py-8">
              <Users size={48} className="mx-auto mb-2 opacity-50" />
              <p>暂无 Agent</p>
              <p className="text-sm mt-2">Agent 将在运行时自动创建</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default AgentsView;

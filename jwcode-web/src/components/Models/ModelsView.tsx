import { useEffect, useState } from 'react';
import { Brain, RefreshCw, Wifi, WifiOff, Activity, AlertTriangle } from 'lucide-react';
import { api, type Model } from '../../services/api';
// 本地兼容类型
interface LocalModelStatus {
  overallStatus: 'healthy' | 'degraded' | 'error';
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

  const loadData = async () => {
    setLoading(true);
    setError(null);
    
    try {
      const [modelsRes, systemRes] = await Promise.all([
        api.models.list(),
        api.system.status()
      ]);
      
      if (modelsRes.success && modelsRes.data) {
        setModels(modelsRes.data);
      }
      
      if (systemRes.success && systemRes.data) {
        setStatus({
          overallStatus: 'healthy',
          healthRate: 0.95,
          healthyInstances: modelsRes.data?.length || 0,
          totalInstances: modelsRes.data?.length || 0,
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

  const handleToggle = async (modelId: string, currentEnabled: boolean) => {
    const res = await api.models.update(modelId, { status: currentEnabled ? 'offline' : 'online' });
    if (res.success) {
      loadData();
    }
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
        <button
          onClick={loadData}
          className="flex items-center gap-2 px-3 py-1.5 bg-dark-surface border border-dark-border rounded hover:bg-dark-hover transition-colors"
        >
          <RefreshCw size={14} />
          刷新
        </button>
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
                    onClick={() => handleToggle(model.id, model.status === 'online')}
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

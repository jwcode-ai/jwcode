import { useEffect, useState } from 'react';
import { Users, RefreshCw, CheckCircle, Circle, AlertTriangle, Trash2 } from 'lucide-react';
import { api, type Agent } from '../../services/api';

export function AgentsView() {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    
    const res = await api.agents.list();
    
    if (res.success && res.data) {
      setAgents(res.data);
    } else {
      setError(res.error || '加载 Agents 失败');
    }
    
    setLoading(false);
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleActivate = async (agentId: string) => {
    const res = await api.agents.setActive(agentId);
    if (res.success) {
      setAgents(prev => prev.map(a => ({
        ...a,
        active: a.id === agentId
      })));
    }
  };

  const handleDelete = async (agentId: string) => {
    if (!confirm('确定要删除这个 Agent 吗？')) return;
    
    const res = await api.agents.delete(agentId);
    if (res.success) {
      setAgents(prev => prev.filter(a => a.id !== agentId));
    }
  };

  const getStateIcon = (state: Agent['state']) => {
    switch (state) {
      case 'idle': return <Circle size={14} className="text-dark-muted" />;
      case 'busy': return <RefreshCw size={14} className="text-accent-blue animate-spin" />;
      case 'error': return <AlertTriangle size={14} className="text-accent-red" />;
    }
  };

  const getStateLabel = (state: Agent['state']) => {
    switch (state) {
      case 'idle': return '空闲';
      case 'busy': return '工作中';
      case 'error': return '异常';
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

      {/* Agents List */}
      <div className="flex-1 overflow-y-auto">
        <div className="space-y-3">
          {agents.map(agent => (
            <div
              key={agent.id}
              className={`bg-dark-surface border rounded-lg p-4 transition-all ${
                agent.active ? 'border-accent-blue shadow-lg shadow-accent-blue/10' : 'border-dark-border'
              }`}
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div 
                    className="w-10 h-10 rounded-full flex items-center justify-center text-white font-bold"
                    style={{ backgroundColor: agent.color }}
                  >
                    {agent.name.charAt(0).toUpperCase()}
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <h3 className="font-medium">{agent.name}</h3>
                      {agent.active && (
                        <span className="px-2 py-0.5 bg-accent-blue/20 text-accent-blue rounded text-xs">
                          当前活跃
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-3 mt-1 text-xs text-dark-muted">
                      <span className="flex items-center gap-1">
                        {getStateIcon(agent.state)}
                        {getStateLabel(agent.state)}
                      </span>
                      <span>{agent.description}</span>
                    </div>
                  </div>
                </div>
                
                <div className="flex items-center gap-2">
                  {!agent.active && (
                    <button
                      onClick={() => handleActivate(agent.id)}
                      className="flex items-center gap-1 px-3 py-1.5 bg-accent-blue text-white rounded hover:bg-accent-blue/90 transition-colors text-sm"
                    >
                      <CheckCircle size={14} />
                      激活
                    </button>
                  )}
                  <button
                    onClick={() => handleDelete(agent.id)}
                    className="p-1.5 rounded hover:bg-accent-red/10 text-dark-muted hover:text-accent-red transition-colors"
                    title="删除"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              </div>
            </div>
          ))}

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

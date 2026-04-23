import { useEffect, useState } from 'react';
import { Target, RefreshCw, ToggleLeft, ToggleRight, AlertTriangle } from 'lucide-react';
import { api, type Skill } from '../../services/api';

export function SkillsView() {
  const [skills, setSkills] = useState<Skill[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    
    const res = await api.skills.list();
    
    if (res.success && res.data) {
      setSkills(res.data);
    } else {
      setError(res.error || '加载技能失败');
    }
    
    setLoading(false);
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleToggle = async (skillId: string, currentEnabled: boolean) => {
    const res = await api.skills.toggle(skillId, !currentEnabled);
    if (res.success) {
      setSkills(prev => prev.map(s => 
        s.id === skillId ? { ...s, enabled: !currentEnabled } : s
      ));
    }
  };

  const getCategoryIcon = (category: string) => {
    const icons: Record<string, string> = {
      'coding': '💻',
      'analysis': '🔍',
      'refactor': '🔧',
      'test': '🧪',
      'deploy': '🚀',
    };
    return icons[category] || '⭐';
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
          <Target size={20} />
          技能管理
          <span className="text-sm font-normal text-dark-muted">({skills.length})</span>
        </h2>
        <button
          onClick={loadData}
          className="flex items-center gap-2 px-3 py-1.5 bg-dark-surface border border-dark-border rounded hover:bg-dark-hover transition-colors"
        >
          <RefreshCw size={14} />
          刷新
        </button>
      </div>

      {/* Skills Grid */}
      <div className="flex-1 overflow-y-auto">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {skills.map(skill => (
            <div
              key={skill.id}
              className={`bg-dark-surface border rounded-lg p-4 transition-all ${
                skill.enabled 
                  ? 'border-dark-border hover:border-accent-blue' 
                  : 'border-dark-border opacity-60'
              }`}
            >
              <div className="flex items-start justify-between mb-3">
                <div className="flex items-center gap-2">
                  <span className="text-2xl">{skill.icon || getCategoryIcon(skill.category)}</span>
                  <div>
                    <h3 className="font-medium">{skill.name}</h3>
                    <span className="text-xs text-dark-muted px-2 py-0.5 bg-dark-bg rounded">
                      {skill.category}
                    </span>
                  </div>
                </div>
                <button
                  onClick={() => handleToggle(skill.id, skill.enabled)}
                  className={`p-1.5 rounded transition-colors ${
                    skill.enabled ? 'text-accent-green hover:bg-accent-green/10' : 'text-dark-muted hover:bg-dark-hover'
                  }`}
                  title={skill.enabled ? '禁用' : '启用'}
                >
                  {skill.enabled ? <ToggleRight size={24} /> : <ToggleLeft size={24} />}
                </button>
              </div>
              
              <p className="text-sm text-dark-muted mb-3 line-clamp-2">
                {skill.description}
              </p>
              
              <div className="flex items-center gap-2 text-xs">
                <span className={`px-2 py-0.5 rounded ${
                  skill.enabled ? 'bg-accent-green/20 text-accent-green' : 'bg-dark-hover text-dark-muted'
                }`}>
                  {skill.enabled ? '已启用' : '已禁用'}
                </span>
              </div>
            </div>
          ))}
        </div>

        {skills.length === 0 && (
          <div className="text-center text-dark-muted py-8">
            <Target size={48} className="mx-auto mb-2 opacity-50" />
            <p>暂无技能</p>
          </div>
        )}
      </div>
    </div>
  );
}

export default SkillsView;

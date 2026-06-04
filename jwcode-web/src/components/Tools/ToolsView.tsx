import { useEffect, useState } from 'react';
import { Wrench, Search, RefreshCw, ToggleLeft, ToggleRight, ChevronRight, ChevronDown, AlertTriangle, Lock } from 'lucide-react';
import { usePlanStore } from '../../stores/planStore';
import { api, type Tool } from '../../services/api';

export function ToolsView() {
  const planMode = usePlanStore((s) => s.mode);
  const [tools, setTools] = useState<Tool[]>([]);
  const [filteredTools, setFilteredTools] = useState<Tool[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [expandedTool, setExpandedTool] = useState<string | null>(null);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    
    const res = await api.tools.list();
    
    if (res.success && res.data) {
      setTools(res.data);
      setFilteredTools(res.data);
    } else {
      setError(res.error || '加载工具失败');
    }
    
    setLoading(false);
  };

  useEffect(() => {
    loadData();
  }, []);

  useEffect(() => {
    let filtered = tools;
    
    if (searchQuery) {
      filtered = filtered.filter(tool => 
        tool.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        tool.description.toLowerCase().includes(searchQuery.toLowerCase())
      );
    }
    
    if (selectedCategory) {
      filtered = filtered.filter(tool => tool.category === selectedCategory);
    }
    
    setFilteredTools(filtered);
  }, [searchQuery, selectedCategory, tools]);

  const handleToggle = async (toolId: string, currentEnabled: boolean) => {
    const res = await api.tools.toggle(toolId, !currentEnabled);
    if (res.success) {
      setTools(prev => prev.map(t => 
        t.id === toolId ? { ...t, enabled: !currentEnabled } : t
      ));
    }
  };

  const categories = [...new Set(tools.map(t => t.category))];

  const getCategoryColor = (category: string) => {
    const colors: Record<string, string> = {
      'file': 'bg-accent-blue/20 text-accent-blue',
      'search': 'bg-accent-green/20 text-accent-green',
      'git': 'bg-accent-yellow/20 text-accent-yellow',
      'web': 'bg-accent-purple/20 text-accent-purple',
      'system': 'bg-accent-red/20 text-accent-red',
    };
    return colors[category] || 'bg-dark-hover text-dark-muted';
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
          <Wrench size={20} />
          工具列表
          <span className="text-sm font-normal text-dark-muted">({filteredTools.length})</span>
        </h2>
        <button
          onClick={loadData}
          className="flex items-center gap-2 px-3 py-1.5 bg-dark-surface border border-dark-border rounded hover:bg-dark-hover transition-colors"
        >
          <RefreshCw size={14} />
          刷新
        </button>
      </div>

      {/* Search and Filter */}
      <div className="flex gap-3 mb-4">
        <div className="flex-1 relative">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-dark-muted" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="搜索工具..."
            className="w-full pl-10 pr-4 py-2 bg-dark-surface border border-dark-border rounded-lg text-dark-text placeholder-dark-muted focus:border-accent-blue focus:outline-none"
          />
        </div>
        <div className="flex gap-1">
          <button
            onClick={() => setSelectedCategory(null)}
            className={`px-3 py-2 rounded-lg text-sm transition-colors ${
              !selectedCategory ? 'bg-accent-blue text-white' : 'bg-dark-surface border border-dark-border hover:bg-dark-hover'
            }`}
          >
            全部
          </button>
          {categories.slice(0, 4).map(cat => (
            <button
              key={cat}
              onClick={() => setSelectedCategory(cat)}
              className={`px-3 py-2 rounded-lg text-sm transition-colors ${
                selectedCategory === cat ? 'bg-accent-blue text-white' : 'bg-dark-surface border border-dark-border hover:bg-dark-hover'
              }`}
            >
              {cat}
            </button>
          ))}
        </div>
      </div>

      {/* Tools List */}
      <div className="flex-1 overflow-y-auto">
        <div className="space-y-2">
          {filteredTools.map(tool => (
            <div
              key={tool.id}
              className={`bg-dark-surface border rounded-lg transition-colors ${
                tool.enabled ? 'border-dark-border' : 'border-dark-border opacity-60'
              }`}
            >
              <div 
                className="flex items-center justify-between p-4 cursor-pointer"
                onClick={() => setExpandedTool(expandedTool === tool.id ? null : tool.id)}
              >
                <div className="flex items-center gap-3 flex-1">
                  <button
                    onClick={(e) => { e.stopPropagation(); setExpandedTool(expandedTool === tool.id ? null : tool.id); }}
                    className="text-dark-muted"
                  >
                    {expandedTool === tool.id ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                  </button>
                  <span className={`px-2 py-0.5 rounded text-xs ${getCategoryColor(tool.category)}`}>
                    {tool.category}
                  </span>
                  <span className="font-medium">{tool.name}</span>
                  {planMode === "plan" && (tool.category === "File Operation" || tool.category === "Execution") && <Lock size={14} className="text-accent-red ml-1.5" />}
                  {planMode === "plan" && (tool.category === "File Operation" || tool.category === "Execution") && <Lock size={14} className="text-accent-red ml-1.5" />}
                </div>
                
                <div className="flex items-center gap-3">
                  <button
                    onClick={(e) => { e.stopPropagation(); handleToggle(tool.id, tool.enabled); }}
                    className={`p-1.5 rounded transition-colors ${
                      tool.enabled ? 'text-accent-green hover:bg-accent-green/10' : 'text-dark-muted hover:bg-dark-hover'
                    }`}
                    title={tool.enabled ? '禁用' : '启用'}
                  >
                    {tool.enabled ? <ToggleRight size={24} /> : <ToggleLeft size={24} />}
                  </button>
                </div>
              </div>
              
              {expandedTool === tool.id && (
                <div className="px-4 pb-4 border-t border-dark-border">
                  <p className="text-sm text-dark-muted mt-3 mb-3">{tool.description}</p>
                  {tool.params && tool.params.length > 0 && (
                    <div>
                      <div className="text-xs text-dark-muted mb-2">参数</div>
                      <div className="space-y-1">
                        {tool.params.map((param, idx) => (
                          <div key={idx} className="flex items-center gap-2 text-sm">
                            <code className="px-1.5 py-0.5 bg-dark-bg rounded text-accent-blue">
                              {param.name}
                              {param.required && <span className="text-accent-red">*</span>}
                            </code>
                            <span className="text-dark-muted text-xs">({param.type})</span>
                            <span className="text-dark-muted text-xs">{param.description}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}

          {filteredTools.length === 0 && (
            <div className="text-center text-dark-muted py-8">
              <Wrench size={48} className="mx-auto mb-2 opacity-50" />
              <p>暂无工具</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default ToolsView;

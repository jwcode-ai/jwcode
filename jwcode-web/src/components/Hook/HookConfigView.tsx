import { useEffect, useCallback } from 'react';
import { useHooksStore } from '../../stores/hooksStore';
import { HookRuleTable } from './HookRuleTable';
import { HookRuleDrawer } from './HookRuleDrawer';
import { HookStatsCards } from './HookStatsCards';
import { Download, Plus, RefreshCw, Upload, X } from 'lucide-react';

export function HookConfigView() {
  const {
    rules, stats, events, agents, loading, error,
    formOpen, loadAll, openForm, clearError,
  } = useHooksStore();

  useEffect(() => {
    loadAll();
  }, []);

  const handleExport = useCallback(async () => {
    const { api } = await import('../../services/api');
    const res = await api.hooks.export();
    if (res.success && res.data) {
      const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'hooks-export.json';
      a.click();
      URL.revokeObjectURL(url);
    }
  }, []);

  const handleImport = useCallback(() => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json';
    input.onchange = async (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (!file) return;
      const text = await file.text();
      try {
        const data = JSON.parse(text);
        const { api } = await import('../../services/api');
        const mode = confirm('合并到现有配置吗？\n"确定"=合并 / "取消"=替换') ? 'merge' : 'replace';
        const res = await api.hooks.import({ ...data, mergeMode: mode });
        if (res.success) {
          loadAll();
        }
      } catch (err) {
        console.error('Import failed:', err);
      }
    };
    input.click();
  }, [loadAll]);

  return (
    <div className="flex h-full flex-col bg-dark-bg text-dark-text">
      {/* Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-dark-border px-6 py-4">
        <div>
          <h2 className="text-base font-semibold text-dark-text">Hook 配置</h2>
          <p className="mt-0.5 text-xs text-dark-muted">在工具执行、任务流转等生命周期事件上挂载拦截器，实现审批与自定义逻辑</p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={loadAll}
            title="刷新"
            className="rounded-lg p-2 text-dark-muted transition-colors hover:bg-dark-hover hover:text-dark-text"
          >
            <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
          </button>
          <button
            onClick={handleImport}
            className="inline-flex items-center gap-1.5 rounded-lg border border-dark-border bg-dark-surface px-3 py-2 text-sm text-dark-text transition-colors hover:bg-dark-hover"
          >
            <Upload size={14} />
            导入
          </button>
          <button
            onClick={handleExport}
            className="inline-flex items-center gap-1.5 rounded-lg border border-dark-border bg-dark-surface px-3 py-2 text-sm text-dark-text transition-colors hover:bg-dark-hover"
          >
            <Download size={14} />
            导出
          </button>
          <button
            onClick={() => openForm()}
            className="inline-flex items-center gap-1.5 rounded-lg bg-accent-blue px-3 py-2 text-sm text-white transition-colors hover:bg-accent-blue/90"
          >
            <Plus size={14} />
            新增规则
          </button>
        </div>
      </div>

      {/* Body */}
      <div className="flex-1 overflow-auto px-6 py-4">
        {error && (
          <div className="mb-3 flex items-center justify-between rounded-lg border border-accent-red/40 bg-accent-red/10 px-4 py-2 text-sm text-accent-red">
            <span>{error}</span>
            <button onClick={clearError} className="text-accent-red/70 transition-colors hover:text-accent-red">
              <X size={16} />
            </button>
          </div>
        )}

        <HookStatsCards stats={stats} />

        <section className="mt-4 min-w-0 rounded-xl border border-dark-border bg-dark-surface p-4">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-dark-text">规则列表</h3>
            <span className="text-xs text-dark-muted">{rules.length} 条</span>
          </div>
          {loading && rules.length === 0 ? (
            <div className="flex h-[520px] items-center justify-center text-dark-muted">加载中...</div>
          ) : (
            <div className="min-h-[520px]">
              <HookRuleTable rules={rules} onEdit={(rule) => openForm(rule)} />
            </div>
          )}
        </section>
      </div>

      {formOpen && <HookRuleDrawer events={events} agents={agents} />}
    </div>
  );
}

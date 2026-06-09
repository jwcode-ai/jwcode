import { useEffect, useRef, useCallback } from 'react';
import { useHooksStore } from '../../stores/hooksStore';
import { HookRuleTable } from './HookRuleTable';
import { HookRuleDrawer } from './HookRuleDrawer';
import { HookStatsCards } from './HookStatsCards';
import { HookLogsPanel } from './HookLogsPanel';
import { LifecycleMappingPanel } from './LifecycleMappingPanel';
import { Plus, Upload, Download, RefreshCw } from 'lucide-react';

export function HookConfigView() {
  const {
    rules, stats, events, agents, loading, error,
    formOpen, logsExpanded,
    loadAll, loadLogs, openForm, clearError,
    setLogsExpanded,
  } = useHooksStore();

  const logsTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    loadAll();
  }, []);

  // Poll logs only when panel is expanded
  useEffect(() => {
    if (logsExpanded) {
      loadLogs();
      logsTimerRef.current = setInterval(() => loadLogs(), 5000);
      return () => {
        if (logsTimerRef.current) clearInterval(logsTimerRef.current);
      };
    }
    return () => {
      if (logsTimerRef.current) clearInterval(logsTimerRef.current);
    };
  }, [logsExpanded]);

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
        const mode = confirm('合并到现有配置？\n"确定"=合并 / "取消"=替换') ? 'merge' : 'replace';
        const res = await api.hooks.import({ ...data, mergeMode: mode });
        if (res.success) {
          loadAll();
        }
      } catch (err) {
        console.error('Import failed:', err);
      }
    };
    input.click();
  }, []);

  return (
    <div className="flex flex-col h-full p-4 gap-4 overflow-auto">
      {/* Toolbar */}
      <div className="flex items-center justify-between flex-shrink-0">
        <h2 className="text-lg font-semibold text-gray-100">Hook 配置</h2>
        <div className="flex items-center gap-2">
          <button
            onClick={loadAll}
            className="p-2 hover:bg-gray-700 rounded text-gray-400 transition"
            title="刷新"
          >
            <RefreshCw size={16} />
          </button>
          <button
            onClick={handleImport}
            className="flex items-center gap-1 px-3 py-1.5 text-sm bg-gray-700 hover:bg-gray-600 text-gray-200 rounded transition"
          >
            <Upload size={14} /> 导入
          </button>
          <button
            onClick={handleExport}
            className="flex items-center gap-1 px-3 py-1.5 text-sm bg-gray-700 hover:bg-gray-600 text-gray-200 rounded transition"
          >
            <Download size={14} /> 导出
          </button>
          <button
            onClick={() => openForm()}
            className="flex items-center gap-1 px-3 py-1.5 text-sm bg-blue-600 hover:bg-blue-500 text-white rounded transition"
          >
            <Plus size={14} /> 添加规则
          </button>
        </div>
      </div>

      {/* Error banner */}
      {error && (
        <div className="bg-red-900/50 border border-red-700 text-red-200 px-4 py-2 rounded flex items-center justify-between">
          <span>{error}</span>
          <button onClick={clearError} className="text-red-400 hover:text-red-300">×</button>
        </div>
      )}

      {/* Stats */}
      <HookStatsCards stats={stats} />

      {/* Rule table */}
      {loading ? (
        <div className="flex-1 flex items-center justify-center text-gray-500">加载中...</div>
      ) : (
        <HookRuleTable
          rules={rules}
          onEdit={(rule) => openForm(rule)}
        />
      )}

      {/* Lifecycle Mappings */}
      <LifecycleMappingPanel events={events} agents={agents} />

      {/* Logs */}
      <HookLogsPanel
        expanded={logsExpanded}
        onToggle={() => setLogsExpanded(!logsExpanded)}
      />

      {/* Form Drawer */}
      {formOpen && <HookRuleDrawer events={events} agents={agents} />}
    </div>
  );
}

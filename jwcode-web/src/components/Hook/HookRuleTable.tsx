import { useState } from 'react';
import { useHooksStore } from '../../stores/hooksStore';
import type { HookRule } from '../../types';
import { Edit2, Trash2, CheckSquare, Square } from 'lucide-react';

interface Props {
  rules: HookRule[];
  onEdit: (rule: HookRule) => void;
}

const PRIORITY_COLORS: Record<string, string> = {
  SYSTEM: 'bg-red-700 text-red-200',
  SECURITY: 'bg-orange-700 text-orange-200',
  PROJECT: 'bg-blue-700 text-blue-200',
  USER: 'bg-green-700 text-green-200',
  PLUGIN: 'bg-purple-700 text-purple-200',
};

const TYPE_COLORS: Record<string, string> = {
  SHELL: 'bg-yellow-700 text-yellow-200',
  HTTP: 'bg-cyan-700 text-cyan-200',
  PROMPT: 'bg-pink-700 text-pink-200',
  AGENT: 'bg-indigo-700 text-indigo-200',
};

export function HookRuleTable({ rules, onEdit }: Props) {
  const { toggleRule, deleteRule, batchDelete, batchToggle } = useHooksStore();
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [selectAll, setSelectAll] = useState(false);

  if (rules.length === 0) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center text-gray-500 py-16 gap-3">
        <div className="text-4xl">🪝</div>
        <p>暂无 Hook 规则</p>
        <p className="text-sm text-gray-600">点击"添加规则"创建第一个 Hook</p>
      </div>
    );
  }

  const toggleSelect = (name: string) => {
    const next = new Set(selected);
    next.has(name) ? next.delete(name) : next.add(name);
    setSelected(next);
    setSelectAll(next.size === rules.length);
  };

  const toggleAll = () => {
    if (selectAll) {
      setSelected(new Set());
      setSelectAll(false);
    } else {
      setSelected(new Set(rules.map(r => r.name)));
      setSelectAll(true);
    }
  };

  const handleBatchDelete = () => {
    if (selected.size === 0) return;
    if (confirm(`确认删除 ${selected.size} 条规则？`)) {
      batchDelete(Array.from(selected));
      setSelected(new Set());
      setSelectAll(false);
    }
  };

  const handleBatchToggle = (enabled: boolean) => {
    if (selected.size === 0) return;
    batchToggle(Array.from(selected), enabled);
    setSelected(new Set());
    setSelectAll(false);
  };

  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* Batch actions */}
      {selected.size > 0 && (
        <div className="flex items-center gap-2 mb-2 px-2 py-1.5 bg-gray-800 rounded text-sm">
          <span className="text-gray-400">已选 {selected.size} 项</span>
          <button onClick={() => handleBatchToggle(true)}
            className="px-2 py-0.5 bg-green-700 hover:bg-green-600 text-green-200 rounded">全部启用</button>
          <button onClick={() => handleBatchToggle(false)}
            className="px-2 py-0.5 bg-gray-600 hover:bg-gray-500 text-gray-200 rounded">全部禁用</button>
          <button onClick={handleBatchDelete}
            className="px-2 py-0.5 bg-red-700 hover:bg-red-600 text-red-200 rounded">删除</button>
        </div>
      )}
      <div className="flex-1 overflow-auto border border-gray-700 rounded">
        <table className="w-full text-sm">
          <thead className="bg-gray-800 text-gray-400 sticky top-0">
            <tr>
              <th className="p-2 w-8">
                <button onClick={toggleAll}>
                  {selectAll ? <CheckSquare size={14} /> : <Square size={14} />}
                </button>
              </th>
              <th className="p-2 text-left">名称</th>
              <th className="p-2 text-left">事件</th>
              <th className="p-2 text-left">类型</th>
              <th className="p-2 text-left">优先级</th>
              <th className="p-2 text-left">状态</th>
              <th className="p-2 text-right">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-700">
            {rules.map(rule => (
              <tr key={rule.name} className="hover:bg-gray-800/50 transition">
                <td className="p-2">
                  <button onClick={() => toggleSelect(rule.name)}>
                    {selected.has(rule.name)
                      ? <CheckSquare size={14} className="text-blue-400" />
                      : <Square size={14} className="text-gray-500" />}
                  </button>
                </td>
                <td className="p-2">
                  <div className="text-gray-200 font-medium">{rule.name}</div>
                  {rule.description && (
                    <div className="text-xs text-gray-500 truncate max-w-[200px]">{rule.description}</div>
                  )}
                </td>
                <td className="p-2">
                  <div className="flex flex-wrap gap-1">
                    {rule.events.slice(0, 3).map(e => (
                      <span key={e} className="px-1.5 py-0.5 bg-gray-700 text-gray-300 rounded text-xs">{e}</span>
                    ))}
                    {rule.events.length > 3 && (
                      <span className="px-1.5 py-0.5 text-xs text-gray-500">+{rule.events.length - 3}</span>
                    )}
                  </div>
                </td>
                <td className="p-2">
                  <span className={`px-1.5 py-0.5 rounded text-xs ${TYPE_COLORS[rule.implementationType] || 'bg-gray-700'}`}>
                    {rule.implementationType}
                  </span>
                </td>
                <td className="p-2">
                  <span className={`px-1.5 py-0.5 rounded text-xs ${PRIORITY_COLORS[rule.priority] || 'bg-gray-700'}`}>
                    {rule.priority}
                  </span>
                </td>
                <td className="p-2">
                  <button
                    onClick={() => toggleRule(rule.name, !rule.enabled)}
                    className={`w-9 h-5 rounded-full transition relative ${
                      rule.enabled ? 'bg-green-600' : 'bg-gray-600'
                    }`}
                  >
                    <span className={`block w-3.5 h-3.5 rounded-full bg-white absolute top-0.5 transition ${
                      rule.enabled ? 'left-[18px]' : 'left-[2px]'
                    }`} />
                  </button>
                </td>
                <td className="p-2 text-right">
                  <div className="flex items-center justify-end gap-1">
                    <button
                      onClick={() => onEdit(rule)}
                      className="p-1 hover:bg-gray-700 rounded text-gray-400 hover:text-blue-400"
                      title="编辑"
                    >
                      <Edit2 size={14} />
                    </button>
                    <button
                      onClick={() => { if (confirm(`删除规则 "${rule.name}"？`)) deleteRule(rule.name); }}
                      className="p-1 hover:bg-gray-700 rounded text-gray-400 hover:text-red-400"
                      title="删除"
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

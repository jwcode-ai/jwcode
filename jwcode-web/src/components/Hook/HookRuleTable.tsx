import { useState } from 'react';
import { useHooksStore } from '../../stores/hooksStore';
import type { HookRule } from '../../types';
import { Edit2, Trash2, CheckSquare, Square } from 'lucide-react';

interface Props {
  rules: HookRule[];
  onEdit: (rule: HookRule) => void;
}

// 语义化徽章：背景用 accent 透明色，文字用 accent 实色，自动适配明暗主题
const PRIORITY_BADGE: Record<string, string> = {
  SYSTEM: 'bg-accent-red/15 text-accent-red',
  SECURITY: 'bg-accent-orange/15 text-accent-orange',
  PROJECT: 'bg-accent-blue/15 text-accent-blue',
  USER: 'bg-accent-green/15 text-accent-green',
  PLUGIN: 'bg-accent-purple/15 text-accent-purple',
};

const TYPE_BADGE: Record<string, string> = {
  SHELL: 'bg-accent-yellow/15 text-accent-yellow',
  HTTP: 'bg-accent-cyan/15 text-accent-cyan',
  PROMPT: 'bg-accent-purple/15 text-accent-purple',
  AGENT: 'bg-accent-blue/15 text-accent-blue',
};

export function HookRuleTable({ rules, onEdit }: Props) {
  const { toggleRule, deleteRule, batchDelete, batchToggle } = useHooksStore();
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [selectAll, setSelectAll] = useState(false);

  if (rules.length === 0) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center gap-3 py-16 text-dark-muted">
        <div className="text-4xl opacity-60">🪝</div>
        <p className="text-sm">暂无 Hook 规则</p>
        <p className="text-xs text-dark-muted/70">点击右上角"新增规则"创建第一个 Hook</p>
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
    <div className="flex flex-1 flex-col min-h-0">
      {/* Batch actions */}
      {selected.size > 0 && (
        <div className="mb-2 flex items-center gap-2 rounded-lg border border-accent-blue/30 bg-accent-blue/5 px-3 py-1.5 text-sm">
          <span className="text-dark-muted">已选 {selected.size} 项</span>
          <button
            onClick={() => handleBatchToggle(true)}
            className="rounded bg-accent-green/15 px-2 py-0.5 text-accent-green transition-colors hover:bg-accent-green/25"
          >
            全部启用
          </button>
          <button
            onClick={() => handleBatchToggle(false)}
            className="rounded bg-dark-hover px-2 py-0.5 text-dark-text transition-colors hover:bg-dark-border"
          >
            全部禁用
          </button>
          <button
            onClick={handleBatchDelete}
            className="rounded bg-accent-red/15 px-2 py-0.5 text-accent-red transition-colors hover:bg-accent-red/25"
          >
            删除
          </button>
        </div>
      )}
      <div className="flex-1 overflow-auto rounded-xl border border-dark-border">
        <table className="w-full text-sm">
          <thead className="sticky top-0 bg-dark-hover text-dark-muted">
            <tr>
              <th className="w-8 p-2.5">
                <button onClick={toggleAll}>
                  {selectAll ? <CheckSquare size={14} /> : <Square size={14} />}
                </button>
              </th>
              <th className="p-2.5 text-left font-medium">名称</th>
              <th className="p-2.5 text-left font-medium">事件</th>
              <th className="p-2.5 text-left font-medium">类型</th>
              <th className="p-2.5 text-left font-medium">优先级</th>
              <th className="p-2.5 text-left font-medium">状态</th>
              <th className="p-2.5 text-right font-medium">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-dark-border">
            {rules.map(rule => (
              <tr key={rule.name} className="transition-colors hover:bg-dark-hover/50">
                <td className="p-2.5">
                  <button onClick={() => toggleSelect(rule.name)}>
                    {selected.has(rule.name)
                      ? <CheckSquare size={14} className="text-accent-blue" />
                      : <Square size={14} className="text-dark-muted" />}
                  </button>
                </td>
                <td className="p-2.5">
                  <div className="font-medium text-dark-text">{rule.name}</div>
                  {rule.description && (
                    <div className="mt-0.5 max-w-[220px] truncate text-xs text-dark-muted">{rule.description}</div>
                  )}
                </td>
                <td className="p-2.5">
                  <div className="flex flex-wrap gap-1">
                    {rule.events.slice(0, 3).map(e => (
                      <span key={e} className="rounded bg-dark-hover px-1.5 py-0.5 text-xs text-dark-text">{e}</span>
                    ))}
                    {rule.events.length > 3 && (
                      <span className="px-1 py-0.5 text-xs text-dark-muted">+{rule.events.length - 3}</span>
                    )}
                  </div>
                </td>
                <td className="p-2.5">
                  <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${TYPE_BADGE[rule.implementationType] || 'bg-dark-hover text-dark-muted'}`}>
                    {rule.implementationType}
                  </span>
                </td>
                <td className="p-2.5">
                  <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${PRIORITY_BADGE[rule.priority] || 'bg-dark-hover text-dark-muted'}`}>
                    {rule.priority}
                  </span>
                </td>
                <td className="p-2.5">
                  <button
                    onClick={() => toggleRule(rule.name, !rule.enabled)}
                    className={`relative h-5 w-9 rounded-full transition-colors ${
                      rule.enabled ? 'bg-accent-green' : 'bg-dark-border'
                    }`}
                    title={rule.enabled ? '点击禁用' : '点击启用'}
                  >
                    <span
                      className={`absolute top-0.5 block h-3.5 w-3.5 rounded-full bg-white transition-all ${
                        rule.enabled ? 'left-[18px]' : 'left-[2px]'
                      }`}
                    />
                  </button>
                </td>
                <td className="p-2.5">
                  <div className="flex items-center justify-end gap-1">
                    <button
                      onClick={() => onEdit(rule)}
                      className="rounded p-1 text-dark-muted transition-colors hover:bg-dark-hover hover:text-accent-blue"
                      title="编辑"
                    >
                      <Edit2 size={14} />
                    </button>
                    <button
                      onClick={() => { if (confirm(`删除规则 "${rule.name}"？`)) deleteRule(rule.name); }}
                      className="rounded p-1 text-dark-muted transition-colors hover:bg-dark-hover hover:text-accent-red"
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

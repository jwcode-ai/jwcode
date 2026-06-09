import { useState, useEffect } from 'react';
import { useHooksStore } from '../../stores/hooksStore';
import type { HookRule, HookRuleFormData, HookEventCategory, HookAgentInfo } from '../../types';
import { ChevronLeft, ChevronRight, Play } from 'lucide-react';

interface Props {
  events: HookEventCategory[];
  agents: HookAgentInfo[];
}

const EMPTY_FORM: HookRuleFormData = {
  name: '', description: '', events: [], implementationType: 'SHELL',
  command: '', url: '', promptTemplate: '', agentName: '',
  priority: 'USER', tools: [], matcherToolNamePattern: '',
  matcherFromState: '', matcherToState: '',
  timeoutMs: 30000, enabled: true, failOpen: true, scope: 'project',
};

const IMPL_TYPE_OPTIONS = [
  { value: 'SHELL' as const, label: 'Shell 脚本', desc: '执行外部脚本，stdin 接收 JSON，stdout 返回决策' },
  { value: 'HTTP' as const, label: 'HTTP 端点', desc: 'POST JSON 到外部端点' },
  { value: 'PROMPT' as const, label: 'LLM Prompt', desc: '发送单轮 Prompt 给 LLM 做风险评估' },
  { value: 'AGENT' as const, label: 'AI Agent', desc: '启动只读子代理做深度调查' },
];

const PRIORITY_OPTIONS = [
  { value: 'SYSTEM' as const, label: 'SYSTEM (100)', color: 'text-red-400' },
  { value: 'SECURITY' as const, label: 'SECURITY (80)', color: 'text-orange-400' },
  { value: 'PROJECT' as const, label: 'PROJECT (60)', color: 'text-blue-400' },
  { value: 'USER' as const, label: 'USER (40)', color: 'text-green-400' },
  { value: 'PLUGIN' as const, label: 'PLUGIN (20)', color: 'text-purple-400' },
];

export function HookRuleDrawer({ events, agents }: Props) {
  const {
    editingRule, fieldErrors, dryRunResult,
    createRule, updateRule, closeForm, dryRun, setDryRunResult, clearFieldErrors,
  } = useHooksStore();

  const [step, setStep] = useState(0);
  const [form, setForm] = useState<HookRuleFormData>(EMPTY_FORM);
  const [eventSearch, setEventSearch] = useState('');

  useEffect(() => {
    if (editingRule) {
      setForm({
        name: editingRule.name,
        description: editingRule.description || '',
        events: editingRule.events,
        implementationType: editingRule.implementationType,
        command: editingRule.command || '',
        url: editingRule.url || '',
        promptTemplate: editingRule.promptTemplate || '',
        agentName: editingRule.agentName || '',
        priority: editingRule.priority,
        tools: editingRule.tools || [],
        matcherToolNamePattern: editingRule.matchers?.toolNamePattern || '',
        matcherFromState: editingRule.matchers?.fromState || '',
        matcherToState: editingRule.matchers?.toState || '',
        timeoutMs: editingRule.timeoutMs,
        enabled: editingRule.enabled,
        failOpen: editingRule.failOpen,
        scope: editingRule.scope || 'project',
      });
    }
  }, [editingRule]);

  const update = <K extends keyof HookRuleFormData>(field: K, value: HookRuleFormData[K]) => {
    setForm(prev => ({ ...prev, [field]: value }));
    clearFieldErrors();
  };

  const toggleEvent = (eventName: string) => {
    setForm(prev => ({
      ...prev,
      events: prev.events.includes(eventName)
        ? prev.events.filter(e => e !== eventName)
        : [...prev.events, eventName],
    }));
  };

  const toggleAllEvents = (catEvents: string[]) => {
    const allSelected = catEvents.every(e => form.events.includes(e));
    if (allSelected) {
      setForm(prev => ({ ...prev, events: prev.events.filter(e => !catEvents.includes(e)) }));
    } else {
      setForm(prev => ({
        ...prev,
        events: [...new Set([...prev.events, ...catEvents])],
      }));
    }
  };

  const handleSubmit = async () => {
    // Build matchers from flat fields
    const matchers: Record<string, string> = {};
    if (form.matcherToolNamePattern) matchers.toolNamePattern = form.matcherToolNamePattern;
    if (form.matcherFromState) matchers.fromState = form.matcherFromState;
    if (form.matcherToState) matchers.toState = form.matcherToState;

    const data: HookRuleFormData = {
      ...form,
      tools: form.tools.length > 0 ? form.tools : [],
    };

    // Attach matchers to the form (cast needed since HookRuleFormData has flat fields)
    const payload = { ...data } as Record<string, unknown>;
    payload.matchers = matchers;
    delete payload.matcherToolNamePattern;
    delete payload.matcherFromState;
    delete payload.matcherToState;

    if (editingRule) {
      await updateRule(editingRule.name, payload as unknown as HookRuleFormData);
    } else {
      await createRule(payload as unknown as HookRuleFormData);
    }
  };

  const handleDryRun = async () => {
    setDryRunResult(null);
    const matchers: Record<string, string> = {};
    if (form.matcherToolNamePattern) matchers.toolNamePattern = form.matcherToolNamePattern;
    await dryRun({
      hook: {
        name: form.name || 'dry-run',
        description: form.description,
        events: form.events,
        implementationType: form.implementationType,
        command: form.command,
        url: form.url,
        promptTemplate: form.promptTemplate,
        agentName: form.agentName,
        priority: form.priority,
        tools: form.tools,
        matchers,
        timeoutMs: form.timeoutMs,
        enabled: true,
        failOpen: form.failOpen,
        scope: form.scope,
      } as HookRule,
      eventType: form.events[0] || 'PRE_TOOL_USE',
      toolName: '',
      toolInput: '',
    });
  };

  const filteredEvents = eventSearch
    ? events.map(cat => ({
        ...cat,
        events: cat.events.filter(e =>
          e.name.toLowerCase().includes(eventSearch.toLowerCase()) ||
          e.summary.includes(eventSearch)
        ),
      })).filter(cat => cat.events.length > 0)
    : events;

  const steps = [
    { title: '基本信息', desc: '名称、描述、事件' },
    { title: '实现配置', desc: '执行方式与参数' },
    { title: '高级选项', desc: '优先级、过滤、测试' },
  ];

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div className="absolute inset-0 bg-black/50" onClick={closeForm} />
      <div className="relative w-[560px] h-full bg-gray-900 border-l border-gray-700 flex flex-col shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-gray-700 flex-shrink-0">
          <h3 className="text-lg font-semibold text-gray-100">
            {editingRule ? `编辑: ${editingRule.name}` : '添加 Hook 规则'}
          </h3>
          <button onClick={closeForm}
            className="p-1 hover:bg-gray-700 rounded text-gray-400">×</button>
        </div>

        {/* Stepper */}
        <div className="flex border-b border-gray-700 flex-shrink-0">
          {steps.map((s, i) => (
            <button
              key={i}
              onClick={() => setStep(i)}
              className={`flex-1 p-2 text-center text-xs transition border-b-2 ${
                i === step
                  ? 'border-blue-500 text-blue-400 bg-blue-900/30'
                  : 'border-transparent text-gray-500 hover:text-gray-400'
              }`}
            >
              <div className="font-medium">{s.title}</div>
            </button>
          ))}
        </div>

        {/* Form body */}
        <div className="flex-1 overflow-auto p-4 space-y-4">
          {/* Field error */}
          {fieldErrors && Object.keys(fieldErrors).length > 0 && (
            <div className="bg-red-900/30 border border-red-700 rounded p-2 text-xs text-red-300">
              {Object.entries(fieldErrors).map(([k, v]) => (
                <div key={k}><strong>{k}:</strong> {v}</div>
              ))}
            </div>
          )}

          {/* Step 0: Basic Info */}
          {step === 0 && (
            <div className="space-y-4">
              <div>
                <label className="block text-xs text-gray-400 mb-1">名称 *</label>
                <input value={form.name} onChange={e => update('name', e.target.value)}
                  placeholder="my-custom-hook"
                  className={`w-full bg-gray-800 border ${fieldErrors?.name ? 'border-red-500' : 'border-gray-600'} rounded px-3 py-2 text-sm text-gray-200`} />
              </div>
              <div>
                <label className="block text-xs text-gray-400 mb-1">描述</label>
                <textarea value={form.description} onChange={e => update('description', e.target.value)}
                  placeholder="这个 Hook 的作用..."
                  rows={2}
                  className="w-full bg-gray-800 border border-gray-600 rounded px-3 py-2 text-sm text-gray-200" />
              </div>
              <div>
                <label className="block text-xs text-gray-400 mb-1">Scope</label>
                <select value={form.scope} onChange={e => update('scope', e.target.value as HookRuleFormData['scope'])}
                  className="w-full bg-gray-800 border border-gray-600 rounded px-3 py-2 text-sm text-gray-200">
                  <option value="project">project (.jwcode/hooks.json)</option>
                  <option value="user">user (~/.jwcode/hooks.json)</option>
                  <option value="local">local (.jwcode/hooks.local.json)</option>
                </select>
              </div>
              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="text-xs text-gray-400">事件 * <span className="text-blue-400">({form.events.length} 已选)</span></label>
                </div>
                <input value={eventSearch} onChange={e => setEventSearch(e.target.value)}
                  placeholder="搜索事件..."
                  className="w-full bg-gray-800 border border-gray-600 rounded px-3 py-1.5 text-sm text-gray-200 mb-2" />
                <div className="max-h-[280px] overflow-auto space-y-2 border border-gray-700 rounded p-2">
                  {filteredEvents.map(cat => (
                    <div key={cat.category}>
                      <button
                        onClick={() => toggleAllEvents(cat.events.map(e => e.name))}
                        className="text-xs text-gray-500 hover:text-gray-300 font-medium mb-1"
                      >
                        {cat.category} — 全选/取消
                      </button>
                      {cat.events.map(evt => (
                        <label key={evt.name} className="flex items-center gap-2 py-1 px-1 hover:bg-gray-800 rounded cursor-pointer">
                          <input
                            type="checkbox"
                            checked={form.events.includes(evt.name)}
                            onChange={() => toggleEvent(evt.name)}
                            className="w-3.5 h-3.5"
                          />
                          <span className="text-xs text-gray-300">{evt.name}</span>
                          <span className="text-xs text-gray-600">— {evt.summary}</span>
                        </label>
                      ))}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* Step 1: Implementation */}
          {step === 1 && (
            <div className="space-y-4">
              <div>
                <label className="block text-xs text-gray-400 mb-2">实现类型 *</label>
                <div className="grid grid-cols-2 gap-2">
                  {IMPL_TYPE_OPTIONS.map(opt => (
                    <button
                      key={opt.value}
                      onClick={() => update('implementationType', opt.value)}
                      className={`p-3 rounded border text-left transition ${
                        form.implementationType === opt.value
                          ? 'border-blue-500 bg-blue-900/30'
                          : 'border-gray-700 bg-gray-800 hover:border-gray-600'
                      }`}
                    >
                      <div className="text-sm font-medium text-gray-200">{opt.label}</div>
                      <div className="text-xs text-gray-500 mt-1">{opt.desc}</div>
                    </button>
                  ))}
                </div>
              </div>
              {form.implementationType === 'SHELL' && (
                <div>
                  <label className="block text-xs text-gray-400 mb-1">命令 *</label>
                  <input value={form.command} onChange={e => update('command', e.target.value)}
                    placeholder="python3 .jwcode/hooks/audit.py"
                    className={`w-full bg-gray-800 border ${fieldErrors?.command ? 'border-red-500' : 'border-gray-600'} rounded px-3 py-2 text-sm text-gray-200`} />
                </div>
              )}
              {form.implementationType === 'HTTP' && (
                <div>
                  <label className="block text-xs text-gray-400 mb-1">URL *</label>
                  <input value={form.url} onChange={e => update('url', e.target.value)}
                    placeholder="https://example.com/hooks/audit"
                    className={`w-full bg-gray-800 border ${fieldErrors?.url ? 'border-red-500' : 'border-gray-600'} rounded px-3 py-2 text-sm text-gray-200`} />
                </div>
              )}
              {form.implementationType === 'PROMPT' && (
                <div>
                  <label className="block text-xs text-gray-400 mb-1">Prompt 模板 *</label>
                  <textarea value={form.promptTemplate} onChange={e => update('promptTemplate', e.target.value)}
                    placeholder="你是一个安全审计专家，请分析以下工具调用..."
                    rows={5}
                    className={`w-full bg-gray-800 border ${fieldErrors?.promptTemplate ? 'border-red-500' : 'border-gray-600'} rounded px-3 py-2 text-sm text-gray-200 font-mono`} />
                </div>
              )}
              {form.implementationType === 'AGENT' && (
                <div>
                  <label className="block text-xs text-gray-400 mb-1">Agent *</label>
                  <select value={form.agentName} onChange={e => update('agentName', e.target.value)}
                    className={`w-full bg-gray-800 border ${fieldErrors?.agentName ? 'border-red-500' : 'border-gray-600'} rounded px-3 py-2 text-sm text-gray-200`}>
                    <option value="">选择 Agent...</option>
                    {agents.map(a => (
                      <option key={a.id} value={a.id}>{a.name} ({a.id})</option>
                    ))}
                  </select>
                  <p className="text-xs text-gray-600 mt-1">Agent 将以只读模式运行（Read/Grep/Glob），不会修改文件</p>
                </div>
              )}
            </div>
          )}

          {/* Step 2: Advanced */}
          {step === 2 && (
            <div className="space-y-4">
              <div>
                <label className="block text-xs text-gray-400 mb-1">优先级</label>
                <select value={form.priority} onChange={e => update('priority', e.target.value as HookRuleFormData['priority'])}
                  className="w-full bg-gray-800 border border-gray-600 rounded px-3 py-2 text-sm text-gray-200">
                  {PRIORITY_OPTIONS.map(opt => (
                    <option key={opt.value} value={opt.value}>{opt.label}</option>
                  ))}
                </select>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs text-gray-400 mb-1">超时 (ms)</label>
                  <input type="number" value={form.timeoutMs}
                    onChange={e => update('timeoutMs', parseInt(e.target.value) || 30000)}
                    min={100} max={300000} step={1000}
                    className="w-full bg-gray-800 border border-gray-600 rounded px-3 py-2 text-sm text-gray-200" />
                </div>
                <div className="flex flex-col gap-3">
                  <label className="flex items-center gap-2 text-sm text-gray-300">
                    <input type="checkbox" checked={form.enabled}
                      onChange={e => update('enabled', e.target.checked)} />
                    启用
                  </label>
                  <label className="flex items-center gap-2 text-sm text-gray-300">
                    <input type="checkbox" checked={form.failOpen}
                      onChange={e => update('failOpen', e.target.checked)} />
                    Fail-open（超时放行）
                  </label>
                </div>
              </div>
              <div>
                <label className="block text-xs text-gray-400 mb-1">工具过滤（正则，可选）</label>
                <input value={form.matcherToolNamePattern} onChange={e => update('matcherToolNamePattern', e.target.value)}
                  placeholder="Bash.*|FileWrite.*"
                  className="w-full bg-gray-800 border border-gray-600 rounded px-3 py-2 text-sm text-gray-200" />
              </div>
              {form.events.some(e => e.includes('STATE')) && (
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs text-gray-400 mb-1">From State</label>
                    <input value={form.matcherFromState} onChange={e => update('matcherFromState', e.target.value)}
                      className="w-full bg-gray-800 border border-gray-600 rounded px-3 py-2 text-sm text-gray-200" />
                  </div>
                  <div>
                    <label className="block text-xs text-gray-400 mb-1">To State</label>
                    <input value={form.matcherToState} onChange={e => update('matcherToState', e.target.value)}
                      className="w-full bg-gray-800 border border-gray-600 rounded px-3 py-2 text-sm text-gray-200" />
                  </div>
                </div>
              )}

              {/* Dry-run result */}
              {dryRunResult && (
                <div className="bg-gray-800 border border-gray-700 rounded p-3">
                  <div className="text-xs font-medium text-gray-300 mb-2">Dry-run 结果</div>
                  <div className="text-xs space-y-1">
                    <div><span className="text-gray-500">决策:</span> <span className={
                      dryRunResult.decision === 'ALLOW' ? 'text-green-400' :
                      dryRunResult.decision === 'DENY' ? 'text-red-400' : 'text-yellow-400'
                    }>{dryRunResult.decision}</span></div>
                    <div><span className="text-gray-500">原因:</span> <span className="text-gray-300">{dryRunResult.reason}</span></div>
                    {dryRunResult.contextOutput && (
                      <div><span className="text-gray-500">输出:</span> <span className="text-gray-300">{dryRunResult.contextOutput}</span></div>
                    )}
                  </div>
                </div>
              )}

              <button onClick={handleDryRun}
                className="flex items-center gap-1 px-3 py-1.5 text-xs bg-gray-700 hover:bg-gray-600 text-gray-300 rounded transition">
                <Play size={12} /> 试运行 (Dry Run)
              </button>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between p-4 border-t border-gray-700 flex-shrink-0">
          <div className="flex gap-2">
            {step > 0 && (
              <button onClick={() => setStep(step - 1)}
                className="flex items-center gap-1 px-3 py-1.5 text-sm bg-gray-700 hover:bg-gray-600 text-gray-300 rounded">
                <ChevronLeft size={14} /> 上一步
              </button>
            )}
          </div>
          <div className="flex gap-2">
            <button onClick={closeForm}
              className="px-4 py-1.5 text-sm bg-gray-700 hover:bg-gray-600 text-gray-300 rounded">
              取消
            </button>
            {step < 2 ? (
              <button onClick={() => setStep(step + 1)}
                className="flex items-center gap-1 px-4 py-1.5 text-sm bg-blue-600 hover:bg-blue-500 text-white rounded">
                下一步 <ChevronRight size={14} />
              </button>
            ) : (
              <button onClick={handleSubmit}
                className="px-4 py-1.5 text-sm bg-blue-600 hover:bg-blue-500 text-white rounded">
                {editingRule ? '保存修改' : '创建规则'}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

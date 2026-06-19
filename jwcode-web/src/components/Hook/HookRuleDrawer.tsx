import { useState, useEffect } from 'react';
import { useHooksStore } from '../../stores/hooksStore';
import type { HookRule, HookRuleFormData, HookEventCategory, HookAgentInfo } from '../../types';
import { ChevronLeft, ChevronRight, Play, X } from 'lucide-react';

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
  { value: 'SYSTEM' as const, label: 'SYSTEM (100)' },
  { value: 'SECURITY' as const, label: 'SECURITY (80)' },
  { value: 'PROJECT' as const, label: 'PROJECT (60)' },
  { value: 'USER' as const, label: 'USER (40)' },
  { value: 'PLUGIN' as const, label: 'PLUGIN (20)' },
];

const inputClass = (hasError?: unknown) =>
  `w-full rounded-lg border bg-dark-bg px-3 py-2 text-sm text-dark-text placeholder-dark-muted/60 transition-colors focus:outline-none focus:ring-2 ${
    hasError
      ? 'border-accent-red focus:border-accent-red focus:ring-accent-red/20'
      : 'border-dark-border focus:border-accent-blue focus:ring-accent-blue/20'
  }`;

const labelClass = 'mb-1 block text-xs font-medium text-dark-muted';

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
    const matchers: Record<string, string> = {};
    if (form.matcherToolNamePattern) matchers.toolNamePattern = form.matcherToolNamePattern;
    if (form.matcherFromState) matchers.fromState = form.matcherFromState;
    if (form.matcherToState) matchers.toState = form.matcherToState;

    const data: HookRuleFormData = {
      ...form,
      tools: form.tools.length > 0 ? form.tools : [],
    };

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
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={closeForm} />
      <div className="relative flex h-full w-[560px] flex-col border-l border-dark-border bg-dark-bg shadow-2xl animate-slide-in-right">
        {/* Header */}
        <div className="flex flex-shrink-0 items-center justify-between border-b border-dark-border px-4 py-3.5">
          <h3 className="text-base font-semibold text-dark-text">
            {editingRule ? `编辑: ${editingRule.name}` : '添加 Hook 规则'}
          </h3>
          <button
            onClick={closeForm}
            className="rounded-lg p-1 text-dark-muted transition-colors hover:bg-dark-hover hover:text-dark-text"
          >
            <X size={18} />
          </button>
        </div>

        {/* Stepper */}
        <div className="flex flex-shrink-0 border-b border-dark-border">
          {steps.map((s, i) => {
            const active = i === step;
            const done = i < step;
            return (
              <button
                key={i}
                onClick={() => setStep(i)}
                className={`relative flex-1 border-b-2 px-2 py-2.5 text-center text-xs transition-colors ${
                  active
                    ? 'border-accent-blue bg-accent-blue/5 text-accent-blue'
                    : done
                      ? 'border-transparent text-dark-text'
                      : 'border-transparent text-dark-muted hover:text-dark-text'
                }`}
              >
                <span className={`mr-1 inline-flex h-4 w-4 items-center justify-center rounded-full text-[10px] ${
                  active ? 'bg-accent-blue text-white' : done ? 'bg-accent-green text-white' : 'bg-dark-hover text-dark-muted'
                }`}>
                  {done ? '✓' : i + 1}
                </span>
                <span className="font-medium">{s.title}</span>
              </button>
            );
          })}
        </div>

        {/* Form body */}
        <div className="flex-1 space-y-4 overflow-auto p-4">
          {fieldErrors && Object.keys(fieldErrors).length > 0 && (
            <div className="rounded-lg border border-accent-red/40 bg-accent-red/10 p-2.5 text-xs text-accent-red">
              {Object.entries(fieldErrors).map(([k, v]) => (
                <div key={k}><strong>{k}:</strong> {v}</div>
              ))}
            </div>
          )}

          {/* Step 0: Basic Info */}
          {step === 0 && (
            <div className="space-y-4">
              <div>
                <label className={labelClass}>名称 *</label>
                <input
                  value={form.name}
                  onChange={e => update('name', e.target.value)}
                  placeholder="my-custom-hook"
                  className={inputClass(fieldErrors?.name)}
                />
              </div>
              <div>
                <label className={labelClass}>描述</label>
                <textarea
                  value={form.description}
                  onChange={e => update('description', e.target.value)}
                  placeholder="这个 Hook 的作用..."
                  rows={2}
                  className={inputClass()}
                />
              </div>
              <div>
                <label className={labelClass}>Scope</label>
                <select
                  value={form.scope}
                  onChange={e => update('scope', e.target.value as HookRuleFormData['scope'])}
                  className={inputClass()}
                >
                  <option value="project">project (.jwcode/hooks.json)</option>
                  <option value="user">user (~/.jwcode/hooks.json)</option>
                  <option value="local">local (.jwcode/hooks.local.json)</option>
                </select>
              </div>
              <div>
                <div className="mb-1 flex items-center justify-between">
                  <label className="text-xs font-medium text-dark-muted">事件 *</label>
                  <span className="text-xs text-accent-blue">{form.events.length} 已选</span>
                </div>
                <input
                  value={eventSearch}
                  onChange={e => setEventSearch(e.target.value)}
                  placeholder="搜索事件..."
                  className={inputClass() + ' mb-2'}
                />
                <div className="max-h-[280px] space-y-2 overflow-auto rounded-lg border border-dark-border bg-dark-surface p-2">
                  {filteredEvents.map(cat => (
                    <div key={cat.category}>
                      <button
                        onClick={() => toggleAllEvents(cat.events.map(e => e.name))}
                        className="mb-1 text-xs font-medium text-dark-muted transition-colors hover:text-dark-text"
                      >
                        {cat.category} — 全选/取消
                      </button>
                      {cat.events.map(evt => {
                        const checked = form.events.includes(evt.name);
                        return (
                          <label
                            key={evt.name}
                            className="flex cursor-pointer items-center gap-2 rounded px-1 py-1 transition-colors hover:bg-dark-hover"
                          >
                            <input
                              type="checkbox"
                              checked={checked}
                              onChange={() => toggleEvent(evt.name)}
                              className="h-3.5 w-3.5 accent-accent-blue"
                            />
                            <span className="text-xs text-dark-text">{evt.name}</span>
                            <span className="truncate text-xs text-dark-muted">— {evt.summary}</span>
                          </label>
                        );
                      })}
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
                <label className={labelClass}>实现类型 *</label>
                <div className="grid grid-cols-2 gap-2">
                  {IMPL_TYPE_OPTIONS.map(opt => {
                    const active = form.implementationType === opt.value;
                    return (
                      <button
                        key={opt.value}
                        onClick={() => update('implementationType', opt.value)}
                        className={`rounded-lg border p-3 text-left transition-colors ${
                          active
                            ? 'border-accent-blue bg-accent-blue/10 ring-1 ring-accent-blue/30'
                            : 'border-dark-border bg-dark-surface hover:bg-dark-hover'
                        }`}
                      >
                        <div className={`text-sm font-medium ${active ? 'text-accent-blue' : 'text-dark-text'}`}>{opt.label}</div>
                        <div className="mt-1 text-xs text-dark-muted">{opt.desc}</div>
                      </button>
                    );
                  })}
                </div>
              </div>
              {form.implementationType === 'SHELL' && (
                <div>
                  <label className={labelClass}>命令 *</label>
                  <input
                    value={form.command}
                    onChange={e => update('command', e.target.value)}
                    placeholder="python3 .jwcode/hooks/audit.py"
                    className={`font-mono ${inputClass(fieldErrors?.command)}`}
                  />
                </div>
              )}
              {form.implementationType === 'HTTP' && (
                <div>
                  <label className={labelClass}>URL *</label>
                  <input
                    value={form.url}
                    onChange={e => update('url', e.target.value)}
                    placeholder="https://example.com/hooks/audit"
                    className={inputClass(fieldErrors?.url)}
                  />
                </div>
              )}
              {form.implementationType === 'PROMPT' && (
                <div>
                  <label className={labelClass}>Prompt 模板 *</label>
                  <textarea
                    value={form.promptTemplate}
                    onChange={e => update('promptTemplate', e.target.value)}
                    placeholder="你是一个安全审计专家，请分析以下工具调用..."
                    rows={5}
                    className={`font-mono ${inputClass(fieldErrors?.promptTemplate)}`}
                  />
                </div>
              )}
              {form.implementationType === 'AGENT' && (
                <div>
                  <label className={labelClass}>Agent *</label>
                  <select
                    value={form.agentName}
                    onChange={e => update('agentName', e.target.value)}
                    className={inputClass(fieldErrors?.agentName)}
                  >
                    <option value="">选择 Agent...</option>
                    {agents.map(a => (
                      <option key={a.id} value={a.id}>{a.name} ({a.description})</option>
                    ))}
                  </select>
                  <p className="mt-1 text-xs text-dark-muted">Agent 将以只读模式运行（Read/Grep/Glob），不会修改文件</p>
                </div>
              )}
            </div>
          )}

          {/* Step 2: Advanced */}
          {step === 2 && (
            <div className="space-y-4">
              <div>
                <label className={labelClass}>优先级</label>
                <select
                  value={form.priority}
                  onChange={e => update('priority', e.target.value as HookRuleFormData['priority'])}
                  className={inputClass()}
                >
                  {PRIORITY_OPTIONS.map(opt => (
                    <option key={opt.value} value={opt.value}>{opt.label}</option>
                  ))}
                </select>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className={labelClass}>超时 (ms)</label>
                  <input
                    type="number"
                    value={form.timeoutMs}
                    onChange={e => update('timeoutMs', parseInt(e.target.value) || 30000)}
                    min={100}
                    max={300000}
                    step={1000}
                    className={inputClass()}
                  />
                </div>
                <div className="flex flex-col gap-2.5">
                  <label className="flex items-center gap-2 text-sm text-dark-text">
                    <input
                      type="checkbox"
                      checked={form.enabled}
                      onChange={e => update('enabled', e.target.checked)}
                      className="h-3.5 w-3.5 accent-accent-blue"
                    />
                    启用
                  </label>
                  <label className="flex items-center gap-2 text-sm text-dark-text">
                    <input
                      type="checkbox"
                      checked={form.failOpen}
                      onChange={e => update('failOpen', e.target.checked)}
                      className="h-3.5 w-3.5 accent-accent-blue"
                    />
                    Fail-open（超时放行）
                  </label>
                </div>
              </div>
              <div>
                <label className={labelClass}>工具过滤（正则，可选）</label>
                <input
                  value={form.matcherToolNamePattern}
                  onChange={e => update('matcherToolNamePattern', e.target.value)}
                  placeholder="Bash.*|FileWrite.*"
                  className={`font-mono ${inputClass()}`}
                />
              </div>
              {form.events.some(e => e.includes('STATE')) && (
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className={labelClass}>From State</label>
                    <input
                      value={form.matcherFromState}
                      onChange={e => update('matcherFromState', e.target.value)}
                      className={inputClass()}
                    />
                  </div>
                  <div>
                    <label className={labelClass}>To State</label>
                    <input
                      value={form.matcherToState}
                      onChange={e => update('matcherToState', e.target.value)}
                      className={inputClass()}
                    />
                  </div>
                </div>
              )}

              {/* Dry-run result */}
              {dryRunResult && (
                <div className="rounded-lg border border-dark-border bg-dark-surface p-3">
                  <div className="mb-2 text-xs font-medium text-dark-text">Dry-run 结果</div>
                  <div className="space-y-1 text-xs">
                    <div>
                      <span className="text-dark-muted">决策: </span>
                      <span className={
                        dryRunResult.decision === 'ALLOW' ? 'text-accent-green' :
                        dryRunResult.decision === 'DENY' ? 'text-accent-red' : 'text-accent-yellow'
                      }>{dryRunResult.decision}</span>
                    </div>
                    <div><span className="text-dark-muted">原因: </span><span className="text-dark-text">{dryRunResult.reason}</span></div>
                    {dryRunResult.contextOutput && (
                      <div><span className="text-dark-muted">输出: </span><span className="text-dark-text">{dryRunResult.contextOutput}</span></div>
                    )}
                  </div>
                </div>
              )}

              <button
                onClick={handleDryRun}
                className="inline-flex items-center gap-1.5 rounded-lg border border-dark-border bg-dark-surface px-3 py-1.5 text-xs text-dark-text transition-colors hover:bg-dark-hover"
              >
                <Play size={12} /> 试运行 (Dry Run)
              </button>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex flex-shrink-0 items-center justify-between border-t border-dark-border px-4 py-3">
          <div className="flex gap-2">
            {step > 0 && (
              <button
                onClick={() => setStep(step - 1)}
                className="inline-flex items-center gap-1 rounded-lg border border-dark-border bg-dark-surface px-3 py-1.5 text-sm text-dark-text transition-colors hover:bg-dark-hover"
              >
                <ChevronLeft size={14} /> 上一步
              </button>
            )}
          </div>
          <div className="flex gap-2">
            <button
              onClick={closeForm}
              className="rounded-lg border border-dark-border bg-dark-surface px-4 py-1.5 text-sm text-dark-text transition-colors hover:bg-dark-hover"
            >
              取消
            </button>
            {step < 2 ? (
              <button
                onClick={() => setStep(step + 1)}
                className="inline-flex items-center gap-1 rounded-lg bg-accent-blue px-4 py-1.5 text-sm text-white transition-colors hover:bg-accent-blue/90"
              >
                下一步 <ChevronRight size={14} />
              </button>
            ) : (
              <button
                onClick={handleSubmit}
                className="rounded-lg bg-accent-blue px-4 py-1.5 text-sm text-white transition-colors hover:bg-accent-blue/90"
              >
                {editingRule ? '保存修改' : '创建规则'}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

/**
 * Provider settings — manage AI provider configuration (API keys, base URL, models).
 * Calls GET/POST /api/config/provider for persistence.
 */
import { memo, useState, useEffect, useCallback } from 'react';
import { Plus, Trash2, Eye, EyeOff, RefreshCw, CheckCircle, XCircle, Key } from 'lucide-react';


interface ProviderInfo {
  baseUrl?: string;
  hasApiKey: boolean;
  modelCount: number;
  apiType?: string;
}

interface ProviderSummary {
  configured: boolean;
  defaultProvider: string | null;
  providers: Record<string, ProviderInfo>;
}

interface AddFormData {
  provider: string;
  baseUrl: string;
  apiKey: string;
  modelId: string;
  apiType: string;
}

const API_TYPE_OPTIONS = [
  { value: 'openai-completions', label: 'OpenAI Compatible' },
  { value: 'anthropic-messages', label: 'Anthropic Messages' },
];

const INITIAL_FORM: AddFormData = {
  provider: '',
  baseUrl: '',
  apiKey: '',
  modelId: '',
  apiType: 'openai-completions',
};

export const ProviderSettings = memo(function ProviderSettings() {
  const [summary, setSummary] = useState<ProviderSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAdd, setShowAdd] = useState(false);
  const [form, setForm] = useState<AddFormData>(INITIAL_FORM);
  const [saving, setSaving] = useState(false);
  const [showKey, setShowKey] = useState(false);

  const loadProviders = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClientGet('/api/config/provider') as any;
      if (res?.success && res?.data) {
        setSummary(res.data as ProviderSummary);
      }
    } catch (e: unknown) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadProviders(); }, [loadProviders]);

  const handleSave = useCallback(async () => {
    if (!form.provider.trim() || !form.apiKey.trim()) return;
    setSaving(true);
    try {
      const res = await fetch('/api/config/provider', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          provider: form.provider.trim(),
          baseUrl: form.baseUrl.trim(),
          apiType: form.apiType,
          apiKey: form.apiKey.trim(),
          setDefault: summary?.providers && Object.keys(summary.providers).length === 0,
          models: form.modelId.trim() ? [{
            id: form.modelId.trim(),
            name: form.modelId.trim(),
            enabled: true,
            priority: 10,
          }] : [],
        }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setShowAdd(false);
      setForm(INITIAL_FORM);
      loadProviders();
    } catch (e: unknown) {
      setError(`Save failed: ${String(e)}`);
    } finally {
      setSaving(false);
    }
  }, [form, summary, loadProviders]);

  const handleDelete = useCallback(async (providerName: string) => {
    if (!confirm(`Remove provider "${providerName}"? This deletes its API keys and models.`)) return;
    try {
      // Delete by saving with empty configuration for this provider
      await fetch('/api/config/provider', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ provider: providerName, apiKeys: [], models: [] }),
      });
      loadProviders();
    } catch (e: unknown) {
      setError(String(e));
    }
  }, [loadProviders]);

  if (loading) {
    return <div className="p-4 text-dark-muted">Loading provider configuration...</div>;
  }

  return (
    <div className="flex-1 overflow-y-auto p-4">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold flex items-center gap-2">
          <Key size={18} className="text-accent-yellow" />
          Provider Configuration
        </h2>
        <div className="flex gap-2">
          <button
            onClick={loadProviders}
            className="p-2 rounded-lg hover:bg-dark-hover text-dark-muted transition-colors"
            title="Refresh"
          >
            <RefreshCw size={16} />
          </button>
          <button
            onClick={() => setShowAdd(true)}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-accent-blue text-white rounded-lg hover:opacity-90 transition-opacity text-sm"
          >
            <Plus size={14} />
            Add Provider
          </button>
        </div>
      </div>

      {error && (
        <div className="mb-3 p-3 bg-accent-red/10 border border-accent-red/30 rounded-lg text-sm text-accent-red flex items-center gap-2">
          <XCircle size={14} />
          {error}
          <button onClick={() => setError(null)} className="ml-auto text-xs underline">Dismiss</button>
        </div>
      )}

      {/* Status badge */}
      <div className="mb-4 flex items-center gap-2">
        <span className="text-sm">Status:</span>
        {summary?.configured ? (
          <span className="flex items-center gap-1 text-sm text-green-400">
            <CheckCircle size={14} /> Configured
          </span>
        ) : (
          <span className="flex items-center gap-1 text-sm text-accent-yellow">
            <XCircle size={14} /> Not configured — add a provider below
          </span>
        )}
        {summary?.defaultProvider && (
          <span className="text-xs text-dark-muted ml-2">
            (default: {summary.defaultProvider})
          </span>
        )}
      </div>

      {/* Provider list */}
      <div className="space-y-3 max-w-2xl">
        {summary?.providers && Object.entries(summary.providers).map(([name, info]) => (
          <div key={name} className="bg-dark-surface border border-dark-border rounded-lg p-4">
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2">
                <span className="font-medium text-dark-text">{name}</span>
                {summary.defaultProvider === name && (
                  <span className="text-xs px-1.5 py-0.5 bg-accent-blue/20 text-accent-blue rounded">default</span>
                )}
              </div>
              <button
                onClick={() => handleDelete(name)}
                className="p-1 rounded hover:bg-dark-hover text-dark-muted hover:text-accent-red transition-colors"
                title="Remove provider"
              >
                <Trash2 size={14} />
              </button>
            </div>
            <div className="text-sm text-dark-muted space-y-0.5">
              <div>URL: <code className="text-xs bg-dark-bg px-1 py-0.5 rounded">{info.baseUrl || '(not set)'}</code></div>
              <div>Models: {info.modelCount} • Type: {info.apiType || 'openai-completions'}</div>
              <div className="flex items-center gap-1">
                API Key: {info.hasApiKey
                  ? <span className="text-green-400 flex items-center gap-1"><CheckCircle size={12} /> configured</span>
                  : <span className="text-accent-red flex items-center gap-1"><XCircle size={12} /> missing</span>
                }
              </div>
            </div>
          </div>
        ))}

        {(!summary?.providers || Object.keys(summary.providers).length === 0) && !showAdd && (
          <div className="text-dark-muted text-sm p-4 text-center">
            No providers configured. Click "Add Provider" to get started.
          </div>
        )}
      </div>

      {/* Add Provider Form */}
      {showAdd && (
        <div className="mt-4 bg-dark-surface border border-dark-border rounded-lg p-4 max-w-2xl">
          <h3 className="font-medium mb-3">Add Provider</h3>
          <div className="space-y-3">
            <div>
              <label className="block text-sm text-dark-muted mb-1">Provider Name</label>
              <input
                type="text"
                value={form.provider}
                onChange={e => setForm(f => ({ ...f, provider: e.target.value }))}
                placeholder="openai, anthropic, deepseek, ..."
                className="w-full bg-dark-bg border border-dark-border rounded px-3 py-2 text-sm text-dark-text focus:border-accent-blue outline-none"
              />
            </div>
            <div>
              <label className="block text-sm text-dark-muted mb-1">API Type</label>
              <select
                value={form.apiType}
                onChange={e => setForm(f => ({ ...f, apiType: e.target.value }))}
                className="w-full bg-dark-bg border border-dark-border rounded px-3 py-2 text-sm text-dark-text focus:border-accent-blue outline-none"
              >
                {API_TYPE_OPTIONS.map(o => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm text-dark-muted mb-1">Base URL</label>
              <input
                type="text"
                value={form.baseUrl}
                onChange={e => setForm(f => ({ ...f, baseUrl: e.target.value }))}
                placeholder="https://api.openai.com/v1"
                className="w-full bg-dark-bg border border-dark-border rounded px-3 py-2 text-sm text-dark-text focus:border-accent-blue outline-none"
              />
            </div>
            <div>
              <label className="block text-sm text-dark-muted mb-1">API Key</label>
              <div className="flex gap-2">
                <input
                  type={showKey ? 'text' : 'password'}
                  value={form.apiKey}
                  onChange={e => setForm(f => ({ ...f, apiKey: e.target.value }))}
                  placeholder="sk-..."
                  className="flex-1 bg-dark-bg border border-dark-border rounded px-3 py-2 text-sm text-dark-text focus:border-accent-blue outline-none"
                />
                <button
                  onClick={() => setShowKey(k => !k)}
                  className="px-2 rounded border border-dark-border hover:bg-dark-hover text-dark-muted transition-colors"
                >
                  {showKey ? <EyeOff size={14} /> : <Eye size={14} />}
                </button>
              </div>
            </div>
            <div>
              <label className="block text-sm text-dark-muted mb-1">Default Model ID</label>
              <input
                type="text"
                value={form.modelId}
                onChange={e => setForm(f => ({ ...f, modelId: e.target.value }))}
                placeholder="gpt-4o, claude-sonnet-4-6, ..."
                className="w-full bg-dark-bg border border-dark-border rounded px-3 py-2 text-sm text-dark-text focus:border-accent-blue outline-none"
              />
            </div>
            <div className="flex gap-2 pt-2">
              <button
                onClick={handleSave}
                disabled={saving || !form.provider.trim() || !form.apiKey.trim()}
                className="flex-1 py-2 bg-accent-blue text-white rounded-lg hover:opacity-90 disabled:opacity-50 transition-opacity text-sm"
              >
                {saving ? 'Saving...' : 'Save Provider'}
              </button>
              <button
                onClick={() => { setShowAdd(false); setForm(INITIAL_FORM); }}
                className="px-4 py-2 border border-dark-border rounded-lg hover:bg-dark-hover text-dark-muted transition-colors text-sm"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
});

// Raw fetch wrapper to match the api client pattern
async function apiClientGet(path: string): Promise<unknown> {
  const res = await fetch(path);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

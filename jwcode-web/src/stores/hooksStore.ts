import { create } from 'zustand';
import { api } from '../services/api';
import type {
  HookRule, HookRuleFormData, HookDryRunRequest, HookDryRunResult,
  HookExecutionLog, HookStats, HookEventCategory, HookAgentInfo
} from '../types';

interface HooksState {
  // Data
  rules: HookRule[];
  logs: HookExecutionLog[];
  stats: HookStats | null;
  events: HookEventCategory[];
  agents: HookAgentInfo[];
  lifecycleMappings: Record<string, string>;
  loading: boolean;
  error: string | null;
  fieldErrors: Record<string, string> | null;

  // UI state
  formOpen: boolean;
  editingRule: HookRule | null;
  dryRunResult: HookDryRunResult | null;
  logsExpanded: boolean;

  // Actions
  loadRules: () => Promise<void>;
  loadLogs: () => Promise<void>;
  loadStats: () => Promise<void>;
  loadEvents: () => Promise<void>;
  loadAgents: () => Promise<void>;
  loadLifecycleMappings: () => Promise<void>;
  loadAll: () => Promise<void>;

  createRule: (data: HookRuleFormData) => Promise<boolean>;
  updateRule: (name: string, data: HookRuleFormData) => Promise<boolean>;
  deleteRule: (name: string) => Promise<boolean>;
  batchDelete: (names: string[]) => Promise<boolean>;
  toggleRule: (name: string, enabled: boolean) => Promise<boolean>;
  batchToggle: (names: string[], enabled: boolean) => Promise<boolean>;

  dryRun: (request: HookDryRunRequest) => Promise<HookDryRunResult | null>;

  saveLifecycleMappings: (mappings: Record<string, string>) => Promise<boolean>;

  openForm: (rule?: HookRule) => void;
  closeForm: () => void;
  setDryRunResult: (result: HookDryRunResult | null) => void;
  setLogsExpanded: (expanded: boolean) => void;
  clearError: () => void;
  clearFieldErrors: () => void;
}

export const useHooksStore = create<HooksState>((set, get) => ({
  rules: [],
  logs: [],
  stats: null,
  events: [],
  agents: [],
  lifecycleMappings: {},
  loading: false,
  error: null,
  fieldErrors: null,

  formOpen: false,
  editingRule: null,
  dryRunResult: null,
  logsExpanded: false,

  loadRules: async () => {
    const res = await api.hooks.list();
    if (res.success && res.data) set({ rules: res.data });
    else set({ error: res.error || 'Failed to load rules' });
  },

  loadLogs: async () => {
    const res = await api.hooks.logs();
    if (res.success && res.data) set({ logs: res.data });
  },

  loadStats: async () => {
    const res = await api.hooks.stats();
    if (res.success && res.data) set({ stats: res.data });
  },

  loadEvents: async () => {
    const res = await api.hooks.events();
    if (res.success && res.data) set({ events: res.data });
  },

  loadAgents: async () => {
    const res = await api.hooks.agents();
    if (res.success && res.data) set({ agents: res.data });
  },

  loadLifecycleMappings: async () => {
    const res = await api.hooks.lifecycleMappings.get();
    if (res.success && res.data) set({ lifecycleMappings: res.data });
  },

  loadAll: async () => {
    set({ loading: true, error: null });
    try {
      await Promise.all([
        get().loadRules(),
        get().loadStats(),
        get().loadEvents(),
        get().loadAgents(),
        get().loadLifecycleMappings(),
      ]);
    } catch (e) {
      set({ error: String(e) });
    } finally {
      set({ loading: false });
    }
  },

  createRule: async (data) => {
    set({ fieldErrors: null });
    const res = await api.hooks.create(data);
    if (res.success) {
      await get().loadRules();
      await get().loadStats();
      set({ formOpen: false, editingRule: null });
      return true;
    }
    // Handle field-level errors
    if (res.error) {
      set({ error: res.error });
      try {
        const parsed = JSON.parse(res.error);
        if (parsed.fieldErrors) set({ fieldErrors: parsed.fieldErrors });
      } catch {}
    }
    return false;
  },

  updateRule: async (name, data) => {
    set({ fieldErrors: null });
    const res = await api.hooks.update(name, data);
    if (res.success) {
      await get().loadRules();
      set({ formOpen: false, editingRule: null });
      return true;
    }
    set({ error: res.error || 'Update failed' });
    return false;
  },

  deleteRule: async (name) => {
    const res = await api.hooks.delete(name);
    if (res.success) {
      await get().loadRules();
      await get().loadStats();
      return true;
    }
    set({ error: res.error || 'Delete failed' });
    return false;
  },

  batchDelete: async (names) => {
    const res = await api.hooks.batchDelete(names);
    if (res.success) {
      await get().loadRules();
      await get().loadStats();
      return true;
    }
    set({ error: res.error || 'Batch delete failed' });
    return false;
  },

  toggleRule: async (name, enabled) => {
    const res = await api.hooks.toggle(name, enabled);
    if (res.success) {
      // Optimistic update
      set(state => ({
        rules: state.rules.map(r =>
          r.name === name ? { ...r, enabled } : r
        ),
      }));
      return true;
    }
    // Revert optimistic update
    await get().loadRules();
    return false;
  },

  batchToggle: async (names, enabled) => {
    const res = await api.hooks.batchToggle(names, enabled);
    if (res.success) {
      set(state => ({
        rules: state.rules.map(r =>
          names.includes(r.name) ? { ...r, enabled } : r
        ),
      }));
      return true;
    }
    await get().loadRules();
    return false;
  },

  dryRun: async (request) => {
    const res = await api.hooks.dryRun(request);
    if (res.success && res.data) {
      set({ dryRunResult: res.data });
      return res.data;
    }
    set({ error: res.error || 'Dry-run failed' });
    return null;
  },

  saveLifecycleMappings: async (mappings) => {
    const res = await api.hooks.lifecycleMappings.save(mappings);
    if (res.success) {
      set({ lifecycleMappings: mappings });
      return true;
    }
    set({ error: res.error || 'Save mappings failed' });
    return false;
  },

  openForm: (rule) => set({ formOpen: true, editingRule: rule || null, dryRunResult: null, fieldErrors: null }),
  closeForm: () => set({ formOpen: false, editingRule: null, dryRunResult: null, fieldErrors: null }),
  setDryRunResult: (result) => set({ dryRunResult: result }),
  setLogsExpanded: (expanded) => set({ logsExpanded: expanded }),
  clearError: () => set({ error: null }),
  clearFieldErrors: () => set({ fieldErrors: null }),
}));

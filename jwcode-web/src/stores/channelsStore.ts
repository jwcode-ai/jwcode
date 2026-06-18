import { create } from 'zustand';
import { api } from '../services/api';
import type { Channel, ChannelFormData } from '../types';

interface ChannelsState {
  channels: Channel[];
  loading: boolean;
  error: string | null;
  formOpen: boolean;
  editing: Channel | null;
  qrChannel: Channel | null;   // 当前正在扫码登录的渠道

  load: () => Promise<void>;
  create: (data: ChannelFormData) => Promise<boolean>;
  update: (id: string, data: ChannelFormData) => Promise<boolean>;
  remove: (id: string) => Promise<boolean>;
  toggle: (id: string, enabled: boolean) => Promise<boolean>;
  openForm: (ch?: Channel) => void;
  closeForm: () => void;
  openQr: (ch: Channel) => void;
  closeQr: () => void;
  clearError: () => void;
}

export const useChannelsStore = create<ChannelsState>((set, get) => ({
  channels: [], loading: false, error: null, formOpen: false, editing: null, qrChannel: null,

  load: async () => {
    set({ loading: true, error: null });
    const res = await api.channels.list();
    set({ loading: false, ...(res.success ? { channels: res.data ?? [] } : { error: res.error ?? 'Failed to load' }) });
  },

  create: async (data) => {
    const res = await api.channels.create(data);
    if (res.success) { await get().load(); set({ formOpen: false, editing: null }); return true; }
    set({ error: res.error ?? 'Create failed' }); return false;
  },

  update: async (id, data) => {
    const res = await api.channels.update(id, data);
    if (res.success) { await get().load(); set({ formOpen: false, editing: null }); return true; }
    set({ error: res.error ?? 'Update failed' }); return false;
  },

  remove: async (id) => {
    const res = await api.channels.delete(id);
    if (res.success) { set(s => ({ channels: s.channels.filter(c => c.id !== id) })); return true; }
    set({ error: res.error ?? 'Delete failed' }); return false;
  },

  toggle: async (id, enabled) => {
    const res = await api.channels.toggle(id, enabled);
    if (res.success) { set(s => ({ channels: s.channels.map(c => c.id === id ? { ...c, enabled } : c) })); return true; }
    await get().load(); return false;
  },

  openForm: (ch) => set({ formOpen: true, editing: ch ?? null, error: null }),
  closeForm: () => set({ formOpen: false, editing: null, error: null }),
  openQr: (ch) => set({ qrChannel: ch }),
  closeQr: () => { set({ qrChannel: null }); get().load(); },
  clearError: () => set({ error: null }),
}));

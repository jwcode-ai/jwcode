import { useState } from 'react';
import { X, QrCode } from 'lucide-react';
import { useChannelsStore } from '../../stores/channelsStore';
import type { ChannelFormData, ChannelType } from '../../types';

const CHANNEL_LABELS: Record<ChannelType, string> = { wechat: '微信', feishu: '飞书', dingtalk: '钉钉' };
const EMPTY_FORM: ChannelFormData = { name: '', type: 'wechat', appId: '', appSecret: '', token: '', encodingAESKey: '', enabled: true };

export function ChannelDrawer({ onClose }: { onClose: () => void }) {
  const { editing, create, update, error, clearError, openQr } = useChannelsStore();
  const [form, setForm] = useState<ChannelFormData>(editing ? { ...editing, appSecret: '', token: '', encodingAESKey: '' } : EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  const upd = (k: keyof ChannelFormData, v: unknown) => { clearError(); setForm(f => ({ ...f, [k]: v })); };

  const handleSave = async () => {
    if (!form.name.trim()) return;
    setSaving(true);
    const ok = editing ? await update(editing.id!, form) : await create(form);
    setSaving(false);
    if (ok) onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/50 backdrop-blur-sm animate-fade-in" onClick={onClose}>
      <div className="w-[440px] bg-dark-surface h-full overflow-y-auto flex flex-col border-l border-dark-border" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between px-6 py-4 border-b border-dark-border">
          <h2 className="font-semibold text-dark-text">{editing ? '编辑频道' : '新建频道'}</h2>
          <button onClick={onClose} className="p-1 rounded-lg text-dark-muted hover:text-dark-text hover:bg-dark-hover transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex-1 p-6 space-y-5">
          {error && <div className="bg-red-500/10 border border-red-500/40 text-red-400 px-3 py-2 rounded-lg text-sm">{error}</div>}

          {/* 基础信息 */}
          <Section title="基础信息">
            <Field label="频道名称" required>
              <input value={form.name} onChange={e => upd('name', e.target.value)} placeholder="例如：研发群机器人" className={INPUT} />
            </Field>
            <Field label="频道类型">
              <select value={form.type} onChange={e => upd('type', e.target.value as ChannelType)} className={INPUT} disabled={!!editing}>
                {(Object.keys(CHANNEL_LABELS) as ChannelType[]).map(t => <option key={t} value={t}>{CHANNEL_LABELS[t]}</option>)}
              </select>
              {editing && <Hint>频道类型创建后不可修改</Hint>}
            </Field>
          </Section>

          {/* 凭证 */}
          <Section title="接入凭证">
            <Field label="AppID / 企业ID">
              <input value={form.appId} onChange={e => upd('appId', e.target.value)} placeholder="AppID" className={INPUT} />
            </Field>
            <Field label="AppSecret">
              <input type="password" value={form.appSecret} onChange={e => upd('appSecret', e.target.value)} placeholder={editing ? '留空表示不修改' : '应用密钥'} className={INPUT} />
            </Field>
            <Field label="Token">
              <input value={form.token} onChange={e => upd('token', e.target.value)} placeholder={editing ? '留空表示不修改' : '回调校验 Token'} className={INPUT} />
            </Field>
            {form.type === 'wechat' && (
              <Field label="EncodingAESKey">
                <input value={form.encodingAESKey} onChange={e => upd('encodingAESKey', e.target.value)} placeholder="选填，消息加解密密钥" className={INPUT} />
              </Field>
            )}
            {form.type === 'wechat' && (
              <Hint>微信 iLink 机器人通过扫码授权，凭证可留空，保存后在列表点击「扫码登录」。</Hint>
            )}
          </Section>

          <div className="flex items-center justify-between rounded-lg border border-dark-border px-3 py-2.5">
            <div>
              <p className="text-sm text-dark-text">启用频道</p>
              <p className="text-xs text-dark-muted">关闭后停止收发消息</p>
            </div>
            <Toggle checked={form.enabled} onChange={v => upd('enabled', v)} />
          </div>

          {/* 微信扫码入口（仅编辑态可用，需先有渠道记录） */}
          {form.type === 'wechat' && editing && (
            <button
              onClick={() => { openQr(editing); onClose(); }}
              className="w-full flex items-center justify-center gap-2 text-sm bg-green-600/15 hover:bg-green-600/25 text-green-400 border border-green-600/40 px-3 py-2.5 rounded-lg transition-colors"
            >
              <QrCode className="w-4 h-4" /> 扫码登录 / 重新授权
            </button>
          )}
        </div>

        <div className="flex justify-end gap-2 px-6 py-4 border-t border-dark-border">
          <button onClick={onClose} className="px-4 py-2 text-sm text-dark-muted hover:text-dark-text transition-colors">取消</button>
          <button onClick={handleSave} disabled={saving || !form.name.trim()} className="px-4 py-2 text-sm bg-accent-blue hover:bg-accent-blue/90 text-white rounded-lg disabled:opacity-50 transition-colors">
            {saving ? '保存中...' : '保存'}
          </button>
        </div>
      </div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="space-y-3">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-dark-muted">{title}</h3>
      {children}
    </div>
  );
}

function Field({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-xs text-dark-muted mb-1.5">{label}{required && <span className="text-red-400 ml-0.5">*</span>}</label>
      {children}
    </div>
  );
}

function Hint({ children }: { children: React.ReactNode }) {
  return <p className="text-xs text-dark-muted mt-1.5 leading-relaxed">{children}</p>;
}

function Toggle({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <button onClick={() => onChange(!checked)} className={`w-9 h-5 rounded-full relative transition-colors shrink-0 ${checked ? 'bg-green-600' : 'bg-gray-600'}`}>
      <span className={`absolute top-0.5 w-4 h-4 bg-white rounded-full transition-all ${checked ? 'left-[18px]' : 'left-0.5'}`} />
    </button>
  );
}

const INPUT = 'w-full bg-dark-bg border border-dark-border rounded-lg px-3 py-2 text-sm text-dark-text placeholder-dark-muted/60 focus:outline-none focus:border-accent-blue transition-colors';

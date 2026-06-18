import { QrCode, Pencil, Trash2, Circle } from 'lucide-react';
import { useChannelsStore } from '../../stores/channelsStore';
import type { ChannelType } from '../../types';

const LABELS: Record<ChannelType, string> = { wechat: '微信', feishu: '飞书', dingtalk: '钉钉' };
const TYPE_COLOR: Record<ChannelType, string> = {
  wechat: 'bg-green-500/15 text-green-400 border-green-500/30',
  feishu: 'bg-blue-500/15 text-blue-400 border-blue-500/30',
  dingtalk: 'bg-orange-500/15 text-orange-400 border-orange-500/30',
};

export function ChannelTable() {
  const { channels, toggle, remove, openForm, openQr } = useChannelsStore();

  if (channels.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center">
        <div className="text-5xl mb-3">🔌</div>
        <p className="text-dark-text font-medium">还没有配置任何渠道</p>
        <p className="text-sm text-dark-muted mt-1">点击右上角「新建渠道」接入微信、飞书或钉钉</p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-dark-border overflow-hidden">
      <table className="w-full text-sm">
        <thead>
          <tr className="bg-dark-hover/40 text-xs text-dark-muted">
            <th className="text-left py-2.5 px-4 font-medium">渠道名称</th>
            <th className="text-left py-2.5 px-4 font-medium">类型</th>
            <th className="text-left py-2.5 px-4 font-medium">AppID</th>
            <th className="text-left py-2.5 px-4 font-medium">连接</th>
            <th className="text-center py-2.5 px-4 font-medium">启用</th>
            <th className="text-right py-2.5 px-4 font-medium">操作</th>
          </tr>
        </thead>
        <tbody>
          {channels.map(ch => (
            <tr key={ch.id} className="border-t border-dark-border hover:bg-dark-hover/30 transition-colors">
              <td className="py-3 px-4 text-dark-text font-medium">{ch.name}</td>
              <td className="py-3 px-4">
                <span className={`text-xs px-2 py-0.5 rounded border ${TYPE_COLOR[ch.type] ?? 'bg-gray-700 text-gray-300 border-gray-600'}`}>
                  {LABELS[ch.type] ?? ch.type}
                </span>
              </td>
              <td className="py-3 px-4 text-dark-muted font-mono text-xs">{ch.appId || '—'}</td>
              <td className="py-3 px-4">
                <span className="inline-flex items-center gap-1.5 text-xs">
                  <Circle className={`w-2 h-2 ${ch.connected ? 'fill-green-500 text-green-500' : 'fill-gray-500 text-gray-500'}`} />
                  <span className={ch.connected ? 'text-green-400' : 'text-dark-muted'}>{ch.connected ? '已连接' : '未连接'}</span>
                </span>
              </td>
              <td className="py-3 px-4 text-center">
                <Toggle enabled={ch.enabled} onChange={v => toggle(ch.id, v)} />
              </td>
              <td className="py-3 px-4">
                <div className="flex items-center justify-end gap-1">
                  {ch.type === 'wechat' && (
                    <IconBtn title="扫码登录" onClick={() => openQr(ch)} className="text-green-400 hover:bg-green-500/10">
                      <QrCode className="w-4 h-4" />
                    </IconBtn>
                  )}
                  <IconBtn title="编辑" onClick={() => openForm(ch)} className="text-blue-400 hover:bg-blue-500/10">
                    <Pencil className="w-4 h-4" />
                  </IconBtn>
                  <IconBtn title="删除" onClick={() => confirm(`确认删除渠道「${ch.name}」？`) && remove(ch.id)} className="text-red-400 hover:bg-red-500/10">
                    <Trash2 className="w-4 h-4" />
                  </IconBtn>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function IconBtn({ children, title, onClick, className = '' }: { children: React.ReactNode; title: string; onClick: () => void; className?: string }) {
  return (
    <button title={title} onClick={onClick} className={`p-1.5 rounded-md transition-colors ${className}`}>
      {children}
    </button>
  );
}

function Toggle({ enabled, onChange }: { enabled: boolean; onChange: (v: boolean) => void }) {
  return (
    <button onClick={() => onChange(!enabled)} className={`w-9 h-5 rounded-full relative transition-colors ${enabled ? 'bg-green-600' : 'bg-gray-600'}`}>
      <span className={`absolute top-0.5 w-4 h-4 bg-white rounded-full transition-all ${enabled ? 'left-[18px]' : 'left-0.5'}`} />
    </button>
  );
}

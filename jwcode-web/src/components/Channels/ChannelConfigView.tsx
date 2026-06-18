import { useEffect } from 'react';
import { Plus, RefreshCw, X } from 'lucide-react';
import { useChannelsStore } from '../../stores/channelsStore';
import { ChannelTable } from './ChannelTable';
import { ChannelDrawer } from './ChannelDrawer';
import { WechatQrModal } from './WechatQrModal';

export function ChannelConfigView() {
  const { load, loading, error, formOpen, openForm, closeForm, clearError, qrChannel, closeQr } = useChannelsStore();

  useEffect(() => { load(); }, []);

  return (
    <div className="flex flex-col h-full bg-dark-bg text-dark-text">
      {/* Toolbar */}
      <div className="flex items-center justify-between px-6 py-4 border-b border-dark-border shrink-0">
        <div>
          <h2 className="font-semibold text-base">渠道管理</h2>
          <p className="text-xs text-dark-muted mt-0.5">接入微信、飞书、钉钉，用户可通过渠道发送任务并实时接收进度与结果</p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => load()} title="刷新" className="p-2 rounded-lg text-dark-muted hover:text-dark-text hover:bg-dark-hover transition-colors">
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
          <button onClick={() => openForm()} className="bg-accent-blue hover:bg-accent-blue/90 text-white text-sm px-3 py-2 rounded-lg flex items-center gap-1.5 transition-colors">
            <Plus className="w-4 h-4" /> 新建渠道
          </button>
        </div>
      </div>

      {/* Error banner */}
      {error && (
        <div className="mx-6 mt-3 bg-red-500/10 border border-red-500/40 text-red-400 px-3 py-2 rounded-lg text-sm flex justify-between items-center">
          <span>{error}</span>
          <button onClick={clearError} className="text-red-400 hover:text-red-300"><X className="w-4 h-4" /></button>
        </div>
      )}

      {/* Content */}
      <div className="flex-1 overflow-auto px-6 py-4">
        {loading && useChannelsStore.getState().channels.length === 0
          ? <div className="text-center py-20 text-dark-muted">加载中...</div>
          : <ChannelTable />}
      </div>

      {/* Drawer + QR modal */}
      {formOpen && <ChannelDrawer onClose={closeForm} />}
      {qrChannel && <WechatQrModal channel={qrChannel} onClose={closeQr} />}
    </div>
  );
}

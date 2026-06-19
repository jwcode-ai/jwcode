import { useEffect, useRef, useState } from 'react';
import { Modal } from '../common/Modal';
import { api } from '../../services/api';
import type { Channel } from '../../types';

type Phase = 'loading' | 'waiting' | 'scanned' | 'confirmed' | 'expired' | 'error';

export function WechatQrModal({ channel, onClose }: { channel: Channel; onClose: () => void }) {
  const [phase, setPhase] = useState<Phase>('loading');
  const [qrUrl, setQrUrl] = useState<string | null>(null);       // QR 图片 URL
  const [wechatUrl, setWechatUrl] = useState<string | null>(null); // 原始微信链接
  const [imgFailed, setImgFailed] = useState(false);
  const [errMsg, setErrMsg] = useState<string>('');
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopPoll = () => { if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null; } };

  const fetchQr = async () => {
    setPhase('loading'); setQrUrl(null); setWechatUrl(null); setImgFailed(false); setErrMsg('');
    try {
      const res = await api.channels.wechat.qrcode(channel.id, '');
      const inner = (res as any)?.data?.data ?? (res as any)?.data;
      const wcUrl: string = inner?.qrcode_img_content;
      const qrcode: string = inner?.qrcode;
      if (!res.success || !wcUrl || !qrcode) {
        setPhase('error');
        setErrMsg((res as any)?.error || `字段缺失: ${JSON.stringify(inner)}`);
        return;
      }
      setWechatUrl(wcUrl);
      // 用 Google Charts 生成二维码图片（国内访问不到时会触发 onError 降级）
      setQrUrl(`https://chart.googleapis.com/chart?cht=qr&chs=240x240&chld=M|1&chl=${encodeURIComponent(wcUrl)}`);
      setPhase('waiting');
      startPoll(qrcode);
    } catch (e: any) { setPhase('error'); setErrMsg(String(e?.message ?? e)); }
  };

  const startPoll = (qrcode: string) => {
    stopPoll();
    pollRef.current = setInterval(async () => {
      try {
        const res = await api.channels.wechat.qrcodeStatus(channel.id, '', qrcode);
        // iLink 返回 { data: { status, ret, bot_token, ... }, errcode, ret, errmsg }
        // 后端包一层 { success, data: <iLink raw> }
        const payload: any = (res as any)?.data?.data ?? (res as any)?.data;
        const status: string = payload?.status;
        const ret: number = payload?.ret;
        // 控制台调试（用户 F12 即可看到每次返回的原始 payload）
        // eslint-disable-next-line no-console
        console.debug('[WechatQr] poll', { status, ret, payload });

        // confirmed 检查必须先于 scaned — iLink 确认后 status 仍为 scaned + ret=0
        if (status === 'confirmed' || (status === 'scaned' && ret === 0)) {
          stopPoll(); setPhase('confirmed'); setTimeout(onClose, 1200);
        } else if (status === 'scaned' || status === 'scanned') {
          setPhase('scanned');
        } else if (status === 'expired' || status === 'canceled') { stopPoll(); setPhase('expired'); }
        // iLink 在手机端确认后，status 会从 scaned 保持不变 + ret=0 视为确认成功
      } catch (e) {
        // eslint-disable-next-line no-console
        console.warn("[WechatQr] poll error", e);
      }
    }, 2000);
  };

  useEffect(() => { fetchQr(); return stopPoll; }, []); // eslint-disable-line

  return (
    <Modal isOpen onClose={onClose} title={`扫码登录 · ${channel.name}`} size="sm">
      <div className="flex flex-col items-center gap-4 py-2">
        {/* QR area */}
        <div className="relative w-60 h-60 rounded-lg bg-white flex items-center justify-center overflow-hidden">
          {phase === 'loading' && <span className="text-gray-400 text-sm">二维码生成中...</span>}

          {/* QR 图片（Google Charts 失败时隐藏，显示降级提示） */}
          {qrUrl && !imgFailed && phase !== 'error' && phase !== 'expired' && (
            <img
              src={qrUrl}
              alt="微信登录二维码"
              className="w-full h-full object-contain"
              onError={() => setImgFailed(true)}
            />
          )}
          {/* imgFailed 降级：Google Charts 国内不可用 */}
          {imgFailed && phase === 'waiting' && (
            <div className="flex flex-col items-center justify-center gap-2 px-4 text-center">
              <span className="text-3xl">🔔</span>
              <p className="text-xs text-gray-500">二维码图片加载失败</p>
              <p className="text-xs text-gray-400">请点击下方按钮在新标签页打开</p>
            </div>
          )}

          {/* 状态遮罩 */}
          {phase === 'scanned' && (
            <div className="absolute inset-0 bg-black/70 flex flex-col items-center justify-center text-white gap-1">
              <span className="text-2xl">✓</span><span className="text-sm">已扫描，请在手机上确认</span>
            </div>
          )}
          {phase === 'confirmed' && (
            <div className="absolute inset-0 bg-green-600/90 flex flex-col items-center justify-center text-white gap-1">
              <span className="text-2xl">🎉</span><span className="text-sm">登录成功</span>
            </div>
          )}
          {phase === 'expired' && (
            <div className="absolute inset-0 bg-black/80 flex flex-col items-center justify-center text-white gap-2">
              <span className="text-sm">二维码已失效</span>
              <button onClick={fetchQr} className="text-xs bg-green-600 hover:bg-green-500 px-3 py-1 rounded">点击刷新</button>
            </div>
          )}
          {phase === 'error' && (
            <div className="absolute inset-0 bg-black/80 flex flex-col items-center justify-center text-red-300 gap-2 px-4 text-center">
              <span className="text-xs">{errMsg || '未知错误'}</span>
              <button onClick={fetchQr} className="text-xs bg-blue-600 hover:bg-blue-500 px-3 py-1 rounded text-white">重试</button>
            </div>
          )}
        </div>

        <p className="text-sm text-dark-muted text-center">
          {phase === 'waiting' && (imgFailed ? '请在新标签页打开后用微信扫码' : '请使用微信扫描二维码授权机器人')}
          {phase === 'scanned' && '已扫描，请在手机上确认...'}
          {phase === 'confirmed' && '机器人已授权并启动'}
          {phase === 'loading' && '正在连接 iLink 服务...'}
          {(phase === 'expired' || phase === 'error') && '二维码已失效'}
        </p>

        {/* 新标签页打开按钮（始终可用，作为主要或备用扫码方式） */}
        {wechatUrl && phase === 'waiting' && (
          <button
            onClick={() => window.open(wechatUrl, '_blank', 'width=480,height=600,noopener')}
            className="w-full flex items-center justify-center gap-2 text-sm bg-green-600/15 hover:bg-green-600/25 text-green-400 border border-green-600/40 px-3 py-2.5 rounded-lg transition-colors"
          >
            <span>🔔</span> 在新标签页打开二维码
          </button>
        )}
      </div>
    </Modal>
  );
}

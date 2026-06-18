import { useEffect, useRef, useState } from 'react';
import { Modal } from '../common/Modal';
import { api } from '../../services/api';
import type { Channel } from '../../types';

type Phase = 'loading' | 'waiting' | 'scanned' | 'confirmed' | 'expired' | 'error';

export function WechatQrModal({ channel, onClose }: { channel: Channel; onClose: () => void }) {
  const [phase, setPhase] = useState<Phase>('loading');
  const [qrUrl, setQrUrl] = useState<string | null>(null);       // QR 鍥剧墖 URL
  const [wechatUrl, setWechatUrl] = useState<string | null>(null); // 鍘熷寰俊閾炬帴
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
        setErrMsg((res as any)?.error || `瀛楁缂哄け: ${JSON.stringify(inner)}`);
        return;
      }
      setWechatUrl(wcUrl);
      // 鐢?Google Charts 鐢熸垚浜岀淮鐮佸浘鐗囷紙鍥藉唴璁块棶涓嶅埌鏃朵細瑙﹀彂 onError 闄嶇骇锛?      setQrUrl(`https://chart.googleapis.com/chart?cht=qr&chs=240x240&chld=M|1&chl=${encodeURIComponent(wcUrl)}`);
      setPhase('waiting');
      startPoll(qrcode);
    } catch (e: any) { setPhase('error'); setErrMsg(String(e?.message ?? e)); }
  };

  const startPoll = (qrcode: string) => {
    stopPoll();
    pollRef.current = setInterval(async () => {
      try {
        const res = await api.channels.wechat.qrcodeStatus(channel.id, '', qrcode);
        // iLink 杩斿洖 { data: { status, ret, bot_token, ... }, errcode, ret, errmsg }
        // 鍚庣鍖呬竴灞?{ success, data: <iLink raw> }
        const payload: any = (res as any)?.data?.data ?? (res as any)?.data;
        const status: string = payload?.status;
        const ret: number = payload?.ret;
        // 鎺у埗鍙拌皟璇曪紙鐢ㄦ埛 F12 鍗冲彲鐪嬪埌姣忔杩斿洖鐨勫師濮?payload锛?        // eslint-disable-next-line no-console
        console.debug('[WechatQr] poll', { status, ret, payload });

        // confirmed 妫€鏌ュ繀椤诲厛浜?scaned 鈥?iLink 纭鍚?status 浠嶄负 scaned + ret=0
        if (status === 'confirmed' || (status === 'scaned' && ret === 0)) {
          stopPoll(); setPhase('confirmed'); setTimeout(onClose, 1200);
        } else // confirmed must be checked BEFORE scaned
        if (status === 'confirmed' || (status === 'scaned' && ret === 0)) {
          stopPoll(); setPhase('confirmed'); setTimeout(onClose, 1200);
        } else if (status === 'scaned' || status === 'scanned') {
          setPhase('scanned');
        } else if (status === 'expired' || status === 'canceled') { stopPoll(); setPhase('expired'); }
        // iLink 鍦ㄦ墜鏈虹纭鍚庯紝status 浼氫粠 scaned 淇濇寔涓嶅彉 + ret=0 瑙嗕负纭鎴愬姛
      } catch (e) {
        // eslint-disable-next-line no-console
        console.warn("[WechatQr] poll error", e);
      }
    }, 2000);
  };

  useEffect(() => { fetchQr(); return stopPoll; }, []); // eslint-disable-line

  return (
    <Modal isOpen onClose={onClose} title={`鎵爜鐧诲綍 路 ${channel.name}`} size="sm">
      <div className="flex flex-col items-center gap-4 py-2">
        {/* QR area */}
        <div className="relative w-60 h-60 rounded-lg bg-white flex items-center justify-center overflow-hidden">
          {phase === 'loading' && <span className="text-gray-400 text-sm">浜岀淮鐮佺敓鎴愪腑...</span>}

          {/* QR 鍥剧墖锛圙oogle Charts 澶辫触鏃堕殣钘忥紝鏄剧ず闄嶇骇鎻愮ず锛?*/}
          {qrUrl && !imgFailed && phase !== 'error' && phase !== 'expired' && (
            <img
              src={qrUrl}
              alt="微信登录二维码片"
              className="w-full h-full object-contain"
              onError={() => setImgFailed(true)}
            />
          )}
          {/* imgFailed 闄嶇骇锛欸oogle Charts 鍥藉唴涓嶅彲鐢?*/}
          {imgFailed && phase === 'waiting' && (
            <div className="flex flex-col items-center justify-center gap-2 px-4 text-center">
              <span className="text-3xl">馃敆</span>
              <p className="text-xs text-gray-500">浜岀淮鐮佸浘鐗囧姞杞藉け璐</p>
              <p className="text-xs text-gray-400">璇风偣鍑讳笅鏂规寜閽湪鏂版爣绛鹃〉鎵撳紑</p>
            </div>
          )}

          {/* 鐘舵€侀伄缃?*/}
          {phase === 'scanned' && (
            <div className="absolute inset-0 bg-black/70 flex flex-col items-center justify-center text-white gap-1">
              <span className="text-2xl">鉁</span><span className="text-sm">宸叉壂鎻忥紝璇峰湪鎵嬫満涓婄‘璁</span>
            </div>
          )}
          {phase === 'confirmed' && (
            <div className="absolute inset-0 bg-green-600/90 flex flex-col items-center justify-center text-white gap-1">
              <span className="text-2xl">馃帀</span><span className="text-sm">鐧诲綍鎴愬姛</span>
            </div>
          )}
          {phase === 'expired' && (
            <div className="absolute inset-0 bg-black/80 flex flex-col items-center justify-center text-white gap-2">
              <span className="text-sm">浜岀淮鐮佸凡澶辨晥</span>
              <button onClick={fetchQr} className="text-xs bg-green-600 hover:bg-green-500 px-3 py-1 rounded">鐐瑰嚮鍒锋柊</button>
            </div>
          )}
          {phase === 'error' && (
            <div className="absolute inset-0 bg-black/80 flex flex-col items-center justify-center text-red-300 gap-2 px-4 text-center">
              <span className="text-xs">{errMsg || '??????'}</span>
              <button onClick={fetchQr} className="text-xs bg-blue-600 hover:bg-blue-500 px-3 py-1 rounded text-white">閲嶈瘯</button>
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

        {/* 鏂版爣绛鹃〉鎵撳紑鎸夐挳锛堝缁堝彲鐢紝浣滀负涓昏鎴栧鐢ㄦ壂鐮佹柟寮忥級 */}
        {wechatUrl && phase === 'waiting' && (
          <button
            onClick={() => window.open(wechatUrl, '_blank', 'width=480,height=600,noopener')}
            className="w-full flex items-center justify-center gap-2 text-sm bg-green-600/15 hover:bg-green-600/25 text-green-400 border border-green-600/40 px-3 py-2.5 rounded-lg transition-colors"
          >
            <span>馃敆</span> 鍦ㄦ柊鏍囩椤垫墦寮€浜岀淮鐮?          </button>
        )}
      </div>
    </Modal>
  );
}
